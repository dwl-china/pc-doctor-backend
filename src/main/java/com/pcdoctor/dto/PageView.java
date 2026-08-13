package com.pcdoctor.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 分页响应（契约 §1：{total, records}）。
 */
public record PageView<T>(long total, List<T> records) {

    public static <T> PageView<T> from(IPage<T> page) {
        return new PageView<>(page.getTotal(), page.getRecords());
    }
}