package com.pcdoctor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.dto.CategoryRequest;
import com.pcdoctor.entity.Category;
import com.pcdoctor.mapper.AppointmentMapper;
import com.pcdoctor.mapper.CategoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分类模块（#5.6）：按 id 增删查（修复老前端删错对象，#17）；被预约引用时禁止删除。
 */
@Slf4j
@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final AppointmentMapper appointmentMapper;

    public CategoryService(CategoryMapper categoryMapper, AppointmentMapper appointmentMapper) {
        this.categoryMapper = categoryMapper;
        this.appointmentMapper = appointmentMapper;
    }

    public List<Category> list() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getId));
    }

    @Transactional
    public Category create(CategoryRequest request) {
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getName, request.name()));
        if (count > 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, "分类已存在");
        }
        Category c = new Category();
        c.setName(request.name());
        categoryMapper.insert(c);
        log.info("分类创建: id={}, name={}", c.getId(), c.getName());
        return c;
    }

    @Transactional
    public void delete(Long id) {
        if (categoryMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        Long used = appointmentMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.pcdoctor.entity.Appointment>()
                .eq(com.pcdoctor.entity.Appointment::getCategoryId, id));
        if (used > 0) {
            throw new BizException(ErrorCode.INVALID_STATUS, "该分类下存在预约，无法删除");
        }
        categoryMapper.deleteById(id);
        log.info("分类删除: id={}", id);
    }
}