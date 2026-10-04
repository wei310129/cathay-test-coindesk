package com.example.currency.currency;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.currency.error.ApiException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 【規格實作1】以JPA查詢及交易寫入幣別對照；【規格實作3】提供資料庫中的中文名稱。 */
@Service
@Transactional(readOnly = true)
public class CurrencyService {
    private final CurrencyRepository repository;

    public CurrencyService(CurrencyRepository repository) {
        this.repository = repository;
    }

    /** 【規格實作1｜查詢】取得按代碼排序的全部幣別對照。 */
    public List<Currency> list() {
        return repository.findAll(Sort.by("code"));
    }

    /** 【規格實作1｜查詢】取得單筆；不存在時回傳404屬此實作的錯誤處理約定。 */
    public Currency get(String code) {
        return repository.findById(code).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Currency not found: " + code));
    }

    /** 【規格實作1｜新增】交易內檢查重複並新增資料；409及名稱去空白是實作約定。 */
    @Transactional
    public void create(String code, String chineseName) {
        if (repository.existsById(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Currency already exists: " + code);
        }
        // persist() keeps a concurrent duplicate create from silently becoming an update.
        Currency currency = new Currency(code, chineseName.trim());
        repository.insert(currency);
    }

    /** 【規格實作1｜修改】更新中文名稱並flush，讓資料庫寫入錯誤在本次呼叫內被發現。 */
    @Transactional
    public void update(String code, String chineseName) {
        Currency currency = get(code);
        currency.setChineseName(chineseName.trim());
        repository.saveAndFlush(currency);
    }

    /** 【規格實作1｜刪除】刪除既有對照並flush；不影響外部來源的幣別或匯率。 */
    @Transactional
    public void delete(String code) {
        repository.delete(get(code));
        repository.flush();
    }

    /** 【規格實作3｜中文名稱】每次從資料庫產生代碼對照，讓轉換API反映CRUD的修改。 */
    public Map<String, String> chineseNames() {
        return list().stream().collect(Collectors.toMap(Currency::getCode, Currency::getChineseName));
    }
}
