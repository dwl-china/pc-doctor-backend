package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.CategoryRequest;
import com.pcdoctor.entity.Category;
import com.pcdoctor.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类接口（契约 §4.8）：读公开，写管理员（按 id 删除，#17）。
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Result<List<Category>> list() {
        return Result.ok(categoryService.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Category> create(@Valid @RequestBody CategoryRequest request) {
        return Result.ok(categoryService.create(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}