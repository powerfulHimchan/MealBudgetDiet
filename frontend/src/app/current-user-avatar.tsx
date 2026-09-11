"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";
import { request } from "../lib/api";

export type CurrentUserAvatarData = { displayName: string; profileImageUrl: string | null };

let cachedUser: CurrentUserAvatarData | null = null;
let pendingUser: Promise<CurrentUserAvatarData> | null = null;
const subscribers = new Set<(user: CurrentUserAvatarData | null) => void>();

function publishCurrentUser(user: CurrentUserAvatarData | null) {
  cachedUser = user;
  subscribers.forEach((subscriber) => subscriber(user));
}

function loadCurrentUser() {
  if (cachedUser) return Promise.resolve(cachedUser);
  if (!pendingUser) {
    pendingUser = request<CurrentUserAvatarData>("/api/v1/auth/me")
      .then((user) => {
        publishCurrentUser(user);
        return user;
      })
      .finally(() => {
        pendingUser = null;
      });
  }
  return pendingUser;
}

export function updateCurrentUserAvatar(user: CurrentUserAvatarData) {
  publishCurrentUser(user);
}

export function clearCurrentUserAvatar() {
  publishCurrentUser(null);
}

export function CurrentUserAvatar() {
  const [user, setUser] = useState<CurrentUserAvatarData | null>(cachedUser);

  useEffect(() => {
    let active = true;
    const update = (current: CurrentUserAvatarData | null) => {
      if (active) setUser(current);
    };
    subscribers.add(update);
    void loadCurrentUser().catch(() => undefined);
    return () => {
      active = false;
      subscribers.delete(update);
    };
  }, []);

  return (
    <Link className="avatar" href="/settings/account" aria-label="계정 설정">
      {user?.profileImageUrl ? (
        <Image alt="" fill priority sizes="38px" src={user.profileImageUrl} unoptimized />
      ) : (
        user?.displayName.trim().slice(0, 1) || "나"
      )}
    </Link>
  );
}
