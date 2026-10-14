// Signing fingerprints are public identifiers; configure the Play app signing
// certificate (and an explicitly trusted test certificate only when needed).
export const dynamic = "force-dynamic";

export function GET() {
  const values = (process.env.ANDROID_APP_SHA256_FINGERPRINTS ?? "").split(",").map((value) => value.trim().toUpperCase()).filter(Boolean);
  if (values.length === 0 || values.some((value) => !/^([0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(value))) {
    return Response.json({ code: "ANDROID_APP_LINKS_NOT_CONFIGURED" }, { status: 503, headers: { "Cache-Control": "no-store" } });
  }
  return Response.json([{
    relation: ["delegate_permission/common.handle_all_urls"],
    target: { namespace: "android_app", package_name: "app.sikbi", sha256_cert_fingerprints: [...new Set(values)] },
  }], { headers: { "Cache-Control": "public, max-age=300" } });
}
