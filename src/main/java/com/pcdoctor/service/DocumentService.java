package com.pcdoctor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.common.TransactionAfterCommit;
import com.pcdoctor.dto.DocumentRequest;
import com.pcdoctor.dto.PageView;
import com.pcdoctor.entity.Document;
import com.pcdoctor.mapper.DocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档模块（#5.4）：Markdown 文档 CRUD + 标题模糊搜索；删除连带清理文件（#5.10）。
 */
@Slf4j
@Service
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final FileService fileService;

    public DocumentService(DocumentMapper documentMapper, FileService fileService) {
        this.documentMapper = documentMapper;
        this.fileService = fileService;
    }

    public PageView<Document> list(int page, int size, String title) {
        LambdaQueryWrapper<Document> qw = new LambdaQueryWrapper<>();
        if (title != null && !title.isBlank()) {
            qw.like(Document::getTitle, title);
        }
        qw.orderByDesc(Document::getCreateTime);
        return PageView.from(documentMapper.selectPage(new Page<>(page, size), qw));
    }

    public Document detail(Long id) {
        return requireDocument(id);
    }

    @Transactional
    public Document create(DocumentRequest request) {
        Document d = new Document();
        d.setTitle(request.title());
        d.setSummary(request.summary());
        d.setFile(request.file());
        documentMapper.insert(d);
        log.info("文档创建: id={}, title={}", d.getId(), d.getTitle());
        return d;
    }

    @Transactional
    public Document update(Long id, DocumentRequest request) {
        Document d = requireDocument(id);
        String oldFile = d.getFile();
        Document update = new Document();
        update.setId(id);
        update.setTitle(request.title());
        update.setSummary(request.summary());
        update.setFile(request.file());
        documentMapper.updateById(update);
        // 换文件后清理旧文件
        if (oldFile != null && !oldFile.equals(request.file())) {
            TransactionAfterCommit.run(() -> fileService.deleteManaged(oldFile));
        }
        return documentMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id) {
        Document d = requireDocument(id);
        String file = d.getFile();
        documentMapper.deleteById(id);
        TransactionAfterCommit.run(() -> fileService.deleteManaged(file));
        log.info("文档删除: id={}", id);
    }

    private Document requireDocument(Long id) {
        Document d = documentMapper.selectById(id);
        if (d == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return d;
    }
}