package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.ModelTrainingDatasetImportRequest;
import com.aipal.dto.ModelTrainingDatasetMockRequest;
import com.aipal.dto.ModelTrainingDatasetSaveRequest;
import com.aipal.dto.ModelTrainingRequest;
import com.aipal.security.RequirePermission;
import com.aipal.service.ModelTrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/model-training")
@RequiredArgsConstructor
public class ModelTrainingController {

    private final ModelTrainingService modelTrainingService;

    @PostMapping("/jobs")
    @RequirePermission("model:update")
    public Result<?> createJob(@RequestBody ModelTrainingRequest request) {
        return Result.success(modelTrainingService.createJob(request));
    }

    @GetMapping("/jobs")
    @RequirePermission("model:list")
    public Result<?> listJobs() {
        return Result.success(modelTrainingService.listJobs());
    }

    @GetMapping("/jobs/{id}")
    @RequirePermission("model:list")
    public Result<?> getJob(@PathVariable String id) {
        return Result.success(modelTrainingService.getJob(id));
    }

    @GetMapping("/jobs/{id}/logs")
    @RequirePermission("model:list")
    public Result<?> getLogs(@PathVariable String id) {
        return Result.success(modelTrainingService.getLogs(id));
    }

    @GetMapping("/datasets")
    @RequirePermission("model:list")
    public Result<?> listDatasets() {
        return Result.success(modelTrainingService.listDatasets());
    }

    @PostMapping("/datasets/import")
    @RequirePermission("model:update")
    public Result<?> importDataset(@RequestBody ModelTrainingDatasetImportRequest request) {
        return Result.success(modelTrainingService.importDataset(request));
    }

    @PostMapping("/datasets/mock")
    @RequirePermission("model:update")
    public Result<?> previewMockDataset(@RequestBody ModelTrainingDatasetMockRequest request) {
        return Result.success(modelTrainingService.previewMockDataset(request));
    }

    @PostMapping("/datasets/mock/preview")
    @RequirePermission("model:update")
    public Result<?> previewMockDatasetV2(@RequestBody ModelTrainingDatasetMockRequest request) {
        return Result.success(modelTrainingService.previewMockDataset(request));
    }

    @PostMapping("/datasets/save")
    @RequirePermission("model:update")
    public Result<?> saveDataset(@RequestBody ModelTrainingDatasetSaveRequest request) {
        return Result.success(modelTrainingService.saveDataset(request));
    }
}
