package com.example.currency.currency;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class CurrencyCreateRequest extends CurrencyNameRequest {
    @NotNull
    @Pattern(regexp = "[A-Z]{3}")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
