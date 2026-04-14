package com.aipal.dto;

import lombok.Data;

@Data
public class PageRequest {
    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private String sortBy;
    private String sortOrder = "desc";
}
