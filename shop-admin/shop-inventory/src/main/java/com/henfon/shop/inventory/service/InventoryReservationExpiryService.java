package com.henfon.shop.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.inventory.entity.InventoryStock;
import com.henfon.shop.inventory.entity.InventoryStockLock;
import com.henfon.shop.inventory.entity.InventoryStockLog;
import com.henfon.shop.inventory.mapper.InventoryStockLockMapper;
import com.henfon.shop.inventory.mapper.InventoryStockLogMapper;
import com.henfon.shop.inventory.mapper.InventoryStockMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存预占过期补偿服务。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class InventoryReservationExpiryService {
    private static final int LOCKED = 0;

    private final InventoryStockLockMapper lockMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryStockLogMapper logMapper;

    /**
     * 创建库存预占过期补偿服务。
     *
     * @param lockMapper 锁定流水数据访问对象
     * @param stockMapper 库存台账数据访问对象
     * @param logMapper 库存流水数据访问对象
     * @author Henfon
     * @date 2026-08-30
     */
    public InventoryReservationExpiryService(InventoryStockLockMapper lockMapper,
                                             InventoryStockMapper stockMapper,
                                             InventoryStockLogMapper logMapper) {
        this.lockMapper = lockMapper;
        this.stockMapper = stockMapper;
        this.logMapper = logMapper;
    }

    /**
     * 扫描并补偿已过期的库存预占。
     *
     * @param limit 本次最多处理数量
     * @return 实际释放数量
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public int compensate(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        List<InventoryStockLock> locks = lockMapper.selectList(new LambdaQueryWrapper<InventoryStockLock>()
                .eq(InventoryStockLock::getStatus, LOCKED)
                .eq(InventoryStockLock::getIsDeleted, 0)
                .isNotNull(InventoryStockLock::getExpireAt)
                .le(InventoryStockLock::getExpireAt, LocalDateTime.now())
                .orderByAsc(InventoryStockLock::getExpireAt)
                .last("LIMIT " + safeLimit));
        int released = 0;
        for (InventoryStockLock lock : locks) {
            if (releaseOne(lock.getId())) {
                released++;
            }
        }
        return released;
    }

    /**
     * 原子释放单条过期锁定流水及对应库存。
     *
     * @param lockId 锁定流水ID
     * @return 是否成功释放
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public boolean releaseOne(Long lockId) {
        InventoryStockLock lock = lockMapper.selectById(lockId);
        if (lock == null || !Integer.valueOf(LOCKED).equals(lock.getStatus())) {
            return false;
        }
        InventoryStock stock = stockMapper.selectById(lock.getStockId());
        if (stock == null) {
            return false;
        }
        // 先原子抢占释放权，再回补库存；重复任务会因状态不再是锁定而安全跳过。
        if (lockMapper.markReleasedIfLocked(lockId, LocalDateTime.now()) == 0) {
            return false;
        }
        int beforeAvailable = stock.getAvailableStock();
        int beforeLocked = stock.getLockedStock();
        if (stockMapper.release(stock.getId(), lock.getQuantity()) == 0) {
            throw new IllegalStateException("库存锁定过期释放失败，库存锁定数量不足");
        }
        InventoryStock after = stockMapper.selectById(stock.getId());
        InventoryStockLog log = new InventoryStockLog();
        log.setStockId(stock.getId());
        log.setSkuId(stock.getSkuId());
        log.setBizType("EXPIRE_RELEASE");
        log.setBizNo(lock.getOrderNo());
        log.setChangeQuantity(lock.getQuantity());
        log.setBeforeAvailable(beforeAvailable);
        log.setAfterAvailable(after.getAvailableStock());
        log.setBeforeLocked(beforeLocked);
        log.setAfterLocked(after.getLockedStock());
        log.setRemark("订单库存预占过期自动补偿释放");
        logMapper.insert(log);
        return true;
    }
}
