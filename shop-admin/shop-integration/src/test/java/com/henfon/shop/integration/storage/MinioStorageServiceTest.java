package com.henfon.shop.integration.storage;

import com.henfon.shop.common.exception.BusinessException;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MinIO 文件存储服务测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
class MinioStorageServiceTest {

    /**
     * 未配置 MinIO 时返回明确业务错误码。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectUploadWhenStorageNotConfigured() {
        MinioClient client = mock(MinioClient.class);
        MinioStorageService service = new MinioStorageService(client,
                new MinioProperties("", "access", "secret", "shop"));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

        BusinessException exception = assertThrows(BusinessException.class, () -> service.upload(file));

        assertEquals("STORAGE_NOT_CONFIGURED", exception.getCode());
    }

    /**
     * 上传失败时自动重试，第三次成功后返回结果。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRetryUploadAndEventuallySucceed() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioProperties properties = new MinioProperties("http://localhost:9000", "access", "secret", "shop");
        MinioStorageService service = new MinioStorageService(client, properties);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2});
        when(client.bucketExists(any())).thenReturn(true);
        when(client.putObject(any()))
                .thenThrow(new RuntimeException("temporary-1"))
                .thenThrow(new RuntimeException("temporary-2"))
                .thenReturn(null);
        when(client.getPresignedObjectUrl(any())).thenReturn("http://localhost:9000/shop/media/a.png");

        MinioStorageService.UploadResult result = service.upload(file);

        assertEquals("image/png", result.contentType());
        verify(client, org.mockito.Mockito.times(3)).putObject(any());
    }

    /**
     * MinIO 存储桶可访问时健康检查返回成功并更新指标。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldReportHealthyWhenBucketExists() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioStorageService service = new MinioStorageService(client,
                new MinioProperties("http://localhost:9000", "access", "secret", "shop"));
        when(client.bucketExists(any())).thenReturn(true);

        assertTrue(service.checkHealth());
        assertEquals(1, service.getOperationSuccessCount());
        assertEquals(0, service.getConsecutiveFailureCount());
    }

    /**
     * MinIO 存储桶不可访问时健康检查返回失败并累计指标。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldReportUnhealthyWhenBucketCheckFails() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioStorageService service = new MinioStorageService(client,
                new MinioProperties("http://localhost:9000", "access", "secret", "shop"));
        when(client.bucketExists(any())).thenThrow(new RuntimeException("offline"));

        assertFalse(service.checkHealth());
        assertEquals(1, service.getOperationFailureCount());
        assertEquals(1, service.getConsecutiveFailureCount());
    }

    /**
     * 健康指示器输出 MinIO 运行指标和状态。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldExposeHealthDetails() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioStorageService service = new MinioStorageService(client,
                new MinioProperties("http://localhost:9000", "access", "secret", "shop"));
        when(client.bucketExists(any())).thenReturn(true);

        var health = new MinioHealthIndicator(service).health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals(1L, health.getDetails().get("operationSuccessCount"));
        assertTrue((Boolean) health.getDetails().get("configured"));
    }
}
