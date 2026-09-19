import React, { useMemo, useState } from 'react';
import { AlertTriangle, ArrowLeft, Search } from 'lucide-react';
import { BrandMark } from '../components/common/BrandMark';
import {
  ACTION_PERMISSIONS,
  GUIDE_GROUPS,
  GUIDE_SECTIONS,
  MENU_PERMISSIONS,
  type GuideSection,
} from './guideSections';

/**
 * 电商运营系统操作指南（独立页面）。
 *
 * 从顶栏问号以新标签页打开：`guide.html` 是 vite 的第二个入口，与主应用共用一份
 * Tailwind 产物，但不挂载 AdminContext、不发任何接口请求 —— 指南要在未登录时也能打开，
 * 也方便直接把地址发给别人。
 */

/** 把章节的可搜索文本拼起来，供关键字过滤使用。 */
function searchableText(section: GuideSection): string {
  return [
    section.title,
    section.group,
    section.summary,
    section.route || '',
    section.permission || '',
    ...(section.capabilities || []),
    ...(section.steps || []).flatMap((step) => [step.title, step.detail]),
    ...(section.notes || []),
  ]
    .join(' ')
    .toLowerCase();
}

const MetaChip: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <span className="inline-flex items-center gap-1 rounded-md border border-[#E2E8F0] bg-[#F8FAFC] px-2 py-0.5 text-[11px] text-slate-600">
    <span className="text-slate-400">{label}</span>
    <code className="font-mono text-[11px] text-slate-700">{value}</code>
  </span>
);

const SectionCard: React.FC<{ section: GuideSection }> = ({ section }) => (
  <section id={section.id} className="scroll-mt-24 rounded-xl border border-[#E2E8F0] bg-white p-5 sm:p-6">
    <div className="flex flex-wrap items-center gap-2 text-[11px] text-slate-400">
      <span>{section.group}</span>
      {section.route && <MetaChip label="菜单" value={section.route} />}
      {section.permission && <MetaChip label="权限" value={section.permission} />}
    </div>
    <h2 className="mt-2 text-lg font-semibold text-[#0F172A]">{section.title}</h2>
    <p className="mt-2 text-sm leading-6 text-slate-600">{section.summary}</p>

    {section.capabilities && section.capabilities.length > 0 && (
      <div className="mt-4">
        <h3 className="text-xs font-semibold tracking-wide text-slate-500">这个页面能做什么</h3>
        <ul className="mt-2 space-y-1.5">
          {section.capabilities.map((item) => (
            <li key={item} className="flex gap-2 text-sm leading-6 text-slate-600">
              <span aria-hidden="true" className="mt-2 h-1 w-1 shrink-0 rounded-full bg-blue-500" />
              <span>{item}</span>
            </li>
          ))}
        </ul>
      </div>
    )}

    {section.steps && section.steps.length > 0 && (
      <div className="mt-4">
        <h3 className="text-xs font-semibold tracking-wide text-slate-500">
          {section.capabilities ? '典型操作' : '操作步骤'}
        </h3>
        <ol className="mt-2 space-y-3">
          {section.steps.map((step, index) => (
            <li key={step.title} className="flex gap-3">
              <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-blue-50 text-[11px] font-semibold text-blue-700">
                {index + 1}
              </span>
              <p className="text-sm leading-6 text-slate-600">
                <span className="font-medium text-slate-900">{step.title}：</span>
                {step.detail}
              </p>
            </li>
          ))}
        </ol>
      </div>
    )}

    {section.notes && section.notes.length > 0 && (
      <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50/70 p-3">
        <p className="flex items-center gap-1.5 text-xs font-semibold text-amber-800">
          <AlertTriangle className="h-3.5 w-3.5" />
          注意
        </p>
        <ul className="mt-1.5 space-y-1">
          {section.notes.map((note) => (
            <li key={note} className="text-[13px] leading-6 text-amber-900">
              {note}
            </li>
          ))}
        </ul>
      </div>
    )}
  </section>
);

export const OperationsGuidePage: React.FC = () => {
  const [keyword, setKeyword] = useState('');
  const trimmed = keyword.trim().toLowerCase();

  const visibleSections = useMemo(
    () => (trimmed ? GUIDE_SECTIONS.filter((section) => searchableText(section).includes(trimmed)) : GUIDE_SECTIONS),
    [trimmed],
  );

  // 目录只列当前命中的章节，分组保持指南里的固定顺序。
  const groupedSections = useMemo(
    () =>
      GUIDE_GROUPS.map((group) => ({
        group,
        sections: visibleSections.filter((section) => section.group === group),
      })).filter((entry) => entry.sections.length > 0),
    [visibleSections],
  );

  const menuCount = MENU_PERMISSIONS.length;
  const showPermissionTable = !trimmed || '权限 菜单 路由 授权 permission'.toLowerCase().includes(trimmed);

  return (
    <div className="min-h-screen bg-[#F8FAFC] text-[#191C1E]">
      <a
        href="#guide-main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-white focus:px-3 focus:py-2 focus:text-sm focus:shadow"
      >
        跳到指南正文
      </a>

      <header className="sticky top-0 z-30 border-b border-[#E2E8F0] bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3 sm:px-6">
          <div className="flex items-center gap-2.5">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 text-white">
              <BrandMark className="h-4.5 w-4.5" />
            </span>
            <div className="leading-tight">
              <p className="text-sm font-semibold text-[#0F172A]">电商运营系统操作指南</p>
              <p className="text-[11px] text-slate-500">Henfon 电商后台 · 独立页面，可长期保留在标签页</p>
            </div>
          </div>
          <a
            href="/"
            className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-[#E2E8F0] px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            返回管理后台
          </a>
        </div>
      </header>

      <main id="guide-main" className="mx-auto max-w-6xl px-4 pb-16 pt-6 sm:px-6">
        <h1 className="text-2xl font-bold tracking-tight text-[#0F172A] sm:text-[28px]">
          电商运营系统操作指南
        </h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
          按后台侧边栏的分组逐块说明：每个模块的入口在哪、能做什么、典型操作怎么走，以及那些「看起来像 bug、
          其实是口径」的地方。共覆盖 {menuCount} 个功能页面，全部功能清单与当前版本一致。
        </p>

        <div className="mt-5 flex flex-wrap items-center gap-3">
          <div className="relative w-full max-w-sm">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              type="search"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索功能，例如 发货、导出、权限、秒杀…"
              aria-label="搜索指南章节"
              className="h-10 w-full rounded-lg border border-[#E2E8F0] bg-white pl-9 pr-3 text-sm text-slate-700 outline-none transition-colors placeholder:text-slate-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
            />
          </div>
          <p className="text-xs text-slate-500" role="status">
            {trimmed ? `匹配到 ${visibleSections.length} 个章节` : `共 ${GUIDE_SECTIONS.length} 个章节`}
          </p>
          {trimmed && (
            <button
              type="button"
              onClick={() => setKeyword('')}
              className="rounded-lg border border-[#E2E8F0] px-2.5 py-1.5 text-xs text-slate-600 transition-colors hover:bg-slate-50"
            >
              清空搜索
            </button>
          )}
        </div>

        <div className="mt-6 lg:grid lg:grid-cols-[240px_minmax(0,1fr)] lg:gap-6">
          <nav aria-label="指南目录" className="mb-6 lg:mb-0">
            <div className="lg:sticky lg:top-24 lg:max-h-[calc(100vh-7rem)] lg:overflow-y-auto lg:pr-1">
              <p className="text-[11px] font-semibold tracking-wide text-slate-400">目录</p>
              {groupedSections.length === 0 && (
                <p className="mt-3 text-xs text-slate-400">没有匹配的章节，换个词试试。</p>
              )}
              <div className="mt-2 space-y-3">
                {groupedSections.map((entry) => (
                  <div key={entry.group}>
                    <p className="text-xs font-semibold text-slate-500">{entry.group}</p>
                    <ul className="mt-1 space-y-0.5 border-l border-[#E2E8F0] pl-3">
                      {entry.sections.map((section) => (
                        <li key={section.id}>
                          <a
                            href={`#${section.id}`}
                            className="block rounded py-1 text-[13px] text-slate-600 transition-colors hover:text-blue-700"
                          >
                            {section.title}
                          </a>
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
                {showPermissionTable && (
                  <div>
                    <p className="text-xs font-semibold text-slate-500">附录</p>
                    <ul className="mt-1 space-y-0.5 border-l border-[#E2E8F0] pl-3">
                      <li>
                        <a
                          href="#permission-table"
                          className="block rounded py-1 text-[13px] text-slate-600 transition-colors hover:text-blue-700"
                        >
                          权限速查表
                        </a>
                      </li>
                    </ul>
                  </div>
                )}
              </div>
            </div>
          </nav>

          <div className="space-y-4">
            {groupedSections.map((entry) => (
              <React.Fragment key={entry.group}>
                <h2 className="pt-2 text-xs font-semibold tracking-wide text-slate-400 first:pt-0">
                  {entry.group}
                </h2>
                {entry.sections.map((section) => (
                  <SectionCard key={section.id} section={section} />
                ))}
              </React.Fragment>
            ))}

            {showPermissionTable && (
              <section id="permission-table" className="scroll-mt-24 rounded-xl border border-[#E2E8F0] bg-white p-5 sm:p-6">
                <h2 className="text-lg font-semibold text-[#0F172A]">权限速查表</h2>
                <p className="mt-2 text-sm leading-6 text-slate-600">
                  菜单看不到时，用这张表核对角色里该勾哪一项。权限编码同时是接口的鉴权标识，报错信息里出现的就是它。
                </p>

                <h3 className="mt-4 text-xs font-semibold tracking-wide text-slate-500">菜单权限</h3>
                <div className="mt-2 overflow-x-auto">
                  <table className="w-full min-w-[520px] border-collapse text-left text-sm">
                    <caption className="sr-only">菜单名称、路由地址与权限编码对照</caption>
                    <thead>
                      <tr className="border-b border-[#E2E8F0] text-xs text-slate-500">
                        <th scope="col" className="py-2 pr-4 font-medium">菜单名称</th>
                        <th scope="col" className="py-2 pr-4 font-medium">路由地址</th>
                        <th scope="col" className="py-2 font-medium">权限编码</th>
                      </tr>
                    </thead>
                    <tbody>
                      {MENU_PERMISSIONS.map(([name, route, permission]) => (
                        <tr key={permission} className="border-b border-slate-100 last:border-0">
                          <td className="py-2 pr-4 text-slate-700">{name}</td>
                          <td className="py-2 pr-4 font-mono text-xs text-slate-500">{route}</td>
                          <td className="py-2 font-mono text-xs text-slate-700">{permission}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                <h3 className="mt-6 text-xs font-semibold tracking-wide text-slate-500">按钮权限</h3>
                <div className="mt-2 overflow-x-auto">
                  <table className="w-full min-w-[620px] border-collapse text-left text-sm">
                    <caption className="sr-only">模块、操作与按钮权限编码对照</caption>
                    <thead>
                      <tr className="border-b border-[#E2E8F0] text-xs text-slate-500">
                        <th scope="col" className="py-2 pr-4 font-medium">模块</th>
                        <th scope="col" className="py-2 pr-4 font-medium">操作</th>
                        <th scope="col" className="py-2 font-medium">权限编码</th>
                      </tr>
                    </thead>
                    <tbody>
                      {ACTION_PERMISSIONS.map(([module, action, permission]) => (
                        <tr key={`${module}-${action}`} className="border-b border-slate-100 last:border-0">
                          <td className="py-2 pr-4 text-slate-700">{module}</td>
                          <td className="py-2 pr-4 text-slate-600">{action}</td>
                          <td className="py-2 font-mono text-xs text-slate-700">{permission}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            )}

            {visibleSections.length === 0 && (
              <div className="rounded-xl border border-dashed border-[#E2E8F0] bg-white p-10 text-center">
                <p className="text-sm text-slate-600">没有匹配「{keyword}」的章节</p>
                <p className="mt-1 text-xs text-slate-400">换个说法试试，比如模块名、按钮名或权限编码的一部分。</p>
              </div>
            )}
          </div>
        </div>
      </main>

      <footer className="border-t border-[#E2E8F0] bg-white py-6">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 text-xs text-slate-500 sm:px-6">
          <p>指南内容与菜单、权限编码保持同步；功能有增减时请一并更新本页。</p>
          <a href="/" className="font-medium text-blue-700 hover:text-blue-800">
            返回管理后台
          </a>
        </div>
      </footer>
    </div>
  );
};
