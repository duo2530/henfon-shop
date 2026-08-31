package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.identity.entity.SysLoginLog;
import com.henfon.shop.identity.entity.SysOperLog;
import com.henfon.shop.identity.mapper.SysLoginLogMapper;
import com.henfon.shop.identity.mapper.SysOperLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 系统登录与操作审计日志查询服务。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class AuditLogService {

    private final SysLoginLogMapper loginLogMapper;
    private final SysOperLogMapper operLogMapper;

    /**
     * 创建审计日志服务。
     *
     * @param loginLogMapper 登录日志数据访问对象
     * @param operLogMapper 操作日志数据访问对象
     * @author Henfon
     * @date 2026-08-31
     */
    public AuditLogService(SysLoginLogMapper loginLogMapper, SysOperLogMapper operLogMapper) {
        this.loginLogMapper = loginLogMapper;
        this.operLogMapper = operLogMapper;
    }

    /**
     * 分页查询登录日志。
     *
     * @param username 用户名关键字
     * @param status 登录状态
     * @param current 页码
     * @param size 页大小
     * @return 登录日志分页结果
     * @author Henfon
     * @date 2026-08-31
     */
    public IPage<SysLoginLog> pageLogin(String username, Integer status, long current, long size) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String keyword = StringUtils.hasText(username) ? username.trim() : null;
        return loginLogMapper.selectPage(new Page<>(safeCurrent, safeSize), new LambdaQueryWrapper<SysLoginLog>()
                .like(keyword != null, SysLoginLog::getUsername, keyword)
                .eq(status != null, SysLoginLog::getLoginStatus, status)
                .orderByDesc(SysLoginLog::getLoginAt));
    }

    /**
     * 分页查询操作审计日志。
     *
     * @param username 操作用户名关键字
     * @param moduleKey 业务模块标识
     * @param current 页码
     * @param size 页大小
     * @return 操作日志分页结果
     * @author Henfon
     * @date 2026-08-31
     */
    public IPage<SysOperLog> pageOperation(String username, String moduleKey, long current, long size) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String normalizedUsername = StringUtils.hasText(username) ? username.trim() : null;
        String normalizedModule = StringUtils.hasText(moduleKey) ? moduleKey.trim() : null;
        return operLogMapper.selectPage(new Page<>(safeCurrent, safeSize), new LambdaQueryWrapper<SysOperLog>()
                .like(normalizedUsername != null, SysOperLog::getUsername, normalizedUsername)
                .eq(normalizedModule != null, SysOperLog::getModuleKey, normalizedModule)
                .orderByDesc(SysOperLog::getCreatedAt));
    }

    /**
     * 记录管理员登录结果，使用独立事务避免认证失败回滚日志。
     *
     * @param userId 用户ID，可为空
     * @param username 登录用户名
     * @param status 登录状态，1成功、0失败
     * @param loginIp 客户端IP
     * @param failureReason 失败原因
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(Long userId, String username, int status, String loginIp, String failureReason) {
        SysLoginLog log = new SysLoginLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setLoginStatus(status);
        log.setLoginIp(loginIp);
        log.setFailureReason(failureReason);
        loginLogMapper.insert(log);
    }
}
