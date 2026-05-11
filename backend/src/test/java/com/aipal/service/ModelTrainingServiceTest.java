package com.aipal.service;

import com.aipal.dto.ModelTrainingRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelTrainingServiceTest {

    @Test
    void rejectsTrainDataOutsideProjectRoot() {
        ModelTrainingService service = new ModelTrainingService();
        ModelTrainingRequest request = new ModelTrainingRequest();
        request.setTrainData("../data/train.jsonl");

        assertThrows(IllegalArgumentException.class, () -> service.createJob(request));
    }

    @Test
    void rejectsNonPositiveEpochs() {
        ModelTrainingService service = new ModelTrainingService();
        ModelTrainingRequest request = new ModelTrainingRequest();
        request.setTrainData("bge-m3-training/data/sample_train.jsonl");
        request.setEpochs(0);

        assertThrows(IllegalArgumentException.class, () -> service.createJob(request));
    }

    @Test
    void acceptsDryRunRequestWithDefaults() {
        ModelTrainingService service = new ModelTrainingService();
        ModelTrainingRequest request = new ModelTrainingRequest();
        request.setTrainData("bge-m3-training/data/sample_train.jsonl");
        request.setDryRun(true);
        request.setLearningRate(new BigDecimal("0.00001"));

        var job = service.createJob(request);

        assertEquals(true, job.getId().startsWith("MT_"));
        assertEquals("BAAI/bge-m3", job.getModelPath());
        assertEquals("bge-m3-training/output/bge-m3-ft", job.getOutputDir());
    }
}
