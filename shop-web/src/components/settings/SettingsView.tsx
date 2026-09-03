import React, { useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { getTradeFreightTemplate, saveTradeFreightTemplate } from '../../api/adminApi';
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
import { PermissionGate } from '../common/PermissionGate';

export const SettingsView: React.FC = () => {
  const { showToast, requirePermission, confirm } = useAdmin();

  // Settings State
  const [storeName, setStoreName] = useState('极简臻品官方旗舰店');
  const [storeContactPhone, setStoreContactPhone] = useState('400-888-9999');
  const [storeContactEmail, setStoreContactEmail] = useState('support@brandmall.com');
  const [lowStockThreshold, setLowStockThreshold] = useState(10);
  const [autoNotifyEmail, setAutoNotifyEmail] = useState(true);
  const [autoTrackingSync, setAutoTrackingSync] = useState(true);
  const [enableWechatPay, setEnableWechatPay] = useState(true);
  const [defaultCarrier, setDefaultCarrier] = useState('顺丰速运');
  const [freightTemplateId, setFreightTemplateId] = useState<number>();
  const [baseWeightGram, setBaseWeightGram] = useState(1000);
  const [baseFee, setBaseFee] = useState(15);
  const [additionalWeightGram, setAdditionalWeightGram] = useState(1000);
  const [additionalFee, setAdditionalFee] = useState(5);
  const [freeShippingThreshold, setFreeShippingThreshold] = useState(99);
  const [remoteSurcharge, setRemoteSurcharge] = useState(0);
  const [remoteRegionsCsv, setRemoteRegionsCsv] = useState('西藏,新疆,港澳台');

  useEffect(() => {
    getTradeFreightTemplate().then((template) => {
      setFreightTemplateId(template.id);
      setDefaultCarrier(template.carrierName || '顺丰速运');
      setBaseWeightGram(template.baseWeightGram || 1000);
      setBaseFee(Number(template.baseFee || 0));
      setAdditionalWeightGram(template.additionalWeightGram || 1000);
      setAdditionalFee(Number(template.additionalFee || 0));
      setFreeShippingThreshold(Number(template.freeShippingThreshold || 0));
      setRemoteSurcharge(Number(template.remoteSurcharge || 0));
      setRemoteRegionsCsv(template.remoteRegionsCsv || '');
    }).catch(() => {
      // 后端尚未启动时保留默认值，页面仍可用于查看和编辑其他设置。
    });
  }, []);

  const handleSaveSettings = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!requirePermission('system:config:save', '保存系统配置')) return;
    try {
      const template = await saveTradeFreightTemplate({
        id: freightTemplateId,
        templateName: '全国顺丰配送模板',
        carrierName: defaultCarrier,
        baseWeightGram,
        baseFee,
        additionalWeightGram,
        additionalFee,
        freeShippingThreshold,
        remoteSurcharge,
        remoteRegionsCsv,
        status: 1,
      });
      setFreightTemplateId(template.id);
      showToast('系统设置已成功保存，运费规则立即生效', 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '运费配置保存失败，请稍后重试', 'error');
    }
  };

  const handleResetSettings = () => {
    setStoreName('极简臻品官方旗舰店');
    setLowStockThreshold(10);
    setAutoNotifyEmail(true);
    setAutoTrackingSync(true);
    setDefaultCarrier('顺丰速运');
    setBaseWeightGram(1000);
    setBaseFee(15);
    setAdditionalWeightGram(1000);
    setAdditionalFee(5);
    setFreeShippingThreshold(99);
    setRemoteSurcharge(0);
    setRemoteRegionsCsv('西藏,新疆,港澳台');
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
          <button type="button" onClick={async () => { if (await confirm('确认重置系统设置为默认值吗？', '恢复默认设置')) handleResetSettings(); }} className="h-[36px] px-3.5 rounded-lg border border-[#E2E8F0] bg-white text-gray-700 hover:bg-gray-50 flex items-center gap-1.5 text-xs font-semibold shadow-2xs transition-colors"><RotateCcw className="w-3.5 h-3.5 text-gray-500" /><span>重置默认</span></button>
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
              <p className="text-xs text-gray-500">配置低库存告警阈值及买家订单邮件通知机制</p>
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
                <span className="font-semibold text-gray-800 text-sm">买家支付成功后自动发送邮件通知</span>
                <p className="text-xs text-gray-500">包含订单编号、预计发货时间及售后保障提示</p>
              </div>
              <input
                type="checkbox"
                checked={autoNotifyEmail}
                onChange={(e) => setAutoNotifyEmail(e.target.checked)}
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

            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">首重（克）</label>
              <input type="number" min="1" value={baseWeightGram}
                onChange={(e) => setBaseWeightGram(Number(e.target.value) || 1000)}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm outline-none" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">首重费用（元）</label>
              <input type="number" min="0" step="0.01" value={baseFee}
                onChange={(e) => setBaseFee(Number(e.target.value) || 0)}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm outline-none" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">续重（克）</label>
              <input type="number" min="1" value={additionalWeightGram}
                onChange={(e) => setAdditionalWeightGram(Number(e.target.value) || 1000)}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm outline-none" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">每续重费用（元）</label>
              <input type="number" min="0" step="0.01" value={additionalFee}
                onChange={(e) => setAdditionalFee(Number(e.target.value) || 0)}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm outline-none" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">包邮门槛（元，0 表示不包邮）</label>
              <input type="number" min="0" step="0.01" value={freeShippingThreshold}
                onChange={(e) => setFreeShippingThreshold(Number(e.target.value) || 0)}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm outline-none" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">偏远地区附加费（元）</label>
              <input type="number" min="0" step="0.01" value={remoteSurcharge}
                onChange={(e) => setRemoteSurcharge(Number(e.target.value) || 0)}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm outline-none" />
            </div>
            <div className="md:col-span-2">
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">偏远地区关键词（逗号分隔）</label>
              <input type="text" value={remoteRegionsCsv}
                onChange={(e) => setRemoteRegionsCsv(e.target.value)}
                placeholder="例如：西藏,新疆,港澳台"
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm outline-none" />
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

            <div className="rounded-lg border border-dashed border-gray-200 bg-gray-50 p-3 text-xs text-gray-500">
              支付宝暂未接入，当前仅开放微信支付。
            </div>
          </div>
        </div>
      </form>
    </div>
  );
};
