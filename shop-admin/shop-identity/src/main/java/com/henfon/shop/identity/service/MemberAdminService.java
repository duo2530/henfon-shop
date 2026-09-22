package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.entity.MemberAddress;
import com.henfon.shop.identity.dto.MemberAddressAdminUpdateRequest;
import com.henfon.shop.identity.dto.MemberAddressAdminView;
import com.henfon.shop.identity.dto.MemberAddressRequest;
import com.henfon.shop.identity.dto.MemberAdminAdjustRequest;
import com.henfon.shop.identity.dto.MemberAdminCreateRequest;
import com.henfon.shop.identity.dto.MemberAdminUpdateRequest;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.identity.mapper.MemberTagMapper;
import com.henfon.shop.identity.mapper.MemberUserTagMapper;
import com.henfon.shop.identity.mapper.MemberConsumptionStatMapper;
import com.henfon.shop.identity.entity.MemberTag;
import com.henfon.shop.identity.entity.MemberUserTag;
import com.henfon.shop.identity.entity.MemberConsumptionStat;
import com.henfon.shop.identity.entity.MemberAssetAudit;
import com.henfon.shop.identity.mapper.MemberAddressMapper;
import com.henfon.shop.identity.mapper.MemberAssetAuditMapper;
import com.henfon.shop.identity.dto.MemberTagSaveRequest;
import com.henfon.shop.identity.dto.MemberUserTagsRequest;
import com.henfon.shop.integration.storage.ImageReferenceResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 后台会员管理应用服务。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class MemberAdminService {

    private final MemberUserMapper memberUserMapper;
    private final MemberTagMapper memberTagMapper;
    private final MemberUserTagMapper memberUserTagMapper;
    private final MemberConsumptionStatMapper memberConsumptionStatMapper;
    private final MemberAssetAuditMapper memberAssetAuditMapper;
    private final MemberAddressMapper memberAddressMapper;
    private final MemberPortalService memberPortalService;
    private final ImageReferenceResolver imageReferenceResolver;

    /**
     * 创建后台会员管理服务。
     *
     * @param memberUserMapper 会员数据访问对象
     * @param memberTagMapper 标签数据访问对象
     * @param memberUserTagMapper 会员标签关联数据访问对象
     * @param memberConsumptionStatMapper 消费统计数据访问对象
     * @param memberAssetAuditMapper 资产审计数据访问对象
     * @param imageReferenceResolver 媒体引用解析器
     * @author Henfon
     * @date 2026-08-30
     */
    public MemberAdminService(MemberUserMapper memberUserMapper, MemberTagMapper memberTagMapper,
                              MemberUserTagMapper memberUserTagMapper,
                              MemberConsumptionStatMapper memberConsumptionStatMapper,
                              MemberAssetAuditMapper memberAssetAuditMapper,
                              MemberAddressMapper memberAddressMapper,
                              MemberPortalService memberPortalService,
                              ImageReferenceResolver imageReferenceResolver) {
        this.memberUserMapper = memberUserMapper;
        this.memberTagMapper = memberTagMapper;
        this.memberUserTagMapper = memberUserTagMapper;
        this.memberConsumptionStatMapper = memberConsumptionStatMapper;
        this.memberAssetAuditMapper = memberAssetAuditMapper;
        this.memberAddressMapper = memberAddressMapper;
        // 改地址走门户那套逻辑而不是在这里重写一遍：「一个会员只能有一个默认地址」「删掉默认
        // 之后要补位」这些约束只有一份实现才不会前后不一致。
        this.memberPortalService = memberPortalService;
        this.imageReferenceResolver = imageReferenceResolver;
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
        return page(keyword, memberLevel, status, current, size, 0L);
    }

    /**
     * 按租户数据权限分页查询会员资料。
     *
     * @param keyword 会员编号、用户名、昵称、手机号或邮箱关键字
     * @param memberLevel 会员等级
     * @param status 账户状态
     * @param current 当前页
     * @param size 页大小
     * @param tenantId 当前数据权限租户
     * @return 会员分页数据（包含 total 总数）
     * @author Henfon
     * @date 2026-09-04
     */
    public IPage<MemberUser> page(String keyword, String memberLevel, Integer status,
                                  long current, long size, Long tenantId) {
        // 租户条件由认证主体传入，避免固定默认租户导致跨租户数据泄露。
        long scopedTenantId = tenantId == null ? 0L : tenantId;
        String normalizedLevel = StringUtils.hasText(memberLevel) ? memberLevel.trim().toUpperCase() : null;
        LambdaQueryWrapper<MemberUser> wrapper = new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getTenantId, scopedTenantId)
                .eq(normalizedLevel != null, MemberUser::getMemberLevel, normalizedLevel)
                .eq(status != null, MemberUser::getStatus, status)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(MemberUser::getMemberNo, keyword)
                        .or().like(MemberUser::getUsername, keyword)
                        .or().like(MemberUser::getNickname, keyword)
                        .or().like(MemberUser::getPhone, keyword)
                        .or().like(MemberUser::getEmail, keyword))
                .orderByDesc(MemberUser::getCreatedAt);
        IPage<MemberUser> page = memberUserMapper.selectPage(new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 200)), wrapper);
        page.getRecords().forEach(this::enrichMember);
        return page;
    }

    /**
     * 分页查询会员收货地址。
     *
     * 关键字同时匹配地址本身（收货人、电话、详细地址）和所属会员（编号、用户名、昵称、
     * 手机号）——客服手上通常只有半截手机号或买家报的收货人姓名，只能按会员查的话
     * 这条链路就断了。
     *
     * 会员这一侧按数据权限租户过滤，与会员列表页一致；地址表本身没有租户列，不再叠加
     * 过滤，否则要么拼出超长的 IN 列表，要么分页总数与实际行数对不上。
     *
     * @param keyword 收货人、电话、详细地址或会员关键字
     * @param memberId 指定会员，为空表示全部
     * @param isDefault 是否默认地址
     * @param current 当前页
     * @param size 页大小
     * @param tenantId 当前数据权限租户
     * @return 地址分页数据
     * @author Henfon
     * @date 2026-09-22
     */
    public IPage<MemberAddressAdminView> pageAddresses(String keyword, Long memberId, Integer isDefault,
                                                       long current, long size, Long tenantId) {
        long scopedTenantId = tenantId == null ? 0L : tenantId;
        String text = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<Long> matched = List.of();
        if (text != null) {
            matched = memberUserMapper.selectList(new LambdaQueryWrapper<MemberUser>()
                            .eq(MemberUser::getTenantId, scopedTenantId)
                            .and(query -> query.like(MemberUser::getMemberNo, text)
                                    .or().like(MemberUser::getUsername, text)
                                    .or().like(MemberUser::getNickname, text)
                                    .or().like(MemberUser::getPhone, text))
                            .select(MemberUser::getId))
                    .stream().map(MemberUser::getId).toList();
        }
        List<Long> matchedMemberIds = matched;
        LambdaQueryWrapper<MemberAddress> wrapper = new LambdaQueryWrapper<MemberAddress>()
                .eq(memberId != null, MemberAddress::getMemberId, memberId)
                .eq(isDefault != null, MemberAddress::getIsDefault, isDefault)
                .and(text != null, query -> query
                        .like(MemberAddress::getReceiverName, text)
                        .or().like(MemberAddress::getReceiverPhone, text)
                        .or().like(MemberAddress::getDetailAddress, text)
                        .or().in(!matchedMemberIds.isEmpty(), MemberAddress::getMemberId, matchedMemberIds))
                .orderByDesc(MemberAddress::getIsDefault)
                .orderByDesc(MemberAddress::getUpdatedAt);
        IPage<MemberAddress> page = memberAddressMapper.selectPage(
                new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 200)), wrapper);
        Map<Long, MemberUser> members = memberUserMapper.selectBatchIds(page.getRecords().stream()
                        .map(MemberAddress::getMemberId).distinct().toList())
                .stream().collect(Collectors.toMap(MemberUser::getId, Function.identity(), (left, right) -> left));
        return page.convert(address -> toAddressView(address, members));
    }

    /**
     * 修改会员收货地址。
     *
     * 会员归属以库里的记录为准，不采信请求里的 memberId：后台改地址不该顺带把地址划到
     * 另一个会员名下，那是另一种操作，走错一次就是发错货。
     *
     * @param id 地址ID
     * @param request 地址内容
     * @author Henfon
     * @date 2026-09-22
     */
    @Transactional
    public void updateAddress(Long id, MemberAddressAdminUpdateRequest request) {
        MemberAddress address = requireAddress(id);
        memberPortalService.saveAddress(new MemberAddressRequest(address.getId(), address.getMemberId(),
                request.receiverName(), request.receiverPhone(), request.province(), request.city(),
                request.district(), request.detailAddress(), request.addressTag(), request.isDefault()));
    }

    /**
     * 删除会员收货地址。
     *
     * @param id 地址ID
     * @author Henfon
     * @date 2026-09-22
     */
    @Transactional
    public void deleteAddress(Long id) {
        MemberAddress address = requireAddress(id);
        memberPortalService.deleteAddress(address.getMemberId(), address.getId());
    }

    /**
     * 把某条地址设为该会员的默认地址。
     *
     * @param id 地址ID
     * @author Henfon
     * @date 2026-09-22
     */
    @Transactional
    public void setDefaultAddress(Long id) {
        MemberAddress address = requireAddress(id);
        memberPortalService.setDefaultAddress(address.getMemberId(), address.getId());
    }

    /**
     * 读取地址并在不存在时报错。
     *
     * @param id 地址ID
     * @return 地址记录
     * @author Henfon
     * @date 2026-09-22
     */
    private MemberAddress requireAddress(Long id) {
        MemberAddress address = memberAddressMapper.selectById(id);
        if (address == null) {
            throw new BusinessException("MEMBER_ADDRESS_NOT_FOUND", "收货地址不存在或已删除");
        }
        return address;
    }

    /**
     * 把地址记录补上所属会员的展示信息。
     *
     * @param address 地址记录
     * @param members 本次分页涉及的会员
     * @return 后台地址视图
     * @author Henfon
     * @date 2026-09-22
     */
    private MemberAddressAdminView toAddressView(MemberAddress address, Map<Long, MemberUser> members) {
        MemberUser member = members.get(address.getMemberId());
        String name = member == null ? null
                : StringUtils.hasText(member.getNickname()) ? member.getNickname() : member.getUsername();
        return new MemberAddressAdminView(address.getId(), address.getMemberId(),
                member == null ? null : member.getMemberNo(), name,
                member == null ? null : member.getPhone(),
                address.getReceiverName(), address.getReceiverPhone(), address.getProvince(),
                address.getCity(), address.getDistrict(), address.getDetailAddress(),
                address.getAddressTag(), address.getIsDefault(), address.getCreatedAt(), address.getUpdatedAt());
    }

    /**
     * 创建后台录入的会员档案。
     *
     * @param request 新会员资料
     * @return 创建后的会员
     * @author Henfon
     * @date 2026-09-01
     */
    @Transactional
    public MemberUser create(MemberAdminCreateRequest request) {
        String nickname = request.nickname().trim();
        String phone = trimToNull(request.phone());
        String email = trimToNull(request.email());
        String requestedUsername = trimToNull(request.username());
        // 线下录入会员不强制设置登录密码；用户名优先使用手机号，避免生成的档案无法检索。
        String username = requestedUsername != null ? requestedUsername : (phone != null ? phone : "member_" + IdWorker.getIdStr());
        long duplicateCount = memberUserMapper.selectCount(new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getTenantId, 0L)
                .and(query -> query.eq(MemberUser::getUsername, username)
                        .or(phone != null, q -> q.eq(MemberUser::getPhone, phone))
                        .or(email != null, q -> q.eq(MemberUser::getEmail, email))));
        if (duplicateCount > 0) {
            throw new BusinessException("MEMBER_EXISTS", "用户名、手机号或邮箱已被其他会员使用");
        }
        Integer status = request.status() == null ? 1 : request.status();
        if (status != 0 && status != 1) {
            throw new BusinessException("MEMBER_STATUS_INVALID", "会员状态只能是正常或冻结");
        }
        MemberUser member = new MemberUser();
        member.setTenantId(0L);
        member.setMemberNo("M" + IdWorker.getIdStr());
        member.setUsername(username);
        member.setNickname(nickname);
        member.setPhone(phone);
        member.setEmail(email);
        member.setMemberLevel(StringUtils.hasText(request.memberLevel()) ? request.memberLevel().trim().toUpperCase() : "REGULAR");
        member.setStatus(status);
        member.setAvatarUrl(imageReferenceResolver.normalizeReference(trimToNull(request.avatarUrl())));
        member.setRemark(trimToNull(request.remark()));
        member.setPoints(0L);
        member.setBalance(BigDecimal.ZERO);
        member.setRegisteredAt(LocalDateTime.now());
        try {
            memberUserMapper.insert(member);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("MEMBER_EXISTS", "用户名、手机号或邮箱已被其他会员使用");
        }
        // 返回与列表查询一致的消费统计和标签字段，前端无需额外刷新即可展示。
        return enrichMember(member);
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
        return enrichMember(member);
    }

    /**
     * 分页查询会员资产审计流水。
     *
     * @param memberId 会员ID
     * @param current 当前页
     * @param size 页大小
     * @return 资产审计流水分页数据
     * @author Henfon
     * @date 2026-09-04
     */
    public IPage<MemberAssetAudit> pageAssetAudits(Long memberId, long current, long size) {
        // 先校验会员归属，再读取流水，避免通过会员ID越权查看其他账户资产。
        requireMember(memberId);
        return memberAssetAuditMapper.selectPage(new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 200)),
                new LambdaQueryWrapper<MemberAssetAudit>()
                        .eq(MemberAssetAudit::getMemberId, memberId)
                        .orderByDesc(MemberAssetAudit::getCreatedAt)
                        .orderByDesc(MemberAssetAudit::getId));
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
        // 会员资产更新成功后记录不可变审计流水，便于核对每次余额和积分变动。
        MemberAssetAudit audit = new MemberAssetAudit();
        audit.setMemberId(member.getId());
        audit.setPointsDelta(request.pointsDelta());
        audit.setPointsBefore(currentPoints);
        audit.setPointsAfter(nextPoints);
        audit.setBalanceDelta(request.balanceDelta().setScale(2, RoundingMode.HALF_UP));
        audit.setBalanceBefore(currentBalance);
        audit.setBalanceAfter(nextBalance);
        audit.setOperation("ADMIN_ADJUST");
        audit.setRemark(StringUtils.hasText(request.remark()) ? request.remark().trim() : null);
        memberAssetAuditMapper.insert(audit);
        return enrichMember(member);
    }

    /**
     * 查询启用的会员标签字典。
     *
     * <p>后台画像筛选栏需要看到完整标签体系，因此这里不做「只返回被引用过的标签」的过滤，
     * 未绑定会员的标签同样返回并给出人数 0，方便运营识别尚未启用的画像维度。</p>
     *
     * @return 标签列表，含每个标签当前绑定的会员人数
     * @author Henfon
     * @date 2026-08-30
     */
    public List<MemberTag> listTags() {
        // 标签只返回默认租户下的启用数据，避免后台误绑定已停用标签。
        List<MemberTag> tags = memberTagMapper.selectList(new LambdaQueryWrapper<MemberTag>()
                .eq(MemberTag::getTenantId, 0L)
                .eq(MemberTag::getStatus, 1)
                .orderByAsc(MemberTag::getSortNo)
                .orderByAsc(MemberTag::getId));
        if (tags.isEmpty()) {
            return tags;
        }
        // 关联表逻辑删除由 MyBatis-Plus 自动过滤，标签数通常只有几十个，一次 IN 查询即可完成统计。
        List<Long> tagIds = tags.stream().map(MemberTag::getId).collect(Collectors.toList());
        Map<Long, Long> usage = memberUserTagMapper.selectList(new LambdaQueryWrapper<MemberUserTag>()
                        .in(MemberUserTag::getTagId, tagIds)).stream()
                .collect(Collectors.groupingBy(MemberUserTag::getTagId, Collectors.counting()));
        tags.forEach(tag -> tag.setMemberCount(usage.getOrDefault(tag.getId(), 0L).intValue()));
        return tags;
    }

    /**
     * 新增或修改会员标签。
     *
     * @param id 标签ID，新增时为空
     * @param request 标签请求
     * @return 保存后的标签
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public MemberTag saveTag(Long id, MemberTagSaveRequest request) {
        String tagName = request.tagName().trim();
        MemberTag tag = id == null ? new MemberTag() : memberTagMapper.selectOne(new LambdaQueryWrapper<MemberTag>()
                .eq(MemberTag::getId, id).eq(MemberTag::getTenantId, 0L));
        if (tag == null) {
            throw new BusinessException("MEMBER_TAG_NOT_FOUND", "会员标签不存在");
        }
        tag.setTenantId(0L);
        tag.setTagName(tagName);
        tag.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        tag.setStatus(request.status() == null ? 1 : request.status());
        if (tag.getStatus() != 0 && tag.getStatus() != 1) {
            throw new BusinessException("MEMBER_TAG_STATUS_INVALID", "标签状态只能是启用或停用");
        }
        try {
            if (tag.getId() == null) {
                memberTagMapper.insert(tag);
            } else if (memberTagMapper.updateById(tag) == 0) {
                throw new BusinessException("MEMBER_CONCURRENT_UPDATE", "会员标签已被其他操作修改，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("MEMBER_TAG_EXISTS", "会员标签名称已存在");
        }
        return tag;
    }

    /**
     * 删除会员标签。
     *
     * @param id 标签ID
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void deleteTag(Long id) {
        MemberTag tag = memberTagMapper.selectOne(new LambdaQueryWrapper<MemberTag>()
                .eq(MemberTag::getId, id).eq(MemberTag::getTenantId, 0L));
        if (tag == null) {
            throw new BusinessException("MEMBER_TAG_NOT_FOUND", "会员标签不存在");
        }
        // 先逻辑删除关联，避免历史关联在恢复标签后产生脏数据。
        List<MemberUserTag> relations = memberUserTagMapper.selectList(new LambdaQueryWrapper<MemberUserTag>()
                .eq(MemberUserTag::getTagId, id));
        relations.forEach(memberUserTagMapper::deleteById);
        memberTagMapper.deleteById(id);
    }

    /**
     * 覆盖会员标签绑定关系。
     *
     * @param memberId 会员ID
     * @param request 标签名称列表
     * @return 更新后的会员
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public MemberUser updateTags(Long memberId, MemberUserTagsRequest request) {
        MemberUser member = requireMember(memberId);
        LinkedHashSet<String> names = request.tags().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<MemberUserTag> existing = memberUserTagMapper.selectList(new LambdaQueryWrapper<MemberUserTag>()
                .eq(MemberUserTag::getMemberId, memberId));
        Map<Long, MemberUserTag> existingByTag = existing.stream()
                .collect(Collectors.toMap(MemberUserTag::getTagId, Function.identity(), (left, right) -> left));
        List<Long> targetTagIds = new ArrayList<>();
        for (String name : names) {
            MemberTag tag = memberTagMapper.selectOne(new LambdaQueryWrapper<MemberTag>()
                    .eq(MemberTag::getTenantId, 0L).eq(MemberTag::getTagName, name));
            if (tag == null) {
                tag = new MemberTag();
                tag.setTenantId(0L);
                tag.setTagName(name);
                tag.setSortNo(0);
                tag.setStatus(1);
                try {
                    memberTagMapper.insert(tag);
                } catch (DuplicateKeyException exception) {
                    tag = memberTagMapper.selectOne(new LambdaQueryWrapper<MemberTag>()
                            .eq(MemberTag::getTenantId, 0L).eq(MemberTag::getTagName, name));
                }
            }
            if (tag == null) {
                throw new BusinessException("MEMBER_TAG_SAVE_FAILED", "会员标签保存失败");
            }
            targetTagIds.add(tag.getId());
            if (!existingByTag.containsKey(tag.getId())) {
                MemberUserTag relation = new MemberUserTag();
                relation.setMemberId(memberId);
                relation.setTagId(tag.getId());
                memberUserTagMapper.insert(relation);
            }
        }
        existing.stream().filter(item -> !targetTagIds.contains(item.getTagId())).forEach(memberUserTagMapper::deleteById);
        return enrichMember(member);
    }

    /**
     * 记录会员支付成功后的消费统计。
     *
     * @param memberId 会员ID
     * @param amount 实付金额
     * @param paidAt 支付时间
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void recordPaid(Long memberId, BigDecimal amount, LocalDateTime paidAt) {
        if (memberId == null) {
            return;
        }
        MemberConsumptionStat stat = memberConsumptionStatMapper.selectOne(new LambdaQueryWrapper<MemberConsumptionStat>()
                .eq(MemberConsumptionStat::getMemberId, memberId));
        BigDecimal normalizedAmount = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        if (stat == null) {
            stat = new MemberConsumptionStat();
            stat.setMemberId(memberId);
            stat.setPaidOrderCount(1L);
            stat.setPaidAmount(normalizedAmount);
            stat.setLastOrderAt(paidAt == null ? LocalDateTime.now() : paidAt);
            try {
                memberConsumptionStatMapper.insert(stat);
                return;
            } catch (DuplicateKeyException exception) {
                stat = memberConsumptionStatMapper.selectOne(new LambdaQueryWrapper<MemberConsumptionStat>()
                        .eq(MemberConsumptionStat::getMemberId, memberId));
            }
        }
        if (stat == null) {
            throw new BusinessException("MEMBER_STAT_SAVE_FAILED", "会员消费统计保存失败");
        }
        stat.setPaidOrderCount((stat.getPaidOrderCount() == null ? 0L : stat.getPaidOrderCount()) + 1L);
        stat.setPaidAmount((stat.getPaidAmount() == null ? BigDecimal.ZERO : stat.getPaidAmount()).add(normalizedAmount));
        if (stat.getLastOrderAt() == null || (paidAt != null && paidAt.isAfter(stat.getLastOrderAt()))) {
            stat.setLastOrderAt(paidAt);
        }
        if (memberConsumptionStatMapper.updateById(stat) == 0) {
            throw new BusinessException("MEMBER_STAT_CONCURRENT_UPDATE", "会员消费统计已被其他操作修改，请重试");
        }
    }

    /**
     * 记录退款成功后的消费统计回滚。
     *
     * @param memberId 会员ID
     * @param refundAmount 退款金额
     * @param paidAmount 原支付金额
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void recordRefunded(Long memberId, BigDecimal refundAmount, BigDecimal paidAmount) {
        if (memberId == null) {
            return;
        }
        MemberConsumptionStat stat = memberConsumptionStatMapper.selectOne(new LambdaQueryWrapper<MemberConsumptionStat>()
                .eq(MemberConsumptionStat::getMemberId, memberId));
        if (stat == null) {
            return;
        }
        BigDecimal refund = refundAmount == null ? BigDecimal.ZERO : refundAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal current = stat.getPaidAmount() == null ? BigDecimal.ZERO : stat.getPaidAmount();
        stat.setPaidAmount(current.subtract(refund).max(BigDecimal.ZERO));
        if (paidAmount != null && refund.compareTo(paidAmount) >= 0) {
            stat.setPaidOrderCount(Math.max(0L, (stat.getPaidOrderCount() == null ? 0L : stat.getPaidOrderCount()) - 1L));
        }
        if (memberConsumptionStatMapper.updateById(stat) == 0) {
            throw new BusinessException("MEMBER_STAT_CONCURRENT_UPDATE", "会员消费统计已被其他操作修改，请重试");
        }
    }

    /**
     * 给会员补充标签和消费统计字段。
     *
     * @param member 会员实体
     * @return 补充后的会员实体
     * @author Henfon
     * @date 2026-08-30
     */
    private MemberUser enrichMember(MemberUser member) {
        if (member == null) {
            return null;
        }
        // 所有后台读会员的出口都汇集到这里，头像在返回前统一重签，避免过期地址变成裂图。
        member.setAvatarUrl(imageReferenceResolver.accessUrl(member.getAvatarUrl()));
        List<MemberUserTag> relations = memberUserTagMapper.selectList(new LambdaQueryWrapper<MemberUserTag>()
                .eq(MemberUserTag::getMemberId, member.getId()));
        if (relations.isEmpty()) {
            member.setTagsCsv("");
        } else {
            List<Long> tagIds = relations.stream().map(MemberUserTag::getTagId).toList();
            List<MemberTag> tags = memberTagMapper.selectList(new LambdaQueryWrapper<MemberTag>()
                    .in(MemberTag::getId, tagIds).eq(MemberTag::getTenantId, 0L));
            member.setTagsCsv(tags.stream().map(MemberTag::getTagName).collect(Collectors.joining(",")));
        }
        MemberConsumptionStat stat = memberConsumptionStatMapper.selectOne(new LambdaQueryWrapper<MemberConsumptionStat>()
                .eq(MemberConsumptionStat::getMemberId, member.getId()));
        member.setTotalSpent(stat == null || stat.getPaidAmount() == null ? BigDecimal.ZERO : stat.getPaidAmount());
        member.setOrderCount(stat == null || stat.getPaidOrderCount() == null ? 0L : stat.getPaidOrderCount());
        member.setLastOrderAt(stat == null ? null : stat.getLastOrderAt());
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
