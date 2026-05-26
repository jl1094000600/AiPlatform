package com.aipal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthMenu {
    private Long id;
    private String menuCode;
    private String menuName;
    private String path;
    private String icon;
    private String permissionCode;
    private Long parentId;
    private Integer sortOrder;
    private List<AuthMenu> children = new ArrayList<>();
}
