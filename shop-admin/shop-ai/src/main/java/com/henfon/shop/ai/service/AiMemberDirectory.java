package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 买家名册：把会话上的会员 ID 解析成客服看得懂的名字。
 *
 * 存在的理由跟 {@link AiAgentDirectory} 一样：等待队列上如果只有一串会员 ID，客服接了会话
 * 也不知道对方是谁；而会话标题取的是"买家对智能客服说的第一句话"，可能是「你是」「在吗」
 * 这类没有辨识度的片段，拿它当队列的主标识会让人对着 "你是" 发愣。
 *
 * 名称取"昵称 → 账号 → 会员编号"的降级顺序，与内容模块的通知收件人显示口径一致。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Component
public class AiMemberDirectory {

    /** 会员记录查不到时的兜底名称前缀。 */
    private static final String FALLBACK_PREFIX = "会员";

    private final MemberUserMapper memberMapper;

    /**
     * 创建买家名册。
     *
     * @param memberMapper 会员数据访问对象
     * @author Henfon
     * @date 2026-09-21
     */
    public AiMemberDirectory(MemberUserMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    /**
     * 解析单个会员的显示名。
     *
     * @param memberId 会员 ID，可为空
     * @return 显示名，未登录会话返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    public String displayName(Long memberId) {
        if (memberId == null) {
            return null;
        }
        return displayName(memberMapper.selectById(memberId), memberId);
    }

    /**
     * 批量解析会员显示名。
     *
     * 等待队列一次最多几十条，一次查库比逐条查询更划算；查不到的 ID 不进结果，
     * 由调用方落到兜底名称。
     *
     * @param memberIds 会员 ID 集合
     * @return 会员 ID 到显示名的映射
     * @author Henfon
     * @date 2026-09-21
     */
    public Map<Long, String> displayNames(List<Long> memberIds) {
        Map<Long, String> names = new HashMap<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return names;
        }
        List<Long> distinct = memberIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return names;
        }
        List<MemberUser> members = memberMapper.selectList(Wrappers.lambdaQuery(MemberUser.class)
                .in(MemberUser::getId, distinct));
        if (members != null) {
            members.forEach(member -> names.put(member.getId(), displayName(member, member.getId())));
        }
        return names;
    }

    /**
     * 从会员记录取显示名。
     *
     * @param member 会员，可为空
     * @param memberId 会员 ID
     * @return 显示名
     * @author Henfon
     * @date 2026-09-21
     */
    private String displayName(MemberUser member, Long memberId) {
        if (member == null) {
            return FALLBACK_PREFIX + memberId;
        }
        if (StringUtils.hasText(member.getNickname())) {
            return member.getNickname().trim();
        }
        if (StringUtils.hasText(member.getUsername())) {
            return member.getUsername().trim();
        }
        return FALLBACK_PREFIX + member.getId();
    }
}
