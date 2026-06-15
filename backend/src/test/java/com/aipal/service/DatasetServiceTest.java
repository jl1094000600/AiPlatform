package com.aipal.service;

import com.aipal.dto.DatasetImportRequest;
import com.aipal.entity.AiDataset;
import com.aipal.mapper.AiDatasetMapper;
import com.aipal.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasetServiceTest {
    @TempDir
    Path storage;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void importsPreviewsAndDeletesManagedCsvFile() throws Exception {
        TenantContext.set(new TenantContext.Context(
                17L, "owner", 3L, "tenant-3", false, Set.of(), Set.of()));
        AiDatasetMapper mapper = mock(AiDatasetMapper.class);
        AtomicReference<AiDataset> stored = new AtomicReference<>();
        when(mapper.insert(any())).thenAnswer(invocation -> {
            AiDataset dataset = invocation.getArgument(0);
            dataset.setId(8L);
            stored.set(dataset);
            return 1;
        });
        when(mapper.selectById(8L)).thenAnswer(invocation -> stored.get());
        when(mapper.deleteById(8L)).thenReturn(1);
        DatasetService service = new DatasetService(
                mapper, new DataGeneratorService(new ObjectMapper()), new ObjectMapper());
        ReflectionTestUtils.setField(service, "storageDirectory", storage.toString());
        DatasetImportRequest request = new DatasetImportRequest();
        request.setDatasetName("customers");
        request.setFormat("csv");
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.csv", "text/csv", "name,age\nAlice,30\nBob,40\n".getBytes());

        AiDataset dataset = service.importDataset(request, file);
        List<Map<String, Object>> preview = service.previewDataset(dataset.getId(), 100);
        Path managedFile = Path.of(dataset.getFilePath());

        assertEquals(2, dataset.getRecordCount());
        assertEquals(17L, dataset.getOwnerId());
        assertEquals("Alice", preview.getFirst().get("name"));
        assertTrue(Files.exists(managedFile));
        assertTrue(service.deleteDataset(dataset.getId()));
        assertFalse(Files.exists(managedFile));
    }

    @Test
    void importsGbkCsvAndLegacyXls() throws Exception {
        TenantContext.set(new TenantContext.Context(
                17L, "owner", 3L, "tenant-3", false, Set.of(), Set.of()));
        AiDatasetMapper mapper = mock(AiDatasetMapper.class);
        AtomicReference<AiDataset> stored = new AtomicReference<>();
        when(mapper.insert(any())).thenAnswer(invocation -> {
            AiDataset dataset = invocation.getArgument(0);
            dataset.setId(stored.get() == null ? 1L : 2L);
            stored.set(dataset);
            return 1;
        });
        when(mapper.selectById(any())).thenAnswer(invocation -> stored.get());
        DatasetService service = service(mapper);

        DatasetImportRequest csvRequest = request("gbk", "csv");
        AiDataset csv = service.importDataset(csvRequest, new MockMultipartFile(
                "file", "gbk.csv", "text/csv", "姓名,城市\n张三,上海\n".getBytes(Charset.forName("GBK"))));
        assertEquals("张三", service.previewDataset(csv.getId(), 10).getFirst().get("姓名"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("name");
            sheet.createRow(1).createCell(0).setCellValue("Alice");
            workbook.write(output);
        }
        AiDataset xls = service.importDataset(request("legacy", "xls"), new MockMultipartFile(
                "file", "legacy.xls", "application/vnd.ms-excel", output.toByteArray()));
        assertEquals("Alice", service.previewDataset(xls.getId(), 10).getFirst().get("name"));
    }

    @Test
    void refusesExternalFilesAndKeepsManagedFileWhenDatabaseDeleteFails() throws Exception {
        TenantContext.set(new TenantContext.Context(
                17L, "owner", 3L, "tenant-3", false, Set.of(), Set.of()));
        AiDatasetMapper mapper = mock(AiDatasetMapper.class);
        AtomicReference<AiDataset> stored = new AtomicReference<>();
        when(mapper.insert(any())).thenAnswer(invocation -> {
            AiDataset dataset = invocation.getArgument(0);
            dataset.setId(8L);
            stored.set(dataset);
            return 1;
        });
        when(mapper.selectById(8L)).thenAnswer(invocation -> stored.get());
        when(mapper.deleteById(8L)).thenReturn(0);
        DatasetService service = service(mapper);
        AiDataset dataset = service.importDataset(request("safe", "csv"), new MockMultipartFile(
                "file", "safe.csv", "text/csv", "name\nAlice\n".getBytes()));
        Path managedFile = Path.of(dataset.getFilePath());

        assertFalse(service.deleteDataset(8L));
        assertTrue(Files.exists(managedFile));

        AiDataset external = new AiDataset();
        external.setFilePath(storage.getParent().resolve("outside.csv").toString());
        external.setFormat("csv");
        assertThrows(IllegalArgumentException.class, () -> service.loadDatasetRecords(external));
    }

    private DatasetService service(AiDatasetMapper mapper) {
        DatasetService service = new DatasetService(
                mapper, new DataGeneratorService(new ObjectMapper()), new ObjectMapper());
        ReflectionTestUtils.setField(service, "storageDirectory", storage.toString());
        return service;
    }

    private DatasetImportRequest request(String name, String format) {
        DatasetImportRequest request = new DatasetImportRequest();
        request.setDatasetName(name);
        request.setFormat(format);
        return request;
    }
}
