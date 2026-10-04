package com.example.currency.coindesk;

import java.math.BigDecimal;
import java.util.List;

public class ConvertedRateResponse {
    private final String updatedTime;
    private final List<Rate> currencies;

    public ConvertedRateResponse(String updatedTime, List<Rate> currencies) {
        this.updatedTime = updatedTime;
        this.currencies = currencies;
    }

    public String getUpdatedTime() {
        return updatedTime;
    }

    public List<Rate> getCurrencies() {
        return currencies;
    }

    public static class Rate {
        private final String code;
        private final String chineseName;
        private final BigDecimal rate;

        public Rate(String code, String chineseName, BigDecimal rate) {
            this.code = code;
            this.chineseName = chineseName;
            this.rate = rate;
        }

        public String getCode() { return code; }
        public String getChineseName() { return chineseName; }
        public BigDecimal getRate() { return rate; }
    }
}
