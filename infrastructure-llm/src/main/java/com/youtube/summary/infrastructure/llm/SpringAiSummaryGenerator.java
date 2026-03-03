package com.youtube.summary.infrastructure.llm;

import com.youtube.summary.domain.Summary;
import com.youtube.summary.domain.Transcript;
import com.youtube.summary.domain.port.SummaryGenerator;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Summary generator using Spring AI ChatClient (OpenAI, Ollama, etc.).
 */
public class SpringAiSummaryGenerator implements SummaryGenerator {

    private static final String SYSTEM_PROMPT = """
            CRITICAL: You MUST respond entirely in English. If the transcript is in another language, first translate \
            it to English in your mind, then summarize that English version. Never output summary or tags in any other language.
            You are a software engineer. Given a video transcript, produce:
            1. A short summary in English (2-4 paragraphs) with the main technical content and takeaways.
            2. On the very last line of your response: exactly 5-10 software/tech hashtags in English, nothing else. \
            Example: #Java #SpringBoot #API #Tutorial #SoftwareEngineering
            Format: Write the summary paragraphs, then a blank line, then one line containing ONLY hashtags and spaces \
            (e.g. #Java #SpringBoot #API #Tutorial #SoftwareEngineering). No other text or labels. The last line MUST \
            be hashtags so the system can parse it.
            """;

    private final ChatClient chatClient;

    public SpringAiSummaryGenerator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Summary generate(Transcript transcript) {
        String text = transcript.getFullText();
        if (text == null || text.isBlank()) {
            return new Summary(transcript.getVideoId(), "(No content to summarize)", List.of());
        }
        String truncated = text.length() > 12000 ? text.substring(0, 12000) + "..." : text;
        String userPrompt = "Respond ONLY in English. Summarize this transcript in 2-4 paragraphs (translate to " +
                "English first if needed). End your response with a single line of 5-10 #hashtags, software/tech " +
                "topics only, e.g. #Java #API #Tutorial.\n\nTranscript:\n\n" + truncated;
        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
        String raw = response != null ? stripMarkdownCodeBlocks(response.trim()) : "";
        return parseSummaryAndTags(transcript.getVideoId(), raw);
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

    private static final Pattern HASHTAG = Pattern.compile("#\\w+");

    static Summary parseSummaryAndTags(com.youtube.summary.domain.VideoId videoId, String raw) {
        if (raw.isEmpty()) {
            return new Summary(videoId, "", List.of());
        }
        String[] lines = raw.split("\\r?\\n");
        List<String> tags = new ArrayList<>();
        int firstTagLineIndex = -1;
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].trim().contains("#")) {
                firstTagLineIndex = i;
            } else if (firstTagLineIndex >= 0) {
                break;
            }
        }
        if (firstTagLineIndex >= 0) {
            for (int i = firstTagLineIndex; i < lines.length; i++) {
                Matcher m = HASHTAG.matcher(lines[i]);
                while (m.find()) {
                    String tag = m.group();
                    if (!tags.contains(tag)) tags.add(tag);
                }
            }
        }
        String summaryText;
        if (firstTagLineIndex >= 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < firstTagLineIndex; i++) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(lines[i]);
            }
            summaryText = sb.toString().trim();
        } else {
            summaryText = raw;
            Matcher m = HASHTAG.matcher(raw);
            while (m.find()) {
                String tag = m.group();
                if (!tags.contains(tag)) tags.add(tag);
            }
        }
        return new Summary(videoId, summaryText, tags);
    }
}
