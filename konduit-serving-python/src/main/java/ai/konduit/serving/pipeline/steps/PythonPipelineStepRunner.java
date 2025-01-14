/*
 *
 *  * ******************************************************************************
 *  *  * Copyright (c) 2015-2019 Skymind Inc.
 *  *  * Copyright (c) 2019 Konduit AI.
 *  *  *
 *  *  * This program and the accompanying materials are made available under the
 *  *  * terms of the Apache License, Version 2.0 which is available at
 *  *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *  *
 *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  *  * License for the specific language governing permissions and limitations
 *  *  * under the License.
 *  *  *
 *  *  * SPDX-License-Identifier: Apache-2.0
 *  *  *****************************************************************************
 *
 *
 */

package ai.konduit.serving.pipeline.steps;

import ai.konduit.serving.executioner.PythonExecutioner;
import ai.konduit.serving.executioner.Pipeline;
import ai.konduit.serving.util.python.PythonTransform;
import ai.konduit.serving.model.PythonConfig;
import ai.konduit.serving.pipeline.PipelineStep;
import ai.konduit.serving.pipeline.PythonPipelineStep;
import ai.konduit.serving.util.python.PythonVariables;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.datavec.api.records.Record;
import org.datavec.api.transform.TransformProcess;
import org.datavec.api.writable.Writable;
import org.datavec.local.transforms.LocalTransformExecutor;
import org.nd4j.base.Preconditions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Run python code as part of a {@link Pipeline}
 *
 * @author Adam Gibson
 */
@Slf4j
public class PythonPipelineStepRunner extends BasePipelineStepRunner {

    private Map<String, PythonTransform> pythonTransform;
    private Map<String,TransformProcess> transformProcesses;

    public PythonPipelineStepRunner(PipelineStep pipelineStep) {
        super(pipelineStep);
        PythonPipelineStep pythonConfig = (PythonPipelineStep) pipelineStep;
        pythonTransform = new HashMap<>();
        transformProcesses = new HashMap<>();
        boolean setPath = false;
        for(Map.Entry<String,PythonConfig> configEntry : pythonConfig.getPythonConfigs().entrySet()) {
            Preconditions.checkState(pipelineStep.hasInputName(configEntry.getKey()),"Invalid input name specified for transform " + configEntry.getKey());
            PythonConfig currConfig = configEntry.getValue();
            if(currConfig.getPythonPath() != null && !setPath) {
                log.info("Over riding python path " + currConfig.getPythonPath());
                System.setProperty(PythonExecutioner.DEFAULT_PYTHON_PATH_PROPERTY,currConfig.getPythonPath());
                setPath = true;
            }


            String code = configEntry.getValue().getPythonCode();
            if(code == null) {
                try {
                    code = FileUtils.readFileToString(new File(currConfig.getPythonCodePath()), Charset.defaultCharset());
                } catch (IOException e) {
                    log.error("Unable to read code from " + currConfig.getPythonCodePath());
                }
                log.info("Resolving code from " + currConfig.getPythonCodePath());
            }


            Preconditions.checkNotNull(code,"No code to run!");
            Preconditions.checkState(!code.isEmpty(),"Code resolved to an empty string!");
            PythonTransform pythonTransform = PythonTransform.builder()
                    .code(code)
                    .returnAllInputs(currConfig.isReturnAllInputs())
                    .inputs(currConfig.getPythonInputs() != null ? PythonVariables.schemaFromMap(currConfig.getPythonInputs()): null)
                    .outputs(currConfig.getPythonOutputs() != null ? PythonVariables.schemaFromMap(currConfig.getPythonOutputs()) : null)
                    .inputSchema(pythonConfig.inputSchemaForName(configEntry.getKey()))
                    .outputSchema(pythonConfig.outputSchemaForName(configEntry.getKey()))
                    .build();
            this.pythonTransform.put(configEntry.getKey(),pythonTransform);
            TransformProcess transformProcess = new TransformProcess.Builder(pythonConfig.inputSchemaForName(configEntry.getKey()))
                    .transform(pythonTransform)
                    .build();
            this.transformProcesses.put(configEntry.getKey(),transformProcess);
        }

        PythonExecutioner.init();
    }

    @Override
    public void destroy() {
        //get rid of everything but the main interpreter and clear all the variables but the default one
        PythonExecutioner.clearNonMainInterpreters();
        PythonExecutioner.resetAllInterpreters();
    }

    @Override
    public Record[] transform(Record[] input) {
        Record[] ret = new Record[input.length];
        for(int i = 0; i < ret.length; i++) {
            if(transformProcesses.containsKey(pipelineStep.inputNameAt(i))) {
                TransformProcess transformProcess = transformProcesses.get(pipelineStep.inputNameAt(i));
                Preconditions.checkState(input[i].getRecord() != null && !input[i].getRecord().isEmpty(),"Record should not be empty!");
                List<List<Writable>> execute = LocalTransformExecutor.execute(Arrays.asList(input[i].getRecord()),transformProcess );
                ret[i] =  new org.datavec.api.records.impl.Record(execute.get(0),null);
            }
            else {
                ret[i] = input[i];
            }
        }

        log.info("Post python transform execution");
        return ret;
    }

    @Override
    public void processValidWritable(Writable writable, List<Writable> record, int inputIndex, Object... extraArgs) {
        //no-op here
    }
}
