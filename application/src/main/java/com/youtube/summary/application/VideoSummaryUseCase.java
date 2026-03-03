package com.youtube.summary.application;

import com.youtube.summary.domain.*;
import com.youtube.summary.domain.port.AgendaGenerator;
import com.youtube.summary.domain.port.SummaryGenerator;
import com.youtube.summary.domain.port.TranscriptProvider;
import com.youtube.summary.domain.port.TranscriptTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Application use case: fetch transcript (with optional fallback), translate to English, then generate summary and agenda.
 */
public class VideoSummaryUseCase {

    private static final Logger log = LoggerFactory.getLogger(VideoSummaryUseCase.class);

    private final List<TranscriptProvider> transcriptProviders;
    private final TranscriptTranslator transcriptTranslator;
    private final SummaryGenerator summaryGenerator;
    private final AgendaGenerator agendaGenerator;

    public VideoSummaryUseCase(List<TranscriptProvider> transcriptProviders,
                               TranscriptTranslator transcriptTranslator,
                               SummaryGenerator summaryGenerator,
                               AgendaGenerator agendaGenerator) {
        this.transcriptProviders = transcriptProviders != null ? transcriptProviders : List.of();
        this.transcriptTranslator = transcriptTranslator;
        this.summaryGenerator = summaryGenerator;
        this.agendaGenerator = agendaGenerator;
    }

    /**
     * Run the pipeline: transcript -> translate to English -> summary -> agenda.
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

        Transcript englishTranscript = transcriptTranslator.translateToEnglish(transcript);

        Summary summary = summaryGenerator.generate(englishTranscript);
        Agenda agenda = agendaGenerator.generate(englishTranscript);

        return Optional.of(new VideoSummaryResult(transcript, summary, agenda, usedFallback));
    }
}
