import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ChevronDown, ChevronRight, LayoutGrid } from 'lucide-react';
import { ALL_CATEGORY_ID, PortalCategoryNode, findCategoryPath } from '../api/portalApi';
import { CategoryGlyph } from './CategoryGlyph';

interface CategoryRailProps {
  categoryTree: PortalCategoryNode[];
  selectedCategory: string;
  onSelectCategory: (categoryId: string) => void;
  /** 抽屉形态下选中类目后需要收起抽屉。 */
  onNavigate?: () => void;
  /**
   * flyout：桌面端常驻左栏，一级 hover/focus 时右侧弹出下级浮层（京东、淘宝的左侧分类栏）；
   * inline：窄屏抽屉内的展开式，触摸屏没有 hover，默认走这套。
   */
  variant?: 'flyout' | 'inline';
}

/** 鼠标从左栏移进浮层要跨过间隙，留点延迟再收，避免路径中断时闪关。 */
const FLYOUT_CLOSE_DELAY_MS = 140;
const FLYOUT_ID = 'portal-category-flyout';

/** 选中类目在树上的完整路径，用来判断各级高亮。 */
const useSelectedPath = (categoryTree: PortalCategoryNode[], selectedCategory: string) => useMemo(
  () => (selectedCategory === ALL_CATEGORY_ID
    ? []
    : findCategoryPath(categoryTree, (node) => node.id === selectedCategory)),
  [categoryTree, selectedCategory]
);

const RailHeading: React.FC = () => (
  <div className="flex items-center gap-1.5 px-2 pb-2 text-[11px] font-bold uppercase tracking-wider text-zinc-400">
    <LayoutGrid className="h-3.5 w-3.5" />
    <span>全部分类</span>
  </div>
);

const AllCategoryButton: React.FC<{
  selectedCategory: string;
  onSelect: () => void;
}> = ({ selectedCategory, onSelect }) => (
  <button
    type="button"
    onClick={onSelect}
    aria-current={selectedCategory === ALL_CATEGORY_ID ? 'true' : undefined}
    className={`flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs font-medium transition ${
      selectedCategory === ALL_CATEGORY_ID
        ? 'bg-zinc-900 text-white shadow-xs'
        : 'text-zinc-700 hover:bg-zinc-100'
    }`}
  >
    <LayoutGrid className="h-3.5 w-3.5 shrink-0" />
    <span className="truncate">全部商品</span>
  </button>
);

/**
 * 展开式类目树，用于窄屏抽屉。
 *
 * 一级平铺可见，二级在节点下内联展开，三级收在二级之下。
 */
const InlineCategoryRail: React.FC<CategoryRailProps> = ({
  categoryTree,
  selectedCategory,
  onSelectCategory,
  onNavigate,
}) => {
  const [expandedLevel1Ids, setExpandedLevel1Ids] = useState<Set<string>>(new Set());
  const [expandedLevel2Ids, setExpandedLevel2Ids] = useState<Set<string>>(new Set());

  const selectedPath = useSelectedPath(categoryTree, selectedCategory);
  const activePathIds = useMemo(() => new Set(selectedPath.map((node) => node.id)), [selectedPath]);

  // 选中项所在路径始终自动展开：从「全部商品」点进任意层级后，树上要能看到自己在哪。
  useEffect(() => {
    const level1 = selectedPath[0];
    const level2 = selectedPath[1];
    if (!level1) return;
    setExpandedLevel1Ids((previous) => (previous.has(level1.id) ? previous : new Set(previous).add(level1.id)));
    if (level2) {
      setExpandedLevel2Ids((previous) => (previous.has(level2.id) ? previous : new Set(previous).add(level2.id)));
    }
  }, [selectedPath]);

  const toggle = (setter: React.Dispatch<React.SetStateAction<Set<string>>>, id: string) => {
    setter((previous) => {
      const next = new Set(previous);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const select = (categoryId: string) => {
    onSelectCategory(categoryId);
    onNavigate?.();
  };

  return (
    <nav aria-label="商品分类导航" className="space-y-0.5">
      <RailHeading />
      <AllCategoryButton selectedCategory={selectedCategory} onSelect={() => select(ALL_CATEGORY_ID)} />

      {categoryTree.map((level1) => {
        const level1Expanded = expandedLevel1Ids.has(level1.id);
        const level1Active = selectedPath[0]?.id === level1.id;
        const hasChildren = level1.children.length > 0;
        return (
          <div key={level1.id}>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => select(level1.id)}
                aria-current={selectedCategory === level1.id ? 'true' : undefined}
                className={`flex min-w-0 flex-1 items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs font-medium transition ${
                  level1Active
                    ? 'bg-zinc-900 text-white shadow-xs'
                    : 'text-zinc-700 hover:bg-zinc-100'
                }`}
              >
                <CategoryGlyph code={level1.code} className="h-3.5 w-3.5 shrink-0" />
                <span className="truncate">{level1.name}</span>
              </button>
              {hasChildren && (
                <button
                  type="button"
                  onClick={() => toggle(setExpandedLevel1Ids, level1.id)}
                  aria-expanded={level1Expanded}
                  aria-label={`${level1Expanded ? '收起' : '展开'}${level1.name}的下级分类`}
                  className={`shrink-0 rounded p-1 transition ${
                    level1Active ? 'text-zinc-400 hover:text-zinc-700' : 'text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700'
                  }`}
                >
                  <ChevronDown className={`h-3.5 w-3.5 transition-transform ${level1Expanded ? 'rotate-180' : ''}`} />
                </button>
              )}
            </div>

            {level1Expanded && hasChildren && (
              <div className="mt-0.5 space-y-0.5 border-l border-zinc-200 pl-2 ml-3.5">
                {level1.children.map((level2) => {
                  const level2Expanded = expandedLevel2Ids.has(level2.id);
                  const level2Active = activePathIds.has(level2.id);
                  const hasLevel3 = level2.children.length > 0;
                  return (
                    <div key={level2.id}>
                      <div className="flex items-center gap-1">
                        <button
                          type="button"
                          onClick={() => select(level2.id)}
                          aria-current={selectedCategory === level2.id ? 'true' : undefined}
                          className={`flex min-w-0 flex-1 items-center justify-between gap-2 rounded-lg px-2 py-1.5 text-left text-[11px] transition ${
                            level2Active
                              ? 'bg-zinc-100 font-semibold text-zinc-900'
                              : 'text-zinc-600 hover:bg-zinc-50 hover:text-zinc-900'
                          }`}
                        >
                          <span className="truncate">{level2.name}</span>
                          {hasLevel3 && <span className="shrink-0 text-[10px] text-zinc-400">{level2.children.length}</span>}
                        </button>
                        {hasLevel3 && (
                          <button
                            type="button"
                            onClick={() => toggle(setExpandedLevel2Ids, level2.id)}
                            aria-expanded={level2Expanded}
                            aria-label={`${level2Expanded ? '收起' : '展开'}${level2.name}的下级分类`}
                            className="shrink-0 rounded p-0.5 text-zinc-400 transition hover:bg-zinc-100 hover:text-zinc-700"
                          >
                            <ChevronRight className={`h-3 w-3 transition-transform ${level2Expanded ? 'rotate-90' : ''}`} />
                          </button>
                        )}
                      </div>

                      {level2Expanded && hasLevel3 && (
                        <div className="mb-1 mt-0.5 flex flex-wrap gap-1 pl-2">
                          {level2.children.map((level3) => (
                            <button
                              key={level3.id}
                              type="button"
                              onClick={() => select(level3.id)}
                              aria-current={selectedCategory === level3.id ? 'true' : undefined}
                              className={`rounded px-1.5 py-0.5 text-[10px] transition ${
                                selectedCategory === level3.id
                                  ? 'bg-zinc-900 text-white'
                                  : 'bg-zinc-50 text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900'
                              }`}
                            >
                              {level3.name}
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        );
      })}
    </nav>
  );
};

/**
 * 桌面端常驻左栏。
 *
 * 照京东、淘宝的做法：左栏只列一级类目，鼠标移到哪一条，右侧就贴着弹出一块浮层，
 * 浮层里按二级分组铺开三级。一级本身仍可直接点进筛选，浮层只是让下级不用先跳转再找。
 */
const FlyoutCategoryRail: React.FC<CategoryRailProps> = ({
  categoryTree,
  selectedCategory,
  onSelectCategory,
  onNavigate,
}) => {
  const [openId, setOpenId] = useState<string | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const closeTimerRef = useRef<number | null>(null);

  const cancelClose = useCallback(() => {
    if (closeTimerRef.current !== null) {
      window.clearTimeout(closeTimerRef.current);
      closeTimerRef.current = null;
    }
  }, []);

  const openFlyout = useCallback((categoryId: string) => {
    cancelClose();
    setOpenId(categoryId);
  }, [cancelClose]);

  const scheduleClose = useCallback(() => {
    cancelClose();
    closeTimerRef.current = window.setTimeout(() => {
      closeTimerRef.current = null;
      setOpenId(null);
    }, FLYOUT_CLOSE_DELAY_MS);
  }, [cancelClose]);

  // 卸载时别把定时器留下。
  useEffect(() => cancelClose, [cancelClose]);

  const selectedPath = useSelectedPath(categoryTree, selectedCategory);
  const activePathIds = useMemo(() => new Set(selectedPath.map((node) => node.id)), [selectedPath]);

  const openNode = useMemo(
    () => (openId ? categoryTree.find((node) => node.id === openId) ?? null : null),
    [categoryTree, openId]
  );

  const select = (categoryId: string) => {
    onSelectCategory(categoryId);
    setOpenId(null);
    onNavigate?.();
  };

  const handleRowEnter = (categoryId: string, hasChildren: boolean) => {
    if (hasChildren) openFlyout(categoryId);
    else scheduleClose();
  };

  const handleRowFocus = (categoryId: string, hasChildren: boolean) => {
    if (hasChildren) openFlyout(categoryId);
    else setOpenId(null);
  };

  return (
    <div
      ref={containerRef}
      className="relative"
      onMouseLeave={scheduleClose}
      onKeyDown={(event) => {
        if (event.key === 'Escape') setOpenId(null);
      }}
      // React 的 onBlur 是 focusout：焦点移出整块（左栏 + 浮层）才收，在两者之间 Tab 不会误关。
      onBlur={(event) => {
        if (!containerRef.current?.contains(event.relatedTarget as Node | null)) scheduleClose();
      }}
    >
      <nav aria-label="商品分类导航" className="space-y-0.5">
        <RailHeading />
        <AllCategoryButton selectedCategory={selectedCategory} onSelect={() => select(ALL_CATEGORY_ID)} />

        {categoryTree.map((level1) => {
          const hasChildren = level1.children.length > 0;
          const isOpen = openId === level1.id;
          const isActive = selectedPath[0]?.id === level1.id;
          return (
            <button
              key={level1.id}
              type="button"
              onClick={() => select(level1.id)}
              onMouseEnter={() => handleRowEnter(level1.id, hasChildren)}
              onFocus={() => handleRowFocus(level1.id, hasChildren)}
              aria-current={selectedCategory === level1.id ? 'true' : undefined}
              aria-expanded={hasChildren ? isOpen : undefined}
              aria-controls={hasChildren ? FLYOUT_ID : undefined}
              className={`flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs font-medium transition ${
                isActive
                  ? 'bg-zinc-900 text-white shadow-xs'
                  : isOpen
                    ? 'bg-amber-50 text-amber-900'
                    : 'text-zinc-700 hover:bg-zinc-100 hover:text-amber-700'
              }`}
            >
              <CategoryGlyph code={level1.code} className="h-3.5 w-3.5 shrink-0" />
              <span className="truncate flex-1">{level1.name}</span>
              {hasChildren && (
                <ChevronRight
                  className={`h-3.5 w-3.5 shrink-0 ${
                    isActive ? 'text-white/60' : isOpen ? 'text-amber-500' : 'text-zinc-300'
                  }`}
                />
              )}
            </button>
          );
        })}
      </nav>

      {/* 浮层贴着左栏卡片右边缘弹出，left 里的 0.75rem 补的正是卡片横向 padding。 */}
      {openNode && openNode.children.length > 0 && (
        <div
          id={FLYOUT_ID}
          role="group"
          aria-label={`${openNode.name}的下级分类`}
          onMouseEnter={cancelClose}
          className="absolute left-[calc(100%+0.75rem)] top-0 z-40 max-h-[calc(100vh-9rem)] w-[560px] max-w-[calc(100vw-16rem)] overflow-y-auto rounded-2xl border border-zinc-200 bg-white p-5 shadow-2xl"
        >
          <div className="mb-4 flex items-center justify-between gap-3 border-b border-zinc-100 pb-3">
            <span className="text-sm font-bold text-zinc-900">{openNode.name}</span>
            <button
              type="button"
              onClick={() => select(openNode.id)}
              className="shrink-0 text-[11px] font-semibold text-amber-600 transition hover:text-amber-700"
            >
              查看全部 ›
            </button>
          </div>

          <div className="grid grid-cols-2 gap-x-8 gap-y-5">
            {openNode.children.map((level2) => (
              <div key={level2.id}>
                <button
                  type="button"
                  onClick={() => select(level2.id)}
                  aria-current={selectedCategory === level2.id ? 'true' : undefined}
                  className={`flex items-center gap-0.5 text-xs font-bold transition ${
                    activePathIds.has(level2.id) ? 'text-amber-700' : 'text-zinc-900 hover:text-amber-700'
                  }`}
                >
                  <span className="truncate">{level2.name}</span>
                  <ChevronRight className="h-3 w-3 shrink-0" />
                </button>
                {level2.children.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-x-3.5 gap-y-1.5">
                    {level2.children.map((level3) => (
                      <button
                        key={level3.id}
                        type="button"
                        onClick={() => select(level3.id)}
                        aria-current={selectedCategory === level3.id ? 'true' : undefined}
                        className={`text-[11px] transition ${
                          selectedCategory === level3.id
                            ? 'font-bold text-amber-700'
                            : 'text-zinc-500 hover:text-amber-700'
                        }`}
                      >
                        {level3.name}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export const CategoryRail: React.FC<CategoryRailProps> = ({ variant = 'inline', ...props }) => (
  variant === 'flyout' ? <FlyoutCategoryRail {...props} /> : <InlineCategoryRail {...props} />
);
