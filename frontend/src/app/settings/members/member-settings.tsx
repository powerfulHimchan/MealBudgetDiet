"use client";

import {
  CircleAlert,
  LoaderCircle,
  ShieldCheck,
  ShieldMinus,
  ShieldPlus,
  UserRound,
  Users,
  X,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import { mutation, request } from "../../../lib/api";
import { SettingsPageFrame } from "../settings-page-frame";

type MemberRole = "ADMIN" | "MEMBER";
type Member = { id: string; displayName: string; role: MemberRole; joinedAt: string; profileImageUrl: string | null };
type Ledger = { currentUserRole: MemberRole; memberCount: number; name: string };
type CurrentUser = { id: string; displayName: string };
type RoleChange = { member: Member; nextRole: MemberRole };

const joinedDate = new Intl.DateTimeFormat("ko-KR", {
  dateStyle: "medium",
  timeZone: "Asia/Seoul",
});

export function MemberSettings() {
  const [members, setMembers] = useState<Member[]>([]);
  const [ledger, setLedger] = useState<Ledger | null>(null);
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [roleChange, setRoleChange] = useState<RoleChange | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    Promise.all([
      request<{ items: Member[] }>("/api/v1/members"),
      request<Ledger>("/api/v1/ledger"),
      request<CurrentUser>("/api/v1/auth/me"),
    ])
      .then(([memberData, nextLedger, user]) => {
        if (!active) return;
        setMembers(memberData.items);
        setLedger(nextLedger);
        setCurrentUser(user);
      })
      .catch((reason: Error) => active && setError(reason.message))
      .finally(() => active && setIsLoading(false));
    return () => { active = false; };
  }, []);

  const adminCount = useMemo(
    () => members.filter((member) => member.role === "ADMIN").length,
    [members],
  );
  const isAdmin = ledger?.currentUserRole === "ADMIN";

  async function changeRole() {
    if (!roleChange || !isAdmin) return;
    setIsSaving(true);
    setError(null);
    setNotice(null);
    try {
      const updated = await mutation<Member>(`/api/v1/members/${roleChange.member.id}/role`, "PATCH", {
        role: roleChange.nextRole,
      });
      setMembers((current) => current.map((member) => member.id === updated.id ? updated : member));
      if (updated.id === currentUser?.id) {
        const nextLedger = await request<Ledger>("/api/v1/ledger");
        setLedger(nextLedger);
      }
      setNotice(
        roleChange.nextRole === "ADMIN"
          ? `${updated.displayName} 님을 관리자로 지정했습니다.`
          : `${updated.displayName} 님의 관리자 권한을 해제했습니다.`,
      );
      setRoleChange(null);
    } catch (reason) {
      setRoleChange(null);
      setError(reason instanceof Error ? reason.message : "참여자 권한을 변경하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <SettingsPageFrame
      badge={ledger && <span className="page-badge"><ShieldCheck size={16} /> {isAdmin ? "관리자" : "멤버"}</span>}
      description="공유 장부를 함께 사용하는 사람을 확인하고 관리자 권한을 설정하세요."
      eyebrow="MEMBER SETTINGS"
      showBackLink
      title="참여자 관리"
    >
      {error && <div className="message-banner message-banner--error" role="alert">{error}</div>}
      {notice && (
        <button className="message-banner message-banner--notice" onClick={() => setNotice(null)} type="button">
          {notice}<X size={16} />
        </button>
      )}

      {isLoading || !ledger ? (
        <section className="page-loading"><LoaderCircle className="spin" size={28} /><strong>참여자를 불러오고 있어요</strong></section>
      ) : (
        <section aria-labelledby="member-list-title" className="member-settings-panel">
          <div className="member-settings-toolbar">
            <div>
              <p className="eyebrow">SHARED LEDGER MEMBERS</p>
              <h2 id="member-list-title">{ledger.name} 참여자</h2>
              <p>관리자는 예산·카테고리·참여자 권한을 변경할 수 있습니다.</p>
            </div>
            <div className="member-summary" aria-label="참여자 요약">
              <span><Users size={17} /><strong>{members.length}</strong>명</span>
              <span><ShieldCheck size={17} /><strong>{adminCount}</strong>명 관리자</span>
            </div>
          </div>

          {!isAdmin && <p className="member-permission-note"><ShieldCheck size={17} /> 참여자 조회는 가능하지만 관리자 권한 변경은 관리자만 할 수 있습니다.</p>}

          <ul className="member-settings-list">
            {members.map((member) => {
              const isMe = member.id === currentUser?.id;
              const isLastAdmin = member.role === "ADMIN" && adminCount === 1;
              const nextRole = member.role === "ADMIN" ? "MEMBER" : "ADMIN";
              return (
                <li key={member.id}>
                  <span className="member-avatar">
                    {member.profileImageUrl ? <Image alt="" fill sizes="42px" src={member.profileImageUrl} unoptimized /> : <UserRound size={21} />}
                  </span>
                  <span className="member-settings-name">
                    <span><strong>{member.displayName}</strong>{isMe && <small>나</small>}</span>
                    <span>{joinedDate.format(new Date(member.joinedAt))} 참여</span>
                  </span>
                  <span className={`member-role-chip member-role-chip--${member.role.toLowerCase()}`}>{member.role === "ADMIN" ? "관리자" : "일반 참여자"}</span>
                  {isAdmin && (
                    <button
                      className="member-role-button"
                      disabled={isLastAdmin}
                      onClick={() => setRoleChange({ member, nextRole })}
                      title={isLastAdmin ? "마지막 관리자는 권한을 해제할 수 없습니다." : undefined}
                      type="button"
                    >
                      {nextRole === "ADMIN" ? <ShieldPlus size={17} /> : <ShieldMinus size={17} />}
                      {nextRole === "ADMIN" ? "관리자로 지정" : "일반 참여자로 변경"}
                    </button>
                  )}
                </li>
              );
            })}
          </ul>

          <div className="member-safety-note"><CircleAlert size={18} /><p><strong>마지막 관리자는 유지해야 합니다.</strong><span>관리자를 여러 명 지정할 수 있으며, 다른 관리자가 있어야 기존 관리자의 권한을 해제할 수 있습니다.</span></p></div>
        </section>
      )}

      {roleChange && (
        <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !isSaving) setRoleChange(null); }} role="presentation">
          <section aria-labelledby="member-role-dialog-title" aria-modal="true" className="member-role-dialog" role="dialog">
            <span className="member-role-dialog-icon">{roleChange.nextRole === "ADMIN" ? <ShieldPlus size={27} /> : <ShieldMinus size={27} />}</span>
            <p className="eyebrow">CHANGE MEMBER ROLE</p>
            <h2 id="member-role-dialog-title">
              {roleChange.member.displayName} 님을 {roleChange.nextRole === "ADMIN" ? "관리자로 지정할까요?" : "일반 참여자로 변경할까요?"}
            </h2>
            <p>
              {roleChange.nextRole === "ADMIN"
                ? "관리자는 예산과 카테고리를 변경하고 다른 참여자의 관리자 권한도 설정할 수 있습니다."
                : roleChange.member.id === currentUser?.id
                  ? "본인의 관리자 권한을 해제하면 이후 이 화면에서 권한을 변경할 수 없습니다."
                  : "변경 후 해당 참여자는 식비 조회·등록·편집은 계속할 수 있지만 관리 기능은 사용할 수 없습니다."}
            </p>
            <div>
              <button className="secondary-button" disabled={isSaving} onClick={() => setRoleChange(null)} type="button">취소</button>
              <button className="dark-button" disabled={isSaving} onClick={() => void changeRole()} type="button">
                {isSaving && <LoaderCircle className="spin" size={16} />}권한 변경
              </button>
            </div>
          </section>
        </div>
      )}
    </SettingsPageFrame>
  );
}
