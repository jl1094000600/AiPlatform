package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.AiModel;
import com.aipal.service.ModelService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/models")
@RequiredArgsConstructor
public class ModelController {
    private final ModelService modelService;

    @GetMapping
    public Result<Page<AiModel>> listModels(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(modelService.listModels(pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<AiModel> getModel(@PathVariable Long id) {
        return Result.success(modelService.getModelById(id));
    }

    @PostMapping
    public Result<Boolean> createModel(@RequestBody AiModel model) {
        return Result.success(modelService.saveModel(model));
    }

    @PutMapping("/{id}")
    public Result<Boolean> updateModel(@PathVariable Long id, @RequestBody AiModel model) {
        model.setId(id);
        return Result.success(modelService.updateModel(model));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteModel(@PathVariable Long id) {
        return Result.success(modelService.deleteModel(id));
    }
}
