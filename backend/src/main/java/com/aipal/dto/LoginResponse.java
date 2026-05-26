package com.aipal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String realName;
    private AuthTenant tenant;
    private List<AuthTenant> tenants;
    private List<String> roles;
    private List<String> permissions;
    private List<AuthMenu> menus;
    private Boolean platformAdmin;
}
