import React, { FormEvent, useState } from 'react';
import { Eye, EyeOff, LockKeyhole, Sparkles } from 'lucide-react';
import { useAdmin } from '../../context/AdminContext';

export const AdminLogin: React.FC = () => {
  const { login } = useAdmin();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await login(username, password);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '登录失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="relative flex min-h-screen items-center justify-center overflow-hidden bg-slate-950 bg-cover bg-center bg-no-repeat px-4"
      style={{
        backgroundImage: "linear-gradient(115deg, rgba(5, 11, 31, 0.86), rgba(9, 17, 42, 0.68)), url('https://images.unsplash.com/photo-1557682250-33bd709cbe85?auto=format&fit=crop&w=2400&q=85')"
      }}
    >
      {/* 图片上增加轻微暗角，确保白色登录卡片和输入文字始终清晰。 */}
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_50%_45%,transparent_18%,rgba(3,7,20,0.34)_100%)]" />
      {/* 叠加几条细腻曲线，呼应订单流转与数据流动的后台主题。 */}
      <svg
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 h-full w-full opacity-70"
        viewBox="0 0 1440 900"
        preserveAspectRatio="none"
      >
        <defs>
          <linearGradient id="loginCurveCyan" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#67e8f9" stopOpacity="0" />
            <stop offset="42%" stopColor="#67e8f9" stopOpacity="0.55" />
            <stop offset="100%" stopColor="#818cf8" stopOpacity="0.08" />
          </linearGradient>
          <linearGradient id="loginCurveViolet" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#818cf8" stopOpacity="0.05" />
            <stop offset="58%" stopColor="#a78bfa" stopOpacity="0.4" />
            <stop offset="100%" stopColor="#c4b5fd" stopOpacity="0" />
          </linearGradient>
        </defs>
        <path
          d="M-80 650 C 190 480, 310 770, 570 600 S 980 300, 1530 470"
          fill="none"
          stroke="url(#loginCurveCyan)"
          strokeWidth="2"
          className="animate-pulse"
        />
        <path
          d="M-100 760 C 170 570, 360 850, 650 690 S 1060 430, 1540 610"
          fill="none"
          stroke="url(#loginCurveViolet)"
          strokeWidth="1.5"
          opacity="0.85"
        />
        <path
          d="M-60 210 C 230 350, 390 100, 690 240 S 1110 520, 1510 280"
          fill="none"
          stroke="url(#loginCurveCyan)"
          strokeWidth="1"
          opacity="0.45"
        />
      </svg>

      <form onSubmit={submit} className="relative w-full max-w-md rounded-2xl bg-white p-8 shadow-2xl">
        <div className="mb-8 flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-500 text-white">
            <Sparkles className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-slate-900">Henfon电商后台</h1>
            <p className="text-sm text-slate-500">管理员登录</p>
          </div>
        </div>

        <label htmlFor="admin-username" className="mb-2 block text-sm font-medium text-slate-700">用户名</label>
        <input
          id="admin-username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          required
          autoComplete="username"
          className="mb-4 w-full rounded-lg border border-slate-300 px-3 py-2.5 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/15"
        />

        <label htmlFor="admin-password" className="mb-2 block text-sm font-medium text-slate-700">密码</label>
        <div className="relative">
          <LockKeyhole className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-slate-400" />
          <input
            id="admin-password"
            type={showPassword ? 'text' : 'password'}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            autoComplete="current-password"
            className="w-full rounded-lg border border-slate-300 py-2.5 pl-9 pr-11 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/15"
          />
          <button
            type="button"
            onClick={() => setShowPassword((visible) => !visible)}
            aria-label={showPassword ? '隐藏密码' : '显示密码'}
            className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500/30"
          >
            {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        </div>

        {error && <p role="alert" className="mt-3 text-sm text-rose-600">{error}</p>}
        <button disabled={submitting} className="mt-6 w-full rounded-lg bg-blue-600 py-2.5 font-semibold text-white hover:bg-blue-700 disabled:opacity-60">
          {submitting ? '登录中...' : '登录后台'}
        </button>
      </form>
    </div>
  );
};
