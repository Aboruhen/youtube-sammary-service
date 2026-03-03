package com.youtube.summary.infrastructure.llm;

import com.youtube.summary.domain.Transcript;
import com.youtube.summary.domain.TranscriptSegment;
import com.youtube.summary.domain.port.TranscriptTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates a transcript to English using the LLM before summary/agenda generation.
 */
public class SpringAiTranscriptTranslator implements TranscriptTranslator {

    private static final Logger log = LoggerFactory.getLogger(SpringAiTranscriptTranslator.class);

    private static final String SYSTEM_PROMPT = """
            You are a translator. Translate the given transcript lines to English.
            Rules:
            - Output ONLY the translated lines, one line per line of input.
            - Do NOT add line numbers, bullets, or any prefix. Plain text only.
            - Keep exactly the same number of lines as the input.
            - If a line is already in English, output it unchanged.
            - Preserve meaning and tone; this is typically technical or educational content.
            """;

    /** Max segments per LLM call so the model returns one line per line (avoids summarization). */
    private static final int CHUNK_SIZE = 80;
    private static final int MAX_CHUNK_CHARS = 15_000;

    private final ChatClient chatClient;

    public SpringAiTranscriptTranslator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Transcript translateToEnglish(Transcript transcript) {
        if (transcript == null || transcript.isEmpty()) {
            return transcript;
        }
        List<TranscriptSegment> segments = transcript.getSegments();
        List<String> allTranslatedLines = new ArrayList<>(segments.size());

        for (int offset = 0; offset < segments.size(); offset += CHUNK_SIZE) {
            int end = Math.min(offset + CHUNK_SIZE, segments.size());
            List<TranscriptSegment> chunk = segments.subList(offset, end);
            String numbered = buildNumberedLines(chunk);
            if (numbered.length() > MAX_CHUNK_CHARS) {
                numbered = numbered.substring(0, MAX_CHUNK_CHARS) + "\n... [truncated]";
            }
            String userPrompt = "Translate these lines to English. Output exactly " + chunk.size() + " lines, one translation per line, no numbers or labels:\n\n" + numbered;
            String response;
            try {
                response = chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(userPrompt)
                        .call()
                        .content();
            } catch (Exception e) {
                log.warn("Transcript translation failed for video {} at chunk {}-{}, using original: {}",
                        transcript.getVideoId().getValue(), offset, end, e.getMessage());
                return transcript;
            }
            if (response == null || response.isBlank()) {
                log.warn("Empty translation response for video {} at chunk {}-{}, using original",
                        transcript.getVideoId().getValue(), offset, end);
                return transcript;
            }
            String raw = stripMarkdownCodeBlocks(response.trim());
            List<String> chunkLines = parseLines(raw);
            // if (chunkLines.size() != chunk.size()) {
                // log.info("!!!!!!Translation line count mismatch trabslated {}\\n, using original",
                // chunkLines);
                // log.warn("Translation line count mismatch for video {} chunk {}-{} (expected {}, got {}), using original",
                        // transcript.getVideoId().getValue(), offset, end, chunk.size(), chunkLines.size());
                // return transcript;
            // }
            allTranslatedLines.addAll(chunkLines);
        }

        List<TranscriptSegment> translatedSegments = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            TranscriptSegment orig = segments.get(i);
            String text = i < allTranslatedLines.size() ? allTranslatedLines.get(i) : orig.getText();
            translatedSegments.add(new TranscriptSegment(orig.getStartSeconds(), orig.getDurationSeconds(), text));
        }
        return new Transcript(transcript.getVideoId(), translatedSegments);
    }

    private static String buildNumberedLines(List<TranscriptSegment> segments) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(i + 1).append(". ").append(segments.get(i).getText());
        }
        return sb.toString();
    }

    private static List<String> parseLines(String raw) {
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String trimmed = line.trim();
            // Strip leading "1. ", "1) ", etc.
            if (!trimmed.isEmpty()) {
                trimmed = trimmed.replaceFirst("^\\d+[.)]\\s*", "").trim();
            }
            lines.add(trimmed);
        }
        return lines;
    }

    private static String stripMarkdownCodeBlocks(String text) {
        if (text == null || text.isEmpty()) return text;
        String s = text.strip();
        if (s.startsWith("```") && s.endsWith("```")) {
            int first = s.indexOf('\n');
            int last = s.lastIndexOf('\n');
            if (first > 0 && last > first) {
                s = s.substring(first + 1, last).trim();
            } else {
                s = s.replaceAll("^```\\w*\\n?|\\n?```$", "").trim();
            }
        }
        return s;
    }
}
