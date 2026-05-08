package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.BizCustomer;
import com.aipal.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "1") int pageNum,
                          @RequestParam(defaultValue = "20") int pageSize,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) Integer status) {
        return Result.success(customerService.listCustomers(pageNum, pageSize, keyword, status));
    }

    @GetMapping("/{id}")
    public Result<BizCustomer> get(@PathVariable Long id) {
        return Result.success(customerService.getCustomer(id));
    }

    @PostMapping
    public Result<Boolean> create(@RequestBody BizCustomer customer) {
        return Result.success(customerService.createCustomer(customer));
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody BizCustomer customer) {
        return Result.success(customerService.updateCustomer(id, customer));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(customerService.deleteCustomer(id));
    }

    @PostMapping("/{id}/freeze")
    public Result<Boolean> freeze(@PathVariable Long id) {
        return Result.success(customerService.freezeCustomer(id));
    }

    @PostMapping("/{id}/balance/adjust")
    public Result<Boolean> adjustBalance(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(String.valueOf(request.getOrDefault("amount", "0")));
        String type = (String) request.getOrDefault("type", "ADJUST");
        String remark = (String) request.getOrDefault("remark", "");
        return Result.success(customerService.adjustBalance(id, amount, type, remark));
    }
}
