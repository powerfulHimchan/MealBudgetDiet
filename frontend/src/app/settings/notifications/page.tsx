import { BellRing } from "lucide-react";
import { PushNotificationSettings } from "../push-notification-settings";
import { SettingsPageFrame } from "../settings-page-frame";

export default function NotificationSettingsPage() {
  return (
    <SettingsPageFrame
      badge={<span className="page-badge"><BellRing size={16} /> 기기별 설정</span>}
      description="이 기기에서 소비 속도에 따른 월 예산 초과 위험 알림을 받을지 선택하세요."
      eyebrow="NOTIFICATION SETTINGS"
      showBackLink
      title="푸시 알림"
    >
      <div className="settings-content"><PushNotificationSettings /></div>
    </SettingsPageFrame>
  );
}
