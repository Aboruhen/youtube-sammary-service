package com.youtube.summary.api.config;

import com.youtube.summary.application.VideoSummaryUseCase;
import com.youtube.summary.domain.port.AgendaGenerator;
import com.youtube.summary.domain.port.SummaryGenerator;
import com.youtube.summary.domain.port.TranscriptProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the use case with all transcript providers (primary + fallback) and LLM adapters.
 */
@Configuration
public class SummaryConfig {

    @Bean
    public VideoSummaryUseCase videoSummaryUseCase(
            List<TranscriptProvider> transcriptProviders,
            SummaryGenerator summaryGenerator,
            AgendaGenerator agendaGenerator) {
        return new VideoSummaryUseCase(transcriptProviders, summaryGenerator, agendaGenerator);
    }
}
