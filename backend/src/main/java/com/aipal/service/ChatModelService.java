package com.aipal.service;

import com.aipal.entity.AiModel;
import com.aipal.mapper.AiModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.ChatMemoryPromptAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.model.MediaModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatModelService {

    private final AiModelMapper modelMapper;
    private final Map<String, ChatClient> chatClients = new ConcurrentHashMap<>();
    private final Map<String, MediaModel> mediaModels = new ConcurrentHashMap<>();
    private final InMemoryChatMemory chatMemory = new InMemoryChatMemory();

    @Value("${spring-ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${spring-ai.anthropic.api-key:}")
    private String anthropicApiKey;

    public ChatModelService(AiModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    @PostConstruct
    public void init() {
        refreshModels();
    }

    public void refreshModels() {
        List<AiModel> models = modelMapper.selectList(
            new LambdaQueryWrapper<AiModel>().eq(AiModel::getStatus, 1)
        );

        for (AiModel model : models) {
            try {
                registerModel(model);
            } catch (Exception e) {
                log.error("Failed to register model: {}", model.getModelCode(), e);
            }
        }
        log.info("Loaded {} AI models", models.size());
    }

    public void registerModel(AiModel model) {
        ChatClient chatClient = createChatClient(model);
        if (chatClient != null) {
            chatClients.put(model.getModelCode(), chatClient);
            log.info("Registered chat client for model: {}", model.getModelCode());
        }
    }

    private ChatClient createChatClient(AiModel model) {
        String provider = model.getProvider().toLowerCase();

        return switch (provider) {
            case "openai" -> createOpenAIClient(model);
            case "anthropic" -> createAnthropicClient(model);
            default -> {
                log.warn("Unsupported provider: {}", provider);
                yield null;
            }
        };
    }

    private ChatClient createOpenAIClient(AiModel model) {
        OpenAiApi openAiApi = OpenAiApi.builder()
            .apiKey(openaiApiKey)
            .baseUrl(model.getEndpoint())
            .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(model.getModelVersion())
            .temperature(0.7)
            .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(options)
            .build();

        return ChatClient.builder(chatModel)
            .defaultAdvisors(new PromptChatMemoryAdvisor(chatMemory))
            .build();
    }

    private ChatClient createAnthropicClient(AiModel model) {
        AnthropicApi anthropicApi = AnthropicApi.builder()
            .apiKey(anthropicApiKey)
            .baseUrl(model.getEndpoint())
            .build();

        AnthropicChatModel chatModel = AnthropicChatModel.builder()
            .anthropicApi(anthropicApi)
            .defaultOptions(AnthropicApi.AnthropicOptions.builder()
                .model(model.getModelVersion())
                .temperature(0.7)
                .build())
            .build();

        return ChatClient.builder(chatModel)
            .defaultAdvisors(new PromptChatMemoryAdvisor(chatMemory))
            .build();
    }

    public ChatClient getChatClient(String modelCode) {
        ChatClient client = chatClients.get(modelCode);
        if (client == null) {
            AiModel model = modelMapper.selectOne(
                new LambdaQueryWrapper<AiModel>()
                    .eq(AiModel::getModelCode, modelCode)
                    .eq(AiModel::getStatus, 1)
            );
            if (model != null) {
                registerModel(model);
                client = chatClients.get(modelCode);
            }
        }
        return client;
    }

    public String chat(String modelCode, String userMessage) {
        ChatClient client = getChatClient(modelCode);
        if (client == null) {
            throw new RuntimeException("Chat client not found for model: " + modelCode);
        }
        return client.prompt()
            .user(userMessage)
            .call()
            .content();
    }

    public String chat(String modelCode, String userMessage, Map<String, Object> context) {
        ChatClient client = getChatClient(modelCode);
        if (client == null) {
            throw new RuntimeException("Chat client not found for model: " + modelCode);
        }
        return client.prompt()
            .system(sp -> sp.text("Context: " + context))
            .user(userMessage)
            .call()
            .content();
    }

    public MediaModel getMediaModel(String modelCode) {
        return mediaModels.get(modelCode);
    }

    public void unregisterModel(String modelCode) {
        chatClients.remove(modelCode);
        log.info("Unregistered model: {}", modelCode);
    }
}
