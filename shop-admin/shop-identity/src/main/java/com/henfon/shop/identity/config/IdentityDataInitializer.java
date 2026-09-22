package com.henfon.shop.identity.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.identity.entity.MemberTag;
import com.henfon.shop.identity.entity.SysDept;
import com.henfon.shop.identity.entity.SysMenu;
import com.henfon.shop.identity.entity.SysRole;
import com.henfon.shop.identity.entity.SysUser;
import com.henfon.shop.identity.mapper.MemberTagMapper;
import com.henfon.shop.identity.mapper.SysDeptMapper;
import com.henfon.shop.identity.mapper.SysMenuMapper;
import com.henfon.shop.identity.mapper.SysRoleMapper;
import com.henfon.shop.identity.mapper.SysRoleMenuMapper;
import com.henfon.shop.identity.mapper.SysUserMapper;
import com.henfon.shop.identity.mapper.SysUserRoleMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 身份模块基础数据初始化器。
 *
 * <p>首次启动自动创建平台部门、超级管理员角色、客服角色、系统菜单、admin 账号与客服账号，
 * 重复启动不会重复插入，也不会覆盖已经被改过的数据（密码、菜单名称等）。</p>
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Component
@Order(1)
public class IdentityDataInitializer implements ApplicationRunner {

    /**
     * 会员画像标签字典的预置项，格式为「标签名 / 排序号 / 维度说明」。
     *
     * <p>标签字典是后台画像筛选的基础数据，与业务数据无关，因此随应用启动幂等补齐。</p>
     */
    private static final String[][] PRESET_MEMBER_TAGS = {
            {"高净值客户", "10", "价值分层：累计消费与客单价处于头部"},
            {"高复购客户", "20", "价值分层：复购频次高于同行均值"},
            {"价格敏感型", "30", "价值分层：优惠券依赖度高，正价转化低"},
            {"潜力成长型", "40", "价值分层：近期消费增速快，可重点培育"},
            {"大促活跃客", "50", "消费行为：大促期间下单占比高"},
            {"秒杀常客", "60", "消费行为：常参与限时限量活动"},
            {"新客首单", "70", "消费行为：注册后完成首单，处于激活期"},
            {"低频沉睡客", "80", "消费行为：长期未下单，需要召回"},
            {"数码偏好", "90", "品类偏好：集中在数码电子品类"},
            {"音频发烧", "100", "品类偏好：集中在耳机音箱品类"},
            {"家居生活", "110", "品类偏好：集中在家居生活品类"},
            {"户外运动", "120", "品类偏好：集中在户外运动品类"},
            {"时尚穿搭", "130", "品类偏好：集中在服饰穿搭品类"},
            {"售后高频", "140", "服务风险：售后申请次数明显偏高"},
            {"投诉敏感", "150", "服务风险：历史投诉或差评记录"},
            {"多地址收货", "160", "服务风险：常用收货地址数量多"}
    };

    /**
     * 客服角色默认拥有的权限编码。
     *
     * <p>包含接待与回复所必需的几项：客服工作台（同时是接待接口的权限）、查看与处理工单、
     * 查看知识库，以及排班。排班在真实组织里通常归排班主管，但本项目只有客服与超管两个
     * 管理角色，不放开的话客服账号登录后既看不到自己的班次也没法自助调整；上线做权限细分时
     * 把 ai:agent:schedule 从这份名单里摘掉即可。不含任何写知识库的权限——客服能看标准
     * 答案但不能改。</p>
     *
     * <p>ai:agent:stats:query（客服统计）刻意不放进来：那一页看的是"每位客服接待了几单、
     * 多久、被评了几分"，属于主管视角，摆在同事之间会变成互相比较的排行榜。要放开时把权限
     * 编码加到本名单即可，页面本身已经按权限点授权。</p>
     */
    private static final String[] AGENT_PERMISSION_CODES = {
            "ai:agent:serve", "ai:agent:schedule", "ai:ticket:query", "ai:ticket:handle", "ai:faq:query"
    };

    /**
     * 客服演示账号，格式为「登录名 / 姓名」。
     *
     * 建两个而不是一个：抢单式接待、坐席状态、排班与客服统计这几块都是多人场景才看得出效果，
     * 只有一个客服账号时统计页永远只有一行、也验证不了"会话被别人接走"的分支。
     */
    private static final String[][] DEMO_AGENTS = {
            {"service01", "客服一号"},
            {"service02", "客服二号"}
    };

    private final SysDeptMapper sysDeptMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final MemberTagMapper memberTagMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建身份基础数据初始化器。
     *
     * @param sysDeptMapper 部门数据访问对象
     * @param sysRoleMapper 角色数据访问对象
     * @param sysMenuMapper 菜单数据访问对象
     * @param sysUserMapper 用户数据访问对象
     * @param sysUserRoleMapper 用户角色关联数据访问对象
     * @param sysRoleMenuMapper 角色菜单关联数据访问对象
     * @param memberTagMapper 会员标签数据访问对象
     * @param passwordEncoder 密码编码器
     * @author Henfon
     * @date 2026-08-29
     */
    public IdentityDataInitializer(SysDeptMapper sysDeptMapper, SysRoleMapper sysRoleMapper,
                                   SysMenuMapper sysMenuMapper, SysUserMapper sysUserMapper,
                                   SysUserRoleMapper sysUserRoleMapper, SysRoleMenuMapper sysRoleMenuMapper,
                                   MemberTagMapper memberTagMapper, PasswordEncoder passwordEncoder) {
        this.sysDeptMapper = sysDeptMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.memberTagMapper = memberTagMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 执行身份基础数据初始化。
     *
     * @param args 应用启动参数
     * @author Henfon
     * @date 2026-08-29
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SysDept rootDept = ensureRootDept();
        SysRole superAdmin = ensureSuperAdminRole();
        List<SysMenu> menus = ensureSystemMenus();
        menus.addAll(ensureBusinessMenus());
        bindRoleMenus(superAdmin.getId(), menus);
        ensureAdminUser(rootDept.getId(), superAdmin.getId());
        // 客服角色单独登记：客服账号登录后只应看到接待相关的几个页面，把它绑成全量菜单
        // 等于人人都是超管，接待界面与经营数据混在一起也没有边界可言。
        SysRole agentRole = ensureAgentRole();
        bindAgentMenus(agentRole.getId(), menus);
        ensureAgentUser(rootDept.getId(), agentRole.getId());
        ensurePresetMemberTags();
    }

    /**
     * 确保会员画像标签字典存在。
     *
     * <p>后台「客户与会员中心」的画像筛选栏读取的是标签字典而不是会员数据推导，
     * 因此首次启动补齐一套常用画像维度，保证空库也能直接做标签筛选。
     * 已存在的标签不做任何改写，运营调整过的名称、排序与停用状态都会保留。</p>
     *
     * @author Henfon
     * @date 2026-09-17
     */
    private void ensurePresetMemberTags() {
        for (String[] preset : PRESET_MEMBER_TAGS) {
            String tagName = preset[0];
            Long existing = memberTagMapper.selectCount(new LambdaQueryWrapper<MemberTag>()
                    .eq(MemberTag::getTenantId, 0L).eq(MemberTag::getTagName, tagName));
            if (existing != null && existing > 0) {
                continue;
            }
            MemberTag tag = new MemberTag();
            tag.setTenantId(0L);
            tag.setTagName(tagName);
            tag.setSortNo(Integer.parseInt(preset[1]));
            tag.setStatus(1);
            tag.setRemark(preset[2]);
            memberTagMapper.insert(tag);
        }
    }

    /**
     * 确保平台根部门存在。
     *
     * @return 平台根部门
     * @author Henfon
     * @date 2026-08-29
     */
    private SysDept ensureRootDept() {
        SysDept dept = sysDeptMapper.selectOne(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getTenantId, 0L).eq(SysDept::getDeptCode, "PLATFORM").last("LIMIT 1"));
        if (dept != null) {
            return dept;
        }
        dept = new SysDept();
        dept.setTenantId(0L);
        dept.setParentId(0L);
        dept.setDeptName("平台运营中心");
        dept.setDeptCode("PLATFORM");
        dept.setSortNo(0);
        dept.setStatus(1);
        sysDeptMapper.insert(dept);
        return dept;
    }

    /**
     * 确保超级管理员角色存在。
     *
     * @return 超级管理员角色
     * @author Henfon
     * @date 2026-08-29
     */
    private SysRole ensureSuperAdminRole() {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, 0L).eq(SysRole::getRoleKey, "SUPER_ADMIN").last("LIMIT 1"));
        if (role != null) {
            return role;
        }
        role = new SysRole();
        role.setTenantId(0L);
        role.setRoleKey("SUPER_ADMIN");
        role.setRoleName("超级管理员");
        role.setRoleSort(0);
        role.setStatus(1);
        role.setDataScope("ALL");
        role.setDescription("拥有后台全部功能权限");
        sysRoleMapper.insert(role);
        return role;
    }

    /**
     * 确保系统菜单和按钮权限存在。
     *
     * @return 系统菜单集合
     * @author Henfon
     * @date 2026-08-29
     */
    private List<SysMenu> ensureSystemMenus() {
        List<SysMenu> menus = new ArrayList<>();
        SysMenu root = ensureMenu("系统管理", "DIRECTORY", null, "system", 10,
                0L, null, "Shield");
        menus.add(root);
        menus.add(ensureMenu("用户管理", "MENU", "system:user:query", "/system/users", 10,
                root.getId(), null, "Users"));
        menus.add(ensureMenu("角色管理", "MENU", "system:role:query", "/system/roles", 20,
                root.getId(), null, "ShieldCheck"));
        menus.add(ensureMenu("菜单管理", "MENU", "system:menu:query", "/system/menus", 30,
                root.getId(), null, "Menu"));
        menus.add(ensureMenu("数据权限", "MENU", "system:data-rule:query", "/system/data-rules", 40,
                root.getId(), null, "Lock"));
        // 将系统菜单挂到目录节点下，便于前端按树形结构渲染侧边栏。
        for (int i = 1; i < menus.size(); i++) {
            SysMenu menu = menus.get(i);
            if (!root.getId().equals(menu.getParentId())) {
                menu.setParentId(root.getId());
                sysMenuMapper.updateById(menu);
            }
        }
        String[][] buttons = {
                {"新增用户", "system:user:add"}, {"修改用户", "system:user:update"},
                {"删除用户", "system:user:delete"}, {"查询部门", "system:dept:query"},
                {"保存部门", "system:dept:save"}, {"保存角色", "system:role:save"},
                {"保存菜单", "system:menu:save"}, {"保存数据规则", "system:data-rule:save"},
                {"查询用户角色", "system:user-role:query"}, {"保存用户角色", "system:user-role:save"},
                {"查询角色菜单", "system:role-menu:query"}, {"保存角色菜单", "system:role-menu:save"},
                {"查询角色数据权限", "system:role-data-rule:query"}, {"保存角色数据权限", "system:role-data-rule:save"}
        };
        for (String[] button : buttons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 100);
            if (!root.getId().equals(menu.getParentId())) {
                menu.setParentId(root.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        return menus;
    }

    /**
     * 确保管理端业务菜单存在，供前端按权限动态生成导航。
     *
     * @return 业务菜单集合
     * @author Henfon
     * @date 2026-08-29
     */
    private List<SysMenu> ensureBusinessMenus() {
        List<SysMenu> menus = new ArrayList<>();
        SysMenu dashboard = ensureMenu("工作台", "MENU", "dashboard:view", "/dashboard", 1,
                0L, "DashboardView", "LayoutDashboard");
        SysMenu ecommerce = ensureMenu("电商运营中心", "DIRECTORY", null, "/ecommerce", 2,
                0L, null, "ShoppingBag");
        SysMenu marketing = ensureMenu("营销与促销中心", "DIRECTORY", null, "/marketing", 3,
                0L, null, "BadgePercent");
        SysMenu inventory = ensureMenu("仓储进销存", "DIRECTORY", null, "/inventory", 4,
                0L, null, "Boxes");
        SysMenu finance = ensureMenu("财务结算中心", "DIRECTORY", null, "/finance", 5,
                0L, null, "Coins");
        SysMenu analytics = ensureMenu("经营分析与BI", "DIRECTORY", null, "/analytics", 6,
                0L, null, "LineChart");
        SysMenu content = ensureMenu("内容与客户运营", "DIRECTORY", null, "/content", 7,
                0L, null, "MessageSquare");
        // 客服中心：把工单、实时接待、知识库、排班四个页面从「内容与客户运营」里摘出来单独成组。
        // 它们原本跟轮播海报、客户评价挤在一个目录里，而那四个页面是客服岗每天的工作面，
        // 单独成组之后按岗找菜单才不用先在内容目录里翻一遍。
        SysMenu service = ensureMenu("客服中心", "DIRECTORY", null, "/service", 8,
                0L, null, "Headphones");
        // 先按原权限编码定位参数配置，确保旧版本角色关联继续指向参数配置页面。
        SysMenu settingsConfig = ensureMenu("参数配置", "MENU", "system:config:view", "/settings", 1,
                0L, "SettingsView", "Settings");
        if ("系统与参数设置".equals(settingsConfig.getMenuName())) {
            // 旧记录本身就是参数配置菜单，先改名释放目录节点名称，再创建新的目录记录。
            normalizeMenu(settingsConfig, "参数配置", "MENU", "system:config:view", "/settings", 1,
                    0L, "SettingsView", "Settings");
        }
        // 系统设置作为目录承载参数配置、登录记录和操作审计三个独立页面。
        // 排序让到 9：客服中心插在内容与客户运营之后，系统设置仍然排在业务目录的最后。
        SysMenu settings = ensureMenu("系统与参数设置", "DIRECTORY", null, null, 9,
                0L, null, "Settings");
        normalizeMenu(settings, "系统与参数设置", "DIRECTORY", null, null, 9,
                0L, null, "Settings");
        normalizeMenu(settingsConfig, "参数配置", "MENU", "system:config:view", "/settings", 1,
                settings.getId(), "SettingsView", "Settings");
        menus.addAll(List.of(dashboard, ecommerce, marketing, inventory, finance, analytics, content, service,
                settings));
        menus.add(settingsConfig);
        // 系统设置保存权限挂在设置菜单下，供运费模板等参数配置接口校验。
        SysMenu settingsSave = ensureMenu("保存系统配置", "BUTTON", "system:config:save", null, 100,
                settingsConfig.getId(), null, null);
        if (!settingsConfig.getId().equals(settingsSave.getParentId())) {
            // 兼容旧版本保存按钮曾直接挂在系统设置节点下的历史数据。
            settingsSave.setParentId(settingsConfig.getId());
            sysMenuMapper.updateById(settingsSave);
        }
        menus.add(settingsSave);

        menus.add(ensureMenu("商品管理", "MENU", "catalog:product:query", "/ecommerce/products", 1,
                ecommerce.getId(), "ProductManagementView", "Package"));
        SysMenu categoryMenu = ensureMenu("类目管理", "MENU", "catalog:category:query", "/ecommerce/categories", 2,
                ecommerce.getId(), "CategoryManagementView", "Tags");
        // 兼容旧版本曾以 BUTTON 类型写入相同权限编码的记录，确保升级后路由和组件信息被补齐。
        if (!"MENU".equals(categoryMenu.getMenuType())
                || !"/ecommerce/categories".equals(categoryMenu.getRoutePath())
                || !ecommerce.getId().equals(categoryMenu.getParentId())) {
            categoryMenu.setMenuType("MENU");
            categoryMenu.setRoutePath("/ecommerce/categories");
            categoryMenu.setComponent("CategoryManagementView");
            categoryMenu.setIcon("Tags");
            categoryMenu.setParentId(ecommerce.getId());
            categoryMenu.setSortNo(2);
            sysMenuMapper.updateById(categoryMenu);
        }
        menus.add(categoryMenu);
        menus.add(ensureMenu("订单履约", "MENU", "trade:order:query", "/ecommerce/orders", 3,
                ecommerce.getId(), "OrderManagementView", "ShoppingCart"));
        SysMenu member = ensureMenu("商城会员", "MENU", "member:user:query", "/ecommerce/customers", 4,
                ecommerce.getId(), "UserManagementView", "UserCheck");
        menus.add(member);
        // 商品增删改、上下架和复制使用独立按钮权限，避免仅拥有查询权限即可修改目录数据。
        String[][] productButtons = {
                {"商品保存", "catalog:product:save"}, {"商品删除", "catalog:product:delete"},
                {"商品上下架", "catalog:product:status"}, {"商品审核", "catalog:product:audit"}
        };
        for (String[] button : productButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 100);
            if (!ecommerce.getId().equals(menu.getParentId())) {
                menu.setParentId(ecommerce.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        menus.add(ensureMenu("优惠券中心", "MENU", "marketing:coupon:query", "/marketing/coupons", 1,
                marketing.getId(), "CouponManagementView", "Ticket"));
        menus.add(ensureMenu("秒杀与拼团", "MENU", "marketing:flash:query", "/marketing/flash-sales", 2,
                marketing.getId(), "FlashSaleManagementView", "Zap"));
        menus.add(ensureMenu("调拨与出入库", "MENU", "inventory:stock:query", "/inventory/stock", 1,
                inventory.getId(), "WarehouseStockView", "Warehouse"));
        menus.add(ensureMenu("供应商与采购", "MENU", "inventory:supplier:query", "/inventory/suppliers", 2,
                inventory.getId(), "SupplierManagementView", "Truck"));
        menus.add(ensureMenu("资金流水对账", "MENU", "payment:transaction:query", "/finance/transactions", 1,
                finance.getId(), "TransactionReconciliationView", "DollarSign"));
        menus.add(ensureMenu("发票与税务", "MENU", "payment:invoice:query", "/finance/invoices", 2,
                finance.getId(), "InvoiceManagementView", "FileText"));
        menus.add(ensureMenu("经营大屏", "MENU", "reporting:overview:query", "/analytics/overview", 1,
                analytics.getId(), "AnalyticsOverviewView", "LineChart"));
        menus.add(ensureMenu("商品动销榜", "MENU", "reporting:product:query", "/analytics/products", 2,
                analytics.getId(), "ProductAnalyticsView", "BarChart3"));
        menus.add(ensureMenu("轮播海报", "MENU", "content:banner:query", "/content/banners", 1,
                content.getId(), "BannerManagementView", "Image"));
        menus.add(ensureMenu("客户评价", "MENU", "content:review:query", "/content/reviews", 2,
                content.getId(), "ReviewManagementView", "MessageSquare"));
        // 收货地址归到内容与客户运营：它跟评价一样是"看客户"的视角，而不是会员档案的一部分——
        // 会员页管的是账户，这一页管的是买家要把货寄到哪。
        SysMenu addressMenu = ensureMenu("收货地址", "MENU", "member:address:query", "/content/member-addresses", 3,
                content.getId(), "MemberAddressView", "MapPin");
        menus.add(addressMenu);
        // —— 客服中心的四个页面 ——
        // 排序把客服工作台放在第一位：客服账号登录后落地的是组内第一个页面，接待是他们的
        // 日常起点，落到工单列表上还得再点一次。路由统一收进 /service 前缀，与所属目录一致。
        //
        // 客服工作台：等待接入的会话队列 + 自己正在接待的会话。整组接口共用一个权限点，
        // 能看队列却接不了会话的角色没有实际意义；它同时也是这个页面的菜单权限。
        SysMenu agentMenu = ensureMenu("客服工作台", "MENU", "ai:agent:serve", "/service/ai-agent", 1,
                service.getId(), "AiAgentWorkbenchView", "MessageSquare");
        normalizeMenu(agentMenu, "客服工作台", "MENU", "ai:agent:serve", "/service/ai-agent", 1,
                service.getId(), "AiAgentWorkbenchView", "MessageSquare");
        menus.add(agentMenu);

        // 客服工单沿用 ai:ticket:query 作为页面访问权限。该权限码上一批先按 BUTTON 登记过，
        // 而 ensureMenu 对已存在的记录只补图标、不改类型与路由，所以必须再走一次 normalizeMenu
        // 把它补齐成 MENU，否则侧边栏拿不到路由与组件名（与类目管理的历史兼容处理同理）。
        SysMenu ticketMenu = ensureMenu("客服工单", "MENU", "ai:ticket:query", "/service/ai-tickets", 2,
                service.getId(), "AiTicketManagementView", "Headphones");
        normalizeMenu(ticketMenu, "客服工单", "MENU", "ai:ticket:query", "/service/ai-tickets", 2,
                service.getId(), "AiTicketManagementView", "Headphones");
        menus.add(ticketMenu);

        // 知识库运营页沿用 ai:faq:query 作为页面访问权限。与客服工单同理：该权限码上一批已按
        // BUTTON 登记过，ensureMenu 命中旧记录时只补图标、不改类型与路由，必须再走一次
        // normalizeMenu 才能把它补齐成 MENU，否则侧边栏拿不到路由与组件名。
        // 同步与召回测试两个动作另受 ai:knowledge:sync 约束，只有查看权的账号进得来但改不了。
        SysMenu knowledgeMenu = ensureMenu("知识库运营", "MENU", "ai:faq:query", "/service/ai-knowledge", 3,
                service.getId(), "AiKnowledgeView", "BookOpen");
        normalizeMenu(knowledgeMenu, "知识库运营", "MENU", "ai:faq:query", "/service/ai-knowledge", 3,
                service.getId(), "AiKnowledgeView", "BookOpen");
        menus.add(knowledgeMenu);

        // 客服排班：按周给客服排班次，决定对外的服务时段提示。与客服工作台分开授权——
        // 每位客服都能看自己的班，但改班次是排班主管的事，共用权限会让误改影响对外提示。
        SysMenu scheduleMenu = ensureMenu("客服排班", "MENU", "ai:agent:schedule",
                "/service/ai-agent-schedule", 4, service.getId(), "AiAgentScheduleView", "CalendarClock");
        normalizeMenu(scheduleMenu, "客服排班", "MENU", "ai:agent:schedule",
                "/service/ai-agent-schedule", 4, service.getId(), "AiAgentScheduleView", "CalendarClock");
        menus.add(scheduleMenu);

        // 客服统计：按人看接待量、服务时长与好评差评率。单独一个权限点而不是并进客服工作台：
        // 它是"看别人干得怎么样"的视角，与能不能接会话是两件事，将来要给主管单独开这个页面时
        // 不必再把工作台的接待权限一起发出去。
        SysMenu statsMenu = ensureMenu("客服统计", "MENU", "ai:agent:stats:query",
                "/service/ai-agent-stats", 5, service.getId(), "AiAgentStatsView", "BarChart3");
        normalizeMenu(statsMenu, "客服统计", "MENU", "ai:agent:stats:query",
                "/service/ai-agent-stats", 5, service.getId(), "AiAgentStatsView", "BarChart3");
        menus.add(statsMenu);

        // 订单操作按钮用于控制后台发货、取消、备注和退款等写权限。
        String[][] tradeButtons = {
                {"订单发货", "trade:order:ship"}, {"订单取消", "trade:order:cancel"},
                {"订单备注", "trade:order:remark"}, {"订单退款", "trade:order:refund"},
                {"订单审核", "trade:order:audit"},
                {"售后查询", "trade:after-sale:query"}, {"售后审核", "trade:after-sale:audit"}
        };
        for (String[] button : tradeButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 100);
            if (!ecommerce.getId().equals(menu.getParentId())) {
                menu.setParentId(ecommerce.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        // 类目管理权限供后台类目维护接口校验，暂挂在电商运营目录下。
        String[][] categoryButtons = {{"类目管理", "catalog:category:query"}};
        for (String[] button : categoryButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 101);
            // 类目查询权限同时作为菜单权限使用时避免将同一菜单 ID 重复加入角色关联表。
            if (categoryMenu.getId().equals(menu.getId())) {
                continue;
            }
            if (!ecommerce.getId().equals(menu.getParentId())) {
                menu.setParentId(ecommerce.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        // 会员状态操作权限挂在会员菜单下，供管理端冻结/解冻按钮进行权限控制。
        String[][] memberButtons = {{"会员冻结/解冻", "member:user:status"}};
        for (String[] button : memberButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 100);
            if (!member.getId().equals(menu.getParentId())) {
                menu.setParentId(member.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        // 改地址单独一个权限点：地址改错就是发错货，能查地址的角色不一定该有改的权力。
        String[][] addressButtons = {{"收货地址维护", "member:address:save"}};
        for (String[] button : addressButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 100);
            if (!addressMenu.getId().equals(menu.getParentId())) {
                menu.setParentId(addressMenu.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        // 库存调整权限与库存查询菜单保持同一父节点，便于后台按按钮权限控制出入库操作。
        String[][] inventoryButtons = {{"库存调整", "inventory:stock:adjust"}};
        for (String[] button : inventoryButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 100);
            if (!inventory.getId().equals(menu.getParentId())) {
                menu.setParentId(inventory.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        // 盘点接口权限挂在仓储目录下，控制盘点查询、创建和差异提交操作。
        String[][] stocktakeButtons = {
                {"库存盘点查询", "inventory:stocktake:query"},
                {"库存盘点创建", "inventory:stocktake:create"},
                {"库存盘点完成", "inventory:stocktake:complete"},
                {"库存盘点导入", "inventory:stocktake:import"},
                {"库存盘点取消", "inventory:stocktake:cancel"}
        };
        for (String[] button : stocktakeButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 102);
            if (!inventory.getId().equals(menu.getParentId())) {
                menu.setParentId(inventory.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        // 仓库基础管理权限挂在仓储目录下，分别控制查询、保存、启停和删除操作。
        String[][] warehouseButtons = {
                {"仓库查询", "inventory:warehouse:query"}, {"仓库保存", "inventory:warehouse:save"},
                {"仓库启停", "inventory:warehouse:status"}, {"仓库删除", "inventory:warehouse:delete"}
        };
        for (String[] button : warehouseButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 101);
            if (!inventory.getId().equals(menu.getParentId())) {
                menu.setParentId(inventory.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        // 供应商基础管理权限挂在仓储目录下，控制供应商查询、保存、启停和删除操作。
        String[][] supplierButtons = {
                {"供应商查询", "inventory:supplier:query"}, {"供应商保存", "inventory:supplier:save"},
                {"供应商启停", "inventory:supplier:status"}, {"供应商删除", "inventory:supplier:delete"}
        };
        for (String[] button : supplierButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 103);
            if (!inventory.getId().equals(menu.getParentId())) {
                menu.setParentId(inventory.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        // 采购单和供应商供货关系权限挂在库存目录下，保证采购入库操作可审计控制。
        String[][] purchaseButtons = {
                {"采购单查询", "inventory:purchase:query"}, {"采购单创建", "inventory:purchase:create"},
                {"采购单验收", "inventory:purchase:receive"}
        };
        for (String[] button : purchaseButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 104);
            if (!inventory.getId().equals(menu.getParentId())) {
                menu.setParentId(inventory.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        // 秒杀活动权限挂在营销目录下，控制活动保存、启停和删除操作。
        String[][] flashSaleButtons = {
                {"秒杀活动保存", "marketing:flash:save"}, {"秒杀活动启停", "marketing:flash:status"},
                {"秒杀活动删除", "marketing:flash:delete"}
        };
        for (String[] button : flashSaleButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 103);
            if (!marketing.getId().equals(menu.getParentId())) {
                menu.setParentId(marketing.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        // 秒杀活动详情是列表页操作列上的「详情」入口，不占独立菜单项，因此登记为按钮权限而不是 MENU：
        // 侧边栏只渲染 MENU，故多出的权限点不会在导航里显示。
        // normalizeMenu 会把早期版本误建成的 MENU 记录原地改写成 BUTTON（ensureMenu 只增不改，
        // 不做这一步旧库会长期残留一个指向已删路由的孤儿菜单页）。
        SysMenu flashDetailButton = ensureMenu("秒杀活动详情", "BUTTON", "marketing:flash:detail", null, 106);
        normalizeMenu(flashDetailButton, "秒杀活动详情", "BUTTON", "marketing:flash:detail", null, 106,
                marketing.getId(), null, null);
        menus.add(flashDetailButton);
        // 审计日志分别作为独立菜单页面，权限编码继续复用后端查询接口的鉴权规则。
        SysMenu loginLogs = ensureMenu("登录记录", "MENU", "system:audit:login", "/settings/login-logs", 2,
                settings.getId(), "LoginLogManagementView", "LogIn");
        normalizeMenu(loginLogs, "登录记录", "MENU", "system:audit:login", "/settings/login-logs", 2,
                settings.getId(), "LoginLogManagementView", "LogIn");
        SysMenu operationLogs = ensureMenu("操作审计", "MENU", "system:audit:operation", "/settings/operation-logs", 3,
                settings.getId(), "OperationLogManagementView", "ClipboardCheck");
        normalizeMenu(operationLogs, "操作审计", "MENU", "system:audit:operation", "/settings/operation-logs", 3,
                settings.getId(), "OperationLogManagementView", "ClipboardCheck");
        menus.add(loginLogs);
        menus.add(operationLogs);
        // RocketMQ 死信查询和人工重试权限挂在系统设置下，避免补偿接口被普通订单操作员误用。
        String[][] outboxButtons = {
                {"消息死信查询", "trade:outbox:query"}, {"消息死信重试", "trade:outbox:retry"}
        };
        for (String[] button : outboxButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 105);
            if (!settings.getId().equals(menu.getParentId())) {
                menu.setParentId(settings.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        // 发票查询和状态维护权限挂在财务目录下，供后台开票流程使用。
        String[][] invoiceButtons = {
                {"发票查询", "payment:invoice:query"}, {"发票状态维护", "payment:invoice:status"}
        };
        for (String[] button : invoiceButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 102);
            if (!finance.getId().equals(menu.getParentId())) {
                menu.setParentId(finance.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        // 对账差异处理权限挂在财务目录下，控制确认、忽略和重新匹配等人工操作。
        String[][] reconciliationButtons = {{"对账差异处理", "payment:transaction:manage"}};
        for (String[] button : reconciliationButtons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, 103);
            if (!finance.getId().equals(menu.getParentId())) {
                menu.setParentId(finance.getId());
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
        // 导出中心权限单独授权：查询、创建、下载三分，避免仅有列表查询权限的账号批量取走全量数据。
        ensureChildButtons(menus, new String[][]{
                {"导出任务查询", "export:task:query"},
                {"导出任务创建", "export:task:create"},
                {"导出文件下载", "export:task:download"}
        }, settings.getId(), 107);
        // 各业务数据的导出权限跟随所属模块，拥有查询权限不等于拥有导出权限。
        ensureChildButtons(menus, new String[][]{
                {"商品导出", "catalog:product:export"},
                {"订单导出", "trade:order:export"},
                {"会员导出", "member:user:export"}
        }, ecommerce.getId(), 107);
        ensureChildButtons(menus, new String[][]{{"优惠券导出", "marketing:coupon:export"}},
                marketing.getId(), 104);
        // 秒杀明细与预占记录两类导出共用一个权限点：详情页只有一个导出入口，拆成两个权限只会增加授权负担。
        ensureChildButtons(menus, new String[][]{{"秒杀活动导出", "marketing:flash:export"}},
                marketing.getId(), 105);
        ensureChildButtons(menus, new String[][]{{"库存流水导出", "inventory:stock:export"}},
                inventory.getId(), 106);
        ensureChildButtons(menus, new String[][]{{"资金流水导出", "payment:transaction:export"}},
                finance.getId(), 104);
        ensureChildButtons(menus, new String[][]{{"商品动销导出", "reporting:product:export"}},
                analytics.getId(), 101);
        ensureChildButtons(menus, new String[][]{
                {"登录日志导出", "system:audit:login:export"},
                {"操作日志导出", "system:audit:operation:export"}
        }, settings.getId(), 108);
        // AI 客服的权限点按 BUTTON 登记；ai:ticket:query 与 ai:faq:query 已经分别由「客服工单」
        // 与「知识库运营」两个菜单作为页面权限承载，这里不再重复登记，否则同一权限码会同时存在
        // 一条 MENU 与一条 BUTTON。查询与写入分开授权：只给查询的运营账号能看工单与知识库，
        // 但不能改工单状态、不能维护知识库，也拿不到同步与召回测试。
        ensureChildButtons(menus, new String[][]{
                {"客服工单处理", "ai:ticket:handle"},
                {"知识库问答保存", "ai:faq:save"},
                {"知识库问答删除", "ai:faq:delete"},
                {"知识库向量同步", "ai:knowledge:sync"}
        }, service.getId(), 109);
        return menus;
    }

    /**
     * 按父节点创建或复用一组按钮权限，返回值追加到菜单集合。
     *
     * @param menus 菜单集合
     * @param buttons 按钮定义，每项为「名称、权限编码」
     * @param parentId 父菜单ID
     * @param sort 排序号
     * @author Henfon
     * @date 2026-09-16
     */
    private void ensureChildButtons(List<SysMenu> menus, String[][] buttons, Long parentId, int sort) {
        for (String[] button : buttons) {
            SysMenu menu = ensureMenu(button[0], "BUTTON", button[1], null, sort);
            if (!parentId.equals(menu.getParentId())) {
                menu.setParentId(parentId);
                sysMenuMapper.updateById(menu);
            }
            menus.add(menu);
        }
    }

    /**
     * 按权限编码创建或读取菜单。
     *
     * @param name 菜单名称
     * @param type 菜单类型
     * @param permission 权限编码
     * @param route 路由地址
     * @param sort 排序号
     * @return 菜单实体
     * @author Henfon
     * @date 2026-08-29
     */
    private SysMenu ensureMenu(String name, String type, String permission, String route, int sort) {
        return ensureMenu(name, type, permission, route, sort, 0L, null, null);
    }

    /**
     * 按权限编码创建或读取指定父节点下的菜单。
     *
     * @param name 菜单名称
     * @param type 菜单类型
     * @param permission 权限编码
     * @param route 路由地址
     * @param sort 排序号
     * @param parentId 父菜单ID
     * @param component 前端组件名称
     * @param icon 图标名称
     * @return 菜单实体
     * @author Henfon
     * @date 2026-08-29
     */
    private SysMenu ensureMenu(String name, String type, String permission, String route, int sort,
                               Long parentId, String component, String icon) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(permission != null, SysMenu::getPermissionCode, permission)
                .eq(permission == null, SysMenu::getMenuName, name)
                .last("LIMIT 1");
        SysMenu menu = sysMenuMapper.selectOne(wrapper);
        if (menu != null) {
            if (icon != null && !icon.equals(menu.getIcon())) {
                // 初始化阶段补齐历史菜单的图标，避免旧库继续使用前端默认图标。
                menu.setIcon(icon);
                sysMenuMapper.updateById(menu);
            }
            return menu;
        }
        menu = new SysMenu();
        menu.setParentId(parentId == null ? 0L : parentId);
        menu.setMenuName(name);
        menu.setMenuType(type);
        menu.setRoutePath(route);
        menu.setComponent(component);
        menu.setIcon(icon);
        menu.setPermissionCode(permission);
        menu.setSortNo(sort);
        menu.setVisible(1);
        menu.setStatus(1);
        menu.setKeepAlive(1);
        sysMenuMapper.insert(menu);
        return menu;
    }

    /**
     * 将历史菜单记录同步为当前页面定义，兼容旧版本已经创建的同权限记录。
     *
     * @param menu 待同步菜单
     * @param name 菜单名称
     * @param type 菜单类型
     * @param permission 权限编码
     * @param route 路由地址
     * @param sort 排序号
     * @param parentId 父菜单ID
     * @param component 前端组件名称
     * @param icon 图标名称
     * @author Henfon
     * @date 2026-09-01
     * @description 仅在字段发生变化时更新数据库，避免每次启动产生无意义写入。
     */
    private void normalizeMenu(SysMenu menu, String name, String type, String permission, String route, int sort,
                               Long parentId, String component, String icon) {
        boolean changed = !Objects.equals(menu.getMenuName(), name)
                || !Objects.equals(menu.getMenuType(), type)
                || !Objects.equals(menu.getPermissionCode(), permission)
                || !Objects.equals(menu.getRoutePath(), route)
                || !Objects.equals(menu.getSortNo(), sort)
                || !Objects.equals(menu.getParentId(), parentId)
                || !Objects.equals(menu.getComponent(), component)
                || !Objects.equals(menu.getIcon(), icon);
        if (changed) {
            // 统一修正历史记录，确保动态菜单树和前端页面路由保持一致。
            menu.setMenuName(name);
            menu.setMenuType(type);
            menu.setPermissionCode(permission);
            menu.setRoutePath(route);
            menu.setSortNo(sort);
            menu.setParentId(parentId == null ? 0L : parentId);
            menu.setComponent(component);
            menu.setIcon(icon);
            sysMenuMapper.updateById(menu);
        }
    }

    /**
     * 为超级管理员绑定全部菜单权限。
     *
     * @param roleId 角色ID
     * @param menus 菜单集合
     * @author Henfon
     * @date 2026-08-29
     */
    private void bindRoleMenus(Long roleId, List<SysMenu> menus) {
        for (SysMenu menu : menus) {
            sysRoleMenuMapper.insertRelation(roleId, menu.getId());
        }
    }

    /**
     * 确保客服角色存在。
     *
     * <p>角色键取英文常量而不是中文名：名称是给人看的、随时可能被运营改，角色键一旦被
     * 代码引用就不能再动。这里按角色键幂等查找，名字改了也不会被重复创建。</p>
     *
     * @return 客服角色
     * @author Henfon
     * @date 2026-09-21
     */
    private SysRole ensureAgentRole() {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, 0L).eq(SysRole::getRoleKey, "CUSTOMER_SERVICE").last("LIMIT 1"));
        if (role != null) {
            return role;
        }
        role = new SysRole();
        role.setTenantId(0L);
        role.setRoleKey("CUSTOMER_SERVICE");
        role.setRoleName("客服专员");
        role.setRoleSort(10);
        role.setStatus(1);
        role.setDataScope("ALL");
        role.setDescription("负责买家在线咨询的实时接待与工单回复");
        sysRoleMapper.insert(role);
        return role;
    }

    /**
     * 给客服角色绑定接待相关的菜单与权限。
     *
     * <p>按权限编码匹配，而不是按菜单名或菜单 ID：菜单可以被改名、ID 在不同环境不一致，
     * 权限编码才是稳定的契约。父级目录不必显式绑定，菜单树查询会沿 parent_id 自动补齐。</p>
     *
     * @param roleId 客服角色ID
     * @param menus 全部菜单
     * @author Henfon
     * @date 2026-09-21
     */
    private void bindAgentMenus(Long roleId, List<SysMenu> menus) {
        for (SysMenu menu : menus) {
            String code = menu.getPermissionCode();
            if (code == null || code.isEmpty()) {
                continue;
            }
            for (String allowed : AGENT_PERMISSION_CODES) {
                if (allowed.equals(code)) {
                    sysRoleMenuMapper.insertRelation(roleId, menu.getId());
                    break;
                }
            }
        }
    }

    /**
     * 确保客服演示账号存在并绑定客服角色。
     *
     * @param deptId 默认部门ID
     * @param roleId 客服角色ID
     * @author Henfon
     * @date 2026-09-21
     */
    private void ensureAgentUser(Long deptId, Long roleId) {
        for (String[] preset : DEMO_AGENTS) {
            String username = preset[0];
            String realName = preset[1];
            SysUser agent = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getTenantId, 0L).eq(SysUser::getUsername, username).last("LIMIT 1"));
            if (agent == null) {
                agent = new SysUser();
                agent.setTenantId(0L);
                agent.setUsername(username);
                agent.setPasswordHash(passwordEncoder.encode("123456"));
                agent.setRealName(realName);
                agent.setNickname(realName);
                agent.setDeptId(deptId);
                agent.setStatus(1);
                agent.setUserType("ADMIN");
                agent.setRemark("在线客服演示账号，默认角色为客服专员");
                sysUserMapper.insert(agent);
            }
            // 已存在的账号不重置密码：本地把演示账号改过密码后再重启，不该被初始化流程改回去。
            sysUserRoleMapper.insertRelation(agent.getId(), roleId);
        }
    }

    /**
     * 确保默认管理员账号存在并绑定超级管理员角色。
     *
     * @param deptId 默认部门ID
     * @param roleId 超级管理员角色ID
     * @author Henfon
     * @date 2026-08-29
     */
    private void ensureAdminUser(Long deptId, Long roleId) {
        SysUser admin = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, 0L).eq(SysUser::getUsername, "admin").last("LIMIT 1"));
        if (admin == null) {
            admin = new SysUser();
            admin.setTenantId(0L);
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("123456"));
            admin.setRealName("系统管理员");
            admin.setNickname("管理员");
            admin.setDeptId(deptId);
            admin.setStatus(1);
            admin.setUserType("ADMIN");
            sysUserMapper.insert(admin);
        }
        sysUserRoleMapper.insertRelation(admin.getId(), roleId);
    }
}
