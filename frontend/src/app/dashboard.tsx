"use client";

import { ChevronRight, CircleDollarSign, LoaderCircle, Plus, TrendingUp } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { AppNav } from "./app-nav";
import { request } from "../lib/api";
import { CurrentUserAvatar } from "./current-user-avatar";

type DashboardData = {
  yearMonth: string;
  cycleUnit: "MONTHLY" | "WEEKLY";
  period: { from: string; to: string };
  budget: number;
  spent: number;
  remaining: number;
  projectedSpent: number;
  usageRate: number;
  pushUsageThreshold: number;
  status: "NORMAL" | "WARNING" | "EXCEEDED";
  recentExpenses: Array<{
    id: string;
    amount: number;
    spentOn: string;
    categoryName: string | null;
    merchant: string | null;
    version: number;
  }>;
};

const won = new Intl.NumberFormat("ko-KR");
const dateLabel = new Intl.DateTimeFormat("ko-KR", { month: "short", day: "numeric", timeZone: "UTC" });
const statusLabel = { NORMAL: "안정", WARNING: "주의", EXCEEDED: "초과" } as const;

export function Dashboard() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    request<DashboardData>("/api/v1/dashboard?recentSize=5")
      .then((dashboard) => active && setData(dashboard))
      .catch((reason: Error) => active && setError(reason.message));
    return () => { active = false; };
  }, []);

  const displayedRate = Math.max(0, Math.min(100, Number(data?.usageRate ?? 0)));

  return (
    <main className="app-shell">
      <header className="topbar">
        <Link className="brand" href="/" aria-label="MealBudgetDiet 홈">
          <span className="brand-mark">M</span><span>MealBudgetDiet</span>
        </Link>
        <CurrentUserAvatar />
      </header>

      {error && <div className="message-banner message-banner--error">{error}</div>}
      {!data ? (
        <section className="page-loading" aria-label="대시보드 불러오는 중">
          <LoaderCircle className="spin" size={28} /><strong>예산 현황을 불러오고 있어요</strong>
        </section>
      ) : (
        <div className="dashboard-grid">
          <section className="budget-card" aria-labelledby="budget-title">
            <div className="budget-card__topline">
              <div>
                <p className="eyebrow">{data.cycleUnit === "WEEKLY" ? "주간" : data.yearMonth} 예산 주기</p>
                <h1 id="budget-title">
                  {data.remaining >= 0
                    ? `${won.format(data.remaining)}원 남았어요`
                    : `${won.format(Math.abs(data.remaining))}원 초과했어요`}
                </h1>
                <p className="budget-cycle-period">{data.period.from} ~ {data.period.to}</p>
              </div>
              <span className={`status-chip status-chip--${data.status.toLowerCase()}`}>{statusLabel[data.status]}</span>
            </div>

            <div className="progress-track" aria-label={`예산 사용률 ${Math.round(Number(data.usageRate))}%`}>
              <span style={{ width: `${displayedRate}%` }} />
            </div>

            <div className="budget-stats">
              <div><span>이번 주기 사용</span><strong>{won.format(data.spent)}원</strong></div>
              <div><span>전체 예산</span><strong>{won.format(data.budget)}원</strong></div>
              <div className="usage-stat"><span>현재 사용률</span><strong>{Number(data.usageRate).toFixed(1)}%</strong></div>
            </div>

            <div className="projection-panel">
              <span className="projection-icon"><TrendingUp size={19} /></span>
              <div>
                <span>현재 추이 기준 마지막 날 예상 소비액</span>
                <strong>{won.format(data.projectedSpent)}원</strong>
              </div>
              <small>{data.projectedSpent > data.budget ? "현재 속도라면 예산을 초과할 수 있어요." : "현재 속도라면 예산 안에서 마칠 것으로 보여요."}</small>
            </div>

            <Link className="primary-action" href="/expenses?new=1"><Plus size={20} strokeWidth={2.5} />식비 등록</Link>
          </section>

          <section className="recent-card" aria-labelledby="recent-title">
            <div className="section-heading">
              <div><p className="eyebrow">최근 기록</p><h2 id="recent-title">새로 등록된 식비</h2></div>
              <Link href="/expenses">전체 보기<ChevronRight size={17} /></Link>
            </div>

            {data.recentExpenses.length === 0 ? (
              <div className="expense-empty"><CircleDollarSign size={30} /><strong>아직 식비가 없어요</strong><span>첫 식비를 등록해 보세요.</span></div>
            ) : (
              <ul className="expense-list">
                {data.recentExpenses.map((expense, index) => (
                  <li key={expense.id}>
                    <span className={`expense-icon expense-icon--${["mint", "orange", "blue"][index % 3]}`}>
                      <CircleDollarSign size={20} />
                    </span>
                    <div className="expense-copy">
                      <strong>{expense.merchant || expense.categoryName || "식비"}</strong>
                      <span>{expense.categoryName || "미분류"} · {dateLabel.format(new Date(`${expense.spentOn}T00:00:00Z`))}</span>
                    </div>
                    <strong className="expense-amount">-{won.format(expense.amount)}원</strong>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
      <AppNav active="home" />
    </main>
  );
}
