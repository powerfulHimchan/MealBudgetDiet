# Sikbi Android 테스트 앱

이 프로젝트는 `https://sikbi.app` PWA를 여는 Trusted Web Activity(TWA) 앱입니다.

- 패키지 ID: `app.sikbi`
- 최소 API: 24 (Android 7.0 이상, Play 자동 보호 요구사항)
- 대상 API: 36
- 새 버전: `1.0.2` (`versionCode 3`)

## 네이비 그라데이션 시작 화면

중앙 영수증·숟가락·포크 로고와 하단 Sikbi 문구를 네트워크 연결 전에 네이티브로 렌더링합니다.
브라우저가 splash transfer를 지원하면 같은 화면을 전달하여 웹 로딩 중에도 유지합니다.
지원하지 않는 브라우저나 전송 실패 시 원래 URL을 열며, 전송 준비는 최대 2초로 제한합니다.
의도적으로 표시 시간을 늘리지 않습니다. 기존 로그인과 초대 URL, 알림 링크는 browser-helper가 처리합니다.
Android 12 이상에서는 OS의 단색 시작 화면을 거친 뒤 그라데이션 화면으로 이어집니다.

`Android checks`는 Android 빌드, lint, 화면 비율별 렌더링과 링크 회귀 검사를 실행합니다.
실제 기기의 실행·뒤로가기·오프라인·링크 진입 확인도 Play 내부 테스트에서 진행하세요.

## Play 업데이트

main 병합 후 `Android release AAB`가 기존 업로드 키로 서명한 `Sikbi-1.0.2.aab`를 생성합니다.
`Sikbi-1.0.2-play-bundle` artifact에는 AAB, 한국어 출시 노트, SHA-256 체크섬이 포함됩니다.
기존 키를 새로 만들거나 바꾸지 않습니다. Play 업로드 전 기존 모든 트랙에서 가장 높은 versionCode가
3 미만인지 확인해야 합니다. 이미 3 이상을 올렸다면 두 버전 설정 파일의 versionCode를 높여 다시 빌드하세요.
Play Console에서 Sikbi를 선택해 해당 트랙의 새 버전 만들기에서 AAB를 업로드하고 출시 노트를 입력합니다.
기존 버전에서 업데이트한 상태로 확인한 후 프로덕션 검토에 제출합니다.

## 테스트 APK

GitHub Actions의 `Android test APK` 워크플로가 debug APK를 빌드합니다. 워크플로 실행 결과의
`Sikbi-test-apk` artifact를 내려받아 압축을 풀고 `Sikbi-test.apk`를 Android 기기에
설치합니다.

debug APK는 테스트 전용이며 Google Play 제출에는 사용하지 않습니다.

## Play 내부 테스트 AAB

`Android release AAB` 워크플로는 다음 GitHub Actions secrets가 등록된 경우에만
업로드 키로 서명한 AAB를 생성합니다.

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

키와 비밀번호는 저장소에 커밋하지 않습니다.
