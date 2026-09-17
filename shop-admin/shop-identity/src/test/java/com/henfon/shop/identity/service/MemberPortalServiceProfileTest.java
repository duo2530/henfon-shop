package com.henfon.shop.identity.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.dto.MemberProfileUpdateRequest;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberAddressMapper;
import com.henfon.shop.identity.mapper.MemberCompareHistoryMapper;
import com.henfon.shop.identity.mapper.MemberCompareItemMapper;
import com.henfon.shop.identity.mapper.MemberFavoriteMapper;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.integration.storage.ImageReferenceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门户会员资料更新闭环测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@ExtendWith(MockitoExtension.class)
class MemberPortalServiceProfileTest {

    @Mock private MemberAddressMapper addressMapper;
    @Mock private MemberFavoriteMapper favoriteMapper;
    @Mock private MemberCompareHistoryMapper historyMapper;
    @Mock private MemberCompareItemMapper itemMapper;
    @Mock private MemberUserMapper userMapper;
    @Mock private ImageReferenceResolver imageReferenceResolver;

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
     * 验证资料更新会清理可选字段，并保留空白昵称原值。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldNormalizeProfileFieldsAndKeepNickname() {
        MemberUser member = member();
        when(userMapper.selectOne(any())).thenReturn(member);
        when(userMapper.updateById(any(MemberUser.class))).thenReturn(1);

        MemberUser result = service.updateProfile(7L,
                new MemberProfileUpdateRequest("   ", " 13800138000 ", " ", "  "));

        assertEquals("原昵称", result.getNickname());
        assertEquals("13800138000", result.getPhone());
        assertEquals(null, result.getEmail());
        assertEquals(null, result.getAvatarUrl());
        verify(userMapper).updateById(member);
    }

    /**
     * 验证联系方式唯一键冲突转换为稳定业务错误码。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldMapDuplicateContactToBusinessError() {
        when(userMapper.selectOne(any())).thenReturn(member());
        when(userMapper.updateById(any(MemberUser.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProfile(7L,
                        new MemberProfileUpdateRequest(null, "13800138001", null, null)));

        assertEquals("MEMBER_CONTACT_EXISTS", exception.getCode());
    }

    /**
     * 构造会员实体。
     *
     * @return 会员实体
     * @author Henfon
     * @date 2026-09-04
     */
    private MemberUser member() {
        MemberUser member = new MemberUser();
        member.setId(7L);
        member.setStatus(1);
        member.setNickname("原昵称");
        member.setPhone("13800000000");
        member.setEmail("old@example.com");
        member.setAvatarUrl("old-avatar");
        return member;
    }
}
