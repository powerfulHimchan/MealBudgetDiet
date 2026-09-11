# Google Play 출시 가이드

- 기준일: 2026-09-10
- 앱 이름: `식비(Sikbi)`
- Android 패키지 ID: `app.sikbi`
- 배포 방식: 운영 PWA + Trusted Web Activity(TWA)
- 생성 도구: GoogleChromeLabs Bubblewrap
- 목표 API: Android 16, API 36 이상

패키지 ID는 Play Console에 앱을 만든 뒤 변경할 수 없다고 보고 관리한다. Android 프로젝트와 서명 키는 운영 도메인이 확정된 뒤 생성한다.

## 1. 현재 준비된 항목

- Web App Manifest와 서비스 워커
- 192px, 512px, maskable, monochrome PNG 아이콘
- 공개 개인정보처리방침: `/privacy`
- 공개 계정 삭제 안내: `/account-deletion`
- 앱 내부 계정 삭제 경로: `/settings/account`
- 탈퇴 계정의 이메일·표시 이름·비밀번호·프로필 연결 제거
- 실제 PostgreSQL, MinIO, 백엔드와 프론트엔드를 연결한 핵심 E2E

## 2. 출시 전 반드시 확정할 값

| 항목 | 상태 | 반영 위치 |
|---|---|---|
| 운영 HTTPS 도메인 | 확정: `https://sikbi.app` | Cloudflare DNS, `PUBLIC_BASE_URL`, TWA `host` |
| 개인정보 문의 이메일 | 미정 | `/privacy`, Play Console |
| 호스팅·DB 사업자 | 확정: Cloudflare DNS + Railway App/PostgreSQL | 개인정보처리방침, Data safety |
| Object Storage·SMTP·백업 보존 기간 | 미정 | 개인정보처리방침, 운영 정책 |
| Play App Signing SHA-256 지문 | 첫 AAB 등록 후 확인 | `/.well-known/assetlinks.json` |
| Play 개인 계정 생성일 | 확인 필요 | 12명·14일 비공개 테스트 의무 판단 |

연락 이메일, Object Storage·SMTP 사업자와 백업 정책이 확정되기 전의 개인정보처리방침은 개발 초안이다. Play 심사에는 실제 사업자, 이전 여부, 연락 이메일과 보존 기간을 반영한 버전을 사용한다.

## 3. 운영 PWA 배포

1. 운영 웹은 `sikbi.app`, 운영 API는 `api.sikbi.app`을 사용한다.
2. 프론트엔드, 백엔드, PostgreSQL, S3 호환 Object Storage와 SMTP를 운영 환경에 배포한다.
3. TLS 인증서를 적용하고 HTTP 요청을 HTTPS로 리다이렉트한다.
4. `PUBLIC_BASE_URL=https://sikbi.app`를 설정한다.
5. 아래 URL이 인증 없이 정상 응답하는지 확인한다.

```text
https://sikbi.app/manifest.webmanifest
https://sikbi.app/privacy
https://sikbi.app/account-deletion
https://sikbi.app/icons/icon-512.png
https://sikbi.app/icons/icon-maskable-512.png
```

6. Chrome DevTools의 Application 탭과 Lighthouse로 manifest, service worker, HTTPS, 설치 가능성을 확인한다.

## 4. Bubblewrap Android 프로젝트 생성

Bubblewrap는 Google의 TWA용 CLI다. 운영 manifest가 외부에서 접근 가능해진 뒤 저장소 루트의 `android/`에 생성한다.

```bash
npx --yes @bubblewrap/cli@latest init \
  --manifest="https://sikbi.app/manifest.webmanifest" \
  --directory="android"
```

초기화 질문에는 다음 값을 사용한다.

| 질문 | 값 |
|---|---|
| Application name | `식비` |
| Launcher name | `식비` |
| Package ID | `app.sikbi` |
| Start URL | `/` |
| Display mode | `standalone` |
| Orientation | `any` |
| Theme color | `#080d18` |
| Background color | `#f2f6fc` |
| Notifications | enabled |
| Fallback | `customtabs` |

생성 직후 아래를 수행한다.

1. 사용한 Bubblewrap 버전을 확인하고 Android 프로젝트의 개발 의존성으로 같은 버전을 고정한다.
2. `twa-manifest.json`의 `packageId`, `host`, `iconUrl`, `maskableIconUrl`, `enableNotifications`를 검토한다.
3. Gradle 설정의 `targetSdkVersion` 또는 `targetSdk`가 36 이상인지 확인한다.
4. keystore, `*.jks`, `*.keystore`, Play 서비스 계정 JSON과 비밀번호를 `.gitignore`에 추가한다.
5. 생성된 프로젝트는 Android Studio에서도 열어 빌드할 수 있다.

Bubblewrap 갱신은 자동으로 무조건 적용하지 않는다. Dependabot이 새 버전을 알리게 하고, 변경 로그와 Android Browser Helper 호환성을 확인한 뒤 내부 테스트 트랙에서 검증한다.

## 5. 서명과 Digital Asset Links

1. 별도의 업로드 키를 생성하고 안전한 비밀 저장소와 오프라인 백업에 보관한다.
2. Play App Signing을 사용해 첫 AAB를 내부 테스트 트랙에 업로드한다.
3. Play Console의 앱 서명 키 인증서 SHA-256 지문을 확인한다. 업로드 키 지문과 혼동하지 않는다.
4. Bubblewrap에 Play 앱 서명 지문을 추가하고 Asset Links 파일을 생성한다.

```bash
cd android
bubblewrap fingerprint add "<PLAY_APP_SIGNING_SHA256>" --name="play-app-signing"
bubblewrap fingerprint generateAssetLinks --output="assetlinks.json"
```

5. 생성 파일을 운영 웹의 `/.well-known/assetlinks.json`에 배포한다.
6. Play에서 설치한 앱을 열어 주소 표시줄 없는 TWA로 동작하는지 확인한다. 검증에 실패하면 Custom Tab으로 열리므로 인증서 지문, package ID, HTTPS 응답과 Content-Type을 다시 확인한다.

로컬 서명 빌드도 TWA로 검증하려면 로컬 서명 지문을 Asset Links에 추가할 수 있다. 인증서 SHA-256 지문은 공개 검증 정보이지만 keystore와 비밀번호는 비밀이다.

## 6. AAB 생성과 기술 검증

```bash
cd android
bubblewrap build
```

Bubblewrap는 서명된 `app-release-bundle.aab`을 생성한다. 제출 전 다음을 확인한다.

- target API 36 이상
- package ID `app.sikbi`
- version code 증가
- Android 13 이상에서 알림 권한 허용·거부 흐름
- 로그인, 이미지 업로드, Push, 오프라인 안내, 뒤로 가기
- 휴대폰·태블릿·회전·다크 시스템 바 표시
- Play App Signing 빌드의 Digital Asset Links 검증
- Pre-launch report의 crash, ANR, 접근성 경고

## 7. Play Console 입력 체크리스트

### 앱 콘텐츠

- 개인정보처리방침 URL: `https://sikbi.app/privacy`
- 계정 삭제 URL: `https://sikbi.app/account-deletion`
- App access: 심사 전용 관리자 또는 참여자 계정과 로그인 절차 제공
- Ads: 광고 없음
- Target audience: 실제 배포 연령을 선택하고, 어린이 대상이 아니라면 스토어 설명과 설정을 일치시킴
- Content rating 설문 완료
- Financial features 선언 완료: 식비·예산 기록 기능이 있음을 밝히고 결제, 대출, 투자, 암호화폐 기능은 없다고 정확히 답변

심사 계정의 비밀번호와 초대 코드는 저장소나 스토어 설명에 넣지 않고 Play Console의 App access 영역에만 입력한다.

### Data safety 초안

최종 답변은 실제 운영 인프라와 SDK를 확인한 뒤 제출한다.

| Play 데이터 유형 | 현재 사용 | 목적 | 필수 여부 |
|---|---|---|---|
| Personal info / Name | 표시 이름 | 계정·공유 장부 | 가입 시 필수 |
| Personal info / Email address | 로그인 이메일 | 계정·인증 | 필수 |
| Financial info / Other financial info | 식비·예산 | 핵심 기능·통계 | 필수 |
| Photos and videos / Photos | 영수증·프로필 사진 | 앱 기능 | 선택 |
| App activity 또는 User-generated content | 메모·상호·카테고리 | 공유 기록 | 항목별 선택 |

- 전송 중 HTTPS 암호화: 예
- 데이터 삭제 요청: 앱 내부와 외부 웹 모두 지원
- 제3자 공유 여부: 실제 SDK와 사업자 계약상 Play 정의를 확인한 뒤 확정
- 수집 여부: 사용자의 기기 밖 서버로 전송해 보관하므로 해당 유형은 일반적으로 수집으로 신고

## 8. 스토어 등록정보 초안

짧은 설명:

> 공유 식비를 함께 기록하고 예산 초과 위험과 기간별 통계를 확인하세요.

전체 설명:

> 식비(Sikbi)는 가족이나 가까운 사람과 하나의 식비 장부를 함께 관리하는 앱입니다. 식비를 빠르게 등록하고 최대 3장의 이미지를 첨부할 수 있습니다. 월 예산과 원하는 예산 시작일을 설정하고, 현재 소비 속도로 예산을 초과할 위험이 있을 때 알림을 받을 수 있습니다. 기간별 지출 추이와 카테고리 통계로 소비 흐름을 한눈에 확인하세요. 초대 코드, 참여자 역할, 카테고리와 Push 기준도 설정할 수 있습니다.

그래픽 산출물:

- 고해상도 앱 아이콘: 512×512 PNG
- Feature graphic: 1024×500 PNG
- 휴대전화 스크린샷: 대시보드, 식비 등록·이미지, 통계, 설정·알림 각 1장 이상
- 7인치·10인치 태블릿을 지원 대상으로 선택하면 해당 스크린샷도 준비

## 9. 개인 개발자 계정 테스트

개인 개발자 계정이 2023년 11월 13일 이후 생성되었다면 현재 정책상 최소 12명의 테스터가 14일 연속 opt-in한 비공개 테스트를 완료한 뒤 프로덕션 접근을 신청해야 한다. 계정 생성일을 Play Console에서 확인한다.

권장 순서는 내부 테스트 → 비공개 테스트 → 프로덕션이다. 비공개 테스트 중에는 주요 기능별 체크리스트, 기기/Android 버전, 발견한 문제, 반영한 개선을 기록해 프로덕션 접근 신청 답변과 포트폴리오에 활용한다.

## 10. 공식 참고 자료

- [Trusted Web Activity 빠른 시작](https://developer.chrome.com/docs/android/trusted-web-activity/quick-start)
- [Bubblewrap CLI](https://github.com/GoogleChromeLabs/bubblewrap/tree/main/packages/cli)
- [Google Play 목표 API 요구사항](https://developer.android.com/google/play/requirements/target-sdk)
- [앱 계정 삭제 요구사항](https://support.google.com/googleplay/android-developer/answer/13327111)
- [신규 개인 계정 테스트 요구사항](https://support.google.com/googleplay/android-developer/answer/14151465)
- [앱 심사 준비](https://support.google.com/googleplay/android-developer/answer/9859455)
