package com.henfon.shop.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.security.MemberPrincipalResolver;
import com.henfon.shop.integration.storage.MinioStorageService;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 门户会员文件上传接口。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@RestController
@RequestMapping("/api/portal/storage")
public class StoragePortalController {

    private final MinioStorageService storageService;

    /**
     * 创建门户文件服务控制器。
     *
     * @param storageService MinIO 文件服务
     * @author Henfon
     * @date 2026-09-01
     */
    public StoragePortalController(MinioStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 上传会员评价凭证图片。
     *
     * @param file 图片文件
     * @param authentication 当前认证信息
     * @return 文件对象信息
     * @author Henfon
     * @date 2026-09-01
     */
    @PostMapping("/upload")
    public ApiResponse<MinioStorageService.UploadResult> upload(@RequestParam("file") MultipartFile file,
                                                                 Authentication authentication) {
        // 先解析会员身份，避免匿名请求占用存储空间。
        MemberPrincipalResolver.requireMemberId(authentication);
        return ApiResponse.success(storageService.upload(file), MDC.get("requestId"));
    }
}
