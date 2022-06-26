/*
 *
 *  * Copyright [2022] [DMetaSoul Team]
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.apache.flink.lakesoul.tools;

import com.alibaba.fastjson.JSONObject;
import com.dmetasoul.lakesoul.meta.DataTypeUtil;
import com.dmetasoul.lakesoul.meta.entity.TableInfo;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.api.Schema.Builder;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.TableException;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.catalog.CatalogPartitionSpec;
import org.apache.flink.table.catalog.CatalogTable;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.types.DataType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import scala.collection.JavaConverters;
import scala.collection.Map$;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.flink.lakesoul.tools.LakeSoulSinkOptions.CDC_CHANGE_COLUMN;
import static org.apache.flink.lakesoul.tools.LakeSoulSinkOptions.RECORD_KEY_NAME;

public class FlinkUtil {
  private FlinkUtil() {
  }

  private static final String NOT_NULL = " NOT NULL";

  public static String convert(TableSchema schema) {
    return schema.toRowDataType().toString();
  }

  public static String getRangeValue(CatalogPartitionSpec cps) {
    return "Null";
  }

  public static Boolean isLakesoulCdcTable(Map<String, String> options) {
    return false;
  }

  public static Boolean isLakesoulCdcTable(Configuration options) {
    return false;
  }

  public static StructType toSparkSchema(TableSchema tsc, Boolean isCdc) {
    StructType stNew = new StructType();

    for (int i = 0; i < tsc.getFieldCount(); i++) {
      String name = tsc.getFieldName(i).get();
      DataType dt = tsc.getFieldDataType(i).get();
      String dtName = dt.getLogicalType().getTypeRoot().name();
      stNew = stNew.add(name, DataTypeUtil.convertDatatype(dtName), dt.getLogicalType().isNullable());
    }

    return stNew;
  }

  public static StringData RowKindToOperation(String rowKind) {
    if ("+I".equals(rowKind)) {
      return StringData.fromString("insert");
    }
    if ("-U".equals(rowKind)) {
      return StringData.fromString("update");
    }
    if ("+U".equals(rowKind)) {
      return StringData.fromString("update");
    }
    if ("-D".equals(rowKind)) {
      return StringData.fromString("delete");
    }
    return null;
  }

  public static CatalogTable toFlinkCatalog(TableInfo tableInfo) {
    String tableSchema = tableInfo.getTableSchema();
    StructType struct = (StructType) org.apache.spark.sql.types.DataType.fromJson(tableSchema);
    Builder bd = Schema.newBuilder();
    JSONObject properties = tableInfo.getProperties();
    String lakesoulCdcColumnName = properties.getString(CDC_CHANGE_COLUMN);
    boolean contains = (lakesoulCdcColumnName == null || "".equals(lakesoulCdcColumnName));
    String hashColumn = properties.getString(RECORD_KEY_NAME);
    //todo
    for (StructField sf : struct.fields()) {
      if (contains && sf.name().equals(lakesoulCdcColumnName)) {
        continue;
      }
      String tyname = DataTypeUtil.convertToFlinkDatatype(sf.dataType().typeName());
      if (!sf.nullable()) {
        tyname += NOT_NULL;
      }
      bd = bd.column(sf.name(), tyname);
    }
    bd.primaryKey(Arrays.asList(hashColumn.split(",")));
    String partitionKeys = tableInfo.getPartitions();
    List<String> partitionKey = Arrays.stream(partitionKeys.split(";")).collect(Collectors.toList());
    HashMap<String, String> conf = new HashMap<>();
    properties.forEach((key, value) -> conf.put(key, (String) value));
    return CatalogTable.of(bd.build(), "", partitionKey, conf);
  }

  public static String stringListToString(List<String> list) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < list.size(); i++) {
      builder.append(list.get(i)).append(",");
    }
    return builder.deleteCharAt(builder.length() - 1).toString();
  }

  public static String generatePartitionPath(LinkedHashMap<String, String> partitionSpec) {
    if (partitionSpec.isEmpty()) {
      return "";
    }
    StringBuilder suffixBuf = new StringBuilder();
    int i = 0;
    for (Map.Entry<String, String> e : partitionSpec.entrySet()) {
      if (i > 0) {
        suffixBuf.append(",");
      }
      suffixBuf.append(escapePathName(e.getKey()));
      suffixBuf.append('=');
      suffixBuf.append(escapePathName(e.getValue()));
      i++;
    }
    return suffixBuf.toString();
  }

  public static scala.collection.immutable.Map<String, String> getScalaMap(Map<String, String> javaMap) {
    scala.collection.mutable.Map<String, String> scalaMap = JavaConverters.mapAsScalaMap(javaMap);
    Object objTest = Map$.MODULE$.<String, String>newBuilder().$plus$plus$eq(scalaMap.toSeq());
    Object resultTest = ((scala.collection.mutable.Builder) objTest).result();
    return (scala.collection.immutable.Map<String, String>) (scala.collection.immutable.Map) resultTest;
  }

  private static String escapePathName(String path) {
    if (path == null || path.length() == 0) {
      throw new TableException("Path should not be null or empty: " + path);
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      sb.append(c);
    }
    return sb.toString();
  }

}
