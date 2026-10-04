package com.example.currency.coindesk;

import com.example.currency.currency.Currency;
import com.example.currency.currency.CurrencyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
 * 受控來源的API串接測試：只替換外部Client，Controller、轉換器、Service與JPA/H2都實際執行。
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

    /** 驗證原始API路由確實呼叫Client，並完整保留Client回傳的JSON。 */
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

    /** 驗證轉換API串接受控來源及資料庫中的即時名稱，正確輸出UTC時間和精確匯率。 */
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

    private JsonNode sourceFixture() throws Exception {
        return mapper.readTree("{\"time\":{\"updatedISO\":\"2024-09-02T15:07:20+08:00\"},"
                + "\"bpi\":{\"USD\":{\"code\":\"USD\",\"rate_float\":31.123456789},"
                + "\"EUR\":{\"code\":\"EUR\",\"rate_float\":52.234567891}}}");
    }
}
