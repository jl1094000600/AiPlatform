package com.aipal.imageagent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/image-agent")
@RequiredArgsConstructor
public class ImageAgentController {

    private final ImageRecognitionAgent imageAgent;

    @PostMapping("/recognize")
    public ResponseEntity<Map<String, Object>> recognizeImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", required = false) String prompt) {
        try {
            String result = imageAgent.recognizeImage(file);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "result", result,
                "fileName", file.getOriginalFilename()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Image recognition failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/process-document")
    public ResponseEntity<Map<String, Object>> processDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", required = false) String prompt) {
        try {
            String result = imageAgent.processDocument(file);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "result", result,
                "fileName", file.getOriginalFilename()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Document processing failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "agent", imageAgent.getAgentName(),
            "agentCode", imageAgent.getAgentCode(),
            "health", "healthy"
        ));
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
            "agentCode", imageAgent.getAgentCode(),
            "agentName", imageAgent.getAgentName(),
            "capabilities", new String[]{"image_recognition", "document_processing"}
        ));
    }
}
