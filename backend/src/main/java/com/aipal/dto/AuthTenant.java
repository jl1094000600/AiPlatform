package com.aipal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthTenant {
    private Long id;
    private String tenantCode;
    private String tenantName;
    private Integer status;
    private Integer defaultTenant;
}
