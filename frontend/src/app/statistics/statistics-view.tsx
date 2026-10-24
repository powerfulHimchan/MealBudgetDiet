"use client";

import { ChevronLeft, ChevronRight, Download, LoaderCircle, TrendingDown, TrendingUp } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { BrandLink } from "../brand-link";
import { CurrentUserAvatar } from "../current-user-avatar";
import { request, todayInSeoul } from "../../lib/api";
import { addMonths, adjacentCycle, budgetCycleContaining, budgetCycleStarting, cycleForDate, weeklyCycleContaining, BudgetCycleUnit } from "../../lib/budget-cycle";
import { BottomNav } from "../ui/bottom-nav";
import { Button } from "../ui/button";
import { DateField } from "../ui/date-field";
import { Hero } from "../ui/hero";
import { PresetChip } from "../ui/preset-chip";
import designStyles from "../ui/design-system.module.css";
import { SpendingAnalysisView, type SpendingAnalysis } from "./spending-analysis";
import { SpendingTrend } from "./spending-trend";
import navigationStyles from "./cycle-navigation.module.css";

type StatisticsData = {
  period: { from: string; to: string };
  totalAmount: number;
  budget: { amount: number; usageRate: number };
  comparison: {
    from: string; to: string; totalAmount: number; changeAmount: number; changeRate: number | null;
    currentPeriod?: DateRange | null; currentAmount?: number; samePoint?: boolean; available?: boolean;
  };
  daily: Array<{ date: string; amount: number }>;
  spendingAnalysis?: SpendingAnalysis | null;
  categories: Array<{ categoryId: string | null; categoryName: string; amount: number; ratio: number }>;
};

type PresetRangeKey = "current" | "previous" | "threeMonths" | "year";
type RangeKey = PresetRangeKey | "custom" | "cycle";
type DateRange = { from: string; to: string };

const won = new Intl.NumberFormat("ko-KR");

function comparisonDateRange(from: string, to: string) {
  const [year, month, day] = from.split("-").map(Number);
  const [endYear, endMonth, endDay] = to.split("-").map(Number);
  const start = `${year}년 ${month}월 ${day}일`;
  const end = year !== endYear ? `${endYear}년 ${endMonth}월 ${endDay}일`
    : month !== endMonth ? `${endMonth}월 ${endDay}일` : `${endDay}일`;
  return from === to ? start : `${start}~${end}`;
}

function presetRange(key: PresetRangeKey, startDay: number, unit: BudgetCycleUnit = "MONTHLY", weekStartDay = 1): DateRange {
  const today = todayInSeoul();
  if (unit === "WEEKLY" && key !== "year") {
    const todayDate = new Date(`${today}T00:00:00Z`);
    if (key === "previous") todayDate.setUTCDate(todayDate.getUTCDate() - 7);
    if (key === "threeMonths") todayDate.setUTCDate(todayDate.getUTCDate() - 14);
    const start = weeklyCycleContaining(todayDate.toISOString().slice(0, 10), weekStartDay);
    const end = weeklyCycleContaining(today, weekStartDay);
    return { from: start.from, to: key === "threeMonths" ? end.to : start.to };
  }
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

const rangeButtons: Array<{ key: PresetRangeKey; label: string }> = [
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
  const [cycleUnit, setCycleUnit] = useState<BudgetCycleUnit>("MONTHLY");
  const [weekStartDay, setWeekStartDay] = useState(1);
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
    const controller = new AbortController();
    request<{ cycleSettings: { budgetCycleStartDay: number; budgetCycleUnit: BudgetCycleUnit; budgetWeekStartDay: number }; statistics: StatisticsData }>(
      "/api/v1/statistics/initial", { signal: controller.signal },
    )
      .then(({ cycleSettings: ledger, statistics }) => {
        const nextRange = statistics.period;
        if (!active) return;
        setBudgetCycleStartDay(ledger.budgetCycleStartDay);
        setCycleUnit(ledger.budgetCycleUnit);
        setWeekStartDay(ledger.budgetWeekStartDay);
        setData(statistics);
        setRange(nextRange);
        setDraft(nextRange);
      })
      .catch((reason: Error) => active && setError(reason.message))
      .finally(() => active && setIsLoading(false));
    return () => { active = false; controller.abort(); };
  }, [initialRange]);

  function selectPreset(key: PresetRangeKey) {
    const nextRange = presetRange(key, budgetCycleStartDay, cycleUnit, weekStartDay);
    setSelectedRange(key);
    setDraft(nextRange);
    void load(nextRange);
  }

  function moveCycle(direction: -1 | 1) {
    if (isLoading || !data) return;
    const boundary = direction === -1 ? range.from : range.to;
    const anchor = cycleForDate(boundary, cycleUnit, budgetCycleStartDay, weekStartDay);
    const cycle = adjacentCycle(anchor, direction, cycleUnit, budgetCycleStartDay, weekStartDay);
    const nextRange = { from: cycle.from, to: cycle.to };
    setSelectedRange("cycle");
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
  const analysisCycle = data ? cycleForDate(data.period.from, cycleUnit, budgetCycleStartDay, weekStartDay) : null;
  const showAnalysis = !isLoading && data && (data.spendingAnalysis?.eligible || data.spendingAnalysis?.status === "NO_EXPENSES")
    && data.period.from === draft.from && data.period.to === draft.to
    && data.period.from === analysisCycle?.from && data.period.to === analysisCycle?.to
    && data.period.to < todayInSeoul();
  const changeIsUp = (data?.comparison.changeAmount ?? 0) > 0;
  const comparison = data?.comparison;
  const comparisonAvailable = comparison?.available !== false;
  const comparisonTitle = selectedRange === "current" || selectedRange === "previous" || selectedRange === "cycle" ? "이전 주기" : "이전 기간";
  const comparisonRate = comparison?.changeRate == null ? null : Number(comparison.changeRate);
  const shorterPreviousPeriod = comparison?.samePoint && comparison.currentPeriod
    && (Date.parse(comparison.to) - Date.parse(comparison.from))
      < (Date.parse(comparison.currentPeriod.to) - Date.parse(comparison.currentPeriod.from));

  return (
    <main className={`app-shell analytics-shell ${designStyles.scope}`}>
      <header className="topbar">
        <BrandLink />
        <CurrentUserAvatar />
      </header>

      <Hero eyebrow="SPENDING ANALYTICS" title="기간별 통계" description="기간별 지출 추이와 카테고리 비중을 확인하세요." action={
        <Button variant="white" href={`/api/v1/expenses/export.csv?${csvParams}`} download><Download aria-hidden="true" size={17} /> CSV 내보내기</Button>
      } />

      <section className="range-panel" aria-label="통계 기간 선택">
        <div className="range-presets" role="group" aria-label="기간 프리셋">
          {rangeButtons.map((button) => (
            <PresetChip selected={selectedRange === button.key} key={button.key} disabled={isLoading} onClick={() => selectPreset(button.key)}>
              {button.label}
            </PresetChip>
          ))}
        </div>
        <form className="custom-range" onSubmit={submitCustom}>
          <DateField label="시작일" ariaLabel="통계 시작일" onChange={(from) => setDraft({ ...draft, from })} value={draft.from} />
          <DateField label="종료일" ariaLabel="통계 종료일" onChange={(to) => setDraft({ ...draft, to })} value={draft.to} />
          <Button disabled={isLoading} type="submit">조회</Button>
        </form>
        <nav className={navigationStyles.navigation} aria-label="주기 이동">
          <Button aria-label="이전 주기" variant="outline" disabled={isLoading || !data} onClick={() => moveCycle(-1)}><ChevronLeft aria-hidden="true" size={16} /><span className={navigationStyles.buttonLabel}>이전 주기</span></Button>
          <div className={navigationStyles.period} aria-live="polite"><span>조회된 기간</span><strong title={comparisonDateRange(range.from, range.to)}>{comparisonDateRange(range.from, range.to)}</strong></div>
          <Button aria-label="다음 주기" variant="outline" disabled={isLoading || !data} onClick={() => moveCycle(1)}><span className={navigationStyles.buttonLabel}>다음 주기</span><ChevronRight aria-hidden="true" size={16} /></Button>
        </nav>
      </section>

      {error && <div className="message-banner message-banner--error">{error}</div>}
      {isLoading && !data ? (
        <section className="page-loading"><LoaderCircle className="spin" size={28} /><strong>통계를 계산하고 있어요</strong></section>
      ) : data && (
        <>
          <SpendingAnalysisView
            key={`${data.period.from}/${data.period.to}`}
            period={data.period}
            analysis={showAnalysis ? data.spendingAnalysis : null}
            disabled={isLoading}
            onAnalyzed={(response) => setData((current) => current?.period.from === response.period.from && current.period.to === response.period.to
              ? { ...current, spendingAnalysis: response.analysis } : current)}
            onRefresh={() => void load(data.period)}
          />

          <section className="summary-grid" aria-label="통계 요약">
            <article><span>총 지출</span><strong>{won.format(data.totalAmount)}원</strong><small>{data.period.from} ~ {data.period.to}</small></article>
            <article><span>기간 예산</span><strong>{won.format(data.budget.amount)}원</strong><small>사용률 {Number(data.budget.usageRate).toFixed(1)}%</small></article>
            <article>
              <span>{comparisonTitle}{data.comparison.samePoint ? " 같은 시점" : ""} 대비</span>
              {comparisonAvailable ? <>
                <strong className={changeIsUp ? "amount-up" : "amount-down"}>
                  {changeIsUp ? <TrendingUp aria-hidden="true" size={20} /> : <TrendingDown aria-hidden="true" size={20} />}
                  {won.format(Math.abs(data.comparison.changeAmount))}원
                </strong>
                <small>{comparisonDateRange(data.comparison.from, data.comparison.to)}보다 {data.comparison.changeAmount === 0 ? "같은 금액을 썼어요" : `${won.format(Math.abs(data.comparison.changeAmount))}원 ${changeIsUp ? "더 썼어요" : "덜 썼어요"}`}{comparisonRate == null ? " (이전 지출이 없어 증감률을 계산할 수 없어요)" : ` (${comparisonRate > 0 ? "+" : ""}${comparisonRate.toFixed(1)}%)`}</small>
                {data.comparison.currentPeriod && <small>비교한 지출: {comparisonDateRange(data.comparison.currentPeriod.from, data.comparison.currentPeriod.to)} · {won.format(data.comparison.currentAmount ?? 0)}원</small>}
                {shorterPreviousPeriod && <small>이전 주기가 짧아 마지막 날짜까지 비교했어요.</small>}
              </> : <><strong>아직 비교할 수 없어요</strong><small>선택한 기간이 시작되면 비교할 수 있어요.</small></>}
            </article>
          </section>

          <div className="analytics-grid">
            <SpendingTrend key={`${data.period.from}/${data.period.to}`} daily={data.daily} period={data.period} weekStartDay={weekStartDay} isLoading={isLoading} />

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
      <BottomNav active="statistics" />
    </main>
  );
}
