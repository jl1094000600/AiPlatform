package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.DataGenerationRequest;
import com.aipal.security.RequirePermission;
import com.aipal.service.DataGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/data-generator")
@RequiredArgsConstructor
public class DataGeneratorController {

    private final DataGeneratorService dataGeneratorService;

    @GetMapping("/templates")
    @RequirePermission("benchmark:view")
    public Result<List<String>> getTemplateNames() {
        return Result.success(dataGeneratorService.getTemplateNames());
    }

    @PostMapping("/generate")
    @RequirePermission("benchmark:run")
    public Result<String> generateData(@RequestBody DataGenerationRequest request) {
        try {
            String template = extractTemplate(request);
            int count = request.getCount() == null ? DataGeneratorService.MIN_GENERATION_COUNT : request.getCount();
            DataGeneratorService.TemplateType templateType = DataGeneratorService.TemplateType.valueOf(template.toUpperCase());
            String data = dataGeneratorService.generateDataByTemplate(templateType, count);
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("Generation failed: " + e.getMessage());
        }
    }

    private String extractTemplate(DataGenerationRequest request) {
        if (request.getTemplate() != null && !request.getTemplate().isBlank()) {
            return request.getTemplate();
        }
        if (request.getTemplateId() != null) {
            return getTemplateById(request.getTemplateId());
        }
        throw new IllegalArgumentException("template or templateId is required");
    }

    private String getTemplateById(int id) {
        List<String> templates = dataGeneratorService.getTemplateNames();
        if (id > 0 && id <= templates.size()) {
            return templates.get(id - 1);
        }
        throw new IllegalArgumentException("Invalid templateId: " + id);
    }
}
