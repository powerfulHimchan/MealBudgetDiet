import { ArrowLeft, Construction } from "lucide-react";
import Link from "next/link";

type PlaceholderPageProps = {
  description: string;
  title: string;
};

export function PlaceholderPage({ description, title }: PlaceholderPageProps) {
  return (
    <main className="placeholder-page">
      <span className="placeholder-page__icon"><Construction size={28} /></span>
      <p className="eyebrow">다음 구현 단계</p>
      <h1>{title}</h1>
      <p>{description}</p>
      <Link href="/"><ArrowLeft size={18} /> 대시보드로 돌아가기</Link>
    </main>
  );
}
