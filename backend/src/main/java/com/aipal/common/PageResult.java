package com.aipal.common;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private long total;
    private int pageNum;
    private int pageSize;
    private int totalPages;
    private List<T> records;

    public static <T> PageResult<T> of(long total, int pageNum, int pageSize, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setTotalPages((int) ((total + pageSize - 1) / pageSize));
        result.setRecords(records);
        return result;
    }
}
