package com.henfon.shop.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.content.dto.ContentBannerSaveRequest;
import com.henfon.shop.content.entity.ContentBanner;
import com.henfon.shop.content.mapper.ContentBannerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Banner 后台管理应用服务，负责内容校验、定时发布和逻辑删除。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class ContentBannerAdminService {

    private static final int ENABLED = 1;
    private static final int DISABLED = 0;

    private final ContentBannerMapper bannerMapper;

    /**
     * 创建 Banner 后台管理服务。
     *
     * @param bannerMapper Banner 数据访问对象
     * @author Henfon
     * @date 2026-08-30
     */
    public ContentBannerAdminService(ContentBannerMapper bannerMapper) {
        this.bannerMapper = bannerMapper;
    }

    /**
     * 分页查询 Banner。
     *
     * @param keyword 标题或标签关键字
     * @param status 状态，1 启用、0 停用
     * @param current 当前页
     * @param size 页大小
     * @return Banner 分页结果
     * @author Henfon
     * @date 2026-08-30
     */
    public IPage<ContentBanner> page(String keyword, Integer status, long current, long size) {
        // 限制后台分页上限，避免内容运营误传大页大小拖慢数据库。
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        LambdaQueryWrapper<ContentBanner> wrapper = new LambdaQueryWrapper<ContentBanner>()
                .eq(status != null, ContentBanner::getStatus, status)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(ContentBanner::getBannerTitle, keyword)
                        .or().like(ContentBanner::getBannerTag, keyword))
                .orderByAsc(ContentBanner::getSortNo)
                .orderByDesc(ContentBanner::getCreatedAt);
        return bannerMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
    }

    /**
     * 保存或更新 Banner。
     *
     * @param request Banner 保存请求
     * @return Banner ID
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public Long save(ContentBannerSaveRequest request) {
        validate(request);
        ContentBanner banner = new ContentBanner();
        banner.setId(request.id());
        banner.setBannerTitle(request.bannerTitle().trim());
        banner.setBannerTag(trimToNull(request.bannerTag()));
        banner.setSubtitle(trimToNull(request.subtitle()));
        banner.setImageUrl(request.imageUrl().trim());
        banner.setLinkType(normalizeLinkType(request.linkType()));
        banner.setLinkTarget(trimToNull(request.linkTarget()));
        banner.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        banner.setStatus(request.status() == null ? ENABLED : request.status());
        banner.setStartAt(request.startAt());
        banner.setEndAt(request.endAt());
        banner.setRemark(trimToNull(request.remark()));
        if (banner.getId() == null) {
            // 新 Banner 默认启用，门户查询会根据时间窗口自动过滤尚未发布的内容。
            bannerMapper.insert(banner);
        } else {
            ensureExists(banner.getId());
            if (bannerMapper.updateById(banner) == 0) {
                throw new BusinessException("CONTENT_BANNER_CONCURRENT_UPDATE", "Banner 已被其他操作修改，请刷新后重试");
            }
        }
        return banner.getId();
    }

    /**
     * 修改 Banner 启停状态。
     *
     * @param id Banner ID
     * @param status 目标状态
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != ENABLED && status != DISABLED)) {
            throw new BusinessException("CONTENT_BANNER_STATUS_INVALID", "Banner 状态必须为 0 或 1");
        }
        ContentBanner banner = getRequired(id);
        banner.setStatus(status);
        if (bannerMapper.updateById(banner) == 0) {
            throw new BusinessException("CONTENT_BANNER_CONCURRENT_UPDATE", "Banner 已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 逻辑删除 Banner。
     *
     * @param id Banner ID
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void delete(Long id) {
        // 删除只影响后台可见性，不物理清理图片对象，避免误删仍被其他环境引用的媒体。
        ensureExists(id);
        bannerMapper.deleteById(id);
    }

    /**
     * 获取指定 Banner，不存在时抛出统一业务异常。
     *
     * @param id Banner ID
     * @return Banner 实体
     * @author Henfon
     * @date 2026-08-30
     */
    private ContentBanner getRequired(Long id) {
        if (id == null) {
            throw new BusinessException("CONTENT_BANNER_NOT_FOUND", "Banner 不存在");
        }
        ContentBanner banner = bannerMapper.selectById(id);
        if (banner == null) {
            throw new BusinessException("CONTENT_BANNER_NOT_FOUND", "Banner 不存在");
        }
        return banner;
    }

    /**
     * 校验 Banner 跳转和发布窗口。
     *
     * @param request Banner 保存请求
     * @author Henfon
     * @date 2026-08-30
     */
    private void validate(ContentBannerSaveRequest request) {
        if (request.startAt() != null && request.endAt() != null && request.startAt().isAfter(request.endAt())) {
            throw new BusinessException("CONTENT_BANNER_TIME_INVALID", "Banner 开始时间不能晚于结束时间");
        }
        Integer status = request.status();
        if (status != null && status != ENABLED && status != DISABLED) {
            throw new BusinessException("CONTENT_BANNER_STATUS_INVALID", "Banner 状态必须为 0 或 1");
        }
        String linkType = normalizeLinkType(request.linkType());
        if (!StringUtils.hasText(request.linkTarget()) && !"NONE".equals(linkType)) {
            throw new BusinessException("CONTENT_BANNER_LINK_INVALID", "当前跳转类型必须填写跳转目标");
        }
        if (StringUtils.hasText(request.linkTarget()) && "NONE".equals(linkType)) {
            throw new BusinessException("CONTENT_BANNER_LINK_INVALID", "无跳转 Banner 不能填写跳转目标");
        }
        if ("URL".equals(linkType) && StringUtils.hasText(request.linkTarget())
                && !(request.linkTarget().startsWith("http://") || request.linkTarget().startsWith("https://"))) {
            throw new BusinessException("CONTENT_BANNER_LINK_INVALID", "外部链接必须以 http:// 或 https:// 开头");
        }
    }

    /**
     * 规范化跳转类型并拦截不支持的类型。
     *
     * @param linkType 跳转类型
     * @return 大写跳转类型
     * @author Henfon
     * @date 2026-08-30
     */
    private String normalizeLinkType(String linkType) {
        String normalized = StringUtils.hasText(linkType) ? linkType.trim().toUpperCase() : "NONE";
        if (!normalized.equals("PRODUCT") && !normalized.equals("CATEGORY")
                && !normalized.equals("URL") && !normalized.equals("COUPON")
                && !normalized.equals("NONE")) {
            throw new BusinessException("CONTENT_BANNER_LINK_INVALID", "不支持的 Banner 跳转类型");
        }
        return normalized;
    }

    /**
     * 将空白字符串转换为 null，避免无意义的空文本进入数据库。
     *
     * @param value 待处理字符串
     * @return 清理后的字符串
     * @author Henfon
     * @date 2026-08-30
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 校验 Banner 存在。
     *
     * @param id Banner ID
     * @author Henfon
     * @date 2026-08-30
     */
    private void ensureExists(Long id) {
        getRequired(id);
    }
}
