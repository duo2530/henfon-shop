import { useEffect } from 'react';

let lockCount = 0;
let previousOverflow = '';
let previousPaddingRight = '';

function lockPageScroll() {
  if (lockCount === 0) {
    // 补上滚动条宽度，避免底层内容在锁定瞬间横向跳动
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
    previousOverflow = document.body.style.overflow;
    previousPaddingRight = document.body.style.paddingRight;
    document.body.style.overflow = 'hidden';
    if (scrollbarWidth > 0) {
      document.body.style.paddingRight = `${scrollbarWidth}px`;
    }
  }
  lockCount += 1;
}

function releasePageScroll() {
  lockCount = Math.max(0, lockCount - 1);
  if (lockCount === 0) {
    document.body.style.overflow = previousOverflow;
    document.body.style.paddingRight = previousPaddingRight;
  }
}

/**
 * 弹层打开时锁住底层页面滚动，避免滚轮穿透到 body。
 * 多个弹层叠开时按引用计数，最后一个关闭才恢复原样式。
 */
export function useBodyScrollLock(active = true) {
  useEffect(() => {
    if (!active) return;
    lockPageScroll();
    return releasePageScroll;
  }, [active]);
}
