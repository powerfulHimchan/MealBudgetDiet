# Android 홈 화면 위젯 준비

현재 저장소는 `https://sikbi.app`의 PWA와 Bubblewrap 기반 TWA 출시 가이드만 포함한다. 아직 `android/` 네이티브 프로젝트와 서명 키가 없어 이 커밋만으로 홈 화면에 설치할 수 있는 위젯이나 AAB가 만들어지는 것은 아니다. 패키지 ID는 `app.sikbi`로 유지한다.

## 첫 위젯의 범위

- 장부의 현재 월간/주간 기간, 전체 예산, 사용 금액, 남은 금액 및 사용률을 표시한다.
- `식비 등록`을 누르면 앱의 `https://sikbi.app/expenses?new=1`을 연다. 장부 홈은 `https://sikbi.app/`이다.
- 위젯은 잠금 화면이나 다른 사람이 보는 홈 화면에 금액을 노출할 수 있으므로 사용자가 직접 추가했을 때만 표시한다. 로그아웃·장부 탈퇴·인증 만료 시 금액을 지우고 로그인 안내를 보여준다. 마지막 사용자/장부의 값을 다른 계정에 재사용하지 않는다.
- 사진·상호명·참여자 이름은 위젯에 표시하지 않는다.

## 위젯용 응답

`GET /api/v1/widget/summary`는 로그인 및 활성 장부 참여가 필요한 읽기 전용 API다. 기존 대시보드와 같은 서버 계산을 사용하되 최근 내역과 개인정보는 응답에 포함하지 않고 `Cache-Control: no-store`를 보낸다.

```json
{
  "cycleUnit": "WEEKLY",
  "period": { "from": "2026-09-10", "to": "2026-09-16" },
  "budget": 200000,
  "spent": 85000,
  "remaining": 115000,
  "usageRate": 42.50,
  "status": "NORMAL"
}
```

금액은 원 단위 정수이며 `remaining`은 초과 시 음수가 될 수 있다. 서버 시간대는 `Asia/Seoul`이고, `cycleUnit`은 `MONTHLY`/`WEEKLY`, `status`는 `NORMAL`/`WARNING`/`EXCEEDED`다. 실제 데이터는 사용자 장부에 따라 달라진다.

## 네이티브 프로젝트가 생긴 뒤

1. [Play 출시 가이드](play-store-release.md)대로 Bubblewrap 프로젝트를 `android/`에 생성하고 패키지·도메인과 Play 앱 서명 지문 검증을 끝낸다. Android Studio 프로젝트에 `AppWidgetProviderInfo`, `AppWidgetProvider` 및 홈 화면용 `RemoteViews` 레이아웃을 추가한다.
2. TWA 브라우저의 웹 세션 쿠키를 위젯 프로세스가 직접 가져올 수 있다고 가정하지 않는다. 네이티브 인증 또는 서버에서 별도로 발급·철회할 수 있는 위젯 전용 권한 흐름을 설계하고, 비밀은 Android 보안 저장소에만 보관한다. 이 인증 연동 전에는 위젯에 금액 대신 앱 열기/식비 등록 바로가기만 제공한다.
3. 위젯 갱신은 설치·앱 복귀·식비 변경에 맞춰 요청하고, 네트워크 실패에는 마지막 사용자 값 대신 안전한 비공개 상태를 보여준다. 로그인 해제 및 계정 전환 시 저장된 위젯 데이터와 권한도 제거한다.
4. Android 에뮬레이터와 실기기에서 작은 화면·글자 확대·세로/가로, 오프라인·만료 세션·다른 장부 전환, 버튼의 앱 딥링크를 검증한다. Play 테스트 트랙에 AAB를 올리기 전 별도로 확인한다.

공식 참고: [Android 앱 위젯 구성](https://developer.android.com/develop/ui/views/appwidgets), [Trusted Web Activity 빠른 시작](https://developer.chrome.com/docs/android/trusted-web-activity/quick-start).
