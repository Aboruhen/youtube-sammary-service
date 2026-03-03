package com.youtube.summary.api.config;

import com.youtube.summary.domain.port.AgendaGenerator;
import com.youtube.summary.domain.port.SummaryGenerator;
import com.youtube.summary.domain.port.TranscriptTranslator;
import com.youtube.summary.infrastructure.llm.SpringAiAgendaGenerator;
import com.youtube.summary.infrastructure.llm.SpringAiSummaryGenerator;
import com.youtube.summary.infrastructure.llm.SpringAiTranscriptTranslator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LlmPromptProperties.class)
public class LlmConfig {

    @Bean
    public TranscriptTranslator transcriptTranslator(ChatClient.Builder chatClientBuilder,
                                                     LlmPromptProperties prompts) {
        return new SpringAiTranscriptTranslator(
                chatClientBuilder.build(),
                prompts.getTranslation().getSystem(),
                prompts.getTranslation().getUserPrefix());
    }

    @Bean
    public SummaryGenerator summaryGenerator(ChatClient.Builder chatClientBuilder,
                                             LlmPromptProperties prompts) {
        return new SpringAiSummaryGenerator(
                chatClientBuilder.build(),
                prompts.getSummary().getSystem(),
                prompts.getSummary().getUserPrefix());
    }

    @Bean
    public AgendaGenerator agendaGenerator(ChatClient.Builder chatClientBuilder,
                                           LlmPromptProperties prompts) {
        return new SpringAiAgendaGenerator(
                chatClientBuilder.build(),
                prompts.getAgenda().getSystem(),
                prompts.getAgenda().getUserPrefix());
    }
}
