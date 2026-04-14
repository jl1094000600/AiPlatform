package com.aipal.dto;

import lombok.Data;
import java.util.List;

@Data
public class PageResponse<T> {
    private List<T> records;
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private Integer totalPages;
}
