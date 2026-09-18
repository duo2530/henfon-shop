import React, { FormEvent, useEffect, useState } from 'react';
import { Edit3, ExternalLink, Image as ImageIcon, Loader2, Plus, RefreshCw, Save, Trash2, Upload, X } from 'lucide-react';
import { BackendContentBanner, deleteContentBanner, listContentBanners, saveContentBanner, updateContentBannerStatus, uploadStorageFile } from '../../api/adminApi';
import { useAdmin } from '../../context/AdminContext';
import { formatDate } from '../../utils/datetime';
import { useBodyScrollLock } from '../../hooks/useBodyScrollLock';

interface BannerForm {
  id?: number;
  bannerTitle: string;
  bannerTag: string;
  subtitle: string;
  imageUrl: string;
  /** 后端重签或上传返回的临时访问地址，仅用于预览，不参与保存。 */
  imageAccessUrl?: string;
  linkType: string;
  linkTarget: string;
  sortNo: number;
  status: number;
  startAt: string;
  endAt: string;
  remark: string;
}

const emptyForm: BannerForm = { bannerTitle: '', bannerTag: '', subtitle: '', imageUrl: '', linkType: 'NONE', linkTarget: '', sortNo: 0, status: 1, startAt: '', endAt: '', remark: '' };

function toForm(banner: BackendContentBanner): BannerForm {
  return { id: banner.id, bannerTitle: banner.bannerTitle, bannerTag: banner.bannerTag || '', subtitle: banner.subtitle || '', imageUrl: banner.imageUrl, imageAccessUrl: banner.imageAccessUrl, linkType: banner.linkType || 'NONE', linkTarget: banner.linkTarget || '', sortNo: banner.sortNo || 0, status: banner.status, startAt: banner.startAt ? banner.startAt.slice(0, 16) : '', endAt: banner.endAt ? banner.endAt.slice(0, 16) : '', remark: banner.remark || '' };
}

/** 轮播投放期只展示到天，未设置结束时间视为长期投放。 */
function displayDate(value?: string): string {
  return formatDate(value, '长期');
}

/**
 * Banner 后台管理页面，真实读取和写入内容中心接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
export const BannerManagementView: React.FC = () => {
  const { showToast, confirm } = useAdmin();
  const [banners, setBanners] = useState<BackendContentBanner[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [editing, setEditing] = useState<BannerForm | null>(null);
  const [actionId, setActionId] = useState<number | null>(null);

  const loadBanners = async () => {
    setLoading(true);
    setLoadError(null);
    try { const page = await listContentBanners({ size: 200 }); setBanners(page.records || []); }
    catch (error) { const message = error instanceof Error ? error.message : 'Banner 加载失败'; setLoadError(message); showToast(message, 'error'); }
    finally { setLoading(false); }
  };

  useEffect(() => { void loadBanners(); }, []);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!editing) return;
    setSaving(true);
    try {
      await saveContentBanner({ ...editing, startAt: editing.startAt || undefined, endAt: editing.endAt || undefined, linkTarget: editing.linkTarget || undefined });
      setEditing(null); showToast('Banner 已保存', 'success'); await loadBanners();
    } catch (error) { showToast(error instanceof Error ? error.message : 'Banner 保存失败', 'error'); }
    finally { setSaving(false); }
  };

  const remove = async (id: number) => {
    if (!await confirm('确定删除该 Banner 吗？删除后门户将不再展示。', '删除 Banner')) return;
    setActionId(id);
    try { await deleteContentBanner(id); showToast('Banner 已删除', 'success'); await loadBanners(); }
    catch (error) { showToast(error instanceof Error ? error.message : 'Banner 删除失败', 'error'); }
    finally { setActionId(null); }
  };

  const toggleStatus = async (banner: BackendContentBanner) => {
    setActionId(banner.id);
    try { await updateContentBannerStatus(banner.id, banner.status === 1 ? 0 : 1); showToast(banner.status === 1 ? 'Banner 已停用' : 'Banner 已启用', 'success'); await loadBanners(); }
    catch (error) { showToast(error instanceof Error ? error.message : 'Banner 状态更新失败', 'error'); }
    finally { setActionId(null); }
  };

  const update = (key: keyof BannerForm, value: string | number) => setEditing((current) => current ? { ...current, [key]: value } : current);

  /** 上传海报图，入库用对象键、预览用临时地址，避免把会过期的签名地址写进 Banner。 */
  const handleImageUpload = async (file: File) => {
    setUploading(true);
    try {
      const result = await uploadStorageFile(file);
      setEditing((current) => current ? { ...current, imageUrl: result.objectKey, imageAccessUrl: result.url } : current);
      showToast('图片上传成功', 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '图片上传失败，请重试', 'error');
    } finally {
      setUploading(false);
    }
  };

  /** 手动改动图片地址时丢弃旧预览，避免显示与输入不一致的图片。 */
  const updateImageUrl = (value: string) => setEditing((current) => current ? { ...current, imageUrl: value, imageAccessUrl: '' } : current);

  // 弹层打开期间锁住底层文档滚动，避免出现滚动穿透。
  useBodyScrollLock(Boolean(editing));
  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4"><div><div className="flex items-center gap-2"><h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">轮播海报与页面装修</h2><span className="text-xs bg-rose-50 text-rose-700 font-semibold px-2 py-0.5 rounded-full border border-rose-200">视觉营销</span></div><p className="text-xs md:text-sm text-[#434655] mt-0.5">配置首页焦点轮播、活动公告及定时发布内容。</p></div><button onClick={() => setEditing({ ...emptyForm })} className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs"><Plus className="w-4 h-4" />新增轮播海报</button></div>
      {loadError ? <div className="bg-white rounded-xl border border-red-200 py-16 text-center text-sm text-red-600"><p>{loadError}</p><button type="button" onClick={() => void loadBanners()} className="mt-3 inline-flex items-center gap-1 text-blue-600 hover:underline"><RefreshCw className="w-4 h-4" />重新加载</button></div> : loading ? <div className="flex items-center justify-center py-20 text-sm text-gray-500"><Loader2 className="w-4 h-4 mr-2 animate-spin" />正在加载 Banner…</div> : banners.length === 0 ? <div className="bg-white rounded-xl border border-dashed border-[#CBD5E1] py-20 text-center text-sm text-gray-500"><ImageIcon className="w-8 h-8 mx-auto mb-3 text-gray-300" />暂无 Banner，点击右上角新增内容。</div> : <div className="grid grid-cols-1 md:grid-cols-3 gap-5">{banners.map((banner) => { const busy = actionId === banner.id; return <div key={banner.id} className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden flex flex-col justify-between"><div><div className="relative h-36 w-full bg-gray-100 overflow-hidden"><img src={banner.imageAccessUrl || banner.imageUrl} alt={banner.bannerTitle} className="w-full h-full object-cover hover:scale-105 transition-transform duration-300" /><div className="absolute top-2 left-2"><span className="text-[11px] font-bold bg-black/60 text-white px-2 py-0.5 rounded">{banner.linkType}</span></div><div className="absolute top-2 right-2"><button disabled={busy} onClick={() => void toggleStatus(banner)} className={`text-[11px] font-bold px-2 py-0.5 rounded shadow-xs disabled:opacity-60 ${banner.status === 1 ? 'bg-emerald-500 text-white' : 'bg-gray-500 text-white'}`}>{busy ? '处理中…' : banner.status === 1 ? '已启用' : '已停用'}</button></div></div><div className="p-4"><h3 className="font-bold text-gray-900 text-sm mb-1 line-clamp-1">{banner.bannerTitle}</h3><div className="text-xs text-gray-400 font-mono mb-3 flex items-center gap-1 truncate"><ExternalLink className="w-3 h-3 text-blue-500 shrink-0" />{banner.linkTarget || '无跳转'}</div><div className="grid grid-cols-2 gap-2 bg-gray-50 p-2.5 rounded-lg text-center text-xs"><div><span className="text-gray-400 block text-[10px]">排序</span><strong className="text-gray-800 font-mono">{banner.sortNo}</strong></div><div><span className="text-gray-400 block text-[10px]">发布时间</span><strong className="text-gray-800 font-mono">{displayDate(banner.startAt)}</strong></div></div></div></div><div className="p-4 pt-0 flex items-center justify-between text-xs text-gray-400"><span>{displayDate(banner.startAt)} ~ {displayDate(banner.endAt)}</span><div className="flex items-center gap-1"><button disabled={busy} onClick={() => setEditing(toForm(banner))} className="p-1 text-gray-400 hover:text-blue-600 rounded disabled:opacity-40" title="编辑"><Edit3 className="w-4 h-4" /></button><button disabled={busy} onClick={() => void remove(banner.id)} className="p-1 text-gray-400 hover:text-red-600 rounded disabled:opacity-40" title="删除"><Trash2 className="w-4 h-4" /></button></div></div></div>; })}</div>}
      {editing && <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4"><form onSubmit={submit} className="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto p-6 space-y-4"><div className="flex items-center justify-between"><h3 className="text-lg font-bold text-gray-900">{editing.id ? '编辑 Banner' : '新增 Banner'}</h3><button type="button" onClick={() => setEditing(null)} className="text-gray-400 hover:text-gray-700"><X className="w-5 h-5" /></button></div><div className="grid grid-cols-1 md:grid-cols-2 gap-4"><label className="text-xs text-gray-600 md:col-span-2">标题<input required maxLength={200} value={editing.bannerTitle} onChange={(e) => update('bannerTitle', e.target.value)} className="mt-1 w-full border rounded-lg px-3 py-2 text-sm" /></label><div className="text-xs text-gray-600 md:col-span-2"><span>海报图片</span><div className="mt-1 flex items-start gap-3"><div className="w-28 h-16 rounded-lg border border-gray-200 bg-gray-50 overflow-hidden flex items-center justify-center shrink-0">{editing.imageAccessUrl || /^https?:\/\//.test(editing.imageUrl) ? <img src={editing.imageAccessUrl || editing.imageUrl} alt="海报预览" className="w-full h-full object-cover" /> : <ImageIcon className="w-5 h-5 text-gray-300" />}</div><div className="flex-1 space-y-2"><div className="flex items-center gap-2"><label className={`h-[34px] px-3 rounded-lg border text-xs font-semibold flex items-center gap-1.5 cursor-pointer ${uploading ? 'opacity-60 pointer-events-none' : 'hover:border-blue-400 hover:text-blue-600'}`}>{uploading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Upload className="w-3.5 h-3.5" />}{uploading ? '上传中…' : '选择图片'}<input type="file" accept="image/jpeg,image/png,image/webp,image/gif" className="hidden" onChange={(event) => { const file = event.target.files?.[0]; if (file) void handleImageUpload(file); event.target.value = ''; }} /></label><span className="text-[11px] text-gray-400">JPG/PNG/WEBP/GIF，不超过 10MB</span></div><input required maxLength={1024} value={editing.imageUrl} onChange={(e) => updateImageUrl(e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm" placeholder="上传后自动填入，也可粘贴外部图片地址" /></div></div></div><label className="text-xs text-gray-600">标签<input maxLength={128} value={editing.bannerTag} onChange={(e) => update('bannerTag', e.target.value)} className="mt-1 w-full border rounded-lg px-3 py-2 text-sm" /></label><label className="text-xs text-gray-600">排序<input type="number" value={editing.sortNo} onChange={(e) => update('sortNo', Number(e.target.value))} className="mt-1 w-full border rounded-lg px-3 py-2 text-sm" /></label><label className="text-xs text-gray-600">跳转类型<select value={editing.linkType} onChange={(e) => update('linkType', e.target.value)} className="mt-1 w-full border rounded-lg px-3 py-2 text-sm"><option value="NONE">无跳转</option><option value="PRODUCT">商品</option><option value="CATEGORY">类目</option><option value="COUPON">优惠券</option><option value="URL">外部链接</option></select></label><label className="text-xs text-gray-600">跳转目标<input value={editing.linkTarget} onChange={(e) => update('linkTarget', e.target.value)} className="mt-1 w-full border rounded-lg px-3 py-2 text-sm" placeholder={editing.linkType === 'NONE' ? '无跳转时留空' : '商品ID、类目编码或 URL'} /></label><label className="text-xs text-gray-600">开始时间<input type="datetime-local" value={editing.startAt} onChange={(e) => update('startAt', e.target.value)} className="mt-1 w-full border rounded-lg px-3 py-2 text-sm" /></label><label className="text-xs text-gray-600">结束时间<input type="datetime-local" value={editing.endAt} onChange={(e) => update('endAt', e.target.value)} className="mt-1 w-full border rounded-lg px-3 py-2 text-sm" /></label><label className="text-xs text-gray-600">状态<select value={editing.status} onChange={(e) => update('status', Number(e.target.value))} className="mt-1 w-full border rounded-lg px-3 py-2 text-sm"><option value={1}>启用</option><option value={0}>停用</option></select></label><label className="text-xs text-gray-600 md:col-span-2">副标题<textarea maxLength={500} value={editing.subtitle} onChange={(e) => update('subtitle', e.target.value)} className="mt-1 w-full border rounded-lg px-3 py-2 text-sm" rows={2} /></label></div><div className="flex justify-end gap-2 pt-2"><button type="button" onClick={() => setEditing(null)} className="px-4 py-2 rounded-lg border text-sm">取消</button><button disabled={saving} type="submit" className="px-4 py-2 rounded-lg bg-blue-600 text-white text-sm flex items-center gap-2 disabled:opacity-60">{saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}保存</button></div></form></div>}
    </div>
  );
};
