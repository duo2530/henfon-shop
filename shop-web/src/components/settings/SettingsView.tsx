import React, { useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import {
  BackendSystemConfig,
  getSystemConfig,
  getTradeFreightTemplate,
  resetSystemConfig,
  saveSystemConfig,
  saveTradeFreightTemplate
} from '../../api/adminApi';
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
  const { showToast, requirePermission, confirm, logisticsCarriers } = useAdmin();

  // Settings State
  const [storeName, setStoreName] = useState('');
  const [storeContactPhone, setStoreContactPhone] = useState('');
  const [storeContactEmail, setStoreContactEmail] = useState('');
  const [lowStockThreshold, setLowStockThreshold] = useState<number | null>(null);
  const [autoNotifyEmail, setAutoNotifyEmail] = useState<boolean | null>(null);
  const [autoTrackingSync, setAutoTrackingSync] = useState<boolean | null>(null);
  const [enableWechatPay, setEnableWechatPay] = useState<boolean | null>(null);
  const [defaultCarrier, setDefaultCarrier] = useState('');
  const [freightTemplateId, setFreightTemplateId] = useState<number>();
  const [baseWeightGram, setBaseWeightGram] = useState<number | null>(null);
  const [baseFee, setBaseFee] = useState<number | null>(null);
  const [additionalWeightGram, setAdditionalWeightGram] = useState<number | null>(null);
  const [additionalFee, setAdditionalFee] = useState<number | null>(null);
  const [freeShippingThreshold, setFreeShippingThreshold] = useState<number | null>(null);
  const [remoteSurcharge, setRemoteSurcharge] = useState<number | null>(null);
  const [remoteRegionsCsv, setRemoteRegionsCsv] = useState('');
  const [systemConfigVersion, setSystemConfigVersion] = useState<number>();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const applySystemConfig = (config: BackendSystemConfig) => {
    setStoreName(config.storeName);
    setStoreContactPhone(config.storeContactPhone);
    setStoreContactEmail(config.storeContactEmail);
    setLowStockThreshold(config.lowStockThreshold);
    setAutoNotifyEmail(config.autoNotifyEmail);
    setAutoTrackingSync(config.autoTrackingSync);
    setEnableWechatPay(config.enableWechatPay);
    setSystemConfigVersion(config.version);
  };

  useEffect(() => {
    let active = true;
    setLoading(true);
    setLoadError(null);
    void Promise.all([getSystemConfig(), getTradeFreightTemplate()]).then(([config, template]) => {
      if (!active) return;
      applySystemConfig(config);
      setFreightTemplateId(template.id);
      setDefaultCarrier(template.carrierName || '');
      setBaseWeightGram(template.baseWeightGram);
      setBaseFee(Number(template.baseFee));
      setAdditionalWeightGram(template.additionalWeightGram);
      setAdditionalFee(Number(template.additionalFee));
      setFreeShippingThreshold(Number(template.freeShippingThreshold));
      setRemoteSurcharge(Number(template.remoteSurcharge));
      setRemoteRegionsCsv(template.remoteRegionsCsv || '');
    }).catch((error) => {
      if (!active) return;
      setLoadError(error instanceof Error ? error.message : '系统设置加载失败，请重试');
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => {
      active = false;
    };
  }, []);

  const handleSaveSettings = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!requirePermission('system:config:save', '保存系统配置')) return;
    if (lowStockThreshold === null || autoNotifyEmail === null || autoTrackingSync === null || enableWechatPay === null
      || baseWeightGram === null || baseFee === null || additionalWeightGram === null
      || additionalFee === null || freeShippingThreshold === null || remoteSurcharge === null) {
      showToast('系统配置尚未加载完成，请稍后重试', 'warning');
      return;
    }
    setSaving(true);
    try {
      const [config, template] = await Promise.all([
        saveSystemConfig({
          storeName,
          storeContactPhone,
          storeContactEmail,
          lowStockThreshold,
          autoNotifyEmail,
          autoTrackingSync,
          enableWechatPay,
          version: systemConfigVersion,
        }),
        saveTradeFreightTemplate({
        id: freightTemplateId,
        templateName: '全国配送模板',
        carrierName: defaultCarrier,
        baseWeightGram,
        baseFee,
        additionalWeightGram,
        additionalFee,
        freeShippingThreshold,
        remoteSurcharge,
        remoteRegionsCsv,
        status: 1,
        })
      ]);
      applySystemConfig(config);
      setFreightTemplateId(template.id);
      setDefaultCarrier(template.carrierName || '');
      showToast('系统设置已成功保存，运费规则立即生效', 'success');
    } catch (error) {
      // 两类配置分别持久化，任一失败时都重新读取服务端快照，避免本地值与已成功保存的一半配置不一致。
      const [latestConfig, latestTemplate] = await Promise.allSettled([getSystemConfig(), getTradeFreightTemplate()]);
      if (latestConfig.status === 'fulfilled') applySystemConfig(latestConfig.value);
      if (latestTemplate.status === 'fulfilled') {
        setFreightTemplateId(latestTemplate.value.id);
        setDefaultCarrier(latestTemplate.value.carrierName || '');
        setBaseWeightGram(latestTemplate.value.baseWeightGram);
        setBaseFee(Number(latestTemplate.value.baseFee));
        setAdditionalWeightGram(latestTemplate.value.additionalWeightGram);
        setAdditionalFee(Number(latestTemplate.value.additionalFee));
        setFreeShippingThreshold(Number(latestTemplate.value.freeShippingThreshold));
        setRemoteSurcharge(Number(latestTemplate.value.remoteSurcharge));
        setRemoteRegionsCsv(latestTemplate.value.remoteRegionsCsv || '');
      }
      showToast(error instanceof Error ? error.message : '运费配置保存失败，请稍后重试', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleResetSettings = async () => {
    if (!requirePermission('system:config:save', '重置系统配置')) return;
    try {
      const config = await resetSystemConfig(systemConfigVersion);
      applySystemConfig(config);
      showToast('已恢复服务端默认系统配置', 'info');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '系统配置重置失败，请稍后重试', 'error');
    }
  };

  if (loading) {
    return <div className="flex items-center justify-center py-20 text-sm text-gray-500">正在加载系统配置…</div>;
  }

  if (loadError) {
    return <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-6 text-sm text-red-700"><p>{loadError}</p><button type="button" onClick={() => window.location.reload()} className="mt-3 rounded-lg border border-red-300 bg-white px-3 py-1.5 text-xs font-semibold">刷新重试</button></div>;
  }

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
            <button type="button" onClick={async () => { if (await confirm('确认恢复服务端默认系统配置吗？', '恢复默认设置')) await handleResetSettings(); }} className="h-[36px] px-3.5 rounded-lg border border-[#E2E8F0] bg-white text-gray-700 hover:bg-gray-50 flex items-center gap-1.5 text-xs font-semibold shadow-2xs transition-colors"><RotateCcw className="w-3.5 h-3.5 text-gray-500" /><span>重置默认</span></button>
            <button type="submit" disabled={saving} className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 disabled:opacity-60 flex items-center justify-center gap-1.5 text-xs font-semibold shadow-sm transition-colors cursor-pointer"><Save className="w-4 h-4" /><span>{saving ? '保存中…' : '保存全部配置'}</span></button>
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
                  onChange={(e) => setLowStockThreshold(e.target.value === '' ? null : parseInt(e.target.value, 10))}
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
                checked={autoNotifyEmail === true}
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
                <option value="">请选择承运商</option>
                {logisticsCarriers.map((item) => <option key={item.code} value={item.name}>{item.name}</option>)}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">首重（克）</label>
              <input type="number" min="1" value={baseWeightGram}
                onChange={(e) => setBaseWeightGram(e.target.value === '' ? null : Number(e.target.value))}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm outline-none" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">首重费用（元）</label>
              <input type="number" min="0" step="0.01" value={baseFee}
                onChange={(e) => setBaseFee(e.target.value === '' ? null : Number(e.target.value))}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm outline-none" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">续重（克）</label>
              <input type="number" min="1" value={additionalWeightGram}
                onChange={(e) => setAdditionalWeightGram(e.target.value === '' ? null : Number(e.target.value))}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm outline-none" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">每续重费用（元）</label>
              <input type="number" min="0" step="0.01" value={additionalFee}
                onChange={(e) => setAdditionalFee(e.target.value === '' ? null : Number(e.target.value))}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm outline-none" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">包邮门槛（元，0 表示不包邮）</label>
              <input type="number" min="0" step="0.01" value={freeShippingThreshold}
                onChange={(e) => setFreeShippingThreshold(e.target.value === '' ? null : Number(e.target.value))}
                className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm outline-none" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1.5">偏远地区附加费（元）</label>
              <input type="number" min="0" step="0.01" value={remoteSurcharge}
                onChange={(e) => setRemoteSurcharge(e.target.value === '' ? null : Number(e.target.value))}
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
                <span className="font-semibold text-gray-800 text-sm">发货后自动开启物流单号实时轨迹追踪</span>
                <p className="text-xs text-gray-500">买家可在订单中心实时查看各转运节点详情</p>
              </div>
              <input
                type="checkbox"
                checked={autoTrackingSync === true}
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
                checked={enableWechatPay === true}
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
