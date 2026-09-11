const anonymousUnauthorizedPaths = new Set([
  "/api/v1/auth/login",
  "/api/v1/auth/register",
  "/api/v1/auth/register-ledger",
  "/api/v1/auth/password-reset-requests",
  "/api/v1/auth/password-resets",
  "/api/v1/bootstrap/admin",
]);

function recoverExpiredSession(url: string, status: number) {
  if (status !== 401 || typeof window === "undefined") return;
  const pathname = new URL(url, window.location.origin).pathname;
  if (anonymousUnauthorizedPaths.has(pathname) || window.location.pathname === "/login") return;

  const destination = `${window.location.pathname}${window.location.search}`;
  const loginUrl = new URL("/login", window.location.origin);
  loginUrl.searchParams.set("expired", "1");
  loginUrl.searchParams.set("next", destination);
  window.location.replace(loginUrl.toString());
}

export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { credentials: "include", ...init });
  if (!response.ok) {
    recoverExpiredSession(url, response.status);
    const problem = await response.json().catch(() => null) as { detail?: string } | null;
    throw new Error(problem?.detail ?? (response.status === 401
      ? "로그인 세션이 만료되었습니다."
      : "요청을 처리하지 못했습니다."));
  }
  if (response.status === 204) return undefined as T;
  const responseText = await response.text();
  return (responseText ? JSON.parse(responseText) : undefined) as T;
}

export async function mutation<T>(
  url: string,
  method: string,
  body?: unknown,
  additionalHeaders?: Record<string, string>,
): Promise<T> {
  const csrf = await request<{ headerName: string; token: string }>("/api/v1/auth/csrf");
  return request<T>(url, {
    method,
    headers: {
      "Content-Type": "application/json",
      [csrf.headerName]: csrf.token,
      ...additionalHeaders,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export async function multipartMutation<T>(url: string, formData: FormData): Promise<T> {
  const csrf = await request<{ headerName: string; token: string }>("/api/v1/auth/csrf");
  return request<T>(url, {
    method: "POST",
    headers: { [csrf.headerName]: csrf.token },
    body: formData,
  });
}

export function todayInSeoul() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date());
}

export function currentYearMonthInSeoul() {
  return todayInSeoul().slice(0, 7);
}
