package com.youtube.summary.infrastructure.llm;

import com.youtube.summary.domain.Summary;
import com.youtube.summary.domain.Transcript;
import com.youtube.summary.domain.port.SummaryGenerator;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Summary generator using Spring AI ChatClient (OpenAI, Ollama, etc.).
 */
public class SpringAiSummaryGenerator implements SummaryGenerator {

    private static final String SYSTEM_PROMPT = """
            You are a software engineer with broad experience across your tech field. You understand development, \
            architecture, DevOps, and industry practices. Given a video transcript, produce a clear, short summary \
            (2-4 paragraphs) that captures the main technical content and takeaways.
            Rules:
            - Always output the summary in English. If the transcript is in another language, translate the summary \
            into English; do not summarize in the original language.
            - After the summary paragraphs, add a single line of YouTube-style hashtags: 5-10 relevant tags using \
            common words or IT/tech terms (e.g. #Java #SpringBoot #Tutorial #SoftwareEngineering #DevOps). \
            Use the # symbol, no spaces inside tags, space-separated between tags.
            - Output only the summary text followed by the hashtag line. No other headings or meta-commentary.
            """;

    private final ChatClient chatClient;

    public SpringAiSummaryGenerator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Summary generate(Transcript transcript) {
        String text = transcript.getFullText();
        if (text == null || text.isBlank()) {
            return new Summary(transcript.getVideoId(), "(No content to summarize)");
        }
        String truncated = text.length() > 12000 ? text.substring(0, 12000) + "..." : text;
        String userPrompt = "Summarize the following video transcript. Output in English (translate if needed) and " +
                "append a line of relevant #hashtags (IT/tech terms).\n\n" + truncated;
        String summaryText = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
        return new Summary(transcript.getVideoId(), summaryText != null ? summaryText.trim() : "");
    }
}
