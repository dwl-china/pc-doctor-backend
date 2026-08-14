package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.dto.DocumentRequest;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.entity.Document;
import com.pcdoctor.service.DocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * 文档接口（契约 §4.6）：读公开，写管理员。
 */
@RestController
@RequestMapping("/api/documents")
@Validated
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public Result<PageView<Document>> list(@RequestParam(defaultValue = "1") @Min(value = 1, message = "page 最小为 1") int page,
                                           @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 最小为 1") @Max(value = 100, message = "size 最大为 100") int size,
                                           @RequestParam(required = false) String title) {
        return Result.ok(documentService.list(page, size, title));
    }

    @GetMapping("/{id}")
    public Result<Document> detail(@PathVariable Long id) {
        return Result.ok(documentService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<Document> create(@Valid @RequestBody DocumentRequest request) {
        return Result.ok(documentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<Document> update(@PathVariable Long id, @Valid @RequestBody DocumentRequest request) {
        return Result.ok(documentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.ok();
    }
}