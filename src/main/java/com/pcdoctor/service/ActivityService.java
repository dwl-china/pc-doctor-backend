package com.pcdoctor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.common.TransactionAfterCommit;
import com.pcdoctor.dto.ActivityRequest;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.entity.Activity;
import com.pcdoctor.mapper.ActivityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 活动模块（#5.5）：CRUD；删除连带清理文件（#5.10）。
 */
@Slf4j
@Service
public class ActivityService {

    private final ActivityMapper activityMapper;
    private final FileService fileService;

    public ActivityService(ActivityMapper activityMapper, FileService fileService) {
        this.activityMapper = activityMapper;
        this.fileService = fileService;
    }

    public PageView<Activity> list(int page, int size) {
        LambdaQueryWrapper<Activity> qw = new LambdaQueryWrapper<Activity>()
                .orderByDesc(Activity::getCreateTime);
        return PageView.from(activityMapper.selectPage(new Page<>(page, size), qw));
    }

    public Activity detail(Long id) {
        return requireActivity(id);
    }

    @Transactional
    public Activity create(ActivityRequest request) {
        Activity a = new Activity();
        a.setTitle(request.title());
        a.setSummary(request.summary());
        a.setFile(request.file());
        a.setCover(request.cover());
        activityMapper.insert(a);
        log.info("活动创建: id={}, title={}", a.getId(), a.getTitle());
        return a;
    }

    @Transactional
    public Activity update(Long id, ActivityRequest request) {
        Activity a = requireActivity(id);
        Activity update = new Activity();
        update.setId(id);
        update.setTitle(request.title());
        update.setSummary(request.summary());
        update.setFile(request.file());
        update.setCover(request.cover());
        activityMapper.updateById(update);
        // 换文件/封面后清理旧文件。
        // 封面是可选字段：客户端不传时 MyBatis-Plus 不会更新该列（DB 保留旧值），
        // 所以只有"确实传了新封面且与旧值不同"才能删旧文件，否则会删掉仍在被引用的文件。
        String oldFile = a.getFile();
        String oldCover = a.getCover();
        String newCover = request.cover();
        TransactionAfterCommit.run(() -> {
            if (oldFile != null && !oldFile.equals(request.file())) {
                fileService.deleteManaged(oldFile);
            }
            if (oldCover != null && newCover != null && !newCover.isBlank() && !oldCover.equals(newCover)) {
                fileService.deleteManaged(oldCover);
            }
        });
        return activityMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id) {
        Activity a = requireActivity(id);
        String file = a.getFile();
        String cover = a.getCover();
        activityMapper.deleteById(id);
        TransactionAfterCommit.run(() -> {
            fileService.deleteManaged(file);
            fileService.deleteManaged(cover);
        });
        log.info("活动删除: id={}", id);
    }

    private Activity requireActivity(Long id) {
        Activity a = activityMapper.selectById(id);
        if (a == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        return a;
    }
}