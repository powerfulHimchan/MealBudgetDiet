import { UserRoundCog } from "lucide-react";
import { SettingsPlaceholder } from "../settings-placeholder";

export default function AccountSettingsPage() {
  return <SettingsPlaceholder description="내 로그인 정보와 서비스 이용 상태를 관리합니다." eyebrow="ACCOUNT SETTINGS" icon={UserRoundCog} nextStep="비밀번호 변경·로그아웃·회원 탈퇴 기능을 이 화면에 연결할 예정입니다." title="계정 설정" />;
}
