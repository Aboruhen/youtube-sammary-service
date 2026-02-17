package com.youtube.summary.application;

import com.youtube.summary.domain.*;
import com.youtube.summary.domain.port.AgendaGenerator;
import com.youtube.summary.domain.port.SummaryGenerator;
import com.youtube.summary.domain.port.TranscriptProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Application use case: fetch transcript (with optional fallback), generate summary and agenda.
 */
public class VideoSummaryUseCase {

    private static final Logger log = LoggerFactory.getLogger(VideoSummaryUseCase.class);

    private final List<TranscriptProvider> transcriptProviders;
    private final SummaryGenerator summaryGenerator;
    private final AgendaGenerator agendaGenerator;

    public VideoSummaryUseCase(List<TranscriptProvider> transcriptProviders,
                               SummaryGenerator summaryGenerator,
                               AgendaGenerator agendaGenerator) {
        this.transcriptProviders = transcriptProviders != null ? transcriptProviders : List.of();
        this.summaryGenerator = summaryGenerator;
        this.agendaGenerator = agendaGenerator;
    }

    /**
     * Run the pipeline: transcript -> summary -> agenda.
     * Tries each TranscriptProvider in order until one returns a non-empty transcript.
     */
    public Optional<VideoSummaryResult> run(VideoId videoId) {
        Transcript transcript = null;
        boolean usedFallback = false;
        int providerIndex = 0;

        for (TranscriptProvider provider : transcriptProviders) {
            Optional<Transcript> opt = provider.fetch(videoId);
            if (opt.isPresent() && !opt.get().isEmpty()) {
                transcript = opt.get();
                usedFallback = providerIndex > 0;
                log.info("Obtained transcript for video {} using provider index {}", videoId.getValue(), providerIndex);
                break;
            }
            providerIndex++;
        }

        if (transcript == null || transcript.isEmpty()) {
            log.warn("No transcript available for video {}", videoId.getValue());
            return Optional.empty();
        }

        Summary summary = summaryGenerator.generate(transcript);
        Agenda agenda = agendaGenerator.generate(transcript);

        return Optional.of(new VideoSummaryResult(transcript, summary, agenda, usedFallback));
    }
}
