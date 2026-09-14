package com.pcdoctor.service;

import com.pcdoctor.exception.BizException;
import com.pcdoctor.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传/下载（#5.8）：
 * - 上传：类型目录白名单 + 扩展名/MIME 白名单，文件名服务端生成（UUID），绝不信任客户端文件名
 * - 下载：type/name 白名单校验（拒绝 ../ 与绝对路径），流式输出
 * - 上传目录不对外静态暴露，统一走本服务
 */
@Slf4j
@Service
public class FileService {

    /** 上传类型目录白名单（与契约 §4.10 一致）。notice = 站点公告类图片（如交流群二维码） */
    public static final Set<String> ALLOWED_TYPES = Set.of("avatar", "appointment", "doc", "activity", "message", "notice");

    /** 扩展名 → MIME（下载时 Content-Type） */
    private static final Map<String, String> EXT_MIME = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif",
            "md", "text/markdown; charset=utf-8",
            "pdf", "application/pdf");

    /** 文件名白名单：仅字母/数字/下划线/点/连字符，天然拒绝路径穿越 */
    private static final String SAFE_NAME_PATTERN = "^[A-Za-z0-9._-]{1,100}$";

    private final Path baseDir;

    public FileService(@Value("${pc-doctor.upload.path}") String uploadPath) {
        this.baseDir = Path.of(uploadPath).toAbsolutePath().normalize();
    }

    /** 上传。返回可访问 URL：/api/files/{type}/{name} */
    public String upload(MultipartFile file, String type) {
        String ext = extractExt(file.getOriginalFilename());
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "不支持的上传类型");
        }
        if (!EXT_MIME.containsKey(ext) || !isAllowedMime(file.getContentType(), ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = baseDir.resolve(type).normalize();
        if (!dir.startsWith(baseDir)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("文件保存失败: type={}, name={}", type, name, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        log.info("文件上传: type={}, name={}, size={}", type, name, file.getSize());
        return "/api/files/" + type + "/" + name;
    }

    /** 保存字节（头像服务端裁剪压缩后复用同一白名单落盘） */
    public String saveBytes(String type, String ext, byte[] bytes) {
        if (!ALLOWED_TYPES.contains(type) || !EXT_MIME.containsKey(ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = baseDir.resolve(type).normalize();
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), bytes);
        } catch (IOException e) {
            log.error("文件保存失败: type={}, name={}", type, name, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        return "/api/files/" + type + "/" + name;
    }

    /** 解析下载路径（校验通过后返回磁盘路径与 MIME） */
    public Download resolve(String type, String name) {
        if (!ALLOWED_TYPES.contains(type) || !name.matches(SAFE_NAME_PATTERN)
                || !name.equals(Path.of(name).getFileName().toString())) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        Path path = baseDir.resolve(type).resolve(name).normalize();
        if (!path.startsWith(baseDir) || !Files.isRegularFile(path)) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        String ext = extractExt(name);
        return new Download(path, EXT_MIME.get(ext));
    }

    /**
     * 删除受管文件（#5.10）：仅处理 /api/files/{type}/{name} 形式的业务文件，
     * 校验通过才删；不存在或非法路径静默忽略。
     */
    public void deleteManaged(String url) {
        if (url == null || !url.startsWith("/api/files/")) {
            return;
        }
        String[] parts = url.substring("/api/files/".length()).split("/", 2);
        if (parts.length != 2) {
            return;
        }
        try {
            Download d = resolve(parts[0], parts[1]);
            Files.deleteIfExists(d.path());
            log.info("文件清理: {}", url);
        } catch (BizException ignored) {
            // 文件已不存在或非法路径，无需处理
        } catch (IOException e) {
            log.warn("文件清理失败: {}", url, e);
        }
    }

    private String extractExt(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** MIME 与扩展名交叉校验：图片按 image/* 大类核对，md/pdf 精确匹配 */
    private boolean isAllowedMime(String mime, String ext) {
        if (mime == null) {
            return false;
        }
        String normalized = mime.toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "jpg", "jpeg", "png", "webp", "gif" -> normalized.startsWith("image/");
            // md 为纯文本，前端/客户端常以 octet-stream 上传；下载时固定以 text/markdown 输出
            case "md" -> normalized.startsWith("text/") || normalized.contains("markdown")
                    || normalized.equals("application/octet-stream");
            case "pdf" -> normalized.equals("application/pdf");
            default -> false;
        };
    }

    /** 下载句柄 */
    public record Download(Path path, String mime) {
    }
}