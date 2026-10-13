import type { ComponentProps } from "react";
import { BottomNav } from "./ui/bottom-nav";

export function AppNav(props: ComponentProps<typeof BottomNav>) {
  return <BottomNav {...props} />;
}
