package com.pcdoctor.dto;

import java.util.List;

/**
 * 批量操作结果（契约 §1「批量接口约定」）。
 *
 * <p>不变式：{@code total} = 去重后的 id 数，恒有 {@code success + failures.size() == total}。
 * 部分成功同样由 {@code Result.ok(...)} 返回（code=0），失败明细走 {@code failures} ——
 * 前端拦截器对非 0 业务码会弹全局错误并 reject，失败项不能借业务码表达。
 */
public record BatchResult(long total, long success, List<Failure> failures) {

    /**
     * 单条失败项。
     *
     * @param id     去重后的 id 原值（统一转字符串，用户 id 本就是字符串、其余是数字）
     * @param code   取自 {@code ErrorCode}，前端可据此区分「状态冲突可重试」与「账号不存在」
     * @param reason 可直接展示给用户的文案
     */
    public record Failure(String id, int code, String reason) {
    }

    public static BatchResult of(long total, List<Failure> failures) {
        return new BatchResult(total, total - failures.size(), failures);
    }
}
