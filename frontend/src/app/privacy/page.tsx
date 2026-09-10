import type { Metadata } from "next";
import Link from "next/link";
import { PublicDocument } from "../public-document";

export const metadata: Metadata = {
  title: "개인정보처리방침 | MealBudgetDiet",
  description: "MealBudgetDiet의 개인정보 수집, 이용, 보관 및 삭제 방침입니다.",
};

export default function PrivacyPage() {
  return (
    <PublicDocument
      description="MealBudgetDiet 운영자(powerfulHimchan)는 필요한 정보만 처리하고, 사용자가 자신의 정보를 통제할 수 있도록 합니다."
      eyebrow="PRIVACY POLICY"
      title="개인정보처리방침"
    >
      <p className="public-document-date">시행일: 2026년 9월 10일</p>

      <section>
        <h2>1. 처리하는 정보와 이용 목적</h2>
        <div className="public-document-table-wrap">
          <table>
            <thead><tr><th>구분</th><th>처리 정보</th><th>이용 목적</th></tr></thead>
            <tbody>
              <tr><td>계정</td><td>이메일, 표시 이름, 단방향 해시된 비밀번호</td><td>가입, 로그인, 참여자 식별, 비밀번호 재설정</td></tr>
              <tr><td>공유 장부</td><td>식비 금액·날짜·상호·메모·카테고리, 예산, 역할</td><td>공동 조회·편집, 예산 계산, 기간별 통계</td></tr>
              <tr><td>선택 이미지</td><td>프로필 사진, 사용자가 첨부한 식비 이미지</td><td>프로필 및 지출 증빙 표시</td></tr>
              <tr><td>선택 알림</td><td>브라우저 Push 구독 주소와 암호화 키, 수신 상태</td><td>예산 초과 위험 알림 발송</td></tr>
              <tr><td>보안 기록</td><td>로그인·비밀번호 재설정 요청의 가명 처리 식별값, 오류 기록</td><td>비정상 요청 제한, 장애 대응, 보안 감사</td></tr>
            </tbody>
          </table>
        </div>
        <p>서비스는 로그인 유지에 필요한 HttpOnly 세션 쿠키를 사용합니다. 광고 쿠키나 광고 식별자는 사용하지 않습니다.</p>
      </section>

      <section>
        <h2>2. 처리 방법과 공유 범위</h2>
        <p>비밀번호 원문은 저장하지 않고 단방향 해시로 보관합니다. 업로드 이미지는 비공개 객체 저장소에 저장하며, 활성 공유 장부 참여자에게만 조회 권한을 부여합니다. 식비와 예산 정보는 사용자가 참여 중인 같은 공유 장부의 참여자에게 표시됩니다.</p>
        <p>서비스 운영에 필요한 범위에서 호스팅, 데이터베이스·객체 저장소, 이메일 발송 사업자가 정보를 처리할 수 있습니다. 실제 운영 사업자와 국외 이전 여부는 배포 환경이 확정되면 이 방침에 반영합니다. 개인정보를 판매하거나 맞춤 광고에 이용하지 않습니다.</p>
      </section>

      <section>
        <h2>3. 보관과 삭제</h2>
        <ul>
          <li>계정 이용 중에는 서비스 제공을 위해 정보를 보관합니다.</li>
          <li>일반 참여자가 탈퇴하면 이메일, 표시 이름, 비밀번호, 프로필 사진, Push 구독과 로그인 세션을 삭제하거나 계정과 연결할 수 없도록 익명화합니다.</li>
          <li>다른 참여자와 이미 공유된 식비 기록은 공동 장부의 연속성을 위해 작성자 식별정보와 연결을 끊은 상태로 유지될 수 있습니다.</li>
          <li>마지막 관리자가 장부 종료를 선택하면 장부의 식비, 예산, 이미지와 참여자 계정을 삭제합니다.</li>
          <li>법령상 보관 의무가 있는 경우에는 해당 기간 동안 분리 보관한 뒤 삭제합니다. 백업 데이터는 운영 백업 정책에 따라 순차적으로 만료됩니다.</li>
        </ul>
        <p>구체적인 삭제 방법은 <Link href="/account-deletion">계정 삭제 안내</Link>에서 확인할 수 있습니다.</p>
      </section>

      <section>
        <h2>4. 이용자의 권리</h2>
        <p>설정에서 프로필 사진을 변경·삭제하고, Push 수신을 해제하거나, 계정을 직접 삭제할 수 있습니다. 계정 삭제가 어려운 경우 아래 문의 채널로 요청할 수 있습니다.</p>
      </section>

      <section>
        <h2>5. 보호 조치</h2>
        <p>전송 구간 암호화(HTTPS), HttpOnly 세션 쿠키, 접근 권한 검사, 로그인 시도 제한, 이미지 형식 검증과 메타데이터 제거, 비공개 객체 저장소를 적용합니다.</p>
      </section>

      <section>
        <h2>6. 문의</h2>
        <p>개인정보 또는 계정 삭제 문의는 <a href="https://github.com/powerfulHimchan/MealBudgetDiet/issues">MealBudgetDiet GitHub 이슈</a>를 이용해 주세요. 공개 이슈에는 비밀번호, 초대 코드, 영수증 이미지 등 민감한 정보를 작성하지 마세요.</p>
      </section>

      <aside className="public-document-notice">
        운영 도메인과 인프라 사업자가 확정되면 연락 이메일, 처리 위탁 사업자, 국외 이전 및 백업 보존 기간을 실제 운영 내용에 맞게 갱신합니다.
      </aside>
    </PublicDocument>
  );
}
