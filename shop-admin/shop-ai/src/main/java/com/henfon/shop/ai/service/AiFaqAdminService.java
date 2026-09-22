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

import java.util.List;

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

    /** 导出条数上限。知识再多也到不了这个量级，留一道防止一次把整表读进内存。 */
    private static final int MAX_EXPORT_ROWS = 5000;

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
        return faqMapper.selectPage(new Page<>(current, size), conditions(keyword, category, enabled));
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
     * 导出知识库问答为 CSV 文本。
     *
     * 沿用分页那套筛选条件，导的就是界面上筛出来的内容，而不是整张表——运营通常是按某个
     * 分类导出交给业务同学改，导出全部再手工删反而更容易出错。
     *
     * 走 CSV 而不是接入异步导出中心：知识库是几百到几千条的文案，浏览器直接下载足够，
     * 为它排一次队列、生成一个要回收的文件，反而多了个"找不到下载链接在哪"的环节。
     *
     * @param keyword 问法或答案关键字
     * @param category 业务分类
     * @param enabled 启用状态
     * @return CSV 文本，含表头；没有记录时只有表头
     * @author Henfon
     * @date 2026-09-22
     */
    public String exportCsv(String keyword, String category, Integer enabled) {
        List<AiFaq> rows = faqMapper.selectList(conditions(keyword, category, enabled)
                .last("LIMIT " + MAX_EXPORT_ROWS));
        StringBuilder csv = new StringBuilder();
        appendRow(csv, List.of("ID", "标准问法", "标准答案", "业务分类", "检索关键词", "排序", "启用", "向量同步", "更新时间"));
        if (rows != null) {
            for (AiFaq row : rows) {
                appendRow(csv, List.of(String.valueOf(row.getId()),
                        row.getQuestion(),
                        row.getAnswer(),
                        row.getCategory(),
                        row.getKeywords(),
                        String.valueOf(row.getSortNo()),
                        row.getEnabled() != null && row.getEnabled() == 1 ? "启用" : "停用",
                        row.getSyncStatus(),
                        String.valueOf(row.getUpdatedAt())));
            }
        }
        return csv.toString();
    }

    /**
     * 按筛选条件构造查询条件。
     *
     * @param keyword 问法或答案关键字
     * @param category 业务分类
     * @param enabled 启用状态
     * @return 查询条件
     * @author Henfon
     * @date 2026-09-22
     */
    private LambdaQueryWrapper<AiFaq> conditions(String keyword, String category, Integer enabled) {
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
        return wrapper;
    }

    /**
     * 写一行 CSV。
     *
     * 含分隔符、引号或换行的字段必须加引号并把引号翻倍：答案里出现逗号是很常见的，
     * 不加引号这一行就会被 Excel 拆成两列。
     *
     * @param csv 目标
     * @param cells 单元格文本，可含 null
     * @author Henfon
     * @date 2026-09-22
     */
    private void appendRow(StringBuilder csv, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(quote(cells.get(i)));
        }
        csv.append("\r\n");
    }

    /**
     * 转义单个单元格。
     *
     * @param value 原始文本，可为空
     * @return 转义后的单元格
     * @author Henfon
     * @date 2026-09-22
     */
    private String quote(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0 && text.indexOf('\n') < 0
                && text.indexOf('\r') < 0) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
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
