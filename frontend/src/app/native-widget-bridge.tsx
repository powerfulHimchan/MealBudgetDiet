"use client";

import { useEffect } from "react";
import { setWidgetPort } from "@/lib/native-widget";

export function NativeWidgetBridge() {
  useEffect(() => {
    const receive = (event: MessageEvent) => {
      if (event.origin !== "https://sikbi.app" || event.data !== "sikbi:ready" || !event.ports[0]) return;
      setWidgetPort(event.ports[0]);
    };
    window.addEventListener("message", receive);
    return () => { window.removeEventListener("message", receive); setWidgetPort(); };
  }, []);
  return null;
}
