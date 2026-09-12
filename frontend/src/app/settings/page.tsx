import {
  Bell,
  ChevronRight,
  Tags,
  TicketCheck,
  UserRoundCog,
  Users,
  WalletCards,
} from "lucide-react";
import Link from "next/link";
import { SettingsPageFrame } from "./settings-page-frame";

const menuItems = [
  {
    description: "예산 주기 시작일과 기본·주기별 예산을 관리합니다.",
    href: "/settings/budget",
    icon: WalletCards,
    label: "예산 관리",
  },
  {
    description: "현재 기기에서 예산 초과 위험 알림을 받을지 설정합니다.",
    href: "/settings/notifications",
    icon: Bell,
    label: "푸시 알림",
  },
  {
    description: "장보기, 외식, 배달처럼 식비를 분류할 항목을 관리합니다.",
    href: "/settings/categories",
    icon: Tags,
    label: "카테고리 관리",
  },
  {
    description: "공유 장부의 멤버와 관리자 권한을 확인하고 관리합니다.",
    href: "/settings/members",
    icon: Users,
    label: "멤버 관리",
  },
  {
    description: "가족이나 동료를 공유 장부로 초대할 코드를 관리합니다.",
    href: "/settings/invitations",
    icon: TicketCheck,
    label: "초대 코드",
  },
  {
    description: "비밀번호 변경, 로그아웃, 회원 탈퇴를 관리합니다.",
    href: "/settings/account",
    icon: UserRoundCog,
    label: "계정 설정",
  },
] as const;

export default function SettingsPage() {
  return (
    <SettingsPageFrame
      description="예산, 알림, 공유 장부와 계정에 관한 설정을 한곳에서 관리하세요."
      eyebrow="SETTINGS"
      title="설정"
    >
      <section aria-label="설정 목록" className="settings-menu-section">
        <div className="settings-menu-grid">
          {menuItems.map((item) => {
            const Icon = item.icon;
            return (
              <Link className="settings-menu-card" href={item.href} key={item.href}>
                <span className="settings-menu-icon"><Icon size={22} /></span>
                <span className="settings-menu-copy">
                  <strong>{item.label}</strong>
                  <span>{item.description}</span>
                </span>
                <ChevronRight aria-hidden="true" className="settings-menu-arrow" size={20} />
              </Link>
            );
          })}
        </div>
      </section>
    </SettingsPageFrame>
  );
}
