package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.dto.MemberAdminAdjustRequest;
import com.henfon.shop.identity.dto.MemberAdminUpdateRequest;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 后台会员管理应用服务。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class MemberAdminService {

    private final MemberUserMapper memberUserMapper;

    /**
     * 创建后台会员管理服务。
     *
     * @param memberUserMapper 会员数据访问对象
     * @author Henfon
     * @date 2026-08-30
     */
    public MemberAdminService(MemberUserMapper memberUserMapper) {
        this.memberUserMapper = memberUserMapper;
    }

    /**
     * 分页查询会员资料。
     *
     * @param keyword 会员编号、用户名、昵称、手机号或邮箱关键字
     * @param memberLevel 会员等级
     * @param status 账户状态
     * @param current 当前页
     * @param size 页大小
     * @return 会员分页数据
     * @author Henfon
     * @date 2026-08-30
     */
    public IPage<MemberUser> page(String keyword, String memberLevel, Integer status,
                                  long current, long size) {
        // 门户会员统一属于默认租户，后台查询不允许跨租户读取数据。
        String normalizedLevel = StringUtils.hasText(memberLevel) ? memberLevel.trim().toUpperCase() : null;
        LambdaQueryWrapper<MemberUser> wrapper = new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getTenantId, 0L)
                .eq(normalizedLevel != null, MemberUser::getMemberLevel, normalizedLevel)
                .eq(status != null, MemberUser::getStatus, status)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(MemberUser::getMemberNo, keyword)
                        .or().like(MemberUser::getUsername, keyword)
                        .or().like(MemberUser::getNickname, keyword)
                        .or().like(MemberUser::getPhone, keyword)
                        .or().like(MemberUser::getEmail, keyword))
                .orderByDesc(MemberUser::getCreatedAt);
        return memberUserMapper.selectPage(new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 200)), wrapper);
    }

    /**
     * 冻结或解冻会员账户。
     *
     * @param id 会员ID
     * @param status 账户状态，1 正常，0 冻结
     * @author Henfon
     * @date 2026-08-30
     */
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("MEMBER_STATUS_INVALID", "会员状态只能是正常或冻结");
        }
        MemberUser member = memberUserMapper.selectOne(new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getId, id).eq(MemberUser::getTenantId, 0L));
        if (member == null) {
            throw new BusinessException("MEMBER_NOT_FOUND", "会员不存在");
        }
        if (Integer.valueOf(status).equals(member.getStatus())) {
            return;
        }
        // 使用版本字段执行乐观锁更新，防止后台并发覆盖账户状态。
        member.setStatus(status);
        if (memberUserMapper.updateById(member) == 0) {
            throw new BusinessException("MEMBER_CONCURRENT_UPDATE", "会员状态已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 更新会员基础资料。
     *
     * @param id 会员ID
     * @param request 资料更新请求
     * @return 更新后的会员
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public MemberUser update(Long id, MemberAdminUpdateRequest request) {
        MemberUser member = requireMember(id);
        // 会员昵称为数据库必填字段，空值请求保留原昵称，避免资料编辑误清空。
        member.setNickname(StringUtils.hasText(request.nickname()) ? request.nickname().trim() : member.getNickname());
        member.setPhone(trimToNull(request.phone()));
        member.setEmail(trimToNull(request.email()));
        member.setMemberLevel(StringUtils.hasText(request.memberLevel()) ? request.memberLevel().trim().toUpperCase() : member.getMemberLevel());
        member.setAvatarUrl(trimToNull(request.avatarUrl()));
        member.setRemark(trimToNull(request.remark()));
        updateMember(member);
        return member;
    }

    /**
     * 调整会员余额和积分，并阻止余额或积分出现负数。
     *
     * @param id 会员ID
     * @param request 调账请求
     * @return 更新后的会员
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public MemberUser adjust(Long id, MemberAdminAdjustRequest request) {
        MemberUser member = requireMember(id);
        long currentPoints = member.getPoints() == null ? 0L : member.getPoints();
        BigDecimal currentBalance = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();
        long nextPoints;
        try {
            nextPoints = Math.addExact(currentPoints, request.pointsDelta());
        } catch (ArithmeticException exception) {
            throw new BusinessException("MEMBER_ASSET_INVALID", "积分调整结果超出范围");
        }
        BigDecimal nextBalance = currentBalance.add(request.balanceDelta()).setScale(2, RoundingMode.HALF_UP);
        if (nextPoints < 0 || nextBalance.signum() < 0) {
            throw new BusinessException("MEMBER_ASSET_INVALID", "余额和积分不能为负数");
        }
        member.setPoints(nextPoints);
        member.setBalance(nextBalance);
        if (StringUtils.hasText(request.remark())) {
            member.setRemark("调账：" + request.remark().trim());
        }
        updateMember(member);
        return member;
    }

    /**
     * 查询会员并校验默认租户归属。
     *
     * @param id 会员ID
     * @return 会员实体
     * @author Henfon
     * @date 2026-08-30
     */
    private MemberUser requireMember(Long id) {
        MemberUser member = memberUserMapper.selectOne(new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getId, id).eq(MemberUser::getTenantId, 0L));
        if (member == null) {
            throw new BusinessException("MEMBER_NOT_FOUND", "会员不存在");
        }
        return member;
    }

    /**
     * 使用乐观锁更新会员资料。
     *
     * @param member 会员实体
     * @author Henfon
     * @date 2026-08-30
     */
    private void updateMember(MemberUser member) {
        try {
            if (memberUserMapper.updateById(member) == 0) {
                throw new BusinessException("MEMBER_CONCURRENT_UPDATE", "会员资料已被其他操作修改，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("MEMBER_CONTACT_EXISTS", "手机号或邮箱已被其他会员使用");
        }
    }

    /**
     * 清理可选字符串。
     *
     * @param value 原始字符串
     * @return 清理后的字符串或空值
     * @author Henfon
     * @date 2026-08-30
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
