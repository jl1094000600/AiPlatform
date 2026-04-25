package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.service.DataGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-generator")
@RequiredArgsConstructor
public class DataGeneratorController {

    private final DataGeneratorService dataGeneratorService;

    @GetMapping("/templates")
    public Result<List<String>> getTemplateNames() {
        return Result.success(dataGeneratorService.getTemplateNames());
    }

    @PostMapping("/generate")
    public Result<String> generateData(@RequestBody Map<String, Object> request) {
        try {
            String template = extractTemplate(request);
            Integer count = request.get("count") != null ? Integer.parseInt(request.get("count").toString()) : 100;
            DataGeneratorService.TemplateType templateType = DataGeneratorService.TemplateType.valueOf(template.toUpperCase());
            String data = dataGeneratorService.generateDataByTemplate(templateType, count);
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("Generation failed: " + e.getMessage());
        }
    }

    private String extractTemplate(Map<String, Object> request) {
        if (request.containsKey("template") && request.get("template") != null) {
            return request.get("template").toString();
        }
        if (request.containsKey("templateId") && request.get("templateId") != null) {
            Object templateId = request.get("templateId");
            if (templateId instanceof Number) {
                int id = ((Number) templateId).intValue();
                return getTemplateById(id);
            }
            return getTemplateById(Integer.parseInt(templateId.toString()));
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