"use client";

import { Bell, BellOff, LoaderCircle, Smartphone } from "lucide-react";
import { useEffect, useState } from "react";
import { mutation, request } from "../../lib/api";

type PushState = "checking" | "unsupported" | "denied" | "idle" | "active";
type ServerSubscription = { id: string; status: "ACTIVE" | "EXPIRED" | "DISABLED"; createdAt: string };
type StoredSubscription = { endpoint: string; id: string };

const STORAGE_KEY = "meal-budget-diet-push-subscription";

function base64UrlToUint8Array(value: string) {
  const padding = "=".repeat((4 - (value.length % 4)) % 4);
  const base64 = (value + padding).replaceAll("-", "+").replaceAll("_", "/");
  const raw = window.atob(base64);
  return Uint8Array.from(raw, (character) => character.charCodeAt(0));
}

function subscriptionBody(subscription: PushSubscription) {
  const serialized = subscription.toJSON();
  if (!serialized.endpoint || !serialized.keys?.p256dh || !serialized.keys.auth) {
    throw new Error("브라우저에서 푸시 구독 정보를 가져오지 못했습니다.");
  }
  return {
    endpoint: serialized.endpoint,
    expirationTime: serialized.expirationTime ?? null,
    keys: { p256dh: serialized.keys.p256dh, auth: serialized.keys.auth },
  };
}

function storeServerSubscription(subscription: PushSubscription, id: string) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ endpoint: subscription.endpoint, id } satisfies StoredSubscription));
}

function storedServerSubscription(subscription: PushSubscription) {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null") as StoredSubscription | null;
    return stored?.endpoint === subscription.endpoint ? stored : null;
  } catch {
    return null;
  }
}

export function PushNotificationSettings() {
  const [state, setState] = useState<PushState>("checking");
  const [browserSubscription, setBrowserSubscription] = useState<PushSubscription | null>(null);
  const [serverSubscriptionId, setServerSubscriptionId] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isIOSInstallRequired, setIsIOSInstallRequired] = useState(false);

  useEffect(() => {
    let active = true;
    const timer = window.setTimeout(async () => {
      setIsIOSInstallRequired(
        /iPad|iPhone|iPod/.test(navigator.userAgent)
        && !window.matchMedia("(display-mode: standalone)").matches,
      );
      if (!("serviceWorker" in navigator) || !("PushManager" in window) || !("Notification" in window)) {
        if (active) setState("unsupported");
        return;
      }
      if (Notification.permission === "denied") {
        if (active) setState("denied");
        return;
      }
      try {
        const registration = await navigator.serviceWorker.register("/sw.js", { updateViaCache: "none" });
        const existing = await registration.pushManager.getSubscription();
        if (!active) return;
        setBrowserSubscription(existing);
        if (existing) {
          const stored = storedServerSubscription(existing);
          try {
            const saved = await mutation<ServerSubscription>("/api/v1/push-subscriptions", "PUT", subscriptionBody(existing));
            if (!active) return;
            storeServerSubscription(existing, saved.id);
            setServerSubscriptionId(saved.id);
          } catch {
            if (!active) return;
            setServerSubscriptionId(stored?.id ?? null);
            setMessage("기기 구독을 서버와 동기화하지 못했습니다. 잠시 후 다시 시도해 주세요.");
          }
        }
        if (active) setState(existing ? "active" : "idle");
      } catch {
        if (active) setState("unsupported");
      }
    }, 0);
    return () => { active = false; window.clearTimeout(timer); };
  }, []);

  async function enablePush() {
    setIsSaving(true);
    setMessage(null);
    try {
      const permission = await Notification.requestPermission();
      if (permission !== "granted") {
        setState(permission === "denied" ? "denied" : "idle");
        setMessage("알림 권한을 허용해야 이 기기에서 푸시를 받을 수 있습니다.");
        return;
      }
      const { publicKey } = await request<{ publicKey: string }>("/api/v1/push/vapid-public-key");
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: base64UrlToUint8Array(publicKey),
      });
      const saved = await mutation<ServerSubscription>("/api/v1/push-subscriptions", "PUT", subscriptionBody(subscription));
      storeServerSubscription(subscription, saved.id);
      setBrowserSubscription(subscription);
      setServerSubscriptionId(saved.id);
      setState("active");
      setMessage("이 기기에서 예산 알림을 받습니다.");
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : "푸시 알림을 활성화하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
  }

  async function disablePush() {
    if (!browserSubscription) return;
    setIsSaving(true);
    setMessage(null);
    try {
      let id = serverSubscriptionId;
      if (!id) {
        const saved = await mutation<ServerSubscription>("/api/v1/push-subscriptions", "PUT", subscriptionBody(browserSubscription));
        id = saved.id;
      }
      await mutation(`/api/v1/push-subscriptions/${id}`, "DELETE");
      await browserSubscription.unsubscribe();
      localStorage.removeItem(STORAGE_KEY);
      setBrowserSubscription(null);
      setServerSubscriptionId(null);
      setState("idle");
      setMessage("이 기기의 예산 알림을 해제했습니다.");
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : "푸시 알림을 해제하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <section className="setting-panel push-setting-panel">
      <div className="panel-heading">
        <span className="panel-icon">{state === "active" ? <Bell size={21} /> : <BellOff size={21} />}</span>
        <div><p className="eyebrow">DEVICE NOTIFICATION</p><h2>푸시 알림</h2></div>
        <span className={`source-chip push-state push-state--${state}`}>
          {state === "active" ? "활성" : state === "checking" ? "확인 중" : "비활성"}
        </span>
      </div>
	  <p className="setting-description">예산 사용률이 80% 이상이고 현재 소비 속도가 이어질 때 월 예산 초과가 예상되면 월 1회 알려드립니다.</p>

      {state === "checking" && <div className="push-support-note"><LoaderCircle className="spin" size={18} />기기 지원 여부를 확인하고 있어요.</div>}
      {state === "unsupported" && <div className="push-support-note"><Smartphone size={18} />이 브라우저에서는 Web Push를 지원하지 않습니다.</div>}
      {state === "denied" && <div className="push-support-note push-support-note--warning"><BellOff size={18} />브라우저 설정에서 MealBudgetDiet 알림 권한을 허용해 주세요.</div>}
      {isIOSInstallRequired && <div className="push-support-note"><Smartphone size={18} />iPhone에서는 홈 화면에 앱을 추가한 뒤 푸시를 활성화할 수 있습니다.</div>}
      {message && <p className="push-message" role="status">{message}</p>}

      <div className="push-actions">
        {state === "active" ? (
          <button className="secondary-button danger-button" disabled={isSaving} onClick={() => void disablePush()} type="button">
            {isSaving && <LoaderCircle className="spin" size={16} />}이 기기 알림 해제
          </button>
        ) : (
          <button className="dark-button" disabled={isSaving || state === "checking" || state === "unsupported" || state === "denied"} onClick={() => void enablePush()} type="button">
            {isSaving && <LoaderCircle className="spin" size={16} />}이 기기 알림 활성화
          </button>
        )}
      </div>
    </section>
  );
}
