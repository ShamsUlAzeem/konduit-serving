from konduit.inference import *
from konduit.json_utils import json_with_type
from konduit.server import Server
from konduit.client import Client

from jnius_config import set_classpath
import json
import os

konduit_jar = os.path.join('tests', 'konduit.jar')
set_classpath(konduit_jar)
from jnius import autoclass


TransformProcessBuilder = autoclass('org.datavec.api.transform.TransformProcess$Builder')
TransformProcess = autoclass('org.datavec.api.transform.TransformProcess')

SchemaBuilder = autoclass('org.datavec.api.transform.schema.Schema$Builder')
schema = SchemaBuilder().addColumnString('first').build()
tp = TransformProcessBuilder(schema) \
    .appendStringColumnTransform("first", "two") \
    .build()

tp_json = tp.toJson()
from_json = TransformProcess.fromJson(tp_json)
json_tp = json.dumps(tp_json)
as_python_json = json.loads(tp_json)
transform_process = TransformProcessPipelineStep(
    transform_processes={'default': as_python_json},
    input_names=['default'],
    output_names=['default'],
    input_schemas={'default': ['String']},
    output_schemas={'default': ['String']},
    input_column_names={'default': ['first']},
    output_column_names={'default': ['first']}
)

input_names = ['default']
output_names = ['default']
port = 65322
print('Running on port ' + str(port))
parallel_inference_config = ParallelInferenceConfig(workers=1)
serving_config = ServingConfig(http_port=port,
                               input_data_type='JSON',
                               output_data_type='ARROW',
                               log_timings=True,
                               parallel_inference_config=parallel_inference_config)

inference = InferenceConfiguration(serving_config=serving_config,
                                   pipeline_steps=[transform_process])
as_json = json_with_type(inference)
server = Server(config=inference,
                extra_start_args='-Xmx8g',
                jar_path=konduit_jar)
process = server.start()
print('Process started. Sleeping 10 seconds.')
client = Client(input_names=input_names,
                output_names=output_names,
                return_output_type='ARROW',
                input_type='JSON',
                endpoint_output_type='RAW',
                url='http://localhost:' + str(port))

data_input = {'first': 'value'}

try:
    predicted = client.predict(data_input)
    print(predicted)
    process.wait()
    server.stop()
except Exception as e:
    print(e)
    server.stop()