/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { AdminProvider, useAdmin } from './context/AdminContext';
import { Sidebar } from './components/layout/Sidebar';
import { TopNavbar } from './components/layout/TopNavbar';
import { DashboardView } from './components/dashboard/DashboardView';
import { ProductManagementView } from './components/products/ProductManagementView';
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
import { NotificationDrawer } from './components/common/NotificationDrawer';
import { ToastContainer } from './components/common/ToastContainer';
import { AdminLogin } from './components/auth/AdminLogin';

const AdminLayoutContent: React.FC = () => {
  const { currentTab } = useAdmin();
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  return (
    <div className="min-h-screen bg-[#F8FAFC] text-[#191C1E] font-sans antialiased flex flex-col">
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
        className="flex-1 md:ml-[240px] mt-[56px] p-4 sm:p-6 lg:p-8 overflow-x-hidden min-h-[calc(100vh-56px)]"
      >
        {currentTab === 'dashboard' && <DashboardView />}
        {currentTab === 'products' && <ProductManagementView />}
        {currentTab === 'orders' && <OrderManagementView />}
        {currentTab === 'users' && <UserManagementView />}
        
        {/* Marketing Views */}
        {currentTab === 'coupons' && <CouponManagementView />}
        {currentTab === 'flash_sales' && <FlashSaleManagementView />}

        {/* Inventory Views */}
        {currentTab === 'inventory_stock' && <WarehouseStockView />}
        {currentTab === 'inventory_suppliers' && <SupplierManagementView />}

        {/* Finance Views */}
        {currentTab === 'finance_transactions' && <TransactionReconciliationView />}
        {currentTab === 'finance_invoices' && <InvoiceManagementView />}

        {/* Analytics Views */}
        {currentTab === 'analytics_overview' && <AnalyticsOverviewView />}
        {currentTab === 'analytics_products' && <ProductAnalyticsView />}

        {/* Content Views */}
        {currentTab === 'content_banners' && <BannerManagementView />}
        {currentTab === 'content_reviews' && <ReviewManagementView />}

        {/* RBAC & System */}
        {currentTab === 'roles' && <RoleManagementView />}
        {currentTab === 'menus' && <MenuManagementView />}
        {currentTab === 'system_users' && <SystemUserManagementView />}
        {currentTab === 'authorization' && <AuthorizationView />}
        {currentTab === 'data_permissions' && <DataPermissionView />}
        {currentTab === 'settings' && <SettingsView />}
      </main>
    </div>
  );
};

export default function App() {
  return (
    <AdminProvider>
      <AuthenticatedApp />
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
