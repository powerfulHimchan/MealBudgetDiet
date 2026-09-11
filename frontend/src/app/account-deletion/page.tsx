import type { Metadata } from "next";
import { LogIn, Trash2 } from "lucide-react";
import Link from "next/link";
import { PublicDocument } from "../public-document";

export const metadata: Metadata = {
  title: "계정 삭제 안내 | Sikbi - 함께 쓰는 식비 관리",
  description: "웹에서 식비(Sikbi) 계정과 관련 정보를 삭제하는 방법을 안내합니다.",
};

export default function AccountDeletionPage() {
  return (
    <PublicDocument
      description="앱을 설치하지 않아도 이 웹페이지에서 로그인해 계정 삭제를 직접 요청할 수 있습니다."
      eyebrow="ACCOUNT DELETION"
      title="Sikbi 계정 삭제"
    >
      <section>
        <h2>웹에서 직접 삭제하는 방법</h2>
        <ol className="public-document-steps">
          <li><strong>계정 설정 열기</strong><span>아래 버튼을 누르고 삭제할 계정으로 로그인합니다.</span></li>
          <li><strong>회원 탈퇴 선택</strong><span>계정 설정의 위험 구역에서 회원 탈퇴 또는 장부 종료를 선택합니다.</span></li>
          <li><strong>본인 확인 후 삭제</strong><span>현재 비밀번호를 입력하고 삭제 내용을 확인하면 즉시 처리됩니다.</span></li>
        </ol>
        <Link className="public-document-primary" href="/settings/account">
          <LogIn size={18} /> 로그인하고 계정 삭제
        </Link>
      </section>

      <section>
        <h2>삭제되는 정보</h2>
        <ul>
          <li>이메일, 표시 이름, 단방향 해시된 비밀번호와 프로필 사진</li>
          <li>로그인 세션, 비밀번호 재설정 토큰과 Push 구독 정보</li>
          <li>계정과 직접 연결된 참여 상태 및 식별 정보</li>
        </ul>
      </section>

      <section>
        <h2>공유 장부에 남을 수 있는 정보</h2>
        <p>다른 참여자가 계속 사용하는 공유 장부의 식비 기록은 공동 기록의 연속성을 위해 탈퇴 계정의 식별정보와 연결을 끊은 상태로 유지될 수 있습니다. 마지막 관리자가 장부 종료를 선택하면 장부의 식비, 예산, 이미지와 모든 참여자 계정이 함께 삭제됩니다.</p>
      </section>

      <section>
        <h2>삭제 전 확인</h2>
        <p>삭제는 되돌릴 수 없습니다. 마지막 관리자는 장부 이름을 이용한 추가 확인을 완료해야 합니다. 삭제 후 같은 이메일로 다시 가입하면 이전 계정과 연결되지 않은 새 계정이 생성됩니다.</p>
      </section>

      <aside className="public-document-danger">
        <Trash2 size={20} />
        <div><strong>삭제 요청에 문제가 있나요?</strong><span><a href="https://github.com/powerfulHimchan/MealBudgetDiet/issues">GitHub 문의 채널</a>에서 요청해 주세요. 공개 글에는 민감한 정보를 남기지 마세요.</span></div>
      </aside>
    </PublicDocument>
  );
}
