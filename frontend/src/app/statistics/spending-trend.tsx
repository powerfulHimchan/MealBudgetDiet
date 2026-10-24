"use client";

import { LoaderCircle } from "lucide-react";
import { useCallback, useId, useMemo, useState, useSyncExternalStore } from "react";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, type TooltipContentProps } from "recharts";
import { aggregateSpendingTrend, automaticSpendingTrendUnit, spendingTrendRangeLabel, spendingTrendTicks, type SpendingTrendPeriod, type SpendingTrendPoint, type SpendingTrendUnit } from "../../lib/spending-trend";
import { PresetChip } from "../ui/preset-chip";
import styles from "./spending-trend.module.css";

const labels = { daily: "일별", weekly: "주별", monthly: "월별" };
const units: SpendingTrendUnit[] = ["daily", "weekly", "monthly"];
const won = new Intl.NumberFormat("ko-KR");
const compactWon = new Intl.NumberFormat("ko-KR", { notation: "compact", maximumFractionDigits: 1 });

function subscribePointer(listener: () => void) {
  const media = window.matchMedia("(pointer: coarse)");
  media.addEventListener("change", listener);
  return () => media.removeEventListener("change", listener);
}

function coarsePointer() {
  return window.matchMedia("(pointer: coarse)").matches;
}

function serverPointer() {
  return false;
}

function SpendingTrendTooltip({ active, payload }: TooltipContentProps) {
  const point = payload[0]?.payload as SpendingTrendPoint | undefined;
  if (!active || !point) return null;
  return <div className={styles.tooltip} role="tooltip">
    <p>{spendingTrendRangeLabel(point)}</p>
    <strong>지출 합계 {won.format(point.amount)}원</strong>
  </div>;
}

export function SpendingTrend({ daily, period, weekStartDay, isLoading }: {
  daily: Array<{ date: string; amount: number }>;
  period: SpendingTrendPeriod;
  weekStartDay: number;
  isLoading: boolean;
}) {
  const [unit, setUnit] = useState<SpendingTrendUnit>(() => automaticSpendingTrendUnit(period));
  const [tickLimit, setTickLimit] = useState(4);
  const touchTooltip = useSyncExternalStore(subscribePointer, coarsePointer, serverPointer);
  const id = useId();
  const descriptionId = `${id}-description`;
  const fillId = `${id}-fill`;
  const points = useMemo(() => aggregateSpendingTrend(daily, period, unit, weekStartDay), [daily, period, unit, weekStartDay]);
  const ticks = useMemo(() => spendingTrendTicks(points, tickLimit), [points, tickLimit]);
  const resize = useCallback((width: number) => setTickLimit(width < 480 ? 4 : 6), []);
  const crossesYears = period.from.slice(0, 4) !== period.to.slice(0, 4);

  return <section className="chart-panel">
    <div className="panel-heading">
      <div><p className="eyebrow">SPENDING TREND</p><h2>{labels[unit]} 지출 추이</h2></div>
      {isLoading && <LoaderCircle className="spin" aria-label="지출 추이 불러오는 중" size={20} />}
    </div>
    <div className={styles.units} role="group" aria-label="지출 추이 표시 단위">
      {units.map((value) => <PresetChip className={styles.unit} disabled={isLoading} key={value} selected={unit === value} onClick={() => setUnit(value)}>{labels[value]}</PresetChip>)}
    </div>
    <p className="sr-only" id={descriptionId}>{period.from} ~ {period.to}, {points.length}개 {labels[unit]} 구간의 지출 합계.</p>
    <div className="chart-wrap" role="img" aria-label={`${labels[unit]} 지출 추이 차트`} aria-describedby={descriptionId}>
      {daily.length === 0 ? <div className="compact-empty">선택한 기간에 지출이 없습니다.</div> : (
        <ResponsiveContainer width="100%" height="100%" onResize={resize}>
          <AreaChart key={unit} accessibilityLayer={false} data={points} margin={{ top: 8, right: 8, left: -12, bottom: 0 }}>
            <defs><linearGradient id={fillId} x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="var(--blue)" stopOpacity={0.4} /><stop offset="100%" stopColor="var(--blue)" stopOpacity={0.02} /></linearGradient></defs>
            <CartesianGrid stroke="var(--line)" strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="from" ticks={ticks} interval="preserveStartEnd" tickFormatter={(value: string) => unit === "monthly" ? crossesYears ? value.slice(2, 7) : `${Number(value.slice(5, 7))}월` : crossesYears ? value.slice(2) : value.slice(5)} tick={{ fontSize: 11, fill: "var(--mute)" }} axisLine={false} tickLine={false} minTickGap={16} />
            <YAxis tickFormatter={(value: number) => compactWon.format(value)} tick={{ fontSize: 11, fill: "var(--mute)" }} axisLine={false} tickLine={false} />
            <Tooltip content={SpendingTrendTooltip} trigger={touchTooltip ? "click" : "hover"} isAnimationActive={false} />
            <Area isAnimationActive={false} type="monotone" dataKey="amount" stroke="var(--blue)" strokeWidth={2} fill={`url(#${fillId})`} dot={points.length === 1 ? { r: 4, fill: "var(--blue)" } : false} />
          </AreaChart>
        </ResponsiveContainer>
      )}
    </div>
  </section>;
}
