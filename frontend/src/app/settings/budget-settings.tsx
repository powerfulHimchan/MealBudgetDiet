"use client";

import { CalendarRange, CircleDollarSign, LoaderCircle, ShieldCheck, Trash2 } from "lucide-react";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { currentYearMonthInSeoul, mutation, request } from "../../lib/api";
import { budgetCycleContaining } from "../../lib/budget-cycle";
import { CalendarPicker } from "../ui/date-picker";
import { SettingsPageFrame } from "./settings-page-frame";

type Ledger = {
  id: string;
  name: string;
  defaultMonthlyBudget: number;
  budgetCycleStartDay: number;
  memberCount: number;
  currentUserRole: "ADMIN" | "MEMBER";
  version: number;
};
type Budget = {
  yearMonth: string;
  period: { from: string; to: string };
  amount: number;
  source: "DEFAULT" | "MONTHLY_OVERRIDE";
  version: number;
};
type BudgetCycleSettings = { budgetCycleStartDay: number; version: number };

const won = new Intl.NumberFormat("ko-KR");

export function BudgetSettings() {
  const [ledger, setLedger] = useState<Ledger | null>(null);
  const [budget, setBudget] = useState<Budget | null>(null);
  const [yearMonth, setYearMonth] = useState(currentYearMonthInSeoul());
  const [defaultAmount, setDefaultAmount] = useState("");
  const [monthlyAmount, setMonthlyAmount] = useState("");
  const [startDay, setStartDay] = useState("1");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadBudget = useCallback(async (month: string) => {
    const nextBudget = await request<Budget>(`/api/v1/budgets/${month}`);
    setBudget(nextBudget);
    setMonthlyAmount(String(nextBudget.amount));
  }, []);

  const loadAll = useCallback(async (month: string) => {
    setIsLoading(true);
    setError(null);
    try {
      const nextLedger = await request<Ledger>("/api/v1/ledger");
      setLedger(nextLedger);
      setDefaultAmount(String(nextLedger.defaultMonthlyBudget));
      setStartDay(String(nextLedger.budgetCycleStartDay));
      await loadBudget(month);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "예산 설정을 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, [loadBudget]);

  useEffect(() => {
    let active = true;
    request<Ledger>("/api/v1/ledger")
      .then(async (nextLedger) => {
        const currentCycle = budgetCycleContaining(
          new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date()),
          nextLedger.budgetCycleStartDay,
        );
        const nextBudget = await request<Budget>(`/api/v1/budgets/${currentCycle.yearMonth}`);
        if (!active) return;
        setLedger(nextLedger);
        setDefaultAmount(String(nextLedger.defaultMonthlyBudget));
        setStartDay(String(nextLedger.budgetCycleStartDay));
        setYearMonth(currentCycle.yearMonth);
        setBudget(nextBudget);
        setMonthlyAmount(String(nextBudget.amount));
      })
      .catch((reason: Error) => active && setError(reason.message))
      .finally(() => active && setIsLoading(false));
    return () => { active = false; };
  }, []);

  function clearMessages() {
    setError(null);
    setNotice(null);
  }

  async function saveDefault(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!ledger) return;
    setIsSaving(true);
    setError(null);
    try {
      await mutation("/api/v1/budgets/default", "PUT", { amount: Number(defaultAmount), version: ledger.version });
      setNotice("기본 월 예산을 변경했습니다.");
      await loadAll(yearMonth);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "기본 예산을 저장하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
  }

  async function saveBudgetCycle(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!ledger) return;
    setIsSaving(true);
    setError(null);
    try {
      const settings = await mutation<BudgetCycleSettings>("/api/v1/ledger/settings/budget-cycle", "PUT", {
        startDay: Number(startDay),
        version: ledger.version,
      });
      const currentCycle = budgetCycleContaining(
        new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date()),
        settings.budgetCycleStartDay,
      );
      setLedger({ ...ledger, budgetCycleStartDay: settings.budgetCycleStartDay, version: settings.version });
      setStartDay(String(settings.budgetCycleStartDay));
      setYearMonth(currentCycle.yearMonth);
      await loadBudget(currentCycle.yearMonth);
      setNotice("예산 주기 시작일을 변경했습니다.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "예산 주기를 저장하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
  }

  async function saveMonthly(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!budget) return;
    setIsSaving(true);
    setError(null);
    try {
      const next = await mutation<Budget>(`/api/v1/budgets/${yearMonth}`, "PUT", {
        amount: Number(monthlyAmount),
        version: budget.source === "MONTHLY_OVERRIDE" ? budget.version : 0,
      });
      setBudget(next);
      setMonthlyAmount(String(next.amount));
      setNotice(`${yearMonth}의 별도 예산을 저장했습니다.`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "월별 예산을 저장하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
  }

  async function deleteMonthly() {
    if (!budget || budget.source !== "MONTHLY_OVERRIDE") return;
    setIsSaving(true);
    setError(null);
    try {
      await mutation(`/api/v1/budgets/${yearMonth}?version=${budget.version}`, "DELETE");
      await loadBudget(yearMonth);
      setNotice(`${yearMonth}의 별도 예산을 삭제했습니다.`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "월별 예산을 삭제하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
  }

  const isAdmin = ledger?.currentUserRole === "ADMIN";
  const startDayChanged = !!ledger && startDay !== String(ledger.budgetCycleStartDay);
  const defaultChanged = !!ledger && defaultAmount !== String(ledger.defaultMonthlyBudget);
  const monthlyChanged = !!budget && monthlyAmount !== String(budget.amount);
  const previewDay = Math.min(31, Math.max(1, Number(startDay) || 1));
  const previewCycle = budgetCycleContaining(
    new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date()),
    previewDay,
  );

  return (
    <SettingsPageFrame
      badge={ledger && <span className="page-badge"><ShieldCheck size={16} /> {isAdmin ? "관리자" : "멤버"}</span>}
      description="공유 장부에 적용할 기본 예산과 월별 예외 예산을 관리하세요."
      eyebrow="BUDGET SETTINGS"
      showBackLink
      title="예산 관리"
    >
      {error && <div className="message-banner message-banner--error">{error}</div>}
      {notice && <button className="message-banner message-banner--notice" onClick={() => setNotice(null)} type="button">{notice}<span>닫기</span></button>}

      {isLoading || !ledger || !budget ? (
        <section className="page-loading"><LoaderCircle className="spin" size={28} /><strong>예산 설정을 불러오고 있어요</strong></section>
      ) : (
        <div className="settings-grid">
          <section className="setting-panel">
            <div className="panel-heading">
              <span className="panel-icon"><CalendarRange size={21} /></span>
              <div><p className="eyebrow">BUDGET CYCLE</p><h2>예산 주기</h2></div>
            </div>
            <p className="setting-description">급여일이나 카드 결제일에 맞춰 한 달의 시작일을 정하세요. 모든 참여자에게 동일하게 적용됩니다.</p>
            <form className="budget-form" onSubmit={saveBudgetCycle}>
              <label>
                <span>매월 시작일</span>
                <span className="money-input"><input aria-label="예산 주기 시작일" max="31" min="1" onChange={(event) => setStartDay(event.target.value)} readOnly={!isAdmin} required type="number" value={startDay} /><b>일</b></span>
              </label>
              <div className="cycle-preview"><span>현재 주기 미리보기</span><strong>{previewCycle.from} ~ {previewCycle.to}</strong><small>해당 날짜가 없는 달에는 그 달의 마지막 날부터 시작합니다.</small></div>
              {isAdmin ? (
                <div className="setting-form-actions">
                  <button className="secondary-button" disabled={isSaving || !startDayChanged} onClick={() => { setStartDay(String(ledger.budgetCycleStartDay)); clearMessages(); }} type="button">취소</button>
                  <button className="dark-button" disabled={isSaving || !startDayChanged} type="submit">예산 주기 저장</button>
                </div>
              ) : <p className="read-only-note">관리자만 예산 주기를 변경할 수 있습니다.</p>}
            </form>
          </section>

          <section className="setting-panel">
            <div className="panel-heading">
              <span className="panel-icon"><CircleDollarSign size={21} /></span>
              <div><p className="eyebrow">DEFAULT BUDGET</p><h2>기본 월 예산</h2></div>
            </div>
            <p className="setting-description">별도 예산을 지정하지 않은 모든 달에 적용됩니다.</p>
            <form className="budget-form" onSubmit={saveDefault}>
              <label><span>매월 기본 예산</span><span className="money-input"><input min="1" onChange={(event) => setDefaultAmount(event.target.value)} readOnly={!isAdmin} required type="number" value={defaultAmount} /><b>원</b></span></label>
              {isAdmin ? (
                <div className="setting-form-actions">
                  <button className="secondary-button" disabled={isSaving || !defaultChanged} onClick={() => { setDefaultAmount(String(ledger.defaultMonthlyBudget)); clearMessages(); }} type="button">취소</button>
                  <button className="dark-button" disabled={isSaving || !defaultChanged} type="submit">기본 예산 저장</button>
                </div>
              ) : <p className="read-only-note">관리자만 예산을 변경할 수 있습니다.</p>}
            </form>
          </section>

          <section className="setting-panel">
            <div className="panel-heading">
              <span className="panel-icon"><CircleDollarSign size={21} /></span>
              <div><p className="eyebrow">MONTHLY OVERRIDE</p><h2>월별 예산</h2></div>
              <span className={`source-chip source-chip--${budget.source.toLowerCase()}`}>{budget.source === "DEFAULT" ? "기본값 적용" : "별도 설정"}</span>
            </div>
            <p className="setting-description">여행이나 명절처럼 지출 계획이 다른 달만 별도 금액을 지정하세요.</p>
            <form className="budget-form" onSubmit={saveMonthly}>
              <label>
                <span>주기 기준 월</span>
                <CalendarPicker
                  ariaLabel="주기 기준 월"
                  mode="month"
                  onChange={(month) => {
                    if (!month) return;
                    setError(null);
                    setYearMonth(month);
                    void loadBudget(month);
                  }}
                  value={yearMonth}
                />
              </label>
              <label><span>해당 월 예산</span><span className="money-input"><input min="1" onChange={(event) => setMonthlyAmount(event.target.value)} readOnly={!isAdmin} required type="number" value={monthlyAmount} /><b>원</b></span></label>
              {isAdmin && (
                <div className="budget-actions">
                  {budget.source === "MONTHLY_OVERRIDE" && <button className="secondary-button danger-button" disabled={isSaving} onClick={deleteMonthly} type="button"><Trash2 size={16} />별도 설정 삭제</button>}
                  <span className="setting-form-actions">
                    <button className="secondary-button" disabled={isSaving || !monthlyChanged} onClick={() => { setMonthlyAmount(String(budget.amount)); clearMessages(); }} type="button">취소</button>
                    <button className="dark-button" disabled={isSaving || !monthlyChanged} type="submit">월별 예산 저장</button>
                  </span>
                </div>
              )}
            </form>
          </section>

          <section className="budget-guide">
            <strong>현재 적용 금액</strong><span>{won.format(budget.amount)}원</span><p>{budget.period.from} ~ {budget.period.to} 주기에는 {budget.source === "DEFAULT" ? "기본 월 예산" : "별도로 지정한 예산"}이 적용됩니다.</p>
          </section>
        </div>
      )}
    </SettingsPageFrame>
  );
}
