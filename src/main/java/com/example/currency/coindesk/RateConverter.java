package com.example.currency.coindesk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/** 【規格實作3｜資料轉換】純資料運算，不自行呼叫HTTP或資料庫；可直接做單元測試。 */
@Component
public class RateConverter {
    private static final DateTimeFormatter OUTPUT_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    /**
     * 【規格實作3】將來源更新時間及bpi轉為時間、幣別、中文名稱與匯率。
     * UTC時區、幣別排序及缺少中文對照時回傳null，是README已說明的實作假設。
     */
    public ConvertedRateResponse convert(JsonNode source, Map<String, String> chineseNames) {
        // 補充來源檢查：缺少ISO時間或非空幣別物件時，交由API層回報轉換失敗。
        if (source == null || !source.path("time").path("updatedISO").isTextual()
                || !source.path("bpi").isObject() || source.path("bpi").size() == 0) {
            throw new IllegalArgumentException("Missing time.updatedISO or nonempty bpi");
        }
        // 【規格實作3｜更新時間】解析來源offset，依本專案假設統一為UTC並套用指定顯示格式。
        String updatedTime = OffsetDateTime.parse(source.path("time").path("updatedISO").asText())
                .withOffsetSameInstant(ZoneOffset.UTC).format(OUTPUT_TIME);
        List<ConvertedRateResponse.Rate> rates = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> entries = source.path("bpi").fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            JsonNode value = entry.getValue();
            // 【規格實作3｜幣別】保留來源代碼；代碼與bpi鍵值一致是補充資料檢查。
            String code = value.path("code").asText();
            if (!code.matches("[A-Z]{3}") || !code.equals(entry.getKey())) {
                throw new IllegalArgumentException("Invalid currency code");
            }
            // 【規格實作3｜匯率】以BigDecimal保留rate_float精度；rate字串fallback屬補充約定。
            BigDecimal rate;
            if (value.path("rate_float").isNumber()) {
                rate = value.path("rate_float").decimalValue();
            } else if (value.path("rate").isTextual()) {
                rate = new BigDecimal(value.path("rate").asText().replace(",", ""));
            } else {
                throw new IllegalArgumentException("Missing currency rate");
            }
            if (rate.signum() < 0) {
                throw new IllegalArgumentException("Negative currency rate");
            }
            // 【規格實作3｜中文名稱】使用Service提供的資料庫對照；沒有對照時保留null。
            rates.add(new ConvertedRateResponse.Rate(code, chineseNames.get(code), rate));
        }
        rates.sort(Comparator.comparing(ConvertedRateResponse.Rate::getCode));
        return new ConvertedRateResponse(updatedTime, rates);
    }
}
