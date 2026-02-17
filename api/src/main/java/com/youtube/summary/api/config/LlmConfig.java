package com.youtube.summary.api.config;

import com.youtube.summary.domain.port.AgendaGenerator;
import com.youtube.summary.domain.port.SummaryGenerator;
import com.youtube.summary.infrastructure.llm.SpringAiAgendaGenerator;
import com.youtube.summary.infrastructure.llm.SpringAiSummaryGenerator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    @Bean
    public SummaryGenerator summaryGenerator(ChatClient.Builder chatClientBuilder) {
        return new SpringAiSummaryGenerator(chatClientBuilder.build());
    }

    @Bean
    public AgendaGenerator agendaGenerator(ChatClient.Builder chatClientBuilder) {
        return new SpringAiAgendaGenerator(chatClientBuilder.build());
    }
}
