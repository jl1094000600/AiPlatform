package com.aipal.service;

import com.aipal.entity.BillingBalanceTransaction;
import com.aipal.entity.BizCustomer;
import com.aipal.mapper.BillingBalanceTransactionMapper;
import com.aipal.mapper.BizCustomerMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final BizCustomerMapper customerMapper;
    private final BillingBalanceTransactionMapper transactionMapper;

    public Page<BizCustomer> listCustomers(int pageNum, int pageSize, String keyword, Integer status) {
        Page<BizCustomer> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(BizCustomer::getCustomerName, keyword)
                    .or()
                    .like(BizCustomer::getCustomerCode, keyword));
        }
        if (status != null) {
            wrapper.eq(BizCustomer::getStatus, status);
        }
        wrapper.orderByDesc(BizCustomer::getCreateTime);
        return customerMapper.selectPage(page, wrapper);
    }

    public BizCustomer getCustomer(Long id) {
        return customerMapper.selectById(id);
    }

    public boolean createCustomer(BizCustomer customer) {
        LocalDateTime now = LocalDateTime.now();
        customer.setCreateTime(now);
        customer.setUpdateTime(now);
        if (customer.getBalance() == null) customer.setBalance(BigDecimal.ZERO);
        if (customer.getWarningBalance() == null) customer.setWarningBalance(new BigDecimal("100"));
        if (customer.getStatus() == null) customer.setStatus(1);
        return customerMapper.insert(customer) > 0;
    }

    public boolean updateCustomer(Long id, BizCustomer customer) {
        customer.setId(id);
        customer.setUpdateTime(LocalDateTime.now());
        return customerMapper.updateById(customer) > 0;
    }

    public boolean freezeCustomer(Long id) {
        BizCustomer customer = new BizCustomer();
        customer.setId(id);
        customer.setStatus(0);
        customer.setUpdateTime(LocalDateTime.now());
        return customerMapper.updateById(customer) > 0;
    }

    public boolean deleteCustomer(Long id) {
        return customerMapper.deleteById(id) > 0;
    }

    @Transactional
    public boolean adjustBalance(Long id, BigDecimal amount, String type, String remark) {
        BizCustomer customer = customerMapper.selectById(id);
        if (customer == null) return false;
        BigDecimal before = customer.getBalance() == null ? BigDecimal.ZERO : customer.getBalance();
        BigDecimal delta = amount == null ? BigDecimal.ZERO : amount;
        if ("DEDUCT".equalsIgnoreCase(type)) {
            delta = delta.abs().negate();
        }
        BigDecimal after = before.add(delta);

        BizCustomer update = new BizCustomer();
        update.setId(id);
        update.setBalance(after);
        update.setStatus(after.compareTo(BigDecimal.ZERO) < 0 ? 0 : customer.getStatus());
        update.setUpdateTime(LocalDateTime.now());
        customerMapper.updateById(update);

        BillingBalanceTransaction tx = new BillingBalanceTransaction();
        tx.setCustomerId(id);
        tx.setTransactionType(type == null ? "ADJUST" : type.toUpperCase());
        tx.setAmount(delta);
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(after);
        tx.setRemark(remark);
        tx.setCreateTime(LocalDateTime.now());
        return transactionMapper.insert(tx) > 0;
    }
}
