import { Construction, type LucideIcon } from "lucide-react";
import { SettingsPageFrame } from "./settings-page-frame";

type SettingsPlaceholderProps = {
  description: string;
  eyebrow: string;
  icon: LucideIcon;
  nextStep: string;
  title: string;
};

export function SettingsPlaceholder({ description, eyebrow, icon: Icon, nextStep, title }: SettingsPlaceholderProps) {
  return (
    <SettingsPageFrame description={description} eyebrow={eyebrow} showBackLink title={title}>
      <section className="settings-placeholder-panel">
        <span className="settings-placeholder-icon"><Icon size={25} /></span>
        <div>
          <p className="eyebrow"><Construction size={15} /> NEXT IMPLEMENTATION</p>
          <h2>메뉴 구조를 준비했습니다</h2>
          <p>{nextStep}</p>
        </div>
      </section>
    </SettingsPageFrame>
  );
}
