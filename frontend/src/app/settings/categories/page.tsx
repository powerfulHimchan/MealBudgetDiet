import { Tags } from "lucide-react";
import { SettingsPlaceholder } from "../settings-placeholder";

export default function CategorySettingsPage() {
  return <SettingsPlaceholder description="공유 장부에서 사용할 식비 분류를 관리합니다." eyebrow="CATEGORY SETTINGS" icon={Tags} nextStep="카테고리 추가·이름 변경·순서 변경·삭제 기능을 이 화면에 연결할 예정입니다." title="카테고리 관리" />;
}
