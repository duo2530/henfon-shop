import React, { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import {
  AlertCircle, ArrowRight, Eye, EyeOff, Loader2,
  LockKeyhole, ShieldCheck, User
} from 'lucide-react';
import { getLoginCaptcha, type LoginCaptcha } from '../../api/adminApi';
import { BrandMark } from '../common/BrandMark';
import { useAdmin } from '../../context/AdminContext';

/**
 * 「记住密码」把用户名与密码明文写进浏览器本地，且只在登录成功后落盘。
 * 明文存储是内部演示环境的取舍，生产部署应改为只记用户名、由浏览器凭据管理器接管密码。
 * 登录态 token 不在这里，仍由 adminApi 独立管理。
 */
const REMEMBERED_USERNAME_KEY = 'henfon.admin.remembered-username';
const REMEMBERED_PASSWORD_KEY = 'henfon.admin.remembered-password';

/* 输入框聚焦改用「边框变色 + 柔和外环」替代全局 3px 实心轮廓，焦点依然清晰可见。 */
const FIELD_CLASS =
  'h-11 w-full rounded-lg border border-slate-300 bg-white pl-10 pr-3 text-sm text-slate-900 transition placeholder:text-slate-400 hover:border-slate-400 focus:border-blue-600 focus:ring-2 focus:ring-blue-500/15 focus-visible:outline-none';

const LABEL_CLASS = 'mb-1.5 block text-[13px] font-medium text-slate-700';

/**
 * 背景流线：沿网格线匀速掠过，`top` 对齐 32px 的方格，速度与相位各自错开。
 * 透明度由渐变尾端的品牌色 alpha 决定，越淡的越像远景。
 */
const BACKGROUND_LINES = [
  { top: 96, duration: 18, delay: -3, tint: 'to-blue-600/45' },
  { top: 192, duration: 25, delay: -12, tint: 'to-blue-600/28' },
  { top: 320, duration: 15, delay: -7, tint: 'to-blue-600/40' },
  { top: 448, duration: 28, delay: -20, tint: 'to-blue-600/24' },
  { top: 576, duration: 20, delay: -5, tint: 'to-blue-600/34' },
  { top: 704, duration: 23, delay: -15, tint: 'to-blue-600/28' },
  { top: 832, duration: 17, delay: -9, tint: 'to-blue-600/24' },
];

/** 纯装饰层：不进无障碍树、不接管指针事件；跟随指针做几像素视差。 */
const LoginBackdrop: React.FC = () => {
  const parallaxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // 系统开启「减少动效」时不做视差（CSS 动画由 index.css 的全局兜底停掉）。
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    let frame = 0;
    const handleMove = (event: PointerEvent) => {
      if (frame) return;
      frame = window.requestAnimationFrame(() => {
        frame = 0;
        const layer = parallaxRef.current;
        if (!layer) return;
        const offsetX = (event.clientX / window.innerWidth - 0.5) * -16;
        const offsetY = (event.clientY / window.innerHeight - 0.5) * -16;
        layer.style.transform = `translate3d(${offsetX.toFixed(2)}px, ${offsetY.toFixed(2)}px, 0)`;
      });
    };
    window.addEventListener('pointermove', handleMove, { passive: true });
    return () => {
      window.removeEventListener('pointermove', handleMove);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, []);

  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <div ref={parallaxRef} className="absolute -inset-16 will-change-transform">
        {/* 仓储实景打底：照片本身柔化一档退到后面，两层白纱分别压上下与左右，卡片四周才不会跟背景较劲。 */}
        <div
          className="absolute inset-0 bg-cover bg-center"
          style={{
            backgroundImage: `url(${import.meta.env.BASE_URL}login-bg.jpg)`,
            filter: 'saturate(0.88) contrast(0.94) brightness(1.02)',
          }}
        />
        <div className="absolute inset-0 bg-gradient-to-b from-white/66 via-white/52 to-white/76" />
        <div className="absolute inset-0 bg-gradient-to-r from-white/55 via-white/20 to-white/55" />
        <div className="login-bg-grid absolute inset-0" />
      </div>
      {BACKGROUND_LINES.map((line) => (
        <span
          key={line.top}
          style={{ top: line.top, animationDuration: `${line.duration}s`, animationDelay: `${line.delay}s` }}
          className={`login-bg-line absolute -left-32 h-px w-32 bg-gradient-to-r from-transparent ${line.tint}`}
        />
      ))}
    </div>
  );
};

export const AdminLogin: React.FC = () => {
  const { login } = useAdmin();
  const [username, setUsername] = useState(() => localStorage.getItem(REMEMBERED_USERNAME_KEY) ?? '');
  const [password, setPassword] = useState(() => localStorage.getItem(REMEMBERED_PASSWORD_KEY) ?? '');
  const [remember, setRemember] = useState(
    () => Boolean(localStorage.getItem(REMEMBERED_USERNAME_KEY) || localStorage.getItem(REMEMBERED_PASSWORD_KEY))
  );
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

  /** 账号已回填就直接落到密码框（密码也记住时，核一下就能提交），否则落用户名框。 */
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
      // 登录成功后再落盘，避免把输错的账号密码记成默认值。
      if (remember) {
        localStorage.setItem(REMEMBERED_USERNAME_KEY, username);
        localStorage.setItem(REMEMBERED_PASSWORD_KEY, password);
      } else {
        localStorage.removeItem(REMEMBERED_USERNAME_KEY);
        localStorage.removeItem(REMEMBERED_PASSWORD_KEY);
      }
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

  return (
    /* isolate 让背景层能安全地待在内容之下（-z-10），页面本身不用改定位。 */
    <div className="relative isolate flex min-h-screen items-center justify-center bg-gradient-to-b from-white via-slate-100 to-slate-200 px-4 py-8">
      <LoginBackdrop />
      <main className="w-full max-w-[400px]">
        <div className="rounded-xl border border-slate-200 bg-white px-7 py-8 shadow-[0_1px_2px_rgba(15,23,42,0.04),0_16px_40px_-28px_rgba(15,23,42,0.35)] sm:px-8">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-600 text-white">
              <BrandMark className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-[15px] font-bold tracking-tight text-slate-900">Henfon电商后台</h1>
              <p className="text-[11px] font-medium text-slate-500">E-Commerce RBAC OS</p>
            </div>
          </div>

          <form onSubmit={submit} className="mt-8">
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

            <label htmlFor="admin-password" className={`${LABEL_CLASS} mt-4`}>
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
                className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
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

            <label htmlFor="admin-captcha" className={`${LABEL_CLASS} mt-4`}>
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
                  placeholder="请输入验证码"
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
                className="relative h-11 w-[118px] shrink-0 overflow-hidden rounded-lg border border-slate-300 bg-slate-50 transition-colors hover:border-slate-400 disabled:cursor-wait"
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
                记住密码
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
                className="mt-4 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2.5 text-[13px] leading-relaxed text-rose-700"
              >
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}
            {hint && (
              <p className="mt-4 rounded-lg bg-slate-50 px-3 py-2.5 text-[12px] leading-relaxed text-slate-500">
                {hint}
              </p>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="group mt-6 flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-blue-600 text-sm font-semibold text-white transition-colors hover:bg-blue-700 active:bg-blue-800 disabled:cursor-not-allowed disabled:opacity-60"
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
        </div>

        <p className="mt-6 text-center text-[11px] text-slate-400">© 2026 Henfon商城 · 内部管理系统</p>
      </main>
    </div>
  );
};
