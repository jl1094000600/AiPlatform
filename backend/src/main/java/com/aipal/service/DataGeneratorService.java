package com.aipal.service;

import com.aipal.dto.DatasetImportRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataGeneratorService {

    public static final int MIN_GENERATION_COUNT = 100;
    public static final int MAX_GENERATION_COUNT = 10_000;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    public enum TemplateType {
        USER_PROFILE,
        TRANSACTION,
        SENSOR_DATA,
        SOCIAL_MEDIA,
        LOGISTICS
    }

    public List<String[]> generateData(List<DatasetImportRequest.FieldSchema> fields, int count) {
        validateCount(count);
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("At least one field is required");
        }

        List<String[]> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String[] row = new String[fields.size()];
            for (int j = 0; j < fields.size(); j++) {
                validateField(fields.get(j));
                row[j] = generateFieldValue(fields.get(j), i);
            }
            result.add(row);
        }
        return result;
    }

    public String generateDataByTemplate(TemplateType template, int count) {
        List<DatasetImportRequest.FieldSchema> fields = getTemplateFields(template);
        return convertToJson(fields, generateData(fields, count));
    }

    public String saveGeneratedData(String datasetCode, List<String[]> data, String format) {
        return saveGeneratedData(datasetCode, Collections.emptyList(), data, format);
    }

    public String saveGeneratedData(String datasetCode, List<DatasetImportRequest.FieldSchema> fields,
                                    List<String[]> data, String format) {
        Path outputDir = Path.of(System.getProperty("java.io.tmpdir"), "datasets").toAbsolutePath().normalize();
        return saveGeneratedData(datasetCode, fields, data, format, outputDir);
    }

    public String saveGeneratedData(String datasetCode, List<DatasetImportRequest.FieldSchema> fields,
                                    List<String[]> data, String format, Path outputDirectory) {
        String normalizedFormat = format == null ? "json" : format.toLowerCase(Locale.ROOT);
        if (!List.of("json", "csv").contains(normalizedFormat)) {
            throw new IllegalArgumentException("Generated datasets support only json or csv format");
        }
        try {
            Path outputDir = outputDirectory.toAbsolutePath().normalize();
            Files.createDirectories(outputDir);
            Path filePath = outputDir.resolve(datasetCode + "." + normalizedFormat).normalize();
            if (!filePath.startsWith(outputDir)) {
                throw new IllegalArgumentException("Invalid dataset code");
            }

            String content = "csv".equals(normalizedFormat)
                    ? convertToCsv(fields, data)
                    : convertToJson(fields, data);
            Files.writeString(filePath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return filePath.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save generated data", e);
        }
    }

    String convertToJson(List<DatasetImportRequest.FieldSchema> fields, List<String[]> data) {
        List<Map<String, Object>> records = new ArrayList<>(data.size());
        for (String[] row : data) {
            Map<String, Object> record = new LinkedHashMap<>();
            for (int i = 0; i < row.length; i++) {
                String name = fields != null && i < fields.size() && fields.get(i).getFieldName() != null
                        ? fields.get(i).getFieldName() : "field_" + i;
                record.put(name, row[i]);
            }
            records.add(record);
        }
        try {
            return objectMapper.writeValueAsString(records);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize generated data", e);
        }
    }

    String convertToCsv(List<DatasetImportRequest.FieldSchema> fields, List<String[]> data) {
        StringBuilder csv = new StringBuilder();
        if (fields != null && !fields.isEmpty()) {
            csv.append(fields.stream().map(DatasetImportRequest.FieldSchema::getFieldName)
                    .map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse(""));
            csv.append('\n');
        }
        for (String[] row : data) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) csv.append(',');
                csv.append(escapeCsv(row[i]));
            }
            csv.append('\n');
        }
        return csv.toString();
    }

    private String generateFieldValue(DatasetImportRequest.FieldSchema field, int rowIndex) {
        String fieldType = field.getFieldType() == null ? "string" : field.getFieldType().toLowerCase(Locale.ROOT);
        if ("date".equals(fieldType) || "datetime".equals(fieldType)) {
            return generateDateValue(field, "datetime".equals(fieldType));
        }

        String ruleType = field.getRuleType() == null ? "random" : field.getRuleType().toLowerCase(Locale.ROOT);
        return switch (ruleType) {
            case "range" -> generateRangeValue(field.getRuleConfig());
            case "enum" -> generateEnumValue(field.getRuleConfig());
            case "pattern" -> generatePatternValue(field.getRuleConfig());
            case "fixed" -> field.getRuleConfig() == null ? "" : field.getRuleConfig();
            case "sequence" -> generateSequenceValue(field.getRuleConfig(), rowIndex);
            case "distribution" -> generateDistributionValue(field.getRuleConfig());
            default -> generateDefaultValue(fieldType);
        };
    }

    private String generateSequenceValue(String config, int rowIndex) {
        String[] parts = config == null ? new String[0] : config.split(",", -1);
        long start = parts.length > 0 && !parts[0].isBlank() ? Long.parseLong(parts[0].trim()) : 1L;
        long step = parts.length > 1 && !parts[1].isBlank() ? Long.parseLong(parts[1].trim()) : 1L;
        return String.valueOf(start + step * rowIndex);
    }

    private String generateDateValue(DatasetImportRequest.FieldSchema field, boolean dateTime) {
        String startValue = field.getStartDate();
        String endValue = field.getEndDate();
        if ((startValue == null || endValue == null) && field.getRuleConfig() != null) {
            String[] parts = field.getRuleConfig().split(",", -1);
            if (parts.length >= 2) {
                startValue = parts[0].trim();
                endValue = parts[1].trim();
            }
        }
        if (startValue == null || startValue.isBlank() || endValue == null || endValue.isBlank()) {
            LocalDate today = LocalDate.now();
            startValue = today.minusYears(1).toString();
            endValue = today.toString();
        }

        if (dateTime) {
            LocalDateTime start = parseDateTime(startValue, false);
            LocalDateTime end = parseDateTime(endValue, true);
            if (end.isBefore(start)) throw new IllegalArgumentException("Date range end must not precede start");
            long startSecond = start.toEpochSecond(ZoneOffset.UTC);
            long endSecond = end.toEpochSecond(ZoneOffset.UTC);
            long selected = startSecond == endSecond ? startSecond
                    : ThreadLocalRandom.current().nextLong(startSecond, endSecond + 1);
            return LocalDateTime.ofEpochSecond(selected, 0, ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
        }

        LocalDate start = LocalDate.parse(startValue.substring(0, 10));
        LocalDate end = LocalDate.parse(endValue.substring(0, 10));
        if (end.isBefore(start)) throw new IllegalArgumentException("Date range end must not precede start");
        long days = end.toEpochDay() - start.toEpochDay();
        return start.plusDays(days == 0 ? 0 : ThreadLocalRandom.current().nextLong(days + 1)).toString();
    }

    private LocalDateTime parseDateTime(String value, boolean endOfDay) {
        String normalized = value.trim().replace('T', ' ');
        if (normalized.length() == 10) {
            LocalDate date = LocalDate.parse(normalized);
            return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
        }
        return LocalDateTime.parse(normalized, DATE_TIME_FORMATTER);
    }

    private String generateRangeValue(String config) {
        if (config == null || config.isBlank()) return String.valueOf(ThreadLocalRandom.current().nextInt(100));
        String[] parts = config.split(",", -1);
        if (parts.length != 2) throw new IllegalArgumentException("Range rule requires min,max");
        double min = Double.parseDouble(parts[0].trim());
        double max = Double.parseDouble(parts[1].trim());
        if (max < min) throw new IllegalArgumentException("Range maximum must not be less than minimum");
        double value = min == max ? min : ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
        return min % 1 == 0 && max % 1 == 0 ? String.valueOf(Math.round(value))
                : String.format(Locale.ROOT, "%.2f", value);
    }

    private String generateEnumValue(String config) {
        if (config == null || config.isBlank()) throw new IllegalArgumentException("Enum rule requires values");
        String[] values = config.split(",", -1);
        return values[ThreadLocalRandom.current().nextInt(values.length)].trim();
    }

    private String generatePatternValue(String config) {
        if (config == null || config.isBlank()) return UUID.randomUUID().toString().substring(0, 8);
        return config.replace("{UUID}", UUID.randomUUID().toString().substring(0, 8))
                .replace("UUID", UUID.randomUUID().toString().substring(0, 8))
                .replace("{TIME}", String.valueOf(System.currentTimeMillis()))
                .replace("TIME", String.valueOf(System.currentTimeMillis()))
                .replace("{RAND}", String.valueOf(ThreadLocalRandom.current().nextInt(10_000)))
                .replace("RAND", String.valueOf(ThreadLocalRandom.current().nextInt(10_000)));
    }

    private String generateDistributionValue(String config) {
        double mean = 100;
        double stdDev = 50;
        if (config != null && !config.isBlank()) {
            String[] parts = config.split(",", -1);
            mean = Double.parseDouble(parts[0].trim());
            stdDev = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 10;
            if (stdDev < 0) throw new IllegalArgumentException("Standard deviation must be non-negative");
        }
        return String.format(Locale.ROOT, "%.2f", mean + ThreadLocalRandom.current().nextGaussian() * stdDev);
    }

    private String generateDefaultValue(String fieldType) {
        return switch (fieldType) {
            case "int", "integer" -> String.valueOf(ThreadLocalRandom.current().nextInt(1000));
            case "long" -> String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000));
            case "double", "float", "decimal" -> String.format(Locale.ROOT, "%.2f",
                    ThreadLocalRandom.current().nextDouble(1000));
            case "boolean" -> String.valueOf(ThreadLocalRandom.current().nextBoolean());
            default -> UUID.randomUUID().toString().substring(0, 8);
        };
    }

    private List<DatasetImportRequest.FieldSchema> getTemplateFields(TemplateType template) {
        if (template == null) throw new IllegalArgumentException("Template is required");
        List<DatasetImportRequest.FieldSchema> fields = new ArrayList<>();
        switch (template) {
            case USER_PROFILE -> {
                fields.add(createField("user_id", "string", "pattern", "USR_{UUID}"));
                fields.add(createField("username", "string", "pattern", "user_{RAND}"));
                fields.add(createField("age", "int", "range", "18,80"));
                fields.add(createField("gender", "string", "enum", "male,female,other"));
                fields.add(createField("email", "string", "pattern", "user{RAND}@example.com"));
                fields.add(createField("registered_at", "datetime", "range", "2020-01-01,2026-12-31"));
            }
            case TRANSACTION -> {
                fields.add(createField("txn_id", "string", "pattern", "TXN_{UUID}"));
                fields.add(createField("user_id", "string", "pattern", "USR_{UUID}"));
                fields.add(createField("amount", "double", "distribution", "100,50"));
                fields.add(createField("currency", "string", "enum", "USD,EUR,CNY,JPY"));
                fields.add(createField("txn_time", "datetime", "range", "2024-01-01,2026-12-31"));
                fields.add(createField("status", "string", "enum", "pending,completed,failed,cancelled"));
            }
            case SENSOR_DATA -> {
                fields.add(createField("sensor_id", "string", "pattern", "SN_{UUID}"));
                fields.add(createField("temperature", "double", "distribution", "25,5"));
                fields.add(createField("humidity", "double", "range", "0,100"));
                fields.add(createField("pressure", "double", "distribution", "1013,10"));
                fields.add(createField("timestamp", "datetime", "range", "2024-01-01,2026-12-31"));
            }
            case SOCIAL_MEDIA -> {
                fields.add(createField("post_id", "string", "pattern", "POST_{UUID}"));
                fields.add(createField("user_id", "string", "pattern", "USR_{UUID}"));
                fields.add(createField("content", "string", "pattern", "Post content {RAND}"));
                fields.add(createField("likes", "int", "range", "0,10000"));
                fields.add(createField("shares", "int", "range", "0,1000"));
                fields.add(createField("created_at", "datetime", "range", "2024-01-01,2026-12-31"));
            }
            case LOGISTICS -> {
                fields.add(createField("order_id", "string", "pattern", "ORD_{UUID}"));
                fields.add(createField("warehouse_id", "string", "enum", "WH01,WH02,WH03,WH04,WH05"));
                fields.add(createField("origin", "string", "enum", "Beijing,Shanghai,Guangzhou,Shenzhen,Hangzhou"));
                fields.add(createField("destination", "string", "enum", "Chengdu,Chongqing,Wuhan,Xian,Nanjing"));
                fields.add(createField("weight", "double", "range", "1,200"));
                fields.add(createField("shipping_time", "datetime", "range", "2024-01-01,2026-12-31"));
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

    private void validateCount(int count) {
        if (count < MIN_GENERATION_COUNT || count > MAX_GENERATION_COUNT) {
            throw new IllegalArgumentException("Generation count must be between 100 and 10000");
        }
    }

    private void validateField(DatasetImportRequest.FieldSchema field) {
        if (field == null || field.getFieldName() == null || field.getFieldName().isBlank()) {
            throw new IllegalArgumentException("Field name is required");
        }
    }

    private String escapeCsv(String value) {
        String safe = value == null ? "" : value;
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    public List<String> getTemplateNames() {
        return Arrays.stream(TemplateType.values()).map(Enum::name).map(String::toLowerCase).toList();
    }
}
