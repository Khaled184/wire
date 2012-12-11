/*
 * Copyright (C) 2012 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.protoss.schema;

import com.squareup.protoss.schema.EnumType.Value;
import com.squareup.protoss.schema.MessageType.Field;
import com.squareup.protoss.schema.MessageType.Label;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;

public final class ProtoSchemaParserTest extends TestCase {
  private static final List<Type> NO_TYPES = Collections.emptyList();
  private static final List<String> NO_STRINGS = Collections.emptyList();

  public void testField() throws Exception {
    Field field = new Field(Label.OPTIONAL, "CType", "ctype", 1, "",
        map("default", "STRING", "deprecated", "true"));
    assertTrue(field.isDeprecated());
    assertEquals("STRING", field.getDefault());
  }

  public void testParseMessageAndFields() throws Exception {
    String proto = ""
        + "message SearchRequest {\n"
        + "  required string query = 1;\n"
        + "  optional int32 page_number = 2;\n"
        + "  optional int32 result_per_page = 3;\n"
        + "}";
    Type expected = new MessageType("SearchRequest", "", Arrays.asList(
        new Field(Label.REQUIRED, "string", "query", 1, "", map()),
        new Field(Label.OPTIONAL, "int32", "page_number", 2, "", map()),
        new Field(Label.OPTIONAL, "int32", "result_per_page", 3, "", map())), NO_TYPES);
    ProtoFile protoFile = new ProtoFile("search.proto", null, NO_STRINGS,
        Arrays.asList(expected), map());
    assertEquals(protoFile, new ProtoSchemaParser("search.proto", proto).readProtoFile());
  }

  public void testParseEnum() throws Exception {
    String proto = ""
        + "/**\n"
        + " * What's on my waffles.\n"
        + " * Also works on pancakes.\n"
        + " */\n"
        + "enum Topping {\n"
        + "  FRUIT = 1;\n"
        + "  CREAM = 2;\n"
        + "\n"
        + "  // Quebec Maple syrup\n"
        + "  SYRUP = 3;\n"
        + "}\n";
    Type expected = new EnumType("Topping", "What's on my waffles.\nAlso works on pancakes.",
        Arrays.asList(new Value("FRUIT", 1, ""), new Value("CREAM", 2, ""),
            new Value("SYRUP", 3, "Quebec Maple syrup")));
    ProtoFile protoFile = new ProtoFile("waffles.proto", null, NO_STRINGS,
        Arrays.asList(expected), map());
    ProtoFile actual = new ProtoSchemaParser("waffles.proto", proto).readProtoFile();
    assertEquals(protoFile, actual);
  }

  public void testPackage() throws Exception {
    String proto = ""
        + "package google.protobuf;\n"
        + "option java_package = \"com.google.protobuf\";\n"
        + "\n"
        + "// The protocol compiler can output a FileDescriptorSet containing the .proto\n"
        + "// files it parses.\n"
        + "message FileDescriptorSet {\n"
        + "}\n";
    Type message = new MessageType("FileDescriptorSet", ""
        + "The protocol compiler can output a FileDescriptorSet containing the .proto\n"
        + "files it parses.", Arrays.<Field>asList(), NO_TYPES);
    ProtoFile expected = new ProtoFile("descriptor.proto", "google.protobuf", NO_STRINGS,
        Arrays.asList(message), map("java_package", "com.google.protobuf"));
    assertEquals(expected, new ProtoSchemaParser("descriptor.proto", proto).readProtoFile());
  }

  public void testNestingInMessage() throws Exception {
    String proto = ""
        + "message FieldOptions {\n"
        + "  optional CType ctype = 1 [default = STRING, deprecated=true];\n"
        + "  enum CType {\n"
        + "    STRING = 0;\n"
        + "  };\n"
        + "  // Clients can define custom options in extensions of this message. See above.\n"
        + "  extensions 1000 to max;\n"
        + "}\n";
    Type enumType = new EnumType("CType", "", Arrays.asList(new Value("STRING", 0, "")));
    Type messageType = new MessageType("FieldOptions", "", Arrays.asList(new Field(Label.OPTIONAL,
        "CType", "ctype", 1, "", map("default", "STRING", "deprecated", "true"))),
        Arrays.asList(enumType));
    ProtoFile expected = new ProtoFile("descriptor.proto", null, NO_STRINGS,
        Arrays.asList(messageType), map());
    ProtoFile actual = new ProtoSchemaParser("descriptor.proto", proto).readProtoFile();
    assertEquals(expected, actual);
  }

  public void testImports() throws Exception {
    String proto = "import \"src/test/resources/unittest_import.proto\";\n";
    ProtoFile expected = new ProtoFile("descriptor.proto", null,
        Arrays.asList("src/test/resources/unittest_import.proto"), NO_TYPES, map());
    assertEquals(expected, new ProtoSchemaParser("descriptor.proto", proto).readProtoFile());
  }

  public void testExtend() throws Exception {
    String proto = ""
        + "extend Foo {\n"
        + "  optional int32 bar = 126;\n"
        + "}";
    ProtoFile expected = new ProtoFile("descriptor.proto", null, NO_STRINGS, NO_TYPES, map());
    assertEquals(expected, new ProtoSchemaParser("descriptor.proto", proto).readProtoFile());
  }

  public void testDefaultFieldWithParen() throws Exception {
    String proto = ""
        + "message Foo {\n"
        + "  optional string claim_token = 2 [(squareup.redacted) = true];\n"
        + "}";
    Type messageType = new MessageType("Foo", "", Arrays.asList(new Field(Label.OPTIONAL, "string",
        "claim_token", 2, "", map("squareup.redacted", "true"))), NO_TYPES);
    ProtoFile expected = new ProtoFile("descriptor.proto", null, NO_STRINGS,
        Arrays.<Type>asList(messageType), map());
    assertEquals(expected, new ProtoSchemaParser("descriptor.proto", proto).readProtoFile());
  }

  public void testService() throws Exception {
    String proto = ""
        + "service SearchService {\n"
        + "  rpc Search (SearchRequest) returns (SearchResponse);"
        + "  rpc Purchase (PurchaseRequest) returns (PurchaseResponse) {\n"
        + "    option (squareup.sake.timeout) = 15; \n"
        + "  }\n"
        + "}";
    ProtoFile expected = new ProtoFile("descriptor.proto", null, NO_STRINGS, NO_TYPES, map());
    assertEquals(expected, new ProtoSchemaParser("descriptor.proto", proto).readProtoFile());
  }

  private Map<String, String> map(String... keysAndValues) {
    Map<String, String> result = new LinkedHashMap<String, String>();
    for (int i = 0; i < keysAndValues.length; i+=2) {
      result.put(keysAndValues[i], keysAndValues[i+1]);
    }
    return result;
  }
}
