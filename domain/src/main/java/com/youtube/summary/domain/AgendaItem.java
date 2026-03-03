package com.youtube.summary.domain;

import java.util.Objects;

/**
 * Single agenda entry with title, start time and end time (seconds).
 * Times can be formatted as hh:mm:ss for display.
 */
public final class AgendaItem {

    private final String title;
    private final double startSeconds;
    private final double endSeconds;

    /** Creates an item with end time; use when building a full agenda. */
    public AgendaItem(String title, double startSeconds, double endSeconds) {
        this.title = title != null ? title.trim() : "";
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
    }

    /** Legacy: creates item with endSeconds = startSeconds (caller should replace with proper end when building list). */
    public AgendaItem(String title, double startSeconds) {
        this(title, startSeconds, startSeconds);
    }

    public String getTitle() {
        return title;
    }

    public double getStartSeconds() {
        return startSeconds;
    }

    public double getEndSeconds() {
        return endSeconds;
    }

    /** Start time as M:SS (minutes:seconds). */
    public String getTimestamp() {
        return formatHhMmSs(startSeconds);
    }

    /** Format seconds as hh:mm:ss (e.g. 0:05:30, 1:02:00). */
    public static String formatHhMmSs(double seconds) {
        int totalSec = (int) Math.floor(seconds);
        if (totalSec < 0) totalSec = 0;
        int h = totalSec / 3600;
        int m = (totalSec % 3600) / 60;
        int s = totalSec % 60;
        return String.format("%d:%02d:%02d", h, m, s);
    }

    public String getStartTime() {
        return formatHhMmSs(startSeconds);
    }

    public String getEndTime() {
        return formatHhMmSs(endSeconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgendaItem that = (AgendaItem) o;
        return Double.compare(that.startSeconds, startSeconds) == 0
                && Double.compare(that.endSeconds, endSeconds) == 0
                && Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, startSeconds, endSeconds);
    }
}
