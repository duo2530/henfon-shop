import React, { useState, useMemo } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { User, UserStatus } from '../../types';
import { PermissionGate } from '../common/PermissionGate';
import { Pagination } from '../common/Pagination';
import { 
  Users, 
  UserPlus, 
  Flame, 
  Search, 
  Filter, 
  Download, 
  Eye, 
  Edit3, 
  Lock, 
  Unlock, 
  X, 
  Award,
  Calendar,
  Mail,
  Phone,
  Coins,
  Wallet,
  Sparkles,
  Tag,
  CheckSquare,
  Square,
  ShieldCheck,
  ShieldAlert,
  Sliders,
  DollarSign
} from 'lucide-react';

export const UserManagementView: React.FC = () => {
  const { 
    users, 
    addUser, 
    updateUserStatus, 
    updateUser, 
    batchUpdateUserStatus,
    adjustUserBalanceAndPoints,
    updateUserTags,
    showToast,
    searchQuery
  } = useAdmin();

  // Search & Filters
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'suspended'>('all');
  const [tierFilter, setTierFilter] = useState<string>('all');
  const [tagFilter, setTagFilter] = useState<string>('all');
  const [selectedUserIds, setSelectedUserIds] = useState<string[]>([]);

  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 5;

  // Modals & Drawers
  const [inspectUser, setInspectUser] = useState<User | null>(null);
  const [isAddUserModalOpen, setIsAddUserModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [isSubmittingUser, setIsSubmittingUser] = useState(false);

  // Balance & Points Adjustment Modal
  const [adjustingUser, setAdjustingUser] = useState<User | null>(null);
  const [balanceDelta, setBalanceDelta] = useState<number>(0);
  const [pointsDelta, setPointsDelta] = useState<number>(0);
  const [adjustNote, setAdjustNote] = useState<string>('活动补偿充值');

  // Tag Editor Modal
  const [taggingUser, setTaggingUser] = useState<User | null>(null);
  const [userTagsInput, setUserTagsInput] = useState<string[]>([]);
  const [newTagStr, setNewTagStr] = useState('');

  // Form State
  const [formData, setFormData] = useState({
    name: '',
    phone: '',
    email: '',
    userCode: '',
    status: 'active' as UserStatus,
    tier: 'regular' as User['tier'],
    avatar: '',
    balance: 0,
    points: 100,
    tags: ['新注册会员']
  });

  // All unique user tags for quick filtering
  const allUserTags = useMemo(() => {
    const set = new Set<string>();
    users.forEach((u) => u.tags?.forEach((t) => set.add(t)));
    return Array.from(set);
  }, [users]);

  const userStats = useMemo(() => ({
    total: users.length,
    vip: users.filter((user) => user.tier === 'platinum' || user.tier === 'gold').length,
    balance: users.reduce((sum, user) => sum + Number(user.balance || 0), 0),
    points: users.reduce((sum, user) => sum + Number(user.points || 0), 0)
  }), [users]);

  // Filtered Users
  const filteredUsers = useMemo(() => {
    return users.filter((u) => {
      const matchesSearch =
        (searchTerm === '' && searchQuery.trim() === '') ||
        u.name.toLowerCase().includes((searchTerm || searchQuery).toLowerCase()) ||
        u.phone.includes(searchTerm || searchQuery) ||
        u.userCode.toLowerCase().includes((searchTerm || searchQuery).toLowerCase()) ||
        u.email.toLowerCase().includes((searchTerm || searchQuery).toLowerCase());

      const matchesStatus =
        statusFilter === 'all' || u.status === statusFilter;

      const matchesTier =
        tierFilter === 'all' || u.tier === tierFilter;

      const matchesTag =
        tagFilter === 'all' || (u.tags && u.tags.includes(tagFilter));

      return matchesSearch && matchesStatus && matchesTier && matchesTag;
    });
  }, [users, searchTerm, searchQuery, statusFilter, tierFilter, tagFilter]);

  // Paginated Users
  const totalEntries = filteredUsers.length;
  const totalPages = Math.ceil(totalEntries / pageSize) || 1;
  const paginatedUsers = filteredUsers.slice(
    (currentPage - 1) * pageSize,
    currentPage * pageSize
  );

  const isAllCurrentPageSelected =
    paginatedUsers.length > 0 &&
    paginatedUsers.every((u) => selectedUserIds.includes(u.id));

  const handleToggleSelectAll = () => {
    if (isAllCurrentPageSelected) {
      const pageIds = paginatedUsers.map((u) => u.id);
      setSelectedUserIds((prev) => prev.filter((id) => !pageIds.includes(id)));
    } else {
      const pageIds = paginatedUsers.map((u) => u.id);
      setSelectedUserIds((prev) => Array.from(new Set([...prev, ...pageIds])));
    }
  };

  const handleToggleSelectRow = (id: string) => {
    setSelectedUserIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const handleOpenAddModal = () => {
    setEditingUser(null);
    setFormData({
      name: '',
      phone: '',
      email: '',
      userCode: '',
      status: 'active',
      tier: 'regular',
      avatar: '',
      balance: 0,
      points: 100,
      tags: ['新注册会员']
    });
    setIsAddUserModalOpen(true);
  };

  const handleOpenEditModal = (user: User) => {
    setEditingUser(user);
    setFormData({
      name: user.name,
      phone: user.phone,
      email: user.email,
      userCode: user.userCode,
      status: user.status,
      tier: user.tier,
      avatar: user.avatar || '',
      balance: user.balance || 0,
      points: user.points || 0,
      tags: user.tags ? [...user.tags] : []
    });
    setIsAddUserModalOpen(true);
  };

  const handleSubmitUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmittingUser) return;
    if (!formData.name.trim()) {
      showToast('请输入用户姓名', 'error');
      return;
    }

    setIsSubmittingUser(true);
    try {
      if (editingUser) {
        updateUser(editingUser.id, formData);
      } else {
        await addUser(formData);
      }
      setIsAddUserModalOpen(false);
    } catch {
      // 创建失败时保留表单内容，方便修正资料后重试。
    } finally {
      setIsSubmittingUser(false);
    }
  };

  const handleOpenAdjustment = (user: User) => {
    setAdjustingUser(user);
    setBalanceDelta(0);
    setPointsDelta(0);
    setAdjustNote('系统运营补贴');
  };

  const handleConfirmAdjustment = () => {
    if (!adjustingUser) return;
    adjustUserBalanceAndPoints(adjustingUser.id, pointsDelta, balanceDelta, adjustNote);
    setAdjustingUser(null);
  };

  const handleOpenTagging = (user: User) => {
    setTaggingUser(user);
    setUserTagsInput(user.tags ? [...user.tags] : []);
    setNewTagStr('');
  };

  const handleAddUserTag = () => {
    const t = newTagStr.trim();
    if (t && !userTagsInput.includes(t)) {
      setUserTagsInput([...userTagsInput, t]);
      setNewTagStr('');
    }
  };

  const handleRemoveUserTag = (t: string) => {
    setUserTagsInput(userTagsInput.filter((x) => x !== t));
  };

  const handleSaveUserTags = () => {
    if (!taggingUser) return;
    updateUserTags(taggingUser.id, userTagsInput);
    setTaggingUser(null);
  };

  const handleExportUsers = () => {
    const csvContent =
      'data:text/csv;charset=utf-8,\uFEFF' +
      '用户编号,姓名,手机号,邮箱,会员等级,可用余额,积分,成长值,累计消费,状态,用户标签\n' +
      filteredUsers
        .map(
          (u) =>
            `"${u.userCode}","${u.name}","${u.phone}","${u.email}","${u.tier}",${u.balance || 0},${u.points || 0},${u.growthValue || 0},${u.totalSpent},"${u.status === 'active' ? '正常' : '已冻结'}","${(u.tags || []).join(';')}"`
        )
        .join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `会员用户资产报表_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('会员用户资产报表已成功导出', 'success');
  };

  const getTierBadge = (tier: User['tier']) => {
    switch (tier) {
      case 'platinum':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-bold bg-purple-100 text-purple-800 border border-purple-200">
            <Sparkles className="w-3 h-3 text-purple-600" />
            白金VIP
          </span>
        );
      case 'gold':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-bold bg-amber-100 text-amber-800 border border-amber-200">
            <Award className="w-3 h-3 text-amber-600" />
            金卡会员
          </span>
        );
      case 'silver':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-bold bg-slate-100 text-slate-800 border border-slate-200">
            银卡会员
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium bg-gray-100 text-gray-700">
            普通会员
          </span>
        );
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
              客户与会员中心 (Customer CRM & Assets)
            </h2>
            <span className="text-xs bg-purple-50 text-purple-700 font-semibold px-2 py-0.5 rounded-full border border-purple-200">
              全景画像
            </span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            会员多维画像管理、资产调账（余额/积分）、等级成长体系与账户风控冻结。
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleExportUsers}
            className="h-[36px] px-3.5 rounded-lg border border-[#E2E8F0] bg-white text-gray-700 hover:bg-gray-50 flex items-center gap-2 text-xs font-semibold shadow-2xs transition-colors cursor-pointer"
          >
            <Download className="w-4 h-4 text-gray-500" />
            <span>导出会员名单</span>
          </button>

          <PermissionGate permission="member:user:status">
            <button
              id="btn-add-user"
              onClick={handleOpenAddModal}
              className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs transition-colors cursor-pointer"
            >
              <UserPlus className="w-4 h-4" />
              <span>录入会员 (New Member)</span>
            </button>
          </PermissionGate>
        </div>
      </div>

      {/* Bento Grid Stats Summary */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white rounded-xl p-4.5 border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">
              注册会员总数
            </span>
            <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
              <Users className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-gray-900 mt-1">
            {userStats.total.toLocaleString()}
          </div>
          <div className="mt-1 text-xs text-gray-500 font-medium">
            当前筛选结果来自服务端同步
          </div>
        </div>

        <div className="bg-white rounded-xl p-4.5 border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">
              VIP高净值用户
            </span>
            <div className="w-8 h-8 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center">
              <Sparkles className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-gray-900 mt-1">
            {userStats.vip.toLocaleString()}
          </div>
          <div className="mt-1 text-xs text-purple-700 font-medium">
            白金 / 金卡会员
          </div>
        </div>

        <div className="bg-white rounded-xl p-4.5 border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">
              全平台充值沉淀资金
            </span>
            <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <Wallet className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-gray-900 mt-1">
            ¥{userStats.balance.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
          <div className="mt-1 text-xs text-emerald-600 font-medium">
            钱包可用总余额
          </div>
        </div>

        <div className="bg-white rounded-xl p-4.5 border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">
              活跃积分流通量
            </span>
            <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
              <Coins className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-gray-900 mt-1">
            {userStats.points.toLocaleString('zh-CN')}
          </div>
          <div className="mt-1 text-xs text-amber-700 font-medium">
            待兑换商城权益
          </div>
        </div>
      </div>

      {/* Main Table Workspace */}
      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden">
        {/* Table Toolbar */}
        <div className="p-4 border-b border-[#E2E8F0] flex flex-col md:flex-row justify-between gap-3 bg-[#F8FAFC]/50">
          <div className="flex flex-wrap gap-3 flex-1">
            <div className="relative flex-1 max-w-xs">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => {
                  setSearchTerm(e.target.value);
                  setCurrentPage(1);
                }}
                placeholder="搜索会员名、手机号、编号..."
                className="w-full h-[36px] pl-9 pr-3 text-sm rounded-lg border border-[#E2E8F0] bg-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none"
              />
            </div>

            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as any);
                setCurrentPage(1);
              }}
              className="h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white focus:border-blue-500 outline-none text-gray-700"
            >
              <option value="all">所有状态</option>
              <option value="active">正常通行 (Active)</option>
              <option value="suspended">风控冻结 (Suspended)</option>
            </select>

            <select
              value={tierFilter}
              onChange={(e) => {
                setTierFilter(e.target.value);
                setCurrentPage(1);
              }}
              className="h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white focus:border-blue-500 outline-none text-gray-700"
            >
              <option value="all">全部会员等级</option>
              <option value="platinum">白金会员 (Platinum)</option>
              <option value="gold">金卡会员 (Gold)</option>
              <option value="silver">银卡会员 (Silver)</option>
              <option value="regular">普通会员 (Regular)</option>
            </select>
          </div>

          {/* Batch operations toolbar */}
          {selectedUserIds.length > 0 && (
            <div className="flex items-center gap-2 animate-in fade-in-50">
              <span className="text-xs font-semibold text-blue-700 bg-blue-50 px-2.5 py-1 rounded-md">
                已选中 {selectedUserIds.length} 人
              </span>
              <button
                onClick={() => {
                  batchUpdateUserStatus(selectedUserIds, 'suspended');
                  setSelectedUserIds([]);
                }}
                className="h-[34px] px-3 rounded-lg border border-red-200 text-red-600 hover:bg-red-50 text-xs font-semibold flex items-center gap-1 transition-colors"
              >
                <Lock className="w-3.5 h-3.5" />
                <span>批量冻结</span>
              </button>
              <button
                onClick={() => {
                  batchUpdateUserStatus(selectedUserIds, 'active');
                  setSelectedUserIds([]);
                }}
                className="h-[34px] px-3 rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 text-xs font-semibold flex items-center gap-1 transition-colors"
              >
                <Unlock className="w-3.5 h-3.5" />
                <span>批量解冻</span>
              </button>
            </div>
          )}
        </div>

        {/* Tag quick filters bar */}
        <div className="px-4 py-2 bg-gray-50 border-b border-gray-100 flex items-center gap-2 flex-wrap text-xs">
          <span className="text-gray-500 font-medium">标签筛选:</span>
          <button
            onClick={() => setTagFilter('all')}
            className={`px-2 py-0.5 rounded text-xs transition-colors ${
              tagFilter === 'all'
                ? 'bg-blue-600 text-white font-semibold'
                : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-100'
            }`}
          >
            全部画像
          </button>
          {allUserTags.map((tg) => (
            <button
              key={tg}
              onClick={() => setTagFilter(tg)}
              className={`px-2 py-0.5 rounded text-xs transition-colors ${
                tagFilter === tg
                  ? 'bg-blue-600 text-white font-semibold shadow-xs'
                  : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-100'
              }`}
            >
              #{tg}
            </button>
          ))}
        </div>

        {/* User Table Content */}
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-gray-600 uppercase tracking-wider">
              <tr>
                <th className="py-3 px-4 w-12 text-center">
                  <button
                    onClick={handleToggleSelectAll}
                    className="text-gray-400 hover:text-blue-600 transition-colors flex items-center justify-center mx-auto"
                  >
                    {isAllCurrentPageSelected ? (
                      <CheckSquare className="w-4 h-4 text-blue-600" />
                    ) : (
                      <Square className="w-4 h-4 text-gray-400" />
                    )}
                  </button>
                </th>
                <th className="py-3 px-4">会员基础信息 / 标签</th>
                <th className="py-3 px-4 text-center">会员等级</th>
                <th className="py-3 px-4">账户资产 (余额 / 积分)</th>
                <th className="py-3 px-4 text-right">累计消费 / 订单</th>
                <th className="py-3 px-4 text-center">风控状态</th>
                <th className="py-3 px-4 text-right w-[160px]">管理指令</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-gray-100 text-sm text-gray-800">
              {paginatedUsers.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-16 text-gray-400">
                    <Users className="w-12 h-12 mx-auto mb-2 opacity-40" />
                    <p className="text-sm font-medium">未查找到匹配的会员数据</p>
                  </td>
                </tr>
              ) : (
                paginatedUsers.map((user, idx) => {
                  const isSelected = selectedUserIds.includes(user.id);

                  return (
                    <tr
                      key={user.id}
                      className={`hover:bg-[#F8FAFC] transition-colors group ${
                        isSelected ? 'bg-blue-50/50' : idx % 2 === 1 ? 'bg-[#FCFDFF]' : 'bg-white'
                      }`}
                    >
                      {/* Checkbox */}
                      <td className="py-3 px-4 text-center">
                        <button
                          onClick={() => handleToggleSelectRow(user.id)}
                          className="text-gray-400 hover:text-blue-600 transition-colors flex items-center justify-center mx-auto"
                        >
                          {isSelected ? (
                            <CheckSquare className="w-4 h-4 text-blue-600" />
                          ) : (
                            <Square className="w-4 h-4 text-gray-300" />
                          )}
                        </button>
                      </td>

                      {/* User Info Column */}
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-3">
                          {user.avatar ? (
                            <img
                              src={user.avatar}
                              alt={user.name}
                              className="w-10 h-10 rounded-full object-cover border border-gray-200"
                            />
                          ) : (
                            <div className="w-10 h-10 rounded-full bg-blue-100 text-blue-700 font-bold flex items-center justify-center text-sm">
                              {user.name.charAt(0)}
                            </div>
                          )}
                          <div>
                            <div
                              className="font-bold text-gray-900 hover:text-blue-600 cursor-pointer"
                              onClick={() => setInspectUser(user)}
                            >
                              {user.name}
                            </div>
                            <div className="text-xs text-gray-400 font-mono">
                              {user.phone} • {user.userCode}
                            </div>
                            {user.tags && user.tags.length > 0 && (
                              <div className="flex flex-wrap gap-1 mt-1">
                                {user.tags.map((t) => (
                                  <span
                                    key={t}
                                    className="px-1.5 py-0.2 rounded text-[10px] bg-purple-50 text-purple-700 border border-purple-100 font-medium"
                                  >
                                    {t}
                                  </span>
                                ))}
                              </div>
                            )}
                          </div>
                        </div>
                      </td>

                      {/* Tier Badge */}
                      <td className="py-3 px-4 text-center">
                        {getTierBadge(user.tier)}
                      </td>

                      {/* Assets */}
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-emerald-700 text-xs">
                            ¥{(user.balance || 0).toFixed(2)}
                          </span>
                          <span className="text-gray-300">|</span>
                          <span className="font-bold text-amber-700 text-xs flex items-center gap-0.5">
                            <Coins className="w-3 h-3" />
                            {user.points || 0} 积分
                          </span>
                        </div>
                        <div className="mt-0.5">
                          <button
                            onClick={() => handleOpenAdjustment(user)}
                            className="text-[11px] text-blue-600 hover:underline font-medium"
                          >
                            调账充扣 &gt;
                          </button>
                        </div>
                      </td>

                      {/* Total Spent */}
                      <td className="py-3 px-4 text-right">
                        <div className="font-bold text-gray-900 text-sm">
                          ¥{user.totalSpent.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
                        </div>
                        <div className="text-xs text-gray-400">
                          {user.orderCount} 笔订单
                        </div>
                      </td>

                      {/* Status */}
                      <td className="py-3 px-4 text-center">
                        {user.status === 'active' ? (
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full bg-green-100 text-green-800 text-xs font-semibold">
                            <span className="w-1.5 h-1.5 rounded-full bg-green-600 mr-1.5"></span>
                            正常
                          </span>
                        ) : (
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full bg-red-100 text-red-800 text-xs font-semibold">
                            <span className="w-1.5 h-1.5 rounded-full bg-red-600 mr-1.5"></span>
                            已冻结
                          </span>
                        )}
                      </td>

                      {/* Actions */}
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            onClick={() => setInspectUser(user)}
                            className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                            title="查看会员360全景档案"
                          >
                            <Eye className="w-4 h-4" />
                          </button>

                          <button
                            onClick={() => handleOpenTagging(user)}
                            className="p-1.5 text-gray-500 hover:text-purple-600 hover:bg-purple-50 rounded transition-colors"
                            title="编辑标签画像"
                          >
                            <Tag className="w-4 h-4" />
                          </button>

                          <button
                            onClick={() => handleOpenEditModal(user)}
                            className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                            title="编辑基本信息"
                          >
                            <Edit3 className="w-4 h-4" />
                          </button>

                          <button
                            onClick={() =>
                              updateUserStatus(
                                user.id,
                                user.status === 'active' ? 'suspended' : 'active'
                              )
                            }
                            className={`p-1.5 rounded transition-colors ${
                              user.status === 'active'
                                ? 'text-gray-400 hover:text-red-600 hover:bg-red-50'
                                : 'text-gray-400 hover:text-green-600 hover:bg-green-50'
                            }`}
                            title={user.status === 'active' ? '冻结账户' : '解冻账户'}
                          >
                            {user.status === 'active' ? (
                              <Lock className="w-4 h-4" />
                            ) : (
                              <Unlock className="w-4 h-4" />
                            )}
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

        {/* Pagination */}
        <div className="p-4 border-t border-[#E2E8F0] bg-[#F8FAFC] flex items-center justify-between text-xs text-gray-500">
          <div>
            显示第 {(currentPage - 1) * pageSize + 1} -{' '}
            {Math.min(currentPage * pageSize, totalEntries)} 位，共 {totalEntries} 位会员
          </div>

          <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />
        </div>
      </div>

      {/* Inspect User 360 Drawer Modal */}
      {inspectUser && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-lg w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-gray-900">会员360全景档案</h3>
                {getTierBadge(inspectUser.tier)}
              </div>
              <button
                onClick={() => setInspectUser(null)}
                className="text-gray-400 hover:text-gray-600 p-1.5 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-4 space-y-4 text-sm text-gray-700">
              <div className="flex items-center gap-3.5 p-3.5 bg-gray-50 rounded-xl border border-gray-200">
                {inspectUser.avatar ? (
                  <img
                    src={inspectUser.avatar}
                    alt={inspectUser.name}
                    className="w-14 h-14 rounded-full object-cover border border-gray-300"
                  />
                ) : (
                  <div className="w-14 h-14 rounded-full bg-blue-100 text-blue-700 font-bold text-xl flex items-center justify-center">
                    {inspectUser.name.charAt(0)}
                  </div>
                )}
                <div>
                  <h4 className="font-bold text-gray-900 text-base">{inspectUser.name}</h4>
                  <p className="text-xs text-gray-500 font-mono">会员编号: {inspectUser.userCode}</p>
                  <div className="flex items-center gap-2 text-xs text-gray-500 mt-1">
                    <span>上次活跃: {inspectUser.lastActive || '今日在线'}</span>
                  </div>
                </div>
              </div>

              {/* Assets Matrix */}
              <div className="grid grid-cols-3 gap-3 p-3 bg-blue-50/50 rounded-xl border border-blue-100 text-center">
                <div>
                  <span className="text-xs text-gray-500">钱包余额</span>
                  <div className="text-base font-bold text-emerald-700 mt-0.5">
                    ¥{(inspectUser.balance || 0).toFixed(2)}
                  </div>
                </div>
                <div>
                  <span className="text-xs text-gray-500">可用积分</span>
                  <div className="text-base font-bold text-amber-700 mt-0.5">
                    {inspectUser.points || 0}
                  </div>
                </div>
                <div>
                  <span className="text-xs text-gray-500">成长值</span>
                  <div className="text-base font-bold text-purple-700 mt-0.5">
                    {inspectUser.growthValue ?? 0}
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="p-2.5 bg-gray-50 rounded-lg">
                  <div className="text-gray-400 flex items-center gap-1 mb-1">
                    <Phone className="w-3.5 h-3.5" />
                    <span>联系手机</span>
                  </div>
                  <div className="font-semibold text-gray-800">{inspectUser.phone}</div>
                </div>

                <div className="p-2.5 bg-gray-50 rounded-lg">
                  <div className="text-gray-400 flex items-center gap-1 mb-1">
                    <Mail className="w-3.5 h-3.5" />
                    <span>绑定邮箱</span>
                  </div>
                  <div className="font-semibold text-gray-800 truncate">{inspectUser.email}</div>
                </div>

                <div className="p-2.5 bg-gray-50 rounded-lg">
                  <div className="text-gray-400 flex items-center gap-1 mb-1">
                    <Calendar className="w-3.5 h-3.5" />
                    <span>注册时间</span>
                  </div>
                  <div className="font-semibold text-gray-800">{inspectUser.registeredAt}</div>
                </div>

                <div className="p-2.5 bg-gray-50 rounded-lg">
                  <div className="text-gray-400 flex items-center gap-1 mb-1">
                    <Award className="w-3.5 h-3.5" />
                    <span>完成订单数</span>
                  </div>
                  <div className="font-semibold text-blue-700">{inspectUser.orderCount} 笔</div>
                </div>
              </div>

              {/* Tags */}
              {inspectUser.tags && inspectUser.tags.length > 0 && (
                <div>
                  <span className="text-xs text-gray-500 block mb-1.5 font-semibold">用户标签与行为特征:</span>
                  <div className="flex flex-wrap gap-1.5">
                    {inspectUser.tags.map((t) => (
                      <span key={t} className="px-2.5 py-1 rounded-full text-xs bg-purple-50 text-purple-700 border border-purple-200 font-medium">
                        #{t}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>

            <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
              <button
                onClick={() => {
                  setInspectUser(null);
                  handleOpenAdjustment(inspectUser);
                }}
                className="px-4 py-2 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors"
              >
                对该账户调账 / 补发积分
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Balance & Points Adjustment Modal */}
      {adjustingUser && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-md w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <div className="flex items-center gap-2 text-emerald-700">
                <Wallet className="w-5 h-5" />
                <h3 className="text-base font-bold text-gray-900">会员账户资产调账与充扣</h3>
              </div>
              <button
                onClick={() => setAdjustingUser(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-4 space-y-3.5 text-sm">
              <div className="p-3 bg-gray-50 rounded-lg border border-gray-200 text-xs">
                <p className="font-bold text-gray-900">{adjustingUser.name} ({adjustingUser.phone})</p>
                <p className="text-gray-500 mt-0.5">当前余额: ¥{(adjustingUser.balance || 0).toFixed(2)} | 当前积分: {adjustingUser.points || 0}</p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  余额变动金额 (¥) [正数充值，负数扣减]
                </label>
                <input
                  type="number"
                  step="0.01"
                  value={balanceDelta}
                  onChange={(e) => setBalanceDelta(parseFloat(e.target.value) || 0)}
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 font-semibold text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  积分变动数量 (分) [正数赠送，负数扣减]
                </label>
                <input
                  type="number"
                  step="1"
                  value={pointsDelta}
                  onChange={(e) => setPointsDelta(parseInt(e.target.value, 10) || 0)}
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 font-semibold text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  调账凭据与操作事由 *
                </label>
                <input
                  type="text"
                  required
                  value={adjustNote}
                  onChange={(e) => setAdjustNote(e.target.value)}
                  placeholder="例如: 参与双11预售充值返利 / 客服补偿"
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 text-xs"
                />
              </div>
            </div>

            <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
              <button
                onClick={() => setAdjustingUser(null)}
                className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-xs font-medium"
              >
                取消
              </button>
              <button
                onClick={handleConfirmAdjustment}
                className="px-5 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold shadow-xs"
              >
                确认执行调账
              </button>
            </div>
          </div>
        </div>
      )}

      {/* User Tagging Modal */}
      {taggingUser && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-md w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <h3 className="text-base font-bold text-gray-900">
                配置用户画像与业务标签
              </h3>
              <button
                onClick={() => setTaggingUser(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-4 space-y-3.5 text-sm">
              <div className="flex gap-2">
                <input
                  type="text"
                  value={newTagStr}
                  onChange={(e) => setNewTagStr(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      handleAddUserTag();
                    }
                  }}
                  placeholder="输入标签名如: 高复购客户、大促敏感客..."
                  className="flex-1 h-[36px] px-3 rounded-lg border border-gray-300 text-xs outline-none"
                />
                <button
                  type="button"
                  onClick={handleAddUserTag}
                  className="px-3 bg-purple-600 text-white hover:bg-purple-700 text-xs font-semibold rounded-lg"
                >
                  添加
                </button>
              </div>

              <div className="flex flex-wrap gap-1.5 pt-2">
                {userTagsInput.map((t) => (
                  <span
                    key={t}
                    className="inline-flex items-center gap-1 px-2.5 py-1 rounded text-xs bg-purple-50 text-purple-700 border border-purple-200"
                  >
                    {t}
                    <button
                      type="button"
                      onClick={() => handleRemoveUserTag(t)}
                      className="hover:text-red-600"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                ))}
              </div>
            </div>

            <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
              <button
                onClick={() => setTaggingUser(null)}
                className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-xs font-medium"
              >
                取消
              </button>
              <button
                onClick={handleSaveUserTags}
                className="px-5 py-2 rounded-lg bg-purple-600 hover:bg-purple-700 text-white text-xs font-semibold shadow-xs"
              >
                保存画像
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add / Edit User Modal */}
      {isAddUserModalOpen && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-md w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <h3 className="text-base font-bold text-gray-900">
                {editingUser ? '编辑会员档案信息' : '录入新客户会员'}
              </h3>
              <button
                onClick={() => setIsAddUserModalOpen(false)}
                className="text-gray-400 hover:text-gray-600 p-1.5 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSubmitUser} className="py-4 space-y-3.5 text-sm">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  用户姓名 *
                </label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="例如: 李薇 / 张强"
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  手机号码 *
                </label>
                <input
                  type="text"
                  required
                  value={formData.phone}
                  onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                  placeholder="13800138000"
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  电子邮箱
                </label>
                <input
                  type="email"
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  placeholder="user@example.com"
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    会员等级
                  </label>
                  <select
                    value={formData.tier}
                    onChange={(e) =>
                      setFormData({ ...formData, tier: e.target.value as any })
                    }
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm bg-white"
                  >
                    <option value="regular">普通会员 (Regular)</option>
                    <option value="silver">银卡会员 (Silver)</option>
                    <option value="gold">金卡会员 (Gold)</option>
                    <option value="platinum">白金会员 (Platinum)</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    账号状态
                  </label>
                  <select
                    value={formData.status}
                    onChange={(e) =>
                      setFormData({ ...formData, status: e.target.value as any })
                    }
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm bg-white"
                  >
                    <option value="active">正常 (Active)</option>
                    <option value="suspended">冻结 (Suspended)</option>
                  </select>
                </div>
              </div>

              <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setIsAddUserModalOpen(false)}
                  className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 text-xs font-medium"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={isSubmittingUser}
                  className="px-5 py-2 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 text-xs font-semibold shadow-xs disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {isSubmittingUser ? '保存中...' : editingUser ? '保存修改' : '确认新增'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
