/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useEffect, useState } from 'react';
import { AdminProvider, useAdmin } from './context/AdminContext';
import { ExportCenterProvider } from './context/ExportCenterContext';
import { Sidebar } from './components/layout/Sidebar';
import { TopNavbar } from './components/layout/TopNavbar';
import { DashboardView } from './components/dashboard/DashboardView';
import { ProductManagementView } from './components/products/ProductManagementView';
import { CategoryManagementView } from './components/products/CategoryManagementView';
import { OrderManagementView } from './components/orders/OrderManagementView';
import { UserManagementView } from './components/users/UserManagementView';
import { RoleManagementView } from './components/rbac/RoleManagementView';
import { MenuManagementView } from './components/rbac/MenuManagementView';
import { SystemUserManagementView } from './components/rbac/SystemUserManagementView';
import { AuthorizationView } from './components/rbac/AuthorizationView';
import { DataPermissionView } from './components/rbac/DataPermissionView';
import { CouponManagementView } from './components/marketing/CouponManagementView';
import { FlashSaleManagementView } from './components/marketing/FlashSaleManagementView';
import { WarehouseStockView } from './components/inventory/WarehouseStockView';
import { SupplierManagementView } from './components/inventory/SupplierManagementView';
import { TransactionReconciliationView } from './components/finance/TransactionReconciliationView';
import { InvoiceManagementView } from './components/finance/InvoiceManagementView';
import { AnalyticsOverviewView } from './components/analytics/AnalyticsOverviewView';
import { ProductAnalyticsView } from './components/analytics/ProductAnalyticsView';
import { BannerManagementView } from './components/content/BannerManagementView';
import { ReviewManagementView } from './components/content/ReviewManagementView';
import { SettingsView } from './components/settings/SettingsView';
import { LoginLogManagementView } from './components/settings/LoginLogManagementView';
import { OperationLogManagementView } from './components/settings/OperationLogManagementView';
import { NotificationDrawer } from './components/common/NotificationDrawer';
import { ToastContainer } from './components/common/ToastContainer';
import { AdminLogin } from './components/auth/AdminLogin';
import { PermissionDenied } from './components/common/PermissionGate';
import { containsMenuTab, firstMenuTab } from './navigation/menuAdapter';

const AdminLayoutContent: React.FC = () => {
  const { currentTab, authorizedMenuItems, setCurrentTab } = useAdmin();
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  useEffect(() => {
    // 菜单加载完成后纠正可能来自旧状态/深链的未授权页面。
    if (authorizedMenuItems.length > 0 && !containsMenuTab(authorizedMenuItems, currentTab)) {
      const fallbackTab = firstMenuTab(authorizedMenuItems);
      if (fallbackTab) setCurrentTab(fallbackTab);
    }
  }, [authorizedMenuItems, currentTab, setCurrentTab]);

  // 菜单加载完成后严格按服务端授权判断；空菜单代表账号没有可访问页面。
  const hasCurrentTab = authorizedMenuItems.length > 0 && containsMenuTab(authorizedMenuItems, currentTab);

  return (
    <div className="min-h-screen bg-[#F8FAFC] text-[#191C1E] font-sans antialiased flex flex-col">
      <a
        href="#main-content-canvas"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-2 focus:z-[100] focus:rounded-md focus:bg-blue-600 focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-white"
      >
        跳转到主要内容
      </a>
      {/* Toast Feedback */}
      <ToastContainer />

      {/* Persistent Left Sidebar */}
      <Sidebar
        mobileOpen={mobileSidebarOpen}
        onCloseMobile={() => setMobileSidebarOpen(false)}
      />

      {/* Top Navbar */}
      <TopNavbar
        onOpenMobileMenu={() => setMobileSidebarOpen(true)}
        onOpenNotifications={() => setNotificationsOpen(true)}
      />

      {/* Notification Drawer */}
      <NotificationDrawer
        open={notificationsOpen}
        onClose={() => setNotificationsOpen(false)}
      />

      {/* Main Content Area */}
      <main
        id="main-content-canvas"
        tabIndex={-1}
        aria-label="后台主要内容"
        className="flex-1 md:ml-[240px] mt-[56px] p-4 sm:p-6 lg:p-8 overflow-x-hidden min-h-[calc(100vh-56px)]"
      >
        {!hasCurrentTab && <PermissionDenied />}
        {hasCurrentTab && currentTab === 'dashboard' && <DashboardView />}
        {hasCurrentTab && currentTab === 'products' && <ProductManagementView />}
        {hasCurrentTab && currentTab === 'categories' && <CategoryManagementView />}
        {hasCurrentTab && currentTab === 'orders' && <OrderManagementView />}
        {hasCurrentTab && currentTab === 'users' && <UserManagementView />}
        
        {/* Marketing Views */}
        {hasCurrentTab && currentTab === 'coupons' && <CouponManagementView />}
        {hasCurrentTab && currentTab === 'flash_sales' && <FlashSaleManagementView />}

        {/* Inventory Views */}
        {hasCurrentTab && currentTab === 'inventory_stock' && <WarehouseStockView />}
        {hasCurrentTab && currentTab === 'inventory_suppliers' && <SupplierManagementView />}

        {/* Finance Views */}
        {hasCurrentTab && currentTab === 'finance_transactions' && <TransactionReconciliationView />}
        {hasCurrentTab && currentTab === 'finance_invoices' && <InvoiceManagementView />}

        {/* Analytics Views */}
        {hasCurrentTab && currentTab === 'analytics_overview' && <AnalyticsOverviewView />}
        {hasCurrentTab && currentTab === 'analytics_products' && <ProductAnalyticsView />}

        {/* Content Views */}
        {hasCurrentTab && currentTab === 'content_banners' && <BannerManagementView />}
        {hasCurrentTab && currentTab === 'content_reviews' && <ReviewManagementView />}

        {/* RBAC & System */}
        {hasCurrentTab && currentTab === 'roles' && <RoleManagementView />}
        {hasCurrentTab && currentTab === 'menus' && <MenuManagementView />}
        {hasCurrentTab && currentTab === 'system_users' && <SystemUserManagementView />}
        {hasCurrentTab && currentTab === 'authorization' && <AuthorizationView />}
        {hasCurrentTab && currentTab === 'data_permissions' && <DataPermissionView />}
        {hasCurrentTab && currentTab === 'settings' && <SettingsView />}
        {hasCurrentTab && currentTab === 'login_logs' && <LoginLogManagementView />}
        {hasCurrentTab && currentTab === 'operation_logs' && <OperationLogManagementView />}
      </main>
    </div>
  );
};

export default function App() {
  return (
    <AdminProvider>
      {/* 下载中心依赖登录态与权限，必须嵌套在 AdminProvider 内部。 */}
      <ExportCenterProvider>
        <AuthenticatedApp />
      </ExportCenterProvider>
    </AdminProvider>
  );
}

const AuthenticatedApp: React.FC = () => {
  const { authLoading, isAuthenticated } = useAdmin();
  if (authLoading) {
    return <div className="min-h-screen flex items-center justify-center text-slate-500">正在加载后台权限...</div>;
  }
  return isAuthenticated ? <AdminLayoutContent /> : <AdminLogin />;
};
