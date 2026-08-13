package com.pcdoctor.service;

import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件白名单与路径穿越防护（#5、#5.8、#6.1），纯单元测试用临时目录。
 */
class FileServiceTest {

    @TempDir
    Path tempDir;

    FileService service;

    @BeforeEach
    void setUp() {
        service = new FileService(tempDir.toString());
    }

    @Test
    @DisplayName("上传扩展名白名单：exe 拒绝")
    void uploadBadExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "evil.exe",
                "application/octet-stream", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.upload(file, "doc"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("上传类型目录白名单：非法 type 拒绝")
    void uploadBadType() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png",
                "image/png", new byte[]{1});
        assertThatThrownBy(() -> service.upload(file, "../../etc"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("上传 MIME 校验：图片扩展名 + 非图片 MIME 拒绝")
    void uploadMimeMismatch() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png",
                "application/octet-stream", new byte[]{1});
        assertThatThrownBy(() -> service.upload(file, "appointment"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("上传→下载往返：URL 可解析且 MIME 正确")
    void uploadThenResolve() {
        MockMultipartFile file = new MockMultipartFile("file", "pic.png",
                "image/png", new byte[]{1, 2, 3});
        String url = service.upload(file, "appointment");
        assertThat(url).startsWith("/api/files/appointment/").endsWith(".png");
        String name = url.substring(url.lastIndexOf('/') + 1);
        FileService.Download d = service.resolve("appointment", name);
        assertThat(d.mime()).isEqualTo("image/png");
        assertThat(d.path()).exists();
    }

    @Test
    @DisplayName("路径穿越：..、../、斜杠名一律拒绝")
    void resolveTraversal() {
        assertThatThrownBy(() -> service.resolve("doc", ".."))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND));
        assertThatThrownBy(() -> service.resolve("doc", "../application.yml"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND));
        assertThatThrownBy(() -> service.resolve("doc", "a/b.md"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND));
        assertThatThrownBy(() -> service.resolve("..", "x.md"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND));
        assertThatThrownBy(() -> service.resolve("doc", "no-such-file.md"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND));
    }

    @Test
    @DisplayName("deleteManaged：仅删除受管 /api/files 文件，其它 URL 不动")
    void deleteManagedOnlyManaged() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "d.md",
                "text/markdown", "# t".getBytes());
        String url = service.upload(file, "doc");
        String name = url.substring(url.lastIndexOf('/') + 1);
        Path stored = service.resolve("doc", name).path();
        assertThat(stored).exists();

        service.deleteManaged(url);
        assertThat(stored).doesNotExist();

        // 非受管 URL 静默忽略，不抛异常
        service.deleteManaged("https://evil.example/x.md");
        service.deleteManaged("/etc/passwd");
        service.deleteManaged(null);
    }
}