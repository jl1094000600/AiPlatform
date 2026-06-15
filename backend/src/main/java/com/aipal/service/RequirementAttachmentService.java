package com.aipal.service;

import com.aipal.dto.RequirementAttachmentResponse;
import com.aipal.entity.RequirementAttachment;
import com.aipal.entity.RequirementParseTask;
import com.aipal.mapper.RequirementAttachmentMapper;
import com.aipal.mapper.RequirementParseTaskMapper;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementAttachmentService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{8,80}");
    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> AUDIO_MIME_TYPES = Set.of(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/webm",
            "audio/ogg", "audio/mp4", "audio/x-m4a", "audio/aac");

    private final RequirementAttachmentMapper attachmentMapper;
    private final RequirementParseTaskMapper taskMapper;
    private final MultimodalModelClient modelClient;
    private final TenantTaskRunner tenantTaskRunner;
    @Autowired
    @Qualifier("tenantAwareExecutor")
    private Executor tenantAwareExecutor;

    @Value("${aipal.requirements.storage-root:${java.io.tmpdir}/aiplatform-requirements}")
    private String storageRoot;
    @Value("${aipal.requirements.max-image-bytes:15728640}")
    private long maxImageBytes;
    @Value("${aipal.requirements.max-audio-bytes:52428800}")
    private long maxAudioBytes;
    @Value("${aipal.requirements.max-attachments:20}")
    private int maxAttachments;
    @Value("${aipal.requirements.retention-days:30}")
    private int retentionDays;

    public RequirementAttachmentResponse upload(String requestId, MultipartFile file) {
        Long userId = requireUserId();
        String normalizedRequestId = validateRequestId(requestId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("附件不能为空");
        }
        long existing = attachmentMapper.selectCount(new LambdaQueryWrapper<RequirementAttachment>()
                .eq(RequirementAttachment::getUserId, userId)
                .eq(RequirementAttachment::getRequestId, normalizedRequestId));
        if (existing >= maxAttachments) {
            throw new IllegalArgumentException("单次需求最多上传 " + maxAttachments + " 个附件");
        }

        String mimeType = normalizeMimeType(file.getContentType());
        String type = resolveAttachmentType(mimeType);
        long limit = "IMAGE".equals(type) ? maxImageBytes : maxAudioBytes;
        if (file.getSize() <= 0 || file.getSize() > limit) {
            throw new IllegalArgumentException("附件大小超出限制");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取附件失败", e);
        }
        validateFileSignature(type, mimeType, content);
        String originalName = sanitizeFileName(file.getOriginalFilename());
        Path target = resolveStoragePath(normalizedRequestId, originalName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new IllegalStateException("保存附件失败", e);
        }

        RequirementAttachment attachment = new RequirementAttachment();
        attachment.setUserId(userId);
        attachment.setRequestId(normalizedRequestId);
        attachment.setAttachmentType(type);
        attachment.setOriginalFileName(originalName);
        attachment.setMimeType(mimeType);
        attachment.setFileSize((long) content.length);
        attachment.setStoragePath(target.toString());
        attachment.setChecksum(sha256(content));
        attachment.setExpiresAt(retentionDays <= 0 ? null : LocalDateTime.now().plusDays(retentionDays));
        attachment.setCreateTime(LocalDateTime.now());
        attachment.setUpdateTime(LocalDateTime.now());
        attachment.setIsDeleted(0);
        attachmentMapper.insert(attachment);

        RequirementParseTask task = createTask(attachment.getId(), 0);
        submitParse(attachment.getId(), task.getId());
        return toResponse(attachment, task);
    }

    public List<RequirementAttachmentResponse> list(String requestId) {
        Long userId = requireUserId();
        String normalizedRequestId = validateRequestId(requestId);
        return attachmentMapper.selectList(new LambdaQueryWrapper<RequirementAttachment>()
                        .eq(RequirementAttachment::getUserId, userId)
                        .eq(RequirementAttachment::getRequestId, normalizedRequestId)
                        .orderByAsc(RequirementAttachment::getCreateTime))
                .stream().map(item -> toResponse(item, latestTask(item.getId()))).toList();
    }

    public RequirementAttachmentResponse updateResult(Long attachmentId, String resultText) {
        RequirementAttachment attachment = requireOwnedAttachment(attachmentId);
        RequirementParseTask task = latestTask(attachmentId);
        if (task == null || !STATUS_SUCCEEDED.equals(task.getStatus())) {
            throw new IllegalStateException("附件尚未解析成功");
        }
        String value = resultText == null ? "" : resultText.trim();
        if (value.length() > 100_000) {
            throw new IllegalArgumentException("解析结果过长");
        }
        task.setEditedResult(value);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return toResponse(attachment, task);
    }

    public RequirementAttachmentResponse retry(Long attachmentId) {
        RequirementAttachment attachment = requireOwnedAttachment(attachmentId);
        RequirementParseTask latest = latestTask(attachmentId);
        if (latest != null && (STATUS_PENDING.equals(latest.getStatus()) || STATUS_RUNNING.equals(latest.getStatus()))) {
            throw new IllegalStateException("附件正在解析中");
        }
        int retryCount = latest == null || latest.getRetryCount() == null ? 1 : latest.getRetryCount() + 1;
        RequirementParseTask task = createTask(attachmentId, retryCount);
        submitParse(attachmentId, task.getId());
        return toResponse(attachment, task);
    }

    public boolean delete(Long attachmentId) {
        RequirementAttachment attachment = requireOwnedAttachment(attachmentId);
        deleteStoredFile(attachment);
        taskMapper.delete(new LambdaQueryWrapper<RequirementParseTask>()
                .eq(RequirementParseTask::getAttachmentId, attachmentId));
        return attachmentMapper.deleteById(attachmentId) > 0;
    }

    public RequirementAttachment requireOwnedAttachment(Long attachmentId) {
        if (attachmentId == null) throw new IllegalArgumentException("attachmentId 不能为空");
        RequirementAttachment attachment = attachmentMapper.selectOne(new LambdaQueryWrapper<RequirementAttachment>()
                .eq(RequirementAttachment::getId, attachmentId)
                .eq(RequirementAttachment::getUserId, requireUserId())
                .last("LIMIT 1"));
        if (attachment == null) throw new IllegalArgumentException("附件不存在");
        return attachment;
    }

    public RequirementParseTask latestSuccessfulTask(Long attachmentId) {
        RequirementParseTask task = latestTask(attachmentId);
        if (task == null || !STATUS_SUCCEEDED.equals(task.getStatus())) {
            throw new IllegalStateException("附件尚未解析成功: " + attachmentId);
        }
        return task;
    }

    private RequirementParseTask createTask(Long attachmentId, int retryCount) {
        RequirementParseTask task = new RequirementParseTask();
        task.setAttachmentId(attachmentId);
        task.setStatus(STATUS_PENDING);
        task.setRetryCount(retryCount);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        task.setIsDeleted(0);
        taskMapper.insert(task);
        return task;
    }

    private void submitParse(Long attachmentId, Long taskId) {
        tenantAwareExecutor.execute(() -> parse(attachmentId, taskId));
    }

    private void parse(Long attachmentId, Long taskId) {
        RequirementParseTask task = taskMapper.selectById(taskId);
        RequirementAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (task == null || attachment == null) return;
        task.setStatus(STATUS_RUNNING);
        task.setStartTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        try {
            byte[] content = Files.readAllBytes(validateStoredPath(attachment.getStoragePath()));
            String result = "IMAGE".equals(attachment.getAttachmentType())
                    ? modelClient.analyzeImage(content, attachment.getMimeType(), attachment.getOriginalFileName())
                    : modelClient.transcribeAudio(content, attachment.getMimeType(), attachment.getOriginalFileName());
            task.setRawResult(result == null ? "" : result.trim());
            task.setStatus(STATUS_SUCCEEDED);
            task.setErrorMessage(null);
        } catch (Exception e) {
            task.setStatus(STATUS_FAILED);
            task.setErrorMessage(limitError(e.getMessage()));
            log.warn("Requirement attachment parsing failed: attachmentId={}, taskId={}", attachmentId, taskId, e);
        }
        task.setEndTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private RequirementParseTask latestTask(Long attachmentId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<RequirementParseTask>()
                .eq(RequirementParseTask::getAttachmentId, attachmentId)
                .orderByDesc(RequirementParseTask::getCreateTime)
                .orderByDesc(RequirementParseTask::getId)
                .last("LIMIT 1"));
    }

    private RequirementAttachmentResponse toResponse(RequirementAttachment attachment, RequirementParseTask task) {
        RequirementAttachmentResponse.ParseTaskView taskView = task == null ? null
                : RequirementAttachmentResponse.ParseTaskView.builder()
                .id(task.getId()).status(task.getStatus()).resultText(task.getRawResult())
                .editedResult(task.getEditedResult()).errorMessage(task.getErrorMessage())
                .retryCount(task.getRetryCount()).build();
        return RequirementAttachmentResponse.builder()
                .id(attachment.getId()).requestId(attachment.getRequestId())
                .fileName(attachment.getOriginalFileName()).mediaType(attachment.getAttachmentType())
                .mimeType(attachment.getMimeType()).fileSize(attachment.getFileSize())
                .createTime(attachment.getCreateTime()).latestTask(taskView).build();
    }

    private String validateRequestId(String requestId) {
        String value = requestId == null ? "" : requestId.trim();
        if (!REQUEST_ID.matcher(value).matches()) throw new IllegalArgumentException("requestId 格式无效");
        return value;
    }

    private Long requireUserId() {
        Long userId = TenantContext.userId();
        if (userId == null) throw new IllegalStateException("当前用户上下文不存在");
        return userId;
    }

    private String normalizeMimeType(String mimeType) {
        if (mimeType == null) return "";
        return mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String resolveAttachmentType(String mimeType) {
        if (IMAGE_MIME_TYPES.contains(mimeType)) return "IMAGE";
        if (AUDIO_MIME_TYPES.contains(mimeType)) return "AUDIO";
        throw new IllegalArgumentException("仅支持常见图片和音频格式");
    }

    private void validateFileSignature(String type, String mimeType, byte[] bytes) {
        boolean valid = "IMAGE".equals(type) ? isImageSignature(mimeType, bytes) : isAudioSignature(mimeType, bytes);
        if (!valid) throw new IllegalArgumentException("附件内容与文件类型不匹配");
    }

    private boolean isImageSignature(String mime, byte[] b) {
        if ("image/jpeg".equals(mime)) return startsWith(b, 0xFF, 0xD8, 0xFF);
        if ("image/png".equals(mime)) return startsWith(b, 0x89, 0x50, 0x4E, 0x47);
        if ("image/gif".equals(mime)) return asciiAt(b, 0, "GIF8");
        return "image/webp".equals(mime) && asciiAt(b, 0, "RIFF") && asciiAt(b, 8, "WEBP");
    }

    private boolean isAudioSignature(String mime, byte[] b) {
        if (Set.of("audio/mpeg", "audio/mp3").contains(mime)) {
            return asciiAt(b, 0, "ID3") || (b.length > 1 && (b[0] & 0xFF) == 0xFF && ((b[1] & 0xE0) == 0xE0));
        }
        if (Set.of("audio/wav", "audio/x-wav").contains(mime)) return asciiAt(b, 0, "RIFF") && asciiAt(b, 8, "WAVE");
        if ("audio/webm".equals(mime)) return startsWith(b, 0x1A, 0x45, 0xDF, 0xA3);
        if ("audio/ogg".equals(mime)) return asciiAt(b, 0, "OggS");
        if (Set.of("audio/mp4", "audio/x-m4a", "audio/aac").contains(mime)) {
            return asciiAt(b, 4, "ftyp") || startsWith(b, 0xFF, 0xF1) || startsWith(b, 0xFF, 0xF9);
        }
        return false;
    }

    private boolean startsWith(byte[] bytes, int... values) {
        if (bytes.length < values.length) return false;
        for (int i = 0; i < values.length; i++) if ((bytes[i] & 0xFF) != values[i]) return false;
        return true;
    }

    private boolean asciiAt(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) return false;
        for (int i = 0; i < value.length(); i++) if ((char) bytes[offset + i] != value.charAt(i)) return false;
        return true;
    }

    private Path resolveStoragePath(String requestId, String originalName) {
        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        String extension = extensionOf(originalName);
        Path target = root.resolve(String.valueOf(TenantContext.tenantId()))
                .resolve(String.valueOf(requireUserId())).resolve(requestId)
                .resolve(UUID.randomUUID().toString().replace("-", "") + extension).normalize();
        if (!target.startsWith(root)) throw new IllegalStateException("附件存储路径无效");
        return target;
    }

    private Path validateStoredPath(String value) {
        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        Path path = Paths.get(value).toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new IllegalStateException("附件存储路径越界");
        return path;
    }

    private String sanitizeFileName(String fileName) {
        String name = fileName == null ? "attachment" : Paths.get(fileName).getFileName().toString();
        name = name.replaceAll("[\\r\\n\\t]", "_").trim();
        return name.isBlank() ? "attachment" : name.substring(0, Math.min(name.length(), 255));
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return "";
        String extension = fileName.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,8}") ? extension : "";
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException("计算附件校验值失败", e);
        }
    }

    private String limitError(String message) {
        String value = message == null || message.isBlank() ? "附件解析失败" : message;
        return value.substring(0, Math.min(1000, value.length()));
    }

    private void deleteStoredFile(RequirementAttachment attachment) {
        try {
            Files.deleteIfExists(validateStoredPath(attachment.getStoragePath()));
        } catch (IOException e) {
            throw new IllegalStateException("删除附件失败", e);
        }
    }

    @Scheduled(fixedDelayString = "${aipal.requirements.cleanup-delay-ms:3600000}",
            initialDelayString = "${aipal.scheduling.initial-delay-ms:30000}")
    public void cleanupExpiredAttachments() {
        tenantTaskRunner.forEachActiveTenant("requirement-attachment-cleanup", tenant -> {
            List<RequirementAttachment> expired = attachmentMapper.selectList(
                    new LambdaQueryWrapper<RequirementAttachment>()
                            .isNotNull(RequirementAttachment::getExpiresAt)
                            .le(RequirementAttachment::getExpiresAt, LocalDateTime.now()));
            for (RequirementAttachment attachment : expired) {
                try {
                    deleteStoredFile(attachment);
                    taskMapper.delete(new LambdaUpdateWrapper<RequirementParseTask>()
                            .eq(RequirementParseTask::getAttachmentId, attachment.getId()));
                    attachmentMapper.deleteById(attachment.getId());
                } catch (RuntimeException e) {
                    log.warn("Failed to clean expired requirement attachment {}", attachment.getId(), e);
                }
            }
        });
    }
}
