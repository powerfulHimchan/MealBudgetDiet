# Sikbi Android 테스트 앱

이 프로젝트는 `https://sikbi.app` PWA를 여는 Trusted Web Activity(TWA) 앱입니다.

- 패키지 ID: `app.sikbi`
- 최소 API: 23
- 대상 API: 36
- 테스트 버전: `0.1.0-test` (`versionCode 1`)

## 테스트 APK

GitHub Actions의 `Android test APK` 워크플로가 debug APK를 빌드합니다. 워크플로 실행 결과의
`Sikbi-test-apk` artifact를 내려받아 압축을 풀고 `Sikbi-test.apk`를 Android 기기에
설치합니다.

debug APK는 테스트 전용이며 Google Play 제출에는 사용하지 않습니다.
