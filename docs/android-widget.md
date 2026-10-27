# Android 홈 화면 위젯

Android 앱 1.2.0(versionCode 5)부터 지원한다. 패키지 `app.sikbi`, 운영 도메인 `https://sikbi.app`을 유지한다.

## 사용자 흐름

1. 최신 앱 설치 후 홈 화면을 길게 눌러 위젯 목록에서 **Sikbi 식비**를 추가한다. 4×2 또는 4×1 네이비 위젯을 선택할 수 있으며 가로 크기 조절을 지원한다.
2. 위젯의 **연결** → 네이티브 연결 화면 → 웹 로그인 → **이 장부를 위젯에 연결** → **앱으로 돌아가 완료**를 누른다.
3. 남은 예산과 오늘 지출을 확인한다. 두 항목의 이름·금액을 누르면 대시보드로 이동한다. **기록**은 `https://sikbi.app/expenses?new=1`로 이동한다.
4. 설정 → 홈 화면 위젯에서 이 로그인 세션이 승인한 위젯들을 해제할 수 있다. 위젯을 모두 삭제하면 기기의 인증도 삭제한다.

## 데이터와 갱신

- `GET /api/v1/widget/summary`: 기존 웹 세션용. 주기·예산·사용액·잔액·사용률·상태 외 `todaySpent`, `today`를 포함한다.
- `GET /api/v1/widget/device/summary`: 읽기 전용 위젯 토큰 전용. 기존 요약과 같은 서버 계산을 사용하며 이월 예산도 반영한다.
- 모든 응답은 `Cache-Control: no-store`. 장부 이름·멤버·메모·사진·상호명은 포함하지 않는다.
- 오늘은 Asia/Seoul 기준. 초과 예산은 음수와 빨간색으로 표시한다.
- 설치·앱 진입·수동 새로고침·브라우저 숨김 시 갱신 작업을 요청한다. Android의 30분 주기 갱신도 사용한다. 절전 모드에 따라 실행이 지연될 수 있다.
- Chrome TWA에서 검증된 postMessage 채널이 연결되면 식비/예산 변경 후 즉시 갱신을 요청하고 로그아웃 시 기기 권한과 표시 금액을 지운다. 지원하지 않는 브라우저나 DAL 미설정 시 앱 진입/숨김·수동·주기 갱신으로 동작한다.
- 통신 실패 시 이전 금액을 숨기고 새로고침 안내를 표시한다. 401/403은 토큰도 삭제한다. 다른 기기에서 세션이 철회된 경우 다음 갱신 때 반영된다.

## 인증 연결

TWA 쿠키를 네이티브에서 읽지 않는다. 네이티브가 256비트 무작위 verifier를 생성하고 SHA-256 challenge만 웹에 전달한다. 로그인한 사용자가 CSRF 보호된 `POST /api/v1/widget/connections`에 challenge를 승인한다. 승인 유효기간은 5분이다.

브라우저에서 `sikbi-widget://connected?challenge=...`로 돌아오면 네이티브가 보관한 verifier와 challenge를 검증하고 `POST /api/v1/widget/device/claim`에 verifier를 제출한다. 승인은 원자적으로 한 번만 소비되며 서버가 256비트 토큰을 발급한다. 콜백 URL에는 인증 토큰이 없다.

- DB에는 토큰의 SHA-256 해시만 저장한다. 최대 30일 유효하며 승인한 원래 세션의 유효기간을 연장하지 않는다.
- 원래 세션·사용자 ACTIVE·원래 장부 ACTIVE·장부 참여 ACTIVE를 매 요청 확인한다. 일반 API 인증으로 사용할 수 없다.
- 세션 FK ON DELETE CASCADE로 로그아웃·비밀번호 재설정·계정 변경의 세션 제거 시 권한도 삭제한다.
- Android Keystore AES/GCM으로 암호화한 인증만 `noBackupFilesDir`에 보관한다. 금액은 기기 파일에 저장하지 않는다.
- 로그아웃/재연결 중인 이전 네트워크 응답이 금액을 복원하지 않도록 토큰을 재확인한다.
- Digital Asset Links는 `handle_all_urls`와 `use_as_origin`을 선언한다. 운영 웹의 `ANDROID_APP_SHA256_FINGERPRINTS`에 기존 Play 앱 서명 인증서 지문을 유지해야 한다.

## 검증 및 출시

`Android checks`: 단위 테스트·lint·debug 컴파일, 연결/오프라인/초과 금액 RemoteViews 렌더링 및 기록 딥링크 검사.
`WidgetConnectionIntegrationTest`: CSRF, 잘못된 verifier, 재사용, 일반 API 접근 거부, 연결 해제, 로그아웃 cascade, 승인/토큰 만료, 탈퇴한 멤버 접근 거부.
프런트 E2E: 위젯 연결 승인·콜백, 잘못된 challenge, 연결 해제, 로그인 경유 주소 보존.

main의 `Android release AAB`가 기존 업로드 키로 `Sikbi-1.2.0.aab`를 생성한다. Play Console에 AAB를 올린 뒤 기존 설치에서 업데이트하여 실기기로 다음을 검증한다.

- 홈 화면 추가/삭제/크기 조절, 글자 확대, 기록 버튼, 연결 과정
- 오늘 지출·주기 잔액·이월/예산 초과·날짜 변경
- 식비 등록/수정/삭제 후 갱신, 오프라인/복구
- 로그아웃·계정 교체·장부 탈퇴·비밀번호 변경 후 금액 숨김 및 재연결
- Chrome DAL 검증 성공/미지원 브라우저 대체 갱신

공식 문서: [앱 위젯 갱신](https://developer.android.com/develop/ui/views/appwidgets/advanced), [TWA postMessage](https://developer.chrome.com/docs/android/post-message-twa).
