package com.henfon.shop.identity.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.identity.entity.SysDept;
import com.henfon.shop.identity.entity.SysMenu;
import com.henfon.shop.identity.entity.SysRole;
import com.henfon.shop.identity.entity.SysUser;
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

/**
 * 身份模块基础数据初始化器。
 *
 * <p>首次启动自动创建平台部门、超级管理员角色、系统菜单和 admin 账号，重复启动不会重复插入。</p>
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Component
@Order(1)
public class IdentityDataInitializer implements ApplicationRunner {

    private final SysDeptMapper sysDeptMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
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
     * @param passwordEncoder 密码编码器
     * @author Henfon
     * @date 2026-08-29
     */
    public IdentityDataInitializer(SysDeptMapper sysDeptMapper, SysRoleMapper sysRoleMapper,
                                   SysMenuMapper sysMenuMapper, SysUserMapper sysUserMapper,
                                   SysUserRoleMapper sysUserRoleMapper, SysRoleMenuMapper sysRoleMenuMapper,
                                   PasswordEncoder passwordEncoder) {
        this.sysDeptMapper = sysDeptMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
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
        SysMenu root = ensureMenu("系统管理", "DIRECTORY", null, "system", 10);
        menus.add(root);
        menus.add(ensureMenu("用户管理", "MENU", "system:user:query", "/system/users", 10));
        menus.add(ensureMenu("角色管理", "MENU", "system:role:query", "/system/roles", 20));
        menus.add(ensureMenu("菜单管理", "MENU", "system:menu:query", "/system/menus", 30));
        menus.add(ensureMenu("数据权限", "MENU", "system:data-rule:query", "/system/data-rules", 40));
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
        SysMenu settings = ensureMenu("系统与参数设置", "MENU", "system:config:view", "/settings", 8,
                0L, "SettingsView", "Settings");
        menus.addAll(List.of(dashboard, ecommerce, marketing, inventory, finance, analytics, content, settings));

        menus.add(ensureMenu("商品管理", "MENU", "catalog:product:query", "/ecommerce/products", 1,
                ecommerce.getId(), "ProductManagementView", "Package"));
        menus.add(ensureMenu("订单履约", "MENU", "trade:order:query", "/ecommerce/orders", 2,
                ecommerce.getId(), "OrderManagementView", "ShoppingCart"));
        SysMenu member = ensureMenu("商城会员", "MENU", "member:user:query", "/ecommerce/customers", 3,
                ecommerce.getId(), "UserManagementView", "UserCheck");
        menus.add(member);
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

        // 订单操作按钮用于控制后台发货、取消、备注和退款等写权限。
        String[][] tradeButtons = {
                {"订单发货", "trade:order:ship"}, {"订单取消", "trade:order:cancel"},
                {"订单备注", "trade:order:remark"}, {"订单退款", "trade:order:refund"},
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
                {"库存盘点导入", "inventory:stocktake:import"}
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
        return menus;
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
