package com.aipal.service;

import com.aipal.dto.DatasetImportRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Slf4j
@Service
public class DataGeneratorService {

    private static final Random random = new Random();

    public enum TemplateType {
        USER_PROFILE,
        TRANSACTION,
        SENSOR_DATA,
        SOCIAL_MEDIA,
        LOGISTICS
    }

    public List<String[]> generateData(List<DatasetImportRequest.FieldSchema> fields, int count) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }

        List<String[]> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String[] row = new String[fields.size()];
            for (int j = 0; j < fields.size(); j++) {
                row[j] = generateFieldValue(fields.get(j));
            }
            result.add(row);
        }
        return result;
    }

    public String generateDataByTemplate(TemplateType template, int count) {
        List<DatasetImportRequest.FieldSchema> fields = getTemplateFields(template);
        List<String[]> data = generateData(fields, count);
        return convertToJson(fields, data);
    }

    private String generateFieldValue(DatasetImportRequest.FieldSchema field) {
        String ruleType = field.getRuleType();
        if (ruleType == null) {
            ruleType = "random";
        }

        return switch (ruleType.toLowerCase()) {
            case "range" -> generateRangeValue(field.getRuleConfig());
            case "enum" -> generateEnumValue(field.getRuleConfig());
            case "pattern" -> generatePatternValue(field.getRuleConfig());
            case "distribution" -> generateDistributionValue(field.getRuleConfig());
            default -> generateDefaultValue(field.getFieldType());
        };
    }

    private String generateRangeValue(String config) {
        if (config == null || config.isEmpty()) {
            return String.valueOf(random.nextInt(100));
        }
        try {
            String[] parts = config.split(",");
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            return String.valueOf(min + random.nextInt(max - min + 1));
        } catch (Exception e) {
            return String.valueOf(random.nextInt(100));
        }
    }

    private String generateEnumValue(String config) {
        if (config == null || config.isEmpty()) {
            return "UNKNOWN";
        }
        String[] values = config.split(",");
        return values[random.nextInt(values.length)].trim();
    }

    private String generatePatternValue(String config) {
        if (config == null || config.isEmpty()) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        return config.replace("UUID", UUID.randomUUID().toString().substring(0, 8))
                .replace("TIME", String.valueOf(System.currentTimeMillis()))
                .replace("RAND", String.valueOf(random.nextInt(10000)));
    }

    private String generateDistributionValue(String config) {
        if (config == null || config.isEmpty()) {
            return String.format("%.2f", random.nextGaussian() * 50 + 100);
        }
        try {
            String[] parts = config.split(",");
            double mean = Double.parseDouble(parts[0].trim());
            double stdDev = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 10;
            return String.format("%.2f", mean + random.nextGaussian() * stdDev);
        } catch (Exception e) {
            return String.format("%.2f", random.nextGaussian() * 50 + 100);
        }
    }

    private String generateDefaultValue(String fieldType) {
        if (fieldType == null) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        return switch (fieldType.toLowerCase()) {
            case "int", "integer" -> String.valueOf(random.nextInt(1000));
            case "long" -> String.valueOf(random.nextLong());
            case "double", "float" -> String.format("%.2f", random.nextDouble() * 1000);
            case "boolean" -> String.valueOf(random.nextBoolean());
            case "date" -> new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date());
            case "datetime" -> new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            default -> UUID.randomUUID().toString().substring(0, 8);
        };
    }

    private List<DatasetImportRequest.FieldSchema> getTemplateFields(TemplateType template) {
        List<DatasetImportRequest.FieldSchema> fields = new ArrayList<>();

        switch (template) {
            case USER_PROFILE -> {
                fields.add(createField("user_id", "string", "pattern", "USR_{UUID}"));
                fields.add(createField("username", "string", "pattern", "user_{RAND}"));
                fields.add(createField("age", "int", "range", "18,80"));
                fields.add(createField("gender", "string", "enum", "male,female,other"));
                fields.add(createField("email", "string", "pattern", "user{RAND}@example.com"));
                fields.add(createField("注册时间", "datetime", "distribution", "2020-01-01,365"));
            }
            case TRANSACTION -> {
                fields.add(createField("txn_id", "string", "pattern", "TXN_{UUID}"));
                fields.add(createField("user_id", "string", "pattern", "USR_{UUID}"));
                fields.add(createField("amount", "double", "distribution", "100,500"));
                fields.add(createField("currency", "string", "enum", "USD,EUR,CNY,JPY"));
                fields.add(createField("txn_time", "datetime", "distribution", "2024-01-01,730"));
                fields.add(createField("status", "string", "enum", "pending,completed,failed,cancelled"));
            }
            case SENSOR_DATA -> {
                fields.add(createField("sensor_id", "string", "pattern", "SN_{UUID}"));
                fields.add(createField("temperature", "double", "distribution", "25,5"));
                fields.add(createField("humidity", "double", "range", "0,100"));
                fields.add(createField("pressure", "double", "distribution", "1013,10"));
                fields.add(createField("timestamp", "datetime", "distribution", "2024-01-01,365"));
            }
            case SOCIAL_MEDIA -> {
                fields.add(createField("post_id", "string", "pattern", "POST_{UUID}"));
                fields.add(createField("user_id", "string", "pattern", "USR_{UUID}"));
                fields.add(createField("content", "string", "pattern", "Post content {RAND}"));
                fields.add(createField("likes", "int", "range", "0,10000"));
                fields.add(createField("shares", "int", "range", "0,1000"));
                fields.add(createField("created_at", "datetime", "distribution", "2024-01-01,365"));
            }
            case LOGISTICS -> {
                fields.add(createField("order_id", "string", "pattern", "ORD_{UUID}"));
                fields.add(createField("warehouse_id", "string", "enum", "WH01,WH02,WH03,WH04,WH05"));
                fields.add(createField("origin", "string", "enum", "北京,上海,广州,深圳,杭州"));
                fields.add(createField("destination", "string", "enum", "成都,重庆,武汉,西安,南京"));
                fields.add(createField("weight", "double", "range", "1,200"));
                fields.add(createField("shipping_time", "datetime", "distribution", "2024-01-01,365"));
            }
        }
        return fields;
    }

    private DatasetImportRequest.FieldSchema createField(String name, String type, String ruleType, String config) {
        DatasetImportRequest.FieldSchema field = new DatasetImportRequest.FieldSchema();
        field.setFieldName(name);
        field.setFieldType(type);
        field.setRuleType(ruleType);
        field.setRuleConfig(config);
        field.setNullable(false);
        return field;
    }

    public String saveGeneratedData(String datasetCode, List<String[]> data, String format) {
        try {
            String outputDir = System.getProperty("java.io.tmpdir") + "/datasets/";
            Files.createDirectories(Paths.get(outputDir));
            String fileName = datasetCode + "." + (format != null ? format : "json");
            Path filePath = Paths.get(outputDir, fileName);

            StringBuilder content = new StringBuilder();
            if ("csv".equalsIgnoreCase(format)) {
                for (String[] row : data) {
                    content.append(String.join(",", row)).append("\n");
                }
            } else {
                content.append(convertToJson(null, data));
            }

            Files.writeString(filePath, content.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Generated data saved to: {}", filePath);
            return filePath.toString();
        } catch (Exception e) {
            log.error("Failed to save generated data", e);
            throw new RuntimeException("Failed to save generated data: " + e.getMessage());
        }
    }

    private String convertToJson(List<DatasetImportRequest.FieldSchema> fields, List<String[]> data) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < data.size(); i++) {
            json.append("{");
            for (int j = 0; j < data.get(i).length; j++) {
                if (j > 0) json.append(",");
                String fieldName = (fields != null && j < fields.size()) ? fields.get(j).getFieldName() : "field_" + j;
                json.append("\"").append(fieldName).append("\":\"").append(data.get(i)[j]).append("\"");
            }
            json.append("}");
            if (i < data.size() - 1) json.append(",");
        }
        json.append("]");
        return json.toString();
    }

    public List<String> getTemplateNames() {
        return Arrays.stream(TemplateType.values())
                .map(Enum::name)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toList());
    }
}