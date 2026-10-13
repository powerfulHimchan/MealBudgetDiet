import type { ReactNode } from "react";
import styles from "./design-system.module.css";

export function Hero({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: ReactNode }) {
  return <section className={styles.hero}><div><p className={styles.eyebrow}>{eyebrow}</p><h1>{title}</h1><p className={styles.description}>{description}</p></div>{action}</section>;
}
