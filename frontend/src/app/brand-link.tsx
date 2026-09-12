import Image from "next/image";
import Link from "next/link";

type BrandLinkProps = {
  className?: string;
  href?: string;
};

export function BrandLink({ className = "brand", href = "/" }: BrandLinkProps) {
  return (
    <Link aria-label={href === "/" ? "sikbi 홈" : "sikbi 로그인"} className={className} href={href}>
      <span className="brand-mark"><Image alt="" height={34} src="/icon.svg" unoptimized width={34} /></span>
      <span className="brand-name">sikbi - 함께 쓰는 식비 관리</span>
    </Link>
  );
}
