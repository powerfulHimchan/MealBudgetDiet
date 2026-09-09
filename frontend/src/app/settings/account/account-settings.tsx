"use client";

import { Camera, LoaderCircle, ShieldCheck, Trash2, UserRound, X } from "lucide-react";
import Image from "next/image";
import { ChangeEvent, useEffect, useState } from "react";
import { multipartMutation, mutation, request } from "../../../lib/api";
import { SettingsPageFrame } from "../settings-page-frame";

type CurrentUser = {
  id: string;
  email: string;
  displayName: string;
  role: "ADMIN" | "MEMBER";
  profileImageUrl: string | null;
};
type UploadedImage = { id: string; contentUrl: string };
type ProfileImageResponse = { profileImageUrl: string };

export function AccountSettings() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    request<CurrentUser>("/api/v1/auth/me")
      .then((current) => active && setUser(current))
      .catch((reason: Error) => active && setError(reason.message))
      .finally(() => active && setIsLoading(false));
    return () => { active = false; };
  }, []);

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

    setIsSaving(true);
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
      setIsSaving(false);
    }
  }

  async function deleteProfileImage() {
    if (!user?.profileImageUrl) return;
    setIsSaving(true);
    setError(null);
    setNotice(null);
    try {
      await mutation("/api/v1/account/profile-image", "DELETE");
      setUser({ ...user, profileImageUrl: null });
      setNotice("프로필 사진을 삭제했습니다.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "프로필 사진을 삭제하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <SettingsPageFrame
      badge={user && <span className="page-badge"><ShieldCheck size={16} /> {user.role === "ADMIN" ? "관리자" : "멤버"}</span>}
      description="나를 나타내는 프로필 사진과 계정 정보를 관리하세요."
      eyebrow="ACCOUNT SETTINGS"
      showBackLink
      title="계정 설정"
    >
      {error && <div className="message-banner message-banner--error" role="alert">{error}</div>}
      {notice && <button className="message-banner message-banner--notice" onClick={() => setNotice(null)} type="button">{notice}<X size={16} /></button>}

      {isLoading || !user ? (
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
                <strong>{user.displayName}</strong>
                <span>{user.email}</span>
                <p>JPEG, PNG, WebP · 최대 5MB<br />표시용 WebP로 안전하게 변환됩니다.</p>
              </div>
            </div>
            <div className="profile-actions">
              <label className="dark-button">
                {isSaving ? <LoaderCircle className="spin" size={16} /> : <Camera size={16} />}
                {user.profileImageUrl ? "사진 변경" : "사진 등록"}
                <input accept="image/jpeg,image/png,image/webp" disabled={isSaving} onChange={selectProfileImage} type="file" />
              </label>
              {user.profileImageUrl && (
                <button className="secondary-button danger-button" disabled={isSaving} onClick={() => void deleteProfileImage()} type="button"><Trash2 size={16} />사진 삭제</button>
              )}
            </div>
          </section>

          <section className="account-next-panel">
            <p className="eyebrow">NEXT ACCOUNT FEATURES</p>
            <h2>로그인·탈퇴 관리</h2>
            <p>비밀번호 변경, 로그아웃, 회원 탈퇴 기능은 영속 로그인 작업과 함께 다음 단계에서 연결합니다.</p>
          </section>
        </div>
      )}
    </SettingsPageFrame>
  );
}
