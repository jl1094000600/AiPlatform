package com.aipal.service;

import com.aipal.mapper.RequirementAttachmentMapper;
import com.aipal.mapper.RequirementParseTaskMapper;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementAttachmentServiceTest {
    @Mock private RequirementAttachmentMapper attachmentMapper;
    @Mock private RequirementParseTaskMapper taskMapper;
    @Mock private MultimodalModelClient modelClient;
    @Mock private TenantTaskRunner tenantTaskRunner;

    private RequirementAttachmentService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.Context(9L, "user", 4L, "tenant-4",
                false, Set.of(), Set.of()));
        service = new RequirementAttachmentService(attachmentMapper, taskMapper, modelClient, tenantTaskRunner);
        ReflectionTestUtils.setField(service, "tenantAwareExecutor", (java.util.concurrent.Executor) Runnable::run);
        ReflectionTestUtils.setField(service, "storageRoot", System.getProperty("java.io.tmpdir"));
        ReflectionTestUtils.setField(service, "maxImageBytes", 1024L);
        ReflectionTestUtils.setField(service, "maxAudioBytes", 1024L);
        ReflectionTestUtils.setField(service, "maxAttachments", 20);
        ReflectionTestUtils.setField(service, "retentionDays", 30);
        when(attachmentMapper.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rejectsUnsupportedMimeType() {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());
        assertThrows(IllegalArgumentException.class, () -> service.upload("request_1234", file));
    }

    @Test
    void rejectsMimeAndSignatureMismatch() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png", "not-png".getBytes());
        assertThrows(IllegalArgumentException.class, () -> service.upload("request_1234", file));
    }
}
