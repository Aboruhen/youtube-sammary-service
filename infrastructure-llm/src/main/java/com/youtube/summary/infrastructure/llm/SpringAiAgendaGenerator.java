package com.youtube.summary.infrastructure.llm;

import com.youtube.summary.domain.Agenda;
import com.youtube.summary.domain.AgendaItem;
import com.youtube.summary.domain.Transcript;
import com.youtube.summary.domain.TranscriptSegment;
import com.youtube.summary.domain.port.AgendaGenerator;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Agenda generator using Spring AI. Asks LLM for topic titles only; timings are approximated from video duration.
 * Prompt text is injected (e.g. from application.yml) so it can be configured externally.
 */
public class SpringAiAgendaGenerator implements AgendaGenerator {

    private static final String DEFAULT_SYSTEM = """
            MANDATORY: All section titles MUST be in English only. If the transcript is in another language, \
            translate the meaning of each section into English for the title.
            You are an agenda generator. The transcript includes timestamps like [0:00] [1:30].
            Your job: group the content by context into logical sections and give each section a TOPIC-style title \
            that describes what that section is about, in English.
            Output ONLY lines in this format: M:SS - Section title
            Rules:
            - Group by context: one line per logical section. Typical video = 5-15 sections.
            - Use the start timestamp of each section from the transcript [M:SS].
            - Each title must be in English: a short TOPIC that captures the context (e.g. "Setting up the project", \
            "How authentication works", "Configuring the database"). Be specific to the content, not generic.
            - Do NOT use generic labels like "Part 1", "Part 2", "Main Topic", or "Section 3". Never use "Part" with a number.
            - Keep titles concise but descriptive (roughly 3-8 words). No intro, no numbering prefix.
            """;
    private static final String DEFAULT_USER_PREFIX = "Respond ONLY in English. Output the agenda in English only: if the transcript is not in English, translate each section title to English. "
            + "Group this transcript into logical sections. For each section output one line: M:SS - Section title. "
            + "Use timestamps from the brackets [M:SS]. Each title must be a topic in English that describes that section's content (e.g. 'Installing dependencies', 'Explaining the algorithm'). "
            + "Just a list of topics about the transcripts, no other text. No examples, no conclusion, no explanation. "
            + "Do not use 'Part 1', 'Part 2' or other generic labels. Aim for 5-15 sections. "
            + "Example:\n0:00 - Introduction to the project\n2:30 - Installing and configuring the tools\n5:00 - How the API handles requests\n12:00 - Running the demo\n18:00 - Summary and next steps";

    /** Strip leading list style: "1. ", "1) ", "- ", "* ", or "M:SS - " (timestamp prefix). */
    private static final String LEADING_PREFIX_REGEX = "^\\s*(?:\\d+[.)]\\s*|[-*]\\s*|\\d{1,5}:\\d{1,2}(?::\\d{1,2})?\\s*[-–—:\\s]*)";

    private final ChatClient chatClient;
    private final String systemPrompt;
    private final String userPrefix;

    public SpringAiAgendaGenerator(ChatClient chatClient) {
        this(chatClient, null, null);
    }

    public SpringAiAgendaGenerator(ChatClient chatClient, String systemPrompt, String userPrefix) {
        this.chatClient = chatClient;
        this.systemPrompt = (systemPrompt != null && !systemPrompt.isBlank()) ? systemPrompt : DEFAULT_SYSTEM;
        this.userPrefix = (userPrefix != null && !userPrefix.isBlank()) ? userPrefix : DEFAULT_USER_PREFIX;
    }

    @Override
    public Agenda generate(Transcript transcript) {
        String text = transcript.getFullText();
        if (text == null || text.isBlank()) {
            return new Agenda(transcript.getVideoId(), List.of());
        }
        String transcriptWithTimings = buildTranscriptWithTimings(transcript);
        String truncated = transcriptWithTimings.length() > 15000 ? transcriptWithTimings.substring(0, 15000) + "..." : transcriptWithTimings;
        String userPrompt = userPrefix + "\n\nTranscript:\n\n" + truncated;
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        String raw = response != null ? stripMarkdownCodeBlocks(response.trim()) : "";
        List<String> topics = parseTopicLines(raw);
        if (topics.isEmpty()) {
            return new Agenda(transcript.getVideoId(), List.of());
        }
        double videoEndSeconds = getVideoEndSeconds(transcript);
        List<AgendaItem> items = approximateTimings(topics, videoEndSeconds);
        return new Agenda(transcript.getVideoId(), items);
    }

    private static double getVideoEndSeconds(Transcript transcript) {
        if (transcript.getSegments().isEmpty()) return 0;
        TranscriptSegment last = transcript.getSegments().get(transcript.getSegments().size() - 1);
        return last.getEndSeconds();
    }

    /** Parse response into topic titles only (ignore any timestamps in the line; we approximate later). */
    private static List<String> parseTopicLines(String response) {
        if (response == null || response.isBlank()) return List.of();
        List<String> topics = new ArrayList<>();
        for (String line : response.split("\\r?\\n")) {
            String stripped = line.replaceFirst(LEADING_PREFIX_REGEX, "").trim();
            if (stripped.isEmpty()) continue;
            // Take the rest of the line as title (in case AI still outputs "M:SS - Title", we take Title)
            String title = stripped;
            if (title.isEmpty()) title = "Topic";
            topics.add(title);
        }
        return topics;
    }

    /** Assign approximate start/end times by splitting video duration evenly across topics. */
    private static List<AgendaItem> approximateTimings(List<String> topics, double videoEndSeconds) {
        if (topics.isEmpty() || videoEndSeconds <= 0) return List.of();
        int n = topics.size();
        double segmentDuration = videoEndSeconds / n;
        List<AgendaItem> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double start = i * segmentDuration;
            double end = (i + 1) * segmentDuration;
            if (i == n - 1) end = videoEndSeconds;
            items.add(new AgendaItem(topics.get(i), start, end));
        }
        return items;
    }

    private static String buildTranscriptWithTimings(Transcript transcript) {
        if (transcript.getSegments().isEmpty()) {
            return transcript.getFullText();
        }
        StringBuilder sb = new StringBuilder();
        for (TranscriptSegment seg : transcript.getSegments()) {
            int min = (int) (seg.getStartSeconds() / 60);
            int sec = (int) (seg.getStartSeconds() % 60);
            if (sb.length() > 0) sb.append(" ");
            sb.append("[").append(min).append(":").append(String.format("%02d", sec)).append("] ");
            sb.append(seg.getText());
        }
        return sb.toString().trim();
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
