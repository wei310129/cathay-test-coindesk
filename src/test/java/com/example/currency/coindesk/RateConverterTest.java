package com.example.currency.coindesk;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** 【測試要求1】一個正常情境的純單元測試；直接測轉換器，不啟動Spring/H2/HTTP。 */
class RateConverterTest {
    /**
     * 【測試要求1】以受控JSON及中文對照驗證正確時間換算/格式、代碼、名稱查表及來源匯率保留。
     * 輸入值是測試fixture，不是規格固定值；不要求幣別排序，最後印出[UNIT]轉換JSON。
     */
    @Test
    void convertsTimeChineseNamesAndExactDecimalRates() throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        JsonNode source = mapper.readTree("{\"time\":{\"updatedISO\":\"2024-09-02T15:07:20+08:00\"},"
                + "\"bpi\":{\"TWD\":{\"code\":\"TWD\",\"rate_float\":31.123456789},"
                + "\"JPY\":{\"code\":\"JPY\",\"rate_float\":145.6789}}}");
        Map<String, String> names = new HashMap<>();
        names.put("TWD", "測試台幣");
        names.put("JPY", "測試日圓");

        ConvertedRateResponse result = new RateConverter().convert(source, names);

        assertThat(result.getUpdatedTime()).isEqualTo("2024/09/02 07:07:20");
        assertThat(result.getCurrencies()).extracting(ConvertedRateResponse.Rate::getCode,
                        ConvertedRateResponse.Rate::getChineseName, ConvertedRateResponse.Rate::getRate)
                .containsExactlyInAnyOrder(
                        tuple("TWD", "測試台幣", new BigDecimal("31.123456789")),
                        tuple("JPY", "測試日圓", new BigDecimal("145.6789")));
        System.out.println("[UNIT] Converted fixture: " + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
