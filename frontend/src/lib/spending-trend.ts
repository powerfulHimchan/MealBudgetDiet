import { weeklyCycleContaining } from "./budget-cycle";

export type SpendingTrendUnit = "daily" | "weekly" | "monthly";
export type SpendingTrendPeriod = { from: string; to: string };
export type SpendingTrendPoint = SpendingTrendPeriod & { amount: number };

const dayMilliseconds = 24 * 60 * 60 * 1000;

function utcDate(value: string) {
  return new Date(`${value}T00:00:00Z`);
}

function dateText(date: Date) {
  return date.toISOString().slice(0, 10);
}

export function automaticSpendingTrendUnit(period: SpendingTrendPeriod): SpendingTrendUnit {
  const days = (utcDate(period.to).getTime() - utcDate(period.from).getTime()) / dayMilliseconds + 1;
  return days <= 31 ? "daily" : days <= 180 ? "weekly" : "monthly";
}

export function aggregateSpendingTrend(
  daily: ReadonlyArray<{ date: string; amount: number }>,
  period: SpendingTrendPeriod,
  unit: SpendingTrendUnit,
  weekStartDay = 1,
): SpendingTrendPoint[] {
  const points: SpendingTrendPoint[] = [];
  const byStart = new Map<string, SpendingTrendPoint>();
  let from = period.from;
  while (from <= period.to) {
    let to = from;
    if (unit === "weekly") to = weeklyCycleContaining(from, weekStartDay).to;
    if (unit === "monthly") {
      const monthEnd = utcDate(from);
      monthEnd.setUTCMonth(monthEnd.getUTCMonth() + 1, 0);
      to = dateText(monthEnd);
    }
    to = to > period.to ? period.to : to;
    const point = { from, to, amount: 0 };
    points.push(point);
    byStart.set(from, point);
    from = dateText(new Date(utcDate(to).getTime() + dayMilliseconds));
  }

  for (const item of daily) {
    if (item.date < period.from || item.date > period.to) continue;
    const bucketStart = unit === "weekly" ? weeklyCycleContaining(item.date, weekStartDay).from
      : unit === "monthly" ? `${item.date.slice(0, 7)}-01` : item.date;
    const point = byStart.get(bucketStart < period.from ? period.from : bucketStart);
    if (point) point.amount += item.amount;
  }
  return points;
}

export function spendingTrendTicks(points: ReadonlyArray<SpendingTrendPoint>, limit: number) {
  if (points.length <= limit) return points.map((point) => point.from);
  return Array.from({ length: limit }, (_, index) => points[Math.round(index * (points.length - 1) / (limit - 1))].from);
}

export function spendingTrendRangeLabel(point: SpendingTrendPeriod) {
  return point.from === point.to ? point.from : `${point.from} ~ ${point.to}`;
}
