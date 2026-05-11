package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.ModelTrainingRequest;
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
    public Result<?> createJob(@RequestBody ModelTrainingRequest request) {
        return Result.success(modelTrainingService.createJob(request));
    }

    @GetMapping("/jobs")
    public Result<?> listJobs() {
        return Result.success(modelTrainingService.listJobs());
    }

    @GetMapping("/jobs/{id}")
    public Result<?> getJob(@PathVariable String id) {
        return Result.success(modelTrainingService.getJob(id));
    }

    @GetMapping("/jobs/{id}/logs")
    public Result<?> getLogs(@PathVariable String id) {
        return Result.success(modelTrainingService.getLogs(id));
    }
}
