package com.henfon.shop.identity.service;

import com.henfon.shop.identity.dto.MemberAddressRequest;
import com.henfon.shop.identity.entity.MemberAddress;
import com.henfon.shop.identity.mapper.MemberAddressMapper;
import com.henfon.shop.identity.mapper.MemberCompareHistoryMapper;
import com.henfon.shop.identity.mapper.MemberCompareItemMapper;
import com.henfon.shop.identity.mapper.MemberFavoriteMapper;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.integration.storage.ImageReferenceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门户会员地址默认值闭环测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@ExtendWith(MockitoExtension.class)
class MemberPortalServiceAddressTest {

    @Mock
    private MemberAddressMapper addressMapper;
    @Mock
    private MemberFavoriteMapper favoriteMapper;
    @Mock
    private MemberCompareHistoryMapper historyMapper;
    @Mock
    private MemberCompareItemMapper itemMapper;
    @Mock
    private MemberUserMapper userMapper;
    @Mock
    private ImageReferenceResolver imageReferenceResolver;

    private MemberPortalService service;

    /**
     * 初始化会员门户服务测试对象。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @BeforeEach
    void setUp() {
        service = new MemberPortalService(addressMapper, favoriteMapper, historyMapper, itemMapper, userMapper,
                imageReferenceResolver);
    }

    /**
     * 验证会员首个地址会自动标记为默认地址。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldPromoteFirstAddressToDefault() {
        when(addressMapper.selectCount(any())).thenReturn(0L);
        when(addressMapper.insert(any(MemberAddress.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, MemberAddress.class).setId(11L);
            return 1;
        });

        Long addressId = service.saveAddress(request(null, 7L, 0));

        ArgumentCaptor<MemberAddress> captor = ArgumentCaptor.forClass(MemberAddress.class);
        verify(addressMapper).insert(captor.capture());
        assertEquals(11L, addressId);
        assertEquals(1, captor.getValue().getIsDefault());
    }

    /**
     * 验证删除默认地址后会自动提升最近地址。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldPromoteLatestAddressAfterDeletingDefault() {
        MemberAddress current = address(11L, 1);
        MemberAddress candidate = address(12L, 0);
        when(addressMapper.selectOne(any())).thenReturn(current, candidate);
        when(addressMapper.deleteById(11L)).thenReturn(1);
        when(addressMapper.updateById(candidate)).thenReturn(1);

        service.deleteAddress(7L, 11L);

        assertEquals(1, candidate.getIsDefault());
        verify(addressMapper).updateById(candidate);
    }

    /**
     * 构造地址保存请求。
     *
     * @param id 地址ID
     * @param memberId 会员ID
     * @param isDefault 默认标记
     * @return 地址请求
     * @author Henfon
     * @date 2026-09-04
     */
    private MemberAddressRequest request(Long id, Long memberId, Integer isDefault) {
        return new MemberAddressRequest(id, memberId, "张三", "13800138000", "上海市", "上海市",
                "浦东新区", "科技大道1号", "家", isDefault);
    }

    /**
     * 构造会员地址实体。
     *
     * @param id 地址ID
     * @param isDefault 默认标记
     * @return 地址实体
     * @author Henfon
     * @date 2026-09-04
     */
    private MemberAddress address(Long id, Integer isDefault) {
        MemberAddress address = new MemberAddress();
        address.setId(id);
        address.setMemberId(7L);
        address.setIsDefault(isDefault);
        return address;
    }
}
