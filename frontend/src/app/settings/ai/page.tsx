import { AiConnectionCheck } from "./ai-connection-check";
import { SettingsPageFrame } from "../settings-page-frame";

export default function AiConnectionPage() {
  return (
    <SettingsPageFrame description="서버의 OpenAI 설정과 실제 응답을 확인하세요." eyebrow="AI CONNECTION" showBackLink title="AI 연결 확인">
      <AiConnectionCheck />
    </SettingsPageFrame>
  );
}
