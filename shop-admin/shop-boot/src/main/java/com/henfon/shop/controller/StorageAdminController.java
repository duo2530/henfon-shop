package com.henfon.shop.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.integration.storage.MinioStorageService;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端统一文件服务接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/admin/storage")
public class StorageAdminController {

    private final MinioStorageService storageService;

    /**
     * 创建文件服务控制器。
     *
     * @param storageService MinIO 文件服务
     * @author Henfon
     * @date 2026-08-30
     */
    public StorageAdminController(MinioStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 上传媒体文件。
     *
     * @param file 上传文件
     * @return 文件对象信息
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('catalog:product:query', 'payment:invoice:status', 'system:user:update')")
    public ApiResponse<MinioStorageService.UploadResult> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(storageService.upload(file), MDC.get("requestId"));
    }

    /**
     * 删除媒体对象。
     *
     * @param objectKey 对象键
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('catalog:product:query', 'payment:invoice:status')")
    public ApiResponse<Void> delete(@RequestParam @NotBlank String objectKey) {
        storageService.delete(objectKey);
        return ApiResponse.success(MDC.get("requestId"));
    }
}
