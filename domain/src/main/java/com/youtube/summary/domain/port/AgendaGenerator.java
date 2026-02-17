package com.youtube.summary.domain.port;

import com.youtube.summary.domain.Agenda;
import com.youtube.summary.domain.Transcript;

/**
 * Port: generate a timed agenda from a transcript (LLM adapter).
 */
public interface AgendaGenerator {

    Agenda generate(Transcript transcript);
}
