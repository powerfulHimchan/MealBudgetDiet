"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";
import { request } from "../lib/api";

type CurrentUser = { displayName: string; profileImageUrl: string | null };

export function CurrentUserAvatar() {
  const [user, setUser] = useState<CurrentUser | null>(null);

  useEffect(() => {
    let active = true;
    request<CurrentUser>("/api/v1/auth/me")
      .then((current) => active && setUser(current))
      .catch(() => undefined);
    return () => { active = false; };
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
