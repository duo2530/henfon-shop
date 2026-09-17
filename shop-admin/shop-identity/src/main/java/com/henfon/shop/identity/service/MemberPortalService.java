package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.dto.MemberAddressRequest;
import com.henfon.shop.identity.dto.MemberProfileUpdateRequest;
import com.henfon.shop.identity.dto.MemberCompareHistoryResponse;
import com.henfon.shop.identity.entity.MemberAddress;
import com.henfon.shop.identity.entity.MemberCompareHistory;
import com.henfon.shop.identity.entity.MemberCompareItem;
import com.henfon.shop.identity.entity.MemberFavorite;
import com.henfon.shop.identity.mapper.MemberAddressMapper;
import com.henfon.shop.identity.mapper.MemberCompareHistoryMapper;
import com.henfon.shop.identity.mapper.MemberCompareItemMapper;
import com.henfon.shop.identity.mapper.MemberFavoriteMapper;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.integration.storage.ImageReferenceResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门户会员能力服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class MemberPortalService {
    private final MemberAddressMapper addressMapper;
    private final MemberFavoriteMapper favoriteMapper;
    private final MemberCompareHistoryMapper historyMapper;
    private final MemberCompareItemMapper itemMapper;
    private final MemberUserMapper userMapper;
    private final ImageReferenceResolver imageReferenceResolver;

    /**
     * 创建门户会员服务。
     *
     * @param addressMapper 地址数据访问对象
     * @param favoriteMapper 收藏数据访问对象
     * @param historyMapper 对比历史数据访问对象
     * @param itemMapper 对比明细数据访问对象
     * @param userMapper 会员数据访问对象
     * @param imageReferenceResolver 媒体引用解析器
     * @author Henfon
     * @date 2026-08-29
     */
    public MemberPortalService(MemberAddressMapper addressMapper, MemberFavoriteMapper favoriteMapper,
                               MemberCompareHistoryMapper historyMapper, MemberCompareItemMapper itemMapper,
                               MemberUserMapper userMapper, ImageReferenceResolver imageReferenceResolver) {
        this.addressMapper = addressMapper;
        this.favoriteMapper = favoriteMapper;
        this.historyMapper = historyMapper;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
        this.imageReferenceResolver = imageReferenceResolver;
    }

    /**
     * 查询会员资料。
     *
     * @param memberId 会员ID
     * @return 会员资料，不存在时返回 null
     * @author Henfon
     * @date 2026-08-29
     */
    public MemberUser profile(Long memberId) {
        MemberUser member = userMapper.selectOne(new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getId, memberId).eq(MemberUser::getStatus, 1));
        if (member != null) {
            member.setAvatarUrl(imageReferenceResolver.accessUrl(member.getAvatarUrl()));
        }
        return member;
    }

    /**
     * 更新当前会员公开资料。
     *
     * @param memberId 会员ID
     * @param request 资料更新请求
     * @return 更新后的会员资料
     * @author Henfon
     * @date 2026-09-04
     */
    @Transactional
    public MemberUser updateProfile(Long memberId, MemberProfileUpdateRequest request) {
        MemberUser member = userMapper.selectOne(new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getId, memberId).eq(MemberUser::getStatus, 1));
        if (member == null) {
            throw new BusinessException("MEMBER_NOT_FOUND", "会员不存在或已被冻结");
        }
        // 仅允许修改公开资料，账号、等级、积分和余额由服务端维护。
        if (request.nickname() != null && !request.nickname().isBlank()) {
            member.setNickname(request.nickname().trim());
        }
        if (request.phone() != null) member.setPhone(trimToNull(request.phone()));
        if (request.email() != null) member.setEmail(trimToNull(request.email()));
        // null 表示不改动；空串表示清除头像，两者都不能被归一化逻辑合并。
        if (request.avatarUrl() != null) {
            member.setAvatarUrl(imageReferenceResolver.normalizeReference(trimToNull(request.avatarUrl())));
        }
        member.setUpdatedAt(LocalDateTime.now());
        try {
            if (userMapper.updateById(member) == 0) {
                throw new BusinessException("MEMBER_PROFILE_CONCURRENT_UPDATE", "会员资料已被其他操作修改，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            // 手机号和邮箱由数据库唯一索引兜底，转换为稳定业务错误码供门户展示。
            throw new BusinessException("MEMBER_CONTACT_EXISTS", "手机号或邮箱已被其他会员使用");
        }
        member.setAvatarUrl(imageReferenceResolver.accessUrl(member.getAvatarUrl()));
        return member;
    }

    /**
     * 查询会员地址。
     *
     * @param memberId 会员ID
     * @return 地址列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<MemberAddress> addresses(Long memberId) {
        return addressMapper.selectList(new LambdaQueryWrapper<MemberAddress>()
                .eq(MemberAddress::getMemberId, memberId).orderByDesc(MemberAddress::getIsDefault)
                .orderByDesc(MemberAddress::getUpdatedAt));
    }

    /**
     * 保存会员地址。
     *
     * @param request 地址保存请求
     * @return 地址ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public Long saveAddress(MemberAddressRequest request) {
        MemberAddress address = new MemberAddress();
        boolean creating = request.id() == null;
        if (request.id() != null) {
            // 编辑时校验地址归属，避免客户端借助 memberId 修改其他会员地址。
            address = requireAddress(request.memberId(), request.id());
        }
        address.setMemberId(request.memberId());
        address.setReceiverName(request.receiverName());
        address.setReceiverPhone(request.receiverPhone());
        address.setProvince(request.province());
        address.setCity(request.city());
        address.setDistrict(request.district());
        address.setDetailAddress(request.detailAddress());
        address.setAddressTag(request.addressTag());
        address.setIsDefault(request.isDefault() == null ? 0 : request.isDefault());
        // 首个地址必须成为默认地址，避免会员存在地址但结算时没有可选默认地址。
        if (creating && addressMapper.selectCount(new LambdaQueryWrapper<MemberAddress>()
                .eq(MemberAddress::getMemberId, request.memberId())) == 0) {
            address.setIsDefault(1);
        }
        // 编辑默认地址时不允许把唯一默认地址取消，保证默认地址约束始终成立。
        if (!creating && address.getIsDefault() == 0 && request.id().equals(findDefaultAddressId(request.memberId()))) {
            address.setIsDefault(1);
        }
        if (address.getIsDefault() == 1) {
            addressMapper.update(null, new UpdateWrapper<MemberAddress>()
                    .eq("member_id", request.memberId()).set("is_default", 0));
        }
        if (request.id() == null) {
            addressMapper.insert(address);
        } else {
            if (addressMapper.updateById(address) == 0) {
                throw new BusinessException("MEMBER_ADDRESS_CONCURRENT_UPDATE", "地址已被其他操作修改，请刷新后重试");
            }
        }
        return address.getId();
    }

    /**
     * 更新会员地址。
     *
     * @param addressId 地址ID
     * @param request 地址内容
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void updateAddress(Long addressId, MemberAddressRequest request) {
        // 路径ID优先于请求体ID，确保更新目标不可被客户端混淆。
        MemberAddressRequest normalized = new MemberAddressRequest(addressId, request.memberId(), request.receiverName(),
                request.receiverPhone(), request.province(), request.city(), request.district(),
                request.detailAddress(), request.addressTag(), request.isDefault());
        saveAddress(normalized);
    }

    /**
     * 删除会员地址。
     *
     * @param memberId 会员ID
     * @param addressId 地址ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void deleteAddress(Long memberId, Long addressId) {
        MemberAddress address = requireAddress(memberId, addressId);
        // 使用逻辑删除，保留历史订单地址快照所需的审计信息。
        if (addressMapper.deleteById(address.getId()) == 0) {
            throw new BusinessException("MEMBER_ADDRESS_DELETE_FAILED", "地址删除失败，请刷新后重试");
        }
        // 删除默认地址后自动提升最近更新的地址，避免剩余地址全部变成非默认。
        if (address.getIsDefault() == 1) {
            promoteLatestAddress(memberId);
        }
    }

    /**
     * 设置会员默认地址。
     *
     * @param memberId 会员ID
     * @param addressId 地址ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void setDefaultAddress(Long memberId, Long addressId) {
        MemberAddress address = requireAddress(memberId, addressId);
        addressMapper.update(null, new UpdateWrapper<MemberAddress>()
                .eq("member_id", memberId).set("is_default", 0));
        address.setIsDefault(1);
        if (addressMapper.updateById(address) == 0) {
            throw new BusinessException("MEMBER_ADDRESS_CONCURRENT_UPDATE", "地址已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 查询并校验会员地址归属。
     *
     * @param memberId 会员ID
     * @param addressId 地址ID
     * @return 地址实体
     * @author Henfon
     * @date 2026-08-29
     */
    private MemberAddress requireAddress(Long memberId, Long addressId) {
        MemberAddress address = addressMapper.selectOne(new LambdaQueryWrapper<MemberAddress>()
                .eq(MemberAddress::getId, addressId)
                .eq(MemberAddress::getMemberId, memberId));
        if (address == null) {
            throw new BusinessException("MEMBER_ADDRESS_NOT_FOUND", "地址不存在或不属于当前会员");
        }
        return address;
    }

    /**
     * 查询会员当前默认地址ID。
     *
     * @param memberId 会员ID
     * @return 默认地址ID，不存在时返回null
     * @author Henfon
     * @date 2026-09-04
     */
    private Long findDefaultAddressId(Long memberId) {
        MemberAddress defaultAddress = addressMapper.selectOne(new LambdaQueryWrapper<MemberAddress>()
                .eq(MemberAddress::getMemberId, memberId)
                .eq(MemberAddress::getIsDefault, 1)
                .last("LIMIT 1"));
        return defaultAddress == null ? null : defaultAddress.getId();
    }

    /**
     * 将会员最近更新的有效地址设置为默认地址。
     *
     * @param memberId 会员ID
     * @author Henfon
     * @date 2026-09-04
     */
    private void promoteLatestAddress(Long memberId) {
        MemberAddress candidate = addressMapper.selectOne(new LambdaQueryWrapper<MemberAddress>()
                .eq(MemberAddress::getMemberId, memberId)
                .eq(MemberAddress::getIsDefault, 0)
                .orderByDesc(MemberAddress::getUpdatedAt)
                .orderByDesc(MemberAddress::getId)
                .last("LIMIT 1"));
        if (candidate == null) {
            return;
        }
        addressMapper.update(null, new UpdateWrapper<MemberAddress>()
                .eq("member_id", memberId).set("is_default", 0));
        candidate.setIsDefault(1);
        if (addressMapper.updateById(candidate) == 0) {
            throw new BusinessException("MEMBER_ADDRESS_CONCURRENT_UPDATE", "默认地址已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 清理会员资料中的可选文本字段。
     *
     * @param value 原始文本
     * @return 去除首尾空白后的文本，空白内容返回 null
     * @author Henfon
     * @date 2026-09-04
     */
    private String trimToNull(String value) {
        // 空白手机号、邮箱和头像统一转换为空值，避免数据库保存无意义字符串。
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 查询会员收藏商品。
     *
     * @param memberId 会员ID
     * @return 收藏列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<MemberFavorite> favorites(Long memberId) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<MemberFavorite>()
                .eq(MemberFavorite::getMemberId, memberId).orderByDesc(MemberFavorite::getCreatedAt));
    }

    /**
     * 切换商品收藏状态。
     *
     * @param memberId 会员ID
     * @param productId 商品ID
     * @return 是否已收藏
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public boolean toggleFavorite(Long memberId, Long productId) {
        MemberFavorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<MemberFavorite>()
                .eq(MemberFavorite::getMemberId, memberId).eq(MemberFavorite::getProductId, productId));
        if (existing == null) {
            MemberFavorite favorite = new MemberFavorite();
            favorite.setMemberId(memberId);
            favorite.setProductId(productId);
            favoriteMapper.insert(favorite);
            return true;
        }
        favoriteMapper.deleteById(existing.getId());
        return false;
    }

    /**
     * 保存一次商品对比历史。
     *
     * @param memberId 会员ID
     * @param productIds 商品ID列表
     * @return 历史记录ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public Long saveCompare(Long memberId, List<Long> productIds) {
        MemberCompareHistory history = new MemberCompareHistory();
        history.setMemberId(memberId);
        history.setComparedAt(LocalDateTime.now());
        historyMapper.insert(history);
        for (int i = 0; i < productIds.size(); i++) {
            MemberCompareItem item = new MemberCompareItem();
            item.setHistoryId(history.getId());
            item.setProductId(productIds.get(i));
            item.setSortNo(i);
            itemMapper.insert(item);
        }
        return history.getId();
    }

    /**
     * 查询会员最近对比历史。
     *
     * @param memberId 会员ID
     * @param limit 返回条数
     * @return 对比历史列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<MemberCompareHistoryResponse> compareHistory(Long memberId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<MemberCompareHistory> histories = historyMapper.selectList(new LambdaQueryWrapper<MemberCompareHistory>()
                .eq(MemberCompareHistory::getMemberId, memberId).orderByDesc(MemberCompareHistory::getComparedAt)
                .last("LIMIT " + safeLimit));
        // 一次性按历史记录查询明细，确保门户恢复时保留用户原始排序。
        return histories.stream().map(history -> new MemberCompareHistoryResponse(
                history.getId(), history.getComparedAt(), itemMapper.selectList(new LambdaQueryWrapper<MemberCompareItem>()
                        .eq(MemberCompareItem::getHistoryId, history.getId())
                        .orderByAsc(MemberCompareItem::getSortNo))
                        .stream().map(MemberCompareItem::getProductId).toList())).toList();
    }
}
