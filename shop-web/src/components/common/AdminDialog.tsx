import React, { useEffect, useState } from 'react';
import { AlertTriangle, Check, X } from 'lucide-react';

export interface AdminDialogRequest {
  kind: 'confirm' | 'prompt';
  title: string;
  message: string;
  defaultValue?: string;
  resolve: (value: boolean | string | null) => void;
}

interface AdminDialogProps {
  request: AdminDialogRequest | null;
  onResolve: (request: AdminDialogRequest, value: boolean | string | null) => void;
}

/** 管理端统一确认/输入弹框，替代浏览器原生 alert、confirm 和 prompt。 */
export const AdminDialog: React.FC<AdminDialogProps> = ({ request, onResolve }) => {
  const [value, setValue] = useState('');

  useEffect(() => {
    setValue(request?.defaultValue || '');
  }, [request]);

  if (!request) return null;

  const close = (result: boolean | string | null) => onResolve(request, result);
  const isPrompt = request.kind === 'prompt';

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/45 p-4 backdrop-blur-[2px]" role="presentation">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="admin-dialog-title"
        className="w-full max-w-md overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl animate-in fade-in zoom-in-95 duration-200"
      >
        <div className="flex items-start gap-3 border-b border-slate-100 px-5 py-4">
          <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-amber-50 text-amber-600">
            <AlertTriangle className="h-5 w-5" />
          </span>
          <div className="min-w-0 flex-1">
            <h2 id="admin-dialog-title" className="text-base font-bold text-slate-900">{request.title}</h2>
            <p className="mt-1.5 whitespace-pre-wrap text-sm leading-6 text-slate-600">{request.message}</p>
          </div>
          <button type="button" onClick={() => close(isPrompt ? null : false)} className="rounded-lg p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700" aria-label="关闭">
            <X className="h-4 w-4" />
          </button>
        </div>

        {isPrompt && (
          <div className="px-5 pt-4">
            <textarea
              autoFocus
              value={value}
              onChange={(event) => setValue(event.target.value)}
              rows={4}
              placeholder="请输入内容（可选）"
              className="w-full resize-none rounded-xl border border-slate-200 px-3 py-2.5 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10"
            />
          </div>
        )}

        <div className="flex justify-end gap-2 px-5 py-4">
          <button type="button" onClick={() => close(isPrompt ? null : false)} className="h-9 rounded-lg border border-slate-200 px-4 text-sm font-medium text-slate-600 transition hover:bg-slate-50">
            取消
          </button>
          <button type="button" onClick={() => close(isPrompt ? value : true)} className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-blue-600 px-4 text-sm font-semibold text-white transition hover:bg-blue-700">
            <Check className="h-4 w-4" />
            {isPrompt ? '确定' : '确认'}
          </button>
        </div>
      </div>
    </div>
  );
};
