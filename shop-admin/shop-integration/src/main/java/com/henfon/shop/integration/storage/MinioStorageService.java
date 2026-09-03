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

import java.net.URI;
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
     * 将 MinIO 临时访问地址转换为适合持久化的对象键，外部地址保持不变。
     *
     * @param reference 图片对象键或访问地址
     * @return 稳定存储引用
     * @author Henfon
     * @date 2026-09-03
     */
    public String normalizeReference(String reference) {
        if (!StringUtils.hasText(reference)) {
            return reference;
        }
        String objectKey = extractObjectKey(reference.trim());
        return StringUtils.hasText(objectKey) ? objectKey : reference.trim();
    }

    /**
     * 根据稳定对象键或历史预签名地址生成当前有效的访问地址。
     *
     * @param reference 图片对象键或历史访问地址
     * @return 当前有效的访问地址
     * @author Henfon
     * @date 2026-09-03
     */
    public String resolveAccessUrl(String reference) {
        if (!StringUtils.hasText(reference)) {
            return reference;
        }
        String normalized = reference.trim();
        String objectKey = extractObjectKey(normalized);
        if (!StringUtils.hasText(objectKey) && isHttpAddress(normalized)) {
            // 普通外部图片不经过 MinIO 重签，避免改变第三方资源地址。
            return normalized;
        }
        try {
            return presign(StringUtils.hasText(objectKey) ? objectKey : normalized);
        } catch (BusinessException exception) {
            // 对象存储临时不可用时保留原引用，避免影响订单主体数据查询。
            return normalized;
        }
    }

    /**
     * 从 MinIO 路径式访问地址中提取对象键。
     *
     * @param reference 对象键或访问地址
     * @return 对象键，非 MinIO 地址返回空值
     * @author Henfon
     * @date 2026-09-03
     */
    private String extractObjectKey(String reference) {
        if (!isHttpAddress(reference)) {
            return reference;
        }
        try {
            URI uri = URI.create(reference);
            String bucketPrefix = "/" + properties.bucket() + "/";
            String path = uri.getPath();
            if (StringUtils.hasText(path) && path.startsWith(bucketPrefix)) {
                return path.substring(bucketPrefix.length());
            }
        } catch (IllegalArgumentException ignored) {
            // 无法解析的地址按外部地址处理，由调用方原样返回。
        }
        return null;
    }

    /**
     * 判断是否为 HTTP(S) 地址。
     *
     * @param value 待判断字符串
     * @return 是否为 HTTP(S) 地址
     * @author Henfon
     * @date 2026-09-03
     */
    private boolean isHttpAddress(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
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
