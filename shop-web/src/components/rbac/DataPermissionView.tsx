import React, { useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { DataRule, Role } from '../../types';
import { 
  Lock, 
  Plus, 
  Search, 
  Filter, 
  Edit3, 
  Trash2, 
  CheckCircle2, 
  XCircle, 
  X, 
  ShieldCheck, 
  Eye, 
  EyeOff, 
  Database, 
  Sparkles,
  Info,
  Code,
  Building2
} from 'lucide-react';

export const DataPermissionView: React.FC = () => {
  const { 
    dataRules, 
    addDataRule, 
    updateDataRule, 
    deleteDataRule, 
    toggleDataRuleStatus,
    roles,
    departments,
    orders,
    showToast,
    confirm
  } = useAdmin();

  const [searchKeyword, setSearchKeyword] = useState('');
  const [moduleFilter, setModuleFilter] = useState<string>('all');
  const [roleFilter, setRoleFilter] = useState<string>('all');

  // Modal states
  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null);
  const [editingRule, setEditingRule] = useState<DataRule | null>(null);

  const [formData, setFormData] = useState({
    ruleName: '',
    module: 'orders' as DataRule['module'],
    moduleName: '订单履约数据',
    roleId: roles[0]?.id || 'role-ops-manager',
    scopeType: 'dept_and_sub' as DataRule['scopeType'],
    customDeptIds: [] as string[],
    fieldMasks: ['customer_phone_mask'] as string[],
    filterCondition: 'dept_id IN (本部门及子部门树)',
    status: 'active' as 'active' | 'inactive'
  });

  // Data Preview Simulator
  const [previewRole, setPreviewRole] = useState<string>(roles[0]?.id || '');

  useEffect(() => {
    if (!roles.some((role) => role.id === previewRole)) {
      setPreviewRole(roles[0]?.id || '');
    }
  }, [roles, previewRole]);

  const filteredRules = dataRules.filter((rule) => {
    const matchesKeyword = 
      rule.ruleName.toLowerCase().includes(searchKeyword.toLowerCase()) ||
      rule.roleName.toLowerCase().includes(searchKeyword.toLowerCase()) ||
      (rule.filterCondition && rule.filterCondition.toLowerCase().includes(searchKeyword.toLowerCase()));
    const matchesModule = moduleFilter === 'all' || rule.module === moduleFilter;
    const matchesRole = roleFilter === 'all' || rule.roleId === roleFilter;
    return matchesKeyword && matchesModule && matchesRole;
  });

  const handleOpenCreate = () => {
    setEditingRule(null);
    setFormData({
      ruleName: '',
      module: 'orders',
      moduleName: '订单履约数据',
      roleId: roles[1]?.id || roles[0]?.id || '',
      scopeType: 'dept_and_sub',
      customDeptIds: [],
      fieldMasks: ['customer_phone_mask'],
      filterCondition: 'dept_id IN (当前部门及子树)',
      status: 'active'
    });
    setModalMode('create');
  };

  const handleOpenEdit = (rule: DataRule) => {
    setEditingRule(rule);
    setFormData({
      ruleName: rule.ruleName,
      module: rule.module,
      moduleName: rule.moduleName,
      roleId: rule.roleId,
      scopeType: rule.scopeType,
      customDeptIds: rule.customDeptIds || [],
      fieldMasks: rule.fieldMasks,
      filterCondition: rule.filterCondition || '',
      status: rule.status
    });
    setModalMode('edit');
  };

  const handleSaveForm = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.ruleName.trim()) return;

    const matchedRole = roles.find(r => r.id === formData.roleId);
    const roleName = matchedRole ? matchedRole.roleName : '通用角色';

    let moduleName = '订单履约数据';
    if (formData.module === 'products') moduleName = '商品类目与库存';
    if (formData.module === 'users') moduleName = '商城会员资料';
    if (formData.module === 'finance') moduleName = '财务与结算流水';
    if (formData.module === 'inventory') moduleName = '仓储调拨与盘点';

    if (modalMode === 'create') {
      addDataRule({
        ruleName: formData.ruleName.trim(),
        module: formData.module,
        moduleName,
        roleId: formData.roleId,
        roleName,
        scopeType: formData.scopeType,
        customDeptIds: formData.customDeptIds,
        fieldMasks: formData.fieldMasks,
        filterCondition: formData.filterCondition.trim(),
        status: formData.status
      });
    } else if (modalMode === 'edit' && editingRule) {
      updateDataRule(editingRule.id, {
        ruleName: formData.ruleName.trim(),
        module: formData.module,
        moduleName,
        roleId: formData.roleId,
        roleName,
        scopeType: formData.scopeType,
        customDeptIds: formData.customDeptIds,
        fieldMasks: formData.fieldMasks,
        filterCondition: formData.filterCondition.trim(),
        status: formData.status
      });
    }
    setModalMode(null);
  };

  const getScopeBadge = (scope: DataRule['scopeType']) => {
    switch (scope) {
      case 'all': return { label: '全部数据 (All)', color: 'bg-indigo-50 text-indigo-700 border-indigo-200' };
      case 'dept_and_sub': return { label: '本部门及子部门 (Subtree)', color: 'bg-blue-50 text-blue-700 border-blue-200' };
      case 'dept': return { label: '仅本部门 (Dept)', color: 'bg-cyan-50 text-cyan-700 border-cyan-200' };
      case 'self': return { label: '仅本人经手 (Self)', color: 'bg-amber-50 text-amber-700 border-amber-200' };
      case 'custom': return { label: '自定义门店 (Custom)', color: 'bg-purple-50 text-purple-700 border-purple-200' };
      default: return { label: scope, color: 'bg-slate-50 text-slate-700 border-slate-200' };
    }
  };

  const getMaskFieldLabel = (maskKey: string) => {
    switch (maskKey) {
      case 'customer_phone_mask': return '手机号码掩码 (138****1234)';
      case 'customer_id_card': return '身份证号脱敏';
      case 'profit_cost_margin': return '采购底价与毛利率隐藏';
      case 'supply_cost_price': return '供应链结算成本隐藏';
      default: return maskKey;
    }
  };

  // Sample simulation data for live masking preview
  const currentPreviewRule = dataRules.find(r => r.roleId === previewRole && r.module === 'orders');
  const isPhoneMasked = currentPreviewRule?.fieldMasks.includes('customer_phone_mask');

  return (
    <div id="data-permission-view" className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-xl border border-slate-200 shadow-xs">
        <div>
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-purple-100 text-purple-700 flex items-center justify-center font-bold">
              <Lock className="w-4 h-4" />
            </div>
            <h1 className="text-xl font-bold text-slate-900 tracking-tight">数据权限与行级隔离管控 (Data Permissions)</h1>
          </div>
          <p className="text-xs text-slate-500 mt-1">
            定义组织架构行级数据隔离规则（Row-Level Security）及客户隐私敏感字段脱敏脱密（Field Masking）。
          </p>
        </div>
        <button
          id="btn-add-data-rule"
          onClick={handleOpenCreate}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg bg-purple-600 hover:bg-purple-700 text-white font-medium text-sm shadow-xs transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>新增数据权限规则 (New Rule)</span>
        </button>
      </div>

      {/* Filter Toolbar */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs grid grid-cols-1 sm:grid-cols-3 gap-3">
        <div className="relative">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            id="input-search-data-rules"
            type="text"
            placeholder="搜索规则名称、角色或SQL过滤条件..."
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            className="w-full pl-9 pr-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500"
          />
        </div>

        <div>
          <select
            value={moduleFilter}
            onChange={(e) => setModuleFilter(e.target.value)}
            className="w-full px-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 text-slate-700"
          >
            <option value="all">全部业务模块 (All Modules)</option>
            <option value="orders">订单履约数据 (orders)</option>
            <option value="products">商品类目与库存 (products)</option>
            <option value="users">商城会员资料 (users)</option>
            <option value="finance">财务与结算流水 (finance)</option>
          </select>
        </div>

        <div>
          <select
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            className="w-full px-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 text-slate-700"
          >
            <option value="all">全部关联角色 (All Roles)</option>
            {roles.map(r => (
              <option key={r.id} value={r.id}>{r.roleName}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Rules Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
              <tr>
                <th className="px-5 py-3.5">规则名称</th>
                <th className="px-5 py-3.5">适用业务模块</th>
                <th className="px-5 py-3.5">绑定角色</th>
                <th className="px-5 py-3.5">行级数据范围 (Row Scope)</th>
                <th className="px-5 py-3.5">敏感字段脱敏 (Masking)</th>
                <th className="px-5 py-3.5">状态</th>
                <th className="px-5 py-3.5 text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredRules.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center text-slate-400">
                    暂无匹配的数据权限规则
                  </td>
                </tr>
              ) : (
                filteredRules.map((rule) => {
                  const scopeBadge = getScopeBadge(rule.scopeType);

                  return (
                    <tr key={rule.id} className="hover:bg-slate-50/80 transition-colors">
                      <td className="px-5 py-4">
                        <div className="font-semibold text-slate-900">{rule.ruleName}</div>
                        <div className="text-xs font-mono text-slate-400 mt-0.5 flex items-center gap-1">
                          <Code className="w-3 h-3 text-slate-400" />
                          <span>{rule.filterCondition || '全量放行'}</span>
                        </div>
                      </td>

                      <td className="px-5 py-4">
                        <span className="inline-flex items-center gap-1 text-xs px-2.5 py-1 rounded-md font-medium bg-slate-100 text-slate-700">
                          <Database className="w-3 h-3 text-slate-400" />
                          {rule.moduleName}
                        </span>
                      </td>

                      <td className="px-5 py-4">
                        <span className="text-xs font-medium text-slate-800">
                          {rule.roleName}
                        </span>
                      </td>

                      <td className="px-5 py-4">
                        <span className={`inline-flex items-center gap-1 text-xs px-2.5 py-1 rounded-full border font-medium ${scopeBadge.color}`}>
                          <Building2 className="w-3 h-3" />
                          {scopeBadge.label}
                        </span>
                      </td>

                      <td className="px-5 py-4">
                        <div className="flex flex-wrap gap-1">
                          {rule.fieldMasks.length === 0 ? (
                            <span className="text-xs text-slate-400">无脱敏约束 (明文)</span>
                          ) : (
                            rule.fieldMasks.map((mask, idx) => (
                              <span
                                key={idx}
                                className="inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded bg-rose-50 text-rose-700 border border-rose-200"
                              >
                                <EyeOff className="w-3 h-3" />
                                {getMaskFieldLabel(mask)}
                              </span>
                            ))
                          )}
                        </div>
                      </td>

                      <td className="px-5 py-4">
                        <button
                          onClick={() => toggleDataRuleStatus(rule.id)}
                          className={`inline-flex items-center gap-1 text-xs px-2.5 py-1 rounded-full font-medium transition-colors ${
                            rule.status === 'active' 
                              ? 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100' 
                              : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                          }`}
                        >
                          {rule.status === 'active' ? (
                            <CheckCircle2 className="w-3.5 h-3.5" />
                          ) : (
                            <XCircle className="w-3.5 h-3.5" />
                          )}
                          <span>{rule.status === 'active' ? '生效中' : '已暂停'}</span>
                        </button>
                      </td>

                      <td className="px-5 py-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            onClick={() => handleOpenEdit(rule)}
                            title="修改规则"
                            className="p-1.5 text-slate-600 hover:bg-slate-100 rounded-md transition-colors"
                          >
                            <Edit3 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={async () => {
                              if (await confirm(`确认删除数据权限规则「${rule.ruleName}」吗？`, '删除数据权限规则')) {
                                deleteDataRule(rule.id);
                              }
                            }}
                            title="删除规则"
                            className="p-1.5 text-rose-600 hover:bg-rose-50 rounded-md transition-colors"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
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

      {/* Live Data Sandbox Simulator */}
      <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-xs space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-100">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-purple-600" />
            <h2 className="text-sm font-bold text-slate-900">
              数据行级隔离与隐私脱敏沙盒模拟器 (Live Masking Preview)
            </h2>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-500">模拟切换登录角色:</span>
            <select
              value={previewRole}
              onChange={(e) => setPreviewRole(e.target.value)}
              className="px-3 py-1.5 text-xs bg-purple-50 border border-purple-200 text-purple-900 rounded-lg font-semibold focus:outline-none focus:ring-2 focus:ring-purple-500/20"
            >
              {roles.map(r => (
                <option key={r.id} value={r.id}>{r.roleName} ({r.roleKey})</option>
              ))}
            </select>
          </div>
        </div>

        <p className="text-xs text-slate-500">
          根据当前选择角色「{roles.find(r => r.id === previewRole)?.roleName}」的数据权限与脱敏规则，实时渲染以下订单数据列：
        </p>

        <div className="overflow-x-auto border border-slate-100 rounded-lg">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-50 text-slate-600 font-semibold border-b border-slate-100">
              <tr>
                <th className="px-4 py-2.5">订单号</th>
                <th className="px-4 py-2.5">客户姓名</th>
                <th className="px-4 py-2.5">联系电话 (脱敏测试)</th>
                <th className="px-4 py-2.5">收货地址</th>
                <th className="px-4 py-2.5">订单金额</th>
                <th className="px-4 py-2.5">数据行级可见性</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {orders.slice(0, 3).map((ord) => {
                const maskedPhone = isPhoneMasked 
                  ? ord.customerPhone.slice(0, 3) + '****' + ord.customerPhone.slice(7)
                  : ord.customerPhone;

                return (
                  <tr key={ord.id} className="hover:bg-slate-50/60">
                    <td className="px-4 py-3 font-mono font-semibold text-slate-900">{ord.orderNumber}</td>
                    <td className="px-4 py-3 text-slate-800">{ord.customerName}</td>
                    <td className="px-4 py-3 font-mono">
                      <span className={`px-2 py-0.5 rounded ${
                        isPhoneMasked ? 'bg-amber-50 text-amber-700 font-bold' : 'text-slate-700'
                      }`}>
                        {maskedPhone}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-slate-500 truncate max-w-xs">{ord.shippingAddress}</td>
                    <td className="px-4 py-3 font-semibold text-slate-900">¥{ord.amount.toFixed(2)}</td>
                    <td className="px-4 py-3">
                      <span className="inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 font-medium">
                        <CheckCircle2 className="w-3 h-3" />
                        行级放行匹配
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal: Create / Edit Data Rule */}
      {modalMode && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-slate-100">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <Lock className="w-5 h-5 text-purple-600" />
                <h2 className="text-lg font-bold text-slate-900">
                  {modalMode === 'create' ? '新建数据权限规则' : '编辑数据权限配置'}
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
                  规则名称 <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="如：客服专员订单行级数据隔离规则"
                  value={formData.ruleName}
                  onChange={(e) => setFormData({ ...formData, ruleName: e.target.value })}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    适用业务模块 <span className="text-rose-500">*</span>
                  </label>
                  <select
                    value={formData.module}
                    onChange={(e) => setFormData({ ...formData, module: e.target.value as DataRule['module'] })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 bg-white"
                  >
                    <option value="orders">订单履约数据 (orders)</option>
                    <option value="products">商品类目与库存 (products)</option>
                    <option value="users">商城会员资料 (users)</option>
                    <option value="finance">财务与结算流水 (finance)</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    绑定系统角色 <span className="text-rose-500">*</span>
                  </label>
                  <select
                    value={formData.roleId}
                    onChange={(e) => setFormData({ ...formData, roleId: e.target.value })}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 bg-white"
                  >
                    {roles.map(r => (
                      <option key={r.id} value={r.id}>{r.roleName}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  行级数据范围 (Row Scope)
                </label>
                <select
                  value={formData.scopeType}
                  onChange={(e) => setFormData({ ...formData, scopeType: e.target.value as DataRule['scopeType'] })}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 bg-white"
                >
                  <option value="all">全部数据 (All Records)</option>
                  <option value="dept_and_sub">所在部门及所有下级子部门</option>
                  <option value="dept">仅所在本部门数据</option>
                  <option value="self">仅本人经办/创建的数据</option>
                  <option value="custom">自定义指定门店/部门</option>
                </select>
              </div>

              {/* Masking Field Checklist */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  敏感字段脱敏规则 (Field Masking)
                </label>
                <div className="space-y-1.5 p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs">
                  {[
                    { key: 'customer_phone_mask', label: '客户手机号码掩码脱敏 (138****1234)' },
                    { key: 'customer_id_card', label: '实名认证身份证掩码 (310***********1234)' },
                    { key: 'profit_cost_margin', label: '隐藏采购进货底价及商品毛利率' }
                  ].map(m => {
                    const isChecked = formData.fieldMasks.includes(m.key);
                    return (
                      <label key={m.key} className="flex items-center gap-2 text-slate-700 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setFormData({ ...formData, fieldMasks: [...formData.fieldMasks, m.key] });
                            } else {
                              setFormData({ ...formData, fieldMasks: formData.fieldMasks.filter(k => k !== m.key) });
                            }
                          }}
                          className="rounded text-purple-600 focus:ring-purple-500"
                        />
                        <span>{m.label}</span>
                      </label>
                    );
                  })}
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  SQL/逻辑过滤条件表达式
                </label>
                <input
                  type="text"
                  placeholder="如：dept_id IN (sub_tree) AND status = 1"
                  value={formData.filterCondition}
                  onChange={(e) => setFormData({ ...formData, filterCondition: e.target.value })}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 font-mono"
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
                  className="px-4 py-2 text-sm font-medium text-white bg-purple-600 hover:bg-purple-700 rounded-lg transition-colors"
                >
                  确认保存规则
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
