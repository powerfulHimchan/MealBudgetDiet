import { ArrowRight, type LucideIcon } from "lucide-react";
import Link from "next/link";
import styles from "./design-system.module.css";

export function MenuCard({ href, icon: Icon, number, title, description }: { href: string; icon: LucideIcon; number: string; title: string; description: string }) {
  return <Link className={styles.menuCard} href={href}>
    <span className={styles.menuIcon}><Icon aria-hidden="true" size={22} /></span>
    <span className={styles.menuCopy}><span className={styles.menuNumber}>{number}</span><strong>{title}</strong><span className={styles.menuDescription}>{description}</span></span>
    <ArrowRight aria-hidden="true" className={styles.menuArrow} size={20} />
  </Link>;
}
