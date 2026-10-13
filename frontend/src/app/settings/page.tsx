import {
  Bell,
  Tags,
  TicketCheck,
  UserRoundCog,
  Users,
  WalletCards,
} from "lucide-react";
import { SettingsPageFrame } from "./settings-page-frame";
import { MenuCard } from "../ui/menu-card";
import designStyles from "../ui/design-system.module.css";

const menuGroups = [
  { key: "expenses", title: "식비 관리", items: [{
    description: "예산 주기 시작일과 기본·주기별 예산을 관리합니다.",
    href: "/settings/budget",
    icon: WalletCards,
    label: "예산 관리",
    number: "01",
  },
  {
    description: "장보기, 외식, 배달처럼 식비를 분류할 항목을 관리합니다.",
    href: "/settings/categories",
    icon: Tags,
    label: "카테고리 관리",
    number: "02",
  },
  {
    description: "현재 기기에서 예산 초과 위험 알림을 받을지 설정합니다.",
    href: "/settings/notifications",
    icon: Bell,
    label: "푸시 알림",
    number: "03",
  }] },
  { key: "ledger", title: "공유 장부", items: [{
    description: "공유 장부의 멤버와 관리자 권한을 확인하고 관리합니다.",
    href: "/settings/members",
    icon: Users,
    label: "멤버 관리",
    number: "04",
  },
  {
    description: "가족이나 동료를 공유 장부로 초대할 코드를 관리합니다.",
    href: "/settings/invitations",
    icon: TicketCheck,
    label: "초대 코드",
    number: "05",
  }] },
  { key: "account", title: "계정", items: [{
    description: "비밀번호 변경, 로그아웃, 회원 탈퇴를 관리합니다.",
    href: "/settings/account",
    icon: UserRoundCog,
    label: "계정 설정",
    number: "06",
  }] },
] as const;

export default function SettingsPage() {
  return (
    <SettingsPageFrame
      description="예산, 알림, 공유 장부와 계정에 관한 설정을 한곳에서 관리하세요."
      eyebrow="SETTINGS"
      title="설정"
      redesigned
    >
      <div aria-label="설정 목록">{menuGroups.map((group) => (
        <section aria-labelledby={`settings-${group.key}-title`} className={designStyles.menuSection} key={group.key}>
          <h2 className={designStyles.sectionTitle} id={`settings-${group.key}-title`}>{group.title}</h2>
          <div className={designStyles.menuGrid}>
            {group.items.map((item) => <MenuCard description={item.description} href={item.href} icon={item.icon} key={item.href} number={item.number} title={item.label} />)}
            {group.items.length % 2 === 1 && <div aria-hidden="true" className={designStyles.menuFiller} />}
          </div>
        </section>
      ))}</div>
    </SettingsPageFrame>
  );
}
