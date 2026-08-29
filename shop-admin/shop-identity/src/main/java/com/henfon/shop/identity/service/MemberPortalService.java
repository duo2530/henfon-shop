package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.identity.dto.MemberAddressRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 创建门户会员服务。
     *
     * @param addressMapper 地址数据访问对象
     * @param favoriteMapper 收藏数据访问对象
     * @param historyMapper 对比历史数据访问对象
     * @param itemMapper 对比明细数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public MemberPortalService(MemberAddressMapper addressMapper, MemberFavoriteMapper favoriteMapper,
                               MemberCompareHistoryMapper historyMapper, MemberCompareItemMapper itemMapper,
                               MemberUserMapper userMapper) {
        this.addressMapper = addressMapper;
        this.favoriteMapper = favoriteMapper;
        this.historyMapper = historyMapper;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
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
        return userMapper.selectOne(new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getId, memberId).eq(MemberUser::getStatus, 1));
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
        address.setId(request.id());
        address.setMemberId(request.memberId());
        address.setReceiverName(request.receiverName());
        address.setReceiverPhone(request.receiverPhone());
        address.setProvince(request.province());
        address.setCity(request.city());
        address.setDistrict(request.district());
        address.setDetailAddress(request.detailAddress());
        address.setAddressTag(request.addressTag());
        address.setIsDefault(request.isDefault() == null ? 0 : request.isDefault());
        if (address.getIsDefault() == 1) {
            addressMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<MemberAddress>()
                    .eq("member_id", request.memberId()).set("is_default", 0));
        }
        if (address.getId() == null) {
            addressMapper.insert(address);
        } else {
            addressMapper.updateById(address);
        }
        return address.getId();
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
    public List<MemberCompareHistory> compareHistory(Long memberId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return historyMapper.selectList(new LambdaQueryWrapper<MemberCompareHistory>()
                .eq(MemberCompareHistory::getMemberId, memberId).orderByDesc(MemberCompareHistory::getComparedAt)
                .last("LIMIT " + safeLimit));
    }
}
