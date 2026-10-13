import type { AnchorHTMLAttributes, ButtonHTMLAttributes } from "react";
import styles from "./design-system.module.css";

type Appearance = { variant?: "primary" | "outline" | "ink" | "white"; className?: string };
type ButtonProps = Appearance & (ButtonHTMLAttributes<HTMLButtonElement> | (AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }));

export function Button({ variant = "primary", className = "", ...props }: ButtonProps) {
  const classes = `${styles.button} ${styles[variant]} ${className}`;
  if ("href" in props) return <a {...props} className={classes} data-ui="button" />;
  return <button {...props} className={classes} data-ui="button" type={props.type ?? "button"} />;
}
