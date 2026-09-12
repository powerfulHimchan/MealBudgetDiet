import { ArrowLeft, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { AppNav } from "../app-nav";
import { BrandLink } from "../brand-link";

type SettingsPageFrameProps = {
  badge?: ReactNode;
  children: ReactNode;
  description: string;
  eyebrow: string;
  showBackLink?: boolean;
  title: string;
};

export function SettingsPageFrame({
  badge,
  children,
  description,
  eyebrow,
  showBackLink = false,
  title,
}: SettingsPageFrameProps) {
  return (
    <main className="app-shell settings-shell">
      <header className="topbar">
        <BrandLink />
        {badge ?? <span className="page-badge"><SlidersHorizontal size={16} /> 설정</span>}
      </header>

      {showBackLink && <Link className="settings-back-link" href="/settings"><ArrowLeft size={16} /> 설정으로 돌아가기</Link>}

      <section className="page-hero">
        <div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p></div>
      </section>

      {children}
      <AppNav active="settings" />
    </main>
  );
}
