package com.example.devlogapp.ai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 보안 테스트: apiKey 가 반환값·예외 메시지·로그 어디에도 노출되지 않음을 검증.
 * PLAN.md §5.3, 에이전트 시스템 프롬프트 "보안 테스트 (필수)" 항목.
 */
class GeminiAiClientApiKeyLeakTest {

    private static final String TEST_API_KEY = "AIzaSyTestSuperSecretKeyDoNotLeakXYZ123";
    private static final String MODEL = "gemini-2.5-flash";
    private static final String GENERATE_PATH = "/v1beta/models/" + MODEL + ":generateContent";

    private WireMockServer wireMock;
    private GeminiAiClient client;
    private ListAppender logAppender;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        client = new GeminiAiClient(MODEL, "http://localhost:" + wireMock.port());

        logAppender = new ListAppender();
        logAppender.start();
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAppender(logAppender);
        wireMock.stop();
    }

    // ── 200 정상 케이스 ──────────────────────────────────────────────

    @Test
    void successCase_returnValueDoesNotContainApiKey() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"candidates":[{"content":{"parts":[{"text":"블로그 결과"}]}}]}
                                """)));

        String result = client.generate("프롬프트", TEST_API_KEY);

        assertThat(result).doesNotContain(TEST_API_KEY);
    }

    @Test
    void successCase_logDoesNotContainApiKey() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"candidates":[{"content":{"parts":[{"text":"결과"}]}}]}
                                """)));

        client.generate("프롬프트", TEST_API_KEY);

        assertNoApiKeyInLogs();
    }

    // ── 4xx 케이스 ──────────────────────────────────────────────────

    @Test
    void http401Case_exceptionMessageDoesNotContainApiKey() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("{\"error\":{\"message\":\"Incorrect API key provided: " + TEST_API_KEY + "\"}}")));

        assertThatThrownBy(() -> client.generate("프롬프트", TEST_API_KEY))
                .isInstanceOf(AiException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(TEST_API_KEY));
    }

    @Test
    void http400Case_logDoesNotContainApiKey() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("{\"error\":{\"message\":\"bad request\"}}")));

        try {
            client.generate("프롬프트", TEST_API_KEY);
        } catch (AiException ignored) {
        }

        assertNoApiKeyInLogs();
    }

    // ── 5xx 케이스 ──────────────────────────────────────────────────

    @Test
    void http500Case_exceptionMessageDoesNotContainApiKey() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("{\"error\":{\"message\":\"Internal server error, key=" + TEST_API_KEY + "\"}}")));

        assertThatThrownBy(() -> client.generate("프롬프트", TEST_API_KEY))
                .isInstanceOf(AiException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(TEST_API_KEY));
    }

    @Test
    void http503Case_logDoesNotContainApiKey() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("{\"error\":{\"message\":\"service unavailable\"}}")));

        try {
            client.generate("프롬프트", TEST_API_KEY);
        } catch (AiException ignored) {
        }

        assertNoApiKeyInLogs();
    }

    // ── 전송 검증: apiKey 가 x-goog-api-key 헤더로 실제 전달됐는지 ──

    @Test
    void apiKeyIsTransmittedInGoogApiKeyHeader() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}
                                """)));

        client.generate("프롬프트", TEST_API_KEY);

        List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(urlEqualTo(GENERATE_PATH)));
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getHeader("x-goog-api-key")).isEqualTo(TEST_API_KEY);
    }

    // ── 키가 URL 쿼리에 새지 않았는지 (Google 은 ?key= 도 허용하므로 명시 검증) ──

    @Test
    void apiKeyIsNotEmbeddedInUrl() {
        wireMock.stubFor(post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}
                                """)));

        client.generate("프롬프트", TEST_API_KEY);

        List<LoggedRequest> all = wireMock.findAll(postRequestedFor(urlPathEqualTo(GENERATE_PATH)));
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getUrl())
                .as("apiKey 가 URL 에 노출되면 안 됨 — 헤더 인증만 사용")
                .doesNotContain(TEST_API_KEY);
    }

    // ── 필드 reflection: GeminiAiClient 인스턴스에 apiKey 가 stash 됐는지 ──

    @Test
    void clientInstance_hasNoApiKeyField() throws Exception {
        for (Field field : client.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(client);
            if (value instanceof String strValue) {
                assertThat(strValue)
                        .as("필드 '%s' 에 apiKey 값이 저장되지 않아야 함", field.getName())
                        .doesNotContain(TEST_API_KEY);
            }
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    private void assertNoApiKeyInLogs() {
        for (ILoggingEvent event : logAppender.getEvents()) {
            assertThat(event.getFormattedMessage())
                    .as("로그 메시지에 apiKey 포함되지 않아야 함")
                    .doesNotContain(TEST_API_KEY);
        }
    }

    /** 로그 이벤트를 메모리에 수집하는 Logback Appender. */
    static class ListAppender extends AppenderBase<ILoggingEvent> {
        private final List<ILoggingEvent> events = new ArrayList<>();

        @Override
        protected void append(ILoggingEvent eventObject) {
            events.add(eventObject);
        }

        List<ILoggingEvent> getEvents() {
            return List.copyOf(events);
        }
    }
}
