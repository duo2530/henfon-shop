import React, { useState, useEffect, useMemo } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { Role } from '../../types';
import { menuItemsToPermissionTree, PermissionNode } from '../../navigation/identityAdapter';
import { 
  KeyRound, 
  Shield, 
  CheckSquare, 
  Square, 
  ChevronRight, 
  ChevronDown, 
  Save, 
  Copy, 
  Search, 
  RotateCcw, 
  CheckCircle2, 
  XCircle, 
  AlertCircle, 
  Play,
  Layers,
  Sparkles,
  Info
} from 'lucide-react';

export const AuthorizationView: React.FC = () => {
  const { roles, updateRolePermissions, showToast, menuItems } = useAdmin();
  const permissionTreeData = useMemo(() => menuItemsToPermissionTree(menuItems), [menuItems]);

  // Selected active role
  const [selectedRoleId, setSelectedRoleId] = useState<string>(roles[0]?.id || 'role-super-admin');
  const activeRole = roles.find(r => r.id === selectedRoleId) || roles[0];

  // Local state for checked permission keys of active role
  const [checkedKeys, setCheckedKeys] = useState<string[]>([]);
  const [roleSearchKeyword, setRoleSearchKeyword] = useState('');
  const [treeSearchKeyword, setTreeSearchKeyword] = useState('');

  // Expanded tree node IDs
  const [expandedNodes, setExpandedNodes] = useState<Record<string, boolean>>({
    'perm-dashboard': true,
    'perm-ecommerce': true,
    'perm-prod': true,
    'perm-order': true,
    'perm-customer': true,
    'perm-system': true,
    'perm-role-mgmt': true,
    'perm-menu-mgmt-node': true,
    'perm-sysuser-mgmt': true,
    'perm-auth-node': true,
    'perm-data-node': true
  });

  // Permission Simulator state
  const [testPermKey, setTestPermKey] = useState('order:ship');
  const [testResult, setTestResult] = useState<{ tested: boolean; allowed: boolean } | null>(null);

  // Sync checked keys when active role changes
  useEffect(() => {
    if (activeRole) {
      setCheckedKeys(activeRole.permissionKeys || []);
      setTestResult(null);
    }
  }, [selectedRoleId, roles]);

  useEffect(() => {
    const nextExpanded: Record<string, boolean> = {};
    const collect = (nodes: PermissionNode[]) => nodes.forEach((node) => {
      nextExpanded[node.id] = true;
      if (node.children) collect(node.children);
    });
    collect(permissionTreeData);
    setExpandedNodes((previous) => ({ ...nextExpanded, ...previous }));
  }, [permissionTreeData]);

  const toggleNodeExpand = (nodeId: string) => {
    setExpandedNodes(prev => ({ ...prev, [nodeId]: !prev[nodeId] }));
  };

  // Helper to collect all child keys of a permission node
  const getAllChildKeys = (node: PermissionNode): string[] => {
    let keys: string[] = [node.key];
    if (node.children) {
      node.children.forEach(child => {
        keys = [...keys, ...getAllChildKeys(child)];
      });
    }
    return keys;
  };

  // Checkbox toggle logic (supports hierarchical cascading)
  const handleToggleKey = (node: PermissionNode) => {
    if (activeRole?.roleKey === 'super_admin') {
      showToast('超级管理员默认拥有全量通配权限 (*:*:*)，无需手动修改', 'info');
      return;
    }

    const allNodeKeys = getAllChildKeys(node);
    const isCurrentlyChecked = allNodeKeys.every(k => checkedKeys.includes(k));

    if (isCurrentlyChecked) {
      // Uncheck all in this subtree
      setCheckedKeys(prev => prev.filter(k => !allNodeKeys.includes(k)));
    } else {
      // Check all in this subtree
      setCheckedKeys(prev => Array.from(new Set([...prev, ...allNodeKeys])));
    }
  };

  const handleSelectAll = () => {
    let allKeys: string[] = [];
    const collectAll = (nodes: PermissionNode[]) => {
      nodes.forEach(n => {
        allKeys.push(n.key);
        if (n.children) collectAll(n.children);
      });
    };
    collectAll(permissionTreeData);
    setCheckedKeys(allKeys);
    showToast('已全选所有功能与操作权限', 'info');
  };

  const handleClearAll = () => {
    setCheckedKeys(['dashboard:view']);
    showToast('已清空所有权限（保留工作台基础查看）', 'info');
  };

  const handleSave = () => {
    if (!activeRole) return;
    updateRolePermissions(activeRole.id, checkedKeys);
  };

  const handleCopyFromRole = (sourceRoleId: string) => {
    const sourceRole = roles.find(r => r.id === sourceRoleId);
    if (sourceRole) {
      setCheckedKeys(sourceRole.permissionKeys);
      showToast(`已从角色「${sourceRole.roleName}」成功复制权限模板`, 'success');
    }
  };

  const handleRunSimulator = () => {
    if (!testPermKey.trim()) return;
    const isSuper = activeRole?.permissionKeys.includes('*:*:*') || checkedKeys.includes('*:*:*');
    const allowed = isSuper || checkedKeys.includes(testPermKey.trim());
    setTestResult({ tested: true, allowed });
  };

  // Render tree nodes recursively
  const renderTree = (nodes: PermissionNode[], level: number = 0): React.ReactNode => {
    return nodes.map(node => {
      const hasChildren = node.children && node.children.length > 0;
      const isExpanded = !!expandedNodes[node.id];
      const allNodeKeys = getAllChildKeys(node);
      const isAllChecked = allNodeKeys.length > 0 && allNodeKeys.every(k => checkedKeys.includes(k));
      const isPartialChecked = !isAllChecked && allNodeKeys.some(k => checkedKeys.includes(k));

      const matchesSearch = 
        !treeSearchKeyword ||
        node.label.toLowerCase().includes(treeSearchKeyword.toLowerCase()) ||
        node.key.toLowerCase().includes(treeSearchKeyword.toLowerCase());

      return (
        <div key={node.id} className="space-y-1">
          {matchesSearch && (
            <div 
              className={`flex items-center justify-between py-2 px-3 rounded-lg hover:bg-slate-50 transition-colors ${
                level === 0 ? 'bg-slate-50/80 font-bold border border-slate-100' : ''
              }`}
              style={{ marginLeft: `${level * 20}px` }}
            >
              <div className="flex items-center gap-2">
                {hasChildren ? (
                  <button
                    onClick={() => toggleNodeExpand(node.id)}
                    className="p-1 text-slate-400 hover:text-slate-700 rounded transition-colors"
                  >
                    {isExpanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                  </button>
                ) : (
                  <span className="w-6 inline-block" />
                )}

                <button
                  onClick={() => handleToggleKey(node)}
                  className="flex items-center gap-2 text-left"
                >
                  {isAllChecked ? (
                    <CheckSquare className="w-4 h-4 text-blue-600 shrink-0" />
                  ) : isPartialChecked ? (
                    <div className="w-4 h-4 rounded bg-blue-600 text-white flex items-center justify-center text-[10px] font-bold shrink-0">
                      -
                    </div>
                  ) : (
                    <Square className="w-4 h-4 text-slate-300 shrink-0" />
                  )}

                  <span className={`text-sm ${
                    level === 0 ? 'text-slate-900 font-semibold' : level === 1 ? 'text-slate-800 font-medium' : 'text-slate-600'
                  }`}>
                    {node.label}
                  </span>
                </button>
              </div>

              <div className="flex items-center gap-2">
                <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-slate-100 text-slate-500 border border-slate-200">
                  {node.key}
                </span>
                {node.type === 'action' && (
                  <span className="text-[10px] px-1.5 py-0.2 rounded bg-indigo-50 text-indigo-700 font-medium">
                    按钮操作
                  </span>
                )}
              </div>
            </div>
          )}

          {hasChildren && isExpanded && (
            <div className="space-y-1">
              {renderTree(node.children!, level + 1)}
            </div>
          )}
        </div>
      );
    });
  };

  const filteredRoles = roles.filter(r => 
    r.roleName.toLowerCase().includes(roleSearchKeyword.toLowerCase()) ||
    r.roleKey.toLowerCase().includes(roleSearchKeyword.toLowerCase())
  );

  return (
    <div id="authorization-view" className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-xl border border-slate-200 shadow-xs">
        <div>
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-amber-100 text-amber-700 flex items-center justify-center font-bold">
              <KeyRound className="w-4 h-4" />
            </div>
            <h1 className="text-xl font-bold text-slate-900 tracking-tight">功能权限授权矩阵 (Authorization Matrix)</h1>
          </div>
          <p className="text-xs text-slate-500 mt-1">
            采用标准 RBAC 权限模型，将页面路由、菜单目录与按钮操作权限精准授予对应角色。
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            id="btn-save-permissions"
            onClick={handleSave}
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm shadow-xs transition-colors"
          >
            <Save className="w-4 h-4" />
            <span>保存当前角色授权 (Save Auth)</span>
          </button>
        </div>
      </div>

      {/* Main 2-Column Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Column: Role Selector (4 cols) */}
        <div className="lg:col-span-4 bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
          <div className="p-4 border-b border-slate-100 bg-slate-50/50">
            <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
              <Shield className="w-4 h-4 text-blue-600" />
              <span>选择待授权角色 (Select Role)</span>
            </h2>
            <div className="relative mt-2.5">
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="过滤角色名称..."
                value={roleSearchKeyword}
                onChange={(e) => setRoleSearchKeyword(e.target.value)}
                className="w-full pl-8 pr-3 py-1.5 text-xs bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
              />
            </div>
          </div>

          <div className="divide-y divide-slate-100 max-h-[600px] overflow-y-auto">
            {filteredRoles.map(role => {
              const isSelected = role.id === selectedRoleId;
              const isSuper = role.roleKey === 'super_admin';

              return (
                <button
                  key={role.id}
                  id={`role-select-${role.id}`}
                  onClick={() => setSelectedRoleId(role.id)}
                  className={`w-full text-left p-4 transition-all flex items-center justify-between ${
                    isSelected
                      ? 'bg-blue-50/80 border-l-4 border-blue-600'
                      : 'hover:bg-slate-50 text-slate-700'
                  }`}
                >
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className={`text-sm font-semibold ${isSelected ? 'text-blue-900' : 'text-slate-900'}`}>
                        {role.roleName}
                      </span>
                      {isSuper && (
                        <span className="px-1.5 py-0.2 text-[10px] font-bold rounded bg-amber-100 text-amber-800">
                          ALL PERMS
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-400 font-mono">
                      {role.roleKey}
                    </p>
                    <p className="text-[11px] text-slate-500 line-clamp-1">
                      {role.description}
                    </p>
                  </div>
                  <div className="text-right shrink-0">
                    <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">
                      {isSuper ? '全放行' : `${role.permissionKeys.length} 项`}
                    </span>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Right Column: Permission Tree & Simulator (8 cols) */}
        <div className="lg:col-span-8 space-y-6">
          {/* Active Role Bar & Tree Controls */}
          <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-xs space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-slate-100">
              <div>
                <div className="flex items-center gap-2">
                  <span className="text-xs font-semibold text-slate-400">当前正在授权:</span>
                  <span className="text-base font-bold text-slate-900">{activeRole?.roleName}</span>
                  <span className="text-xs font-mono px-2 py-0.5 rounded bg-slate-100 text-slate-700">
                    {activeRole?.roleKey}
                  </span>
                </div>
                <p className="text-xs text-slate-500 mt-0.5">{activeRole?.description}</p>
              </div>

              {/* Copy template dropdown */}
              <div className="flex items-center gap-2">
                <span className="text-xs text-slate-400">复制模板:</span>
                <select
                  onChange={(e) => {
                    if (e.target.value) handleCopyFromRole(e.target.value);
                  }}
                  defaultValue=""
                  className="px-2.5 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-lg text-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                >
                  <option value="" disabled>选择参考角色...</option>
                  {roles.filter(r => r.id !== activeRole?.id).map(r => (
                    <option key={r.id} value={r.id}>{r.roleName}</option>
                  ))}
                </select>
              </div>
            </div>

            {/* Tree Action Toolbar */}
            <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
              <div className="flex items-center gap-2">
                <button
                  onClick={handleSelectAll}
                  className="px-3 py-1.5 text-xs font-medium text-blue-700 bg-blue-50 hover:bg-blue-100 rounded-lg transition-colors"
                >
                  全选所有权限
                </button>
                <button
                  onClick={handleClearAll}
                  className="px-3 py-1.5 text-xs font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors"
                >
                  清空已选
                </button>
              </div>

              <div className="relative w-64">
                <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  placeholder="检索权限节点与权限字..."
                  value={treeSearchKeyword}
                  onChange={(e) => setTreeSearchKeyword(e.target.value)}
                  className="w-full pl-8 pr-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
                />
              </div>
            </div>

            {/* Tree View Canvas */}
            <div className="border border-slate-100 rounded-xl p-3 bg-slate-50/40 max-h-[500px] overflow-y-auto space-y-2">
              {renderTree(permissionTreeData)}
            </div>
          </div>

          {/* Interactive Permission Simulator */}
          <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-xs">
            <div className="flex items-center gap-2 pb-3 border-b border-slate-100">
              <Sparkles className="w-4 h-4 text-indigo-600" />
              <h2 className="text-sm font-bold text-slate-900">权限模拟鉴权测试工具 (Permission Simulator)</h2>
            </div>
            <p className="text-xs text-slate-500 mt-2">
              在下方输入任意功能权限标识（如 `order:ship`, `product:delete`, `system:user:add`），实时验证当前角色是否具备访问许可。
            </p>

            <div className="flex items-center gap-3 mt-4">
              <input
                type="text"
                value={testPermKey}
                onChange={(e) => setTestPermKey(e.target.value)}
                placeholder="如: order:ship"
                className="flex-1 px-3.5 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 font-mono"
              />
              <button
                onClick={handleRunSimulator}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-medium text-xs rounded-lg transition-colors"
              >
                <Play className="w-3.5 h-3.5" />
                <span>执行鉴权测试</span>
              </button>
            </div>

            {testResult && testResult.tested && (
              <div className={`mt-3 p-3 rounded-lg border text-xs flex items-center justify-between ${
                testResult.allowed 
                  ? 'bg-emerald-50 border-emerald-200 text-emerald-800' 
                  : 'bg-rose-50 border-rose-200 text-rose-800'
              }`}>
                <div className="flex items-center gap-2 font-medium">
                  {testResult.allowed ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                  ) : (
                    <XCircle className="w-4 h-4 text-rose-600" />
                  )}
                  <span>
                    角色「{activeRole?.roleName}」对于权限字 <code className="font-mono font-bold bg-white/60 px-1 py-0.5 rounded">{testPermKey}</code> 的判定结果为：
                    <strong className="ml-1">{testResult.allowed ? '【允许访问 / 鉴权通过】' : '【禁止访问 / 鉴权拦截】'}</strong>
                  </span>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
