package com.youtube.summary.application;

import com.youtube.summary.domain.Agenda;
import com.youtube.summary.domain.Summary;
import com.youtube.summary.domain.Transcript;

import java.util.Objects;

/**
 * Result of the video summary use case: transcript, summary, and agenda.
 */
public final class VideoSummaryResult {

    private final Transcript transcript;
    private final Summary summary;
    private final Agenda agenda;
    private final boolean usedFallback;

    public VideoSummaryResult(Transcript transcript, Summary summary, Agenda agenda, boolean usedFallback) {
        this.transcript = Objects.requireNonNull(transcript);
        this.summary = Objects.requireNonNull(summary);
        this.agenda = Objects.requireNonNull(agenda);
        this.usedFallback = usedFallback;
    }

    public Transcript getTranscript() {
        return transcript;
    }

    public Summary getSummary() {
        return summary;
    }

    public Agenda getAgenda() {
        return agenda;
    }

    public boolean isUsedFallback() {
        return usedFallback;
    }
}
