import React from 'react';
import { Coffee, Compass, Headphones, Home, Shirt, Smartphone, Sparkles, Tag } from 'lucide-react';

/**
 * 类目图标。
 *
 * 后端类目没有图标字段，按类目编码前缀取同一套线性图标；
 * 导航、左侧类目树与搜索建议共用，避免三处各写一份映射。
 */
export const CategoryGlyph: React.FC<{ code: string; className?: string }> = ({
  code,
  className = 'w-3.5 h-3.5',
}) => {
  const normalized = (code || '').toUpperCase();
  if (normalized.startsWith('ELEC')) return <Smartphone className={className} />;
  if (normalized.startsWith('CLOTH')) return <Shirt className={className} />;
  if (normalized.startsWith('HOME')) return <Home className={className} />;
  if (normalized.startsWith('BEAUTY')) return <Sparkles className={className} />;
  if (normalized.startsWith('FOOD') || normalized.startsWith('LIFE')) return <Coffee className={className} />;
  if (normalized.startsWith('AUDIO')) return <Headphones className={className} />;
  if (normalized.startsWith('OUTDOOR')) return <Compass className={className} />;
  return <Tag className={className} />;
};
