import React, { useCallback, useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import {
  BackendAiFaq,
  BackendAiKnowledgeStatus,
  BackendAiRecallTest,
  deleteAiFaq,
  exportAiFaqs,
  getAiKnowledgeStatus,
  listAiFaqs,
  retryFailedAiKnowledge,
  saveAiFaq,
  syncAiKnowledge,
  testAiKnowledgeRecall,
} from '../../api/adminApi';
import { PermissionGate } from '../common/PermissionGate';
import { formatDateTime } from '../../utils/datetime';
import { useBodyScrollLock } from '../../hooks/useBodyScrollLock';
import {
  AlertCircle,
  AlertTriangle,
  BookOpen,
  Download,
  FlaskConical,
  Loader2,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Trash2,
  X,
} from 'lucide-react';

const PAGE_SIZE = 20;

/** 与 ai_faq.category 的取值一致，除 OTHER 外都不是商品类目。 */
const CATEGORY_OPTIONS = [
  { value: 'AFTER_SALE', label: '售后' },
  { value: 'SHIPPING', label: '物流' },
  { value: 'PAYMENT', label: '支付' },
  { value: 'INVOICE', label: '发票' },
  { value: 'MEMBER', label: '会员' },
  { value: 'OTHER', label: '其他' },
];

const CATEGORY_LABELS: Record<string, string> = CATEGORY_OPTIONS.reduce(
  (acc, option) => ({ ...acc, [option.value]: option.label }),
  {},
);

const SYNC_STATUS_META: Record<string, { label: string; chip: string }> = {
  SYNCED: { label: '已同步', chip: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  PENDING: { label: '待同步', chip: 'bg-amber-50 text-amber-700 border-amber-200' },
  FAILED: { label: '同步失败', chip: 'bg-rose-50 text-rose-700 border-rose-200' },
};

function syncStatusMeta(status?: string) {
  return SYNC_STATUS_META[status || ''] || {
    label: status || '未同步',
    chip: 'bg-slate-100 text-slate-600 border-slate-200',
  };
}

const EMPTY_EDITOR = {
  id: undefined as number | undefined,
  question: '',
  answer: '',
  category: 'AFTER_SALE',
  keywords: '',
  sortNo: 0,
  enabled: 1,
};

type EditorState = typeof EMPTY_EDITOR;

/**
 * 知识库运营页。
 *
 * 三块内容按维护动作的顺序排：先看同步健康度，再用召回测试找知识缺口，然后改问答，
 * 改完回到召回测试复测——这是「改了之后有没有变好」唯一能被回答的路径，也是这一页存在
 * 的理由。所以召回测试不调用大模型，只有一次向量检索加一次精排，运营可以反复点。
 *
 * 同步是花钱的动作（embedding 按量计费），所以默认只做增量，强制重建要二次确认。
 *
 * @author Henfon
 * @date 2026-09-21
 */
export const AiKnowledgeView: React.FC = () => {
  const { showToast, confirm } = useAdmin();

  // 同步状态
  const [status, setStatus] = useState<BackendAiKnowledgeStatus | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [statusLoading, setStatusLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [retrying, setRetrying] = useState(false);

  // 召回测试
  const [recallQuestion, setRecallQuestion] = useState('');
  const [recallCategory, setRecallCategory] = useState('');
  const [recallResult, setRecallResult] = useState<BackendAiRecallTest | null>(null);
  const [recallRunning, setRecallRunning] = useState(false);
  const [recallError, setRecallError] = useState<string | null>(null);

  // 问答列表
  const [records, setRecords] = useState<BackendAiFaq[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [current, setCurrent] = useState(1);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);
  const [keywordInput, setKeywordInput] = useState('');
  const [categoryInput, setCategoryInput] = useState('');
  const [enabledInput, setEnabledInput] = useState('');
  const [keywordFilter, setKeywordFilter] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [enabledFilter, setEnabledFilter] = useState('');
  const [exporting, setExporting] = useState(false);

  // 编辑弹窗
  const [editor, setEditor] = useState<EditorState | null>(null);
  const [saving, setSaving] = useState(false);

  useBodyScrollLock(Boolean(editor));

  const loadStatus = useCallback(async () => {
    setStatusLoading(true);
    setStatusError(null);
    try {
      setStatus(await getAiKnowledgeStatus());
    } catch (error) {
      setStatus(null);
      setStatusError(error instanceof Error ? error.message : '同步状态加载失败');
    } finally {
      setStatusLoading(false);
    }
  }, []);

  const loadFaqs = useCallback(async (page: number) => {
    setListLoading(true);
    setListError(null);
    try {
      const data = await listAiFaqs({
        current: page,
        size: PAGE_SIZE,
        keyword: keywordFilter.trim() || undefined,
        category: categoryFilter || undefined,
        enabled: enabledFilter === '' ? undefined : Number(enabledFilter),
      });
      setRecords(data.records || []);
      setTotal(data.total || 0);
      setTotalPages(Math.max(data.pages || 1, 1));
      setCurrent(data.current || page);
    } catch (error) {
      setRecords([]);
      setTotal(0);
      setTotalPages(1);
      setListError(error instanceof Error ? error.message : '知识条目加载失败');
    } finally {
      setListLoading(false);
    }
  }, [keywordFilter, categoryFilter, enabledFilter]);

  useEffect(() => {
    void loadStatus();
  }, [loadStatus]);

  useEffect(() => {
    void loadFaqs(current);
    // 筛选条件变化时回到第一页由查询按钮显式触发，这里只负责在筛选或页码变化后重新拉取。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadFaqs, current]);

  const runSync = async (force: boolean) => {
    if (force) {
      const agreed = await confirm(
        '强制重建会忽略内容指纹，把全部商品与问答重新向量化一遍，重新消耗 embedding 额度。确定继续吗？',
        '强制重建向量索引',
      );
      if (!agreed) return;
    }
    setSyncing(true);
    try {
      const result = await syncAiKnowledge(force);
      showToast(
        `同步完成：商品 成功 ${result.product.success} / 跳过 ${result.product.skipped} / 失败 ${result.product.failed}，`
        + `问答 成功 ${result.faq.success} / 跳过 ${result.faq.skipped} / 失败 ${result.faq.failed}，`
        + `清理失效向量 ${result.staleRemoved} 个`,
        result.product.failed + result.faq.failed > 0 ? 'error' : 'success',
      );
      await loadStatus();
      await loadFaqs(current);
    } catch (error) {
      showToast(error instanceof Error ? error.message : '同步失败，请稍后重试', 'error');
    } finally {
      setSyncing(false);
    }
  };

  const runRetry = async () => {
    setRetrying(true);
    try {
      const result = await retryFailedAiKnowledge();
      showToast(
        result.total === 0
          ? '没有需要重试的失败项'
          : `重试完成：共 ${result.total} 条，成功 ${result.success}，仍失败 ${result.failed}`,
        result.failed > 0 ? 'error' : 'success',
      );
      await loadStatus();
      await loadFaqs(current);
    } catch (error) {
      showToast(error instanceof Error ? error.message : '重试失败，请稍后重试', 'error');
    } finally {
      setRetrying(false);
    }
  };

  const runRecall = async () => {
    const question = recallQuestion.trim();
    if (!question) {
      showToast('请先输入测试问句', 'error');
      return;
    }
    setRecallRunning(true);
    setRecallError(null);
    try {
      setRecallResult(await testAiKnowledgeRecall({
        question,
        categoryCode: recallCategory.trim() || undefined,
      }));
    } catch (error) {
      setRecallResult(null);
      setRecallError(error instanceof Error ? error.message : '召回测试失败');
    } finally {
      setRecallRunning(false);
    }
  };

  const openEditor = (faq: BackendAiFaq | null) => {
    setEditor(faq
      ? {
          id: faq.id,
          question: faq.question || '',
          answer: faq.answer || '',
          category: faq.category || 'AFTER_SALE',
          keywords: faq.keywords || '',
          sortNo: faq.sortNo ?? 0,
          enabled: faq.enabled ?? 1,
        }
      : { ...EMPTY_EDITOR });
  };

  const submitEditor = async () => {
    if (!editor) return;
    if (!editor.question.trim() || !editor.answer.trim()) {
      showToast('标准问法与标准答案都不能为空', 'error');
      return;
    }
    setSaving(true);
    try {
      await saveAiFaq({
        id: editor.id,
        question: editor.question.trim(),
        answer: editor.answer.trim(),
        category: editor.category || undefined,
        keywords: editor.keywords.trim() || undefined,
        sortNo: editor.sortNo,
        enabled: editor.enabled,
      });
      showToast(
        editor.id
          ? '已保存，该条目已标为待同步，记得点「增量同步」让改动生效'
          : '已新增，该条目已标为待同步，记得点「增量同步」让改动生效',
        'success',
      );
      setEditor(null);
      await loadFaqs(current);
      await loadStatus();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '保存失败，请稍后重试', 'error');
    } finally {
      setSaving(false);
    }
  };

  const removeFaq = async (faq: BackendAiFaq) => {
    const agreed = await confirm(
      `删除后该条目不再参与召回，其向量点会在下次同步的清理阶段被移除。确定删除「${faq.question}」吗？`,
      '删除知识条目',
    );
    if (!agreed) return;
    try {
      await deleteAiFaq(faq.id);
      showToast('已删除，记得点「增量同步」让向量库同步这条变更', 'success');
      await loadFaqs(current);
      await loadStatus();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '删除失败，请稍后重试', 'error');
    }
  };

  const failedCount = status?.failed || 0;
  const aiEnabled = status?.enabled !== false;

  /**
   * 按当前筛选导出 CSV。
   *
   * 文本前面加 UTF-8 BOM：Excel 打开没有 BOM 的 UTF-8 CSV 会把中文显示成乱码，而运营拿到
   * 文件基本都是直接双击用 Excel 开，让他们先选编码再导入等于没导出。
   */
  const runExport = async () => {
    setExporting(true);
    try {
      const csv = await exportAiFaqs({
        keyword: keywordFilter || undefined,
        category: categoryFilter || undefined,
        enabled: enabledFilter === '' ? undefined : Number(enabledFilter),
      });
      const url = URL.createObjectURL(new Blob([`﻿${csv}`], { type: 'text/csv;charset=utf-8' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = `知识库_${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.csv`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      showToast('已按当前筛选导出', 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '导出失败，请稍后重试', 'error');
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      <div>
        <div className="flex items-center gap-2">
          <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">知识库运营</h2>
          <span className="text-xs bg-blue-50 text-blue-700 font-semibold px-2 py-0.5 rounded-full border border-blue-200 inline-flex items-center gap-1">
            <BookOpen className="w-3 h-3" />问答与检索
          </span>
        </div>
        <p className="text-xs md:text-sm text-[#434655] mt-0.5">
          维护平台规则问答、查看向量同步状态，并用召回测试验证改动是否真的生效。
        </p>
      </div>

      <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5 md:p-6 space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h3 className="text-base font-semibold text-gray-900">向量同步</h3>
            <p className="text-xs text-gray-500 mt-0.5">
              同步按内容指纹跳过未改动的条目，只有新增、修改、删除的条目会重新向量化。「待同步」指问答被改过但还没重新索引的条数；商品改动由下次同步自行发现。
            </p>
          </div>
          <PermissionGate
            permission="ai:knowledge:sync"
            fallback={<span className="text-xs text-gray-500">当前账号没有同步权限</span>}
          >
            <div className="flex items-center gap-2">
              <button
                type="button"
                disabled={syncing || retrying || !aiEnabled}
                onClick={() => void runSync(false)}
                className="h-9 px-4 bg-[#2563EB] text-white rounded-lg text-sm font-semibold hover:bg-blue-700 inline-flex items-center gap-1.5 disabled:opacity-60"
              >
                {syncing ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
                {syncing ? '同步中…' : '增量同步'}
              </button>
              <button
                type="button"
                disabled={syncing || retrying || !aiEnabled}
                onClick={() => void runSync(true)}
                className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-60"
              >
                强制重建
              </button>
              <button
                type="button"
                disabled={syncing || retrying || !aiEnabled || failedCount === 0}
                onClick={() => void runRetry()}
                className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed inline-flex items-center gap-1.5"
              >
                {retrying ? <Loader2 className="w-4 h-4 animate-spin" /> : <AlertTriangle className="w-4 h-4" />}
                重试失败项 {failedCount > 0 ? `(${failedCount})` : ''}
              </button>
            </div>
          </PermissionGate>
        </div>

        {statusLoading ? (
          <p className="text-sm text-gray-500 py-2" role="status">
            <Loader2 className="w-4 h-4 inline animate-spin mr-1.5" />同步状态加载中…
          </p>
        ) : statusError ? (
          <div className="py-4 text-sm text-red-700" role="alert">
            <AlertCircle className="w-5 h-5 inline mr-1.5 align-text-bottom" />
            {statusError}
            <button type="button" onClick={() => void loadStatus()} className="ml-3 text-blue-700 hover:underline">
              重新加载
            </button>
          </div>
        ) : status ? (
          <>
            {!aiEnabled ? (
              <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                AI 客服当前未启用，同步与召回测试不可用。需先配置百炼与 Qdrant 并打开开关。
              </p>
            ) : null}
            <dl className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {[
                { label: '位点总数', value: status.total, tone: 'text-gray-900' },
                { label: '已同步', value: status.synced, tone: 'text-emerald-700' },
                { label: '待同步', value: status.pending, tone: 'text-amber-700' },
                { label: '同步失败', value: status.failed, tone: status.failed > 0 ? 'text-rose-700' : 'text-gray-900' },
              ].map((item) => (
                <div key={item.label} className="rounded-lg border border-[#E2E8F0] px-3 py-2.5">
                  <dt className="text-xs text-gray-500">{item.label}</dt>
                  <dd className={`text-lg font-bold ${item.tone}`}>{item.value ?? 0}</dd>
                </div>
              ))}
            </dl>

            {Object.keys(status.bySource || {}).length > 0 && (
              <div className="text-xs text-gray-600 space-y-1">
                {Object.entries(status.bySource).map(([sourceType, counts]) => (
                  <div key={sourceType}>
                    <span className="font-semibold text-gray-800">
                      {sourceType === 'PRODUCT' ? '商品' : sourceType === 'FAQ' ? '平台问答' : sourceType}
                    </span>
                    <span className="ml-2">
                      {Object.entries(counts).map(([key, value]) => `${syncStatusMeta(key).label} ${value}`).join(' · ')}
                    </span>
                  </div>
                ))}
              </div>
            )}

            {status.failures.length > 0 && (
              <div className="border-t border-gray-100 pt-3">
                <h4 className="text-xs font-semibold text-gray-500 mb-2">失败明细（最多 50 条）</h4>
                <div className="overflow-x-auto">
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="text-left text-gray-500 border-b">
                        <th className="py-1.5 pr-3">来源</th>
                        <th className="py-1.5 pr-3">来源 ID</th>
                        <th className="py-1.5 pr-3">重试次数</th>
                        <th className="py-1.5 pr-3">错误</th>
                        <th className="py-1.5">最近更新</th>
                      </tr>
                    </thead>
                    <tbody>
                      {status.failures.map((failure) => (
                        <tr key={failure.id} className="border-b border-gray-50">
                          <td className="py-1.5 pr-3 whitespace-nowrap text-gray-700">
                            {failure.sourceType === 'PRODUCT' ? '商品' : failure.sourceType === 'FAQ' ? '平台问答' : failure.sourceType}
                          </td>
                          <td className="py-1.5 pr-3 whitespace-nowrap font-mono text-gray-600">{failure.sourceId}</td>
                          <td className="py-1.5 pr-3 whitespace-nowrap text-gray-600">{failure.retryCount ?? 0}</td>
                          <td className="py-1.5 pr-3 text-rose-700 max-w-[380px]">
                            <span className="line-clamp-2">{failure.errorMessage || '-'}</span>
                          </td>
                          <td className="py-1.5 whitespace-nowrap text-gray-500">{formatDateTime(failure.updatedAt, '-')}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </>
        ) : null}
      </section>

      <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5 md:p-6 space-y-4">
        <div className="flex items-center gap-2.5">
          <div className="p-2 bg-blue-50 text-blue-600 rounded-lg"><FlaskConical className="w-5 h-5" /></div>
          <div>
            <h3 className="text-base font-semibold text-gray-900">召回测试</h3>
            <p className="text-xs text-gray-500">
              只用买家的原话跑一遍召回与精排，不调用大模型，可以反复点。
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[260px] max-w-xl">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
            <input
              type="text"
              id="ai-recall-question"
              name="recallQuestion"
              value={recallQuestion}
              onChange={(event) => setRecallQuestion(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') void runRecall();
              }}
              placeholder="输入买家的问法，例如：买错了想退货，运费谁出？"
              className="w-full h-[36px] pl-9 pr-3 text-sm rounded-lg border border-[#E2E8F0] bg-white outline-none focus:border-blue-500"
            />
          </div>
          <input
            type="text"
            id="ai-recall-category"
            name="recallCategory"
            value={recallCategory}
            onChange={(event) => setRecallCategory(event.target.value)}
            placeholder="类目编码（可留空）"
            aria-label="类目编码过滤"
            className="w-[180px] h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white outline-none focus:border-blue-500"
          />
          <button
            type="button"
            disabled={recallRunning}
            onClick={() => void runRecall()}
            className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white text-sm font-semibold hover:bg-blue-700 inline-flex items-center gap-1.5 disabled:opacity-60"
          >
            {recallRunning ? <Loader2 className="w-4 h-4 animate-spin" /> : <FlaskConical className="w-4 h-4" />}
            {recallRunning ? '测试中…' : '测试召回'}
          </button>
        </div>

        {recallError ? (
          <div className="py-3 text-sm text-red-700" role="alert">
            <AlertCircle className="w-5 h-5 inline mr-1.5 align-text-bottom" />{recallError}
          </div>
        ) : null}

        {recallResult ? (
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-3 text-xs">
              <span
                className={`px-2 py-0.5 rounded-full border font-semibold ${
                  recallResult.hit
                    ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                    : 'bg-amber-50 text-amber-700 border-amber-200'
                }`}
              >
                {recallResult.hit ? '命中，资料会喂给模型' : '未命中，模型会答查不到'}
              </span>
              <span className="text-gray-600">
                最高相似度 <span className="font-mono font-semibold text-gray-900">{recallResult.bestScore.toFixed(4)}</span>
              </span>
              <span className="text-gray-600">
                阈值 <span className="font-mono font-semibold text-gray-900">{recallResult.threshold}</span>
              </span>
              <span className="text-gray-500">召回 {recallResult.chunks.length} 条</span>
            </div>

            {recallResult.chunks.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="w-full text-xs">
                  <thead>
                    <tr className="text-left text-gray-500 border-b">
                      <th className="py-1.5 pr-3">来源</th>
                      <th className="py-1.5 pr-3">向量分</th>
                      <th className="py-1.5 pr-3">精排分</th>
                      <th className="py-1.5">命中正文</th>
                    </tr>
                  </thead>
                  <tbody>
                    {recallResult.chunks.map((chunk, index) => (
                      <tr key={`${chunk.sourceId}-${index}`} className="border-b border-gray-50 align-top">
                        <td className="py-2 pr-3 text-gray-800">{chunk.label}</td>
                        <td className="py-2 pr-3 font-mono text-gray-600">{chunk.vectorScore.toFixed(4)}</td>
                        <td className="py-2 pr-3 font-mono text-gray-600">
                          {chunk.rerankScore == null ? '未精排' : chunk.rerankScore.toFixed(4)}
                        </td>
                        <td className="py-2 text-gray-600 max-w-[420px]">
                          <span className="line-clamp-3 whitespace-pre-wrap">{chunk.text}</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-xs text-gray-500">
                这次一条都没召回。低于阈值的召回也会列出来，这里是空列表说明向量库里确实没有相近内容。
              </p>
            )}

            <details className="rounded-lg border border-[#E2E8F0]">
              <summary className="px-3 py-2 text-xs font-semibold text-gray-700 cursor-pointer">
                查看拼给模型的资料原文
              </summary>
              <pre className="px-3 pb-3 text-xs text-gray-700 whitespace-pre-wrap font-sans">
                {recallResult.context || '（空）'}
              </pre>
            </details>
          </div>
        ) : null}
      </section>

      <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5 md:p-6 space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h3 className="text-base font-semibold text-gray-900">平台问答</h3>
            <p className="text-xs text-gray-500 mt-0.5">
              只改了答案不需要重新同步（答案不参与向量化）；改问法或关键词才需要同步。
            </p>
          </div>
          <PermissionGate
            permission="ai:faq:save"
            fallback={<span className="text-xs text-gray-500">当前账号没有维护权限</span>}
          >
            <button
              type="button"
              onClick={() => openEditor(null)}
              className="h-9 px-4 bg-[#2563EB] text-white rounded-lg text-sm font-semibold hover:bg-blue-700 inline-flex items-center gap-1.5"
            >
              <Plus className="w-4 h-4" />新增条目
            </button>
          </PermissionGate>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[220px] max-w-md">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
            <input
              type="search"
              id="ai-faq-keyword"
              name="keyword"
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  setKeywordFilter(keywordInput);
                  setCurrent(1);
                }
              }}
              placeholder="搜索问法或答案…"
              className="w-full h-[36px] pl-9 pr-3 text-sm rounded-lg border border-[#E2E8F0] bg-white outline-none focus:border-blue-500"
            />
          </div>
          <select
            id="ai-faq-category"
            name="category"
            value={categoryInput}
            onChange={(event) => {
              setCategoryInput(event.target.value);
              setCategoryFilter(event.target.value);
              setCurrent(1);
            }}
            aria-label="业务分类"
            className="h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white text-gray-700 outline-none"
          >
            <option value="">全部分类</option>
            {CATEGORY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
          <select
            id="ai-faq-enabled"
            name="enabled"
            value={enabledInput}
            onChange={(event) => {
              setEnabledInput(event.target.value);
              setEnabledFilter(event.target.value);
              setCurrent(1);
            }}
            aria-label="启用状态"
            className="h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white text-gray-700 outline-none"
          >
            <option value="">全部状态</option>
            <option value="1">已启用</option>
            <option value="0">已停用</option>
          </select>
          <button
            type="button"
            onClick={() => {
              setKeywordFilter(keywordInput);
              setCurrent(1);
            }}
            className="h-[36px] px-3 rounded-lg bg-[#2563EB] text-white text-sm font-semibold hover:bg-blue-700"
          >
            查询
          </button>
          <button
            type="button"
            onClick={() => void loadFaqs(current)}
            className="h-[36px] px-3 rounded-lg border border-[#E2E8F0] text-sm text-gray-700 hover:bg-gray-50 inline-flex items-center gap-1.5"
          >
            <RefreshCw className="w-4 h-4" />刷新
          </button>
          <button
            type="button"
            disabled={exporting}
            onClick={() => void runExport()}
            className="h-[36px] px-3 rounded-lg border border-[#E2E8F0] text-sm text-gray-700 hover:bg-gray-50 inline-flex items-center gap-1.5 disabled:opacity-60"
          >
            {exporting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}导出
          </button>
        </div>

        <div className="overflow-x-auto">
          {listError ? (
            <div className="py-10 text-center text-sm text-red-700" role="alert">
              <AlertCircle className="w-6 h-6 mx-auto mb-2" />
              <p>{listError}</p>
              <button
                type="button"
                onClick={() => void loadFaqs(current)}
                className="mt-3 inline-flex items-center gap-1.5 text-blue-700 hover:underline"
              >
                <RefreshCw className="w-4 h-4" />重新加载
              </button>
            </div>
          ) : listLoading ? (
            <p className="py-10 text-center text-sm text-gray-500" role="status">
              <Loader2 className="w-4 h-4 inline animate-spin mr-1.5" />知识条目加载中…
            </p>
          ) : records.length === 0 ? (
            <p className="py-10 text-center text-sm text-gray-400" role="status">暂无知识条目</p>
          ) : (
            <table className="w-full text-xs">
              <thead>
                <tr className="text-left text-gray-500 border-b">
                  <th className="py-2 pr-3 w-[26%]">标准问法</th>
                  <th className="py-2 pr-3 w-[34%]">标准答案</th>
                  <th className="py-2 pr-3">分类</th>
                  <th className="py-2 pr-3">关键词</th>
                  <th className="py-2 pr-3">启用</th>
                  <th className="py-2 pr-3">同步</th>
                  <th className="py-2">操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((item) => {
                  const meta = syncStatusMeta(item.syncStatus);
                  return (
                    <tr key={item.id} className="border-b border-gray-50 hover:bg-slate-50/60 align-top">
                      <td className="py-2.5 pr-3 text-gray-800 font-medium">{item.question}</td>
                      <td className="py-2.5 pr-3 text-gray-600">
                        <span className="line-clamp-2">{item.answer}</span>
                      </td>
                      <td className="py-2.5 pr-3 whitespace-nowrap text-gray-600">
                        {CATEGORY_LABELS[item.category || ''] || item.category || '-'}
                      </td>
                      <td className="py-2.5 pr-3 text-gray-500 max-w-[160px]">
                        <span className="line-clamp-2">{item.keywords || '-'}</span>
                      </td>
                      <td className="py-2.5 pr-3 whitespace-nowrap">
                        <span
                          className={`px-1.5 py-0.5 rounded border font-semibold ${
                            item.enabled === 1
                              ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                              : 'bg-slate-100 text-slate-600 border-slate-200'
                          }`}
                        >
                          {item.enabled === 1 ? '启用' : '停用'}
                        </span>
                      </td>
                      <td className="py-2.5 pr-3 whitespace-nowrap">
                        <span className={`px-1.5 py-0.5 rounded border font-semibold ${meta.chip}`}>{meta.label}</span>
                      </td>
                      <td className="py-2.5 whitespace-nowrap">
                        <PermissionGate permission="ai:faq:save" fallback={null}>
                          <button
                            type="button"
                            onClick={() => openEditor(item)}
                            className="text-blue-700 hover:underline inline-flex items-center gap-1 mr-3"
                          >
                            <Pencil className="w-3 h-3" />编辑
                          </button>
                        </PermissionGate>
                        <PermissionGate permission="ai:faq:delete" fallback={null}>
                          <button
                            type="button"
                            onClick={() => void removeFaq(item)}
                            className="text-rose-600 hover:underline inline-flex items-center gap-1"
                          >
                            <Trash2 className="w-3 h-3" />删除
                          </button>
                        </PermissionGate>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        {!listError && !listLoading && (
          <div className="flex flex-wrap items-center justify-between gap-2 pt-3 border-t border-gray-100 text-xs text-gray-500">
            <span>共 {total} 条，第 {Math.min(current, totalPages)} / {totalPages} 页</span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setCurrent((page) => Math.max(1, page - 1))}
                disabled={current <= 1}
                className="h-8 px-3 rounded-lg border border-[#E2E8F0] font-semibold text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                上一页
              </button>
              <button
                type="button"
                onClick={() => setCurrent((page) => Math.min(totalPages, page + 1))}
                disabled={current >= totalPages}
                className="h-8 px-3 rounded-lg border border-[#E2E8F0] font-semibold text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                下一页
              </button>
            </div>
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
            aria-labelledby="ai-faq-editor-title"
            className="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-2xl border border-slate-200 bg-white shadow-2xl"
          >
            <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
              <div>
                <h2 id="ai-faq-editor-title" className="text-base font-bold text-slate-900">
                  {editor.id ? '编辑知识条目' : '新增知识条目'}
                </h2>
                <p className="mt-1 text-xs text-slate-500">
                  问法与关键词参与向量化，答案在命中后回表拼进模型上下文。
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
              <label className="flex flex-col gap-1 text-xs text-gray-500">
                标准问法
                <input
                  type="text"
                  id="ai-faq-question"
                  name="question"
                  value={editor.question}
                  onChange={(event) => setEditor({ ...editor, question: event.target.value })}
                  placeholder="买家可能怎么问，例如：买错了想退货，运费谁承担？"
                  className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500"
                />
              </label>

              <label className="flex flex-col gap-1 text-xs text-gray-500">
                标准答案
                <textarea
                  id="ai-faq-answer"
                  name="answer"
                  value={editor.answer}
                  onChange={(event) => setEditor({ ...editor, answer: event.target.value })}
                  rows={5}
                  placeholder="按平台真实口径回答。命中后模型会照抄这段内容，不要写承诺性的时效与金额。"
                  className="w-full px-3 py-2 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500 resize-y"
                />
              </label>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <label className="flex flex-col gap-1 text-xs text-gray-500">
                  业务分类
                  <select
                    id="ai-faq-editor-category"
                    name="editorCategory"
                    value={editor.category}
                    onChange={(event) => setEditor({ ...editor, category: event.target.value })}
                    className="h-9 px-3 rounded-lg border border-[#E2E8F0] bg-white text-sm text-gray-700 outline-none"
                  >
                    {CATEGORY_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                  </select>
                </label>
                <label className="flex flex-col gap-1 text-xs text-gray-500">
                  排序号
                  <input
                    type="number"
                    id="ai-faq-sort"
                    name="sortNo"
                    value={editor.sortNo}
                    onChange={(event) => setEditor({ ...editor, sortNo: Number(event.target.value) || 0 })}
                    className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500"
                  />
                </label>
              </div>

              <label className="flex flex-col gap-1 text-xs text-gray-500">
                检索关键词
                <input
                  type="text"
                  id="ai-faq-keywords"
                  name="keywords"
                  value={editor.keywords}
                  onChange={(event) => setEditor({ ...editor, keywords: event.target.value })}
                  placeholder="英文逗号分隔，补买家可能用的说法，例如：退货,运费,谁出"
                  className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-sm text-gray-800 outline-none focus:border-blue-500"
                />
              </label>

              <label className="flex items-center gap-2 text-xs text-gray-600">
                <input
                  type="checkbox"
                  id="ai-faq-enabled-check"
                  name="enabled"
                  checked={editor.enabled === 1}
                  onChange={(event) => setEditor({ ...editor, enabled: event.target.checked ? 1 : 0 })}
                  className="w-4 h-4 rounded border-[#E2E8F0]"
                />
                启用（停用后不参与向量化，其向量点会在下次同步时被清理）
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
