import React, { useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { Role } from '../../types';
import { 
  Shield, 
  Plus, 
  Search, 
  Filter, 
  Edit3, 
  Trash2, 
  KeyRound, 
  Lock, 
  Users, 
  CheckCircle2, 
  XCircle, 
  X,
  Sparkles,
  Info
} from 'lucide-react';
import { useBodyScrollLock } from '../../hooks/useBodyScrollLock';

export const RoleManagementView: React.FC = () => {
  const { 
    roles, 
    addRole, 
    updateRole, 
    deleteRole, 
    updateRoleDataScope,
    systemUsers,
    setCurrentTab,
    departments,
    confirm
  } = useAdmin();

  const [searchKeyword, setSearchKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all');

  // Modal states
  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null);
  const [editingRole, setEditingRole] = useState<Role | null>(null);
  const [formData, setFormData] = useState({
    roleName: '',
    roleKey: '',
    roleSort: 1,
    status: 'active' as 'active' | 'inactive',
    dataScope: 'dept' as Role['dataScope'],
    description: ''
  });

  // Data scope modal state
  const [scopeModalRole, setScopeModalRole] = useState<Role | null>(null);
  const [selectedScope, setSelectedScope] = useState<Role['dataScope']>('dept');
  const [selectedCustomDepts, setSelectedCustomDepts] = useState<string[]>([]);

  // Member list modal state
  const [viewMembersRole, setViewMembersRole] = useState<Role | null>(null);

  // Filter roles
  const filteredRoles = roles.filter((role) => {
    const matchesKeyword = 
      role.roleName.toLowerCase().includes(searchKeyword.toLowerCase()) ||
      role.roleKey.toLowerCase().includes(searchKeyword.toLowerCase()) ||
      role.description.toLowerCase().includes(searchKeyword.toLowerCase());
    const matchesStatus = statusFilter === 'all' || role.status === statusFilter;
    return matchesKeyword && matchesStatus;
  });

  const handleOpenCreate = () => {
    setEditingRole(null);
    setFormData({
      roleName: '',
      roleKey: '',
      roleSort: roles.length + 1,
      status: 'active',
      dataScope: 'dept',
      description: ''
    });
    setModalMode('create');
  };

  const handleOpenEdit = (role: Role) => {
    setEditingRole(role);
    setFormData({
      roleName: role.roleName,
      roleKey: role.roleKey,
      roleSort: role.roleSort,
      status: role.status,
      dataScope: role.dataScope,
      description: role.description
    });
    setModalMode('edit');
  };

  const handleSaveForm = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.roleName.trim() || !formData.roleKey.trim()) return;

    if (modalMode === 'create') {
      addRole({
        roleName: formData.roleName.trim(),
        roleKey: formData.roleKey.trim().toLowerCase().replace(/\s+/g, '_'),
        roleSort: Number(formData.roleSort) || 1,
        status: formData.status,
        dataScope: formData.dataScope,
        permissionKeys: ['dashboard:view'],
        description: formData.description.trim() || '暂无描述'
      });
    } else if (modalMode === 'edit' && editingRole) {
      updateRole(editingRole.id, {
        roleName: formData.roleName.trim(),
        roleKey: formData.roleKey.trim(),
        roleSort: Number(formData.roleSort) || 1,
        status: formData.status,
        dataScope: formData.dataScope,
        description: formData.description.trim()
      });
    }
    setModalMode(null);
  };

  const handleOpenScopeModal = (role: Role) => {
    setScopeModalRole(role);
    setSelectedScope(role.dataScope);
    setSelectedCustomDepts(role.customDeptIds || []);
  };

  const handleSaveScope = () => {
    if (scopeModalRole) {
      updateRoleDataScope(scopeModalRole.id, selectedScope, selectedCustomDepts);
      setScopeModalRole(null);
    }
  };

  const getDataScopeLabel = (scope: Role['dataScope']) => {
    switch (scope) {
      case 'all': return { label: '全部数据权限', bg: 'bg-indigo-50 text-indigo-700 border-indigo-200' };
      case 'dept_and_sub': return { label: '本部门及子部门', bg: 'bg-blue-50 text-blue-700 border-blue-200' };
      case 'dept': return { label: '仅本部门数据', bg: 'bg-cyan-50 text-cyan-700 border-cyan-200' };
      case 'self': return { label: '仅本人数据', bg: 'bg-amber-50 text-amber-700 border-amber-200' };
      case 'custom': return { label: '自定义数据范围', bg: 'bg-purple-50 text-purple-700 border-purple-200' };
      default: return { label: scope, bg: 'bg-slate-50 text-slate-700 border-slate-200' };
    }
  };

  // 弹层打开期间锁住底层文档滚动，避免出现滚动穿透。
  useBodyScrollLock(Boolean(modalMode) || Boolean(scopeModalRole) || Boolean(viewMembersRole));
  return (
    <div id="role-management-view" className="space-y-6">
      {/* Top Banner / Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-xl border border-slate-200 shadow-xs">
        <div>
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-blue-100 text-blue-700 flex items-center justify-center font-bold">
              <Shield className="w-4 h-4" />
            </div>
            <h1 className="text-xl font-bold text-slate-900 tracking-tight">角色权限管理 (Role Management)</h1>
          </div>
          <p className="text-xs text-slate-500 mt-1">
            配置系统角色、绑定职能权限与数据隔离规则，支撑企业级精细化RBAC访问控制。
          </p>
        </div>
        <button
          id="btn-add-role-modal"
          onClick={handleOpenCreate}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-medium text-sm shadow-xs transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>新增角色 (New Role)</span>
        </button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs">
          <p className="text-xs font-medium text-slate-500">已配置角色总数</p>
          <p className="text-2xl font-bold text-slate-900 mt-1">{roles.length}</p>
        </div>
        <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs">
          <p className="text-xs font-medium text-slate-500">生效中角色</p>
          <p className="text-2xl font-bold text-emerald-600 mt-1">
            {roles.filter(r => r.status === 'active').length}
          </p>
        </div>
        <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs">
          <p className="text-xs font-medium text-slate-500">关联后台用户</p>
          <p className="text-2xl font-bold text-blue-600 mt-1">
            {systemUsers.length}
          </p>
        </div>
        <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs">
          <p className="text-xs font-medium text-slate-500">数据范围模式</p>
          <p className="text-2xl font-bold text-purple-600 mt-1">5 种隔离模式</p>
        </div>
      </div>

      {/* Filters and Controls */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="relative w-full md:w-80">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            id="input-search-roles"
            type="text"
            placeholder="搜索角色名称、标识或描述..."
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            className="w-full pl-9 pr-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
          />
        </div>

        <div className="flex items-center gap-2 w-full md:w-auto">
          <Filter className="w-4 h-4 text-slate-400" />
          <span className="text-xs text-slate-500">状态过滤:</span>
          <div className="flex items-center bg-slate-100 p-0.5 rounded-lg text-xs font-medium">
            <button
              onClick={() => setStatusFilter('all')}
              className={`px-3 py-1.5 rounded-md transition-all ${statusFilter === 'all' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-600 hover:text-slate-900'}`}
            >
              全部 ({roles.length})
            </button>
            <button
              onClick={() => setStatusFilter('active')}
              className={`px-3 py-1.5 rounded-md transition-all ${statusFilter === 'active' ? 'bg-white text-emerald-600 shadow-xs' : 'text-slate-600 hover:text-slate-900'}`}
            >
              启用 ({roles.filter(r => r.status === 'active').length})
            </button>
            <button
              onClick={() => setStatusFilter('inactive')}
              className={`px-3 py-1.5 rounded-md transition-all ${statusFilter === 'inactive' ? 'bg-white text-rose-600 shadow-xs' : 'text-slate-600 hover:text-slate-900'}`}
            >
              禁用 ({roles.filter(r => r.status === 'inactive').length})
            </button>
          </div>
        </div>
      </div>

      {/* Role List Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
              <tr>
                <th className="px-5 py-3.5">排序</th>
                <th className="px-5 py-3.5">角色名称</th>
                <th className="px-5 py-3.5">权限字符 (Key)</th>
                <th className="px-5 py-3.5">数据权限范围</th>
                <th className="px-5 py-3.5">状态</th>
                <th className="px-5 py-3.5">绑定用户</th>
                <th className="px-5 py-3.5">创建时间</th>
                <th className="px-5 py-3.5 text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredRoles.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-5 py-12 text-center text-slate-400">
                    未找到匹配的角色记录
                  </td>
                </tr>
              ) : (
                filteredRoles.map((role) => {
                  const scopeBadge = getDataScopeLabel(role.dataScope);
                  const assignedUsersCount = systemUsers.filter(u => u.roles.includes(role.id) || u.roles.includes(role.roleKey)).length;

                  return (
                    <tr key={role.id} className="hover:bg-slate-50/80 transition-colors">
                      <td className="px-5 py-4 font-mono text-slate-400 text-xs">
                        #{role.roleSort}
                      </td>
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-2.5">
                          <div className="w-7 h-7 rounded-md bg-slate-100 flex items-center justify-center text-slate-600">
                            <Shield className="w-3.5 h-3.5" />
                          </div>
                          <div>
                            <div className="font-semibold text-slate-900">{role.roleName}</div>
                            <div className="text-xs text-slate-400 truncate max-w-xs">{role.description}</div>
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-4">
                        <span className="font-mono text-xs px-2 py-0.5 rounded bg-slate-100 text-slate-700 border border-slate-200">
                          {role.roleKey}
                        </span>
                      </td>
                      <td className="px-5 py-4">
                        <span className={`inline-flex items-center gap-1 text-xs px-2.5 py-1 rounded-full border font-medium ${scopeBadge.bg}`}>
                          <Lock className="w-3 h-3" />
                          {scopeBadge.label}
                        </span>
                      </td>
                      <td className="px-5 py-4">
                        <span className={`inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-medium ${
                          role.status === 'active' 
                            ? 'bg-emerald-50 text-emerald-700' 
                            : 'bg-rose-50 text-rose-700'
                        }`}>
                          {role.status === 'active' ? (
                            <CheckCircle2 className="w-3.5 h-3.5" />
                          ) : (
                            <XCircle className="w-3.5 h-3.5" />
                          )}
                          {role.status === 'active' ? '正常' : '已停用'}
                        </span>
                      </td>
                      <td className="px-5 py-4">
                        <button
                          id={`btn-view-role-users-${role.id}`}
                          onClick={() => setViewMembersRole(role)}
                          className="inline-flex items-center gap-1 text-xs text-blue-600 hover:text-blue-700 font-medium hover:underline"
                        >
                          <Users className="w-3.5 h-3.5" />
                          <span>{assignedUsersCount} 人</span>
                        </button>
                      </td>
                      <td className="px-5 py-4 text-xs text-slate-400 font-mono">
                        {role.createdAt}
                      </td>
                      <td className="px-5 py-4 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {/* Quick Assign Permissions */}
                          <button
                            id={`btn-role-auth-${role.id}`}
                            onClick={() => setCurrentTab('authorization')}
                            title="配置功能权限"
                            className="p-1.5 text-blue-600 hover:bg-blue-50 rounded-md transition-colors"
                          >
                            <KeyRound className="w-4 h-4" />
                          </button>

                          {/* Quick Data Scope */}
                          <button
                            id={`btn-role-scope-${role.id}`}
                            onClick={() => handleOpenScopeModal(role)}
                            title="设置数据权限"
                            className="p-1.5 text-purple-600 hover:bg-purple-50 rounded-md transition-colors"
                          >
                            <Lock className="w-4 h-4" />
                          </button>

                          {/* Edit Role */}
                          <button
                            id={`btn-role-edit-${role.id}`}
                            onClick={() => handleOpenEdit(role)}
                            title="编辑角色信息"
                            className="p-1.5 text-slate-600 hover:bg-slate-100 rounded-md transition-colors"
                          >
                            <Edit3 className="w-4 h-4" />
                          </button>

                          {/* Delete Role */}
                          {role.roleKey !== 'super_admin' && (
                            <button
                              id={`btn-role-delete-${role.id}`}
                              onClick={async () => {
                                if (await confirm(`确认要删除角色「${role.roleName}」吗？`, '删除角色')) {
                                  deleteRole(role.id);
                                }
                              }}
                              title="删除角色"
                              className="p-1.5 text-rose-600 hover:bg-rose-50 rounded-md transition-colors"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal: Create or Edit Role */}
      {modalMode && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-slate-100 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <Shield className="w-5 h-5 text-blue-600" />
                <h2 className="text-lg font-bold text-slate-900">
                  {modalMode === 'create' ? '新增系统角色' : '编辑角色信息'}
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
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  角色名称 <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="如：订单履约主管"
                  value={formData.roleName}
                  onChange={(e) => setFormData({ ...formData, roleName: e.target.value })}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  权限字符 (Key) <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="如：order_manager (英文/下划线)"
                  value={formData.roleKey}
                  onChange={(e) => setFormData({ ...formData, roleKey: e.target.value })}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 font-mono"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    显示排序
                  </label>
                  <input
                    type="number"
                    min="1"
                    value={formData.roleSort}
                    onChange={(e) => setFormData({ ...formData, roleSort: Number(e.target.value) })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    状态
                  </label>
                  <select
                    value={formData.status}
                    onChange={(e) => setFormData({ ...formData, status: e.target.value as 'active' | 'inactive' })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 bg-white"
                  >
                    <option value="active">正常启用 (Active)</option>
                    <option value="inactive">暂停使用 (Inactive)</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  默认数据权限范围
                </label>
                <select
                  value={formData.dataScope}
                  onChange={(e) => setFormData({ ...formData, dataScope: e.target.value as Role['dataScope'] })}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 bg-white"
                >
                  <option value="all">全部数据权限 (All Data)</option>
                  <option value="dept_and_sub">本部门及以下数据权限 (Dept & Sub-Depts)</option>
                  <option value="dept">仅本部门数据权限 (Dept Only)</option>
                  <option value="self">仅本人数据权限 (Self Only)</option>
                  <option value="custom">自定义数据范围 (Custom Depts/Stores)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  角色职能说明
                </label>
                <textarea
                  rows={3}
                  placeholder="详细描述该角色的业务职能与使用范围..."
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
                />
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
                  className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors"
                >
                  确认保存
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Data Scope Settings */}
      {scopeModalRole && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-slate-100">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <Lock className="w-5 h-5 text-purple-600" />
                <h2 className="text-lg font-bold text-slate-900">
                  配置数据权限范围 - {scopeModalRole.roleName}
                </h2>
              </div>
              <button
                onClick={() => setScopeModalRole(null)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-md"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-4 mt-4">
              <div className="p-3 bg-purple-50 rounded-lg text-xs text-purple-800 flex items-start gap-2">
                <Info className="w-4 h-4 shrink-0 mt-0.5" />
                <span>
                  数据权限决定了该角色在查看订单、商品流水、报表时能访问的行级数据深度。
                </span>
              </div>

              <div className="space-y-2">
                {[
                  { value: 'all', label: '1. 全部数据权限', desc: '可跨部门、跨门店查看平台所有业务与流水数据。' },
                  { value: 'dept_and_sub', label: '2. 本部门及以下数据权限', desc: '可查看所在部门及所有下属子部门/门店的数据。' },
                  { value: 'dept', label: '3. 仅本部门数据权限', desc: '仅能查看当前所属直接部门的数据。' },
                  { value: 'self', label: '4. 仅本人数据权限', desc: '只能查看和操作当前登录用户本人创建/经办的数据。' },
                  { value: 'custom', label: '5. 自定义数据权限', desc: '精确勾选指定的可访问部门或直营门店。' }
                ].map((item) => (
                  <label
                    key={item.value}
                    className={`block p-3 rounded-lg border cursor-pointer transition-all ${
                      selectedScope === item.value
                        ? 'border-purple-500 bg-purple-50/50 ring-1 ring-purple-500'
                        : 'border-slate-200 hover:bg-slate-50'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <input
                        type="radio"
                        name="dataScopeRadio"
                        value={item.value}
                        checked={selectedScope === item.value}
                        onChange={() => setSelectedScope(item.value as Role['dataScope'])}
                        className="text-purple-600 focus:ring-purple-500"
                      />
                      <span className="font-semibold text-sm text-slate-900">{item.label}</span>
                    </div>
                    <p className="text-xs text-slate-500 ml-5 mt-1">{item.desc}</p>
                  </label>
                ))}
              </div>

              {selectedScope === 'custom' && (
                <div className="border border-slate-200 rounded-lg p-3 bg-slate-50 space-y-2">
                  <p className="text-xs font-semibold text-slate-700">勾选允许查看的部门/门店:</p>
                  <div className="max-h-40 overflow-y-auto space-y-1.5 text-xs">
                    {departments.flatMap((department) => [department, ...(department.children || [])]).map((department) => (
                      <label key={department.id} className="flex items-center gap-2 p-1.5 hover:bg-white rounded">
                        <input
                          type="checkbox"
                          checked={selectedCustomDepts.includes(department.id)}
                          onChange={(e) => setSelectedCustomDepts((previous) => e.target.checked
                            ? Array.from(new Set([...previous, department.id]))
                            : previous.filter((id) => id !== department.id))}
                        />
                        <span>{department.name} ({department.code})</span>
                      </label>
                    ))}
                    {departments.length === 0 && <span className="text-xs text-slate-400">暂无可选部门</span>}
                  </div>
                </div>
              )}

              <div className="flex items-center justify-end gap-2 pt-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setScopeModalRole(null)}
                  className="px-4 py-2 text-sm font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors"
                >
                  取消
                </button>
                <button
                  type="button"
                  onClick={handleSaveScope}
                  className="px-4 py-2 text-sm font-medium text-white bg-purple-600 hover:bg-purple-700 rounded-lg transition-colors"
                >
                  保存数据范围
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Modal: View Assigned Members */}
      {viewMembersRole && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-100">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <Users className="w-5 h-5 text-blue-600" />
                <h2 className="text-lg font-bold text-slate-900">
                  拥有「{viewMembersRole.roleName}」的用户列表
                </h2>
              </div>
              <button
                onClick={() => setViewMembersRole(null)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-md"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="divide-y divide-slate-100 max-h-80 overflow-y-auto mt-4">
              {systemUsers.filter(u => u.roles.includes(viewMembersRole.id) || u.roles.includes(viewMembersRole.roleKey)).length === 0 ? (
                <p className="py-8 text-center text-xs text-slate-400">
                  暂无系统用户分配此角色
                </p>
              ) : (
                systemUsers
                  .filter(u => u.roles.includes(viewMembersRole.id) || u.roles.includes(viewMembersRole.roleKey))
                  .map(user => (
                    <div key={user.id} className="py-3 flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <img
                          src={user.avatar || 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150'}
                          alt={user.realName}
                          className="w-9 h-9 rounded-full object-cover border border-slate-200"
                        />
                        <div>
                          <p className="text-sm font-semibold text-slate-900">{user.realName}</p>
                          <p className="text-xs text-slate-400">@{user.username} · {user.deptName}</p>
                        </div>
                      </div>
                      <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                        user.status === 'active' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'
                      }`}>
                        {user.status === 'active' ? '正常' : '禁用'}
                      </span>
                    </div>
                  ))
              )}
            </div>

            <div className="flex items-center justify-between pt-4 border-t border-slate-100 mt-4">
              <button
                onClick={() => {
                  setViewMembersRole(null);
                  setCurrentTab('system_users');
                }}
                className="text-xs text-blue-600 hover:text-blue-700 font-medium"
              >
                前往用户管理分配账号 →
              </button>
              <button
                onClick={() => setViewMembersRole(null)}
                className="px-4 py-2 text-xs font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-lg"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
