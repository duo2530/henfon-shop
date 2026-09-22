import React, { useCallback, useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import {
  BackendMemberAddress,
  deleteMemberAddress,
  listMemberAddresses,
  setDefaultMemberAddress,
  updateMemberAddress,
} from '../../api/adminApi';
import { PermissionGate } from '../common/PermissionGate';
import { Pagination } from '../common/Pagination';
import { formatDateTime } from '../../utils/datetime';
import { useBodyScrollLock } from '../../hooks/useBodyScrollLock';
import {
  AlertCircle,
  Loader2,
  MapPin,
  Pencil,
  RefreshCw,
  Search,
  Star,
  Trash2,
  X,
} from 'lucide-react';

const PAGE_SIZE = 20;

/** 地址标签沿用门户端的取值，改这里要同步门户的地址表单。 */
const TAG_OPTIONS = ['家', '公司', '学校'];

type EditorState = {
  id: number;
  receiverName: string;
  receiverPhone: string;
  province: string;
  city: string;
  district: string;
  detailAddress: string;
  addressTag: string;
  isDefault: number;
};

/**
 * 会员收货地址管理页。
 *
 * 这一页的用途是核对与订正「货要寄到哪」——改单、发货前核对、买家电话打不通时找备用
 * 联系方式。所以列表按会员把地址聚在一起展示，而不是按时间平铺：同一个人名下有三条
 * 地址时，平铺列表要翻页才能看出他到底留了几个收货点。
 *
 * 写操作另受 member:address:save 约束：看得到地址的角色不一定该有改的权力，改错一条
 * 就是发错一单。
 *
 * @author Henfon
 * @date 2026-09-22
 */
export const MemberAddressView: React.FC = () => {
  const { showToast, confirm } = useAdmin();

  const [records, setRecords] = useState<BackendMemberAddress[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [keywordInput, setKeywordInput] = useState('');
  const [keywordFilter, setKeywordFilter] = useState('');
  const [defaultFilter, setDefaultFilter] = useState('');
  const [onlyDefault, setOnlyDefault] = useState(false);

  const [pendingId, setPendingId] = useState<number | null>(null);
  const [editor, setEditor] = useState<EditorState | null>(null);
  const [saving, setSaving] = useState(false);

  useBodyScrollLock(Boolean(editor));

  const load = useCallback(async (page: number, keyword: string, onlyDefaultFlag: boolean) => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await listMemberAddresses({
        current: page,
        size: PAGE_SIZE,
        keyword: keyword.trim() || undefined,
        isDefault: onlyDefaultFlag ? 1 : undefined,
      });
      setRecords(data.records || []);
      setTotal(data.total || 0);
      setTotalPages(Math.max(data.pages || 1, 1));
      setCurrent(data.current || page);
    } catch (error) {
      setRecords([]);
      setTotal(0);
      setTotalPages(1);
      setLoadError(error instanceof Error ? error.message : '收货地址加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  // 页码与筛选都是取数的输入，直接进依赖：改了就必须重取，少一个就会出现「改了筛选但
  // 列表没动」的假象。
  useEffect(() => {
    void load(current, keywordFilter, onlyDefault);
  }, [load, current, keywordFilter, onlyDefault]);

  const runSearch = () => {
    setKeywordFilter(keywordInput);
    setOnlyDefault(defaultFilter === '1');
    if (current === 1) {
      void load(1, keywordInput, defaultFilter === '1');
    } else {
      setCurrent(1);
    }
  };

  /** 写操作之后按当前筛选重取。 */
  const reload = () => void load(current, keywordFilter, onlyDefault);

  const openEditor = (address: BackendMemberAddress) => {
    setEditor({
      id: address.id,
      receiverName: address.receiverName || '',
      receiverPhone: address.receiverPhone || '',
      province: address.province || '',
      city: address.city || '',
      district: address.district || '',
      detailAddress: address.detailAddress || '',
      addressTag: address.addressTag || '',
      isDefault: address.isDefault === 1 ? 1 : 0,
    });
  };

  const submitEditor = async () => {
    if (!editor) return;
    // 省市区单独校验：少一个行政区划，这一条就寄不出去，等后端报错再让运营逐格回填更费事。
    if (!editor.receiverName.trim() || !editor.receiverPhone.trim() || !editor.detailAddress.trim()
      || !editor.province.trim() || !editor.city.trim() || !editor.district.trim()) {
      showToast('收货人、手机号、省市区与详细地址都不能为空', 'error');
      return;
    }
    setSaving(true);
    try {
      await updateMemberAddress(editor.id, {
        receiverName: editor.receiverName.trim(),
        receiverPhone: editor.receiverPhone.trim(),
        province: editor.province.trim(),
        city: editor.city.trim(),
        district: editor.district.trim(),
        detailAddress: editor.detailAddress.trim(),
        addressTag: editor.addressTag || undefined,
        isDefault: editor.isDefault,
      });
      showToast('地址已更新', 'success');
      setEditor(null);
      await load(current, keywordFilter, onlyDefault);
    } catch (error) {
      showToast(error instanceof Error ? error.message : '保存失败，请稍后重试', 'error');
    } finally {
      setSaving(false);
    }
  };

  const makeDefault = async (address: BackendMemberAddress) => {
    setPendingId(address.id);
    try {
      await setDefaultMemberAddress(address.id);
      showToast('已设为该会员的默认地址', 'success');
      await load(current, keywordFilter, onlyDefault);
    } catch (error) {
      showToast(error instanceof Error ? error.message : '设置失败，请稍后重试', 'error');
    } finally {
      setPendingId(null);
    }
  };

  const remove = async (address: BackendMemberAddress) => {
    const agreed = await confirm(
      `删除后该会员在下单时不再看到这条地址，已下单的历史订单不受影响。确定删除「${address.receiverName} ${address.detailAddress}」吗？`,
      '删除收货地址',
    );
    if (!agreed) return;
    setPendingId(address.id);
    try {
      await deleteMemberAddress(address.id);
      showToast('已删除', 'success');
      await load(current, keywordFilter, onlyDefault);
    } catch (error) {
      showToast(error instanceof Error ? error.message : '删除失败，请稍后重试', 'error');
    } finally {
      setPendingId(null);
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      <div>
        <div className="flex items-center gap-2">
          <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">收货地址</h2>
          <span className="text-xs bg-blue-50 text-blue-700 font-semibold px-2 py-0.5 rounded-full border border-blue-200 inline-flex items-center gap-1">
            <MapPin className="w-3 h-3" />会员收货信息
          </span>
        </div>
        <p className="text-xs md:text-sm text-[#434655] mt-0.5">
          按会员查看与订正收货地址。改地址会影响该会员后续下单，改完请与买家确认。
        </p>
      </div>

      <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5 md:p-6 space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[220px] max-w-md">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
            <input
              type="search"
              id="member-address-keyword"
              name="keyword"
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') runSearch();
              }}
              placeholder="收货人 / 电话 / 地址 / 会员账号"
              className="w-full h-[36px] pl-9 pr-3 text-sm rounded-lg border border-[#E2E8F0] bg-white outline-none focus:border-blue-500"
            />
          </div>
          <select
            id="member-address-default"
            name="isDefault"
            value={defaultFilter}
            onChange={(event) => setDefaultFilter(event.target.value)}
            aria-label="默认地址筛选"
            className="h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white text-gray-700 outline-none"
          >
            <option value="">全部地址</option>
            <option value="1">只看默认地址</option>
          </select>
          <button
            type="button"
            onClick={runSearch}
            className="h-[36px] px-3 rounded-lg bg-[#2563EB] text-white text-sm font-semibold hover:bg-blue-700"
          >
            查询
          </button>
          <button
            type="button"
            onClick={reload}
            className="h-[36px] px-3 rounded-lg border border-[#E2E8F0] text-sm text-gray-700 hover:bg-gray-50 inline-flex items-center gap-1.5"
          >
            <RefreshCw className="w-4 h-4" />刷新
          </button>
        </div>

        <div className="overflow-x-auto">
          {loadError ? (
            <div className="py-10 text-center text-sm text-red-700" role="alert">
              <AlertCircle className="w-6 h-6 mx-auto mb-2" />
              <p>{loadError}</p>
              <button
                type="button"
                onClick={reload}
                className="mt-3 inline-flex items-center gap-1.5 text-blue-700 hover:underline"
              >
                <RefreshCw className="w-4 h-4" />重新加载
              </button>
            </div>
          ) : loading ? (
            <p className="py-10 text-center text-sm text-gray-500" role="status">
              <Loader2 className="w-4 h-4 inline animate-spin mr-1.5" />收货地址加载中…
            </p>
          ) : records.length === 0 ? (
            <p className="py-10 text-center text-sm text-gray-400" role="status">没有符合条件的收货地址</p>
          ) : (
            <table className="w-full text-xs">
              <thead>
                <tr className="text-left text-gray-500 border-b">
                  <th className="py-2 pr-3">会员</th>
                  <th className="py-2 pr-3">收货人</th>
                  <th className="py-2 pr-3">收货地址</th>
                  <th className="py-2 pr-3">标签</th>
                  <th className="py-2 pr-3">默认</th>
                  <th className="py-2 pr-3">更新时间</th>
                  <th className="py-2">操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((item) => (
                  <tr key={item.id} className="border-b border-gray-50 hover:bg-slate-50/60 align-top">
                    <td className="py-2.5 pr-3 whitespace-nowrap">
                      <div className="text-gray-800 font-medium">{item.memberName || `会员 #${item.memberId}`}</div>
                      <div className="text-gray-400 font-mono text-[11px]">{item.memberNo || ''}</div>
                    </td>
                    <td className="py-2.5 pr-3 whitespace-nowrap">
                      <div className="text-gray-800">{item.receiverName}</div>
                      <div className="text-gray-500 font-mono text-[11px]">{item.receiverPhone}</div>
                    </td>
                    <td className="py-2.5 pr-3 text-gray-600 max-w-[320px]">
                      <span className="line-clamp-2">
                        {[item.province, item.city, item.district, item.detailAddress].filter(Boolean).join(' ')}
                      </span>
                    </td>
                    <td className="py-2.5 pr-3 whitespace-nowrap text-gray-600">{item.addressTag || '-'}</td>
                    <td className="py-2.5 pr-3 whitespace-nowrap">
                      {item.isDefault === 1 ? (
                        <span className="px-1.5 py-0.5 rounded border font-semibold bg-blue-50 text-blue-700 border-blue-200 inline-flex items-center gap-1">
                          <Star className="w-3 h-3" />默认
                        </span>
                      ) : (
                        <span className="text-gray-400">—</span>
                      )}
                    </td>
                    <td className="py-2.5 pr-3 whitespace-nowrap text-gray-500">
                      {formatDateTime(item.updatedAt || item.createdAt, '-')}
                    </td>
                    <td className="py-2.5 whitespace-nowrap">
                      <PermissionGate permission="member:address:save" fallback={null}>
                        {item.isDefault === 1 ? null : (
                          <button
                            type="button"
                            disabled={pendingId === item.id}
                            onClick={() => void makeDefault(item)}
                            className="text-blue-700 hover:underline inline-flex items-center gap-1 mr-3 disabled:opacity-50"
                          >
                            <Star className="w-3 h-3" />设为默认
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => openEditor(item)}
                          className="text-blue-700 hover:underline inline-flex items-center gap-1 mr-3"
                        >
                          <Pencil className="w-3 h-3" />编辑
                        </button>
                        <button
                          type="button"
                          disabled={pendingId === item.id}
                          onClick={() => void remove(item)}
                          className="text-rose-600 hover:underline inline-flex items-center gap-1 disabled:opacity-50"
                        >
                          <Trash2 className="w-3 h-3" />删除
                        </button>
                      </PermissionGate>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {!loadError && !loading && (
          <div className="flex flex-wrap items-center justify-between gap-2 pt-3 border-t border-gray-100 text-xs text-gray-500">
            <span>共 {total} 条，第 {Math.min(current, totalPages)} / {totalPages} 页</span>
            <Pagination currentPage={current} totalPages={totalPages} onPageChange={setCurrent} />
          </div>
        )}
      </section>

      {editor && (
        <div
          className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/45 p-4 backdrop-blur-[2px]"
          role="presentation"
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="member-address-editor-title"
            className="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-2xl border border-slate-200 bg-white shadow-2xl"
          >
            <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
              <div>
                <h2 id="member-address-editor-title" className="text-base font-bold text-slate-900">编辑收货地址</h2>
                <p className="mt-1 text-xs text-slate-500">
                  只改收货信息，不改动这条地址属于哪个会员。
                </p>
              </div>
              <button
                type="button"
                onClick={() => setEditor(null)}
                className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                aria-label="关闭"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="px-5 py-4 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <label className="flex flex-col gap-1 text-xs text-gray-500">
                  收货人
                  <input
                    type="text"
                    id="member-address-receiver"
                    name="receiverName"
                    value={editor.receiverName}
                    onChange={(event) => setEditor({ ...editor, receiverName: event.target.value })}
                    className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500"
                  />
                </label>
                <label className="flex flex-col gap-1 text-xs text-gray-500">
                  收货电话
                  <input
                    type="text"
                    id="member-address-phone"
                    name="receiverPhone"
                    value={editor.receiverPhone}
                    onChange={(event) => setEditor({ ...editor, receiverPhone: event.target.value })}
                    className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500"
                  />
                </label>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <label className="flex flex-col gap-1 text-xs text-gray-500">
                  省
                  <input
                    type="text"
                    id="member-address-province"
                    name="province"
                    value={editor.province}
                    onChange={(event) => setEditor({ ...editor, province: event.target.value })}
                    className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500"
                  />
                </label>
                <label className="flex flex-col gap-1 text-xs text-gray-500">
                  市
                  <input
                    type="text"
                    id="member-address-city"
                    name="city"
                    value={editor.city}
                    onChange={(event) => setEditor({ ...editor, city: event.target.value })}
                    className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500"
                  />
                </label>
                <label className="flex flex-col gap-1 text-xs text-gray-500">
                  区县
                  <input
                    type="text"
                    id="member-address-district"
                    name="district"
                    value={editor.district}
                    onChange={(event) => setEditor({ ...editor, district: event.target.value })}
                    className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500"
                  />
                </label>
              </div>

              <label className="flex flex-col gap-1 text-xs text-gray-500">
                详细地址
                <textarea
                  id="member-address-detail"
                  name="detailAddress"
                  value={editor.detailAddress}
                  onChange={(event) => setEditor({ ...editor, detailAddress: event.target.value })}
                  rows={3}
                  className="w-full px-3 py-2 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500 resize-y"
                />
              </label>

              <label className="flex flex-col gap-1 text-xs text-gray-500">
                地址标签
                <select
                  id="member-address-tag"
                  name="addressTag"
                  value={editor.addressTag}
                  onChange={(event) => setEditor({ ...editor, addressTag: event.target.value })}
                  className="h-9 px-3 rounded-lg border border-[#E2E8F0] bg-white text-sm text-gray-700 outline-none"
                >
                  <option value="">不设标签</option>
                  {TAG_OPTIONS.map((tag) => (
                    <option key={tag} value={tag}>{tag}</option>
                  ))}
                </select>
              </label>

              <label className="flex items-center gap-2 text-xs text-gray-600">
                <input
                  type="checkbox"
                  id="member-address-default-check"
                  name="isDefault"
                  checked={editor.isDefault === 1}
                  onChange={(event) => setEditor({ ...editor, isDefault: event.target.checked ? 1 : 0 })}
                  className="w-4 h-4 rounded border-[#E2E8F0]"
                />
                设为该会员的默认地址（会取消他原来那条默认地址）
              </label>
            </div>

            <div className="flex justify-end gap-2 border-t border-slate-100 px-5 py-4">
              <button
                type="button"
                onClick={() => setEditor(null)}
                className="h-9 rounded-lg border border-slate-200 px-4 text-sm font-medium text-slate-600 hover:bg-slate-50"
              >
                取消
              </button>
              <button
                type="button"
                disabled={saving}
                onClick={() => void submitEditor()}
                className="h-9 rounded-lg bg-blue-600 px-4 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-60 inline-flex items-center gap-1.5"
              >
                {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                {saving ? '保存中…' : '保存'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
