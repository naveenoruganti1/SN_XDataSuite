package com.datasuite.xml.controlles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

@RestController
@RequestMapping("/v1/convert")
public class ConversionController {

    private static final Logger logger = LoggerFactory.getLogger(ConversionController.class);

    @PostMapping(value = "/xml_to_json", consumes = MediaType.APPLICATION_XML_VALUE,produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertXMLToJson(@RequestBody String xml) {
        try{

            // 1. Extract root element name dynamically
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));
            String rootElement = doc.getDocumentElement().getNodeName();

            // 2. Convert XML to JsonNode
            XmlMapper xmlMapper = new XmlMapper();
            JsonNode jsonNode  = xmlMapper.readTree(xml.getBytes());

            // 3. Wrap with dynamic root
            ObjectMapper jsonObjectMapper = new ObjectMapper();
            ObjectNode wrappedJson = jsonObjectMapper.createObjectNode();
            wrappedJson.set(rootElement, jsonNode);
            String json = jsonObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrappedJson);
            return ResponseEntity.ok(json);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            logger.error("Error processing XML to JSON", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to convert XML to JSON : "+e.getMessage());
        }
    }

    @PostMapping(value = "/xml_to_yaml", consumes = MediaType.APPLICATION_XML_VALUE,produces = MediaType.APPLICATION_YAML_VALUE)
    public ResponseEntity<String> convertXMLToYaml(@RequestBody String xml){
        try{
            XmlMapper xmlMapper = new XmlMapper();
            JsonNode jsonNode = xmlMapper.readTree(xml.getBytes());
            YAMLMapper yamlObjectMapper = new YAMLMapper();
            String yaml = yamlObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
            return ResponseEntity.ok(yaml);
        } catch (IOException io) {
            logger.error("Error processing XML to YAML", io);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing XML to YAML" + io.getMessage());
        }
    }

    @PostMapping(value = "/xml_to_csv", consumes = MediaType.APPLICATION_XML_VALUE, produces = "application/csv")
    public ResponseEntity<String> convertXMLToCsv(@RequestBody String xml){
        try{
            XmlMapper xmlMapper = new XmlMapper();
            JsonNode root = xmlMapper.readTree(xml.getBytes());
            List<ObjectNode> allRows = new ArrayList<>();

            List<JsonNode> records = extractRecords(root);
            for (JsonNode record : records) {
                if (!record.isObject()) continue;
                processNode((ObjectNode) record, allRows, xmlMapper.createObjectNode());
            }

            if (allRows.isEmpty()) {
                return ResponseEntity.badRequest().body("No valid data to convert.");
            }

            // Extract headers dynamically
            Set<String> headers = new LinkedHashSet<>();
            allRows.forEach(row -> row.fieldNames().forEachRemaining(headers::add));

            CsvSchema.Builder schemaBuilder = CsvSchema.builder().setUseHeader(true);
            headers.forEach(schemaBuilder::addColumn);
            CsvSchema schema = schemaBuilder.build();

            StringWriter writer = new StringWriter();
            CsvMapper csvMapper = new CsvMapper();
            csvMapper.writer(schema).writeValue(writer, allRows);
            return ResponseEntity.ok(writer.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<JsonNode> extractRecords(JsonNode root) {
        List<JsonNode> records = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(records::add);
        } else if (root.isObject()) {
            // Traverse deeply to find arrays of objects
            Deque<JsonNode> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                JsonNode node = stack.pop();
                if (node.isArray() && node.size() > 0 && node.get(0).isObject()) {
                    node.forEach(records::add);
                } else if (node.isObject()) {
                    node.elements().forEachRemaining(stack::push);
                }
            }
        }
        return records;
    }

    private void processNode(ObjectNode inputNode, List<ObjectNode> resultRows, ObjectNode baseRow) {
        Map<String, JsonNode> arrayFields = new LinkedHashMap<>();

        inputNode.fields().forEachRemaining(entry -> {
            if (entry.getValue().isArray() && entry.getValue().size() > 0 &&
                    entry.getValue().get(0).isObject()) {
                arrayFields.put(entry.getKey(), entry.getValue());
            } else if (!entry.getValue().isObject()) {
                baseRow.set(entry.getKey(), entry.getValue());
            }
        });

        if (arrayFields.isEmpty()) {
            resultRows.add(baseRow.deepCopy());
        } else {
            // Explode one array at a time
            for (Map.Entry<String, JsonNode> entry : arrayFields.entrySet()) {
                String arrayName = entry.getKey();
                for (JsonNode item : entry.getValue()) {
                    if (!item.isObject()) continue;
                    ObjectNode row = baseRow.deepCopy();
                    item.fields().forEachRemaining(nestedField ->
                            row.set(arrayName + "." + nestedField.getKey(), nestedField.getValue()));
                    resultRows.add(row);
                }
            }
        }
    }
}