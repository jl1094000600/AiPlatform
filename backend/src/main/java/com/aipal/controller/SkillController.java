package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.SkillRequest;
import com.aipal.dto.SkillResponse;
import com.aipal.service.SkillService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {
    private final SkillService skillService;

    @GetMapping
    public Result<Page<SkillResponse>> listSkills(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize,
                                                  @RequestParam(required = false) Integer status) {
        return Result.success(skillService.listSkills(pageNum, pageSize, status));
    }

    @GetMapping("/enabled")
    public Result<List<SkillResponse>> listEnabledSkills() {
        return Result.success(skillService.listEnabledSkills());
    }

    @GetMapping("/{id}")
    public Result<SkillResponse> getSkill(@PathVariable Long id) {
        return Result.success(skillService.getSkill(id));
    }

    @PostMapping
    public Result<SkillResponse> createSkill(@RequestBody SkillRequest request) {
        return Result.success(skillService.createSkill(request));
    }

    @PutMapping("/{id}")
    public Result<SkillResponse> updateSkill(@PathVariable Long id, @RequestBody SkillRequest request) {
        return Result.success(skillService.updateSkill(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteSkill(@PathVariable Long id) {
        return Result.success(skillService.deleteSkill(id));
    }
}
