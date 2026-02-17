# Architecture Design

This document describes the architecture of the YouTube Summary Service based on the current implementation.

## Overview

The system follows **Hexagonal Architecture** (Ports & Adapters) with a **plugin-style** layout so that transcript sources and LLM backends can be swapped or extended without changing the core logic.

## Goals

- **YouTube video → transcript → summary → agenda with timing**
- **Fallback** when the video has no captions: process by chunks (download + speech-to-text) — placeholder in code, ready for implementation
- **Clear boundaries**: domain and application do not depend on frameworks or external APIs
- **Easy feature changes**: add new transcript providers or LLM adapters by implementing ports and registering plugins

## High-Level Flow

```
  HTTP GET /api/summary?videoUrl=...
       │
       ▼
  SummaryController
       │
       ▼
  VideoSummaryUseCase.run(videoId)
       │
       ├─► TranscriptProvider(s) [ordered]  ──►  YouTubeTranscriptAdapter (or ChunkedTranscriptProvider)
       │         │
       │         └─► Optional<Transcript>
       │
       ├─► SummaryGenerator.generate(transcript)  ──►  SpringAiSummaryGenerator (ChatClient)
       │
       ├─► AgendaGenerator.generate(transcript)   ──►  SpringAiAgendaGenerator (ChatClient)
       │
       └─► VideoSummaryResult(transcript, summary, agenda, usedFallback)
```

## Hexagonal Layout

### Core (inside)

- **Domain** (`domain` module): entities and value objects (`VideoId`, `Transcript`, `TranscriptSegment`, `Summary`, `Agenda`, `AgendaItem`) and **ports** (interfaces):
  - `TranscriptProvider`: fetch transcript for a video
  - `SummaryGenerator`: produce summary from transcript
  - `AgendaGenerator`: produce timed agenda from transcript
- **Application** (`application` module): use case `VideoSummaryUseCase` that depends only on these ports and orchestrates: try each `TranscriptProvider` in order, then call `SummaryGenerator` and `AgendaGenerator`.

### Adapters (outside, pluggable)

- **Infrastructure Transcript** (`infrastructure-transcript`): implements `TranscriptProvider` using the Java [youtube-transcript-api](https://github.com/trldvix/youtube-transcript-api) library (YouTube captions).
- **Infrastructure LLM** (`infrastructure-llm`): implements `SummaryGenerator` and `AgendaGenerator` using Spring AI `ChatClient` (OpenAI or Ollama).
- **Infrastructure Fallback** (`infrastructure-fallback`): implements `TranscriptProvider` for the “no captions” case; currently a placeholder that returns empty (intended: download video by chunks + speech-to-text, then return a `Transcript`).

### API (driving adapter)

- **API** (`api` module): Spring Boot application that exposes REST and **wires** the hexagon:
  - Registers `TranscriptProvider` beans in order (YouTube first, then chunked fallback).
  - Registers `SummaryGenerator` and `AgendaGenerator` backed by `ChatClient`.
  - Instantiates `VideoSummaryUseCase` with the list of providers and the two generators.

## Plugin Pattern

- **Transcript**: Multiple `TranscriptProvider` beans are collected into a `List<TranscriptProvider>` and passed to the use case. Order is defined by `@Order` (e.g. YouTube = 0, chunked = 1). The use case tries each in turn until one returns a non-empty transcript.
- **Summary / Agenda**: Single implementations are used per run; switching OpenAI vs Ollama is done by changing the Spring AI starter and configuration, not by changing the use case. Alternative implementations (e.g. different prompts or models) can be added as new beans and selected via `@Qualifier` or profile if needed.

This gives:

- **Easy feature changes**: new transcript source = new `TranscriptProvider` implementation and a new bean; new LLM = new starter + config or new generator beans.
- **Testability**: ports can be mocked in unit tests; adapters can be tested in isolation.

## Module Dependencies

```
  api
   ├── application
   │     └── domain
   ├── infrastructure-transcript  ──►  domain
   ├── infrastructure-llm        ──►  domain
   └── infrastructure-fallback   ──►  domain
```

- **domain**: no dependency on other project modules or Spring.
- **application**: depends only on `domain`.
- **infrastructure-***: depend only on `domain` (implement ports).
- **api**: depends on `application` and all infrastructure modules; contains configuration and REST.

## Data Flow (Key Types)

| Concept       | Type             | Produced by              | Consumed by                    |
|---------------|------------------|---------------------------|--------------------------------|
| Video id      | `VideoId`        | Controller (from URL)     | Use case, providers, generators |
| Transcript    | `Transcript`     | `TranscriptProvider`     | Use case, `SummaryGenerator`, `AgendaGenerator` |
| Summary       | `Summary`        | `SummaryGenerator`       | Use case → API response        |
| Agenda        | `Agenda`         | `AgendaGenerator`        | Use case → API response        |
| Result        | `VideoSummaryResult` | Use case             | Controller → JSON              |

## Fallback Design (No Captions)

When no YouTube captions are available:

1. The first provider (YouTube) returns `Optional.empty()` or an empty transcript.
2. The use case calls the next provider (chunked fallback).
3. **Current state**: `ChunkedTranscriptProvider` is a placeholder and returns `Optional.empty()`, so the API returns 404.
4. **Intended extension**: implement chunked flow inside the fallback module:
   - Download (or stream) video/audio in chunks.
   - Send each chunk to a Speech-to-Text port (e.g. Whisper API or local model).
   - Build `TranscriptSegment`s with correct start/duration, then `Transcript`, and return `Optional.of(transcript)`.

The use case does not need to change when the chunked implementation is added; only the fallback adapter is implemented.

## Summary

The implementation uses **hexagonal architecture** with **ports** in the domain and **adapters** in infrastructure modules. The **plugin pattern** for transcript providers (ordered list) and the separate LLM adapters make it straightforward to add or swap features (new transcript sources, new LLMs, or a real chunked fallback) without altering the core domain or use case logic.
