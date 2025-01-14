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

package ai.konduit.serving.util.python;

import org.datavec.api.transform.ColumnType;
import org.datavec.api.transform.metadata.BooleanMetaData;
import org.datavec.api.transform.schema.Schema;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * List of utilities for executing python transforms.
 *
 * @author Adam Gibson
 */
public class PythonUtils {

    /**
     * Create a {@link Schema}
     * from {@link PythonVariables}.
     * Types are mapped to types of the same name.
     * @param input the input {@link PythonVariables}
     * @return the output {@link Schema}
     */
    public static Schema fromPythonVariables(PythonVariables input) {
        Schema.Builder schemaBuilder = new Schema.Builder();
        Preconditions.checkState(input.getVariables() != null && input.getVariables().length > 0,"Input must have variables. Found none.");
        for(Map.Entry<String,PythonVariables.Type> entry : input.getVars().entrySet()) {
                 switch(entry.getValue()) {
                     case INT:
                         schemaBuilder.addColumnInteger(entry.getKey());
                         break;
                     case STR:
                         schemaBuilder.addColumnString(entry.getKey());
                         break;
                     case FLOAT:
                         schemaBuilder.addColumnFloat(entry.getKey());
                         break;
                     case NDARRAY:
                         schemaBuilder.addColumnNDArray(entry.getKey(),null);
                         break;
                     case BOOL:
                         schemaBuilder.addColumn(new BooleanMetaData(entry.getKey()));
                 }
        }

        return schemaBuilder.build();
    }

    /**
     * Create a {@link Schema} from an input
     * {@link PythonVariables}
     * Types are mapped to types of the same name
     * @param input the input schema
     * @return the output python variables.
     */
    public static PythonVariables fromSchema(Schema input) {
        PythonVariables ret = new PythonVariables();
        for(int i = 0; i < input.numColumns(); i++) {
            String currColumnName = input.getName(i);
            ColumnType columnType = input.getType(i);
            switch(columnType) {
                case NDArray:
                    ret.add(currColumnName, PythonVariables.Type.NDARRAY);
                    break;
                case Boolean:
                    ret.add(currColumnName, PythonVariables.Type.BOOL);
                    break;
                case Categorical:
                case String:
                    ret.add(currColumnName, PythonVariables.Type.STR);
                    break;
                case Double:
                case Float:
                    ret.add(currColumnName, PythonVariables.Type.FLOAT);
                    break;
                case Integer:
                case Long:
                    ret.add(currColumnName, PythonVariables.Type.INT);
                    break;
                case Bytes:
                    break;
                case Time:
                    throw new UnsupportedOperationException("Unable to process dates with python yet.");
            }
        }

        return ret;
    }
    public static NumpyArray mapToNumpyArray(Map map){
        String dtypeName = (String)map.get("dtype");
        DataType dtype;
        if (dtypeName.equals("float64")){
            dtype = DataType.DOUBLE;
        }
        else if (dtypeName.equals("float32")){
            dtype = DataType.FLOAT;
        }
        else if (dtypeName.equals("int16")){
            dtype = DataType.SHORT;
        }
        else if (dtypeName.equals("int32")){
            dtype = DataType.INT;
        }
        else if (dtypeName.equals("int64")){
            dtype = DataType.LONG;
        }
        else{
            throw new RuntimeException("Unsupported array type " + dtypeName + ".");
        }
        List shapeList = (List)map.get("shape");
        long[] shape = new long[shapeList.size()];
        for (int i = 0; i < shape.length; i++) {
            shape[i] = (Long)shapeList.get(i);
        }

        List strideList = (List)map.get("shape");
        long[] stride = new long[strideList.size()];
        for (int i = 0; i < stride.length; i++) {
            stride[i] = (Long)strideList.get(i);
        }
        long address = (Long)map.get("address");
        NumpyArray numpyArray = new NumpyArray(address, shape, stride, true,dtype);
        return numpyArray;
    }

    public static PythonVariables expandInnerDict(PythonVariables pyvars, String key){
        Map dict = pyvars.getDictValue(key);
        String[] keys = (String[])dict.keySet().toArray(new String[dict.keySet().size()]);
        PythonVariables pyvars2 = new PythonVariables();
        for (String subkey: keys){
            Object value = dict.get(subkey);
            if (value instanceof Map){
                Map map = (Map)value;
                if (map.containsKey("_is_numpy_array")){
                    pyvars2.addNDArray(subkey, mapToNumpyArray(map));

                }
                else{
                    pyvars2.addDict(subkey, (Map)value);
                }

            }
            else if (value instanceof List){
                pyvars2.addList(subkey, ((List) value).toArray());
            }
            else if (value instanceof String){
                System.out.println((String)value);
                pyvars2.addStr(subkey, (String) value);
            }
            else if (value instanceof Integer || value instanceof Long) {
                Number number = (Number) value;
                pyvars2.addInt(subkey, number.intValue());
            }
            else if (value instanceof Float || value instanceof Double) {
                Number number = (Number) value;
                pyvars2.addFloat(subkey, number.doubleValue());
            }
            else if (value instanceof NumpyArray){
                pyvars2.addNDArray(subkey, (NumpyArray)value);
            }
            else if (value == null){
                pyvars2.addStr(subkey, "None"); // FixMe
            }
            else{
                throw new RuntimeException("Unsupported type!" + value);
            }
        }
        return pyvars2;
    }

    public static long[] jsonArrayToLongArray(JSONArray jsonArray){
        long[] longs = new long[jsonArray.size()];
        for (int i=0; i<longs.length; i++){
            longs[i] = (Long)jsonArray.get(i);
        }
        return longs;
    }

    public static Map<String, Object> toMap(JSONObject jsonobj)  {
        Map<String, Object> map = new HashMap<>();
        String[] keys = (String[])jsonobj.keySet().toArray(new String[jsonobj.keySet().size()]);
        for (String key: keys){
            Object value = jsonobj.get(key);
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                JSONObject jsonobj2 = (JSONObject)value;
                if (jsonobj2.containsKey("_is_numpy_array")){
                    value = jsonToNumpyArray(jsonobj2);
                }
                else{
                    value = toMap(jsonobj2);
                }

            }

            map.put(key, value);
        }   return map;
    }


    public static List<Object> toList(JSONArray array) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                JSONObject jsonobj2 = (JSONObject) value;
                if (jsonobj2.containsKey("_is_numpy_array")) {
                    value = jsonToNumpyArray(jsonobj2);
                } else {
                    value = toMap(jsonobj2);
                }
            }
            list.add(value);
        }
        return list;
    }


    private static NumpyArray jsonToNumpyArray(JSONObject map){
        String dtypeName = (String)map.get("dtype");
        DataType dtype;
        if (dtypeName.equals("float64")){
            dtype = DataType.DOUBLE;
        }
        else if (dtypeName.equals("float32")){
            dtype = DataType.FLOAT;
        }
        else if (dtypeName.equals("int16")){
            dtype = DataType.SHORT;
        }
        else if (dtypeName.equals("int32")){
            dtype = DataType.INT;
        }
        else if (dtypeName.equals("int64")){
            dtype = DataType.LONG;
        }
        else{
            throw new RuntimeException("Unsupported array type " + dtypeName + ".");
        }
        List shapeList = (List)map.get("shape");
        long[] shape = new long[shapeList.size()];
        for (int i = 0; i < shape.length; i++) {
            shape[i] = (Long)shapeList.get(i);
        }

        List strideList = (List)map.get("shape");
        long[] stride = new long[strideList.size()];
        for (int i = 0; i < stride.length; i++) {
            stride[i] = (Long)strideList.get(i);
        }
        long address = (Long)map.get("address");
        NumpyArray numpyArray = new NumpyArray(address, shape, stride, true,dtype);
        return numpyArray;
    }


}
