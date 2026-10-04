package com.example.currency.coindesk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 【測試要求2】全部查詢、單筆查詢、新增、修改、刪除各一個獨立API測試。
 * MockMvc執行真實Controller/Service/JPA/H2；每個案例只呼叫一個目標API，不mock業務或資料庫。
 * 每個案例使用獨立H2記憶體資料庫，由schema.sql/data.sql建表及初始化；結束後銷毀context與資料庫。
 * 使用MockMvc呼叫及驗證目標API，僅以pretty JSON印出UTF-8 response body，不執行JDBC準備或JDBC斷言。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("classpath:test-database.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApiIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    /** 【測試要求2｜全部查詢】呼叫GET一次，印出pretty JSON，驗證清單中的代碼與中文名稱有值。 */
    @Test
    void queryAllCurrenciesApi() throws Exception {
        mvc.perform(get("/api/currencies"))
                .andDo(result -> System.out.println(mapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8)).toPrettyString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isNotEmpty())
                .andExpect(jsonPath("$").value(everyItem(allOf(
                        hasEntry(is("code"), matchesPattern("[A-Z]{3}")),
                        hasEntry(is("chineseName"), matchesPattern("(?s).*\\S.*"))))));
    }

    /** 【測試要求2｜查詢】查詢SQL fixture中的USD；只驗證所查代碼/中文對照，不要求全表筆數或排序。 */
    @Test
    void queryCurrencyApi() throws Exception {
        String code = "USD";
        String expectedName = "美元"; // 對應data.sql中的本次查詢fixture，並非限定所有幣別。
        mvc.perform(get("/api/currencies/{code}", code))
                .andDo(result -> System.out.println(mapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8)).toPrettyString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.chineseName").value(expectedName));
    }

    /** 【測試要求2｜新增】以初始化資料中不存在的JPY呼叫POST；斷言201、Location及空body。 */
    @Test
    void createCurrencyApi() throws Exception {
        mvc.perform(post("/api/currencies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"JPY\",\"chineseName\":\"日圓\"}"))
                .andDo(result -> System.out.println(result.getResponse().getContentAsString(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/currencies/JPY"))
                .andExpect(content().string(""));
    }

    /** 【測試要求2｜修改】使用SQL初始化的EUR，僅呼叫PUT；斷言204及空body。 */
    @Test
    void updateCurrencyApi() throws Exception {
        mvc.perform(put("/api/currencies/EUR").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chineseName\":\"歐幣\"}"))
                .andDo(result -> System.out.println(result.getResponse().getContentAsString(StandardCharsets.UTF_8)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    /** 【測試要求2｜刪除】使用SQL初始化的USD，僅呼叫DELETE；斷言並顯示204空回應。 */
    @Test
    void deleteCurrencyApi() throws Exception {
        mvc.perform(delete("/api/currencies/USD"))
                .andDo(result -> System.out.println(result.getResponse().getContentAsString(StandardCharsets.UTF_8)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }
}
