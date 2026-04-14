package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.BizModule;
import com.aipal.service.BizModuleService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/business-modules")
@RequiredArgsConstructor
public class BizModuleController {

    private final BizModuleService bizModuleService;

    @GetMapping
    public Result<Page<BizModule>> listBizModules(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(bizModuleService.listBizModules(pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<BizModule> getBizModule(@PathVariable Long id) {
        return Result.success(bizModuleService.getBizModuleById(id));
    }

    @PostMapping
    public Result<Boolean> createBizModule(@RequestBody BizModule bizModule) {
        return Result.success(bizModuleService.saveBizModule(bizModule));
    }

    @PutMapping("/{id}")
    public Result<Boolean> updateBizModule(@PathVariable Long id, @RequestBody BizModule bizModule) {
        bizModule.setId(id);
        return Result.success(bizModuleService.updateBizModule(bizModule));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteBizModule(@PathVariable Long id) {
        return Result.success(bizModuleService.deleteBizModule(id));
    }
}
