package com.example.currency.coindesk;

import com.example.currency.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

/** 【規格實作2、3共用】負責指定來源的HTTP呼叫；不在此處轉換時間或查詢中文名稱。 */
@Component
public class CoindeskClient {
    private final RestTemplate restTemplate;
    private final String url;

    public CoindeskClient(RestTemplateBuilder builder,
                          @Value("${coindesk.url}") String url,
                          @Value("${coindesk.connect-timeout-ms}") int connectTimeout,
                          @Value("${coindesk.read-timeout-ms}") int readTimeout) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout)).build();
        this.url = url;
    }

    /**
     * 【規格實作2｜呼叫來源】以GET取得coindesk.url設定的原始JSON，供原始及轉換API使用。
     * 正常執行及live測試會真正連線；mock測試由MockRestServiceServer攔截此HTTP請求。
     * 逾時、上游錯誤及無有效JSON時回傳502，屬補充錯誤處理約定。
     */
    public JsonNode fetch() {
        try {
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            if (response == null || !response.isObject()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Upstream returned an empty or invalid JSON object");
            }
            return response;
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to retrieve upstream JSON");
        }
    }

    // Package-visible access allows MockRestServiceServer to exercise the actual HTTP client.
    RestTemplate restTemplate() {
        return restTemplate;
    }
}
