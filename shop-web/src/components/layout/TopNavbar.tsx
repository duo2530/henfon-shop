import React, { useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { containsMenuTab } from '../../navigation/menuAdapter';
import { ExportCenter } from './ExportCenter';
import { 
  Menu, 
  Search, 
  Bell, 
  HelpCircle, 
  User, 
  ShieldCheck, 
  Sparkles,
  ExternalLink
} from 'lucide-react';
import { useBodyScrollLock } from '../../hooks/useBodyScrollLock';

interface TopNavbarProps {
  onOpenMobileMenu: () => void;
  onOpenNotifications: () => void;
}

export const TopNavbar: React.FC<TopNavbarProps> = ({ 
  onOpenMobileMenu, 
  onOpenNotifications 
}) => {
  const { searchQuery, setSearchQuery, notificationUnreadCount, setCurrentTab, showToast, authorizedMenuItems, currentUser, products, orders, users } = useAdmin();
  const [profileOpen, setProfileOpen] = useState(false);
  const [helpModalOpen, setHelpModalOpen] = useState(false);
  const [avatarLoadFailed, setAvatarLoadFailed] = useState(false);

  const hasSettingsMenu = containsMenuTab(authorizedMenuItems, 'settings');
  const displayName = currentUser?.realName || currentUser?.username || '管理员';
  const permissionLabel = currentUser?.permissions?.length ? `已授权 ${currentUser.permissions.length} 项` : '暂无权限';
  const avatarUrl = currentUser?.avatarUrl?.trim();
  const avatarInitial = displayName.slice(0, 1).toUpperCase();

  const handleGlobalSearchKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key !== 'Enter') return;
    const keyword = searchQuery.trim().toLowerCase();
    if (!keyword) return;
    const productMatched = products.some((item) => item.name.toLowerCase().includes(keyword) || item.sku.toLowerCase().includes(keyword));
    const orderMatched = orders.some((item) => item.orderNumber.toLowerCase().includes(keyword) || item.customerName.toLowerCase().includes(keyword) || item.customerPhone.includes(keyword));
    const userMatched = users.some((item) => item.name.toLowerCase().includes(keyword) || item.phone.includes(keyword) || item.userCode.toLowerCase().includes(keyword) || item.email.toLowerCase().includes(keyword));
    const target: 'products' | 'orders' | 'users' = productMatched ? 'products' : orderMatched ? 'orders' : userMatched ? 'users' : 'products';
    setCurrentTab(target);
    showToast(productMatched || orderMatched || userMatched ? '已定位到匹配的业务列表' : '未找到精确匹配，已打开商品列表供筛选', 'info');
  };

  useEffect(() => {
    // 管理员切换头像后清理旧的失败状态，立即尝试加载新地址。
    setAvatarLoadFailed(false);
  }, [avatarUrl]);

  // 弹层打开期间锁住底层文档滚动，避免出现滚动穿透。
  useBodyScrollLock(profileOpen || helpModalOpen);
  return (
    <>
      <header
        id="top-navbar"
        className="fixed top-0 right-0 w-full md:w-[calc(100%-240px)] h-[56px] bg-[#F8FAFC] border-b border-[#E2E8F0] flex justify-between items-center px-4 md:px-6 z-30 shadow-2xs"
      >
        {/* Left Side: Mobile Menu Button & Brand / Search Bar */}
        <div className="flex items-center gap-3 flex-1 max-w-lg">
          <button
            id="mobile-menu-toggle"
            onClick={onOpenMobileMenu}
            className="md:hidden text-gray-700 hover:bg-gray-200 p-2 rounded-lg transition-colors cursor-pointer"
            aria-label="Open Navigation Menu"
          >
            <Menu className="w-5 h-5" />
          </button>

          {/* Global Search Bar */}
          <div className="relative w-full max-w-sm hidden sm:block">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
            <input
              id="global-search-input"
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={handleGlobalSearchKeyDown}
              placeholder="搜索商品、订单编号、客户姓名..."
              className="w-full h-[36px] pl-9 pr-4 rounded-md bg-[#F1F5F9] border border-transparent focus:border-[#2563EB] focus:bg-white focus:ring-2 focus:ring-[#2563EB]/20 text-sm text-gray-800 placeholder-gray-400 transition-all outline-none"
            />
          </div>

          <div className="md:hidden font-semibold text-gray-800 text-sm truncate">
            Henfon电商后台
          </div>
        </div>

        {/* Right Side Actions */}
        <div className="flex items-center gap-2 sm:gap-3">
          {/* Export Center: asynchronous export tasks and file downloads */}
          <ExportCenter />

          {/* Notifications Button */}
          <button
            id="btn-notifications-trigger"
            onClick={onOpenNotifications}
            className="relative text-gray-600 hover:text-gray-900 hover:bg-gray-200/70 p-2 rounded-full transition-colors cursor-pointer"
            aria-label={notificationUnreadCount > 0 ? `通知中心，${notificationUnreadCount} 条未读` : '通知中心'}
          >
            <Bell className="w-5 h-5" />
            {notificationUnreadCount > 0 && (
              <span
                className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 rounded-full bg-red-500 text-white text-[10px] font-semibold leading-none flex items-center justify-center ring-2 ring-[#F8FAFC]"
                aria-hidden="true"
              >
                {notificationUnreadCount > 99 ? '99+' : notificationUnreadCount}
              </span>
            )}
          </button>

          {/* Help / Docs Button */}
          <button
            id="btn-help-trigger"
            onClick={() => setHelpModalOpen(true)}
            className="text-gray-600 hover:text-gray-900 hover:bg-gray-200/70 p-2 rounded-full transition-colors cursor-pointer"
            aria-label="Help and Operations Guide"
          >
            <HelpCircle className="w-5 h-5" />
          </button>

          {/* Administrator Profile */}
          <div className="relative">
            <button
              id="btn-admin-profile"
              onClick={() => setProfileOpen(!profileOpen)}
              aria-label="打开管理员菜单"
              aria-expanded={profileOpen}
              aria-haspopup="menu"
              className="flex items-center gap-2 p-1 rounded-full hover:ring-2 hover:ring-blue-500/30 transition-all cursor-pointer"
            >
              <div className="w-8 h-8 rounded-full overflow-hidden border border-gray-300 bg-gray-200 shadow-2xs">
                {avatarUrl && !avatarLoadFailed ? (
                  <img
                    src={avatarUrl}
                    alt={`${displayName}头像`}
                    className="w-full h-full object-cover"
                    onError={() => setAvatarLoadFailed(true)}
                  />
                ) : (
                  <div className="w-full h-full flex items-center justify-center bg-blue-100 text-blue-700 text-xs font-bold" aria-label="管理员默认头像">
                    {avatarInitial || <User className="w-4 h-4" />}
                  </div>
                )}
              </div>
            </button>

            {/* Profile Dropdown Menu */}
            {profileOpen && (
              <>
                <div
                  className="fixed inset-0 z-40"
                  onClick={() => setProfileOpen(false)}
                />
                <div className="absolute right-0 mt-2 w-56 bg-white rounded-xl shadow-lg border border-[#E2E8F0] p-2 z-50 animate-in fade-in-50 zoom-in-95">
                  <div className="px-3 py-2 border-b border-gray-100 mb-1">
                    <p className="text-sm font-semibold text-gray-900">{displayName}</p>
                    <p className="text-xs text-gray-500 truncate">{currentUser?.username || '管理员账号'}</p>
                    <div className="mt-1.5 flex items-center gap-1 text-[11px] text-blue-700 bg-blue-50 px-2 py-0.5 rounded font-medium w-fit">
                      <ShieldCheck className="w-3.5 h-3.5" />
                      <span>{permissionLabel}</span>
                    </div>
                  </div>

                  {hasSettingsMenu && (
                    <button
                      onClick={() => {
                        setCurrentTab('settings');
                        setProfileOpen(false);
                      }}
                      className="w-full flex items-center gap-2.5 px-3 py-2 text-xs font-medium text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
                    >
                      <User className="w-4 h-4 text-gray-400" />
                      <span>个人中心与偏好</span>
                    </button>
                  )}

                  <button
                    onClick={() => {
                      showToast('快捷刷新运营数据缓存成功', 'success');
                      setProfileOpen(false);
                    }}
                    className="w-full flex items-center gap-2.5 px-3 py-2 text-xs font-medium text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
                  >
                    <Sparkles className="w-4 h-4 text-blue-500" />
                    <span>清理并刷新缓存</span>
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Operations Help Guide Modal */}
      {helpModalOpen && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-md w-full p-6 border border-gray-200 shadow-xl">
            <div className="flex items-center justify-between pb-3 border-b border-gray-100">
              <div className="flex items-center gap-2">
                <div className="p-1.5 bg-blue-100 text-blue-700 rounded-lg">
                  <HelpCircle className="w-5 h-5" />
                </div>
                <h3 className="text-base font-semibold text-gray-900">电商运营系统操作指南</h3>
              </div>
              <button
                onClick={() => setHelpModalOpen(false)}
                className="text-gray-400 hover:text-gray-600 p-1 rounded"
                aria-label="关闭操作指南"
              >
                ✕
              </button>
            </div>

            <div className="py-4 space-y-3 text-sm text-gray-600">
              <div className="p-3 bg-gray-50 rounded-lg border border-gray-100">
                <p className="font-medium text-gray-800 mb-1">📊 工作台看板 (Dashboard)</p>
                <p className="text-xs text-gray-500">
                  实时监控今日营业额、待处理订单与待办事项，点击待办事项可直达相关业务处理。
                </p>
              </div>
              <div className="p-3 bg-gray-50 rounded-lg border border-gray-100">
                <p className="font-medium text-gray-800 mb-1">📦 商品管理 (Products)</p>
                <p className="text-xs text-gray-500">
                  支持多条件筛选、即时状态上下架切换、添加商品、编辑库存与价格等。
                </p>
              </div>
              <div className="p-3 bg-gray-50 rounded-lg border border-gray-100">
                <p className="font-medium text-gray-800 mb-1">🛒 订单履约 (Orders)</p>
                <p className="text-xs text-gray-500">
                  按不同生命周期阶段（待付款/待发货/已完成）统一管理并快速打印填报运单号发货。
                </p>
              </div>
              <div className="p-3 bg-gray-50 rounded-lg border border-gray-100">
                <p className="font-medium text-gray-800 mb-1">👥 用户与客户 (Users)</p>
                <p className="text-xs text-gray-500">
                  查看客户消费画像、等级积分，提供账户冻结/解冻与安全管理控制。
                </p>
              </div>
            </div>

            <div className="pt-3 border-t border-gray-100 flex justify-end">
              <button
                onClick={() => setHelpModalOpen(false)}
                className="px-4 py-2 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors"
              >
                我知道了
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
