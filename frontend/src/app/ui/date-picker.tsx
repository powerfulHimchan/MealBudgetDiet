"use client";

import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";

type CalendarPickerProps = {
  value: string;
  onChange: (value: string) => void;
  ariaLabel: string;
  mode?: "date" | "month";
  disabled?: boolean;
  placeholder?: string;
};

const weekdays = ["일", "월", "화", "수", "목", "금", "토"];

function pad(value: number) {
  return String(value).padStart(2, "0");
}

function initialView(value: string) {
  const match = value.match(/^(\d{4})-(\d{2})/);
  if (match) return { year: Number(match[1]), month: Number(match[2]) - 1 };
  const now = new Date();
  return { year: now.getFullYear(), month: now.getMonth() };
}

function displayValue(value: string, mode: "date" | "month", placeholder: string) {
  if (!value) return placeholder;
  const [year, month, day] = value.split("-");
  return mode === "month" ? `${year}년 ${Number(month)}월` : `${year}년 ${Number(month)}월 ${Number(day)}일`;
}

export function CalendarPicker({
  value,
  onChange,
  ariaLabel,
  mode = "date",
  disabled = false,
  placeholder = mode === "date" ? "날짜 선택" : "월 선택",
}: CalendarPickerProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const initial = initialView(value);
  const [open, setOpen] = useState(false);
  const [viewYear, setViewYear] = useState(initial.year);
  const [viewMonth, setViewMonth] = useState(initial.month);

  useEffect(() => {
    function close(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("pointerdown", close);
    return () => document.removeEventListener("pointerdown", close);
  }, []);

  const days = useMemo(() => {
    const firstWeekday = new Date(Date.UTC(viewYear, viewMonth, 1)).getUTCDay();
    const count = new Date(Date.UTC(viewYear, viewMonth + 1, 0)).getUTCDate();
    return [
      ...Array.from({ length: firstWeekday }, () => null),
      ...Array.from({ length: count }, (_, index) => index + 1),
    ];
  }, [viewMonth, viewYear]);

  function moveMonth(direction: -1 | 1) {
    const date = new Date(Date.UTC(viewYear, viewMonth + direction, 1));
    setViewYear(date.getUTCFullYear());
    setViewMonth(date.getUTCMonth());
  }

  return (
    <div className="calendar-picker" ref={rootRef}>
      <button
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label={ariaLabel}
        className="calendar-picker__trigger"
        disabled={disabled}
        onClick={() => {
          if (!open) {
            const next = initialView(value);
            setViewYear(next.year);
            setViewMonth(next.month);
          }
          setOpen((current) => !current);
        }}
        onKeyDown={(event) => {
          if (event.key === "Escape") setOpen(false);
        }}
        type="button"
      >
        <span className={value ? undefined : "is-placeholder"}>{displayValue(value, mode, placeholder)}</span>
        <CalendarDays size={17} />
      </button>
      {open && (
        <section aria-label={ariaLabel} className="calendar-picker__popover" role="dialog">
          <header>
            <button aria-label={mode === "date" ? "이전 달" : "이전 해"} onClick={() => mode === "date" ? moveMonth(-1) : setViewYear((year) => year - 1)} type="button"><ChevronLeft size={18} /></button>
            <strong>{mode === "date" ? `${viewYear}년 ${viewMonth + 1}월` : `${viewYear}년`}</strong>
            <button aria-label={mode === "date" ? "다음 달" : "다음 해"} onClick={() => mode === "date" ? moveMonth(1) : setViewYear((year) => year + 1)} type="button"><ChevronRight size={18} /></button>
          </header>
          {mode === "date" ? (
            <>
              <div className="calendar-picker__weekdays">
                {weekdays.map((weekday) => <span key={weekday}>{weekday}</span>)}
              </div>
              <div className="calendar-picker__days">
                {days.map((day, index) => day == null ? <span key={`empty-${index}`} /> : (
                  <button
                    aria-label={`${viewYear}년 ${viewMonth + 1}월 ${day}일`}
                    className={value === `${viewYear}-${pad(viewMonth + 1)}-${pad(day)}` ? "is-selected" : undefined}
                    key={day}
                    onClick={() => {
                      onChange(`${viewYear}-${pad(viewMonth + 1)}-${pad(day)}`);
                      setOpen(false);
                    }}
                    type="button"
                  >
                    {day}
                  </button>
                ))}
              </div>
            </>
          ) : (
            <div className="calendar-picker__months">
              {Array.from({ length: 12 }, (_, month) => (
                <button
                  className={value === `${viewYear}-${pad(month + 1)}` ? "is-selected" : undefined}
                  key={month}
                  onClick={() => {
                    onChange(`${viewYear}-${pad(month + 1)}`);
                    setOpen(false);
                  }}
                  type="button"
                >
                  {month + 1}월
                </button>
              ))}
            </div>
          )}
          {value && (
            <button className="calendar-picker__clear" onClick={() => { onChange(""); setOpen(false); }} type="button">
              선택 지우기
            </button>
          )}
        </section>
      )}
    </div>
  );
}
