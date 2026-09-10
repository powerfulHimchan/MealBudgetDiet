import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

const publicAuthPaths = new Set(["/login", "/join", "/setup"]);

export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const hasSession = request.cookies.has("MBD_SESSION");

  if (publicAuthPaths.has(pathname)) {
    return hasSession ? NextResponse.redirect(new URL("/", request.url)) : NextResponse.next();
  }
  if (hasSession) return NextResponse.next();

  const loginUrl = new URL("/login", request.url);
  loginUrl.searchParams.set("next", `${pathname}${search}`);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ["/", "/expenses/:path*", "/statistics/:path*", "/settings/:path*", "/login", "/join", "/setup"],
};
