import { ArrowLeft, ExternalLink, ShieldCheck } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

type PublicDocumentProps = {
  eyebrow: string;
  title: string;
  description: string;
  children: ReactNode;
};

export function PublicDocument({ eyebrow, title, description, children }: PublicDocumentProps) {
  return (
    <main className="public-document-shell">
      <header className="public-document-header">
        <Link aria-label="식비 로그인" className="auth-brand" href="/login">
          <span className="brand-mark">S</span><span>식비</span>
        </Link>
        <Link className="public-document-login" href="/login">
          로그인 <ExternalLink size={15} />
        </Link>
      </header>

      <article className="public-document">
        <div className="public-document-hero">
          <span className="public-document-icon"><ShieldCheck size={25} /></span>
          <p className="eyebrow">{eyebrow}</p>
          <h1>{title}</h1>
          <p>{description}</p>
        </div>
        <div className="public-document-body">{children}</div>
      </article>

      <footer className="public-document-footer">
        <Link href="/login"><ArrowLeft size={15} /> 서비스로 돌아가기</Link>
        <nav aria-label="정책 문서">
          <Link href="/privacy">개인정보처리방침</Link>
          <Link href="/account-deletion">계정 삭제 안내</Link>
        </nav>
      </footer>
    </main>
  );
}
