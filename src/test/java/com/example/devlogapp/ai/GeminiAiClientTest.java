package com.example.devlogapp.ai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GeminiAiClient 기능 테스트 — WireMock 사용, 실제 API 키 호출 없음. (CLAUDE.md 테스트 정책)
 */
class GeminiAiClientTest {

    private static final String MODEL = "gemini-2.5-flash";
    private static final String GENERATE_PATH = "/v1beta/models/" + MODEL + ":generateContent";

    private WireMockServer wireMock;
    private GeminiAiClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        client = new GeminiAiClient(MODEL, "http://localhost:" + wireMock.port());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void successResponse_returnsCandidatesText() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "candidates": [
                                    {
                                      "content": {
                                        "parts": [
                                          {"text": "블로그 초안 내용입니다."}
                                        ],
                                        "role": "model"
                                      },
                                      "finishReason": "STOP"
                                    }
                                  ]
                                }
                                """)));

        String result = client.generate("테스트 프롬프트", "test-key-123");

        assertThat(result).isEqualTo("블로그 초안 내용입니다.");
    }

    @Test
    void http400Response_throwsAiException() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"invalid request\"}}")));

        assertThatThrownBy(() -> client.generate("프롬프트", "test-key-123"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("400");
    }

    @Test
    void http500Response_throwsAiException() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("{\"error\":{\"message\":\"server error\"}}")));

        assertThatThrownBy(() -> client.generate("프롬프트", "test-key-123"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("500");
    }

    @Test
    void textWithEscapeSequences_isUnescapedCorrectly() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "candidates": [
                                    {
                                      "content": {
                                        "parts": [{"text": "# 제목\\n\\n## 섹션\\n내용"}]
                                      }
                                    }
                                  ]
                                }
                                """)));

        String result = client.generate("프롬프트", "test-key");

        assertThat(result).isEqualTo("# 제목\n\n## 섹션\n내용");
    }

    @Test
    void requestSentWithCorrectHeaders() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}
                                """)));

        client.generate("프롬프트", "my-secret-key");

        wireMock.verify(postRequestedFor(urlEqualTo(GENERATE_PATH))
                .withHeader("x-goog-api-key", equalTo("my-secret-key"))
                .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test
    void requestBodyContainsPromptInContentsParts() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"candidates":[{"content":{"parts":[{"text":"결과"}]}}]}
                                """)));

        client.generate("내 프롬프트 내용", "test-key");

        wireMock.verify(postRequestedFor(urlEqualTo(GENERATE_PATH))
                .withRequestBody(containing("contents"))
                .withRequestBody(containing("parts"))
                .withRequestBody(containing("내 프롬프트 내용")));
    }
}
