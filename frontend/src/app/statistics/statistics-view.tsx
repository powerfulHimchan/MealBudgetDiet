"use client";

import { CalendarRange, Download, LoaderCircle, TrendingDown, TrendingUp } from "lucide-react";
import Link from "next/link";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { AppNav } from "../app-nav";
import { request, todayInSeoul } from "../../lib/api";
import { addMonths, budgetCycleContaining, budgetCycleStarting } from "../../lib/budget-cycle";
import { CalendarPicker } from "../ui/date-picker";

type StatisticsData = {
  period: { from: string; to: string };
  totalAmount: number;
  budget: { amount: number; usageRate: number };
  comparison: { from: string; to: string; totalAmount: number; changeAmount: number; changeRate: number | null };
  daily: Array<{ date: string; amount: number }>;
  categories: Array<{ categoryId: string | null; categoryName: string; amount: number; ratio: number }>;
};

type RangeKey = "current" | "previous" | "threeMonths" | "year" | "custom";
type DateRange = { from: string; to: string };

const won = new Intl.NumberFormat("ko-KR");
const compactWon = new Intl.NumberFormat("ko-KR", { notation: "compact", maximumFractionDigits: 1 });

function presetRange(key: Exclude<RangeKey, "custom">, startDay: number): DateRange {
  const today = todayInSeoul();
  const currentCycle = budgetCycleContaining(today, startDay);
  if (key === "current") {
    return { from: currentCycle.from, to: currentCycle.to };
  }
  if (key === "previous") {
    const cycle = budgetCycleStarting(addMonths(currentCycle.yearMonth, -1), startDay);
    return { from: cycle.from, to: cycle.to };
  }
  if (key === "threeMonths") {
    const firstCycle = budgetCycleStarting(addMonths(currentCycle.yearMonth, -2), startDay);
    return { from: firstCycle.from, to: currentCycle.to };
  }
  const year = today.slice(0, 4);
  return { from: `${year}-01-01`, to: `${year}-12-31` };
}

const rangeButtons: Array<{ key: Exclude<RangeKey, "custom">; label: string }> = [
  { key: "current", label: "현재 주기" },
  { key: "previous", label: "직전 주기" },
  { key: "threeMonths", label: "최근 3개 주기" },
  { key: "year", label: "올해" },
];

export function StatisticsView() {
  const initialRange = useMemo(() => presetRange("current", 1), []);
  const [range, setRange] = useState<DateRange>(initialRange);
  const [draft, setDraft] = useState<DateRange>(initialRange);
  const [selectedRange, setSelectedRange] = useState<RangeKey>("current");
  const [budgetCycleStartDay, setBudgetCycleStartDay] = useState(1);
  const [data, setData] = useState<StatisticsData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (nextRange: DateRange) => {
    setIsLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams(nextRange);
      setData(await request<StatisticsData>(`/api/v1/statistics?${params}`));
      setRange(nextRange);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "통계를 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    request<{ budgetCycleStartDay: number }>("/api/v1/ledger")
      .then(async (ledger) => {
        const nextRange = presetRange("current", ledger.budgetCycleStartDay);
        const params = new URLSearchParams(nextRange);
        const statistics = await request<StatisticsData>(`/api/v1/statistics?${params}`);
        if (!active) return;
        setBudgetCycleStartDay(ledger.budgetCycleStartDay);
        setData(statistics);
        setRange(nextRange);
        setDraft(nextRange);
      })
      .catch((reason: Error) => active && setError(reason.message))
      .finally(() => active && setIsLoading(false));
    return () => { active = false; };
  }, [initialRange]);

  function selectPreset(key: Exclude<RangeKey, "custom">) {
    const nextRange = presetRange(key, budgetCycleStartDay);
    setSelectedRange(key);
    setDraft(nextRange);
    void load(nextRange);
  }

  function submitCustom(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (draft.from > draft.to) {
      setError("시작일은 종료일보다 늦을 수 없습니다.");
      return;
    }
    setSelectedRange("custom");
    void load(draft);
  }

  const csvParams = new URLSearchParams(range);
  const changeIsUp = (data?.comparison.changeAmount ?? 0) > 0;

  return (
    <main className="app-shell analytics-shell">
      <header className="topbar">
        <Link className="brand" href="/"><span className="brand-mark">M</span><span>MealBudgetDiet</span></Link>
        <span className="page-badge"><CalendarRange size={16} /> 분석</span>
      </header>

      <section className="page-hero">
        <div><p className="eyebrow">SPENDING ANALYTICS</p><h1>기간별 통계</h1><p>기간별 지출 추이와 카테고리 비중을 확인하세요.</p></div>
        <a className="secondary-button export-button" href={`/api/v1/expenses/export.csv?${csvParams}`} download>
          <Download size={17} /> CSV 내보내기
        </a>
      </section>

      <section className="range-panel" aria-label="통계 기간 선택">
        <div className="range-presets">
          {rangeButtons.map((button) => (
            <button className={selectedRange === button.key ? "is-active" : undefined} key={button.key} onClick={() => selectPreset(button.key)} type="button">
              {button.label}
            </button>
          ))}
        </div>
        <form className="custom-range" onSubmit={submitCustom}>
          <label><span>시작일</span><CalendarPicker ariaLabel="통계 시작일" onChange={(from) => setDraft({ ...draft, from })} value={draft.from} /></label>
          <label><span>종료일</span><CalendarPicker ariaLabel="통계 종료일" onChange={(to) => setDraft({ ...draft, to })} value={draft.to} /></label>
          <button className="dark-button" type="submit">조회</button>
        </form>
      </section>

      {error && <div className="message-banner message-banner--error">{error}</div>}
      {isLoading && !data ? (
        <section className="page-loading"><LoaderCircle className="spin" size={28} /><strong>통계를 계산하고 있어요</strong></section>
      ) : data && (
        <>
          <section className="summary-grid" aria-label="통계 요약">
            <article><span>총 지출</span><strong>{won.format(data.totalAmount)}원</strong><small>{data.period.from} ~ {data.period.to}</small></article>
            <article><span>기간 예산</span><strong>{won.format(data.budget.amount)}원</strong><small>사용률 {Number(data.budget.usageRate).toFixed(1)}%</small></article>
            <article>
              <span>이전 기간 대비</span>
              <strong className={changeIsUp ? "amount-up" : "amount-down"}>
                {changeIsUp ? <TrendingUp size={20} /> : <TrendingDown size={20} />}
                {data.comparison.changeAmount > 0 ? "+" : ""}{won.format(data.comparison.changeAmount)}원
              </strong>
              <small>{data.comparison.changeRate == null ? "비교 기준 없음" : `${Number(data.comparison.changeRate).toFixed(1)}% 변화`}</small>
            </article>
          </section>

          <div className="analytics-grid">
            <section className="chart-panel">
              <div className="panel-heading"><div><p className="eyebrow">DAILY TREND</p><h2>일별 지출 추이</h2></div>{isLoading && <LoaderCircle className="spin" size={20} />}</div>
              <div className="chart-wrap" role="img" aria-label="일별 지출 추이 차트">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={data.daily} margin={{ top: 8, right: 8, left: -12, bottom: 0 }}>
                    <defs><linearGradient id="spending-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#3b82f6" stopOpacity={0.4} /><stop offset="100%" stopColor="#3b82f6" stopOpacity={0.02} /></linearGradient></defs>
                    <CartesianGrid stroke="#dbe4f0" strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="date" tickFormatter={(value: string) => value.slice(5)} tick={{ fontSize: 11, fill: "#64748b" }} axisLine={false} tickLine={false} minTickGap={22} />
                    <YAxis tickFormatter={(value: number) => compactWon.format(value)} tick={{ fontSize: 11, fill: "#64748b" }} axisLine={false} tickLine={false} />
                    <Tooltip formatter={(value) => [`${won.format(Number(value))}원`, "지출"]} labelFormatter={(label) => String(label)} />
                    <Area isAnimationActive={false} type="monotone" dataKey="amount" stroke="#2563eb" strokeWidth={2} fill="url(#spending-fill)" />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </section>

            <section className="category-stat-panel">
              <div className="panel-heading"><div><p className="eyebrow">CATEGORY SHARE</p><h2>카테고리별 비중</h2></div></div>
              {data.categories.length === 0 ? <div className="compact-empty">선택한 기간에 지출이 없습니다.</div> : (
                <ul className="category-bars">
                  {data.categories.map((category) => (
                    <li key={category.categoryId ?? category.categoryName}>
                      <div><strong>{category.categoryName}</strong><span>{Number(category.ratio).toFixed(1)}%</span></div>
                      <div className="category-track"><span style={{ width: `${Math.min(100, Number(category.ratio))}%` }} /></div>
                      <small>{won.format(category.amount)}원</small>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </>
      )}
      <AppNav active="statistics" />
    </main>
  );
}
