package com.youtube.summary.domain;

import java.util.Objects;

/**
 * Single agenda entry with title and start time (seconds).
 */
public final class AgendaItem {

    private final String title;
    private final double startSeconds;

    public AgendaItem(String title, double startSeconds) {
        this.title = title != null ? title.trim() : "";
        this.startSeconds = startSeconds;
    }

    public String getTitle() {
        return title;
    }

    public double getStartSeconds() {
        return startSeconds;
    }

    public String getTimestamp() {
        int totalSec = (int) Math.floor(startSeconds);
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format("%d:%02d", min, sec);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgendaItem that = (AgendaItem) o;
        return Double.compare(that.startSeconds, startSeconds) == 0 && Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, startSeconds);
    }
}
