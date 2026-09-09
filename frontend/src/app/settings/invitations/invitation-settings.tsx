"use client";

import {
  CircleAlert,
  ClipboardCheck,
  Copy,
  KeyRound,
  Link2,
  LoaderCircle,
  Plus,
  TicketCheck,
  Trash2,
  UsersRound,
  X,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { mutation, request } from "../../../lib/api";
import { SettingsPageFrame } from "../settings-page-frame";

type InvitationStatus = "ACTIVE" | "REVOKED";
type InvitationFilter = "ALL" | InvitationStatus;

type Invitation = {
  id: string;
  maskedCode: string;
  status: InvitationStatus;
  useCount: number;
  createdAt: string;
  lastUsedAt: string | null;
};

type CreatedInvitation = {
  id: string;
  code: string;
  joinUrl: string;
  createdAt: string;
};

const dateTimeFormatter = new Intl.DateTimeFormat("ko-KR", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Asia/Seoul",
});

function formatDateTime(value: string) {
  return dateTimeFormatter.format(new Date(value));
}

export function InvitationSettings() {
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [filter, setFilter] = useState<InvitationFilter>("ALL");
  const [createdInvitation, setCreatedInvitation] = useState<CreatedInvitation | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<Invitation | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isCreating, setIsCreating] = useState(false);
  const [isRevoking, setIsRevoking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadInvitations = useCallback(async () => {
    const data = await request<{ items: Invitation[] }>("/api/v1/invitations?status=ALL");
    setInvitations(data.items);
  }, []);

  useEffect(() => {
    let active = true;
    request<{ items: Invitation[] }>("/api/v1/invitations?status=ALL")
      .then((data) => active && setInvitations(data.items))
      .catch((reason: Error) => active && setError(reason.message))
      .finally(() => active && setIsLoading(false));
    return () => { active = false; };
  }, []);

  const activeCount = invitations.filter((invitation) => invitation.status === "ACTIVE").length;
  const totalUseCount = invitations.reduce((sum, invitation) => sum + invitation.useCount, 0);
  const filteredInvitations = useMemo(
    () => filter === "ALL" ? invitations : invitations.filter((invitation) => invitation.status === filter),
    [filter, invitations],
  );

  async function createInvitation() {
    setIsCreating(true);
    setError(null);
    setNotice(null);
    try {
      const created = await mutation<CreatedInvitation>("/api/v1/invitations", "POST");
      setCreatedInvitation(created);
      setFilter("ALL");
      setNotice("새 초대 코드를 발급했습니다. 이 화면을 떠나기 전에 복사해 주세요.");
      await loadInvitations();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "초대 코드를 발급하지 못했습니다.");
    } finally {
      setIsCreating(false);
    }
  }

  async function copyText(value: string, label: string) {
    setError(null);
    try {
      if (!navigator.clipboard) throw new Error("clipboard unavailable");
      await navigator.clipboard.writeText(value);
      setNotice(`${label}를 복사했습니다.`);
    } catch {
      setError("자동 복사를 사용할 수 없습니다. 표시된 내용을 길게 눌러 직접 복사해 주세요.");
    }
  }

  async function revokeInvitation() {
    if (!revokeTarget) return;
    setIsRevoking(true);
    setError(null);
    setNotice(null);
    try {
      await mutation(`/api/v1/invitations/${revokeTarget.id}`, "DELETE");
      if (createdInvitation?.id === revokeTarget.id) setCreatedInvitation(null);
      setNotice(`${revokeTarget.maskedCode} 초대 코드를 취소했습니다.`);
      setRevokeTarget(null);
      await loadInvitations();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "초대 코드를 취소하지 못했습니다.");
    } finally {
      setIsRevoking(false);
    }
  }

  return (
    <SettingsPageFrame
      badge={<span className="page-badge"><UsersRound size={16} /> 모든 참여자</span>}
      description="공유 장부에 참여할 초대 코드를 발급하고 안전하게 공유하세요."
      eyebrow="INVITATION SETTINGS"
      showBackLink
      title="초대 코드"
    >
      {error && <div className="message-banner message-banner--error" role="alert">{error}</div>}
      {notice && (
        <button className="message-banner message-banner--notice" onClick={() => setNotice(null)} type="button">
          {notice}<X size={16} />
        </button>
      )}

      {createdInvitation && (
        <section aria-labelledby="issued-invitation-title" className="invitation-issued-panel">
          <div className="invitation-issued-heading">
            <span><ClipboardCheck size={22} /></span>
            <div>
              <p className="eyebrow">COPY BEFORE LEAVING</p>
              <h2 id="issued-invitation-title">새 초대 코드가 발급됐어요</h2>
            </div>
          </div>
          <p className="invitation-issued-warning">보안을 위해 원문은 지금 한 번만 확인할 수 있습니다. 화면을 떠나기 전에 코드나 링크를 복사하세요.</p>
          <div className="invitation-copy-grid">
            <div>
              <span>초대 코드</span>
              <strong>{createdInvitation.code}</strong>
              <button onClick={() => void copyText(createdInvitation.code, "초대 코드")} type="button"><Copy size={16} /> 초대 코드 복사</button>
            </div>
            <div>
              <span>가입 링크</span>
              <strong>{createdInvitation.joinUrl}</strong>
              <button onClick={() => void copyText(createdInvitation.joinUrl, "가입 링크")} type="button"><Link2 size={16} /> 가입 링크 복사</button>
            </div>
          </div>
          <div className="invitation-issued-actions"><button className="secondary-button" onClick={() => setCreatedInvitation(null)} type="button">복사를 마쳤어요</button></div>
        </section>
      )}

      {isLoading ? (
        <section className="page-loading"><LoaderCircle className="spin" size={28} /><strong>초대 코드를 불러오고 있어요</strong></section>
      ) : (
        <section aria-labelledby="invitation-list-title" className="invitation-settings-panel">
          <div className="invitation-settings-toolbar">
            <div>
              <p className="eyebrow">INVITATION LIST</p>
              <h2 id="invitation-list-title">발급한 초대 코드</h2>
              <p>모든 참여자가 코드를 발급하거나 사용 가능한 코드를 취소할 수 있습니다.</p>
            </div>
            <button className="dark-button" disabled={isCreating || createdInvitation !== null} onClick={() => void createInvitation()} title={createdInvitation ? "현재 발급한 코드를 복사한 뒤 닫아 주세요." : undefined} type="button">
              {isCreating ? <LoaderCircle className="spin" size={17} /> : <Plus size={17} />}
              새 초대 코드 발급
            </button>
          </div>

          <div aria-label="초대 코드 요약" className="invitation-summary">
            <span><TicketCheck size={17} /><strong>{activeCount}</strong>개 사용 가능</span>
            <span><UsersRound size={17} /><strong>{totalUseCount}</strong>회 가입</span>
            <span><KeyRound size={17} />만료일 없음</span>
          </div>

          <div aria-label="초대 코드 상태 필터" className="invitation-filter" role="group">
            {(["ALL", "ACTIVE", "REVOKED"] as const).map((value) => (
              <button aria-pressed={filter === value} className={filter === value ? "is-active" : undefined} key={value} onClick={() => setFilter(value)} type="button">
                {value === "ALL" ? "전체" : value === "ACTIVE" ? "사용 가능" : "취소됨"}
              </button>
            ))}
          </div>

          {filteredInvitations.length === 0 ? (
            <div className="invitation-settings-empty"><TicketCheck size={25} /><strong>표시할 초대 코드가 없습니다.</strong><span>새 코드를 발급해 참여자에게 공유해 보세요.</span></div>
          ) : (
            <ul className="invitation-settings-list">
              {filteredInvitations.map((invitation) => (
                <li key={invitation.id}>
                  <span className={`invitation-status-icon invitation-status-icon--${invitation.status.toLowerCase()}`}><KeyRound size={19} /></span>
                  <span className="invitation-settings-code">
                    <span><strong>{invitation.maskedCode}</strong><small className={`invitation-status-chip invitation-status-chip--${invitation.status.toLowerCase()}`}>{invitation.status === "ACTIVE" ? "사용 가능" : "취소됨"}</small></span>
                    <span>{formatDateTime(invitation.createdAt)} 발급 · {invitation.useCount}회 사용</span>
                    <span>{invitation.lastUsedAt ? `마지막 사용 ${formatDateTime(invitation.lastUsedAt)}` : "아직 사용되지 않음"}</span>
                  </span>
                  {invitation.status === "ACTIVE" && (
                    <button aria-label={`${invitation.maskedCode} 초대 코드 취소`} className="invitation-revoke-button" onClick={() => setRevokeTarget(invitation)} type="button"><Trash2 size={16} /> 코드 취소</button>
                  )}
                </li>
              ))}
            </ul>
          )}

          <div className="invitation-security-note"><CircleAlert size={18} /><p><strong>초대 코드는 필요한 사람에게만 공유하세요.</strong><span>목록에서는 보안을 위해 마스킹된 코드만 확인할 수 있습니다. 공유를 중단하려면 사용 가능한 코드를 취소하세요.</span></p></div>
        </section>
      )}

      {revokeTarget && (
        <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !isRevoking) setRevokeTarget(null); }} role="presentation">
          <section aria-labelledby="invitation-revoke-title" aria-modal="true" className="invitation-revoke-dialog" role="dialog">
            <span className="invitation-revoke-icon"><CircleAlert size={26} /></span>
            <p className="eyebrow">REVOKE INVITATION</p>
            <h2 id="invitation-revoke-title">{revokeTarget.maskedCode} 코드를 취소할까요?</h2>
            <p>취소하면 이 코드로는 더 이상 가입할 수 없습니다. 이미 참여한 사용자는 영향을 받지 않으며, 취소한 코드는 다시 활성화할 수 없습니다.</p>
            <div>
              <button className="secondary-button" disabled={isRevoking} onClick={() => setRevokeTarget(null)} type="button">닫기</button>
              <button className="dark-button invitation-revoke-confirm" disabled={isRevoking} onClick={() => void revokeInvitation()} type="button">{isRevoking && <LoaderCircle className="spin" size={16} />}코드 취소</button>
            </div>
          </section>
        </div>
      )}
    </SettingsPageFrame>
  );
}
