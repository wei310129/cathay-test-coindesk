package com.example.currency.coindesk;

import com.example.currency.error.ApiException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.stream.Stream;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 以受控HTTP回應測試真實Client的錯誤處理，不連外、不啟動Spring或資料庫。 */
class CoindeskClientTest {
    private static final String URL = "https://upstream.example/coindesk.json";
    private CoindeskClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        MockServerRestTemplateCustomizer customizer = new MockServerRestTemplateCustomizer();
        client = new CoindeskClient(new RestTemplateBuilder(customizer), URL, 5000, 10000);
        server = customizer.getServer();
    }

    @ParameterizedTest(name = "upstream HTTP {0}")
    @ValueSource(ints = {400, 404, 500, 503})
    void rejectsUpstreamHttpErrors(int status) {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.valueOf(status)));
        expectBadGateway("Unable to retrieve upstream JSON");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidJsonRoots")
    void rejectsEmptyOrNonobjectJson(String scenario, String body) {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        expectBadGateway("Upstream returned an empty or invalid JSON object");
    }

    private static Stream<Arguments> invalidJsonRoots() {
        return Stream.of(
                Arguments.of("empty response body", ""),
                Arguments.of("JSON null", "null"),
                Arguments.of("JSON array", "[]"),
                Arguments.of("JSON string", "\"invalid\""),
                Arguments.of("JSON number", "123"),
                Arguments.of("JSON boolean", "true"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unreadableResponses")
    void rejectsUnreadableResponses(String scenario, String body, MediaType contentType) {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, contentType));
        expectBadGateway("Unable to retrieve upstream JSON");
    }

    private static Stream<Arguments> unreadableResponses() {
        return Stream.of(
                Arguments.of("malformed JSON", "{", MediaType.APPLICATION_JSON),
                Arguments.of("HTML instead of JSON", "<html>upstream error</html>", MediaType.TEXT_HTML));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"connection refused", "connect timeout", "read timeout"})
    void convertsTransportFailuresToBadGateway(String scenario) {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET)).andRespond(request -> {
            // 模擬HTTP傳輸層的IOException，驗證Client如何處理；不依賴真實網路或計時等待。
            if ("connection refused".equals(scenario)) {
                throw new ConnectException(scenario);
            }
            throw new SocketTimeoutException(scenario);
        });
        expectBadGateway("Unable to retrieve upstream JSON");
    }

    private void expectBadGateway(String message) {
        assertThatThrownBy(client::fetch).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(exception.getMessage()).isEqualTo(message);
        });
        server.verify();
    }
}
