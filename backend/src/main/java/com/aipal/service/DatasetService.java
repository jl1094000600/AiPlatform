package com.aipal.service;

import com.aipal.dto.DatasetImportRequest;
import com.aipal.entity.AiDataset;
import com.aipal.mapper.AiDatasetMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetService {

    private final AiDatasetMapper datasetMapper;
    private final DataGeneratorService dataGeneratorService;

    private static final String[] SUPPORTED_FORMATS = {"json", "csv", "xml", "xlsx", "txt", "parquet"};

    public Page<AiDataset> listDatasets(int pageNum, int pageSize, String name, String category, String format) {
        Page<AiDataset> page = new Page<>(pageNum, pageSize);
        QueryWrapper<AiDataset> wrapper = new QueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            wrapper.like("dataset_name", name);
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq("category", category);
        }
        if (format != null && !format.isEmpty()) {
            wrapper.eq("format", format);
        }
        wrapper.eq("status", 1);
        return datasetMapper.selectPage(page, wrapper);
    }

    public AiDataset getDatasetById(Long id) {
        return datasetMapper.selectById(id);
    }

    public AiDataset importDataset(DatasetImportRequest request, MultipartFile file) throws IOException {
        validateFormat(request.getFormat());

        AiDataset dataset = new AiDataset();
        dataset.setDatasetCode(generateDatasetCode());
        dataset.setDatasetName(request.getDatasetName());
        dataset.setDescription(request.getDescription());
        dataset.setCategory(request.getCategory());
        dataset.setFormat(request.getFormat());
        dataset.setOwnerId(1L);
        dataset.setStatus(1);
        dataset.setCreateTime(LocalDateTime.now());
        dataset.setUpdateTime(LocalDateTime.now());

        if (file != null && !file.isEmpty()) {
            String filePath = saveFile(file, request.getFormat());
            dataset.setFilePath(filePath);
            dataset.setSize(file.getSize());
        }

        String fieldSchema = convertFieldsToSchema(request.getFields());
        dataset.setFieldSchema(fieldSchema);

        int recordCount = request.getFields() != null ? request.getFields().size() * 100 : 0;
        dataset.setRecordCount(recordCount);

        datasetMapper.insert(dataset);
        log.info("Dataset imported: {}", dataset.getDatasetCode());
        return dataset;
    }

    public AiDataset generateDataset(DatasetImportRequest request) {
        AiDataset dataset = new AiDataset();
        dataset.setDatasetCode(generateDatasetCode());
        dataset.setDatasetName(request.getDatasetName());
        dataset.setDescription(request.getDescription());
        dataset.setCategory(request.getCategory());
        dataset.setFormat(request.getFormat() != null ? request.getFormat() : "json");
        dataset.setOwnerId(1L);
        dataset.setStatus(1);
        dataset.setCreateTime(LocalDateTime.now());
        dataset.setUpdateTime(LocalDateTime.now());

        String fieldSchema = convertFieldsToSchema(request.getFields());
        dataset.setFieldSchema(fieldSchema);

        List<String[]> generatedData = dataGeneratorService.generateData(request.getFields(), 100);
        dataset.setRecordCount(generatedData.size());

        String filePath = dataGeneratorService.saveGeneratedData(dataset.getDatasetCode(), generatedData, request.getFormat());
        dataset.setFilePath(filePath);
        try {
            dataset.setSize(Files.size(Paths.get(filePath)));
        } catch (IOException e) {
            dataset.setSize(0L);
            log.warn("Failed to get file size: {}", e.getMessage());
        }

        datasetMapper.insert(dataset);
        log.info("Dataset generated: {}", dataset.getDatasetCode());
        return dataset;
    }

    public boolean updateDataset(AiDataset dataset) {
        dataset.setUpdateTime(LocalDateTime.now());
        return datasetMapper.updateById(dataset) > 0;
    }

    public boolean deleteDataset(Long id) {
        return datasetMapper.deleteById(id) > 0;
    }

    public List<String> getSupportedFormats() {
        return List.of(SUPPORTED_FORMATS);
    }

    private void validateFormat(String format) {
        if (format == null || !List.of(SUPPORTED_FORMATS).contains(format.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported format: " + format + ". Supported: " + List.of(SUPPORTED_FORMATS));
        }
    }

    private String generateDatasetCode() {
        return "DS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String saveFile(MultipartFile file, String format) throws IOException {
        String uploadDir = System.getProperty("java.io.tmpdir") + "/datasets/";
        Files.createDirectories(Paths.get(uploadDir));
        String fileName = UUID.randomUUID() + "." + format;
        Path filePath = Paths.get(uploadDir, fileName);
        file.transferTo(filePath);
        return filePath.toString();
    }

    private String convertFieldsToSchema(List<DatasetImportRequest.FieldSchema> fields) {
        if (fields == null || fields.isEmpty()) {
            return "[]";
        }
        return fields.stream()
                .map(f -> String.format("{\"fieldName\":\"%s\",\"fieldType\":\"%s\",\"ruleType\":\"%s\"}",
                        f.getFieldName(), f.getFieldType(), f.getRuleType()))
                .collect(Collectors.joining(",", "[", "]"));
    }
}