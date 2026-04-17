package com.aipal.agent.image.controller;

import com.aipal.agent.image.dto.A2AMessage;
import com.aipal.agent.image.service.A2AMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/a2a")
@RequiredArgsConstructor
public class A2AController {

    private final A2AMessageService a2aMessageService;

    @PostMapping("/message")
    public ResponseEntity<A2AMessage> handleMessage(@RequestBody A2AMessage message) {
        log.info("Received A2A message from {} to {}", message.getSourceAgent(), message.getTargetAgent());
        A2AMessage response = a2aMessageService.handleMessage(message);
        return ResponseEntity.ok(response);
    }
}
