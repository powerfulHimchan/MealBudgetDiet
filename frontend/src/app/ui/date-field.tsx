"use client";

import type { ComponentProps } from "react";
import { CalendarPicker } from "./date-picker";
import styles from "./design-system.module.css";

export function DateField({ label, ...props }: ComponentProps<typeof CalendarPicker> & { label: string }) {
  return <div className={styles.dateField}><span>{label}</span><CalendarPicker compactOnMobile {...props} /></div>;
}
