export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { credentials: "include", ...init });
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { detail?: string } | null;
    throw new Error(problem?.detail ?? "요청을 처리하지 못했습니다.");
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function mutation<T>(url: string, method: string, body?: unknown): Promise<T> {
  const csrf = await request<{ headerName: string; token: string }>("/api/v1/auth/csrf");
  return request<T>(url, {
    method,
    headers: {
      "Content-Type": "application/json",
      [csrf.headerName]: csrf.token,
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
