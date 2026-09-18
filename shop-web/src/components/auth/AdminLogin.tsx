import React, { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import {
  AlertCircle, ArrowRight, BarChart3, Eye, EyeOff, Loader2,
  LockKeyhole, Package, ShieldCheck, Truck, User
} from 'lucide-react';
import { getLoginCaptcha, type LoginCaptcha } from '../../api/adminApi';
import { BrandMark } from '../common/BrandMark';
import { useAdmin } from '../../context/AdminContext';

/** 「记住用户名」只落浏览器本地，不含密码；登录态 token 仍由 adminApi 独立管理。 */
const REMEMBERED_USERNAME_KEY = 'henfon.admin.remembered-username';

/** 演示环境公开账号，仅用于本地与验收，生产部署应移除该提示。 */
const DEMO_ACCOUNT = { username: 'admin', password: '123456' };

const CAPABILITIES = [
  { icon: ShieldCheck, title: '角色权限管控', description: '菜单与按钮级 RBAC' },
  { icon: BarChart3, title: '经营数据看板', description: '订单库存售后汇总' },
  { icon: Package, title: '商品与库存', description: '类目秒杀出入库联动' },
  { icon: Truck, title: '订单履约', description: '发货物流售后全链路' }
];

/* 输入框聚焦改用「边框变色 + 柔和外环」替代全局 3px 实心轮廓，焦点依然清晰可见。 */
const FIELD_CLASS =
  'h-11 w-full rounded-xl border border-slate-300 bg-white pl-10 pr-3 text-sm text-slate-900 transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10 focus-visible:outline-none';

const LABEL_CLASS = 'mb-1.5 block text-[13px] font-medium text-slate-700';

export const AdminLogin: React.FC = () => {
  const { login } = useAdmin();
  const [username, setUsername] = useState(() => localStorage.getItem(REMEMBERED_USERNAME_KEY) ?? '');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(() => Boolean(localStorage.getItem(REMEMBERED_USERNAME_KEY)));
  const [showPassword, setShowPassword] = useState(false);
  const [capsLock, setCapsLock] = useState(false);
  const [error, setError] = useState('');
  const [hint, setHint] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [captcha, setCaptcha] = useState<LoginCaptcha | null>(null);
  const [captchaCode, setCaptchaCode] = useState('');
  const [captchaLoading, setCaptchaLoading] = useState(false);

  const usernameRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);

  /** 验证码一次性消费，登录失败后必须换一张，否则会连续报同一个错误。 */
  const refreshCaptcha = useCallback(async () => {
    setCaptchaLoading(true);
    setCaptchaCode('');
    try {
      setCaptcha(await getLoginCaptcha());
    } catch (reason) {
      setCaptcha(null);
      setError(reason instanceof Error ? reason.message : '验证码加载失败，请点击图片重试');
    } finally {
      setCaptchaLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshCaptcha();
  }, [refreshCaptcha]);

  /** 有记住的用户名就直接落到密码框，省一次 Tab。 */
  useEffect(() => {
    if (localStorage.getItem(REMEMBERED_USERNAME_KEY)) passwordRef.current?.focus();
    else usernameRef.current?.focus();
  }, []);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!captcha) {
      setError('验证码未加载，请点击验证码图片重新获取');
      return;
    }
    setError('');
    setHint('');
    setSubmitting(true);
    try {
      await login(username, password, captcha.captchaId, captchaCode);
      // 登录成功后再落盘，避免把输错的用户名记成默认值。
      if (remember) localStorage.setItem(REMEMBERED_USERNAME_KEY, username);
      else localStorage.removeItem(REMEMBERED_USERNAME_KEY);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '登录失败，请稍后重试');
      await refreshCaptcha();
    } finally {
      setSubmitting(false);
    }
  };

  /** 仅提示，不做拦截：大小写锁定不影响输入，但会让密码看起来「输对了却登不上」。 */
  const detectCapsLock = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (typeof event.getModifierState === 'function') setCapsLock(event.getModifierState('CapsLock'));
  };

  const fillDemoAccount = () => {
    setUsername(DEMO_ACCOUNT.username);
    setPassword(DEMO_ACCOUNT.password);
    setError('');
    setHint('');
  };

  return (
    <div className="relative min-h-screen overflow-hidden bg-slate-50">
      {/* 页面底噪：浅色网点 + 两团品牌色柔光，替代整块深色背景。 */}
      <div aria-hidden="true" className="pointer-events-none absolute inset-0">
        <div className="absolute -left-32 -top-40 h-[420px] w-[420px] rounded-full bg-blue-200/45 blur-[120px]" />
        <div className="absolute -bottom-44 -right-24 h-[460px] w-[460px] rounded-full bg-indigo-200/40 blur-[130px]" />
        <div
          className="absolute inset-0"
          style={{
            backgroundImage: 'radial-gradient(rgba(100,116,139,0.22) 1px, transparent 1px)',
            backgroundSize: '24px 24px'
          }}
        />
      </div>

      <div className="relative flex min-h-screen items-center justify-center px-4 py-10 sm:px-6 lg:px-8">
        <div className="grid w-full max-w-[960px] overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-[0_28px_70px_-34px_rgba(15,23,42,0.4)] lg:grid-cols-[1.05fr_1fr]">
          {/* 品牌侧栏：小屏隐藏，品牌信息在表单区顶部以小尺寸复现。 */}
          <aside className="relative hidden flex-col overflow-hidden bg-gradient-to-br from-[#1D4ED8] via-[#2563EB] to-[#4F46E5] px-9 py-9 lg:flex xl:px-11">
            <div aria-hidden="true" className="pointer-events-none absolute inset-0">
              <div className="absolute -left-20 -top-24 h-72 w-72 rounded-full bg-white/15 blur-[90px]" />
              <div className="absolute -bottom-28 -right-16 h-80 w-80 rounded-full bg-cyan-300/25 blur-[100px]" />
              <div
                className="absolute inset-0 opacity-[0.13]"
                style={{
                  backgroundImage:
                    'linear-gradient(to right, rgba(255,255,255,0.9) 1px, transparent 1px), linear-gradient(to bottom, rgba(255,255,255,0.9) 1px, transparent 1px)',
                  backgroundSize: '48px 48px'
                }}
              />
              {/* 三条数据流曲线，呼应订单流转与库存台账。 */}
              <svg
                className="absolute inset-0 h-full w-full"
                viewBox="0 0 600 820"
                preserveAspectRatio="none"
                aria-hidden="true"
              >
                <defs>
                  <linearGradient id="loginFlowA" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0%" stopColor="#ffffff" stopOpacity="0" />
                    <stop offset="45%" stopColor="#ffffff" stopOpacity="0.55" />
                    <stop offset="100%" stopColor="#ffffff" stopOpacity="0.04" />
                  </linearGradient>
                  <linearGradient id="loginFlowB" x1="0" y1="1" x2="1" y2="0">
                    <stop offset="0%" stopColor="#a5f3fc" stopOpacity="0" />
                    <stop offset="55%" stopColor="#a5f3fc" stopOpacity="0.5" />
                    <stop offset="100%" stopColor="#c7d2fe" stopOpacity="0" />
                  </linearGradient>
                </defs>
                <path
                  d="M-40 640 C 120 510, 210 720, 380 570 S 560 390, 660 480"
                  fill="none"
                  stroke="url(#loginFlowA)"
                  strokeWidth="1.6"
                />
                <path
                  d="M-30 320 C 140 420, 240 190, 400 300 S 580 470, 680 380"
                  fill="none"
                  stroke="url(#loginFlowB)"
                  strokeWidth="1.1"
                  opacity="0.75"
                />
                <path
                  d="M-50 780 C 130 700, 260 830, 430 720 S 600 620, 680 660"
                  fill="none"
                  stroke="url(#loginFlowA)"
                  strokeWidth="1"
                  opacity="0.5"
                />
                <circle cx="380" cy="570" r="3" fill="#ffffff" fillOpacity="0.65" />
                <circle cx="400" cy="300" r="2.5" fill="#a5f3fc" fillOpacity="0.8" />
                <circle cx="430" cy="720" r="2" fill="#ffffff" fillOpacity="0.45" />
              </svg>
            </div>

            <div className="relative flex items-center gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-white/25 bg-white/15 text-white">
                <BrandMark className="h-6 w-6" />
              </div>
              <div>
                <p className="text-[15px] font-bold tracking-tight text-white">Henfon电商后台</p>
                <p className="text-[11px] font-medium text-blue-100/80">E-Commerce RBAC OS</p>
              </div>
            </div>

            <div className="relative mt-10">
              <h2 className="text-[26px] font-bold leading-snug tracking-tight text-white">
                订单、库存与售后
                <br />
                收进同一块屏幕
              </h2>
              <p className="mt-3.5 text-[13px] leading-relaxed text-blue-100/85">
                从前台下单到仓内发货，从会员评价到运营通知，页面数据全部来自后台服务端。
              </p>

              <div className="mt-8 grid grid-cols-2 gap-2.5">
                {CAPABILITIES.map(({ icon: Icon, title, description }) => (
                  <div key={title} className="rounded-xl border border-white/10 bg-white/10 px-3.5 py-3">
                    <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-white/15 text-white">
                      <Icon className="h-3.5 w-3.5" />
                    </span>
                    <p className="mt-2.5 text-[12.5px] font-semibold text-white">{title}</p>
                    <p className="mt-0.5 text-[11px] leading-relaxed text-blue-100/75">{description}</p>
                  </div>
                ))}
              </div>
            </div>

            <p className="relative mt-auto pt-8 text-[11px] text-blue-100/60">© 2026 Henfon商城 · 内部管理系统</p>
          </aside>

          {/* 表单区。 */}
          <main className="px-7 py-10 sm:px-10 sm:py-12 lg:flex lg:flex-col lg:justify-center">
            <div className="mb-8 flex items-center gap-3 lg:hidden">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-blue-600 to-indigo-500 text-white shadow-sm">
                <BrandMark className="h-5 w-5" />
              </div>
              <div>
                <p className="text-[15px] font-bold tracking-tight text-slate-900">Henfon电商后台</p>
                <p className="text-[11px] font-medium text-slate-500">E-Commerce RBAC OS</p>
              </div>
            </div>

            <h1 className="text-[22px] font-bold tracking-tight text-slate-900">管理员登录</h1>
            <p className="mt-1.5 text-sm text-slate-500">使用后台账号登录，可见菜单与权限由所属角色决定</p>

            <form onSubmit={submit} className="mt-7">
              <label htmlFor="admin-username" className={LABEL_CLASS}>
                用户名
              </label>
              <div className="relative">
                <User className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <input
                  id="admin-username"
                  ref={usernameRef}
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  required
                  autoComplete="username"
                  placeholder="请输入用户名"
                  aria-invalid={Boolean(error)}
                  className={FIELD_CLASS}
                />
              </div>

              <label htmlFor="admin-password" className={`${LABEL_CLASS} mt-5`}>
                密码
              </label>
              <div className="relative">
                <LockKeyhole className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <input
                  id="admin-password"
                  ref={passwordRef}
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  onKeyUp={detectCapsLock}
                  onKeyDown={detectCapsLock}
                  onBlur={() => setCapsLock(false)}
                  required
                  autoComplete="current-password"
                  placeholder="请输入密码"
                  aria-invalid={Boolean(error)}
                  className={`${FIELD_CLASS} pr-11`}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((visible) => !visible)}
                  aria-label={showPassword ? '隐藏密码' : '显示密码'}
                  className="absolute right-2 top-1/2 -translate-y-1/2 rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {capsLock && (
                <p className="mt-1.5 flex items-center gap-1.5 text-[12px] text-amber-600">
                  <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                  大写锁定（Caps Lock）已开启
                </p>
              )}

              <label htmlFor="admin-captcha" className={`${LABEL_CLASS} mt-5`}>
                验证码
              </label>
              <div className="flex items-center gap-2.5">
                <div className="relative flex-1">
                  <ShieldCheck className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <input
                    id="admin-captcha"
                    value={captchaCode}
                    onChange={(event) => setCaptchaCode(event.target.value.trim().toUpperCase())}
                    required
                    maxLength={4}
                    autoComplete="off"
                    placeholder="请输入右侧字符"
                    aria-invalid={Boolean(error)}
                    className={FIELD_CLASS}
                  />
                </div>
                <button
                  type="button"
                  onClick={() => void refreshCaptcha()}
                  disabled={captchaLoading}
                  aria-label="刷新验证码"
                  title="点击刷新验证码"
                  className="relative h-11 w-[118px] shrink-0 overflow-hidden rounded-xl border border-slate-300 bg-slate-50 transition-colors hover:border-blue-400 disabled:cursor-wait"
                >
                  {captcha ? (
                    <img src={captcha.imageBase64} alt="图形验证码" className="h-full w-full" />
                  ) : (
                    <span className="flex h-full w-full items-center justify-center text-[12px] text-slate-400">
                      {captchaLoading ? '加载中...' : '点击重试'}
                    </span>
                  )}
                  {captcha && captchaLoading && (
                    <span className="absolute inset-0 flex items-center justify-center bg-white/70">
                      <Loader2 className="h-4 w-4 animate-spin text-blue-600" />
                    </span>
                  )}
                </button>
              </div>

              <div className="mt-4 flex items-center justify-between">
                <label className="flex cursor-pointer select-none items-center gap-2 text-[13px] text-slate-600">
                  <input
                    type="checkbox"
                    checked={remember}
                    onChange={(event) => setRemember(event.target.checked)}
                    className="h-4 w-4 rounded border-slate-300 accent-blue-600"
                  />
                  记住用户名
                </label>
                <button
                  type="button"
                  onClick={() => setHint('忘记密码请联系系统管理员重置，重置后用新密码登录即可。')}
                  className="text-[13px] font-medium text-blue-600 transition-colors hover:text-blue-700 hover:underline"
                >
                  忘记密码？
                </button>
              </div>

              {error && (
                <div
                  role="alert"
                  className="mt-4 flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2.5 text-[13px] leading-relaxed text-rose-700"
                >
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                  <span>{error}</span>
                </div>
              )}
              {hint && (
                <p className="mt-4 rounded-xl bg-slate-50 px-3 py-2.5 text-[12px] leading-relaxed text-slate-500">
                  {hint}
                </p>
              )}

              <button
                type="submit"
                disabled={submitting}
                className="group mt-6 flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-500 text-sm font-semibold text-white shadow-lg shadow-blue-600/20 transition hover:from-blue-500 hover:to-indigo-400 hover:shadow-blue-600/30 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-70 disabled:active:scale-100"
              >
                {submitting ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    登录中...
                  </>
                ) : (
                  <>
                    登录后台
                    <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
                  </>
                )}
              </button>
            </form>

            <div className="mt-6 flex items-center justify-between gap-3 rounded-xl border border-dashed border-slate-300 bg-slate-50/80 px-3.5 py-2.5">
              <p className="text-[12px] text-slate-500">
                演示账号 <span className="font-medium text-slate-700">{DEMO_ACCOUNT.username}</span>
                <span className="px-1 text-slate-400">/</span>
                <span className="font-medium text-slate-700">{DEMO_ACCOUNT.password}</span>
              </p>
              <button
                type="button"
                onClick={fillDemoAccount}
                className="shrink-0 text-[12px] font-medium text-blue-600 transition-colors hover:text-blue-700 hover:underline"
              >
                一键填入
              </button>
            </div>

            <p className="mt-8 text-center text-[11px] text-slate-400 lg:hidden">© 2026 Henfon商城 · 内部管理系统</p>
          </main>
        </div>
      </div>
    </div>
  );
};
