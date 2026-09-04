package com.henfon.shop.identity.service;

import com.henfon.shop.identity.dto.MemberAdminAdjustRequest;
import com.henfon.shop.identity.entity.MemberAssetAudit;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberAssetAuditMapper;
import com.henfon.shop.identity.mapper.MemberConsumptionStatMapper;
import com.henfon.shop.identity.mapper.MemberTagMapper;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.identity.mapper.MemberUserTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员资产调账审计流水测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@ExtendWith(MockitoExtension.class)
class MemberAdminServiceAssetAuditTest {

    @Mock private MemberUserMapper memberUserMapper;
    @Mock private MemberTagMapper memberTagMapper;
    @Mock private MemberUserTagMapper memberUserTagMapper;
    @Mock private MemberConsumptionStatMapper memberConsumptionStatMapper;
    @Mock private MemberAssetAuditMapper memberAssetAuditMapper;

    private MemberAdminService service;

    /**
     * 初始化会员管理服务。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @BeforeEach
    void setUp() {
        service = new MemberAdminService(memberUserMapper, memberTagMapper, memberUserTagMapper,
                memberConsumptionStatMapper, memberAssetAuditMapper);
    }

    /**
     * 验证调账成功后会记录资产变动前后快照。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRecordAuditAfterAdjust() {
        MemberUser member = new MemberUser();
        member.setId(9L);
        member.setTenantId(0L);
        member.setPoints(100L);
        member.setBalance(new BigDecimal("12.30"));
        when(memberUserMapper.selectOne(any())).thenReturn(member);
        when(memberUserMapper.updateById(any(MemberUser.class))).thenReturn(1);
        when(memberUserTagMapper.selectList(any())).thenReturn(java.util.List.of());
        when(memberConsumptionStatMapper.selectOne(any())).thenReturn(null);

        service.adjust(9L, new MemberAdminAdjustRequest(25L, new BigDecimal("3.456"), "活动补偿"));

        ArgumentCaptor<MemberAssetAudit> captor = ArgumentCaptor.forClass(MemberAssetAudit.class);
        verify(memberAssetAuditMapper).insert(captor.capture());
        MemberAssetAudit audit = captor.getValue();
        assertEquals(100L, audit.getPointsBefore());
        assertEquals(125L, audit.getPointsAfter());
        assertEquals(new BigDecimal("12.30"), audit.getBalanceBefore());
        assertEquals(new BigDecimal("15.76"), audit.getBalanceAfter());
        assertEquals("活动补偿", audit.getRemark());
    }
}
