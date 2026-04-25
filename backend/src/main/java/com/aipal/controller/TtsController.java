package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.TtsRequest;
import com.aipal.dto.TtsResponse;
import com.aipal.dto.VoiceInfo;
import com.aipal.service.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
public class TtsController {

    private final TtsService ttsService;

    @PostMapping("/synthesize")
    public Result<TtsResponse> synthesize(@RequestBody TtsRequest request) {
        log.info("TTS synthesize request: voiceId={}, textLength={}",
                request.getVoiceId(), request.getText() != null ? request.getText().length() : 0);
        TtsResponse response = ttsService.synthesize(request);
        if ("success".equals(response.getStatus())) {
            return Result.success(response);
        } else {
            return Result.error(response.getErrorMessage());
        }
    }

    @PostMapping("/stream")
    public Flux<String> stream(@RequestBody TtsRequest request) {
        log.info("TTS stream request: voiceId={}, textLength={}",
                request.getVoiceId(), request.getText() != null ? request.getText().length() : 0);
        return ttsService.synthesizeStream(request);
    }

    @GetMapping("/voices")
    public Result<List<VoiceInfo>> getVoices(@RequestParam(required = false) String locale) {
        List<VoiceInfo> voices;
        if (locale != null && !locale.isBlank()) {
            voices = ttsService.getVoicesByLocale(locale);
        } else {
            voices = ttsService.getAvailableVoices();
        }
        return Result.success(voices);
    }

    @GetMapping("/audio/{taskId}")
    public ResponseEntity<Resource> getAudio(@PathVariable String taskId) {
        Optional<Path> audioFile = ttsService.getAudioFile(taskId);
        if (audioFile.isPresent()) {
            Resource resource = new FileSystemResource(audioFile.get());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + taskId + ".mp3\"")
                    .body(resource);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/audio/{taskId}")
    public Result<Boolean> deleteAudio(@PathVariable String taskId) {
        boolean deleted = ttsService.deleteAudioFile(taskId);
        return Result.success(deleted);
    }
}
