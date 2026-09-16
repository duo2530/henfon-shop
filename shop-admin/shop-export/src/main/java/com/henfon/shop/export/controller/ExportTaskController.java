package com.henfon.shop.export.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.export.dto.ExportFile;
import com.henfon.shop.export.dto.ExportSubmitRequest;
import com.henfon.shop.export.dto.ExportTaskResponse;
import com.henfon.shop.export.entity.ExportTask;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.service.ExportTaskService;
import com.henfon.shop.identity.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 后台导出中心接口。
 *
 * <p>提交接口只负责排队，真正的文件生成由消息消费者或兜底调度完成，因此页面不会因为
 * 数据量大而长时间等待；下载接口以流式方式转发对象存储中的文件，不经过预签名地址，
 * 复用既有的登录鉴权与任务归属校验。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
@RestController
@RequestMapping("/api/admin/export")
public class ExportTaskController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExportTaskService exportTaskService;

    /**
     * 创建导出中心控制器。
     *
     * @param exportTaskService 导出任务服务
     * @author Henfon
     * @date 2026-09-16
     */
    public ExportTaskController(ExportTaskService exportTaskService) {
        this.exportTaskService = exportTaskService;
    }

    /**
     * 提交导出任务。
     *
     * <p>除导出中心的创建权限外，还需要持有该类型数据自身的导出权限，
     * 例如商品库导出需要 catalog:product:export。</p>
     *
     * @param request 提交请求
     * @param authentication 当前管理员认证信息
     * @return 新建的任务信息
     * @author Henfon
     * @date 2026-09-16
     */
    @PostMapping("/tasks")
    @PreAuthorize("hasAuthority('export:task:create')")
    public ApiResponse<ExportTaskResponse> submit(@Valid @RequestBody ExportSubmitRequest request,
                                                 Authentication authentication) {
        AuthenticatedUser operator = requireOperator(authentication);
        requireTypePermission(request.exportType(), authentication);
        return ApiResponse.success(exportTaskService.submit(request, operator.userId(), operator.username()),
                MDC.get("requestId"));
    }

    /**
     * 分页查询当前管理员的导出任务。
     *
     * @param current 当前页
     * @param size 页大小
     * @param authentication 当前管理员认证信息
     * @return 任务分页结果
     * @author Henfon
     * @date 2026-09-16
     */
    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('export:task:query')")
    public ApiResponse<IPage<ExportTaskResponse>> page(@RequestParam(defaultValue = "1") long current,
                                                       @RequestParam(defaultValue = "20") long size,
                                                       Authentication authentication) {
        AuthenticatedUser operator = requireOperator(authentication);
        return ApiResponse.success(exportTaskService.page(current, size, operator.userId()), MDC.get("requestId"));
    }

    /**
     * 下载导出文件。
     *
     * <p>除导出中心的下载权限外，还需要持有该任务所属类型的导出权限，避免管理员被撤销某类数据的
     * 导出能力后仍能从历史任务里取走文件。</p>
     *
     * @param taskId 任务ID
     * @param authentication 当前管理员认证信息
     * @return 文件流响应
     * @author Henfon
     * @date 2026-09-16
     */
    @GetMapping("/tasks/{taskId}/file")
    @PreAuthorize("hasAuthority('export:task:download')")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long taskId, Authentication authentication) {
        AuthenticatedUser operator = requireOperator(authentication);
        requireTypePermissionOfTask(taskId, operator.userId(), authentication);
        ExportFile file = exportTaskService.download(taskId, operator.userId());
        // 中文文件名必须按 RFC 5987 编码，否则浏览器下载后会出现乱码。
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(new InputStreamResource(file.content()));
    }

    /**
     * 重新排队生成失败的任务。
     *
     * <p>同样需要该任务所属类型的导出权限：重试会重新读取业务数据生成文件，
     * 等价于再次导出一次。</p>
     *
     * @param taskId 任务ID
     * @param authentication 当前管理员认证信息
     * @return 重新排队后的任务信息
     * @author Henfon
     * @date 2026-09-16
     */
    @PostMapping("/tasks/{taskId}/retry")
    @PreAuthorize("hasAuthority('export:task:create')")
    public ApiResponse<ExportTaskResponse> retry(@PathVariable Long taskId, Authentication authentication) {
        AuthenticatedUser operator = requireOperator(authentication);
        requireTypePermissionOfTask(taskId, operator.userId(), authentication);
        return ApiResponse.success(exportTaskService.retry(taskId, operator.userId()), MDC.get("requestId"));
    }

    /**
     * 从导出中心移除任务记录并清理已生成的文件。
     *
     * <p>移除会连带删除该类型导出文件，故与下载、重试一致校验类型权限。</p>
     *
     * @param taskId 任务ID
     * @param authentication 当前管理员认证信息
     * @return 空响应
     * @author Henfon
     * @date 2026-09-16
     */
    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority('export:task:create')")
    public ApiResponse<Void> remove(@PathVariable Long taskId, Authentication authentication) {
        AuthenticatedUser operator = requireOperator(authentication);
        requireTypePermissionOfTask(taskId, operator.userId(), authentication);
        exportTaskService.remove(taskId, operator.userId());
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 读取当前管理员认证主体。
     *
     * @param authentication 当前认证信息
     * @return 认证主体
     * @author Henfon
     * @date 2026-09-16
     */
    private AuthenticatedUser requireOperator(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new BusinessException("EXPORT_UNAUTHENTICATED", "登录状态已失效，请重新登录后再操作");
    }

    /**
     * 校验当前管理员是否具备指定导出类型的权限。
     *
     * <p>类型文本无法解析时直接放行：解析不出来意味着这是个当前版本不认识的类型，
     * 既没有对应的权限编码可用，也无法判断它属于哪类数据，此时交由导出中心的通用权限
     * （export:task:*）与任务归属校验兜住，不额外拦一道没有依据的检查。</p>
     *
     * @param exportTypeText 导出类型文本
     * @param authentication 当前认证信息
     * @author Henfon
     * @date 2026-09-16
     */
    private void requireTypePermission(String exportTypeText, Authentication authentication) {
        ExportType type = ExportType.parse(exportTypeText);
        if (type == null) {
            return;
        }
        String required = type.permission();
        boolean granted = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(required::equals);
        if (!granted) {
            throw new BusinessException("EXPORT_PERMISSION_DENIED",
                    "缺少「" + type.displayName() + "」的导出权限，请联系管理员授权");
        }
    }

    /**
     * 按任务所属类型校验导出权限。
     *
     * <p>下载、重试、移除都是对既有任务的操作，类型信息只在任务记录里，因此先按归属取出任务再校验。
     * 归属校验失败会直接抛任务不存在，不会泄漏他人任务的存在性。</p>
     *
     * @param taskId 任务ID
     * @param operatorId 当前管理员ID
     * @param authentication 当前认证信息
     * @author Henfon
     * @date 2026-09-16
     */
    private void requireTypePermissionOfTask(Long taskId, Long operatorId, Authentication authentication) {
        ExportTask task = exportTaskService.requireOwnedTask(taskId, operatorId);
        requireTypePermission(task.getExportType(), authentication);
    }
}
