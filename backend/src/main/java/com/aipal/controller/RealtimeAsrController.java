package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.service.RealtimeAsrSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/asr")
@RequiredArgsConstructor
public class RealtimeAsrController {
    private final RealtimeAsrSessionService sessionService;

    @PostMapping("/sessions")
    public Result<?> createSession() {
        return Result.success(sessionService.createSession());
    }
}
