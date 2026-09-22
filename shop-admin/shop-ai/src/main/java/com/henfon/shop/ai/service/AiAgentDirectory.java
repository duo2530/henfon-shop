package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.henfon.shop.ai.dto.AiScheduleBoard.AiScheduleAgent;
import com.henfon.shop.ai.entity.AiAgentStatus;
import com.henfon.shop.ai.mapper.AiAgentStatusMapper;
import com.henfon.shop.identity.entity.SysUser;
import com.henfon.shop.identity.mapper.SysUserMapper;
import com.henfon.shop.identity.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服人员名册。
 *
 * "谁是客服"这个判断只在一个地方定义：持有客服工作台权限（ai:agent:serve）的账号。按角色名
 * 找客服是不可靠的——角色可以改名，同一个人也可能通过另一个角色拿到同一权限——所以这里一律
 * 按权限编码反查，排班、评价快照、上线名称都走这一份口径。
 *
 * 名称取"真实姓名 → 昵称 → 登录名"的降级顺序：展示给买家的客服名应该像个人名，
 * 而不是登录账号。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Component
public class AiAgentDirectory {

    /** 客服工作台权限编码，同时也是坐席身份的判定依据。 */
    public static final String AGENT_PERMISSION = "ai:agent:serve";

    /** 找不到账号时的兜底名称。 */
    private static final String FALLBACK_NAME = "客服";

    private final SysUserRoleMapper userRoleMapper;

    private final SysUserMapper userMapper;

    private final AiAgentStatusMapper statusMapper;

    /**
     * 创建客服名册。
     *
     * @param userRoleMapper 用户角色关联数据访问对象
     * @param userMapper 系统用户数据访问对象
     * @param statusMapper 坐席状态数据访问对象
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentDirectory(SysUserRoleMapper userRoleMapper,
                            SysUserMapper userMapper,
                            AiAgentStatusMapper statusMapper) {
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
        this.statusMapper = statusMapper;
    }

    /**
     * 全部客服账号 ID。
     *
     * @return 账号 ID 列表，按账号 ID 升序
     * @author Henfon
     * @date 2026-09-21
     */
    public List<Long> agentIds() {
        List<Long> ids = userRoleMapper.selectUserIdsByPermission(AGENT_PERMISSION);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().distinct().sorted().toList();
    }

    /**
     * 可排班的客服及其当前在线状态。
     *
     * @return 客服列表
     * @author Henfon
     * @date 2026-09-21
     */
    public List<AiScheduleAgent> agents() {
        List<Long> ids = agentIds();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, String> statuses = statuses(ids);
        return enabledUsers(ids).stream()
                .map(user -> new AiScheduleAgent(user.getId(), user.getUsername(), displayName(user),
                        statuses.getOrDefault(user.getId(), AiAgentStatus.OFFLINE)))
                .toList();
    }

    /**
     * 解析坐席展示名称。
     *
     * @param agentId 账号 ID
     * @return 展示名称，账号不存在时返回兜底名称
     * @author Henfon
     * @date 2026-09-21
     */
    public String displayName(Long agentId) {
        if (agentId == null) {
            return FALLBACK_NAME;
        }
        SysUser user = userMapper.selectById(agentId);
        return user == null ? FALLBACK_NAME : displayName(user);
    }

    /**
     * 批量解析坐席展示名称。
     *
     * @param agentIds 账号 ID 集合
     * @return 账号 ID 到展示名称的映射
     * @author Henfon
     * @date 2026-09-21
     */
    public Map<Long, String> displayNames(List<Long> agentIds) {
        Map<Long, String> names = new HashMap<>();
        for (SysUser user : enabledUsers(agentIds)) {
            names.put(user.getId(), displayName(user));
        }
        return names;
    }

    /**
     * 读取账号，忽略已停用与已删除的。
     *
     * @param ids 账号 ID 列表
     * @return 账号列表
     * @author Henfon
     * @date 2026-09-21
     */
    private List<SysUser> enabledUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<SysUser> users = userMapper.selectList(Wrappers.lambdaQuery(SysUser.class)
                .in(SysUser::getId, ids)
                .eq(SysUser::getStatus, 1));
        if (users == null) {
            return List.of();
        }
        return users.stream()
                .sorted(Comparator.comparing(SysUser::getId))
                .toList();
    }

    /**
     * 读取坐席状态。
     *
     * @param ids 账号 ID 列表
     * @return 账号 ID 到状态的映射
     * @author Henfon
     * @date 2026-09-21
     */
    private Map<Long, String> statuses(List<Long> ids) {
        Map<Long, String> statuses = new HashMap<>();
        List<AiAgentStatus> rows = statusMapper.selectList(Wrappers.lambdaQuery(AiAgentStatus.class)
                .in(AiAgentStatus::getAgentId, ids));
        if (rows != null) {
            rows.forEach(row -> statuses.put(row.getAgentId(), row.getStatus()));
        }
        return statuses;
    }

    /**
     * 从账号取展示名称。
     *
     * @param user 账号
     * @return 展示名称
     * @author Henfon
     * @date 2026-09-21
     */
    private String displayName(SysUser user) {
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName().trim();
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername() : FALLBACK_NAME;
    }
}
