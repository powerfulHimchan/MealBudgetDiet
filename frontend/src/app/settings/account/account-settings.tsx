"use client";

import {
  Camera,
  CircleAlert,
  KeyRound,
  LoaderCircle,
  LogOut,
  ShieldCheck,
  Trash2,
  UserRound,
  UserRoundX,
  X,
} from "lucide-react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from "react";
import { multipartMutation, mutation, request } from "../../../lib/api";
import { SettingsPageFrame } from "../settings-page-frame";

type MemberRole = "ADMIN" | "MEMBER";
type CurrentUser = {
  id: string;
  email: string;
  displayName: string;
  role: MemberRole;
  profileImageUrl: string | null;
};
type Ledger = { name: string; currentUserRole: MemberRole };
type Member = { id: string; role: MemberRole };
type UploadedImage = { id: string; contentUrl: string };
type ProfileImageResponse = { profileImageUrl: string };

export function AccountSettings() {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [ledger, setLedger] = useState<Ledger | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newPasswordConfirmation, setNewPasswordConfirmation] = useState("");
  const [withdrawalPassword, setWithdrawalPassword] = useState("");
  const [withdrawalConfirmation, setWithdrawalConfirmation] = useState("");
  const [isWithdrawalOpen, setIsWithdrawalOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSavingImage, setIsSavingImage] = useState(false);
  const [isChangingPassword, setIsChangingPassword] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [isWithdrawing, setIsWithdrawing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    Promise.all([
      request<CurrentUser>("/api/v1/auth/me"),
      request<Ledger>("/api/v1/ledger"),
      request<{ items: Member[] }>("/api/v1/members"),
    ])
      .then(([current, currentLedger, memberData]) => {
        if (!active) return;
        setUser(current);
        setLedger(currentLedger);
        setMembers(memberData.items);
      })
      .catch((reason: Error) => active && setError(reason.message))
      .finally(() => active && setIsLoading(false));
    return () => { active = false; };
  }, []);

  const isLastAdmin = useMemo(
    () => user?.role === "ADMIN" && members.filter((member) => member.role === "ADMIN").length === 1,
    [members, user?.role],
  );
  const expectedConfirmation = ledger ? `${ledger.name} 삭제` : "";
  const isBusy = isSavingImage || isChangingPassword || isLoggingOut || isWithdrawing;

  async function selectProfileImage(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !user) return;
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      setError("JPEG, PNG 또는 WebP 이미지만 등록할 수 있습니다.");
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setError("프로필 사진은 5MB 이하여야 합니다.");
      return;
    }

    setIsSavingImage(true);
    setError(null);
    setNotice(null);
    let uploaded: UploadedImage | null = null;
    try {
      const formData = new FormData();
      formData.append("file", file);
      uploaded = await multipartMutation<UploadedImage>("/api/v1/uploads/images?purpose=PROFILE", formData);
      const response = await mutation<ProfileImageResponse>("/api/v1/account/profile-image", "PUT", { imageId: uploaded.id });
      setUser({ ...user, profileImageUrl: response.profileImageUrl });
      setNotice(user.profileImageUrl ? "프로필 사진을 변경했습니다." : "프로필 사진을 등록했습니다.");
    } catch (reason) {
      if (uploaded) {
        void mutation(`/api/v1/uploads/images/${uploaded.id}`, "DELETE").catch(() => undefined);
      }
      setError(reason instanceof Error ? reason.message : "프로필 사진을 저장하지 못했습니다.");
    } finally {
      setIsSavingImage(false);
    }
  }

  async function deleteProfileImage() {
    if (!user?.profileImageUrl) return;
    setIsSavingImage(true);
    setError(null);
    setNotice(null);
    try {
      await mutation("/api/v1/account/profile-image", "DELETE");
      setUser({ ...user, profileImageUrl: null });
      setNotice("프로필 사진을 삭제했습니다.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "프로필 사진을 삭제하지 못했습니다.");
    } finally {
      setIsSavingImage(false);
    }
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    if (newPassword.length < 8 || newPassword.length > 72) {
      setError("새 비밀번호는 8자 이상 72자 이하로 입력해 주세요.");
      return;
    }
    if (newPassword !== newPasswordConfirmation) {
      setError("새 비밀번호 확인이 일치하지 않습니다.");
      return;
    }

    setIsChangingPassword(true);
    try {
      await mutation("/api/v1/account/password-change", "POST", { currentPassword, newPassword });
      router.replace("/");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "비밀번호를 변경하지 못했습니다.");
      setIsChangingPassword(false);
    }
  }

  async function logout() {
    setIsLoggingOut(true);
    setError(null);
    setNotice(null);
    try {
      await mutation("/api/v1/auth/logout", "POST");
      router.replace("/");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "로그아웃하지 못했습니다.");
      setIsLoggingOut(false);
    }
  }

  function openWithdrawal() {
    setError(null);
    setNotice(null);
    setWithdrawalPassword("");
    setWithdrawalConfirmation("");
    setIsWithdrawalOpen(true);
  }

  async function withdraw(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!withdrawalPassword || (isLastAdmin && withdrawalConfirmation !== expectedConfirmation)) return;
    setIsWithdrawing(true);
    setError(null);
    try {
      await mutation("/api/v1/account/withdrawal", "POST", {
        password: withdrawalPassword,
        confirmation: isLastAdmin ? withdrawalConfirmation : undefined,
      });
      router.replace("/");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "회원 탈퇴를 처리하지 못했습니다.");
      setIsWithdrawing(false);
      setIsWithdrawalOpen(false);
    }
  }

  return (
    <SettingsPageFrame
      badge={user && <span className="page-badge"><ShieldCheck size={16} /> {user.role === "ADMIN" ? "관리자" : "멤버"}</span>}
      description="프로필과 로그인 보안, 공유 장부 참여 상태를 관리하세요."
      eyebrow="ACCOUNT SETTINGS"
      showBackLink
      title="계정 설정"
    >
      {error && <div className="message-banner message-banner--error" role="alert">{error}</div>}
      {notice && <button className="message-banner message-banner--notice" onClick={() => setNotice(null)} type="button">{notice}<X size={16} /></button>}

      {isLoading || !user || !ledger ? (
        <section className="page-loading"><LoaderCircle className="spin" size={28} /><strong>계정 정보를 불러오고 있어요</strong></section>
      ) : (
        <div className="account-settings-grid">
          <section className="profile-settings-panel">
            <div className="panel-heading">
              <span className="panel-icon"><Camera size={21} /></span>
              <div><p className="eyebrow">PROFILE IMAGE</p><h2>프로필 사진</h2></div>
            </div>
            <div className="profile-editor">
              <span className="profile-preview">
                {user.profileImageUrl ? (
                  <Image alt={`${user.displayName} 프로필`} fill priority sizes="128px" src={user.profileImageUrl} unoptimized />
                ) : (
                  <strong>{user.displayName.trim().slice(0, 1) || <UserRound size={36} />}</strong>
                )}
              </span>
              <div>
                <span className="account-role-label">{user.role === "ADMIN" ? "공유 장부 관리자" : "공유 장부 참여자"}</span>
                <strong>{user.displayName}</strong>
                <span>{user.email}</span>
                <p>JPEG, PNG, WebP · 최대 5MB<br />표시용 WebP로 안전하게 변환됩니다.</p>
              </div>
            </div>
            <div className="profile-actions">
              <label className="dark-button">
                {isSavingImage ? <LoaderCircle className="spin" size={16} /> : <Camera size={16} />}
                {user.profileImageUrl ? "사진 변경" : "사진 등록"}
                <input accept="image/jpeg,image/png,image/webp" disabled={isBusy} onChange={selectProfileImage} type="file" />
              </label>
              {user.profileImageUrl && (
                <button className="secondary-button danger-button" disabled={isBusy} onClick={() => void deleteProfileImage()} type="button"><Trash2 size={16} />사진 삭제</button>
              )}
            </div>
          </section>

          <div className="account-settings-stack">
            <section className="account-security-panel">
              <div className="panel-heading">
                <span className="panel-icon"><KeyRound size={21} /></span>
                <div><p className="eyebrow">PASSWORD</p><h2>비밀번호 변경</h2></div>
              </div>
              <p className="account-panel-description">변경하면 보안을 위해 모든 기기에서 로그아웃됩니다.</p>
              <form className="account-password-form" onSubmit={(event) => void changePassword(event)}>
                <label>
                  <span>현재 비밀번호</span>
                  <input autoComplete="current-password" disabled={isBusy} maxLength={72} onChange={(event) => setCurrentPassword(event.target.value)} required type="password" value={currentPassword} />
                </label>
                <label>
                  <span>새 비밀번호 <small>8~72자</small></span>
                  <input autoComplete="new-password" disabled={isBusy} maxLength={72} minLength={8} onChange={(event) => setNewPassword(event.target.value)} required type="password" value={newPassword} />
                </label>
                <label>
                  <span>새 비밀번호 확인</span>
                  <input autoComplete="new-password" disabled={isBusy} maxLength={72} minLength={8} onChange={(event) => setNewPasswordConfirmation(event.target.value)} required type="password" value={newPasswordConfirmation} />
                </label>
                <button className="dark-button" disabled={isBusy || !currentPassword || !newPassword || !newPasswordConfirmation} type="submit">
                  {isChangingPassword && <LoaderCircle className="spin" size={16} />}비밀번호 변경
                </button>
              </form>
            </section>

            <section className="account-session-panel">
              <span className="account-session-icon"><LogOut size={21} /></span>
              <div><strong>현재 기기에서 로그아웃</strong><span>다른 기기의 로그인 상태는 유지됩니다.</span></div>
              <button className="secondary-button" disabled={isBusy} onClick={() => void logout()} type="button">
                {isLoggingOut && <LoaderCircle className="spin" size={16} />}로그아웃
              </button>
            </section>
          </div>

          <section className="account-danger-panel">
            <div>
              <span className="account-danger-icon"><UserRoundX size={22} /></span>
              <div>
                <p className="eyebrow">DANGER ZONE</p>
                <h2>{isLastAdmin ? "공유 장부 종료" : "회원 탈퇴"}</h2>
                <p>
                  {isLastAdmin
                    ? `현재 ${ledger.name}의 마지막 관리자입니다. 탈퇴하면 장부와 모든 참여자의 데이터가 삭제됩니다.`
                    : "탈퇴하면 계정 식별정보와 프로필 사진이 삭제됩니다. 공유 식비 기록은 작성자 연결을 끊고 유지됩니다."}
                </p>
              </div>
            </div>
            <button className="secondary-button account-withdrawal-button" disabled={isBusy} onClick={openWithdrawal} type="button">
              <Trash2 size={16} />{isLastAdmin ? "장부 종료" : "회원 탈퇴"}
            </button>
          </section>
        </div>
      )}

      {isWithdrawalOpen && ledger && (
        <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !isWithdrawing) setIsWithdrawalOpen(false); }} role="presentation">
          <section aria-labelledby="account-withdrawal-title" aria-modal="true" className="account-withdrawal-dialog" role="dialog">
            <span className="account-withdrawal-icon"><CircleAlert size={27} /></span>
            <p className="eyebrow">{isLastAdmin ? "DELETE SHARED LEDGER" : "WITHDRAW ACCOUNT"}</p>
            <h2 id="account-withdrawal-title">{isLastAdmin ? `${ledger.name} 장부를 종료할까요?` : "공유 장부에서 탈퇴할까요?"}</h2>
            <p>
              {isLastAdmin
                ? "모든 식비, 예산, 이미지와 참여자 계정이 함께 삭제되며 복구할 수 없습니다."
                : "계정 식별정보와 프로필 사진이 삭제되고 모든 기기에서 로그아웃됩니다. 공유 기록은 익명화되어 유지되며 이 작업은 되돌릴 수 없습니다."}
            </p>
            <form onSubmit={(event) => void withdraw(event)}>
              <label>
                <span>현재 비밀번호</span>
                <input autoComplete="current-password" disabled={isWithdrawing} maxLength={72} onChange={(event) => setWithdrawalPassword(event.target.value)} required type="password" value={withdrawalPassword} />
              </label>
              {isLastAdmin && (
                <label>
                  <span>확인을 위해 <strong>{expectedConfirmation}</strong> 입력</span>
                  <input autoComplete="off" disabled={isWithdrawing} onChange={(event) => setWithdrawalConfirmation(event.target.value)} required type="text" value={withdrawalConfirmation} />
                </label>
              )}
              <div>
                <button className="secondary-button" disabled={isWithdrawing} onClick={() => setIsWithdrawalOpen(false)} type="button">취소</button>
                <button className="dark-button account-withdrawal-confirm" disabled={isWithdrawing || !withdrawalPassword || (isLastAdmin && withdrawalConfirmation !== expectedConfirmation)} type="submit">
                  {isWithdrawing && <LoaderCircle className="spin" size={16} />}{isLastAdmin ? "장부와 계정 삭제" : "탈퇴하기"}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </SettingsPageFrame>
  );
}
