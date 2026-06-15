package com.aipal.service;

import com.aipal.entity.AiModel;
import com.aipal.mapper.AiModelMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModelServiceCapabilityTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "model-capability-test"), AiModel.class);
    }

    @Test
    void savingDefaultModelClearsExistingDefaultBeforeInsert() {
        AiModelMapper mapper = mock(AiModelMapper.class);
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(mapper.insert(any(AiModel.class))).thenReturn(1);
        ModelService service = new ModelService(mapper);
        AiModel model = model("vision-model", "vision", 1);

        assertTrue(service.saveModel(model));

        assertEquals(ModelService.CAPABILITY_VISION, model.getCapabilityType());
        assertEquals(1, model.getDefaultForCapability());
        InOrder order = inOrder(mapper);
        order.verify(mapper).update(isNull(), any(LambdaUpdateWrapper.class));
        order.verify(mapper).insert(model);
    }

    @Test
    void nonDefaultModelDoesNotClearOtherModels() {
        AiModelMapper mapper = mock(AiModelMapper.class);
        when(mapper.insert(any(AiModel.class))).thenReturn(1);
        ModelService service = new ModelService(mapper);
        AiModel model = model("chat-model", null, null);

        assertTrue(service.saveModel(model));

        assertEquals(ModelService.CAPABILITY_CHAT, model.getCapabilityType());
        assertEquals(0, model.getDefaultForCapability());
    }

    @Test
    void rejectsUnsupportedCapabilityBeforeWriting() {
        AiModelMapper mapper = mock(AiModelMapper.class);
        ModelService service = new ModelService(mapper);
        AiModel model = model("invalid-model", "EMBEDDING", 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.saveModel(model));

        assertTrue(error.getMessage().contains("Unsupported model capability"));
        verifyNoInteractions(mapper);
    }

    private AiModel model(String code, String capability, Integer defaultFlag) {
        AiModel model = new AiModel();
        model.setModelCode(code);
        model.setModelName(code);
        model.setCapabilityType(capability);
        model.setDefaultForCapability(defaultFlag);
        return model;
    }
}
