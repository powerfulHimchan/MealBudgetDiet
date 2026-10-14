# Android 앱 푸시 준비 및 검증

Sikbi는 `app.sikbi` TWA에서 기존 Web Push를 Android 알림으로 전달한다.
현재 알림은 예산 사용률 기준 이상이면서 예상 주기 종료 지출이 예산을 초과할 때 주기당 한 번이다.
이 경로에는 Firebase 프로젝트나 FCM 네이티브 SDK 추가가 필요하지 않다.

## 운영 설정

- `sikbi-api`: 기존 VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT를 유지한다.
  기존 구독을 유지하려면 키를 임의로 재발급하지 않는다. PUSH_DISPATCH_ENABLED=true로 발송한다.
- `sikbi-web`: ANDROID_APP_SHA256_FINGERPRINTS에 설치된 앱의 서명 인증서 SHA-256을 등록한다.
  `AA:BB:...` 형식이며 여러 지문은 쉼표로 구분한다. 지문은 공개 정보다.
- Play 설치 앱은 Play Console 앱 서명 키 인증서를 사용한다. 업로드 키 인증서와 다를 수 있다.
  APK 직접 설치는 해당 APK를 실제로 서명한 인증서를 사용한다. 일회성 CI debug 인증서를
  운영 신뢰 목록에 임의로 추가하지 않는다.
- `https://sikbi.app/.well-known/assetlinks.json`은 지문 등록 후 로그인 없이 200 JSON을 반환한다.
  설정이 비었거나 잘못된 형식이면 503이며 임의의 인증서를 신뢰하지 않는다.
- 앱에는 POST_NOTIFICATIONS, DelegationService, NotificationPermissionRequestActivity,
  sikbi.app에 대한 autoVerify intent filter가 이미 있다.

## 실제 기기 확인

1. Android 설정에서 Sikbi 알림을 허용한다. Chrome으로 접속 중이면 사이트 알림 권한도 확인한다.
2. 앱의 설정 → 푸시 알림 → 이 기기 알림 활성화를 누른다.
3. 활성 표시가 된 뒤 테스트 알림 보내기를 누른다. 테스트는 현재 기기 구독 하나에만 전송된다.
4. 앱을 닫은 상태에서도 알림이 도착하는지 확인한다. 알림을 누르면 푸시 설정 화면이 열린다.
5. 잠금 화면, 앱 알림 이름/아이콘, 재실행 후 활성 유지, 알림 해제 후 미수신을 확인한다.
6. 로그아웃하면 브라우저 구독을 먼저 해제한다. 계정 전환 후에는 새 계정에서 다시 활성화한다.

서버 수락 응답만으로 실제 폰 수신을 완료로 판단하지 않는다. Android 절전 모드와
네트워크 상태에 따라 전달이 지연될 수 있다. 예산을 수정하거나 운영 식비를 추가할 필요 없이
테스트 알림으로 전달 경로를 확인할 수 있다.

## 자동 검증

백엔드 통합 테스트는 기기 소유권, 로그인/CSRF, 사용자별 호출 제한, 설정 누락, 만료 처리를
검증한다. 프런트엔드는 구독 등록/재시도/해제, 잘못된 활성 표시 방지, 테스트 응답,
Android 권한 안내, 로그아웃 전 기기 구독 해제를 검증한다.

참고: [Chromium TWA 알림 위임](https://chromium.googlesource.com/chromium/src/+/HEAD/chrome/android/java/src/org/chromium/chrome/browser/browserservices/permissiondelegation/README.md),
[Android 웹사이트 연결 설정](https://developer.android.com/training/app-links/configure-assetlinks).
