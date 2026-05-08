package com.aipal.service;

import com.aipal.entity.BizCustomer;
import com.aipal.mapper.BillingBalanceTransactionMapper;
import com.aipal.mapper.BizCustomerMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    @Test
    void adjustBalanceWritesCustomerAndTransaction() {
        BizCustomer customer = new BizCustomer();
        customer.setId(1L);
        customer.setBalance(new BigDecimal("100"));
        customer.setStatus(1);

        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        BillingBalanceTransactionMapper transactionMapper = mock(BillingBalanceTransactionMapper.class);
        when(customerMapper.selectById(1L)).thenReturn(customer);
        when(customerMapper.updateById(any())).thenReturn(1);
        when(transactionMapper.insert(any())).thenReturn(1);

        CustomerService service = new CustomerService(customerMapper, transactionMapper);

        assertTrue(service.adjustBalance(1L, new BigDecimal("50"), "RECHARGE", "test"));
    }
}
