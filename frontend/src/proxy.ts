import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

const publicAuthPaths = new Set(["/login", "/signup", "/join", "/setup", "/forgot-password", "/reset-password"]);
const recoveryPaths = new Set(["/forgot-password", "/reset-password"]);

export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const hasSession = request.cookies.has("MBD_SESSION");

  if (pathname === "/login" && request.nextUrl.searchParams.get("expired") === "1") {
    const response = NextResponse.next();
    response.cookies.delete("MBD_SESSION");
    return response;
  }

  if (publicAuthPaths.has(pathname)) {
    return hasSession && !recoveryPaths.has(pathname)
      ? NextResponse.redirect(new URL("/", request.url))
      : NextResponse.next();
  }
  if (hasSession) return NextResponse.next();

  const loginUrl = new URL("/login", request.url);
  loginUrl.searchParams.set("next", `${pathname}${search}`);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ["/", "/expenses/:path*", "/statistics/:path*", "/settings/:path*", "/login", "/signup", "/join", "/setup", "/forgot-password", "/reset-password"],
};
