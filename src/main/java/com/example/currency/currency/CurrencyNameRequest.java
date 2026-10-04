package com.example.currency.currency;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class CurrencyNameRequest {
    @NotBlank
    @Size(max = 64)
    private String chineseName;

    public String getChineseName() {
        return chineseName;
    }

    public void setChineseName(String chineseName) {
        this.chineseName = chineseName;
    }
}
