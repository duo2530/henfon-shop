import React, { useEffect, useRef, useState } from 'react';
import { LoaderCircle, MapPinned, Search } from 'lucide-react';

interface AmapAddressValue {
  region: string;
  detail: string;
}

interface AmapAddressPickerProps {
  value: AmapAddressValue;
  onChange: (value: AmapAddressValue) => void;
}

type AmapInstance = any;

declare global {
  interface Window {
    AMap?: any;
    _AMapSecurityConfig?: { securityJsCode?: string };
  }
}

const AMAP_SCRIPT_ID = 'henfon-amap-js-sdk';

function loadAmap(): Promise<any> {
  const key = import.meta.env.VITE_AMAP_KEY as string | undefined;
  const securityJsCode = import.meta.env.VITE_AMAP_SECURITY_CODE as string | undefined;
  if (!key) return Promise.reject(new Error('未配置高德地图 Key'));
  if (window.AMap) return Promise.resolve(window.AMap);

  window._AMapSecurityConfig = { securityJsCode };
  const existing = document.getElementById(AMAP_SCRIPT_ID) as HTMLScriptElement | null;
  if (existing) {
    return new Promise((resolve, reject) => {
      existing.addEventListener('load', () => resolve(window.AMap));
      existing.addEventListener('error', () => reject(new Error('高德地图加载失败')));
    });
  }

  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.id = AMAP_SCRIPT_ID;
    script.async = true;
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}&plugin=AMap.AutoComplete,AMap.Geocoder`;
    script.onload = () => window.AMap ? resolve(window.AMap) : reject(new Error('高德地图初始化失败'));
    script.onerror = () => reject(new Error('高德地图加载失败'));
    document.head.appendChild(script);
  });
}

function formatRegion(poi: any): string {
  const addressComponent = poi?.addressComponent || {};
  const province = addressComponent.province || poi?.province || '';
  const city = Array.isArray(addressComponent.city)
    ? addressComponent.city[0]
    : addressComponent.city || poi?.city || province;
  const district = addressComponent.district || poi?.district || '';
  return [province, city, district].filter(Boolean).join(' ');
}

function toLngLat(location: any): [number, number] | null {
  if (!location) return null;
  if (typeof location === 'string') {
    const [lng, lat] = location.split(',').map(Number);
    return Number.isFinite(lng) && Number.isFinite(lat) ? [lng, lat] : null;
  }
  const lng = Number(location.lng);
  const lat = Number(location.lat);
  return Number.isFinite(lng) && Number.isFinite(lat) ? [lng, lat] : null;
}

export const AmapAddressPicker: React.FC<AmapAddressPickerProps> = ({ value, onChange }) => {
  const mapElementRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<AmapInstance>(null);
  const geocoderRef = useRef<AmapInstance>(null);
  const autocompleteRef = useRef<AmapInstance>(null);
  const [keyword, setKeyword] = useState(value.detail || value.region);
  const [suggestions, setSuggestions] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [available, setAvailable] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    loadAmap().then((AMap) => {
      if (!active || !mapElementRef.current) return;
      mapRef.current = new AMap.Map(mapElementRef.current, { zoom: 11, resizeEnable: true });
      geocoderRef.current = new AMap.Geocoder();
      autocompleteRef.current = new AMap.AutoComplete({ city: '全国' });
      mapRef.current.on('click', (event: any) => {
        const position = event.lnglat;
        geocoderRef.current.getAddress(position, (status: string, result: any) => {
          if (status !== 'complete' || result.info !== 'OK') return;
          const address = result.regeocode;
          onChange({ region: formatRegion(address), detail: address.formattedAddress || '' });
          setKeyword(address.formattedAddress || '');
        });
      });
      setLoading(false);
    }).catch((reason: Error) => {
      if (!active) return;
      setAvailable(false);
      setLoading(false);
      setError(reason.message);
    });
    return () => {
      active = false;
      mapRef.current?.destroy?.();
      mapRef.current = null;
    };
  }, [onChange]);

  const handleSearch = (nextKeyword: string) => {
    setKeyword(nextKeyword);
    if (!autocompleteRef.current || nextKeyword.trim().length < 2) {
      setSuggestions([]);
      return;
    }
    autocompleteRef.current.search(nextKeyword, (status: string, result: any) => {
      setSuggestions(status === 'complete' && result.info === 'OK' ? result.tips || [] : []);
    });
  };

  const selectSuggestion = (tip: any) => {
    const location = toLngLat(tip.location);
    const detail = tip.address || tip.name || keyword;
    onChange({ region: formatRegion(tip), detail });
    setKeyword(tip.name || detail);
    setSuggestions([]);
    if (location && mapRef.current) {
      mapRef.current.setZoomAndCenter(15, location);
      mapRef.current.clearMap?.();
      new window.AMap.Marker({ position: location }).setMap(mapRef.current);
    }
  };

  return (
    <div className="space-y-2">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-zinc-400" />
        <input
          type="text"
          value={keyword}
          onChange={(event) => handleSearch(event.target.value)}
          placeholder="搜索小区、写字楼或街道，点击地图也可选点"
          className="w-full py-2 pl-9 pr-3 rounded-xl border border-zinc-200 bg-white text-xs text-zinc-900 focus:outline-none focus:border-zinc-900"
        />
        {suggestions.length > 0 && (
          <div className="absolute z-20 left-0 right-0 top-full mt-1 rounded-xl border border-zinc-200 bg-white shadow-lg overflow-hidden">
            {suggestions.slice(0, 5).map((tip, index) => (
              <button
                type="button"
                key={`${tip.id || tip.name}-${index}`}
                onClick={() => selectSuggestion(tip)}
                className="w-full text-left px-3 py-2.5 hover:bg-zinc-50 border-b border-zinc-100 last:border-b-0"
              >
                <span className="block text-xs font-semibold text-zinc-800">{tip.name}</span>
                <span className="block mt-0.5 text-[10px] text-zinc-400">{tip.district || tip.address || '高德地图地址'}</span>
              </button>
            ))}
          </div>
        )}
      </div>
      <div className="relative h-44 rounded-xl overflow-hidden border border-zinc-200 bg-zinc-100">
        {loading && (
          <div className="absolute inset-0 z-10 flex items-center justify-center gap-2 bg-zinc-50 text-xs text-zinc-500">
            <LoaderCircle className="w-4 h-4 animate-spin" /> 正在加载地图
          </div>
        )}
        {!available && (
          <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-1 bg-zinc-50 px-4 text-center">
            <MapPinned className="w-6 h-6 text-zinc-400" />
            <span className="text-xs text-zinc-500">地图选点暂不可用，可继续手工填写下方地址</span>
            <span className="text-[10px] text-zinc-400">{error}</span>
          </div>
        )}
        <div ref={mapElementRef} className="w-full h-full" aria-label="高德地图选点区域" />
      </div>
      <p className="text-[10px] text-zinc-400">提示：点击地图位置或搜索结果会自动回填所在地区和详细地址。</p>
    </div>
  );
};
