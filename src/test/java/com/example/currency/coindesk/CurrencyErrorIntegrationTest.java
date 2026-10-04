package com.example.currency.coindesk;

import com.example.currency.currency.Currency;
import com.example.currency.currency.CurrencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 錯誤回應與寫入保護：每個參數案例都重建臨時H2，並確認原資料沒有變動。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("classpath:test-database.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CurrencyErrorIntegrationTest {
    private static final String INVALID_REQUEST = "Invalid request: code must be 3 uppercase letters; "
            + "chineseName must contain 1-64 nonblank characters; body must be valid JSON";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @SpyBean private CurrencyRepository repository;

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCreateBodies")
    void rejectsInvalidCreateRequests(String scenario, String body) throws Exception {
        expectError(post("/api/currencies").contentType(MediaType.APPLICATION_JSON).content(body),
                400, INVALID_REQUEST);
    }

    private static Stream<Arguments> invalidCreateBodies() {
        return Stream.of(
                Arguments.of("missing code", "{\"chineseName\":\"日圓\"}"),
                Arguments.of("null code", "{\"code\":null,\"chineseName\":\"日圓\"}"),
                Arguments.of("blank code", "{\"code\":\"\",\"chineseName\":\"日圓\"}"),
                Arguments.of("lowercase code", "{\"code\":\"jpy\",\"chineseName\":\"日圓\"}"),
                Arguments.of("short code", "{\"code\":\"JP\",\"chineseName\":\"日圓\"}"),
                Arguments.of("long code", "{\"code\":\"JPYY\",\"chineseName\":\"日圓\"}"),
                Arguments.of("nonletter code", "{\"code\":\"J1Y\",\"chineseName\":\"日圓\"}"),
                Arguments.of("missing name", "{\"code\":\"JPY\"}"),
                Arguments.of("null name", "{\"code\":\"JPY\",\"chineseName\":null}"),
                Arguments.of("empty name", "{\"code\":\"JPY\",\"chineseName\":\"\"}"),
                Arguments.of("whitespace name", "{\"code\":\"JPY\",\"chineseName\":\"   \"}"),
                Arguments.of("name exceeds 64 characters", "{\"code\":\"JPY\",\"chineseName\":\""
                        + longName() + "\"}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUpdateBodies")
    void rejectsInvalidUpdateRequests(String scenario, String body) throws Exception {
        expectError(put("/api/currencies/EUR").contentType(MediaType.APPLICATION_JSON).content(body),
                400, INVALID_REQUEST);
    }

    private static Stream<Arguments> invalidUpdateBodies() {
        return Stream.of(
                Arguments.of("missing name", "{}"),
                Arguments.of("null name", "{\"chineseName\":null}"),
                Arguments.of("empty name", "{\"chineseName\":\"\"}"),
                Arguments.of("whitespace name", "{\"chineseName\":\"   \"}"),
                Arguments.of("name exceeds 64 characters", "{\"chineseName\":\"" + longName() + "\"}"));
    }

    @ParameterizedTest(name = "{0} with code {1}")
    @MethodSource("invalidPathCodes")
    void rejectsInvalidPathCodes(String method, String code) throws Exception {
        MockHttpServletRequestBuilder call = request(HttpMethod.valueOf(method), "/api/currencies/{code}", code);
        if ("PUT".equals(method)) {
            call.contentType(MediaType.APPLICATION_JSON).content("{\"chineseName\":\"新名稱\"}");
        }
        expectError(call, 400, INVALID_REQUEST);
    }

    private static Stream<Arguments> invalidPathCodes() {
        return Stream.of("GET", "PUT", "DELETE").flatMap(method ->
                Stream.of("usd", "US", "USDD", "U1D").map(code -> Arguments.of(method, code)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("unreadableBodies")
    void rejectsUnreadableBodies(String method, String scenario, String body) throws Exception {
        String path = "POST".equals(method) ? "/api/currencies" : "/api/currencies/EUR";
        expectError(request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON).content(body),
                400, INVALID_REQUEST);
    }

    private static Stream<Arguments> unreadableBodies() {
        Stream<Arguments> shared = Stream.of("POST", "PUT").flatMap(method -> Stream.of(
                Arguments.of(method, "missing body", ""),
                Arguments.of(method, "broken JSON", "{"),
                Arguments.of(method, "null body", "null"),
                Arguments.of(method, "array body", "[]")));
        return Stream.concat(shared, Stream.of(
                Arguments.of("POST", "object instead of code", "{\"code\":{},\"chineseName\":\"日圓\"}"),
                Arguments.of("PUT", "array instead of name", "{\"chineseName\":[]}")));
    }

    @ParameterizedTest(name = "{0} for nonexistent currency")
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void rejectsNonexistentCurrency(String method) throws Exception {
        MockHttpServletRequestBuilder call = request(HttpMethod.valueOf(method), "/api/currencies/JPY");
        if ("PUT".equals(method)) {
            call.contentType(MediaType.APPLICATION_JSON).content("{\"chineseName\":\"日圓\"}");
        }
        expectError(call, 404, "Currency not found: JPY");
    }

    @Test
    void duplicateCreateDoesNotOverwriteExistingCurrency() throws Exception {
        expectError(post("/api/currencies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"USD\",\"chineseName\":\"不能覆蓋美元\"}"),
                409, "Currency already exists: USD");
    }

    @Test
    void databaseDuplicateAfterStaleExistenceCheckReturnsConflict() throws Exception {
        // 模擬查重時尚未看見另一筆寫入；insert/persist、主鍵約束及交易回滾仍實際執行。
        doReturn(false).when(repository).existsById("USD");
        expectError(post("/api/currencies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"USD\",\"chineseName\":\"不能覆蓋美元\"}"),
                409, "Currency conflicts with an existing record");
    }

    private void expectError(MockHttpServletRequestBuilder call, int status, String message) throws Exception {
        mvc.perform(call)
                .andDo(result -> System.out.println(mapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8)).toPrettyString()))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.message").value(message));
        assertThat(repository.findAll()).extracting(Currency::getCode, Currency::getChineseName)
                .containsExactlyInAnyOrder(tuple("EUR", "歐元"), tuple("GBP", "英鎊"), tuple("USD", "美元"));
    }

    private static String longName() {
        return new String(new char[65]).replace('\0', '名');
    }
}
