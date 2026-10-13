import { BarChart3, House, ReceiptText, Settings } from "lucide-react";
import Link from "next/link";
import styles from "./design-system.module.css";

const items = [
  { key: "home", href: "/", label: "홈", icon: House },
  { key: "expenses", href: "/expenses", label: "식비", icon: ReceiptText },
  { key: "statistics", href: "/statistics", label: "통계", icon: BarChart3 },
  { key: "settings", href: "/settings", label: "설정", icon: Settings },
] as const;

export function BottomNav({ active }: { active: typeof items[number]["key"] }) {
  return <nav className={styles.bottomNav} aria-label="주요 메뉴">{items.map(({ key, href, label, icon: Icon }) =>
    <Link aria-current={key === active ? "page" : undefined} className={key === active ? styles.current : undefined} href={href} key={key}><Icon aria-hidden="true" size={20} /><span>{label}</span></Link>,
  )}</nav>;
}
