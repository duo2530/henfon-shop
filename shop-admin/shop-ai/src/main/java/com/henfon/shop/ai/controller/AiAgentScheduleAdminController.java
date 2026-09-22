package com.henfon.shop.ai.controller;

import com.henfon.shop.ai.dto.AiScheduleBoard;
import com.henfon.shop.ai.dto.AiScheduleSaveRequest;
import com.henfon.shop.ai.dto.AiScheduleView;
import com.henfon.shop.ai.service.AiScheduleService;
import com.henfon.shop.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客服排班管理接口。
 *
 * 权限点与接待权限分开：客服自己能看班（工作台里的只读接口），但改班是排班主管的事。
 * 共用同一个权限点会让每个客服都能改所有人的班次，而排班一旦被误改，影响的是服务时段
 * 判断——买家侧看到的就是"非服务时间"这类对外提示。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@RestController
@RequestMapping("/api/admin/ai/agent/schedule")
public class AiAgentScheduleAdminController {

    private final AiScheduleService scheduleService;

    /**
     * 创建排班管理控制器。
     *
     * @param scheduleService 排班服务
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentScheduleAdminController(AiScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /**
     * 排班页整块数据：可选客服与已有排班。
     *
     * @return 名册与排班
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ai:agent:schedule')")
    public ApiResponse<AiScheduleBoard> board() {
        return ApiResponse.success(scheduleService.board(), requestId());
    }

    /**
     * 保存一条排班，同一天重复保存为覆盖。
     *
     * @param request 排班内容
     * @return 保存后的排班
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ai:agent:schedule')")
    public ApiResponse<AiScheduleView> save(@Valid @RequestBody AiScheduleSaveRequest request) {
        return ApiResponse.success(scheduleService.save(request), requestId());
    }

    /**
     * 删除一条排班。
     *
     * @param id 排班记录 ID
     * @return 空响应
     * @author Henfon
     * @date 2026-09-21
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:agent:schedule')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ApiResponse.success(null, requestId());
    }

    /**
     * 获取请求链路标识。
     *
     * @return 请求链路标识
     * @author Henfon
     * @date 2026-09-21
     */
    private String requestId() {
        return MDC.get("requestId");
    }
}
