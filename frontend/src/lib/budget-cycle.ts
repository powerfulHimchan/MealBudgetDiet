export type BudgetCycleRange = { yearMonth: string; from: string; to: string };
export type BudgetCycleUnit = "MONTHLY" | "WEEKLY";

function parseYearMonth(yearMonth: string) {
  const [year, month] = yearMonth.split("-").map(Number);
  return { year, month };
}

function yearMonthText(date: Date) {
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
}

function dateText(date: Date) {
  return `${yearMonthText(date)}-${String(date.getUTCDate()).padStart(2, "0")}`;
}

export function addMonths(yearMonth: string, amount: number) {
  const { year, month } = parseYearMonth(yearMonth);
  return yearMonthText(new Date(Date.UTC(year, month - 1 + amount, 1)));
}

export function budgetCycleStarting(yearMonth: string, startDay: number): BudgetCycleRange {
  const { year, month } = parseYearMonth(yearMonth);
  const nextYearMonth = addMonths(yearMonth, 1);
  const next = parseYearMonth(nextYearMonth);
  const currentLastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const nextLastDay = new Date(Date.UTC(next.year, next.month, 0)).getUTCDate();
  const from = new Date(Date.UTC(year, month - 1, Math.min(startDay, currentLastDay)));
  const nextFrom = new Date(Date.UTC(next.year, next.month - 1, Math.min(startDay, nextLastDay)));
  const to = new Date(nextFrom.getTime() - 24 * 60 * 60 * 1000);
  return { yearMonth, from: dateText(from), to: dateText(to) };
}

export function budgetCycleContaining(date: string, startDay: number): BudgetCycleRange {
  const candidate = date.slice(0, 7);
  const cycle = budgetCycleStarting(candidate, startDay);
  return date < cycle.from ? budgetCycleStarting(addMonths(candidate, -1), startDay) : cycle;
}

export function weeklyCycleContaining(date: string, startWeekDay: number): BudgetCycleRange {
  const day = new Date(`${date}T00:00:00Z`);
  const weekday = day.getUTCDay() || 7;
  day.setUTCDate(day.getUTCDate() - (weekday - startWeekDay + 7) % 7);
  const from = dateText(day);
  day.setUTCDate(day.getUTCDate() + 6);
  return { yearMonth: from.slice(0, 7), from, to: dateText(day) };
}

export function cycleForDate(date: string, unit: BudgetCycleUnit, startDay: number, weekStartDay: number) {
  return unit === "WEEKLY" ? weeklyCycleContaining(date, weekStartDay) : budgetCycleContaining(date, startDay);
}
