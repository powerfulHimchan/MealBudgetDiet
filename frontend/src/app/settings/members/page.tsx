import { Users } from "lucide-react";
import { SettingsPlaceholder } from "../settings-placeholder";

export default function MemberSettingsPage() {
  return <SettingsPlaceholder description="공유 장부를 함께 사용하는 사람과 권한을 관리합니다." eyebrow="MEMBER SETTINGS" icon={Users} nextStep="참여자 목록 조회와 관리자 권한 변경 기능을 이 화면에 연결할 예정입니다." title="참여자 관리" />;
}
