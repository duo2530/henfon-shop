import React, { useEffect, useMemo, useState } from 'react';
import {
  BadgePercent, BarChart3, Boxes, ChevronDown, ChevronRight, ClipboardCheck, Coins, DollarSign,
  FileText, Image as ImageIcon, LayoutDashboard, LineChart, Lock, LogOut,
  LogIn, Menu as MenuIcon, MessageSquare, Package, Shield, ShieldCheck,
  ShoppingBag, ShoppingCart, Settings, Sparkles, Ticket, Truck, UserCheck, Users,
  Warehouse, X, Zap
} from 'lucide-react';
import { useAdmin } from '../../context/AdminContext';
import { MenuItem, NavigationTab } from '../../types';
import { routeToTab } from '../../navigation/menuAdapter';

interface SidebarProps { mobileOpen?: boolean; onCloseMobile?: () => void; }
interface NavItem { id: NavigationTab; label: string; icon: React.ReactNode; badge?: number; }
interface NavGroup { id: string; label: string; icon: React.ReactNode; children?: NavItem[]; directTab?: NavigationTab; badge?: number; }

const iconMap: Record<string, React.ComponentType<{ className?: string }>> = {
  LayoutDashboard, ShoppingBag, Package, ShoppingCart, UserCheck, ShieldCheck, Shield,
  Menu: MenuIcon, Users, Lock, Ticket, Zap, Boxes, Truck, Warehouse, LogIn, ClipboardCheck,
  DollarSign, FileText, LineChart, BarChart3, Image: ImageIcon, MessageSquare, BadgePercent, Coins, Settings
};

function renderIcon(name?: string): React.ReactNode {
  const Icon = (name && iconMap[name]) || MenuIcon;
  return <Icon className="w-4 h-4" />;
}

function buildNavGroups(items: MenuItem[], pending: number, urgent: number): NavGroup[] {
  const visible = items.filter((item) => item.visible && item.status === 'active' && item.type !== 'button');
  const groups: NavGroup[] = [];
  visible.forEach((item) => {
    const tab = routeToTab(item.path);
    const children = (item.children || [])
      .filter((child) => child.visible && child.status === 'active' && child.type === 'menu')
      .map((child) => {
        const id = routeToTab(child.path);
        return id ? { id, label: child.title, icon: renderIcon(child.icon), badge: id === 'orders' && pending > 0 ? pending : undefined } : null;
      })
      .filter(Boolean) as NavItem[];
    if (tab) groups.push({ id: item.id, label: item.title, icon: renderIcon(item.icon), directTab: tab, badge: tab === 'dashboard' && urgent > 0 ? urgent : undefined });
    else if (children.length) groups.push({ id: item.id, label: item.title, icon: renderIcon(item.icon), children });
  });
  return groups;
}

export const Sidebar: React.FC<SidebarProps> = ({ mobileOpen, onCloseMobile }) => {
  const { currentTab, setCurrentTab, orders, todos, authorizedMenuItems, logout } = useAdmin();
  const pending = orders.filter((order) => order.status === 'pending_shipment').length;
  const urgent = todos.filter((todo) => todo.urgent).length;
  const navGroups = useMemo(() => buildNavGroups(authorizedMenuItems, pending, urgent), [authorizedMenuItems, pending, urgent]);
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>({});

  useEffect(() => {
    const active = navGroups.find((group) => group.children?.some((child) => child.id === currentTab));
    if (active) setExpandedGroups((prev) => ({ ...prev, [active.id]: true }));
  }, [currentTab, navGroups]);

  const selectTab = (tab: NavigationTab) => { setCurrentTab(tab); onCloseMobile?.(); };

  return <>
    {mobileOpen && <div className="fixed inset-0 bg-black/60 z-40 md:hidden backdrop-blur-xs" onClick={onCloseMobile} />}
    <aside className={`fixed top-0 left-0 h-full w-[240px] bg-white border-r border-[#E2E8F0] flex flex-col z-50 transition-transform duration-200 ease-in-out ${mobileOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'}`}>
      <div className="p-4 px-5 border-b border-[#E2E8F0] flex items-center justify-between">
        <div className="flex items-center gap-3"><div className="w-9 h-9 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-sm"><Sparkles className="w-5 h-5" /></div><div><h1 className="font-bold text-[16px] text-[#0F172A] tracking-tight">Henfon电商后台</h1><p className="text-[11px] text-slate-500 font-medium">E-Commerce RBAC OS</p></div></div>
        {onCloseMobile && <button onClick={onCloseMobile} aria-label="关闭导航菜单" className="md:hidden text-slate-400 hover:text-slate-700 p-1"><X className="w-5 h-5" /></button>}
      </div>
      <nav aria-label="后台主导航" className="flex-1 px-3 py-4 space-y-1.5 overflow-y-auto custom-scrollbar">
        {navGroups.map((group) => {
          if (group.directTab) { const active = currentTab === group.directTab; return <button key={group.id} onClick={() => selectTab(group.directTab!)} aria-current={active ? 'page' : undefined} className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-lg border-l-2 text-sm text-left transition-colors ${active ? 'text-[#1D4ED8] bg-[#EFF6FF] border-[#2563EB] font-semibold' : 'text-slate-600 border-transparent hover:bg-slate-100 hover:text-slate-900'}`}><span className="flex items-center gap-3">{group.icon}<span>{group.label}</span></span>{group.badge !== undefined && <span className="px-2 py-0.5 text-xs rounded-full bg-rose-500 text-white">{group.badge}</span>}</button>; }
          const expanded = Boolean(expandedGroups[group.id]); const hasActive = group.children?.some((child) => child.id === currentTab);
          return <div key={group.id} className="space-y-1 pt-1"><button onClick={() => setExpandedGroups((prev) => ({ ...prev, [group.id]: !expanded }))} aria-expanded={expanded} aria-controls={`nav-group-${group.id}`} className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-lg border-l-2 text-sm text-left transition-colors ${hasActive ? 'text-[#1D4ED8] bg-slate-50 border-transparent font-semibold' : 'text-slate-600 border-transparent hover:bg-slate-100 hover:text-slate-900'}`}><span className="flex items-center gap-3">{group.icon}<span>{group.label}</span></span>{expanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}</button>{expanded && <div id={`nav-group-${group.id}`} className="pl-4 pr-1 py-1 space-y-1 border-l-2 border-slate-200 ml-4.5">{group.children?.map((child) => { const active = currentTab === child.id; return <button key={child.id} onClick={() => selectTab(child.id)} aria-current={active ? 'page' : undefined} className={`w-full flex items-center justify-between px-3 py-2 rounded-md border-l-2 text-xs text-left transition-colors ${active ? 'text-[#1D4ED8] bg-[#EFF6FF] border-[#2563EB] font-semibold' : 'text-slate-500 border-transparent hover:bg-slate-100 hover:text-slate-900'}`}><span className="flex items-center gap-2.5">{child.icon}<span>{child.label}</span></span>{child.badge !== undefined && <span className="px-1.5 text-[11px] font-bold rounded-full bg-rose-500 text-white">{child.badge}</span>}</button>; })}</div>}</div>;
        })}
        {!navGroups.length && <div className="px-4 py-8 text-xs text-slate-400">暂无可用菜单</div>}
      </nav>
      <div className="px-3 py-3 border-t border-[#E2E8F0] space-y-1"><button onClick={logout} aria-label="退出管理系统" className="w-full flex items-center gap-3 px-3.5 py-2 rounded-lg text-xs text-slate-500 hover:bg-rose-50 hover:text-rose-600 transition-colors"><LogOut className="w-4 h-4" /><span>退出管理系统</span></button></div>
    </aside>
  </>;
};
