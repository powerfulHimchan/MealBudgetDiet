"use client";

import {
  BarChart3,
  CircleDollarSign,
  KeyRound,
  LoaderCircle,
  LogIn,
  Mail,
  ShieldCheck,
  UserPlus,
  UsersRound,
  WalletCards,
} from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { mutation, request } from "../lib/api";

type AuthMode = "login" | "join" | "setup" | "forgot" | "reset";
type AuthScreenProps = {
  mode: AuthMode;
  initialInviteCode?: string;
  initialResetToken?: string;
  nextPath?: string;
};

const content = {
  login: {
    eyebrow: "WELCOME BACK",
    title: "로그인",
    description: "한 번 로그인하면 직접 로그아웃할 때까지 안전하게 유지됩니다.",
    submit: "로그인",
  },
  join: {
    eyebrow: "JOIN SHARED LEDGER",
    title: "초대받은 장부에 참여",
    description: "전달받은 초대 코드로 계정을 만들고 같은 식비 장부를 관리하세요.",
    submit: "가입하고 시작하기",
  },
  setup: {
    eyebrow: "FIRST ADMIN SETUP",
    title: "첫 장부 만들기",
    description: "배포 시 설정한 Bootstrap 토큰으로 최초 관리자와 공유 장부를 만듭니다.",
    submit: "관리자 계정 만들기",
  },
  forgot: {
    eyebrow: "PASSWORD RECOVERY",
    title: "비밀번호 찾기",
    description: "가입한 이메일로 30분 동안 한 번 사용할 수 있는 재설정 링크를 보내드립니다.",
    submit: "재설정 링크 받기",
  },
  reset: {
    eyebrow: "SET NEW PASSWORD",
    title: "새 비밀번호 설정",
    description: "다른 서비스에서 사용하지 않는 새 비밀번호를 입력해 주세요.",
    submit: "비밀번호 재설정",
  },
} as const;

function safeDestination(nextPath?: string) {
  if (!nextPath || !nextPath.startsWith("/") || nextPath.startsWith("//") || nextPath.startsWith("/login")) {
    return "/";
  }
  return nextPath;
}

export function AuthScreen({ mode, initialInviteCode = "", initialResetToken = "", nextPath }: AuthScreenProps) {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirmation, setPasswordConfirmation] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [inviteCode, setInviteCode] = useState(initialInviteCode);
  const [bootstrapToken, setBootstrapToken] = useState("");
  const [ledgerName, setLedgerName] = useState("우리집 식비");
  const [monthlyBudget, setMonthlyBudget] = useState("800000");
  const [bootstrapAvailable, setBootstrapAvailable] = useState<boolean | null>(null);
  const [requestSent, setRequestSent] = useState(false);
  const [resetComplete, setResetComplete] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (mode !== "login" && mode !== "setup") return;
    let active = true;
    request<{ available: boolean }>("/api/v1/bootstrap/status")
      .then((response) => active && setBootstrapAvailable(response.available))
      .catch(() => active && setBootstrapAvailable(false));
    return () => { active = false; };
  }, [mode]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if ((mode === "join" || mode === "setup" || mode === "reset") && password !== passwordConfirmation) {
      setError("비밀번호 확인이 일치하지 않습니다.");
      return;
    }
    if (mode === "setup" && Number(monthlyBudget) <= 0) {
      setError("월 예산은 0원보다 크게 입력해 주세요.");
      return;
    }

    setIsSubmitting(true);
    try {
      if (mode === "login") {
        await mutation("/api/v1/auth/login", "POST", { email, password });
        router.replace(safeDestination(nextPath));
      } else if (mode === "join") {
        await mutation("/api/v1/auth/register", "POST", { inviteCode, email, password, displayName });
        router.replace("/");
      } else if (mode === "setup") {
        await mutation(
          "/api/v1/bootstrap/admin",
          "POST",
          { email, password, displayName, ledgerName, defaultMonthlyBudget: Number(monthlyBudget) },
          { "X-Bootstrap-Token": bootstrapToken },
        );
        router.replace("/");
      } else if (mode === "forgot") {
        await mutation("/api/v1/auth/password-reset-requests", "POST", { email });
        setRequestSent(true);
      } else {
        await mutation("/api/v1/auth/password-resets", "POST", {
          token: initialResetToken,
          newPassword: password,
        });
        setResetComplete(true);
      }
      if (mode === "login" || mode === "join" || mode === "setup") router.refresh();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "요청을 처리하지 못했습니다.");
      setIsSubmitting(false);
    }
  }

  const current = content[mode];
  const submitDisabled = isSubmitting
    || ((mode === "login" || mode === "join" || mode === "setup" || mode === "forgot") && !email)
    || ((mode === "login" || mode === "join" || mode === "setup" || mode === "reset") && !password)
    || ((mode === "join" || mode === "setup") && !displayName)
    || ((mode === "join" || mode === "setup" || mode === "reset") && !passwordConfirmation)
    || (mode === "join" && !inviteCode)
    || (mode === "setup" && (!bootstrapToken || !ledgerName || !monthlyBudget || bootstrapAvailable === false))
    || (mode === "reset" && !initialResetToken);

  return (
    <main className="auth-shell">
      <aside className="auth-promo">
        <Link aria-label="MealBudgetDiet 로그인" className="auth-brand" href="/login">
          <span className="brand-mark">M</span><span>MealBudgetDiet</span>
        </Link>
        <div className="auth-promo-copy">
          <p className="eyebrow">BUDGET WITH PEOPLE YOU TRUST</p>
          <h1>식비를 함께 기록하고<br />예산 안에서 생활하세요.</h1>
          <p>지출 기록부터 월 예산 경고와 기간별 통계까지, 하나의 공유 장부에서 관리합니다.</p>
        </div>
        <ul className="auth-benefits">
          <li><span><UsersRound size={19} /></span><div><strong>공유 장부</strong><small>초대한 사람과 함께 조회하고 편집</small></div></li>
          <li><span><WalletCards size={19} /></span><div><strong>예산 속도 경고</strong><small>설정한 기준부터 초과 위험을 빠르게 안내</small></div></li>
          <li><span><BarChart3 size={19} /></span><div><strong>기간별 통계</strong><small>카테고리와 일자별 소비 흐름 확인</small></div></li>
        </ul>
        <p className="auth-security-note"><ShieldCheck size={16} /> 로그인 정보는 HttpOnly 세션 쿠키로 보호됩니다.</p>
      </aside>

      <section className="auth-form-side">
        <div className="auth-card">
          <span className="auth-card-icon">
            {mode === "login" ? <LogIn size={25} /> : mode === "join" ? <UserPlus size={25} /> : mode === "forgot" ? <Mail size={25} /> : <KeyRound size={25} />}
          </span>
          <p className="eyebrow">{current.eyebrow}</p>
          <h2>{current.title}</h2>
          <p className="auth-card-description">{current.description}</p>

          {mode === "forgot" && requestSent ? (
            <div className="auth-complete-panel" aria-live="polite">
              <Mail size={25} />
              <strong>이메일을 확인해 주세요.</strong>
              <span>가입된 계정이라면 비밀번호 재설정 링크를 보내드렸습니다.</span>
              <Link className="auth-primary-link" href="/login">로그인으로 이동</Link>
            </div>
          ) : mode === "reset" && resetComplete ? (
            <div className="auth-complete-panel" aria-live="polite">
              <ShieldCheck size={25} />
              <strong>새 비밀번호를 저장했습니다.</strong>
              <span>보안을 위해 모든 기기에서 로그아웃했습니다. 새 비밀번호로 로그인해 주세요.</span>
              <Link className="auth-primary-link" href="/login">새 비밀번호로 로그인</Link>
            </div>
          ) : mode === "setup" && bootstrapAvailable === false ? (
            <div className="auth-complete-panel">
              <CircleDollarSign size={25} />
              <strong>첫 장부 설정이 완료되어 있습니다.</strong>
              <span>기존 계정으로 로그인해 주세요.</span>
              <Link className="auth-primary-link" href="/login">로그인으로 이동</Link>
            </div>
          ) : (
            <form className="auth-form" onSubmit={(event) => void submit(event)}>
              {error && <div className="message-banner message-banner--error" role="alert">{error}</div>}
              {mode === "reset" && !initialResetToken && (
                <div className="message-banner message-banner--error" role="alert">
                  비밀번호 재설정 링크가 올바르지 않습니다. 이메일에서 받은 링크를 다시 확인해 주세요.
                </div>
              )}

              {mode === "join" && (
                <label>
                  <span>초대 코드</span>
                  <input autoCapitalize="characters" autoComplete="off" maxLength={80} onChange={(event) => setInviteCode(event.target.value)} placeholder="MBD-..." required value={inviteCode} />
                </label>
              )}
              {mode === "setup" && (
                <>
                  <label>
                    <span>Bootstrap 토큰</span>
                    <input autoComplete="off" maxLength={200} onChange={(event) => setBootstrapToken(event.target.value)} required type="password" value={bootstrapToken} />
                    <small>서버 배포 환경 변수에 설정한 일회용 토큰입니다.</small>
                  </label>
                  <div className="auth-form-row">
                    <label>
                      <span>장부 이름</span>
                      <input maxLength={100} onChange={(event) => setLedgerName(event.target.value)} required value={ledgerName} />
                    </label>
                    <label>
                      <span>기본 월 예산</span>
                      <input inputMode="numeric" min={1} onChange={(event) => setMonthlyBudget(event.target.value)} required type="number" value={monthlyBudget} />
                    </label>
                  </div>
                </>
              )}
              {(mode === "join" || mode === "setup") && (
                <label>
                  <span>표시 이름</span>
                  <input autoComplete="name" maxLength={50} onChange={(event) => setDisplayName(event.target.value)} placeholder="장부에 표시할 이름" required value={displayName} />
                </label>
              )}
              {mode !== "reset" && (
                <label>
                  <span>이메일</span>
                  <input autoComplete="email" maxLength={320} onChange={(event) => setEmail(event.target.value)} placeholder="name@example.com" required type="email" value={email} />
                </label>
              )}
              {mode !== "forgot" && (
                <label>
                  <span>{mode === "reset" ? "새 비밀번호" : "비밀번호"} {mode !== "login" && <small>8~72자</small>}</span>
                  <input autoComplete={mode === "login" ? "current-password" : "new-password"} maxLength={72} minLength={mode === "login" ? undefined : 8} onChange={(event) => setPassword(event.target.value)} required type="password" value={password} />
                </label>
              )}
              {(mode === "join" || mode === "setup" || mode === "reset") && (
                <label>
                  <span>{mode === "reset" ? "새 비밀번호 확인" : "비밀번호 확인"}</span>
                  <input autoComplete="new-password" maxLength={72} minLength={8} onChange={(event) => setPasswordConfirmation(event.target.value)} required type="password" value={passwordConfirmation} />
                </label>
              )}

              <button className="auth-submit" disabled={submitDisabled} type="submit">
                {isSubmitting ? <LoaderCircle className="spin" size={18} /> : mode === "login" ? <LogIn size={18} /> : mode === "join" ? <UserPlus size={18} /> : mode === "forgot" ? <Mail size={18} /> : <KeyRound size={18} />}
                {current.submit}
              </button>
            </form>
          )}

          <div className="auth-card-footer">
            {mode === "login" ? (
              <>
                <span>비밀번호를 잊었다면</span><Link href="/forgot-password">비밀번호 재설정</Link>
                <span>초대 코드를 받았다면</span><Link href="/join">초대 코드로 가입</Link>
                {bootstrapAvailable && <><span>처음 설치한 관리자라면</span><Link href="/setup">첫 장부 설정</Link></>}
              </>
            ) : (
              <><span>이미 계정이 있다면</span><Link href="/login">로그인</Link></>
            )}
          </div>
        </div>
      </section>
    </main>
  );
}
