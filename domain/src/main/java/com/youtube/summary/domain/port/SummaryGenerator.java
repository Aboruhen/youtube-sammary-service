package com.youtube.summary.domain.port;

import com.youtube.summary.domain.Summary;
import com.youtube.summary.domain.Transcript;

/**
 * Port: generate a text summary from a transcript (LLM adapter).
 */
public interface SummaryGenerator {

    Summary generate(Transcript transcript);
}
