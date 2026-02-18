package com.youtube.summary.infrastructure.fallback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls OpenAI Whisper API to transcribe audio and return segments with timestamps.
 * Uses response_format=verbose_json for segment-level start/end.
 */
class WhisperApiClient {

    private static final String WHISPER_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final long MAX_FILE_BYTES = 24 * 1024 * 1024; // 24 MB (API limit 25 MB)

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    WhisperApiClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Transcribe a single audio file. Returns segments with start (seconds), duration (seconds), text.
     * If the file is larger than 24 MB, returns empty (caller should split first).
     */
    List<WhisperSegment> transcribe(Path audioFile) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is required for Whisper");
        }
        long size = Files.size(audioFile);
        if (size > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Audio file too large for single request: " + size + " bytes (max " + MAX_FILE_BYTES + ")");
        }

        String boundary = "----WhisperBoundary" + System.currentTimeMillis();
        byte[] body = buildMultipartBody(audioFile, boundary);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WHISPER_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Whisper API error: " + response.statusCode() + " " + response.body());
        }
        return parseVerboseJson(response.body());
    }

    private byte[] buildMultipartBody(Path audioFile, String boundary) throws IOException {
        String fileName = audioFile.getFileName().toString();
        String mime = fileName.endsWith(".wav") ? "audio/wav" : "audio/mpeg";
        byte[] fileBytes = Files.readAllBytes(audioFile);

        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"\r\n");
        sb.append("Content-Type: ").append(mime).append("\r\n\r\n");
        String header = sb.toString();
        String tail = "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\nwhisper-1\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"response_format\"\r\n\r\nverbose_json\r\n"
                + "--" + boundary + "--\r\n";

        byte[] headerBytes = header.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tailBytes = tail.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] result = new byte[headerBytes.length + fileBytes.length + tailBytes.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, result, headerBytes.length, fileBytes.length);
        System.arraycopy(tailBytes, 0, result, headerBytes.length + fileBytes.length, tailBytes.length);
        return result;
    }

    private List<WhisperSegment> parseVerboseJson(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode segmentsNode = root.get("segments");
        List<WhisperSegment> list = new ArrayList<>();
        if (segmentsNode == null || !segmentsNode.isArray()) {
            return list;
        }
        for (JsonNode seg : segmentsNode) {
            double start = seg.has("start") ? seg.get("start").asDouble() : 0;
            double end = seg.has("end") ? seg.get("end").asDouble() : start;
            String text = seg.has("text") ? seg.get("text").asText("").trim() : "";
            if (!text.isEmpty()) {
                list.add(new WhisperSegment(start, end - start, text));
            }
        }
        return list;
    }

    record WhisperSegment(double startSeconds, double durationSeconds, String text) {}
}
