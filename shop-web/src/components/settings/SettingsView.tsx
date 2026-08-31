import React, { useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { 
  Store, 
  BellRing, 
  Truck, 
  CreditCard, 
  Shield, 
  Save, 
  RotateCcw,
  CheckCircle2
} from 'lucide-react';
import { AuditLogPanel } from './AuditLogPanel';
import { PermissionGate } from '../common/PermissionGate';

export const SettingsView: React.FC = () => {
  const { showToast, requirePermission } = useAdmin();

  // Settings State
  const [storeName, setStoreName] = useState('极简臻品官方旗舰店');
  const [storeContactPhone, setStoreContactPhone] = useState('400-888-9999');
  const [storeContactEmail, setStoreContactEmail] = useState('support@brandmall.com');
  const [lowStockThreshold, setLowStockThreshold] = useState(10);
  const [autoNotifySms, setAutoNotifySms] = useState(true);
  const [autoTrackingSync, setAutoTrackingSync] = useState(true);
  const [enableWechatPay, setEnableWechatPay] = useState(true);
  const [enableAlipay, setEnableAlipay] = useState(true);
  const [defaultCarrier, setDefaultCarrier] = useState('顺丰速运');

  const handleSaveSettings = (e: React.FormEvent) => {
    e.preventDefault();
    if (!requirePermission('system:config:save', '保存系统配置')) return;
    showToast('系统设置已成功保存并生效', 'success');
  };

  const handleResetSettings = () => {
    setStoreName('极简臻品官方旗舰店');
    setLowStockThreshold(10);
    setAutoNotifySms(true);
    setAutoTrackingSync(true);
    showToast('已重置为默认系统配置', 'info');
  };

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200 max-w-5xl">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
            系统设置 (System Settings)
          </h2>
          <p className="text-sm text-[#434655] mt-0.5">
            配置商城店铺信息、库存风控告警阈值、物流配送和支付网关。
          </p>
        </div>

        <div className="flex items-center gap-3">
          <PermissionGate permission="system:config:save">
            <button type="button" onClick={() => { if (window.confirm('确认重置系统设置为默认值吗？')) handleResetSettings(); }} className="h-[36px] px-3.5 rounded-lg border border-[#E2E8F0] bg-white text-gray-700 hover:bg-gray-50 flex items-center gap-1.5 text-xs font-semibold shadow-2xs transition-colors"><RotateCcw className="w-3.5 h-3.5 text-gray-500" /><span>重置默认</span></button>
            <button type="button" onClick={handleSaveSettings} className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-1.5 text-xs font-semibold shadow-sm transition-colors cursor-pointer"><Save className="w-4 h-4" /><span>保存全部配置</span></button>
          </PermissionGate>
        </div>
      </div>

      <form onSubmit={handleSaveSettings} className="space-y-6">
        {/* Section 1: 店铺基本信息 */}
        <div className="bg-white rounded-xl border border-[#E2E8F0] p-5 md:p-6 shadow-xs">
          <div className="flex items-center gap-2.5 pb-3 border-b border-gray-100 mb-4">
            <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
              <Store className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-gray-900">店铺基本信息</h3>
              <p className="text-xs text-gray-500">对外展示的商城名称、客服热线与运营主体</p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">
                商城/店铺名称
              </label>
              <input
                type="text"
                value={storeName}
                onChange={(e) => setStoreName(e.target.value)}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none text-sm"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">
                客服联系电话
              </label>
              <input
                type="text"
                value={storeContactPhone}
                onChange={(e) => setStoreContactPhone(e.target.value)}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none text-sm"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">
                运营服务邮箱
              </label>
              <input
                type="email"
                value={storeContactEmail}
                onChange={(e) => setStoreContactEmail(e.target.value)}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none text-sm"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">
                货币结算单位
              </label>
              <select className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-gray-50 text-gray-600 outline-none text-sm" disabled>
                <option>CNY (¥ 人民币)</option>
              </select>
            </div>
          </div>
        </div>

        {/* Section 2: 库存预警与通知机制 */}
        <div className="bg-white rounded-xl border border-[#E2E8F0] p-5 md:p-6 shadow-xs">
          <div className="flex items-center gap-2.5 pb-3 border-b border-gray-100 mb-4">
            <div className="p-2 bg-amber-50 text-amber-600 rounded-lg">
              <BellRing className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-gray-900">库存预警与自动化通知</h3>
              <p className="text-xs text-gray-500">配置低库存告警阈值及买家订单短信/应用通知机制</p>
            </div>
          </div>

          <div className="space-y-4 text-sm">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 p-3 bg-gray-50 rounded-lg">
              <div>
                <span className="font-semibold text-gray-800 text-sm">低库存告警阈值 (件)</span>
                <p className="text-xs text-gray-500">当单品可售库存低于此数值时自动触发工作台告警提醒</p>
              </div>
              <div className="w-32">
                <input
                  type="number"
                  min="1"
                  max="1000"
                  value={lowStockThreshold}
                  onChange={(e) => setLowStockThreshold(parseInt(e.target.value, 10) || 10)}
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm outline-none text-right font-mono"
                />
              </div>
            </div>

            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
              <div>
                <span className="font-semibold text-gray-800 text-sm">买家支付成功后自动发送短信与App通知</span>
                <p className="text-xs text-gray-500">包含订单编号、预计发货时间及售后保障提示</p>
              </div>
              <input
                type="checkbox"
                checked={autoNotifySms}
                onChange={(e) => setAutoNotifySms(e.target.checked)}
                className="w-5 h-5 rounded text-blue-600 focus:ring-blue-500 cursor-pointer"
              />
            </div>
          </div>
        </div>

        {/* Section 3: 物流与配送 */}
        <div className="bg-white rounded-xl border border-[#E2E8F0] p-5 md:p-6 shadow-xs">
          <div className="flex items-center gap-2.5 pb-3 border-b border-gray-100 mb-4">
            <div className="p-2 bg-orange-50 text-orange-600 rounded-lg">
              <Truck className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-gray-900">物流与配送通道</h3>
              <p className="text-xs text-gray-500">默认发货快递选择与全国物流轨迹自动同步</p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">
                默认首选物流承运商
              </label>
              <select
                value={defaultCarrier}
                onChange={(e) => setDefaultCarrier(e.target.value)}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white focus:border-blue-500 outline-none text-sm"
              >
                <option value="顺丰速运">顺丰速运 (SF Express)</option>
                <option value="中通快递">中通快递 (ZTO Express)</option>
                <option value="圆通速递">圆通速递 (YTO Express)</option>
                <option value="京东快递">京东快递 (JD Logistics)</option>
              </select>
            </div>

            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg md:col-span-2">
              <div>
                <span className="font-semibold text-gray-800 text-sm">发货后自动开启快递鸟/顺丰单号实时轨迹追踪</span>
                <p className="text-xs text-gray-500">买家可在订单中心实时查看各转运节点详情</p>
              </div>
              <input
                type="checkbox"
                checked={autoTrackingSync}
                onChange={(e) => setAutoTrackingSync(e.target.checked)}
                className="w-5 h-5 rounded text-blue-600 focus:ring-blue-500 cursor-pointer"
              />
            </div>
          </div>
        </div>

        {/* Section 4: 支付网关渠道 */}
        <div className="bg-white rounded-xl border border-[#E2E8F0] p-5 md:p-6 shadow-xs">
          <div className="flex items-center gap-2.5 pb-3 border-b border-gray-100 mb-4">
            <div className="p-2 bg-emerald-50 text-emerald-600 rounded-lg">
              <CreditCard className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-gray-900">支付结算通道</h3>
              <p className="text-xs text-gray-500">配置线上主流收银台支持的支付渠道</p>
            </div>
          </div>

          <div className="space-y-3">
            <div className="flex items-center justify-between p-3.5 border border-gray-200 rounded-lg">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-lg bg-green-50 text-green-700 flex items-center justify-center font-bold text-xs">
                  微信
                </div>
                <div>
                  <h4 className="text-sm font-semibold text-gray-900">微信支付 (WeChat Pay)</h4>
                  <p className="text-xs text-gray-500">支持微信JSAPI、小程序及原生扫码支付</p>
                </div>
              </div>
              <input
                type="checkbox"
                checked={enableWechatPay}
                onChange={(e) => setEnableWechatPay(e.target.checked)}
                className="w-5 h-5 rounded text-blue-600 focus:ring-blue-500 cursor-pointer"
              />
            </div>

            <div className="flex items-center justify-between p-3.5 border border-gray-200 rounded-lg">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-lg bg-blue-50 text-blue-700 flex items-center justify-center font-bold text-xs">
                  支
                </div>
                <div>
                  <h4 className="text-sm font-semibold text-gray-900">支付宝 (Alipay)</h4>
                  <p className="text-xs text-gray-500">支持快捷收银台、花呗分期及当面付</p>
                </div>
              </div>
              <input
                type="checkbox"
                checked={enableAlipay}
                onChange={(e) => setEnableAlipay(e.target.checked)}
                className="w-5 h-5 rounded text-blue-600 focus:ring-blue-500 cursor-pointer"
              />
            </div>
          </div>
        </div>
      </form>
      <AuditLogPanel />
    </div>
  );
};
