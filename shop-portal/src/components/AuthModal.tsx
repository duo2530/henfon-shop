import React, { useState, useEffect } from 'react';
import {
  X,
  Lock,
  Mail,
  Smartphone,
  User,
  Eye,
  EyeOff,
  Sparkles,
  ArrowRight,
  CheckCircle2,
  AlertCircle,
  ShieldCheck,
  Crown,
  KeyRound,
  RotateCcw,
  Gift,
  HelpCircle,
} from 'lucide-react';
import { UserProfile, MemberLevel } from '../types/ecommerce';
import { loginPortalMember, registerPortalMember, MemberAuthResponse } from '../api/portalApi';

export type AuthMode = 'login-pwd' | 'login-sms' | 'register' | 'forgot-pwd';

interface AuthModalProps {
  isOpen: boolean;
  initialMode?: AuthMode;
  demoMode?: boolean;
  onClose: () => void;
  onLoginSuccess: (user: UserProfile, message: string) => void;
}

// Preset Mock Users for One-Click Quick Testing
export const PRESET_TEST_USERS: Array<{
  label: string;
  badge: string;
  badgeColor: string;
  user: UserProfile;
  pass: string;
}> = [
  {
    label: '黑金SVIP 会员',
    badge: '黑金SVIP',
    badgeColor: 'bg-zinc-900 text-amber-300 border-amber-400/40',
    pass: 'svip123',
    user: {
      id: 'usr-svip-001',
      username: 'henfon_svip',
      nickname: 'Henfon黑金·林先生',
      email: 'lin@henfon.com',
      phone: '13888888888',
      avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80',
      memberLevel: '黑金SVIP',
      points: 3680,
      balance: 850.0,
      couponsCount: 6,
      joinedDate: '2025-01-15',
    },
  },
  {
    label: '黄金VIP 会员',
    badge: '黄金VIP',
    badgeColor: 'bg-amber-100 text-amber-900 border-amber-300',
    pass: 'vip123',
    user: {
      id: 'usr-vip-002',
      username: 'henfon_gold',
      nickname: '悦享品质·陈女士',
      email: 'chen.vivian@outlook.com',
      phone: '13966666666',
      avatar: 'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=150&auto=format&fit=crop&q=80',
      memberLevel: '黄金VIP',
      points: 1250,
      balance: 260.0,
      couponsCount: 3,
      joinedDate: '2025-06-20',
    },
  },
  {
    label: '普通新晋会员',
    badge: '普通会员',
    badgeColor: 'bg-zinc-100 text-zinc-700 border-zinc-200',
    pass: 'user123',
    user: {
      id: 'usr-normal-003',
      username: 'henfon_newbie',
      nickname: '极简主义者',
      email: 'alex.minimal@gmail.com',
      phone: '13612345678',
      avatar: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80',
      memberLevel: '普通会员',
      points: 500,
      balance: 50.0,
      couponsCount: 2,
      joinedDate: '2026-03-01',
    },
  },
];

export const AuthModal: React.FC<AuthModalProps> = ({
  isOpen,
  initialMode = 'login-pwd',
  demoMode = false,
  onClose,
  onLoginSuccess,
}) => {
  const [mode, setMode] = useState<AuthMode>(initialMode);
  const [showPassword, setShowPassword] = useState(false);
  const [agreedTerms, setAgreedTerms] = useState(true);
  const [rememberMe, setRememberMe] = useState(true);

  // Form State
  const [accountInput, setAccountInput] = useState('');
  const [passwordInput, setPasswordInput] = useState('');
  const [phoneInput, setPhoneInput] = useState('');
  const [smsCodeInput, setSmsCodeInput] = useState('');
  const [nicknameInput, setNicknameInput] = useState('');
  const [confirmPasswordInput, setConfirmPasswordInput] = useState('');
  const [countryCode, setCountryCode] = useState('+86');

  // SMS Timer state
  const [countdown, setCountdown] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Terms Modal State
  const [showTermsModal, setShowTermsModal] = useState<'service' | 'privacy' | null>(null);

  useEffect(() => {
    if (isOpen) {
      setMode(initialMode);
      setErrorMsg(null);
      setSuccessMsg(null);
    }
  }, [isOpen, initialMode]);

  // Handle countdown
  useEffect(() => {
    let timer: NodeJS.Timeout;
    if (countdown > 0) {
      timer = setInterval(() => {
        setCountdown((prev) => prev - 1);
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [countdown]);

  if (!isOpen) return null;

  // Send SMS Code Simulator
  const handleSendSms = () => {
    if (!phoneInput || phoneInput.length < 7) {
      setErrorMsg('请输入正确的手机号码');
      return;
    }
    setErrorMsg(null);
    setCountdown(60);
    setSmsCodeInput('888888'); // Auto-fill for friendly testing
    setSuccessMsg('验证码已发送（测试环境已为您自动填入: 888888）');
    setTimeout(() => setSuccessMsg(null), 5000);
  };

  // Password strength calculation
  const getPasswordStrength = (pass: string) => {
    if (!pass) return { score: 0, text: '无', color: 'bg-zinc-200' };
    let score = 0;
    if (pass.length >= 6) score += 1;
    if (pass.length >= 10) score += 1;
    if (/[A-Z]/.test(pass) || /[0-9]/.test(pass)) score += 1;
    if (/[^A-Za-z0-9]/.test(pass)) score += 1;

    if (score <= 1) return { score: 1, text: '弱', color: 'bg-rose-500' };
    if (score === 2 || score === 3) return { score: 2, text: '中等', color: 'bg-amber-500' };
    return { score: 3, text: '强', color: 'bg-emerald-500' };
  };

  const passStrength = getPasswordStrength(passwordInput);

  // Quick Login with Test Account
  const handleQuickLogin = (preset: (typeof PRESET_TEST_USERS)[0]) => {
    setIsLoading(true);
    setTimeout(() => {
      setIsLoading(false);
      onLoginSuccess(preset.user, `欢迎回来，${preset.user.nickname}！`);
      onClose();
    }, 400);
  };

  // Submit Handler
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);

    // Terms validation for register/sms
    if ((mode === 'register' || mode === 'login-sms') && !agreedTerms) {
      setErrorMsg('请阅读并勾选同意《用户服务协议》与《隐私政策》');
      return;
    }

    const mapMember = (member: MemberAuthResponse): UserProfile => ({
      id: String(member.memberId),
      username: member.username,
      nickname: member.nickname,
      email: member.email || '',
      phone: member.phone || '',
      avatar: member.avatarUrl || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80',
      memberLevel: (member.memberLevel === 'GOLD' ? '黄金VIP' : member.memberLevel === 'BLACK_GOLD' ? '黑金SVIP' : '普通会员') as MemberLevel,
      points: Number(member.points || 0),
      balance: Number(member.balance || 0),
      couponsCount: 0,
      joinedDate: new Date().toISOString().split('T')[0],
    });

    if (mode === 'login-pwd') {
        if (!accountInput) {
          setErrorMsg('请输入账号 / 邮箱 / 手机号');
          return;
        }
        if (!passwordInput) {
          setErrorMsg('请输入登录密码');
          return;
        }
        setIsLoading(true);
        try {
          const targetUser = mapMember(await loginPortalMember(accountInput.trim(), passwordInput));
          onLoginSuccess(targetUser, `登录成功！欢迎回来，${targetUser.nickname}`);
          onClose();
        } catch (error) {
          setErrorMsg(error instanceof Error ? error.message : '登录失败，请稍后重试');
        } finally {
          setIsLoading(false);
        }
        return;
      }

      if (mode === 'register') {
        if (!nicknameInput) {
          setErrorMsg('请输入用户昵称');
          return;
        }
        if (!accountInput) {
          setErrorMsg('请输入注册邮箱或手机号');
          return;
        }
        if (passwordInput.length < 6) {
          setErrorMsg('密码长度不能少于 6 位字符');
          return;
        }
        if (passwordInput !== confirmPasswordInput) {
          setErrorMsg('两次输入的密码不一致');
          return;
        }
        setIsLoading(true);
        try {
          const account = accountInput.trim();
          const targetUser = mapMember(await registerPortalMember({
            username: account,
            password: passwordInput,
            nickname: nicknameInput.trim(),
            email: account.includes('@') ? account : undefined,
            phone: account.includes('@') ? undefined : account,
          }));
          onLoginSuccess(targetUser, `注册成功！欢迎回来，${targetUser.nickname}`);
          onClose();
        } catch (error) {
          setErrorMsg(error instanceof Error ? error.message : '注册失败，请稍后重试');
        } finally {
          setIsLoading(false);
        }
        return;
      }

    setIsLoading(true);
    setTimeout(() => {
      setIsLoading(false);

      if (mode === 'login-sms') {
        if (!phoneInput) {
          setErrorMsg('请输入手机号');
          return;
        }
        if (!smsCodeInput) {
          setErrorMsg('请输入短信验证码');
          return;
        }

        const newUser: UserProfile = {
          id: `usr-sms-${Date.now()}`,
          username: `phone_${phoneInput.slice(-4)}`,
          nickname: `Henfon用户_${phoneInput.slice(-4)}`,
          email: `${phoneInput}@henfon-user.com`,
          phone: phoneInput,
          avatar:
            'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=150&auto=format&fit=crop&q=80',
          memberLevel: '普通会员',
          points: 800,
          balance: 60.0,
          couponsCount: 4,
          joinedDate: new Date().toISOString().split('T')[0],
        };

        onLoginSuccess(newUser, '手机快捷验证成功！已为您登录账号');
        onClose();
      } else if (mode === 'forgot-pwd') {
        if (!accountInput) {
          setErrorMsg('请输入您的注册手机或邮箱');
          return;
        }
        setSuccessMsg('重置密码链接与临时验证码已发送至您的账号，请查收！');
        setTimeout(() => {
          setMode('login-pwd');
        }, 1800);
      }
    }, 450);
  };

  // Social fast login simulation (WeChat / Google)
  const handleSocialLogin = (platform: 'wechat' | 'google' | 'apple') => {
    setIsLoading(true);
    setTimeout(() => {
      setIsLoading(false);
      const socialUser: UserProfile = {
        id: `usr-${platform}-${Date.now()}`,
        username: `${platform}_user`,
        nickname:
          platform === 'wechat'
            ? '微信极客体验官'
            : platform === 'google'
            ? 'Google Fast Login'
            : 'Apple ID User',
        email: `${platform}.user@henfon.com`,
        phone: '13800138000',
        avatar:
          platform === 'wechat'
            ? 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80'
            : 'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=150&auto=format&fit=crop&q=80',
        memberLevel: '黄金VIP',
        points: 2000,
        balance: 150.0,
        couponsCount: 4,
        joinedDate: new Date().toISOString().split('T')[0],
      };
      onLoginSuccess(
        socialUser,
        `已通过 ${
          platform === 'wechat' ? '微信' : platform === 'google' ? 'Google' : 'Apple'
        } 授权安全登录！`
      );
      onClose();
    }, 400);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-zinc-950/70 backdrop-blur-sm animate-in fade-in duration-200">
      <div
        className="relative w-full max-w-md bg-white rounded-3xl shadow-2xl border border-zinc-100 overflow-hidden animate-in zoom-in-95 duration-200"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Top Decorative Header Banner */}
        <div className="bg-zinc-900 text-white p-6 pb-7 relative overflow-hidden">
          {/* Subtle glow background */}
          <div className="absolute -right-10 -top-10 w-44 h-44 bg-amber-400/15 rounded-full blur-3xl pointer-events-none" />
          <div className="absolute -left-10 -bottom-10 w-36 h-36 bg-amber-500/10 rounded-full blur-2xl pointer-events-none" />

          {/* Close button */}
          <button
            onClick={onClose}
            className="absolute top-4 right-4 p-2 rounded-full bg-zinc-800/80 text-zinc-400 hover:text-white hover:bg-zinc-700 transition"
            title="关闭窗口"
          >
            <X className="w-4 h-4" />
          </button>

          {/* Brand & Heading */}
          <div className="flex items-center gap-2 mb-2">
            <div className="w-8 h-8 rounded-xl bg-amber-400 text-zinc-950 flex items-center justify-center font-black text-sm shadow-md">
              A
            </div>
            <span className="text-xs tracking-wider uppercase font-extrabold text-amber-300">
              Henfon商城
            </span>
          </div>

          <h2 className="text-xl font-black tracking-tight text-white flex items-center gap-2">
            {mode === 'login-pwd' && '欢迎登录Henfon商城'}
            {mode === 'login-sms' && '手机短信免密登录'}
            {mode === 'register' && '开启您的品质好物之旅'}
            {mode === 'forgot-pwd' && '重置与找回登录密码'}
          </h2>

          <p className="text-xs text-zinc-400 mt-1 flex items-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5 text-amber-400 shrink-0" />
            {mode === 'register'
              ? '新用户注册立享 ¥100 优惠礼券包与黄金 VIP 权益'
              : '登录后享会员专属特价、多端云同步与订单积分'}
          </p>

          {/* Mode Switch Tabs */}
          {mode !== 'forgot-pwd' && (
            <div className="flex items-center bg-zinc-800/90 p-1 rounded-xl mt-4 text-xs font-semibold">
              <button
                onClick={() => {
                  setMode('login-pwd');
                  setErrorMsg(null);
                }}
                className={`flex-1 py-1.5 rounded-lg transition text-center ${
                  mode === 'login-pwd'
                    ? 'bg-amber-400 text-zinc-950 shadow-sm font-bold'
                    : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                密码登录
              </button>
              <button
                onClick={() => {
                  setMode('login-sms');
                  setErrorMsg(null);
                }}
                className={`flex-1 py-1.5 rounded-lg transition text-center ${
                  mode === 'login-sms'
                    ? 'bg-amber-400 text-zinc-950 shadow-sm font-bold'
                    : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                短信免密
              </button>
              <button
                onClick={() => {
                  setMode('register');
                  setErrorMsg(null);
                }}
                className={`flex-1 py-1.5 rounded-lg transition text-center ${
                  mode === 'register'
                    ? 'bg-amber-400 text-zinc-950 shadow-sm font-bold'
                    : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                新客注册
              </button>
            </div>
          )}
        </div>

        {/* Form Body */}
        <div className="p-6 space-y-4 max-h-[70vh] overflow-y-auto custom-scrollbar">
          {/* Error Alert */}
          {errorMsg && (
            <div className="p-3 rounded-2xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2 animate-in fade-in">
              <AlertCircle className="w-4 h-4 shrink-0 text-rose-500" />
              <span>{errorMsg}</span>
            </div>
          )}

          {/* Success Alert */}
          {successMsg && (
            <div className="p-3 rounded-2xl bg-emerald-50 border border-emerald-200 text-emerald-700 text-xs flex items-center gap-2 animate-in fade-in">
              <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-500" />
              <span>{successMsg}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-3.5">
            {/* Mode 1: 密码登录 */}
            {mode === 'login-pwd' && (
              <>
                <div className="space-y-1">
                  <label className="text-xs font-bold text-zinc-700 block">账号 / 邮箱 / 手机号</label>
                  <div className="relative flex items-center">
                    <User className="w-4 h-4 text-zinc-400 absolute left-3.5 pointer-events-none" />
                    <input
                      type="text"
                      required
                      value={accountInput}
                      onChange={(e) => setAccountInput(e.target.value)}
                      placeholder="用户名、邮箱或手机号"
                      className="w-full py-2.5 pl-10 pr-3.5 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-900 placeholder:text-zinc-400 focus:bg-white focus:border-zinc-900 focus:ring-2 focus:ring-zinc-900/10 focus:outline-none transition"
                    />
                  </div>
                </div>

                <div className="space-y-1">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-bold text-zinc-700 block">登录密码</label>
                    <button
                      type="button"
                      onClick={() => setMode('forgot-pwd')}
                      className="text-xs text-zinc-500 hover:text-amber-600 transition"
                    >
                      忘记密码？
                    </button>
                  </div>
                  <div className="relative flex items-center">
                    <Lock className="w-4 h-4 text-zinc-400 absolute left-3.5 pointer-events-none" />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      required
                      value={passwordInput}
                      onChange={(e) => setPasswordInput(e.target.value)}
                      placeholder="请输入您的密码"
                      className="w-full py-2.5 pl-10 pr-10 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-900 placeholder:text-zinc-400 focus:bg-white focus:border-zinc-900 focus:ring-2 focus:ring-zinc-900/10 focus:outline-none transition"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="p-2 absolute right-2 text-zinc-400 hover:text-zinc-700"
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div className="flex items-center justify-between text-xs pt-1">
                  <label className="flex items-center gap-2 cursor-pointer select-none text-zinc-600">
                    <input
                      type="checkbox"
                      checked={rememberMe}
                      onChange={(e) => setRememberMe(e.target.checked)}
                      className="w-4 h-4 rounded border-zinc-300 text-zinc-900 focus:ring-zinc-900"
                    />
                    <span>30天内免登录</span>
                  </label>
                  <span className="text-zinc-400">SSL 256位加密传输</span>
                </div>
              </>
            )}

            {/* Mode 2: 手机短信快捷登录 */}
            {mode === 'login-sms' && (
              <>
                <div className="space-y-1">
                  <label className="text-xs font-bold text-zinc-700 block">手机号码</label>
                  <div className="flex gap-2">
                    <select
                      value={countryCode}
                      onChange={(e) => setCountryCode(e.target.value)}
                      className="py-2.5 px-2.5 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-800 focus:outline-none focus:border-zinc-900 shrink-0 font-medium"
                    >
                      <option value="+86">+86 (中国)</option>
                      <option value="+1">+1 (美国/加拿大)</option>
                      <option value="+852">+852 (中国香港)</option>
                      <option value="+886">+886 (中国台湾)</option>
                      <option value="+81">+81 (日本)</option>
                    </select>

                    <div className="relative flex-1 flex items-center">
                      <Smartphone className="w-4 h-4 text-zinc-400 absolute left-3.5 pointer-events-none" />
                      <input
                        type="tel"
                        required
                        value={phoneInput}
                        onChange={(e) => setPhoneInput(e.target.value)}
                        placeholder="请输入11位手机号码"
                        className="w-full py-2.5 pl-10 pr-3.5 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-900 placeholder:text-zinc-400 focus:bg-white focus:border-zinc-900 focus:ring-2 focus:ring-zinc-900/10 focus:outline-none transition"
                      />
                    </div>
                  </div>
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-bold text-zinc-700 block">短信验证码</label>
                  <div className="flex gap-2">
                    <div className="relative flex-1 flex items-center">
                      <KeyRound className="w-4 h-4 text-zinc-400 absolute left-3.5 pointer-events-none" />
                      <input
                        type="text"
                        maxLength={6}
                        required
                        value={smsCodeInput}
                        onChange={(e) => setSmsCodeInput(e.target.value)}
                        placeholder="6位短信验证码"
                        className="w-full py-2.5 pl-10 pr-3.5 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-900 placeholder:text-zinc-400 focus:bg-white focus:border-zinc-900 focus:ring-2 focus:ring-zinc-900/10 focus:outline-none transition font-mono tracking-wider"
                      />
                    </div>
                    <button
                      type="button"
                      disabled={countdown > 0}
                      onClick={handleSendSms}
                      className={`px-3.5 py-2.5 rounded-xl text-xs font-bold whitespace-nowrap transition ${
                        countdown > 0
                          ? 'bg-zinc-100 text-zinc-400 cursor-not-allowed border border-zinc-200'
                          : 'bg-zinc-900 hover:bg-zinc-800 text-white shadow-xs'
                      }`}
                    >
                      {countdown > 0 ? `${countdown}s 后重发` : '获取验证码'}
                    </button>
                  </div>
                </div>
              </>
            )}

            {/* Mode 3: 注册新用户 */}
            {mode === 'register' && (
              <>
                <div className="space-y-1">
                  <label className="text-xs font-bold text-zinc-700 block">用户昵称</label>
                  <div className="relative flex items-center">
                    <User className="w-4 h-4 text-zinc-400 absolute left-3.5 pointer-events-none" />
                    <input
                      type="text"
                      required
                      value={nicknameInput}
                      onChange={(e) => setNicknameInput(e.target.value)}
                      placeholder="设置您的商城个性昵称"
                      className="w-full py-2.5 pl-10 pr-3.5 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-900 placeholder:text-zinc-400 focus:bg-white focus:border-zinc-900 focus:ring-2 focus:ring-zinc-900/10 focus:outline-none transition"
                    />
                  </div>
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-bold text-zinc-700 block">注册手机或常用邮箱</label>
                  <div className="relative flex items-center">
                    <Mail className="w-4 h-4 text-zinc-400 absolute left-3.5 pointer-events-none" />
                    <input
                      type="text"
                      required
                      value={accountInput}
                      onChange={(e) => setAccountInput(e.target.value)}
                      placeholder="用于登录与接收订单发货提醒"
                      className="w-full py-2.5 pl-10 pr-3.5 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-900 placeholder:text-zinc-400 focus:bg-white focus:border-zinc-900 focus:ring-2 focus:ring-zinc-900/10 focus:outline-none transition"
                    />
                  </div>
                </div>

                <div className="space-y-1">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-bold text-zinc-700 block">设置密码</label>
                    {passwordInput && (
                      <span className="text-[11px] text-zinc-500 font-medium">
                        强度: <strong className="text-zinc-800">{passStrength.text}</strong>
                      </span>
                    )}
                  </div>
                  <div className="relative flex items-center">
                    <Lock className="w-4 h-4 text-zinc-400 absolute left-3.5 pointer-events-none" />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      required
                      value={passwordInput}
                      onChange={(e) => setPasswordInput(e.target.value)}
                      placeholder="至少 6 位，推荐包含字母和数字"
                      className="w-full py-2.5 pl-10 pr-10 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-900 placeholder:text-zinc-400 focus:bg-white focus:border-zinc-900 focus:ring-2 focus:ring-zinc-900/10 focus:outline-none transition"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="p-2 absolute right-2 text-zinc-400 hover:text-zinc-700"
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>

                  {/* Password Strength Indicator Bar */}
                  {passwordInput && (
                    <div className="grid grid-cols-3 gap-1 pt-1">
                      <div
                        className={`h-1 rounded-full ${
                          passStrength.score >= 1 ? passStrength.color : 'bg-zinc-200'
                        }`}
                      />
                      <div
                        className={`h-1 rounded-full ${
                          passStrength.score >= 2 ? passStrength.color : 'bg-zinc-200'
                        }`}
                      />
                      <div
                        className={`h-1 rounded-full ${
                          passStrength.score >= 3 ? passStrength.color : 'bg-zinc-200'
                        }`}
                      />
                    </div>
                  )}
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-bold text-zinc-700 block">确认密码</label>
                  <div className="relative flex items-center">
                    <Lock className="w-4 h-4 text-zinc-400 absolute left-3.5 pointer-events-none" />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      required
                      value={confirmPasswordInput}
                      onChange={(e) => setConfirmPasswordInput(e.target.value)}
                      placeholder="请再次输入新密码"
                      className="w-full py-2.5 pl-10 pr-3.5 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-900 placeholder:text-zinc-400 focus:bg-white focus:border-zinc-900 focus:ring-2 focus:ring-zinc-900/10 focus:outline-none transition"
                    />
                  </div>
                </div>

                {/* New user perk pill */}
                <div className="p-2.5 rounded-2xl bg-amber-50 border border-amber-200/80 flex items-center gap-2 text-amber-900 text-xs">
                  <Gift className="w-4 h-4 text-amber-600 shrink-0" />
                  <span>
                    注册赠送 <strong>¥100新人券包</strong> + <strong>1000商城积分</strong>
                  </span>
                </div>
              </>
            )}

            {/* Mode 4: 找回密码 */}
            {mode === 'forgot-pwd' && (
              <>
                <div className="space-y-1">
                  <label className="text-xs font-bold text-zinc-700 block">注册手机或邮箱</label>
                  <div className="relative flex items-center">
                    <Mail className="w-4 h-4 text-zinc-400 absolute left-3.5 pointer-events-none" />
                    <input
                      type="text"
                      required
                      value={accountInput}
                      onChange={(e) => setAccountInput(e.target.value)}
                      placeholder="输入绑定的手机号或邮箱以找回密码"
                      className="w-full py-2.5 pl-10 pr-3.5 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-900 placeholder:text-zinc-400 focus:bg-white focus:border-zinc-900 focus:ring-2 focus:ring-zinc-900/10 focus:outline-none transition"
                    />
                  </div>
                </div>

                <p className="text-xs text-zinc-500">
                  系统将向您的绑定联系方式发送一次性安全重置验证码，点击下方按钮继续。
                </p>
              </>
            )}

            {/* Terms agreement checkbox */}
            {(mode === 'register' || mode === 'login-sms') && (
              <div className="flex items-start gap-2 pt-1 text-xs text-zinc-500">
                <input
                  type="checkbox"
                  id="agree-terms"
                  checked={agreedTerms}
                  onChange={(e) => setAgreedTerms(e.target.checked)}
                  className="mt-0.5 w-4 h-4 rounded border-zinc-300 text-zinc-900 focus:ring-zinc-900"
                />
                <label htmlFor="agree-terms" className="leading-snug select-none">
                  我已认真阅读并完全同意
                  <button
                    type="button"
                    onClick={() => setShowTermsModal('service')}
                    className="text-amber-600 hover:underline mx-0.5 font-medium"
                  >
                    《服务协议》
                  </button>
                  和
                  <button
                    type="button"
                    onClick={() => setShowTermsModal('privacy')}
                    className="text-amber-600 hover:underline mx-0.5 font-medium"
                  >
                    《隐私权保护政策》
                  </button>
                </label>
              </div>
            )}

            {/* Main Submit Button */}
            <button
              type="submit"
              disabled={isLoading}
              className="w-full py-3 px-4 rounded-xl bg-zinc-900 hover:bg-zinc-800 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-md active:scale-98 transition"
            >
              {isLoading ? (
                <span className="inline-block w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                <>
                  <span>
                    {mode === 'login-pwd' && '立即安全登录'}
                    {mode === 'login-sms' && '验证并登录'}
                    {mode === 'register' && '立即注册并领取新人特惠'}
                    {mode === 'forgot-pwd' && '发送重置验证码'}
                  </span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </>
              )}
            </button>

            {/* Back to Login button if in forgot-pwd mode */}
            {mode === 'forgot-pwd' && (
              <button
                type="button"
                onClick={() => setMode('login-pwd')}
                className="w-full py-2 text-center text-xs font-semibold text-zinc-600 hover:text-zinc-900 transition flex items-center justify-center gap-1"
              >
                <RotateCcw className="w-3 h-3" />
                <span>返回账号密码登录</span>
              </button>
            )}
          </form>

          {/* Social Quick Login Section */}
          {mode !== 'forgot-pwd' && (
            <div className="pt-3 border-t border-zinc-100">
              <div className="flex items-center justify-between mb-3">
                <span className="text-[11px] font-bold text-zinc-400">第三方快捷授权登录</span>
                <span className="text-[10px] text-zinc-400 flex items-center gap-1">
                  <ShieldCheck className="w-3 h-3 text-emerald-500" />
                  官方安全认证
                </span>
              </div>

              <div className="grid grid-cols-3 gap-2">
                {/* WeChat */}
                <button
                  type="button"
                  onClick={() => handleSocialLogin('wechat')}
                  className="py-2 px-2.5 rounded-xl border border-zinc-200 hover:border-emerald-400 hover:bg-emerald-50/50 transition flex items-center justify-center gap-1.5 text-xs text-zinc-700 font-medium group"
                >
                  <div className="w-4 h-4 rounded-full bg-emerald-500 text-white flex items-center justify-center text-[10px] font-black">
                    微
                  </div>
                  <span>微信登录</span>
                </button>

                {/* Google */}
                <button
                  type="button"
                  onClick={() => handleSocialLogin('google')}
                  className="py-2 px-2.5 rounded-xl border border-zinc-200 hover:border-blue-400 hover:bg-blue-50/50 transition flex items-center justify-center gap-1.5 text-xs text-zinc-700 font-medium group"
                >
                  <div className="w-4 h-4 rounded-full bg-blue-500 text-white flex items-center justify-center text-[10px] font-black">
                    G
                  </div>
                  <span>Google</span>
                </button>

                {/* Apple */}
                <button
                  type="button"
                  onClick={() => handleSocialLogin('apple')}
                  className="py-2 px-2.5 rounded-xl border border-zinc-200 hover:border-zinc-900 hover:bg-zinc-100 transition flex items-center justify-center gap-1.5 text-xs text-zinc-700 font-medium group"
                >
                  <div className="w-4 h-4 rounded-full bg-zinc-900 text-white flex items-center justify-center text-[10px] font-black">
                    
                  </div>
                  <span>Apple ID</span>
                </button>
              </div>
            </div>
          )}

          {/* 仅在显式演示环境提供快捷测试账号，生产环境不暴露预置身份。 */}
          {demoMode && <div className="p-3 rounded-2xl bg-zinc-50 border border-zinc-200/80 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-bold text-zinc-600 flex items-center gap-1">
                <Crown className="w-3.5 h-3.5 text-amber-500" />
                测试演示账号（免输一键体验）
              </span>
              <span className="text-[10px] text-zinc-400">点击即刻切换身份</span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
              {PRESET_TEST_USERS.map((preset) => (
                <button
                  key={preset.user.id}
                  type="button"
                  onClick={() => handleQuickLogin(preset)}
                  className="p-2 rounded-xl bg-white border border-zinc-200 hover:border-amber-400 hover:shadow-xs transition text-left flex items-center gap-2 group"
                >
                  <img
                    src={preset.user.avatar}
                    alt={preset.user.nickname}
                    className="w-7 h-7 rounded-lg object-cover ring-1 ring-zinc-200 shrink-0"
                  />
                  <div className="min-w-0 flex-1">
                    <p className="text-[11px] font-bold text-zinc-800 truncate group-hover:text-amber-700">
                      {preset.user.nickname}
                    </p>
                    <span
                      className={`inline-block text-[9px] px-1 py-0.2 rounded border font-semibold ${preset.badgeColor}`}
                    >
                      {preset.badge}
                    </span>
                  </div>
                </button>
              ))}
            </div>
          </div>}
        </div>

        {/* Footer Security Badges */}
        <div className="bg-zinc-50 border-t border-zinc-100 py-2.5 px-6 flex items-center justify-between text-[10px] text-zinc-400">
          <span className="flex items-center gap-1">
            <ShieldCheck className="w-3 h-3 text-emerald-500" />
            国家信息安全等级保护认证
          </span>
          <span>客服热线 400-888-9999</span>
        </div>
      </div>

      {/* Interactive Agreement Modal Popover */}
      {showTermsModal && (
        <div className="fixed inset-0 z-60 flex items-center justify-center p-4 bg-zinc-950/80 backdrop-blur-xs animate-in fade-in">
          <div className="bg-white rounded-3xl max-w-lg w-full p-6 shadow-2xl space-y-4 border border-zinc-200 max-h-[80vh] flex flex-col">
            <div className="flex items-center justify-between border-b border-zinc-100 pb-3">
              <h3 className="text-base font-bold text-zinc-900 flex items-center gap-2">
                <ShieldCheck className="w-5 h-5 text-amber-500" />
                {showTermsModal === 'service' ? 'Henfon商城用户服务协议' : 'Henfon商城隐私权与数据保护政策'}
              </h3>
              <button
                onClick={() => setShowTermsModal(null)}
                className="p-1 rounded-lg text-zinc-400 hover:text-zinc-800"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="overflow-y-auto pr-2 space-y-3 text-xs text-zinc-600 leading-relaxed custom-scrollbar flex-1">
              <p className="font-semibold text-zinc-800">一、总则与会员权益说明</p>
              <p>
                欢迎使用Henfon商城。本协议系您与Henfon商城平台之间就账号注册、商品浏览、在线交易及相关客户服务所订立的具有法律效力的合约。
              </p>

              <p className="font-semibold text-zinc-800">二、用户账号与安全保护</p>
              <p>
                您应保证注册信息的真实性与完整性。平台采用业界领先的 256 位 SSL
                端到端加密保护您的密码与敏感支付信息，承诺绝不向任何第三方无故泄露用户个人数据。
              </p>

              <p className="font-semibold text-zinc-800">三、会员积分与专属优惠券</p>
              <p>
                新用户完成注册后将自动获得新人特惠权益，包括无门槛立减券与会员积分。积分可在结算时按比例抵扣现金使用。
              </p>

              <p className="font-semibold text-zinc-800">四、售后及无理由退换保障</p>
              <p>
                商城所有商品均享 100% 正品保障、顺丰极速配送与 7 天无理由退换货服务，保障您的尊贵购物体验。
              </p>
            </div>

            <button
              onClick={() => {
                setAgreedTerms(true);
                setShowTermsModal(null);
              }}
              className="w-full py-2.5 rounded-xl bg-zinc-900 text-white font-bold text-xs hover:bg-zinc-800 transition"
            >
              我已阅读并同意
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
