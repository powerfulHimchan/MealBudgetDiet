"use client";

import { LoaderCircle, Sparkles } from "lucide-react";
import { useState } from "react";
import { mutation } from "../../lib/api";
import { Button } from "../ui/button";
import styles from "./spending-analysis.module.css";

export type SpendingAnalysis = {
  eligible: boolean;
  status: "UNAVAILABLE" | "NO_EXPENSES" | "NOT_ANALYZED" | "PROCESSING" | "COMPLETED";
  text: string | null;
  analyzedAt: string | null;
};
type Period = { from: string; to: string };
export type AnalysisResponse = { period: Period; analysis: SpendingAnalysis };

export function SpendingAnalysisView({ period, analysis, disabled, onAnalyzed, onRefresh }: {
  period: Period;
  analysis?: SpendingAnalysis | null;
  disabled: boolean;
  onAnalyzed: (response: AnalysisResponse) => void;
  onRefresh: () => void;
}) {
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const noExpenses = analysis?.status === "NO_EXPENSES";
  if (!analysis || (!analysis.eligible && !analysis.text && !noExpenses)) return null;

  async function analyze() {
    if (!analysis?.eligible || isAnalyzing || disabled) return;
    setIsAnalyzing(true);
    setError(null);
    try {
      onAnalyzed(await mutation<AnalysisResponse>("/api/v1/statistics/analysis", "POST", period));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "소비 분석을 완료하지 못했습니다.");
    } finally {
      setIsAnalyzing(false);
    }
  }

  const processing = analysis.status === "PROCESSING";
  return (
    <section className={styles.panel} aria-label="소비 분석">
      <div className="panel-heading">
        <div className={styles.heading}><Sparkles size={20} /><h2>소비 분석</h2></div>
        {!analysis.text && !noExpenses && <Button variant="ink" disabled={disabled || isAnalyzing} onClick={processing ? onRefresh : () => void analyze()}>
          {isAnalyzing && <LoaderCircle className="spin" size={16} />}{isAnalyzing ? "분석 중" : processing ? "결과 확인" : "소비 분석"}
        </Button>}
      </div>
      {noExpenses ? <p className={styles.caption}>이 주기에는 등록된 소비 기록이 없어 분석할 수 없습니다.</p> : analysis.text ? <>
        <p className={styles.summary} aria-label="저장된 소비 분석">{analysis.text}</p>
        <p className={styles.caption}>{analysis.analyzedAt && `${new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", dateStyle: "medium", timeStyle: "short" }).format(new Date(analysis.analyzedAt))} 분석 · `}분석 시점의 통계를 기준으로 한 결과입니다.</p>
      </> : <>
        <p className={styles.caption} role={processing || isAnalyzing ? "status" : undefined}>
          {processing || isAnalyzing ? "이 주기의 소비를 분석하고 있어요. 잠시만 기다려 주세요." : "이 주기의 소비를 분석하여 정리합니다."}
        </p>
        <p className={styles.caption}>분석을 요청하면 금액과 카테고리별 집계 정보가 OpenAI에 전달됩니다.</p>
      </>}
      {error && <div className="message-banner message-banner--error" role="alert">{error}</div>}
    </section>
  );
}
