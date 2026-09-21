package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.ai.dto.AiFaqSaveRequest;
import com.henfon.shop.ai.entity.AiFaq;
import com.henfon.shop.ai.mapper.AiFaqMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 知识库问答维护服务。
 *
 * 保存后把 sync_status 置回 PENDING，由向量同步任务收敛到 Qdrant；这里不做同步，
 * 是因为一次保存不该被外部服务的可用性拖住。删除同理：任务发现来源已不存在时会
 * 清掉对应的向量点。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Service
public class AiFaqAdminService {

    /** 向量待同步状态，与 ai_faq.sync_status 的取值一致。 */
    private static final String SYNC_PENDING = "PENDING";

    private final AiFaqMapper faqMapper;

    /**
     * 创建知识库问答维护服务。
     *
     * @param faqMapper 知识库问答 Mapper
     * @author Henfon
     * @date 2026-09-21
     */
    public AiFaqAdminService(AiFaqMapper faqMapper) {
        this.faqMapper = faqMapper;
    }

    /**
     * 分页查询知识库问答。
     *
     * @param keyword 问法或答案关键字
     * @param category 业务分类
     * @param enabled 启用状态
     * @param current 当前页
     * @param size 页大小
     * @return 分页数据
     * @author Henfon
     * @date 2026-09-21
     */
    public IPage<AiFaq> page(String keyword, String category, Integer enabled, long current, long size) {
        LambdaQueryWrapper<AiFaq> wrapper = Wrappers.lambdaQuery(AiFaq.class)
                .orderByAsc(AiFaq::getSortNo)
                .orderByDesc(AiFaq::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(condition -> condition.like(AiFaq::getQuestion, keyword)
                    .or().like(AiFaq::getAnswer, keyword));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(AiFaq::getCategory, category);
        }
        if (enabled != null) {
            wrapper.eq(AiFaq::getEnabled, enabled);
        }
        return faqMapper.selectPage(new Page<>(current, size), wrapper);
    }

    /**
     * 新增或修改知识库问答。
     *
     * @param request 保存请求
     * @return 记录 ID
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public Long save(AiFaqSaveRequest request) {
        AiFaq entity;
        if (request.getId() == null) {
            entity = new AiFaq();
        } else {
            entity = faqMapper.selectById(request.getId());
            if (entity == null) {
                throw new BusinessException("AI_FAQ_NOT_FOUND", "知识条目不存在或已删除");
            }
        }
        entity.setQuestion(request.getQuestion());
        entity.setAnswer(request.getAnswer());
        entity.setCategory(request.getCategory());
        entity.setKeywords(request.getKeywords());
        entity.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        entity.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        // 问法、答案、关键词任一变化都要重新向量化，所以保存即标脏，由同步任务决定是否真的重算。
        entity.setSyncStatus(SYNC_PENDING);
        if (entity.getId() == null) {
            faqMapper.insert(entity);
        } else {
            faqMapper.updateById(entity);
        }
        return entity.getId();
    }

    /**
     * 删除知识库问答。
     *
     * @param id 记录 ID
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public void delete(Long id) {
        AiFaq entity = faqMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("AI_FAQ_NOT_FOUND", "知识条目不存在或已删除");
        }
        faqMapper.deleteById(id);
    }
}
