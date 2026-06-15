package com.aipal.service;

import com.aipal.dto.DatasetImportRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataGeneratorServiceTest {
    private final DataGeneratorService service = new DataGeneratorService(new ObjectMapper());

    @Test
    void generatesExactRequestedCountWithinBounds() {
        DatasetImportRequest.FieldSchema field = field("name", "string", "enum", "Alice,Bob");

        assertEquals(100, service.generateData(List.of(field), 100).size());
        assertEquals(10_000, service.generateData(List.of(field), 10_000).size());
        assertThrows(IllegalArgumentException.class, () -> service.generateData(List.of(field), 99));
        assertThrows(IllegalArgumentException.class, () -> service.generateData(List.of(field), 10_001));
    }

    @Test
    void escapesCsvAndKeepsGeneratedDatesInsideRange() {
        DatasetImportRequest.FieldSchema date = field("createdAt", "date", "date", null);
        date.setStartDate("2026-01-01");
        date.setEndDate("2026-01-03");
        List<String[]> rows = service.generateData(List.of(date), 100);
        for (String[] row : rows) {
            LocalDate value = LocalDate.parse(row[0]);
            assertTrue(!value.isBefore(LocalDate.of(2026, 1, 1)));
            assertTrue(!value.isAfter(LocalDate.of(2026, 1, 3)));
        }

        String csv = service.convertToCsv(
                List.of(field("message", "string", "enum", "unused")),
                List.<String[]>of(new String[]{"hello, \"world\""}));
        assertTrue(csv.contains("\"hello, \"\"world\"\"\""));
    }

    private DatasetImportRequest.FieldSchema field(String name, String type, String rule, String config) {
        DatasetImportRequest.FieldSchema field = new DatasetImportRequest.FieldSchema();
        field.setFieldName(name);
        field.setFieldType(type);
        field.setRuleType(rule);
        field.setRuleConfig(config);
        return field;
    }
}
