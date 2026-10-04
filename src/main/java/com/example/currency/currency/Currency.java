package com.example.currency.currency;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "currencies")
public class Currency {
    @Id
    @Column(length = 3, nullable = false)
    private String code;

    @Column(name = "chinese_name", length = 64, nullable = false)
    private String chineseName;

    protected Currency() {
    }

    public Currency(String code, String chineseName) {
        this.code = code;
        this.chineseName = chineseName;
    }

    public String getCode() {
        return code;
    }

    public String getChineseName() {
        return chineseName;
    }

    public void setChineseName(String chineseName) {
        this.chineseName = chineseName;
    }
}
