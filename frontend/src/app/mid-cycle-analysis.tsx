"use client";

import { LoaderCircle, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";
import { mutation, request } from "../lib/api";
import { Button } from "./ui/button";
import styles from "./mid-cycle-analysis.module.css";

export type MidCycleAnalysis = {
  eligible: boolean;
  status: "UNAVAILABLE" | "NO_EXPENSES" | "NOT_ANALYZED" | "PROCESSING" | "COMPLETED";
  text: string | null;
  midpoint: string;
  through: string | null;
  analyzedAt: string | null;
};

export function MidCycleAnalysisView({ period, initial }: {
  period: { from: string; to: string };
  initial: MidCycleAnalysis;
}) {
  const [analysis, setAnalysis] = useState(initial);
  const [busy, setBusy] = useState(initial.eligible && !initial.text);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const { from, to } = period;

  useEffect(() => {
    if (!initial.eligible || initial.status === "COMPLETED") return;
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let checks = 0;
    const endpoint = "/api/v1/dashboard/mid-cycle-analysis";

    async function load(generate: boolean) {
      try {
        const result = generate
          ? await mutation<MidCycleAnalysis>(endpoint, "POST", { from, to })
          : await request<MidCycleAnalysis>(`${endpoint}?${new URLSearchParams({ from, to })}`);
        if (!active) return;
        setAnalysis(result);
        if (result.status === "PROCESSING" && ++checks < 30) {
          timer = setTimeout(() => void load(false), 2000);
          return;
        }
        setBusy(false);
        if (result.status === "PROCESSING" || result.status === "NOT_ANALYZED") {
          setError("중간평가를 아직 받지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
      } catch (reason) {
        if (!active) return;
        setBusy(false);
        setError(reason instanceof Error ? reason.message : "중간평가를 불러오지 못했어요.");
      }
    }

    void load(initial.status !== "PROCESSING" || attempt > 0);
    return () => { active = false; clearTimeout(timer); };
  }, [initial.eligible, initial.status, from, to, attempt]);

  if (analysis.status === "UNAVAILABLE") return null;

  return (
    <section className={styles.panel} aria-label="소비 중간평가" aria-busy={busy}>
      <h2 className={styles.heading}><Sparkles size={15} aria-hidden="true" />소비 중간평가</h2>
      {analysis.text ? <>
        <p className={styles.summary} aria-label="저장된 중간평가">{analysis.text}</p>
        <p className={styles.caption}>{analysis.through}까지의 소비 기준 · 주기당 한 번 분석</p>
      </> : analysis.status === "NO_EXPENSES" ? (
        <p className={styles.caption}>등록된 소비 기록이 없어 중간평가를 할 수 없습니다.</p>
      ) : busy ? (
        <p className={styles.loading} role="status"><LoaderCircle className="spin" size={14} />현재까지의 소비를 분석하고 있어요.</p>
      ) : null}
      {error && <div className={styles.error}>
        <p role="alert">{error}</p>
        <Button variant="outline" onClick={() => { setBusy(true); setError(null); setAttempt((value) => value + 1); }} disabled={busy}>다시 시도</Button>
      </div>}
    </section>
  );
}
