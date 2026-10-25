import { SettingsPageFrame } from "../settings-page-frame";
import { WidgetSettings } from "./widget-settings";

export default async function WidgetPage({ searchParams }: {
  searchParams: Promise<{ challenge?: string | string[] }>;
}) {
  const { challenge } = await searchParams;
  return <SettingsPageFrame title="홈 화면 위젯" eyebrow="HOME WIDGET" showBackLink
    description="앱을 열지 않고 남은 예산과 오늘 지출을 확인하고, 바로 기록하세요.">
    <WidgetSettings challenge={typeof challenge === "string" ? challenge : undefined} />
  </SettingsPageFrame>;
}
