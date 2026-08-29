import React, { useMemo, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { Department, SystemUser, Role } from '../../types';
import { 
  Users, 
  Plus, 
  Search, 
  Filter, 
  Edit3, 
  Trash2, 
  KeyRound, 
  Lock, 
  CheckCircle2, 
  XCircle, 
  X, 
  Building2, 
  Shield, 
  Mail, 
  Phone, 
  Clock, 
  MapPin, 
  RotateCcw,
  Sparkles
} from 'lucide-react';

export const SystemUserManagementView: React.FC = () => {
  const { 
    systemUsers, 
    addSystemUser, 
    updateSystemUser, 
    deleteSystemUser, 
    toggleSystemUserStatus,
    roles,
    departments,
    showToast
  } = useAdmin();

  const [searchKeyword, setSearchKeyword] = useState('');
  const [selectedDeptFilter, setSelectedDeptFilter] = useState<string>('all');
  const [selectedRoleFilter, setSelectedRoleFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all');

  // Modal states
  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null);
  const [editingUser, setEditingUser] = useState<SystemUser | null>(null);

  const [formData, setFormData] = useState({
    username: '',
    realName: '',
    deptId: departments[0]?.id || '',
    deptName: departments[0]?.name || '',
    roles: roles[0] ? [roles[0].id] : [],
    phone: '',
    email: '',
    status: 'active' as 'active' | 'inactive',
    dataScope: 'dept_and_sub' as SystemUser['dataScope']
  });

  // Assign Role Modal state
  const [assignRoleUser, setAssignRoleUser] = useState<SystemUser | null>(null);
  const [assignRolesSelected, setAssignRolesSelected] = useState<string[]>([]);

  const deptList = useMemo(() => {
    const result: { id: string; name: string }[] = [];
    const flatten = (items: Department[]) => items.forEach((dept) => {
      result.push({ id: dept.id, name: dept.name });
      if (dept.children) flatten(dept.children);
    });
    flatten(departments);
    return result;
  }, [departments]);

  // Filter users
  const filteredUsers = systemUsers.filter((user) => {
    const matchesKeyword = 
      user.username.toLowerCase().includes(searchKeyword.toLowerCase()) ||
      user.realName.toLowerCase().includes(searchKeyword.toLowerCase()) ||
      user.phone.includes(searchKeyword) ||
      user.email.toLowerCase().includes(searchKeyword.toLowerCase());
    
    const matchesDept = selectedDeptFilter === 'all' || user.deptId === selectedDeptFilter;
    const matchesRole = selectedRoleFilter === 'all' || user.roles.includes(selectedRoleFilter);
    const matchesStatus = statusFilter === 'all' || user.status === statusFilter;

    return matchesKeyword && matchesDept && matchesRole && matchesStatus;
  });

  const handleOpenCreate = () => {
    setEditingUser(null);
    setFormData({
      username: '',
      realName: '',
      deptId: departments[0]?.id || '',
      deptName: departments[0]?.name || '',
      roles: roles[0] ? [roles[0].id] : [],
      phone: '',
      email: '',
      status: 'active',
      dataScope: 'dept_and_sub'
    });
    setModalMode('create');
  };

  const handleOpenEdit = (user: SystemUser) => {
    setEditingUser(user);
    setFormData({
      username: user.username,
      realName: user.realName,
      deptId: user.deptId,
      deptName: user.deptName,
      roles: user.roles,
      phone: user.phone,
      email: user.email,
      status: user.status,
      dataScope: user.dataScope
    });
    setModalMode('edit');
  };

  const handleSaveForm = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.username.trim() || !formData.realName.trim()) return;

    const matchedDept = deptList.find(d => d.id === formData.deptId);
    const deptName = matchedDept ? matchedDept.name : formData.deptName;
    const roleNames = formData.roles.map(rId => {
      const foundRole = roles.find(r => r.id === rId || r.roleKey === rId);
      return foundRole ? foundRole.roleName : rId;
    });

    if (modalMode === 'create') {
      addSystemUser({
        username: formData.username.trim(),
        realName: formData.realName.trim(),
        deptId: formData.deptId,
        deptName,
        roles: formData.roles,
        roleNames,
        phone: formData.phone.trim() || '13800000000',
        email: formData.email.trim() || `${formData.username}@company.com`,
        status: formData.status,
        dataScope: formData.dataScope
      });
    } else if (modalMode === 'edit' && editingUser) {
      updateSystemUser(editingUser.id, {
        username: formData.username.trim(),
        realName: formData.realName.trim(),
        deptId: formData.deptId,
        deptName,
        roles: formData.roles,
        roleNames,
        phone: formData.phone.trim(),
        email: formData.email.trim(),
        status: formData.status,
        dataScope: formData.dataScope
      });
    }
    setModalMode(null);
  };

  const handleOpenAssignRoles = (user: SystemUser) => {
    setAssignRoleUser(user);
    setAssignRolesSelected(user.roles);
  };

  const handleSaveAssignRoles = () => {
    if (!assignRoleUser) return;
    const roleNames = assignRolesSelected.map(rId => {
      const foundRole = roles.find(r => r.id === rId || r.roleKey === rId);
      return foundRole ? foundRole.roleName : rId;
    });
    updateSystemUser(assignRoleUser.id, {
      roles: assignRolesSelected,
      roleNames
    });
    setAssignRoleUser(null);
    showToast(`已更新「${assignRoleUser.realName}」的角色绑定`, 'success');
  };

  const handleResetPassword = (user: SystemUser) => {
    showToast(`用户 ${user.username} 的密码重置链接已发送至 ${user.email} (初始密码: Abc@123456)`, 'info');
  };

  return (
    <div id="system-user-management-view" className="space-y-6">
      {/* Top Header Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-xl border border-slate-200 shadow-xs">
        <div>
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-blue-100 text-blue-700 flex items-center justify-center font-bold">
              <Users className="w-4 h-4" />
            </div>
            <h1 className="text-xl font-bold text-slate-900 tracking-tight">系统用户管理 (System Users)</h1>
          </div>
          <p className="text-xs text-slate-500 mt-1">
            管理公司员工与运营后台账号，分配所属部门、职位角色与行级数据权限范围。
          </p>
        </div>
        <button
          id="btn-add-system-user"
          onClick={handleOpenCreate}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-medium text-sm shadow-xs transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>新建系统用户 (New User)</span>
        </button>
      </div>

      {/* Filter Controls Bar */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {/* Search Input */}
        <div className="relative">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            id="input-search-sys-users"
            type="text"
            placeholder="搜索用户名、姓名、手机..."
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            className="w-full pl-9 pr-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
          />
        </div>

        {/* Dept Filter */}
        <div>
          <select
            value={selectedDeptFilter}
            onChange={(e) => setSelectedDeptFilter(e.target.value)}
            className="w-full px-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 text-slate-700"
          >
            <option value="all">全部归属部门 (All Depts)</option>
            {deptList.map(d => (
              <option key={d.id} value={d.id}>{d.name}</option>
            ))}
          </select>
        </div>

        {/* Role Filter */}
        <div>
          <select
            value={selectedRoleFilter}
            onChange={(e) => setSelectedRoleFilter(e.target.value)}
            className="w-full px-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 text-slate-700"
          >
            <option value="all">全部角色职位 (All Roles)</option>
            {roles.map(r => (
              <option key={r.id} value={r.id}>{r.roleName}</option>
            ))}
          </select>
        </div>

        {/* Status Filter */}
        <div>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as 'all' | 'active' | 'inactive')}
            className="w-full px-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 text-slate-700"
          >
            <option value="all">全部状态 (All Status)</option>
            <option value="active">正常在职 (Active)</option>
            <option value="inactive">停用禁用 (Inactive)</option>
          </select>
        </div>
      </div>

      {/* Users Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
              <tr>
                <th className="px-5 py-3.5">员工用户</th>
                <th className="px-5 py-3.5">所属部门 / 门店</th>
                <th className="px-5 py-3.5">绑定角色</th>
                <th className="px-5 py-3.5">联系方式</th>
                <th className="px-5 py-3.5">状态</th>
                <th className="px-5 py-3.5">最后登录</th>
                <th className="px-5 py-3.5 text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredUsers.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center text-slate-400">
                    未查找到匹配的系统用户
                  </td>
                </tr>
              ) : (
                filteredUsers.map((user) => {
                  return (
                    <tr key={user.id} className="hover:bg-slate-50/80 transition-colors">
                      {/* Avatar & User Info */}
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-3">
                          <img
                            src={user.avatar || 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150'}
                            alt={user.realName}
                            className="w-10 h-10 rounded-full object-cover border border-slate-200 shrink-0"
                          />
                          <div>
                            <div className="font-semibold text-slate-900 flex items-center gap-1.5">
                              <span>{user.realName}</span>
                              {user.username === 'admin' && (
                                <span className="px-1.5 py-0.2 text-[10px] rounded bg-amber-100 text-amber-800 font-bold">
                                  ROOT
                                </span>
                              )}
                            </div>
                            <div className="text-xs text-slate-400 font-mono">@{user.username}</div>
                          </div>
                        </div>
                      </td>

                      {/* Department */}
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-1.5 text-slate-700 text-xs font-medium">
                          <Building2 className="w-3.5 h-3.5 text-slate-400" />
                          <span>{user.deptName}</span>
                        </div>
                      </td>

                      {/* Roles */}
                      <td className="px-5 py-4">
                        <div className="flex flex-wrap gap-1">
                          {user.roleNames.map((rName, idx) => (
                            <span
                              key={idx}
                              className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200"
                            >
                              <Shield className="w-3 h-3" />
                              {rName}
                            </span>
                          ))}
                        </div>
                      </td>

                      {/* Contact */}
                      <td className="px-5 py-4 text-xs space-y-1">
                        <div className="flex items-center gap-1 text-slate-600 font-mono">
                          <Phone className="w-3 h-3 text-slate-400" />
                          <span>{user.phone}</span>
                        </div>
                        <div className="flex items-center gap-1 text-slate-400">
                          <Mail className="w-3 h-3 text-slate-400" />
                          <span className="truncate max-w-xs">{user.email}</span>
                        </div>
                      </td>

                      {/* Status Toggle Switch */}
                      <td className="px-5 py-4">
                        <button
                          onClick={() => toggleSystemUserStatus(user.id)}
                          className={`inline-flex items-center gap-1 text-xs px-2.5 py-1 rounded-full font-medium transition-colors ${
                            user.status === 'active' 
                              ? 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100' 
                              : 'bg-rose-50 text-rose-700 hover:bg-rose-100'
                          }`}
                        >
                          {user.status === 'active' ? (
                            <CheckCircle2 className="w-3.5 h-3.5" />
                          ) : (
                            <XCircle className="w-3.5 h-3.5" />
                          )}
                          <span>{user.status === 'active' ? '正常' : '已停用'}</span>
                        </button>
                      </td>

                      {/* Last Login */}
                      <td className="px-5 py-4 text-xs space-y-0.5 font-mono text-slate-500">
                        <div className="flex items-center gap-1 text-slate-700">
                          <Clock className="w-3 h-3 text-slate-400" />
                          <span>{user.lastLoginTime}</span>
                        </div>
                        <div className="text-[11px] text-slate-400 truncate max-w-xs">
                          {user.lastLoginIp}
                        </div>
                      </td>

                      {/* Actions */}
                      <td className="px-5 py-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          {/* Assign Roles */}
                          <button
                            id={`btn-assign-roles-${user.id}`}
                            onClick={() => handleOpenAssignRoles(user)}
                            title="分配角色"
                            className="p-1.5 text-blue-600 hover:bg-blue-50 rounded-md transition-colors"
                          >
                            <KeyRound className="w-4 h-4" />
                          </button>

                          {/* Reset Password */}
                          <button
                            id={`btn-reset-pwd-${user.id}`}
                            onClick={() => handleResetPassword(user)}
                            title="重置登录密码"
                            className="p-1.5 text-amber-600 hover:bg-amber-50 rounded-md transition-colors"
                          >
                            <RotateCcw className="w-4 h-4" />
                          </button>

                          {/* Edit User */}
                          <button
                            id={`btn-edit-user-${user.id}`}
                            onClick={() => handleOpenEdit(user)}
                            title="编辑用户资料"
                            className="p-1.5 text-slate-600 hover:bg-slate-100 rounded-md transition-colors"
                          >
                            <Edit3 className="w-4 h-4" />
                          </button>

                          {/* Delete User */}
                          {user.username !== 'admin' && (
                            <button
                              id={`btn-delete-user-${user.id}`}
                              onClick={() => {
                                if (confirm(`确认要删除系统用户「${user.username}」吗？`)) {
                                  deleteSystemUser(user.id);
                                }
                              }}
                              title="删除用户"
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

      {/* Modal: Create or Edit User */}
      {modalMode && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-2xl max-w-xl w-full p-6 shadow-2xl border border-slate-100">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <Users className="w-5 h-5 text-blue-600" />
                <h2 className="text-lg font-bold text-slate-900">
                  {modalMode === 'create' ? '开通后台系统用户' : '编辑系统用户资料'}
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
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    登录用户名 <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="如：ops_zhang"
                    value={formData.username}
                    onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 font-mono"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    真实姓名 <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="如：张立群 (运营)"
                    value={formData.realName}
                    onChange={(e) => setFormData({ ...formData, realName: e.target.value })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    所属部门 / 门店 <span className="text-rose-500">*</span>
                  </label>
                  <select
                    value={formData.deptId}
                    onChange={(e) => setFormData({ ...formData, deptId: e.target.value })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 bg-white"
                  >
                    {deptList.map(d => (
                      <option key={d.id} value={d.id}>{d.name}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    账号状态
                  </label>
                  <select
                    value={formData.status}
                    onChange={(e) => setFormData({ ...formData, status: e.target.value as 'active' | 'inactive' })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 bg-white"
                  >
                    <option value="active">正常在职 (Active)</option>
                    <option value="inactive">停用冻结 (Inactive)</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    联系电话
                  </label>
                  <input
                    type="text"
                    placeholder="13800138000"
                    value={formData.phone}
                    onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 font-mono"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    电子邮箱
                  </label>
                  <input
                    type="email"
                    placeholder="user@company.com"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 font-mono"
                  />
                </div>
              </div>

              {/* Roles Selection */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  分配系统角色 (可多选)
                </label>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 p-3 bg-slate-50 border border-slate-200 rounded-lg max-h-36 overflow-y-auto">
                  {roles.map(r => {
                    const isChecked = formData.roles.includes(r.id) || formData.roles.includes(r.roleKey);
                    return (
                      <label key={r.id} className="flex items-center gap-2 text-xs text-slate-700 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setFormData({ ...formData, roles: [...formData.roles, r.id] });
                            } else {
                              setFormData({ ...formData, roles: formData.roles.filter(id => id !== r.id && id !== r.roleKey) });
                            }
                          }}
                          className="rounded text-blue-600 focus:ring-blue-500"
                        />
                        <span>{r.roleName}</span>
                      </label>
                    );
                  })}
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
                  className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors"
                >
                  确认保存
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Quick Assign Roles */}
      {assignRoleUser && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-100">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <KeyRound className="w-5 h-5 text-blue-600" />
                <h2 className="text-lg font-bold text-slate-900">
                  分配系统角色 - {assignRoleUser.realName}
                </h2>
              </div>
              <button
                onClick={() => setAssignRoleUser(null)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-md"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-2 mt-4 max-h-64 overflow-y-auto">
              {roles.map(r => {
                const isSelected = assignRolesSelected.includes(r.id) || assignRolesSelected.includes(r.roleKey);
                return (
                  <label
                    key={r.id}
                    className={`flex items-center justify-between p-3 rounded-lg border cursor-pointer transition-all ${
                      isSelected ? 'border-blue-500 bg-blue-50/50' : 'border-slate-200 hover:bg-slate-50'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={(e) => {
                          if (e.target.checked) {
                            setAssignRolesSelected([...assignRolesSelected, r.id]);
                          } else {
                            setAssignRolesSelected(assignRolesSelected.filter(id => id !== r.id && id !== r.roleKey));
                          }
                        }}
                        className="rounded text-blue-600 focus:ring-blue-500"
                      />
                      <div>
                        <p className="text-sm font-semibold text-slate-900">{r.roleName}</p>
                        <p className="text-xs text-slate-400">{r.description}</p>
                      </div>
                    </div>
                    <span className="text-[11px] font-mono text-slate-400">{r.roleKey}</span>
                  </label>
                );
              })}
            </div>

            <div className="flex items-center justify-end gap-2 pt-4 border-t border-slate-100 mt-4">
              <button
                type="button"
                onClick={() => setAssignRoleUser(null)}
                className="px-4 py-2 text-sm font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors"
              >
                取消
              </button>
              <button
                type="button"
                onClick={handleSaveAssignRoles}
                className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors"
              >
                保存角色配置
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
