package com.henfon.shop.identity.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.dto.MemberAdminAdjustRequest;
import com.henfon.shop.identity.dto.MemberAdminUpdateRequest;
import com.henfon.shop.identity.dto.MemberTagSaveRequest;
import com.henfon.shop.identity.dto.MemberUserTagsRequest;
import com.henfon.shop.identity.entity.MemberTag;
import com.henfon.shop.identity.service.MemberAdminService;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 后台会员管理接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/admin/member")
public class MemberAdminController {

    private final MemberAdminService memberAdminService;

    /**
     * 创建后台会员管理控制器。
     *
     * @param memberAdminService 会员管理服务
     * @author Henfon
     * @date 2026-08-30
     */
    public MemberAdminController(MemberAdminService memberAdminService) {
        this.memberAdminService = memberAdminService;
    }

    /**
     * 分页查询会员列表。
     *
     * @param keyword 会员关键字
     * @param memberLevel 会员等级
     * @param status 账户状态
     * @param current 当前页
     * @param size 页大小
     * @return 会员分页结果
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('member:user:query')")
    public ApiResponse<IPage<MemberUser>> page(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String memberLevel,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size) {
        // 分页和筛选逻辑集中在服务层，控制器只负责参数接收和权限校验。
        return ApiResponse.success(memberAdminService.page(keyword, memberLevel, status, current, size),
                MDC.get("requestId"));
    }

    /**
     * 冻结或解冻会员账户。
     *
     * @param id 会员ID
     * @param status 账户状态，1 正常，0 冻结
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasAuthority('member:user:status')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        // 服务层会校验会员归属、状态枚举和乐观锁版本。
        memberAdminService.updateStatus(id, status);
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 更新会员基础资料。
     *
     * @param id 会员ID
     * @param request 更新请求
     * @return 更新后的会员
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('member:user:status')")
    public ApiResponse<MemberUser> update(@PathVariable Long id,
                                          @Valid @RequestBody MemberAdminUpdateRequest request) {
        return ApiResponse.success(memberAdminService.update(id, request), MDC.get("requestId"));
    }

    /**
     * 调整会员余额和积分。
     *
     * @param id 会员ID
     * @param request 调账请求
     * @return 更新后的会员
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/users/{id}/assets")
    @PreAuthorize("hasAuthority('member:user:status')")
    public ApiResponse<MemberUser> adjust(@PathVariable Long id,
                                          @Valid @RequestBody MemberAdminAdjustRequest request) {
        return ApiResponse.success(memberAdminService.adjust(id, request), MDC.get("requestId"));
    }

    /**
     * 查询会员标签。
     *
     * @return 启用标签列表
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping("/tags")
    @PreAuthorize("hasAuthority('member:user:query')")
    public ApiResponse<List<MemberTag>> listTags() {
        return ApiResponse.success(memberAdminService.listTags(), MDC.get("requestId"));
    }

    /**
     * 新增会员标签。
     *
     * @param request 标签请求
     * @return 保存后的标签
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/tags")
    @PreAuthorize("hasAuthority('member:user:status')")
    public ApiResponse<MemberTag> saveTag(@Valid @RequestBody MemberTagSaveRequest request) {
        return ApiResponse.success(memberAdminService.saveTag(null, request), MDC.get("requestId"));
    }

    /**
     * 修改会员标签。
     *
     * @param id 标签ID
     * @param request 标签请求
     * @return 保存后的标签
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/tags/{id}")
    @PreAuthorize("hasAuthority('member:user:status')")
    public ApiResponse<MemberTag> updateTag(@PathVariable Long id,
                                             @Valid @RequestBody MemberTagSaveRequest request) {
        return ApiResponse.success(memberAdminService.saveTag(id, request), MDC.get("requestId"));
    }

    /**
     * 删除会员标签。
     *
     * @param id 标签ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/tags/{id}")
    @PreAuthorize("hasAuthority('member:user:status')")
    public ApiResponse<Void> deleteTag(@PathVariable Long id) {
        memberAdminService.deleteTag(id);
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 覆盖会员标签绑定。
     *
     * @param id 会员ID
     * @param request 标签名称列表
     * @return 更新后的会员
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/users/{id}/tags")
    @PreAuthorize("hasAuthority('member:user:status')")
    public ApiResponse<MemberUser> updateTags(@PathVariable Long id,
                                               @Valid @RequestBody MemberUserTagsRequest request) {
        return ApiResponse.success(memberAdminService.updateTags(id, request), MDC.get("requestId"));
    }
}
