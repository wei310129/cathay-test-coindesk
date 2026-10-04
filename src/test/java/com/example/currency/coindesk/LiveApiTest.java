package com.example.currency.coindesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 【測試要求3、4｜真實上游】原始來源直接呼叫CoindeskClient；轉換API使用MockMvc，不使用HTTP mock。
 * 每個案例使用獨立H2記憶體資料庫，由schema.sql/data.sql初始化；結束後銷毀context與資料庫。
 * 預設mvn test即執行，必須能連線至指定網址；沒有額外profile、mock或失敗跳過。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("classpath:test-database.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LiveApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private CoindeskClient client;
    @Autowired private ObjectMapper mapper;

    /**
     * 【測試要求3｜live smoke】直接呼叫CoindeskClient.fetch一次，逐欄驗證型別、值與已知格式。
     * 輸出：System.out.println顯示真實來源JSON；不限定當下代碼/筆數/匯率值。
     */
    @Test
    void rawApiActuallyCallsSpecifiedRemoteUrl() {
        JsonNode response = client.fetch();
        System.out.println(response.toPrettyString());
        assertThat(response.isObject()).as("root must be an object").isTrue();
        assertThat(response.path("time").isObject()).as("time must be an object").isTrue();
        String updated = requiredText(response, "/time/updated");
        String updatedISO = requiredText(response, "/time/updatedISO");
        String updatedUk = requiredText(response, "/time/updateduk");
        DateTimeFormatter displayTime = DateTimeFormatter.ofPattern("MMM d, uuuu HH:mm:ss z", Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.STRICT);
        DateTimeFormatter ukTime = DateTimeFormatter.ofPattern("MMM d, uuuu 'at' HH:mm z", Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.STRICT);
        assertThatCode(() -> ZonedDateTime.parse(updated, displayTime))
                .as("time.updated must contain a valid date, time and zone").doesNotThrowAnyException();
        assertThatCode(() -> OffsetDateTime.parse(updatedISO))
                .as("time.updatedISO must be an ISO date-time with an offset").doesNotThrowAnyException();
        assertThatCode(() -> ZonedDateTime.parse(updatedUk, ukTime))
                .as("time.updateduk must contain a valid UK display date-time").doesNotThrowAnyException();
        requiredText(response, "/disclaimer");
        requiredText(response, "/chartName");

        assertThat(response.path("bpi").isObject()).as("bpi must be an object").isTrue();
        assertThat(response.path("bpi").size()).as("bpi must contain currencies").isPositive();
        Iterator<Map.Entry<String, JsonNode>> entries = response.path("bpi").fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            assertThat(entry.getKey()).as("bpi currency key").matches("[A-Z]{3}");
            assertThat(entry.getValue().isObject()).as("bpi.%s must be an object", entry.getKey()).isTrue();
            String currencyPath = "/bpi/" + entry.getKey();
            assertThat(requiredText(response, currencyPath + "/code"))
                    .as("%s/code", currencyPath).matches("[A-Z]{3}").isEqualTo(entry.getKey());
            requiredText(response, currencyPath + "/symbol");
            requiredText(response, currencyPath + "/description");
            assertThat(requiredText(response, currencyPath + "/rate"))
                    .as("%s/rate must be a numeric string with optional thousands separators", currencyPath)
                    .matches("(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d+)?");
            JsonNode numericRate = entry.getValue().path("rate_float");
            assertThat(numericRate.isNumber()).as("%s/rate_float must be a number", currencyPath).isTrue();
            assertThat(numericRate.decimalValue()).as("%s/rate_float", currencyPath)
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
    }

    private String requiredText(JsonNode response, String pointer) {
        JsonNode value = response.at(pointer);
        assertThat(value.isTextual()).as("%s must be a string", pointer).isTrue();
        assertThat(value.asText()).as("%s must not be blank", pointer).isNotBlank();
        return value.asText();
    }

    /**
     * 【測試要求4】呼叫轉換API一次，確認HTTP 200，僅以pretty JSON印出UTF-8 response body。
     */
    @Test
    void convertedApiActuallyCallsSpecifiedRemoteUrl() throws Exception {
        mvc.perform(get("/api/coindesk/converted"))
                .andDo(result -> System.out.println(mapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8)).toPrettyString()))
                .andExpect(status().isOk());
    }
}
