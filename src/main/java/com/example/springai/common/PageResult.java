package com.example.springai.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    private long total;
    private long page;
    private long size;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> pr = new PageResult<>();
        pr.total = page.getTotal();
        pr.page = page.getCurrent();
        pr.size = page.getSize();
        pr.records = page.getRecords();
        return pr;
    }
}