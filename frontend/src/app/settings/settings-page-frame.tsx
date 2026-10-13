import { ArrowLeft, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { AppNav } from "../app-nav";
import { BrandLink } from "../brand-link";
import { BottomNav } from "../ui/bottom-nav";
import { Hero } from "../ui/hero";
import designStyles from "../ui/design-system.module.css";

type SettingsPageFrameProps = {
  badge?: ReactNode;
  children: ReactNode;
  description: string;
  eyebrow: string;
  showBackLink?: boolean;
  title: string;
  redesigned?: boolean;
};

export function SettingsPageFrame({
  badge,
  children,
  description,
  eyebrow,
  showBackLink = false,
  title,
  redesigned = false,
}: SettingsPageFrameProps) {
  return (
    <main className={`app-shell settings-shell${redesigned ? ` ${designStyles.scope}` : ""}`}>
      <header className="topbar">
        <BrandLink />
        {badge ?? <span className="page-badge"><SlidersHorizontal size={16} /> 설정</span>}
      </header>

      {showBackLink && <Link className="settings-back-link" href="/settings"><ArrowLeft size={16} /> 설정으로 돌아가기</Link>}

      {redesigned ? <Hero eyebrow={eyebrow} title={title} description={description} /> : <section className="page-hero">
        <div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p></div>
      </section>}

      {children}
      {redesigned ? <BottomNav active="settings" /> : <AppNav active="settings" />}
    </main>
  );
}
