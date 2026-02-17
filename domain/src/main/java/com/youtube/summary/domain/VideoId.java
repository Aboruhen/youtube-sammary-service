package com.youtube.summary.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing a YouTube video identifier.
 */
public final class VideoId {

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{11}");

    private final String value;

    public VideoId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Video ID cannot be null or blank");
        }
        String extracted = extractVideoId(value);
        if (!VIDEO_ID_PATTERN.matcher(extracted).matches()) {
            throw new IllegalArgumentException("Invalid YouTube video ID: " + value);
        }
        this.value = extracted;
    }

    private static String extractVideoId(String input) {
        if (input.length() == 11 && VIDEO_ID_PATTERN.matcher(input).matches()) {
            return input;
        }
        if (input.contains("v=")) {
            int start = input.indexOf("v=") + 2;
            int end = input.indexOf('&', start) > 0 ? input.indexOf('&', start) : input.length();
            return input.substring(start, Math.min(end, start + 11));
        }
        return input.trim();
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoId videoId = (VideoId) o;
        return Objects.equals(value, videoId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
