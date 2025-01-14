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

package ai.konduit.serving.verticles.python;

import ai.konduit.serving.InferenceConfiguration;
import ai.konduit.serving.config.*;
import ai.konduit.serving.model.PythonConfig;
import ai.konduit.serving.pipeline.PythonPipelineStep;
import ai.konduit.serving.util.python.PythonVariables;
import ai.konduit.serving.verticles.inference.InferenceVerticle;
import ai.konduit.serving.verticles.numpy.tensorflow.BaseMultiNumpyVerticalTest;
import com.jayway.restassured.specification.RequestSpecification;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.Arrays;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(VertxUnitRunner.class)
@NotThreadSafe
public class TestPythonJsonInput extends BaseMultiNumpyVerticalTest {

    @Override
    public Class<? extends AbstractVerticle> getVerticalClazz() {
        return InferenceVerticle.class;
    }

    @After
    public void after(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Override
    public Handler<HttpServerRequest> getRequest() {

        return req -> {
            //should be json body of classification
            req.bodyHandler(body -> {
                System.out.println(body.toJson());
                System.out.println("Finish body" + body);
            });

            req.exceptionHandler(exception -> context.fail(exception));
        };
    }

    @Override
    public JsonObject getConfigObject() throws Exception {
        ParallelInferenceConfig parallelInferenceConfig = ParallelInferenceConfig.defaultConfig();

        PythonConfig pythonConfig = PythonConfig.builder()
                .pythonCode("first += 2")
                .pythonInput("first", PythonVariables.Type.INT.name())
                .pythonOutput("first", PythonVariables.Type.INT.name())
                  .returnAllInputs(false)
                .build();

        PythonPipelineStep pythonStepConfig = PythonPipelineStep.builder()
                .inputName("default")
                .inputColumnName("default", Arrays.asList(new String[]{"first"}))
                .inputSchema("default", new SchemaType[]{SchemaType.Integer})
                .outputColumnName("default", Arrays.asList(new String[]{"output"}))
                .outputSchema("default", new SchemaType[]{SchemaType.Integer})
                .pythonConfig("default",pythonConfig)
                .build();


        ServingConfig servingConfig = ServingConfig.builder()
                .httpPort(port)
                .inputDataType(Input.DataType.NUMPY)
                .predictionType(Output.PredictionType.CLASSIFICATION)
                .parallelInferenceConfig(parallelInferenceConfig)
                .build();


        InferenceConfiguration inferenceConfiguration = InferenceConfiguration.builder()
                .pipelineStep(pythonStepConfig)
                .servingConfig(servingConfig)
                .build();


        return new JsonObject(inferenceConfiguration.toJson());
    }


    @Test(timeout = 60000)
    public void testInferenceResult(TestContext context) throws Exception {
        this.context = context;

        RequestSpecification requestSpecification = given();
        requestSpecification.port(port);
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("first",2);
        requestSpecification.body(jsonObject.encode().getBytes());
        requestSpecification.header("Content-Type","application/json");
        String body = requestSpecification.when()
                .expect().statusCode(200)
                .body(not(isEmptyOrNullString()))
                .post("/raw/dictionary").then()
                .extract()
        .body().asString();
        JsonArray arr = new JsonArray(body);
        JsonObject jsonObject1 = arr.getJsonObject(0);
        assertTrue(jsonObject1.containsKey("output"));
        assertEquals(4,jsonObject1.getInteger("output"),1e-1);

    }
}
