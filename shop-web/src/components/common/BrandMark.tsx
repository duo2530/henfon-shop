import React from 'react';

/**
 * 品牌标记：两根竖画加一根横画组成的「H」，替换原先的星芒图标，避免与第三方 AI 产品标识混淆。
 */
export const BrandMark: React.FC<{ className?: string }> = ({ className }) => (
  <svg viewBox="0 0 32 32" aria-hidden="true" className={className}>
    <rect x="8.5" y="7.5" width="3.6" height="17" rx="1.5" fill="currentColor" />
    <rect x="19.9" y="7.5" width="3.6" height="17" rx="1.5" fill="currentColor" />
    <rect x="12.1" y="14.2" width="7.8" height="3.6" rx="1.4" fill="currentColor" />
  </svg>
);
