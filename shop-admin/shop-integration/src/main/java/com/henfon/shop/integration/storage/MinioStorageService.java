package com.henfon.shop.integration.storage;

import com.henfon.shop.common.exception.BusinessException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final int MAX_UPLOAD_ATTEMPTS = 3;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "application/pdf");

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private final Logger logger = LoggerFactory.getLogger(MinioStorageService.class);
    private final AtomicLong operationSuccessCount = new AtomicLong();
    private final AtomicLong operationFailureCount = new AtomicLong();
    private final AtomicLong consecutiveFailureCount = new AtomicLong();
    private volatile String lastFailureMessage;
    private volatile long lastFailureAt;

    private static final long FAILURE_ALERT_THRESHOLD = 3;

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
        ensureConfigured();
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
        for (int attempt = 1; attempt <= MAX_UPLOAD_ATTEMPTS; attempt++) {
            try {
                ensureBucket();
                // 每次重试重新获取输入流，避免上一次失败已消耗流导致空文件上传。
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(properties.bucket())
                        .object(objectKey)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(contentType)
                        .build());
                recordSuccess();
                return new UploadResult(objectKey, presign(objectKey), file.getSize(), contentType);
            } catch (Exception exception) {
                recordFailure("文件上传失败: " + exception.getMessage(), exception);
                logger.warn("MinIO 文件上传失败，第{}次尝试，共{}次，对象键={}", attempt, MAX_UPLOAD_ATTEMPTS, objectKey,
                        exception);
            }
        }
        throw new BusinessException("STORAGE_UPLOAD_FAILED",
                "文件上传失败，已重试" + MAX_UPLOAD_ATTEMPTS + "次，请检查存储服务配置或稍后重试");
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
        ensureConfigured();
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
            recordFailure("生成文件访问地址失败: " + exception.getMessage(), exception);
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
        ensureConfigured();
        if (!StringUtils.hasText(objectKey) || objectKey.contains("..") || objectKey.startsWith("/")) {
            throw new BusinessException("STORAGE_OBJECT_KEY_INVALID", "文件对象键不合法");
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket()).object(objectKey).build());
        } catch (Exception exception) {
            recordFailure("文件删除失败: " + exception.getMessage(), exception);
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
     * 校验 MinIO 配置和客户端状态。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    private void ensureConfigured() {
        if (properties == null || !properties.isConfigured() || minioClient == null) {
            throw new BusinessException("STORAGE_NOT_CONFIGURED", "文件存储服务未配置，请联系管理员");
        }
    }

    /**
     * 记录一次成功的存储操作并清零连续失败次数。
     *
     * @author Henfon
     * @date 2026-09-04
     * @描述 更新 MinIO 运行指标，供健康检查和监控端点读取
     */
    void recordSuccess() {
        operationSuccessCount.incrementAndGet();
        consecutiveFailureCount.set(0);
    }

    /**
     * 判断 MinIO 服务配置是否完整。
     *
     * @return 配置是否完整且客户端可用
     * @author Henfon
     * @date 2026-09-04
     * @描述 供健康检查端点区分配置缺失和服务不可用
     */
    public boolean isConfigured() {
        return properties != null && properties.isConfigured() && minioClient != null;
    }

    /**
     * 记录一次失败的存储操作，并在连续失败达到阈值时输出结构化告警。
     *
     * @param message 失败摘要
     * @param exception 原始异常
     * @author Henfon
     * @date 2026-09-04
     * @描述 维护失败计数和告警状态，便于日志平台接入通知
     */
    void recordFailure(String message, Exception exception) {
        operationFailureCount.incrementAndGet();
        long consecutive = consecutiveFailureCount.incrementAndGet();
        lastFailureMessage = message;
        lastFailureAt = System.currentTimeMillis();
        if (consecutive >= FAILURE_ALERT_THRESHOLD) {
            logger.error("MinIO 存储连续失败告警，consecutiveFailures={}, lastFailure={}", consecutive, message,
                    exception);
        }
    }

    /**
     * 获取存储操作成功次数。
     *
     * @return 成功次数
     * @author Henfon
     * @date 2026-09-04
     * @描述 为 Actuator 健康指标提供成功计数
     */
    public long getOperationSuccessCount() {
        return operationSuccessCount.get();
    }

    /**
     * 获取存储操作失败次数。
     *
     * @return 失败次数
     * @author Henfon
     * @date 2026-09-04
     * @描述 为 Actuator 健康指标提供失败计数
     */
    public long getOperationFailureCount() {
        return operationFailureCount.get();
    }

    /**
     * 获取当前连续失败次数。
     *
     * @return 连续失败次数
     * @author Henfon
     * @date 2026-09-04
     * @描述 判断是否达到存储告警阈值
     */
    public long getConsecutiveFailureCount() {
        return consecutiveFailureCount.get();
    }

    /**
     * 获取最近一次失败时间戳。
     *
     * @return Unix 毫秒时间戳，未失败时为 0
     * @author Henfon
     * @date 2026-09-04
     * @描述 提供故障发生时间用于监控定位
     */
    public long getLastFailureAt() {
        return lastFailureAt;
    }

    /**
     * 获取最近一次失败摘要。
     *
     * @return 失败摘要
     * @author Henfon
     * @date 2026-09-04
     * @描述 提供健康端点展示的安全错误信息
     */
    public String getLastFailureMessage() {
        return lastFailureMessage;
    }

    /**
     * 检查 MinIO 默认存储桶是否可访问。
     *
     * @return 检查结果
     * @author Henfon
     * @date 2026-09-04
     * @描述 执行轻量级连通性探测，不进行写入操作
     */
    public boolean checkHealth() {
        if (properties == null || !properties.isConfigured() || minioClient == null) {
            return false;
        }
        try {
            boolean exists = minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (exists) {
                recordSuccess();
            } else {
                recordFailure("MinIO 存储桶不存在: " + properties.bucket(), new IllegalStateException("bucket missing"));
            }
            return exists;
        } catch (Exception exception) {
            recordFailure("MinIO 健康检查失败: " + exception.getMessage(), exception);
            return false;
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
