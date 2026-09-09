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
    description: "기본 월 예산과 특정 월에만 적용할 예외 예산을 관리합니다.",
    href: "/settings/budget",
    icon: WalletCards,
    label: "예산 관리",
    status: "사용 가능",
  },
  {
    description: "현재 기기에서 예산 초과 위험 알림을 받을지 설정합니다.",
    href: "/settings/notifications",
    icon: Bell,
    label: "푸시 알림",
    status: "사용 가능",
  },
  {
    description: "장보기, 외식, 배달처럼 식비를 분류할 항목을 관리합니다.",
    href: "/settings/categories",
    icon: Tags,
    label: "카테고리 관리",
    status: "다음 구현",
  },
  {
    description: "공유 장부의 참여자와 관리자 권한을 확인하고 관리합니다.",
    href: "/settings/members",
    icon: Users,
    label: "참여자 관리",
    status: "다음 구현",
  },
  {
    description: "가족이나 동료를 공유 장부로 초대할 코드를 관리합니다.",
    href: "/settings/invitations",
    icon: TicketCheck,
    label: "초대 코드",
    status: "다음 구현",
  },
  {
    description: "비밀번호 변경, 로그아웃, 회원 탈퇴를 관리합니다.",
    href: "/settings/account",
    icon: UserRoundCog,
    label: "계정 설정",
    status: "다음 구현",
  },
] as const;

export default function SettingsPage() {
  return (
    <SettingsPageFrame
      description="예산, 알림, 공유 장부와 계정에 관한 설정을 한곳에서 관리하세요."
      eyebrow="SETTINGS"
      title="설정"
    >
      <section aria-labelledby="settings-menu-title" className="settings-menu-section">
        <div className="settings-section-heading">
          <div>
            <p className="eyebrow">MANAGEMENT</p>
            <h2 id="settings-menu-title">설정 메뉴</h2>
          </div>
          <span>6개 메뉴</span>
        </div>
        <div className="settings-menu-grid">
          {menuItems.map((item) => {
            const Icon = item.icon;
            return (
              <Link className="settings-menu-card" href={item.href} key={item.href}>
                <span className="settings-menu-icon"><Icon size={22} /></span>
                <span className="settings-menu-copy">
                  <span className="settings-menu-title-row">
                    <strong>{item.label}</strong>
                    <small className={item.status === "사용 가능" ? "is-ready" : undefined}>{item.status}</small>
                  </span>
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
