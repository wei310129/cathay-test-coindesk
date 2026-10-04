package com.example.currency.coindesk;

import java.time.format.DateTimeParseException;
import com.example.currency.currency.CurrencyService;
import com.example.currency.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coindesk")
public class CoindeskController {
    private final CoindeskClient client;
    private final RateConverter converter;
    private final CurrencyService currencies;

    public CoindeskController(CoindeskClient client, RateConverter converter, CurrencyService currencies) {
        this.client = client;
        this.converter = converter;
        this.currencies = currencies;
    }

    /** 【規格實作2｜原始CoinDesk API】GET /api/coindesk：呼叫指定來源並回傳原JSON結構。 */
    @GetMapping
    public JsonNode raw() {
        return client.fetch();
    }

    /**
     * 【規格實作3｜轉換API】GET /api/coindesk/converted：呼叫來源並取得資料庫中文名稱，
     * 回傳更新時間（yyyy/MM/dd HH:mm:ss）、幣別代碼、幣別中文名稱及匯率。
     */
    @GetMapping("/converted")
    public ConvertedRateResponse converted() {
        JsonNode source = client.fetch();
        try {
            return converter.convert(source, currencies.chineseNames());
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Upstream JSON cannot be converted");
        }
    }
}
