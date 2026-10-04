# Currency API

本機可檢查的 Java8 REST API 範例。使用 Maven、Spring Boot 2.7.18、Spring Data JPA 及 H2，提供幣別中文名稱 CRUD、指定來源 JSON 查詢與匯率資料轉換。此目錄不含原始題目附件或題目原文。

## 執行與驗證

需要 JDK8、Maven 3.5+。Spring Boot 2.7.18 的 Java8 相容性見 [官方文件](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/getting-started.html#getting-started.system-requirements)。

```powershell
# JAVA_HOME 必須指向 JDK8；預設即執行全部8個核心測試，必須能連線至指定來源
mvn test

# 清除舊編譯產物後再執行相同8個測試
mvn clean test
```

兩個來源API測試會真正呼叫 `https://kengp3.github.io/blog/coindesk.json`。沒有額外profile或mock替代；網路或上游失敗會使測試失敗，不跳過必需項目。

如需啟動服務，另執行 `mvn package` 與 `java -Dfile.encoding=UTF-8 -jar target/currency-api-1.0.0.jar`。正常啟動預設為8080，可用 `--server.port=18085` 更改。

## API

請求與有內容的回應使用 JSON；新增、修改、刪除成功時不回傳 body，以 HTTP 狀態碼表示成功。幣別代碼為三個大寫英文字母，中文名稱必填、最多 64 字元；名稱會去除前後空白。每次查詢按代碼排序。

| 方法 | 路徑 | 用途 | 成功 |
|---|---|---|---|
| GET | `/api/currencies` | 查詢全部幣別 | 200 |
| GET | `/api/currencies/{code}` | 查詢單一幣別 | 200 |
| POST | `/api/currencies` | 新增幣別 | 201，附 Location，無 body |
| PUT | `/api/currencies/{code}` | 修改中文名稱 | 204，無 body |
| DELETE | `/api/currencies/{code}` | 刪除幣別 | 204 |
| GET | `/api/coindesk` | 呼叫指定來源，保留原 JSON 結構 | 200 |
| GET | `/api/coindesk/converted` | 呼叫指定來源，轉換時間、幣別中文名稱及匯率 | 200 |

POST body：

```json
{"code":"JPY","chineseName":"日圓"}
```

PUT `/api/currencies/JPY` body：

```json
{"chineseName":"日幣"}
```

轉換結果範例（依指定來源目前資料）：

```json
{
  "updatedTime": "2024/09/02 07:07:20",
  "currencies": [
    {"code":"EUR","chineseName":"歐元","rate":52243.2865},
    {"code":"GBP","chineseName":"英鎊","rate":43984.0203},
    {"code":"USD","chineseName":"美元","rate":57756.2984}
  ]
}
```

錯誤回應為 `{"status":404,"message":"Currency not found: JPY"}`。不合法請求回傳 400；不存在的幣別回傳 404；重複新增回傳 409；上游連線、HTTP、JSON 或轉換失敗回傳 502。

## 設計假設

- 來源固定為 `https://kengp3.github.io/blog/coindesk.json`，可用 `--coindesk.url=...` 覆寫以便本機測試。
- 來源是靜態測試資料，`updatedTime` 表示來源的更新時間，並非請求當下時間；目前來源資料為 2024 年。
- 題目未指定時區：解析 `time.updatedISO` 的 offset，統一轉成 UTC，再輸出 `yyyy/MM/dd HH:mm:ss`。不依赖 Windows 系統時區。
- 匯率優先使用 `rate_float` 的數值，以 `BigDecimal` 保留小數精度；若缺少數值則解析移除千分位逗號的 `rate` 字串。來源中兩者小數不同時以 `rate_float` 為準。
- 幣別中文名稱由 H2 資料表即時查詢，不寫死在轉換器。缺少或已刪除對照時保留幣別、匯率並回傳 `chineseName: null`。
- 新增、修改、刪除具有交易；主鍵保護重複幣別。新增明確使用 JPA `persist`，避免同時新增被當成更新。
- 使用 H2 記憶體資料庫，每次程序重啟重新建立與初始化；修改不跨程序保存。初始化有 EUR／GBP／USD 三筆資料。
- 上游連線逾時 5 秒、讀取逾時 10 秒。簡潔實作不加快取、排程、登入或額外 UI。
- Spring Boot 2.7.18 配合指定 JDK8；若轉為正式服務，另行評估受支援版本與依賴升級。

## SQL 與結構

- `src/main/resources/schema.sql`：建立 `currencies` 資料表。
- `src/main/resources/data.sql`：UTF-8 中文測試資料。
- `application.properties`：啟動時執行 SQL，JPA 僅驗證 schema（`ddl-auto=validate`），因此資料表確實由提供的 SQL 建立。
- `currency/`：Entity、Spring Data Repository、CRUD Service／Controller、請求驗證。
- `coindesk/`：HTTP Client、純資料轉換器、回應 DTO、Controller。
- `error/`：API 錯誤回應處理。

## 測試與需求對照

| 驗收項目 | 實作／測試 |
|---|---|
| Maven、JDK8、Spring Boot、H2、JPA | `pom.xml`、配置；以實際JDK8編譯及執行8個核心測試 |
| 建表與初始化 SQL | `schema.sql`、`data.sql`；Spring測試context啟動時執行 |
| 要求1：轉換邏輯單元測試 | `RateConverterTest.convertsTimeChineseNamesAndExactDecimalRates`：以兩筆受控JSON及自訂名稱驗證正確時間換算/格式、代碼/名稱對照及來源匯率保留，不要求排序 |
| 要求2：全部查詢API與內容 | `ApiIntegrationTest.queryAllCurrenciesApi`：呼叫GET清單一次，印出pretty JSON，驗證非空陣列與每筆代碼、中文名稱格式，不限定固定名稱、筆數或排序 |
| 要求2：單筆查詢API與內容 | `ApiIntegrationTest.queryCurrencyApi`：只呼叫GET一次，驗證本次查詢代碼與SQL fixture名稱，不限定全表筆數/排序 |
| 要求2：新增API與內容 | `ApiIntegrationTest.createCurrencyApi`：只呼叫POST一次，新增JPY並驗證201、Location及空body |
| 要求2：修改API與內容 | `ApiIntegrationTest.updateCurrencyApi`：只呼叫PUT一次，修改EUR並驗證204空body |
| 要求2：刪除API與內容 | `ApiIntegrationTest.deleteCurrencyApi`：只呼叫DELETE一次，刪除SQL初始化的USD並驗證204空回應 |
| 要求3：原始來源與內容 | `LiveApiTest.rawApiActuallyCallsSpecifiedRemoteUrl`：直接呼叫 `CoindeskClient.fetch()` 一次，印出JSON；驗證三個時間欄位的有效格式、disclaimer/chartName非空，以及每筆幣別的code/symbol/rate/description/rate_float型別、值與格式，不限定當下代碼/筆數/匯率值 |
| 要求4：轉換API與內容 | `LiveApiTest.convertedApiActuallyCallsSpecifiedRemoteUrl`：呼叫轉換API一次，確認HTTP 200，並印出response body |

目前共8個 `@Test`：1個純單元、5個獨立CRUD API（含全部與單筆查詢）、1個真實來源Client、1個真實來源轉換API。CRUD與轉換API測試透過MockMvc執行真實Controller/Service，原始來源測試直接呼叫 `CoindeskClient.fetch()` 並印出JSON；資料庫使用測試專用的臨時H2記憶體資料庫，透過JPA存取。API測試只以pretty JSON印出response body，MockMvc回應以UTF-8解碼；新增成功回傳201，修改與刪除成功回傳204，皆為空body，因此印出空行。

測試輸入及預期值是用來證明規格行為的fixture，並非規格限定的幣別或匯率。converter單元測試以非UTC時間、兩筆自訂匯率與中文對照驗證正確換算、名稱查表及數值保留。原始來源Client測試檢查回應欄位型別、非空值與格式；轉換API測試呼叫API、確認HTTP 200並顯示內容。正確換算與數值保留由受控單元測試驗證。

CRUD使用MockMvc呼叫及驗證目標API，並印出response body，沒有JdbcTemplate/JDBC準備或斷言。API測試透過 `@TestPropertySource` 載入 `src/test/resources/test-database.properties`，覆寫應用程式的資料庫設定；每個案例使用不同名稱的H2記憶體資料庫，重新執行 `schema.sql`、`data.sql`。`@DirtiesContext(AFTER_EACH_TEST_METHOD)` 在案例結束後關閉Spring context與連線池，`DB_CLOSE_DELAY=0` 讓資料庫隨最後一條連線關閉而銷毀。下一個案例從乾淨的初始資料開始，不依賴測試順序；轉換器的純單元測試不啟動資料庫。

CRUD可各自單獨執行，不需網路：

```powershell
mvn '-Dtest=ApiIntegrationTest#queryAllCurrenciesApi' test
mvn '-Dtest=ApiIntegrationTest#queryCurrencyApi' test
mvn '-Dtest=ApiIntegrationTest#createCurrencyApi' test
mvn '-Dtest=ApiIntegrationTest#updateCurrencyApi' test
mvn '-Dtest=ApiIntegrationTest#deleteCurrencyApi' test
```

執行 `mvn clean test` 可在終端機查看測試結果與 API response body，JUnit XML 報告產生於 `target/surefire-reports/`。單案執行會更新對應的報告。

