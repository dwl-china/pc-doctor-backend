package com.pcdoctor.controller;

import com.pcdoctor.common.Result;
import com.pcdoctor.service.FileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件接口（契约 §4.10）：上传登录，下载公开；上传目录不做静态映射（#5）。
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam("type") String type) {
        return Result.ok(Map.of("url", fileService.upload(file, type)));
    }

    /** 下载/展示：type/name 白名单校验（FileService.resolve 拒绝路径穿越），流式输出 */
    @GetMapping("/{type}/{name}")
    public ResponseEntity<FileSystemResource> download(@PathVariable String type, @PathVariable String name) {
        FileService.Download d = fileService.resolve(type, name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.mime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                .body(new FileSystemResource(d.path()));
    }
}