package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.AiTtsConfig;
import com.aipal.security.RequirePermission;
import com.aipal.service.TtsConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tts/config")
@RequiredArgsConstructor
public class TtsConfigController {

    private final TtsConfigService ttsConfigService;

    @GetMapping
    @RequirePermission("tts:manage")
    public Result<List<AiTtsConfig>> listConfigs() {
        return Result.success(ttsConfigService.getAllConfigs());
    }

    @GetMapping("/{key}")
    @RequirePermission("tts:manage")
    public Result<AiTtsConfig> getConfig(@PathVariable String key) {
        return Result.success(ttsConfigService.getConfigByKey(key));
    }

    @PostMapping
    @RequirePermission("tts:manage")
    public Result<Boolean> saveConfig(@RequestBody AiTtsConfig config) {
        return Result.success(ttsConfigService.saveConfig(config));
    }

    @DeleteMapping("/{key}")
    @RequirePermission("tts:manage")
    public Result<Boolean> deleteConfig(@PathVariable String key) {
        return Result.success(ttsConfigService.deleteConfig(key));
    }
}
