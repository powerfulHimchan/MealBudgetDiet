"use client";

import { LoaderCircle, ShieldCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { mutation, request } from "../../../lib/api";
import styles from "./ai-connection-check.module.css";

type AiStatus = { enabled: boolean; configured: boolean; ready: boolean; model: string };
type AiResult = { model: string; text: string; inputTokens: number; outputTokens: number };

export function AiConnectionCheck() {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [result, setResult] = useState<AiResult | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isTesting, setIsTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    request<AiStatus>("/api/v1/admin/ai/status", { signal: controller.signal })
      .then((nextStatus) => { if (!controller.signal.aborted) setStatus(nextStatus); })
      .catch((reason: Error) => { if (!controller.signal.aborted) setError(reason.message); })
      .finally(() => { if (!controller.signal.aborted) setIsLoading(false); });
    return () => controller.abort();
  }, []);

  async function testConnection() {
    if (!status?.ready || isTesting) return;
    setIsTesting(true);
    setError(null);
    setResult(null);
    try {
      setResult(await mutation<AiResult>("/api/v1/admin/ai/connection-test", "POST"));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "연결 테스트를 완료하지 못했습니다.");
    } finally {
      setIsTesting(false);
    }
  }

  return (
    <section className={`setting-panel ${styles.panel}`} aria-label="OpenAI 연결 상태">
      <div className="panel-heading">
        <span className="panel-icon"><ShieldCheck size={21} /></span>
        <div><p className="eyebrow">SERVICE ADMIN</p><h2>OpenAI 연결</h2></div>
      </div>
      <p className="setting-description">서비스 관리자만 확인할 수 있습니다. 테스트는 고정된 확인 문구를 보내며 소량의 API 사용료가 발생합니다.</p>
      {isLoading && <p role="status"><LoaderCircle className="spin" size={18} /> 설정을 확인하고 있어요.</p>}
      {error && <div className="message-banner message-banner--error" role="alert">{error}</div>}
      {status && (
        <>
          <dl className={styles.details}>
            <dt>AI 활성화</dt><dd>{status.enabled ? "활성화" : "비활성화"}</dd>
            <dt>API 키</dt><dd>{status.configured ? "등록됨" : "미등록"}</dd>
            <dt>설정 모델</dt><dd>{status.model}</dd>
          </dl>
          {!status.ready && <p className="setting-description">서버에서 API 키 등록과 AI 활성화 설정을 완료해 주세요.</p>}
          <button className="dark-button" disabled={!status.ready || isTesting} onClick={() => void testConnection()} type="button">
            {isTesting && <LoaderCircle className="spin" size={16} />}{isTesting ? "응답 확인 중" : "연결 테스트"}
          </button>
          <p className="setting-description">등록 상태만으로 키의 유효성이나 잔액을 확인할 수는 없습니다. 연결 테스트 결과를 확인하세요.</p>
        </>
      )}
      {result && (
        <section className={styles.result} role="status" aria-label="연결 테스트 결과">
          <h3>OpenAI 호출 성공</h3>
          <p>{result.text}</p>
          <dl className={styles.details}>
            <dt>응답 모델</dt><dd>{result.model}</dd>
            <dt>입력 토큰</dt><dd>{result.inputTokens}</dd>
            <dt>출력 토큰</dt><dd>{result.outputTokens}</dd>
          </dl>
        </section>
      )}
    </section>
  );
}
