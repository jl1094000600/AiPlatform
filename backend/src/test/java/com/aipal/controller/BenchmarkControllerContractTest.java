package com.aipal.controller;

import com.aipal.service.CriteriaEngineService;
import com.aipal.service.DataGeneratorService;
import com.aipal.service.DatasetService;
import com.aipal.service.EvaluationService;
import com.aipal.service.EvaluationStatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BenchmarkControllerContractTest {

    @Test
    void usesVersionedBenchmarkBasePath() {
        RequestMapping mapping = BenchmarkController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{"/api/v1/benchmark"}, mapping.value());
    }

    @Test
    void exportsReportAsDownloadInsteadOfResultEnvelope() {
        EvaluationStatisticsService statisticsService = mock(EvaluationStatisticsService.class);
        when(statisticsService.generateEvaluationReport(7L)).thenReturn("benchmark report");
        BenchmarkController controller = new BenchmarkController(
                mock(DatasetService.class),
                mock(DataGeneratorService.class),
                mock(EvaluationService.class),
                statisticsService,
                mock(CriteriaEngineService.class)
        );

        ResponseEntity<byte[]> response = controller.exportResult(7L);

        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertEquals("attachment; filename=benchmark-7.txt",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals("benchmark report".getBytes(StandardCharsets.UTF_8), response.getBody());
    }
}
