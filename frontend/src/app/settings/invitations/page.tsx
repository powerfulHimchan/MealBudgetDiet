import { TicketCheck } from "lucide-react";
import { SettingsPlaceholder } from "../settings-placeholder";

export default function InvitationSettingsPage() {
  return <SettingsPlaceholder description="새 참여자가 공유 장부에 들어올 수 있는 초대 코드를 관리합니다." eyebrow="INVITATION SETTINGS" icon={TicketCheck} nextStep="초대 코드 생성·만료·재발급과 공유 기능을 이 화면에 연결할 예정입니다." title="초대 코드" />;
}
