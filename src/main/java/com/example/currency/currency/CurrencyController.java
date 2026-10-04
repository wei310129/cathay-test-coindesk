package com.example.currency.currency;

import java.net.URI;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/currencies")
@Validated
public class CurrencyController {
    private final CurrencyService service;

    public CurrencyController(CurrencyService service) {
        this.service = service;
    }

    /** 【規格實作1｜查詢】GET /api/currencies：回傳全部幣別及中文名稱。 */
    @GetMapping
    public List<Currency> list() {
        return service.list();
    }

    /** 【規格實作1｜查詢】GET /api/currencies/{code}：依代碼查詢單一幣別對照。 */
    @GetMapping("/{code}")
    public Currency get(@PathVariable @Pattern(regexp = "[A-Z]{3}") String code) {
        return service.get(code);
    }

    /** 【規格實作1｜新增】POST /api/currencies：新增對照，成功回傳201與Location，無body。 */
    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody CurrencyCreateRequest request) {
        service.create(request.getCode(), request.getChineseName());
        return ResponseEntity.created(URI.create("/api/currencies/" + request.getCode())).build();
    }

    /** 【規格實作1｜修改】PUT /api/currencies/{code}：修改中文名稱，成功回傳204，無body。 */
    @PutMapping("/{code}")
    public ResponseEntity<Void> update(@PathVariable @Pattern(regexp = "[A-Z]{3}") String code,
                           @Valid @RequestBody CurrencyNameRequest request) {
        service.update(code, request.getChineseName());
        return ResponseEntity.noContent().build();
    }

    /** 【規格實作1｜刪除】DELETE /api/currencies/{code}：刪除對照，成功回傳204空內容。 */
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable @Pattern(regexp = "[A-Z]{3}") String code) {
        service.delete(code);
        return ResponseEntity.noContent().build();
    }
}
