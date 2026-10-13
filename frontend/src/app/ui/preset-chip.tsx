import type { ButtonHTMLAttributes } from "react";
import { Button } from "./button";
import styles from "./design-system.module.css";

export function PresetChip({ selected, className = "", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { selected: boolean }) {
  return <Button {...props} aria-pressed={selected} className={`${styles.preset} ${selected ? `${styles.selected} is-active` : ""} ${className}`} variant="outline" />;
}
