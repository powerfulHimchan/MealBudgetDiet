# Sikbi Android 테스트 앱

이 프로젝트는 `https://sikbi.app` PWA를 여는 Trusted Web Activity(TWA) 앱입니다.

- 패키지 ID: `app.sikbi`
- 최소 API: 23
- 대상 API: 36
- 첫 내부 테스트 버전: `1.0.0` (`versionCode 1`)

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
