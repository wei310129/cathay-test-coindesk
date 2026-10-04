package com.example.currency.coindesk;

import com.example.currency.currency.Currency;
import com.example.currency.currency.CurrencyRepository;
import com.example.currency.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 【測試要求3、4】原始及轉換API的受控來源串接測試，包含正常與失敗測試。
 * 只替換外部Client，Controller、轉換器、Service與JPA/H2都實際執行。
 * 每個案例重建臨時H2；測試fixture的時間、匯率與名稱是可驗證的輸入，不限制真實上游資料。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("classpath:test-database.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CoindeskApiIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CurrencyRepository repository;
    @MockBean private CoindeskClient client;

    /** 【測試要求3】驗證原始API路由確實呼叫Client，並完整保留Client回傳的JSON。 */
    @Test
    void rawApiReturnsClientJson() throws Exception {
        JsonNode source = sourceFixture();
        when(client.fetch()).thenReturn(source);

        mvc.perform(get("/api/coindesk"))
                .andDo(result -> System.out.println(mapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8)).toPrettyString()))
                .andExpect(status().isOk())
                .andExpect(content().json(source.toString(), true));

        verify(client).fetch();
    }

    /** 【測試要求4】驗證轉換API串接受控來源及資料庫中的即時名稱，正確輸出UTC時間和精確匯率。 */
    @Test
    void convertedApiUsesClientDataAndDatabaseNames() throws Exception {
        JsonNode source = sourceFixture();
        when(client.fetch()).thenReturn(source);
        Currency usd = repository.findById("USD").get();
        usd.setChineseName("資料庫測試美元");
        repository.saveAndFlush(usd);

        String json = mvc.perform(get("/api/coindesk/converted"))
                .andDo(result -> System.out.println(mapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8)).toPrettyString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedTime").value("2024/09/02 07:07:20"))
                .andExpect(jsonPath("$.currencies").isArray())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<JsonNode> currencies = new ArrayList<>();
        mapper.readTree(json).path("currencies").forEach(currencies::add);
        assertThat(currencies).extracting(currency -> currency.path("code").asText(),
                        currency -> currency.path("chineseName").asText(),
                        currency -> currency.path("rate").decimalValue())
                .containsExactlyInAnyOrder(
                        tuple("USD", "資料庫測試美元", new BigDecimal("31.123456789")),
                        tuple("EUR", "歐元", new BigDecimal("52.234567891")));
        verify(client).fetch();
    }

    /** 【測試要求3、4｜失敗測試】上游呼叫失敗時，原始及轉換API回傳一致的502 JSON。 */
    @ParameterizedTest(name = "upstream failure at {0}")
    @ValueSource(strings = {"/api/coindesk", "/api/coindesk/converted"})
    void upstreamFailureReturnsBadGateway(String path) throws Exception {
        when(client.fetch()).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "Unable to retrieve upstream JSON"));
        expectBadGateway(path, "Unable to retrieve upstream JSON");
    }

    /** 【測試要求4｜失敗測試】來源結構、時間、幣別代碼或匯率無效時，轉換API回傳502 JSON。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidConversionSources")
    void invalidSourceReturnsBadGateway(String scenario, String sourceJson) throws Exception {
        when(client.fetch()).thenReturn(sourceJson == null ? null : mapper.readTree(sourceJson));
        expectBadGateway("/api/coindesk/converted", "Upstream JSON cannot be converted");
    }

    private static Stream<Arguments> invalidConversionSources() {
        String time = "\"time\":{\"updatedISO\":\"2024-09-02T15:07:20+08:00\"}";
        String bpi = "\"bpi\":{\"USD\":{\"code\":\"USD\",\"rate_float\":31.1}}";
        Stream<Arguments> structural = Stream.of(
                Arguments.of("null source", (String) null),
                Arguments.of("JSON null source", "null"),
                Arguments.of("array source", "[]"),
                Arguments.of("missing time", "{" + bpi + "}"),
                Arguments.of("missing ISO time", "{\"time\":{}," + bpi + "}"),
                Arguments.of("nontextual ISO time", "{\"time\":{\"updatedISO\":123}," + bpi + "}"),
                Arguments.of("empty ISO time", "{\"time\":{\"updatedISO\":\"\"}," + bpi + "}"),
                Arguments.of("malformed ISO time", "{\"time\":{\"updatedISO\":\"invalid\"}," + bpi + "}"),
                Arguments.of("impossible date", "{\"time\":{\"updatedISO\":\"2024-02-30T15:07:20Z\"}," + bpi + "}"),
                Arguments.of("time without offset", "{\"time\":{\"updatedISO\":\"2024-09-02T15:07:20\"}," + bpi + "}"),
                Arguments.of("missing bpi", "{" + time + "}"),
                Arguments.of("empty bpi", "{" + time + ",\"bpi\":{}}"),
                Arguments.of("array bpi", "{" + time + ",\"bpi\":[]}"),
                Arguments.of("null bpi", "{" + time + ",\"bpi\":null}"));
        Stream<Arguments> currencies = Stream.of(
                Arguments.of("missing currency code", "{\"rate_float\":31.1}"),
                Arguments.of("lowercase currency code", "{\"code\":\"usd\",\"rate_float\":31.1}"),
                Arguments.of("code differs from key", "{\"code\":\"EUR\",\"rate_float\":31.1}"),
                Arguments.of("nonobject currency", "null"),
                Arguments.of("missing rate", "{\"code\":\"USD\"}"),
                Arguments.of("wrong rate type", "{\"code\":\"USD\",\"rate_float\":\"31.1\",\"rate\":123}"),
                Arguments.of("nonnumeric fallback rate", "{\"code\":\"USD\",\"rate\":\"invalid\"}"),
                Arguments.of("empty fallback rate", "{\"code\":\"USD\",\"rate\":\"\"}"),
                Arguments.of("negative numeric rate", "{\"code\":\"USD\",\"rate_float\":-1.25}"),
                Arguments.of("negative fallback rate", "{\"code\":\"USD\",\"rate\":\"-1.25\"}"))
                .map(args -> Arguments.of(args.get()[0], "{" + time + ",\"bpi\":{\"USD\":" + args.get()[1] + "}}"));
        return Stream.concat(structural, currencies);
    }

    private void expectBadGateway(String path, String message) throws Exception {
        mvc.perform(get(path))
                .andDo(result -> System.out.println(mapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8)).toPrettyString()))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value(message));
        verify(client).fetch();
    }

    private JsonNode sourceFixture() throws Exception {
        return mapper.readTree("{\"time\":{\"updatedISO\":\"2024-09-02T15:07:20+08:00\"},"
                + "\"bpi\":{\"USD\":{\"code\":\"USD\",\"rate_float\":31.123456789},"
                + "\"EUR\":{\"code\":\"EUR\",\"rate_float\":52.234567891}}}");
    }
}
