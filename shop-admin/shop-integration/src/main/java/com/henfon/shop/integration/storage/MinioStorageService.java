package com.henfon.shop.integration.storage;

import com.henfon.shop.common.exception.BusinessException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * MinIO 统一文件服务，负责上传、临时访问和删除。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class MinioStorageService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final Duration PRESIGN_DURATION = Duration.ofHours(24);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "application/pdf");

    private final MinioClient minioClient;
    private final MinioProperties properties;

    /**
     * 创建 MinIO 文件服务。
     *
     * @param minioClient MinIO 客户端
     * @param properties MinIO 配置
     * @author Henfon
     * @date 2026-08-30
     */
    public MinioStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 上传受限媒体文件。
     *
     * @param file 上传文件
     * @return 文件对象键、临时访问地址和元数据
     * @author Henfon
     * @date 2026-08-30
     */
    public UploadResult upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("STORAGE_FILE_EMPTY", "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("STORAGE_FILE_TOO_LARGE", "文件大小不能超过10MB");
        }
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException("STORAGE_FILE_TYPE_INVALID", "仅支持 JPG、PNG、WEBP、GIF、MP4 和 PDF 文件");
        }
        String objectKey = buildObjectKey(file.getOriginalFilename(), contentType);
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build());
            return new UploadResult(objectKey, presign(objectKey), file.getSize(), contentType);
        } catch (Exception exception) {
            throw new BusinessException("STORAGE_UPLOAD_FAILED", "文件上传失败，请稍后重试");
        }
    }

    /**
     * 获取对象临时访问地址。
     *
     * @param objectKey 对象键
     * @return 24小时有效的临时地址
     * @author Henfon
     * @date 2026-08-30
     */
    public String presign(String objectKey) {
        if (!StringUtils.hasText(objectKey) || objectKey.contains("..") || objectKey.startsWith("/")) {
            throw new BusinessException("STORAGE_OBJECT_KEY_INVALID", "文件对象键不合法");
        }
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .expiry((int) PRESIGN_DURATION.toSeconds())
                    .build());
        } catch (Exception exception) {
            throw new BusinessException("STORAGE_PRESIGN_FAILED", "生成文件访问地址失败");
        }
    }

    /**
     * 删除对象。
     *
     * @param objectKey 对象键
     * @author Henfon
     * @date 2026-08-30
     */
    public void delete(String objectKey) {
        if (!StringUtils.hasText(objectKey) || objectKey.contains("..") || objectKey.startsWith("/")) {
            throw new BusinessException("STORAGE_OBJECT_KEY_INVALID", "文件对象键不合法");
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket()).object(objectKey).build());
        } catch (Exception exception) {
            throw new BusinessException("STORAGE_DELETE_FAILED", "文件删除失败，请稍后重试");
        }
    }

    /**
     * 确保存储桶存在。
     *
     * @author Henfon
     * @date 2026-08-30
     */
    private void ensureBucket() throws Exception {
        if (!minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
            minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(properties.bucket()).build());
        }
    }

    /**
     * 根据日期和随机值生成对象键，避免原始文件名覆盖和路径穿越。
     *
     * @param originalFilename 原始文件名
     * @param contentType 内容类型
     * @return 对象键
     * @author Henfon
     * @date 2026-08-30
     */
    private String buildObjectKey(String originalFilename, String contentType) {
        String extension = extension(originalFilename);
        if (!StringUtils.hasText(extension)) {
            extension = switch (contentType) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                case "image/gif" -> "gif";
                case "video/mp4" -> "mp4";
                case "application/pdf" -> "pdf";
                default -> "bin";
            };
        }
        return "media/" + LocalDate.now() + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

    /**
     * 提取安全扩展名。
     *
     * @param filename 文件名
     * @return 小写扩展名
     * @author Henfon
     * @date 2026-08-30
     */
    private String extension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    /**
     * 上传结果。
     *
     * @param objectKey 对象键
     * @param url 临时访问地址
     * @param size 文件大小
     * @param contentType 内容类型
     * @author Henfon
     * @date 2026-08-30
     */
    public record UploadResult(String objectKey, String url, long size, String contentType) {
    }
}
