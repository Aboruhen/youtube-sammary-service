package com.youtube.summary.domain;

import java.util.Objects;

/**
 * A single segment of transcript with start time and duration (in seconds).
 */
public final class TranscriptSegment {

    private final double startSeconds;
    private final double durationSeconds;
    private final String text;

    public TranscriptSegment(double startSeconds, double durationSeconds, String text) {
        this.startSeconds = startSeconds;
        this.durationSeconds = durationSeconds;
        this.text = text != null ? text.trim() : "";
    }

    public double getStartSeconds() {
        return startSeconds;
    }

    public double getDurationSeconds() {
        return durationSeconds;
    }

    public String getText() {
        return text;
    }

    public double getEndSeconds() {
        return startSeconds + durationSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TranscriptSegment that = (TranscriptSegment) o;
        return Double.compare(that.startSeconds, startSeconds) == 0
                && Double.compare(that.durationSeconds, durationSeconds) == 0
                && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startSeconds, durationSeconds, text);
    }

    @Override
    public String toString() {
        return "TranscriptSegment{" +
                "startSeconds=" + startSeconds +
                ", durationSeconds=" + durationSeconds +
                ", text='" + text + '\'' +
                '}';
    }
}
