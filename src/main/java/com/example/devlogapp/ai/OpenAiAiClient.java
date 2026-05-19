package com.example.devlogapp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions API 어댑터.
 *
 * 보안 불변식 (PLAN.md §5.1~§5.3):
 * - apiKey 는 generate() 인자로만 받고, Authorization 헤더에 실어 HTTP 호출 후 즉시 폐기.
 * - 이 클래스에 apiKey 를 저장하는 필드·정적 변수·ThreadLocal·MDC 없음.
 * - 로그에는 모델명·상태코드·요청 길이만 기록.
 * - 예외 메시지에 apiKey·응답 본문 포함 금지.
 */
@Component
public class OpenAiAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String model;
    private final String baseUrl;

    public OpenAiAiClient(
            @Value("${devlog.ai.openai.model:gpt-4o-mini}") String model,
            @Value("${devlog.ai.openai.base-url:https://api.openai.com}") String baseUrl) {
        this.model = model;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String generate(String prompt, String apiKey) {
        String requestBody = buildRequestBody(prompt);
        log.debug("OpenAI 요청: model={}, prompt_length={}", model, prompt.length());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AiException(0);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            log.warn("OpenAI 호출 실패: status={}", status);
            throw new AiException(status);
        }

        log.debug("OpenAI 응답 수신: status={}", status);
        return extractContent(response.body(), status);
    }

    private String buildRequestBody(String prompt) {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            ));
        } catch (JsonProcessingException e) {
            throw new AiException(0);
        }
    }

    /** 응답 JSON 에서 choices[0].message.content 추출. */
    private String extractContent(String responseBody, int status) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) {
                throw new AiException(status);
            }
            return content.asText();
        } catch (IOException e) {
            throw new AiException(status);
        }
    }
}
