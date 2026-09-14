package com.pcdoctor.service;

import com.pcdoctor.dto.ActivityRequest;
import com.pcdoctor.entity.Activity;
import com.pcdoctor.exception.BizException;
import com.pcdoctor.mapper.ActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 活动模块（需求 2）：聚焦文件清理 —— 尤其是"客户端不传封面时不能删旧封面"这条
 * （原实现会删掉仍被 DB 引用的文件，形成悬空引用）。
 *
 * <p>纯 Mockito 环境无事务，{@code TransactionAfterCommit.run} 会立即执行，因此可以直接断言。
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    ActivityMapper activityMapper;
    @Mock
    FileService fileService;

    ActivityService service;

    private static final String OLD_FILE = "/api/files/activity/old.md";
    private static final String OLD_COVER = "/api/files/activity/old-cover.png";

    @BeforeEach
    void setUp() {
        service = new ActivityService(activityMapper, fileService);
    }

    @Test
    @DisplayName("更新时不传封面：保留旧封面文件（回归：原实现会删掉仍在引用的文件）")
    void updateWithoutCoverKeepsOldFile() {
        when(activityMapper.selectById(1L)).thenReturn(activity(OLD_FILE, OLD_COVER));

        service.update(1L, new ActivityRequest("t", "s", OLD_FILE, null));

        verify(fileService, never()).deleteManaged(OLD_COVER);
        verify(fileService, never()).deleteManaged(OLD_FILE);
    }

    @Test
    @DisplayName("更新时传空字符串封面：同样不删旧文件")
    void updateWithBlankCoverKeepsOldFile() {
        when(activityMapper.selectById(1L)).thenReturn(activity(OLD_FILE, OLD_COVER));

        service.update(1L, new ActivityRequest("t", "s", OLD_FILE, "  "));

        verify(fileService, never()).deleteManaged(OLD_COVER);
    }

    @Test
    @DisplayName("更新时传了相同封面：不删文件")
    void updateWithSameCoverKeepsFile() {
        when(activityMapper.selectById(1L)).thenReturn(activity(OLD_FILE, OLD_COVER));

        service.update(1L, new ActivityRequest("t", "s", OLD_FILE, OLD_COVER));

        verify(fileService, never()).deleteManaged(OLD_COVER);
    }

    @Test
    @DisplayName("换封面：删除旧封面文件")
    void updateWithNewCoverDeletesOld() {
        when(activityMapper.selectById(1L)).thenReturn(activity(OLD_FILE, OLD_COVER));

        service.update(1L, new ActivityRequest("t", "s", OLD_FILE, "/api/files/activity/new-cover.png"));

        verify(fileService).deleteManaged(OLD_COVER);
        verify(fileService, never()).deleteManaged(OLD_FILE);
    }

    @Test
    @DisplayName("换详情文件：删除旧文件")
    void updateReplacesFileDeletesOld() {
        when(activityMapper.selectById(1L)).thenReturn(activity(OLD_FILE, OLD_COVER));
        String newFile = "/api/files/activity/new.md";

        service.update(1L, new ActivityRequest("t", "s", newFile, OLD_COVER));

        verify(fileService).deleteManaged(OLD_FILE);
        verify(fileService, never()).deleteManaged(OLD_COVER);
    }

    @Test
    @DisplayName("删除活动：详情文件与封面一并清理")
    void deleteCleansBothFiles() {
        when(activityMapper.selectById(1L)).thenReturn(activity(OLD_FILE, OLD_COVER));

        service.delete(1L);

        verify(activityMapper).deleteById(1L);
        verify(fileService).deleteManaged(OLD_FILE);
        verify(fileService).deleteManaged(OLD_COVER);
    }

    @Test
    @DisplayName("活动不存在 → 2002")
    void updateMissingActivity() {
        when(activityMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(99L, new ActivityRequest("t", "s", OLD_FILE, null)))
                .isInstanceOf(BizException.class);
        verify(activityMapper, never()).updateById(any(Activity.class));
    }

    private Activity activity(String file, String cover) {
        Activity a = new Activity();
        a.setId(1L);
        a.setTitle("t");
        a.setSummary("s");
        a.setFile(file);
        a.setCover(cover);
        return a;
    }
}
