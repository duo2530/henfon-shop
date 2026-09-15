package com.henfon.shop.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.entity.CatalogSku;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.trade.dto.TradeFreightQuoteRequest;
import com.henfon.shop.trade.dto.TradeFreightQuoteResponse;
import com.henfon.shop.trade.dto.TradeFreightTemplateSaveRequest;
import com.henfon.shop.trade.entity.TradeFreightTemplate;
import com.henfon.shop.trade.mapper.TradeFreightTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * 交易运费模板和运费试算服务。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
public class TradeFreightService {

    private static final int DEFAULT_WEIGHT_GRAM = 1000;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final TradeFreightTemplateMapper templateMapper;
    private final CatalogProductMapper productMapper;
    private final CatalogSkuMapper skuMapper;

    /**
     * 创建运费服务。
     *
     * @param templateMapper 运费模板数据访问对象
     * @param productMapper 商品数据访问对象
     * @param skuMapper SKU数据访问对象
     * @author Henfon
     * @date 2026-09-01
     */
    public TradeFreightService(TradeFreightTemplateMapper templateMapper,
                               CatalogProductMapper productMapper,
                               CatalogSkuMapper skuMapper) {
        this.templateMapper = templateMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
    }

    /**
     * 查询当前启用的默认运费模板。
     *
     * @return 默认运费模板
     * @author Henfon
     * @date 2026-09-01
     */
    public TradeFreightTemplate getDefaultTemplate() {
        TradeFreightTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<TradeFreightTemplate>()
                .eq(TradeFreightTemplate::getStatus, 1)
                .eq(TradeFreightTemplate::getIsDefault, 1)
                .orderByDesc(TradeFreightTemplate::getId)
                .last("LIMIT 1"));
        if (template == null) {
            throw new BusinessException("TRADE_FREIGHT_TEMPLATE_NOT_FOUND", "暂无启用的运费模板");
        }
        return template;
    }

    /**
     * 保存后台默认运费模板。
     *
     * @param request 运费模板保存请求
     * @return 保存后的模板
     * @author Henfon
     * @date 2026-09-01
     */
    @Transactional
    public TradeFreightTemplate saveTemplate(TradeFreightTemplateSaveRequest request) {
        validateTemplate(request);
        TradeFreightTemplate template = request.id() == null
                ? new TradeFreightTemplate() : templateMapper.selectById(request.id());
        if (template == null) {
            throw new BusinessException("TRADE_FREIGHT_TEMPLATE_NOT_FOUND", "运费模板不存在");
        }
        // 当前版本只开放一个默认模板，保存时清理其他默认标记，避免试算命中多个模板。
        templateMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TradeFreightTemplate>()
                .set(TradeFreightTemplate::getIsDefault, 0)
                .eq(TradeFreightTemplate::getIsDefault, 1));
        template.setTemplateName(request.templateName().trim());
        template.setCarrierName(StringUtils.hasText(request.carrierName()) ? request.carrierName().trim() : null);
        template.setBaseWeightGram(request.baseWeightGram());
        template.setBaseFee(money(request.baseFee()));
        template.setAdditionalWeightGram(request.additionalWeightGram());
        template.setAdditionalFee(money(request.additionalFee()));
        template.setFreeShippingThreshold(money(request.freeShippingThreshold()));
        template.setRemoteSurcharge(money(request.remoteSurcharge()));
        template.setRemoteRegionsCsv(normalizeCsv(request.remoteRegionsCsv()));
        template.setStatus(request.status());
        template.setIsDefault(1);
        if (template.getId() == null) {
            templateMapper.insert(template);
        } else if (templateMapper.updateById(template) == 0) {
            throw new BusinessException("TRADE_FREIGHT_TEMPLATE_CONCURRENT_UPDATE", "运费模板已被其他操作修改，请刷新后重试");
        }
        return template;
    }

    /**
     * 按商品、地址和订单金额试算运费。
     *
     * @param request 运费试算请求
     * @return 运费试算结果
     * @author Henfon
     * @date 2026-09-01
     */
    public TradeFreightQuoteResponse quote(TradeFreightQuoteRequest request) {
        TradeFreightTemplate template = getDefaultTemplate();
        long totalWeightGram = 0L;
        for (TradeFreightQuoteRequest.Item item : request.items()) {
            totalWeightGram += resolveWeightGram(item);
            if (totalWeightGram > Integer.MAX_VALUE) {
                throw new BusinessException("TRADE_FREIGHT_WEIGHT_INVALID", "商品总重量超出系统支持范围");
            }
        }
        BigDecimal subtotal = money(request.subtotalAmount());
        BigDecimal freeShippingThreshold = money(template.getFreeShippingThreshold());
        boolean freeShipping = freeShippingThreshold.signum() > 0
                && subtotal.compareTo(freeShippingThreshold) >= 0;
        boolean remoteArea = isRemoteArea(template, request.receiverProvince(), request.receiverCity(), request.receiverDistrict());
        BigDecimal freight = freeShipping ? ZERO : calculateWeightFee(template, totalWeightGram);
        // 偏远附加费独立于包邮门槛，避免“包邮”承诺覆盖实际远程配送成本。
        if (remoteArea) {
            freight = freight.add(money(template.getRemoteSurcharge()));
        }
        return new TradeFreightQuoteResponse(template.getId(), template.getTemplateName(), template.getCarrierName(),
                money(freight), money(template.getFreeShippingThreshold()), (int) totalWeightGram,
                freeShipping, remoteArea);
    }

    /**
     * 校验客户端订单运费是否等于服务端模板试算结果。
     *
     * @param request 订单运费试算请求
     * @param freightAmount 客户端提交运费
     * @return 服务端确认的运费结果
     * @author Henfon
     * @date 2026-09-01
     */
    public TradeFreightQuoteResponse requireMatchedQuote(TradeFreightQuoteRequest request, BigDecimal freightAmount) {
        TradeFreightQuoteResponse quote = quote(request);
        if (freightAmount == null || money(freightAmount).compareTo(quote.freightAmount()) != 0) {
            throw new BusinessException("TRADE_FREIGHT_CHANGED", "运费已变化，请刷新结算页后重试");
        }
        return quote;
    }

    /**
     * 读取商品或 SKU 的计费重量。
     *
     * @param item 运费试算商品
     * @return 商品总重量（克）
     * @author Henfon
     * @date 2026-09-01
     */
    private long resolveWeightGram(TradeFreightQuoteRequest.Item item) {
        CatalogProduct product = productMapper.selectById(item.productId());
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            throw new BusinessException("TRADE_PRODUCT_UNAVAILABLE", "商品不存在或已下架");
        }
        int weightGram = positiveWeight(product.getWeightGram());
        if (item.skuId() != null) {
            CatalogSku sku = skuMapper.selectById(item.skuId());
            if (sku == null || !item.productId().equals(sku.getProductId()) || !Integer.valueOf(1).equals(sku.getStatus())) {
                throw new BusinessException("TRADE_SKU_UNAVAILABLE", "商品规格不存在或已停用");
            }
            weightGram = positiveWeight(sku.getWeightGram());
        }
        return (long) weightGram * item.quantity();
    }

    /**
     * 根据重量计算首重和续重费用。
     *
     * @param template 运费模板
     * @param totalWeightGram 总重量（克）
     * @return 重量运费
     * @author Henfon
     * @date 2026-09-01
     */
    private BigDecimal calculateWeightFee(TradeFreightTemplate template, long totalWeightGram) {
        if (totalWeightGram <= 0) {
            return ZERO;
        }
        long baseWeight = Math.max(template.getBaseWeightGram() == null
                ? DEFAULT_WEIGHT_GRAM : template.getBaseWeightGram(), 1);
        long additionalWeight = Math.max(template.getAdditionalWeightGram() == null
                ? DEFAULT_WEIGHT_GRAM : template.getAdditionalWeightGram(), 1);
        long extraWeight = Math.max(totalWeightGram - baseWeight, 0);
        long additionalUnits = (extraWeight + additionalWeight - 1) / additionalWeight;
        BigDecimal baseFee = money(template.getBaseFee());
        BigDecimal additionalFee = money(template.getAdditionalFee());
        return money(baseFee.add(additionalFee.multiply(BigDecimal.valueOf(additionalUnits))));
    }

    /**
     * 判断收货地址是否命中偏远地区关键词。
     *
     * @param template 运费模板
     * @param province 省
     * @param city 市
     * @param district 区县
     * @return 是否命中偏远地区
     * @author Henfon
     * @date 2026-09-01
     */
    private boolean isRemoteArea(TradeFreightTemplate template, String province, String city, String district) {
        if (!StringUtils.hasText(template.getRemoteRegionsCsv())) {
            return false;
        }
        String address = String.join("", safeText(province), safeText(city), safeText(district));
        return Arrays.stream(template.getRemoteRegionsCsv().split("[,，\\n]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(address::contains);
    }

    /**
     * 校验后台模板参数的业务边界。
     *
     * @param request 模板请求
     * @author Henfon
     * @date 2026-09-01
     */
    private void validateTemplate(TradeFreightTemplateSaveRequest request) {
        if (request.status() != 0 && request.status() != 1) {
            throw new BusinessException("TRADE_FREIGHT_STATUS_INVALID", "运费模板状态不合法");
        }
        if (request.baseFee().signum() < 0 || request.additionalFee().signum() < 0
                || request.remoteSurcharge().signum() < 0) {
            throw new BusinessException("TRADE_FREIGHT_AMOUNT_INVALID", "运费金额不能为负数");
        }
    }

    /**
     * 将重量转换为正数，兼容历史商品未填写重量的记录。
     *
     * @param weightGram 原始重量
     * @return 有效重量
     * @author Henfon
     * @date 2026-09-01
     */
    private int positiveWeight(Integer weightGram) {
        return weightGram == null || weightGram <= 0 ? DEFAULT_WEIGHT_GRAM : weightGram;
    }

    /**
     * 清理地区关键词输入。
     *
     * @param csv 地区关键词
     * @return 规范化后的关键词
     * @author Henfon
     * @date 2026-09-01
     */
    private String normalizeCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return null;
        }
        return Arrays.stream(csv.split("[,，\\n]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    /**
     * 将金额统一为两位小数。
     *
     * @param amount 原始金额
     * @return 两位小数金额
     * @author Henfon
     * @date 2026-09-01
     */
    private BigDecimal money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 安全读取可选地址字段。
     *
     * @param text 地址字段
     * @return 非空文本或空字符串
     * @author Henfon
     * @date 2026-09-01
     */
    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }
}
