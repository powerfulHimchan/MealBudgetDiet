import { WifiOff } from "lucide-react";
import Link from "next/link";

export default function OfflinePage() {
  return (
    <main className="offline-page">
      <span className="offline-page__icon"><WifiOff size={30} /></span>
      <h1>인터넷 연결이 필요해요</h1>
      <p>연결 상태를 확인한 뒤 다시 시도해 주세요.</p>
      <Link href="/">다시 시도</Link>
    </main>
  );
}
