package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.DatasetImportRequest;
import com.aipal.entity.AiDataset;
import com.aipal.service.DatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    @GetMapping
    public Result<?> listDatasets(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String format) {
        return Result.success(datasetService.listDatasets(pageNum, pageSize, name, category, format));
    }

    @GetMapping("/{id}")
    public Result<AiDataset> getDataset(@PathVariable Long id) {
        return Result.success(datasetService.getDatasetById(id));
    }

    @PostMapping("/import")
    public Result<AiDataset> importDataset(
            @RequestParam String datasetName,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String fields,
            @RequestParam(required = false) MultipartFile file) {
        try {
            DatasetImportRequest request = buildImportRequest(datasetName, description, category, format, fields);
            return Result.success(datasetService.importDataset(request, file));
        } catch (Exception e) {
            return Result.error("Import failed: " + e.getMessage());
        }
    }

    @PostMapping("/generate")
    public Result<AiDataset> generateDataset(@RequestBody DatasetImportRequest request) {
        return Result.success(datasetService.generateDataset(request));
    }

    @PutMapping("/{id}")
    public Result<Boolean> updateDataset(@RequestBody AiDataset dataset) {
        return Result.success(datasetService.updateDataset(dataset));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteDataset(@PathVariable Long id) {
        return Result.success(datasetService.deleteDataset(id));
    }

    @GetMapping("/formats")
    public Result<List<String>> getSupportedFormats() {
        return Result.success(datasetService.getSupportedFormats());
    }

    private DatasetImportRequest buildImportRequest(String name, String desc, String category, String format, String fieldsJson) {
        DatasetImportRequest request = new DatasetImportRequest();
        request.setDatasetName(name);
        request.setDescription(desc);
        request.setCategory(category);
        request.setFormat(format != null ? format : "json");
        if (fieldsJson != null && !fieldsJson.isEmpty()) {
            request.setFields(parseFields(fieldsJson));
        }
        return request;
    }

    private List<DatasetImportRequest.FieldSchema> parseFields(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, DatasetImportRequest.FieldSchema.class));
        } catch (Exception e) {
            log.error("Failed to parse fields JSON", e);
            return new ArrayList<>();
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatasetController.class);
}