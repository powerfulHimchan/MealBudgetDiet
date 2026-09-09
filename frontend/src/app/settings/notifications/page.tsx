import { BellRing } from "lucide-react";
import { PushNotificationSettings, PushThresholdSettings } from "../push-notification-settings";
import { SettingsPageFrame } from "../settings-page-frame";

export default function NotificationSettingsPage() {
  return (
    <SettingsPageFrame
      badge={<span className="page-badge"><BellRing size={16} /> 알림 정책</span>}
      description="공유 장부의 알림 기준과 이 기기의 Push 수신 여부를 관리하세요."
      eyebrow="NOTIFICATION SETTINGS"
      showBackLink
      title="푸시 알림"
    >
      <div className="settings-content push-settings-grid">
        <PushThresholdSettings />
        <PushNotificationSettings />
      </div>
    </SettingsPageFrame>
  );
}
