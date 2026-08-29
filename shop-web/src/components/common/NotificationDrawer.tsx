import React from 'react';
import { useAdmin } from '../../context/AdminContext';
import { 
  X, 
  CheckCheck, 
  ShoppingBag, 
  AlertTriangle, 
  Info,
  Clock
} from 'lucide-react';

interface NotificationDrawerProps {
  open: boolean;
  onClose: () => void;
}

export const NotificationDrawer: React.FC<NotificationDrawerProps> = ({ open, onClose }) => {
  const { notifications, markNotificationAsRead, markAllNotificationsAsRead, setCurrentTab } = useAdmin();

  if (!open) return null;

  return (
    <>
      <div
        className="fixed inset-0 bg-black/40 z-50 backdrop-blur-2xs transition-opacity"
        onClick={onClose}
      />
      <div className="fixed top-0 right-0 h-full w-full max-w-md bg-white shadow-2xl z-50 flex flex-col border-l border-[#E2E8F0] animate-in slide-in-from-right duration-200">
        {/* Header */}
        <div className="p-4 border-b border-gray-200 flex items-center justify-between bg-gray-50/70">
          <div>
            <h3 className="font-semibold text-gray-900 text-base">系统消息与预警通知</h3>
            <p className="text-xs text-gray-500 mt-0.5">
              共 {notifications.length} 条通知，{notifications.filter((n) => !n.read).length} 条未读
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={markAllNotificationsAsRead}
              className="text-xs text-blue-600 hover:text-blue-800 font-medium flex items-center gap-1 p-1.5 hover:bg-blue-50 rounded transition-colors"
              title="全部标为已读"
            >
              <CheckCheck className="w-4 h-4" />
              <span>全部已读</span>
            </button>
            <button
              onClick={onClose}
              className="text-gray-400 hover:text-gray-700 p-1.5 rounded-lg hover:bg-gray-200 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Notifications List */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {notifications.length === 0 ? (
            <div className="text-center py-16 text-gray-400">
              <Info className="w-10 h-10 mx-auto mb-2 opacity-40" />
              <p className="text-sm">暂无新通知</p>
            </div>
          ) : (
            notifications.map((item) => {
              let icon = <Info className="w-5 h-5 text-blue-600" />;
              let bg = 'bg-blue-50';

              if (item.type === 'order') {
                icon = <ShoppingBag className="w-5 h-5 text-emerald-600" />;
                bg = 'bg-emerald-50';
              } else if (item.type === 'stock') {
                icon = <AlertTriangle className="w-5 h-5 text-amber-600" />;
                bg = 'bg-amber-50';
              }

              return (
                <div
                  key={item.id}
                  onClick={() => {
                    markNotificationAsRead(item.id);
                    if (item.type === 'order') {
                      setCurrentTab('orders');
                      onClose();
                    } else if (item.type === 'stock') {
                      setCurrentTab('products');
                      onClose();
                    }
                  }}
                  className={`p-3.5 rounded-xl border transition-all cursor-pointer flex gap-3 ${
                    item.read
                      ? 'bg-white border-gray-200 opacity-75'
                      : 'bg-blue-50/40 border-blue-200 shadow-2xs hover:border-blue-300'
                  }`}
                >
                  <div className={`w-9 h-9 rounded-lg ${bg} flex items-center justify-center flex-shrink-0 mt-0.5`}>
                    {icon}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-2 mb-1">
                      <h4 className="text-sm font-semibold text-gray-900 truncate">
                        {item.title}
                      </h4>
                      {!item.read && (
                        <span className="w-2 h-2 rounded-full bg-blue-600 flex-shrink-0" />
                      )}
                    </div>
                    <p className="text-xs text-gray-600 leading-relaxed line-clamp-2">
                      {item.content}
                    </p>
                    <div className="mt-2 flex items-center gap-1 text-[11px] text-gray-400">
                      <Clock className="w-3 h-3" />
                      <span>{item.time}</span>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </>
  );
};
