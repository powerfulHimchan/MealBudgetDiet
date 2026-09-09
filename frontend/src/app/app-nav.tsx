import { BarChart3, House, ReceiptText, Settings } from "lucide-react";
import Link from "next/link";

type AppNavProps = { active: "home" | "expenses" | "statistics" | "settings" };

const items = [
  { key: "home", href: "/", label: "홈", icon: House },
  { key: "expenses", href: "/expenses", label: "식비", icon: ReceiptText },
  { key: "statistics", href: "/statistics", label: "통계", icon: BarChart3 },
  { key: "settings", href: "/settings", label: "설정", icon: Settings },
] as const;

export function AppNav({ active }: AppNavProps) {
  return (
    <nav className="bottom-nav" aria-label="주요 메뉴">
      {items.map((item) => {
        const Icon = item.icon;
        return (
          <Link className={item.key === active ? "is-active" : undefined} href={item.href} key={item.key}>
            <Icon size={20} /><span>{item.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
