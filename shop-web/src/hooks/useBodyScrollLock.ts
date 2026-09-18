import { useEffect } from 'react';

/** 当前打开的全屏弹层数量，用于支持弹层嵌套时的加解锁顺序。 */
let lockCount = 0;
let previousBodyOverflow = '';
let previousBodyPaddingRight = '';

/**
 * 锁住文档滚动，返回解锁函数。
 *
 * 页面滚动发生在 document 上（`main` 没有独立滚动容器），弹层即使自带内部滚动区，
 * 滚轮在弹层内滚到上下边界后仍会把滚动链式传给文档，鼠标落在遮罩空白处滚动时更是
 * 直接滚底层页面，表现为「弹层滚动时底层跟着动」。
 *
 * 解锁时原样还原打开前的内联样式；打开时补上等宽右内边距，抵消滚动条消失造成的
 * 页面横向跳动（顶部导航与侧边栏都是 fixed，不受该内边距影响）。
 */
export function lockDocumentScroll(): () => void {
  const { body, documentElement } = document;
  if (lockCount === 0) {
    previousBodyOverflow = body.style.overflow;
    previousBodyPaddingRight = body.style.paddingRight;
    const scrollbarWidth = window.innerWidth - documentElement.clientWidth;
    body.style.overflow = 'hidden';
    if (scrollbarWidth > 0) body.style.paddingRight = `${scrollbarWidth}px`;
  }
  lockCount += 1;

  let released = false;
  return () => {
    if (released) return;
    released = true;
    lockCount -= 1;
    if (lockCount === 0) {
      body.style.overflow = previousBodyOverflow;
      body.style.paddingRight = previousBodyPaddingRight;
    }
  };
}

/** 全屏弹层打开期间锁住底层滚动，关闭或卸载时自动解锁。 */
export function useBodyScrollLock(active = true): void {
  useEffect(() => {
    if (!active) return undefined;
    return lockDocumentScroll();
  }, [active]);
}
