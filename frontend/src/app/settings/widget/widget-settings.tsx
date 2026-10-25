"use client";

import { useState } from "react";
import { mutation } from "@/lib/api";
import { Button } from "../../ui/button";
import styles from "./widget.module.css";

export function WidgetSettings({ challenge }: { challenge?: string }) {
  const [busy, setBusy] = useState(false);
  const [connected, setConnected] = useState(false);
  const [message, setMessage] = useState("");
  const valid = challenge !== undefined && /^[0-9a-f]{64}$/.test(challenge);
  async function connect() {
    setBusy(true); setMessage("");
    try {
      await mutation("/api/v1/widget/connections", "POST", { challenge });
      setConnected(true);
    } catch (error) { setMessage(error instanceof Error ? error.message : "연결하지 못했습니다."); }
    finally { setBusy(false); }
  }
  async function disconnect() {
    setBusy(true); setMessage("");
    try {
      await mutation("/api/v1/widget/connections", "DELETE");
      setConnected(false); setMessage("이 로그인에서 연결한 위젯을 모두 해제했습니다.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "해제하지 못했습니다."); }
    finally { setBusy(false); }
  }
  return <div className={styles.grid}>
    <section className={styles.card}>
      <h2>안드로이드 홈 화면에 추가하기</h2>
      <ol><li>Sikbi 앱을 최신 버전으로 업데이트하세요.</li><li>홈 화면의 빈 곳을 길게 누르고 위젯에서 Sikbi를 선택하세요.</li><li>위젯의 ‘연결’을 누르고 로그인한 장부를 연결하세요.</li></ol>
      <p>위젯에는 남은 예산과 오늘 지출만 표시됩니다. ‘기록’을 누르면 식비 입력 화면이 열립니다.</p>
      <p>자동 갱신과 새로고침을 지원합니다. 로그아웃하거나 연결이 만료되면 다시 연결하세요.</p>
      {!challenge && <Button href="intent://setup#Intent;scheme=sikbi-widget;package=app.sikbi;end">앱에서 위젯 연결</Button>}
    </section>
    <section className={`${styles.card} ${challenge ? styles.connectFirst : ""}`} aria-labelledby="widget-connect-title">
      <h2 id="widget-connect-title">{valid ? "현재 장부 연결" : "연결 관리"}</h2>
      {valid && !connected && <><p>이 로그인에서 사용하는 장부의 예산과 지출을 위젯에 표시합니다. 홈 화면에서 금액이 보입니다.</p>
        <Button disabled={busy} onClick={connect}>{busy ? "연결 중…" : "이 장부를 위젯에 연결"}</Button></>}
      {connected && <><p>장부 연결을 승인했습니다. 앱으로 돌아가 연결을 마무리하세요.</p>
        <Button href={`intent://connected?challenge=${challenge}#Intent;scheme=sikbi-widget;package=app.sikbi;end`}>앱으로 돌아가 완료</Button></>}
      {challenge && !valid && <p role="alert">연결 주소가 올바르지 않습니다. 위젯에서 연결을 다시 시작하세요.</p>}
      <p>연결 해제는 이 로그인에서 연결한 모든 위젯에 적용됩니다.</p>
      <Button disabled={busy} variant="outline" onClick={disconnect}>위젯 연결 해제</Button>
      {message && <p role="status">{message}</p>}
    </section>
  </div>;
}
