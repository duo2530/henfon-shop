import React, { useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { MenuItem, MenuType } from '../../types';
import { 
  Menu as MenuIcon, 
  Plus, 
  Search, 
  ChevronRight, 
  ChevronDown, 
  Folder, 
  FileText, 
  MousePointerClick, 
  Edit3, 
  Trash2, 
  CheckCircle2, 
  XCircle, 
  X,
  Eye,
  EyeOff,
  Sparkles,
  Layers,
  LayoutDashboard,
  ShoppingBag,
  Package,
  ShoppingCart,
  UserCheck,
  ShieldCheck,
  Shield,
  Users,
  KeyRound,
  Lock,
  Settings
} from 'lucide-react';

export const MenuManagementView: React.FC = () => {
  const { menuItems, addMenuItem, updateMenuItem, deleteMenuItem, confirm } = useAdmin();

  const [searchKeyword, setSearchKeyword] = useState('');
  const [expandedRowIds, setExpandedRowIds] = useState<Record<string, boolean>>({
    'menu-ecommerce': true,
    'menu-prod-mgmt': true,
    'menu-order-mgmt': true,
    'menu-customer-mgmt': true,
    'menu-system-rbac': true,
    'menu-roles': true,
    'menu-menu-mgmt': true,
    'menu-sys-users': true
  });

  // Modal states
  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null);
  const [editingItem, setEditingItem] = useState<MenuItem | null>(null);
  const [parentTargetId, setParentTargetId] = useState<string | null>(null);

  const [formData, setFormData] = useState({
    parentId: null as string | null,
    title: '',
    type: 'menu' as MenuType,
    icon: 'FileText',
    path: '',
    component: '',
    permission: '',
    sort: 1,
    visible: true,
    status: 'active' as 'active' | 'inactive'
  });

  const toggleRow = (id: string) => {
    setExpandedRowIds(prev => ({ ...prev, [id]: !prev[id] }));
  };

  const handleExpandAll = () => {
    const allIds: Record<string, boolean> = {};
    const collectIds = (items: MenuItem[]) => {
      items.forEach(item => {
        allIds[item.id] = true;
        if (item.children) collectIds(item.children);
      });
    };
    collectIds(menuItems);
    setExpandedRowIds(allIds);
  };

  const handleCollapseAll = () => {
    setExpandedRowIds({});
  };

  const handleOpenCreateTop = () => {
    setEditingItem(null);
    setParentTargetId(null);
    setFormData({
      parentId: null,
      title: '',
      type: 'directory',
      icon: 'Folder',
      path: '/new-module',
      component: '',
      permission: '',
      sort: menuItems.length + 1,
      visible: true,
      status: 'active'
    });
    setModalMode('create');
  };

  const handleOpenCreateChild = (parent: MenuItem) => {
    setEditingItem(null);
    setParentTargetId(parent.id);
    setFormData({
      parentId: parent.id,
      title: '',
      type: parent.type === 'directory' ? 'menu' : 'button',
      icon: parent.type === 'directory' ? 'FileText' : '',
      path: parent.type === 'directory' ? `${parent.path || ''}/sub` : '',
      component: '',
      permission: '',
      sort: (parent.children?.length || 0) + 1,
      visible: true,
      status: 'active'
    });
    setModalMode('create');
  };

  const handleOpenEdit = (item: MenuItem) => {
    setEditingItem(item);
    setParentTargetId(item.parentId);
    setFormData({
      parentId: item.parentId,
      title: item.title,
      type: item.type,
      icon: item.icon || '',
      path: item.path || '',
      component: item.component || '',
      permission: item.permission || '',
      sort: item.sort,
      visible: item.visible,
      status: item.status
    });
    setModalMode('edit');
  };

  const handleSaveForm = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.title.trim()) return;

    if (modalMode === 'create') {
      addMenuItem({
        parentId: parentTargetId,
        title: formData.title.trim(),
        type: formData.type,
        icon: formData.icon.trim() || undefined,
        path: formData.path.trim() || undefined,
        component: formData.component.trim() || undefined,
        permission: formData.permission.trim() || undefined,
        sort: Number(formData.sort) || 1,
        visible: formData.visible,
        status: formData.status
      });
    } else if (modalMode === 'edit' && editingItem) {
      updateMenuItem(editingItem.id, {
        title: formData.title.trim(),
        type: formData.type,
        icon: formData.icon.trim() || undefined,
        path: formData.path.trim() || undefined,
        component: formData.component.trim() || undefined,
        permission: formData.permission.trim() || undefined,
        sort: Number(formData.sort) || 1,
        visible: formData.visible,
        status: formData.status
      });
    }
    setModalMode(null);
  };

  // Helper to render icon by name string
  const renderMenuIcon = (iconName?: string) => {
    switch (iconName) {
      case 'LayoutDashboard': return <LayoutDashboard className="w-4 h-4 text-blue-500" />;
      case 'ShoppingBag': return <ShoppingBag className="w-4 h-4 text-emerald-500" />;
      case 'Package': return <Package className="w-4 h-4 text-sky-500" />;
      case 'ShoppingCart': return <ShoppingCart className="w-4 h-4 text-amber-500" />;
      case 'UserCheck': return <UserCheck className="w-4 h-4 text-indigo-500" />;
      case 'ShieldCheck': return <ShieldCheck className="w-4 h-4 text-rose-500" />;
      case 'Shield': return <Shield className="w-4 h-4 text-purple-500" />;
      case 'Menu': return <MenuIcon className="w-4 h-4 text-teal-500" />;
      case 'Users': return <Users className="w-4 h-4 text-blue-600" />;
      case 'KeyRound': return <KeyRound className="w-4 h-4 text-amber-600" />;
      case 'Lock': return <Lock className="w-4 h-4 text-purple-600" />;
      case 'Settings': return <Settings className="w-4 h-4 text-slate-500" />;
      default: return <FileText className="w-4 h-4 text-slate-400" />;
    }
  };

  // Flatten and filter menu tree
  const renderTreeRows = (items: MenuItem[], level: number = 0): React.ReactNode => {
    return items.map((item) => {
      const hasChildren = item.children && item.children.length > 0;
      const isExpanded = !!expandedRowIds[item.id];

      const matchesSearch = 
        !searchKeyword ||
        item.title.toLowerCase().includes(searchKeyword.toLowerCase()) ||
        (item.permission && item.permission.toLowerCase().includes(searchKeyword.toLowerCase())) ||
        (item.path && item.path.toLowerCase().includes(searchKeyword.toLowerCase()));

      return (
        <React.Fragment key={item.id}>
          {matchesSearch && (
            <tr className="hover:bg-slate-50/80 transition-colors border-b border-slate-100">
              {/* Menu Title & Hierarchy */}
              <td className="px-5 py-3.5">
                <div className="flex items-center" style={{ paddingLeft: `${level * 24}px` }}>
                  {hasChildren ? (
                    <button
                      onClick={() => toggleRow(item.id)}
                      className="p-1 hover:bg-slate-200 rounded mr-1.5 text-slate-500 transition-colors"
                    >
                      {isExpanded ? (
                        <ChevronDown className="w-4 h-4" />
                      ) : (
                        <ChevronRight className="w-4 h-4" />
                      )}
                    </button>
                  ) : (
                    <span className="w-6 inline-block" />
                  )}

                  <div className="flex items-center gap-2">
                    {item.type === 'directory' && <Folder className="w-4 h-4 text-amber-500" />}
                    {item.type === 'menu' && renderMenuIcon(item.icon)}
                    {item.type === 'button' && <MousePointerClick className="w-4 h-4 text-indigo-500" />}
                    
                    <span className={`text-sm ${
                      item.type === 'directory' 
                        ? 'font-bold text-slate-900' 
                        : item.type === 'menu' 
                        ? 'font-semibold text-slate-800' 
                        : 'font-normal text-slate-600'
                    }`}>
                      {item.title}
                    </span>
                  </div>
                </div>
              </td>

              {/* Type Badge */}
              <td className="px-5 py-3.5">
                <span className={`inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-medium ${
                  item.type === 'directory' 
                    ? 'bg-amber-50 text-amber-700 border border-amber-200' 
                    : item.type === 'menu' 
                    ? 'bg-blue-50 text-blue-700 border border-blue-200' 
                    : 'bg-indigo-50 text-indigo-700 border border-indigo-200'
                }`}>
                  {item.type === 'directory' && '目录 (Directory)'}
                  {item.type === 'menu' && '菜单 (Menu)'}
                  {item.type === 'button' && '按钮/权限 (Action)'}
                </span>
              </td>

              {/* Path / Component */}
              <td className="px-5 py-3.5 text-xs font-mono text-slate-600">
                {item.path || '-'}
              </td>

              {/* Permission Code */}
              <td className="px-5 py-3.5">
                {item.permission ? (
                  <span className="text-xs font-mono px-2 py-0.5 rounded bg-slate-100 text-slate-700 border border-slate-200">
                    {item.permission}
                  </span>
                ) : (
                  <span className="text-xs text-slate-400">-</span>
                )}
              </td>

              {/* Sort */}
              <td className="px-5 py-3.5 text-xs font-mono text-slate-500">
                {item.sort}
              </td>

              {/* Visibility */}
              <td className="px-5 py-3.5">
                <span className={`inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded font-medium ${
                  item.visible ? 'text-emerald-700 bg-emerald-50' : 'text-slate-500 bg-slate-100'
                }`}>
                  {item.visible ? <Eye className="w-3 h-3" /> : <EyeOff className="w-3 h-3" />}
                  {item.visible ? '显示' : '隐藏'}
                </span>
              </td>

              {/* Status */}
              <td className="px-5 py-3.5">
                <span className={`inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-medium ${
                  item.status === 'active' ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'
                }`}>
                  {item.status === 'active' ? (
                    <CheckCircle2 className="w-3 h-3" />
                  ) : (
                    <XCircle className="w-3 h-3" />
                  )}
                  {item.status === 'active' ? '正常' : '禁用'}
                </span>
              </td>

              {/* Actions */}
              <td className="px-5 py-3.5 text-right">
                <div className="flex items-center justify-end gap-1">
                  {item.type !== 'button' && (
                    <button
                      onClick={() => handleOpenCreateChild(item)}
                      title="添加子项"
                      className="p-1.5 text-blue-600 hover:bg-blue-50 rounded-md transition-colors"
                    >
                      <Plus className="w-4 h-4" />
                    </button>
                  )}
                  <button
                    onClick={() => handleOpenEdit(item)}
                    title="编辑配置"
                    className="p-1.5 text-slate-600 hover:bg-slate-100 rounded-md transition-colors"
                  >
                    <Edit3 className="w-4 h-4" />
                  </button>
                  <button
                    onClick={async () => {
                      if (await confirm(`确认删除「${item.title}」及其下属节点吗？`, '删除菜单节点')) {
                        deleteMenuItem(item.id);
                      }
                    }}
                    title="删除节点"
                    className="p-1.5 text-rose-600 hover:bg-rose-50 rounded-md transition-colors"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </td>
            </tr>
          )}

          {/* Render children recursively if expanded */}
          {hasChildren && isExpanded && renderTreeRows(item.children!, level + 1)}
        </React.Fragment>
      );
    });
  };

  return (
    <div id="menu-management-view" className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-xl border border-slate-200 shadow-xs">
        <div>
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-teal-100 text-teal-700 flex items-center justify-center font-bold">
              <MenuIcon className="w-4 h-4" />
            </div>
            <h1 className="text-xl font-bold text-slate-900 tracking-tight">系统菜单管理 (Menu Management)</h1>
          </div>
          <p className="text-xs text-slate-500 mt-1">
            动态配置左侧导航菜单树、页面路由组件及按钮级权限标识（Permission Codes）。
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleExpandAll}
            className="px-3 py-2 text-xs font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors"
          >
            全部展开
          </button>
          <button
            onClick={handleCollapseAll}
            className="px-3 py-2 text-xs font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors"
          >
            全部折叠
          </button>
          <button
            id="btn-add-top-menu"
            onClick={handleOpenCreateTop}
            className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-lg bg-teal-600 hover:bg-teal-700 text-white font-medium text-sm shadow-xs transition-colors"
          >
            <Plus className="w-4 h-4" />
            <span>新增顶级菜单 (Top Menu)</span>
          </button>
        </div>
      </div>

      {/* Filter / Search */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs flex items-center justify-between">
        <div className="relative w-full md:w-96">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            id="input-search-menus"
            type="text"
            placeholder="搜索菜单名称、路由路径或权限标识 (如 product:add)..."
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            className="w-full pl-9 pr-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500"
          />
        </div>
        <div className="text-xs text-slate-400 font-mono">
          层级结构支持：目录 / 菜单 / 按钮权限
        </div>
      </div>

      {/* Tree Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
              <tr>
                <th className="px-5 py-3.5">菜单名称 (Title)</th>
                <th className="px-5 py-3.5">类型</th>
                <th className="px-5 py-3.5">路由地址 (Path)</th>
                <th className="px-5 py-3.5">权限标识 (Perm Code)</th>
                <th className="px-5 py-3.5">排序</th>
                <th className="px-5 py-3.5">侧栏可见</th>
                <th className="px-5 py-3.5">状态</th>
                <th className="px-5 py-3.5 text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {renderTreeRows(menuItems)}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal: Create or Edit Menu Node */}
      {modalMode && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-slate-100">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <MenuIcon className="w-5 h-5 text-teal-600" />
                <h2 className="text-lg font-bold text-slate-900">
                  {modalMode === 'create' ? '新增菜单配置项' : '编辑菜单属性'}
                </h2>
              </div>
              <button
                onClick={() => setModalMode(null)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-md"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSaveForm} className="space-y-4 mt-4">
              {/* Type Selection */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  菜单类型 <span className="text-rose-500">*</span>
                </label>
                <div className="grid grid-cols-3 gap-2">
                  {[
                    { type: 'directory', label: '目录 (Directory)', icon: <Folder className="w-4 h-4" /> },
                    { type: 'menu', label: '菜单 (Menu)', icon: <FileText className="w-4 h-4" /> },
                    { type: 'button', label: '按钮权限 (Action)', icon: <MousePointerClick className="w-4 h-4" /> }
                  ].map((t) => (
                    <button
                      type="button"
                      key={t.type}
                      onClick={() => setFormData({ ...formData, type: t.type as MenuType })}
                      className={`flex items-center justify-center gap-2 py-2 px-3 rounded-lg text-xs font-medium border transition-all ${
                        formData.type === t.type
                          ? 'border-teal-500 bg-teal-50 text-teal-700 ring-1 ring-teal-500 font-semibold'
                          : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                      }`}
                    >
                      {t.icon}
                      <span>{t.label}</span>
                    </button>
                  ))}
                </div>
              </div>

              {/* Title & Icon */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    菜单名称 <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="如：商品管理"
                    value={formData.title}
                    onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    图标名称 (Lucide Icon)
                  </label>
                  <input
                    type="text"
                    placeholder="如：Package, Shield"
                    value={formData.icon}
                    onChange={(e) => setFormData({ ...formData, icon: e.target.value })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500"
                  />
                </div>
              </div>

              {/* Path & Component */}
              {formData.type !== 'button' && (
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      路由地址 (Path)
                    </label>
                    <input
                      type="text"
                      placeholder="如：/ecommerce/products"
                      value={formData.path}
                      onChange={(e) => setFormData({ ...formData, path: e.target.value })}
                      className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 font-mono"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      组件名称 (Component)
                    </label>
                    <input
                      type="text"
                      placeholder="如：ProductManagementView"
                      value={formData.component}
                      onChange={(e) => setFormData({ ...formData, component: e.target.value })}
                      className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 font-mono"
                    />
                  </div>
                </div>
              )}

              {/* Permission Key */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  权限标识字符 (Permission Key)
                </label>
                <input
                  type="text"
                  placeholder="如：system:role:add 或 product:edit"
                  value={formData.permission}
                  onChange={(e) => setFormData({ ...formData, permission: e.target.value })}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 font-mono"
                />
              </div>

              {/* Sort, Visibility, Status */}
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    显示排序
                  </label>
                  <input
                    type="number"
                    min="1"
                    value={formData.sort}
                    onChange={(e) => setFormData({ ...formData, sort: Number(e.target.value) })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    侧边栏展示
                  </label>
                  <select
                    value={formData.visible ? 'true' : 'false'}
                    onChange={(e) => setFormData({ ...formData, visible: e.target.value === 'true' })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 bg-white"
                  >
                    <option value="true">显示 (Visible)</option>
                    <option value="false">隐藏 (Hidden)</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    状态
                  </label>
                  <select
                    value={formData.status}
                    onChange={(e) => setFormData({ ...formData, status: e.target.value as 'active' | 'inactive' })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 bg-white"
                  >
                    <option value="active">启用 (Active)</option>
                    <option value="inactive">停用 (Inactive)</option>
                  </select>
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setModalMode(null)}
                  className="px-4 py-2 text-sm font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors"
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 text-sm font-medium text-white bg-teal-600 hover:bg-teal-700 rounded-lg transition-colors"
                >
                  确认保存
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
