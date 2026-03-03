package com.youtube.summary.domain.port;

import com.youtube.summary.domain.Transcript;

/**
 * Translates a transcript to English. Used so summary and agenda are always generated from English text.
 * If the transcript is already in English or translation is not needed, implementations may return it unchanged.
 */
public interface TranscriptTranslator {

    /**
     * Returns an English version of the transcript (same structure and timings, translated segment text).
     * May return the original transcript if it is already English or translation fails.
     */
    Transcript translateToEnglish(Transcript transcript);
}
