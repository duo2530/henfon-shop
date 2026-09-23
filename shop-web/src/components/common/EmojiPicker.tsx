import React, { useCallback, useState } from 'react';

interface EmojiEntry {
  emoji: string;
  name: string;
}

interface EmojiGroup {
  id: string;
  label: string;
  items: EmojiEntry[];
}

const GROUPS: EmojiGroup[] = [
  {
    id: 'common',
    label: '常用',
    items: [
      { emoji: '😊', name: '微笑' },
      { emoji: '😂', name: '笑哭' },
      { emoji: '🙂', name: '淡淡一笑' },
      { emoji: '😅', name: '尴尬' },
      { emoji: '🤝', name: '握手' },
      { emoji: '👍', name: '赞' },
      { emoji: '🙏', name: '感谢' },
      { emoji: '👌', name: '好的' },
      { emoji: '❤️', name: '爱心' },
      { emoji: '💯', name: '满分' },
      { emoji: '✨', name: '闪耀' },
      { emoji: '🎉', name: '庆祝' },
      { emoji: '👀', name: '看看' },
      { emoji: '🤔', name: '思考' },
      { emoji: '😢', name: '难过' },
      { emoji: '😡', name: '生气' },
    ],
  },
  {
    id: 'face',
    label: '表情',
    items: [
      { emoji: '😀', name: '大笑' },
      { emoji: '😃', name: '开心' },
      { emoji: '😄', name: '眯眼笑' },
      { emoji: '😁', name: '露齿笑' },
      { emoji: '😆', name: '哈哈' },
      { emoji: '🥰', name: '心动' },
      { emoji: '😍', name: '喜欢' },
      { emoji: '😘', name: '飞吻' },
      { emoji: '😋', name: '好吃' },
      { emoji: '😜', name: '吐舌' },
      { emoji: '🤪', name: '疯了' },
      { emoji: '🤗', name: '拥抱' },
      { emoji: '😐', name: '面无表情' },
      { emoji: '😏', name: '得意' },
      { emoji: '😬', name: '为难' },
      { emoji: '😴', name: '睡着' },
      { emoji: '😳', name: '害羞' },
      { emoji: '🥺', name: '委屈' },
      { emoji: '😭', name: '大哭' },
      { emoji: '😱', name: '震惊' },
      { emoji: '😤', name: '不服' },
      { emoji: '🙄', name: '白眼' },
      { emoji: '😎', name: '酷' },
      { emoji: '🤠', name: '牛仔' },
      { emoji: '🥳', name: '派对' },
      { emoji: '🤓', name: '书呆子' },
    ],
  },
  {
    id: 'gesture',
    label: '手势',
    items: [
      { emoji: '👍', name: '赞' },
      { emoji: '👎', name: '踩' },
      { emoji: '👌', name: '好的' },
      { emoji: '✌️', name: '胜利' },
      { emoji: '🤞', name: '祈祷好运' },
      { emoji: '🤙', name: '打电话' },
      { emoji: '👈', name: '向左指' },
      { emoji: '👉', name: '向右指' },
      { emoji: '👆', name: '向上指' },
      { emoji: '👇', name: '向下指' },
      { emoji: '✋', name: '举手' },
      { emoji: '👋', name: '再见' },
      { emoji: '🤝', name: '握手' },
      { emoji: '🙏', name: '拜托' },
      { emoji: '👏', name: '鼓掌' },
      { emoji: '🙌', name: '欢呼' },
      { emoji: '💪', name: '加油' },
      { emoji: '🤲', name: '捧起' },
    ],
  },
  {
    id: 'shop',
    label: '购物',
    items: [
      { emoji: '🛒', name: '购物车' },
      { emoji: '📦', name: '包裹' },
      { emoji: '🚚', name: '配送中' },
      { emoji: '🎁', name: '礼物' },
      { emoji: '💰', name: '钱袋' },
      { emoji: '💳', name: '付款' },
      { emoji: '🏷️', name: '标签' },
      { emoji: '🧾', name: '小票' },
      { emoji: '⭐', name: '好评' },
      { emoji: '🔥', name: '热卖' },
      { emoji: '✅', name: '已处理' },
      { emoji: '⏳', name: '稍等' },
      { emoji: '📅', name: '排期' },
      { emoji: '📞', name: '电话联系' },
      { emoji: '🏠', name: '门店' },
      { emoji: '🔧', name: '售后处理' },
    ],
  },
];

/** 把表情插到光标处；没有光标信息时追加到末尾。调用方负责按 maxLength 截断。 */
export function insertEmojiAtCursor(
  value: string,
  emoji: string,
  start: number | null,
  end: number | null,
): { text: string; caret: number } {
  const from = start ?? value.length;
  const to = end ?? from;
  const head = value.slice(0, from);
  const next = head + emoji + value.slice(to);
  return { text: next, caret: head.length + emoji.length };
}

interface EmojiPickerProps {
  onSelect: (emoji: string) => void;
  widthClass?: string;
  gridClass?: string;
}

/** 悬浮在输入框上方的表情面板，宽度由调用方按容器给出。 */
export function EmojiPicker({ onSelect, widthClass = 'w-[340px]', gridClass = 'grid-cols-9' }: EmojiPickerProps) {
  const [activeGroup, setActiveGroup] = useState(GROUPS[0].id);
  const current = GROUPS.find((group) => group.id === activeGroup) ?? GROUPS[0];

  const pick = useCallback(
    (emoji: string) => {
      onSelect(emoji);
    },
    [onSelect],
  );

  return (
    <div className={`${widthClass} rounded-lg border border-[#E2E8F0] bg-white shadow-lg`}>
      <div className="flex items-center gap-0.5 border-b border-slate-100 px-1.5 py-1">
        {GROUPS.map((group) => (
          <button
            key={group.id}
            type="button"
            aria-pressed={activeGroup === group.id}
            onClick={() => setActiveGroup(group.id)}
            className={`px-2 py-1 rounded-md text-xs transition-colors ${
              activeGroup === group.id
                ? 'bg-slate-100 text-slate-900 font-semibold'
                : 'text-slate-500 hover:text-slate-800 hover:bg-slate-50'
            }`}
          >
            {group.label}
          </button>
        ))}
      </div>
      <div className={`grid ${gridClass} gap-0.5 p-1.5 max-h-48 overflow-y-auto`}>
        {current.items.map((item) => (
          <button
            key={`${current.id}-${item.emoji}`}
            type="button"
            aria-label={item.name}
            title={item.name}
            onClick={() => pick(item.emoji)}
            className="h-8 rounded-md text-lg leading-none hover:bg-slate-100 transition-colors"
          >
            {item.emoji}
          </button>
        ))}
      </div>
    </div>
  );
}
