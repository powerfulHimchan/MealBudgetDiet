"use client";

import { CalendarRange, CircleDollarSign, LoaderCircle, ShieldCheck, Trash2 } from "lucide-react";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { currentYearMonthInSeoul, mutation, request } from "../../lib/api";
import { budgetCycleContaining, cycleForDate, BudgetCycleUnit } from "../../lib/budget-cycle";
import { CalendarPicker } from "../ui/date-picker";
import { CustomSelect } from "../ui/custom-select";
import { SettingsPageFrame } from "./settings-page-frame";
import { BudgetRolloverDetails, type BudgetRollover } from "../budget-rollover-details";

type Ledger = {
  id: string;
  name: string;
  defaultMonthlyBudget: number;
  budgetCycleStartDay: number;
  budgetCycleUnit: BudgetCycleUnit;
  budgetWeekStartDay: number;
  defaultWeeklyBudget: number | null;
  memberCount: number;
  currentUserRole: "ADMIN" | "MEMBER";
  version: number;
};
type Budget = {
  yearMonth: string;
  period: { from: string; to: string };
  amount: number;
  source: "DEFAULT" | "MONTHLY_OVERRIDE" | "WEEKLY_DEFAULT";
  version: number;
  rollover?: BudgetRollover;
};
type RolloverSettings = { carrySurplus: boolean; carryDeficit: boolean; effectiveFrom: string; pending: boolean; version: number };
type BudgetCycleSettings = Pick<Ledger, "budgetCycleUnit" | "budgetCycleStartDay" | "budgetWeekStartDay" | "defaultWeeklyBudget" | "version">;

const won = new Intl.NumberFormat("ko-KR");

export function BudgetSettings() {
  const [ledger, setLedger] = useState<Ledger | null>(null);
  const [budget, setBudget] = useState<Budget | null>(null);
  const [yearMonth, setYearMonth] = useState(currentYearMonthInSeoul());
  const [defaultAmount, setDefaultAmount] = useState("");
  const [monthlyAmount, setMonthlyAmount] = useState("");
  const [startDay, setStartDay] = useState("1");
  const [unit, setUnit] = useState<BudgetCycleUnit>("MONTHLY");
  const [weekStartDay, setWeekStartDay] = useState("1");
  const [weeklyAmount, setWeeklyAmount] = useState("");
  const [rolloverSettings, setRolloverSettings] = useState<RolloverSettings | null>(null);
  const [carrySurplus, setCarrySurplus] = useState(false);
  const [carryDeficit, setCarryDeficit] = useState(false);
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
      setUnit(nextLedger.budgetCycleUnit);
      setWeekStartDay(String(nextLedger.budgetWeekStartDay));
      setWeeklyAmount(nextLedger.defaultWeeklyBudget == null ? "" : String(nextLedger.defaultWeeklyBudget));
      const nextRollover = await request<RolloverSettings>("/api/v1/budgets/rollover");
      setRolloverSettings(nextRollover);
      setCarrySurplus(nextRollover.carrySurplus);
      setCarryDeficit(nextRollover.carryDeficit);
      if (nextLedger.budgetCycleUnit === "WEEKLY") {
        setBudget(await request<Budget>("/api/v1/budgets/current"));
      } else {
        await loadBudget(month);
      }
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
        const nextBudget = await request<Budget>(nextLedger.budgetCycleUnit === "WEEKLY"
          ? "/api/v1/budgets/current" : `/api/v1/budgets/${currentCycle.yearMonth}`);
        const nextRollover = await request<RolloverSettings>("/api/v1/budgets/rollover");
        if (!active) return;
        setRolloverSettings(nextRollover);
        setCarrySurplus(nextRollover.carrySurplus);
        setCarryDeficit(nextRollover.carryDeficit);
        setLedger(nextLedger);
        setDefaultAmount(String(nextLedger.defaultMonthlyBudget));
        setStartDay(String(nextLedger.budgetCycleStartDay));
        setUnit(nextLedger.budgetCycleUnit);
        setWeekStartDay(String(nextLedger.budgetWeekStartDay));
        setWeeklyAmount(nextLedger.defaultWeeklyBudget == null ? "" : String(nextLedger.defaultWeeklyBudget));
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

  async function saveRollover(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!ledger) return;
    setIsSaving(true);
    clearMessages();
    try {
      const next = await mutation<RolloverSettings>("/api/v1/budgets/rollover", "PUT", {
        carrySurplus, carryDeficit, version: ledger.version,
      });
      setRolloverSettings(next);
      setLedger({ ...ledger, version: next.version });
      setNotice(`예산 이월 설정을 저장했습니다. ${next.effectiveFrom}부터 적용됩니다.`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "이월 설정을 저장하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
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
        unit,
        startDay: Number(startDay),
        weekStartDay: Number(weekStartDay),
        weeklyBudget: weeklyAmount ? Number(weeklyAmount) : null,
        version: ledger.version,
      });
      const currentCycle = budgetCycleContaining(
        new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date()),
        settings.budgetCycleStartDay,
      );
      setLedger({ ...ledger, ...settings });
      setStartDay(String(settings.budgetCycleStartDay));
      setUnit(settings.budgetCycleUnit);
      setWeekStartDay(String(settings.budgetWeekStartDay));
      setWeeklyAmount(settings.defaultWeeklyBudget == null ? "" : String(settings.defaultWeeklyBudget));
      setYearMonth(currentCycle.yearMonth);
      if (settings.budgetCycleUnit === "WEEKLY") {
        setBudget(await request<Budget>("/api/v1/budgets/current"));
      } else {
        await loadBudget(currentCycle.yearMonth);
      }
      setNotice("예산 주기를 변경했습니다.");
      const nextRollover = await request<RolloverSettings>("/api/v1/budgets/rollover");
      setRolloverSettings(nextRollover);
      setCarrySurplus(nextRollover.carrySurplus);
      setCarryDeficit(nextRollover.carryDeficit);
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
      await mutation<Budget>(`/api/v1/budgets/${yearMonth}`, "PUT", {
        amount: Number(monthlyAmount),
        version: budget.source === "MONTHLY_OVERRIDE" ? budget.version : 0,
      });
      await loadBudget(yearMonth);
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
  const startDayChanged = !!ledger && (unit !== ledger.budgetCycleUnit
    || (unit === "MONTHLY" && startDay !== String(ledger.budgetCycleStartDay))
    || (unit === "WEEKLY" && (weekStartDay !== String(ledger.budgetWeekStartDay)
      || weeklyAmount !== (ledger.defaultWeeklyBudget == null ? "" : String(ledger.defaultWeeklyBudget)))));
  const defaultChanged = !!ledger && defaultAmount !== String(ledger.defaultMonthlyBudget);
  const monthlyChanged = !!budget && monthlyAmount !== String(budget.amount);
  const rolloverChanged = !!rolloverSettings && (carrySurplus !== rolloverSettings.carrySurplus || carryDeficit !== rolloverSettings.carryDeficit);
  const previewDay = Math.min(31, Math.max(1, Number(startDay) || 1));
  const previewCycle = cycleForDate(
    new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date()),
    unit, previewDay, Number(weekStartDay),
  );

  return (
    <SettingsPageFrame
      badge={ledger && <span className="page-badge"><ShieldCheck size={16} /> {isAdmin ? "관리자" : "멤버"}</span>}
      description="공유 장부의 예산 주기와 금액을 관리하세요."
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
            <p className="setting-description">월간 또는 주간 주기와 시작일을 정하세요. 모든 멤버에게 동일하게 적용됩니다.</p>
            <form className="budget-form" onSubmit={saveBudgetCycle}>
              <div className="budget-field"><span>주기 단위</span><CustomSelect ariaLabel="예산 주기 단위" disabled={!isAdmin} onChange={(value) => setUnit(value as BudgetCycleUnit)} options={[{ value: "MONTHLY", label: "월" }, { value: "WEEKLY", label: "주" }]} value={unit} /></div>
              {unit === "MONTHLY" ? (
                <label>
                  <span>매월 시작일</span>
                  <span className="money-input"><input aria-label="예산 주기 시작일" max="31" min="1" onChange={(event) => setStartDay(event.target.value)} readOnly={!isAdmin} required type="number" value={startDay} /><b>일</b></span>
                </label>
              ) : (
                <>
                  <div className="budget-field"><span>매주 시작 요일</span><CustomSelect ariaLabel="예산 주기 시작 요일" disabled={!isAdmin} onChange={setWeekStartDay} options={["월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"].map((label, index) => ({ value: String(index + 1), label }))} value={weekStartDay} /></div>
                  <label><span>기본 주 예산</span><span className="money-input"><input aria-label="기본 주 예산" min="1" onChange={(event) => setWeeklyAmount(event.target.value)} readOnly={!isAdmin} required type="number" value={weeklyAmount} /><b>원</b></span></label>
                  <p className="setting-description">월 예산과 주 예산은 별도로 저장됩니다. 주간으로 전환할 때 금액을 직접 입력해 주세요.</p>
                </>
              )}
              <div className="cycle-preview"><span>현재 주기 미리보기</span><strong>{previewCycle.from} ~ {previewCycle.to}</strong>{unit === "MONTHLY" && <small>해당 날짜가 없는 달에는 그 달의 마지막 날부터 시작합니다.</small>}</div>
              {isAdmin ? (
                <div className="setting-form-actions">
                  <button className="secondary-button" disabled={isSaving || !startDayChanged} onClick={() => { setUnit(ledger.budgetCycleUnit); setStartDay(String(ledger.budgetCycleStartDay)); setWeekStartDay(String(ledger.budgetWeekStartDay)); setWeeklyAmount(ledger.defaultWeeklyBudget == null ? "" : String(ledger.defaultWeeklyBudget)); clearMessages(); }} type="button">취소</button>
                  <button className="dark-button" disabled={isSaving || !startDayChanged} type="submit">예산 주기 저장</button>
                </div>
              ) : <p className="read-only-note">관리자만 예산 주기를 변경할 수 있습니다.</p>}
            </form>
          </section>

          {unit === "MONTHLY" && <section className="setting-panel">
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
          </section>}

          {unit === "MONTHLY" && <section className="setting-panel">
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
          </section>}

          {rolloverSettings && <section className="setting-panel">
            <div className="panel-heading"><span className="panel-icon"><CircleDollarSign size={21} /></span><div><p className="eyebrow">BUDGET ROLLOVER</p><h2>예산 이월</h2></div></div>
            <p className="setting-description">직전 주기에 남거나 초과한 금액을 다음 주기 예산에 반영합니다. 모든 멤버에게 동일하게 적용됩니다.</p>
            <form className="budget-form" onSubmit={saveRollover}>
              <label className="rollover-option"><input checked={carrySurplus} disabled={!isAdmin || isSaving} onChange={(event) => setCarrySurplus(event.target.checked)} type="checkbox" /><span>남은 금액 이월<small>남은 예산을 다음 주기 예산에 더합니다.</small></span></label>
              <label className="rollover-option"><input checked={carryDeficit} disabled={!isAdmin || isSaving} onChange={(event) => setCarryDeficit(event.target.checked)} type="checkbox" /><span>초과 금액 이월<small>초과 지출을 다음 주기 예산에서 차감합니다.</small></span></label>
              <p className="setting-description">변경한 설정은 다음 주기부터 적용됩니다. 처음 켜면 이번 주기의 잔액부터 이월하며, 지난 지출을 수정하면 이월금도 다시 계산됩니다.</p>
              {rolloverSettings.pending && <p className="read-only-note">저장된 설정 적용 시작일: {rolloverSettings.effectiveFrom}</p>}
              <p className="setting-description">주간·월간 또는 시작일을 변경하면 새 기준의 이번 주기 잔액부터 다시 이월합니다.</p>
              {isAdmin ? <div className="setting-form-actions">
                <button className="secondary-button" disabled={isSaving || !rolloverChanged} onClick={() => { setCarrySurplus(rolloverSettings.carrySurplus); setCarryDeficit(rolloverSettings.carryDeficit); clearMessages(); }} type="button">취소</button>
                <button className="dark-button" disabled={isSaving || !rolloverChanged} type="submit">이월 설정 저장</button>
              </div> : <p className="read-only-note">관리자만 이월 설정을 변경할 수 있습니다.</p>}
            </form>
          </section>}

          <section className="budget-guide">
            <strong>현재 적용 금액</strong><span>{won.format(budget.rollover?.enabled ? Math.max(0, budget.rollover.effectiveAmount) : budget.amount)}원</span><p>{budget.period.from} ~ {budget.period.to} 주기에는 {budget.source === "WEEKLY_DEFAULT" ? "기본 주 예산" : budget.source === "DEFAULT" ? "기본 월 예산" : "별도로 지정한 예산"}이 적용됩니다.</p>
            <BudgetRolloverDetails value={budget.rollover} />
          </section>
        </div>
      )}
    </SettingsPageFrame>
  );
}
