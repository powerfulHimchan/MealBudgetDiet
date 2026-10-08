# Sikbi GPT API 연결

백엔드 `OpenAiGateway`가 OpenAI Responses API (`POST https://api.openai.com/v1/responses`)를 호출한다. 후속 자연어 식비 입력, 영수증 인식, 식비 분석에서 재사용할 연결 기반이다. 이번 변경은 관리자용 설정 조회와 고정 문구 연결 테스트만 제공한다. 사용자 데이터는 자동 전송하지 않는다.

## Railway 설정

OpenAI API 프로젝트에서 키를 생성하고 **Railway `sikbi-api` → Variables**에 아래 값을 등록한다. Git 저장소, 프론트엔드, Android 앱, `NEXT_PUBLIC_*` 변수에는 키를 넣지 않는다.

```dotenv
OPENAI_ENABLED=true
OPENAI_API_KEY=<OpenAI 프로젝트에서 생성한 키>
OPENAI_MODEL=gpt-4.1-mini
```

OpenAI API 프로젝트의 과금 설정 및 모델 접근 권한이 필요하다. API 사용은 ChatGPT 구독과 별도 과금이다. 키 등록만으로 연결 성공을 보장하지 않으며 연결 테스트 결과를 확인해야 한다.

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `OPENAI_ENABLED` | `false` | 연결 활성화 여부 |
| `OPENAI_API_KEY` | 빈 문자열 | 서버 전용 API 키 |
| `OPENAI_MODEL` | `gpt-4.1-mini` | Responses API를 지원하고 프로젝트에서 접근 가능한 모델 |
| `OPENAI_CONNECT_TIMEOUT` | `5s` | 연결 타임아웃, 1~120초 |
| `OPENAI_REQUEST_TIMEOUT` | `30s` | 요청 타임아웃, 1~120초 |
| `OPENAI_MAX_OUTPUT_TOKENS` | `512` | 출력 토큰 상한, 16~4096 |

키가 없거나 비활성화되어도 기존 서비스는 실행된다. Docker Compose에서도 동일한 변수를 `.env`에 설정한다.

## 관리자 API

두 API는 `SERVICE_ADMIN` 로그인 세션이 필요하다. 일반 회원과 장부 관리자만의 권한으로는 사용할 수 없다. POST에는 기존 CSRF 검사도 적용된다.

### GET `/api/v1/admin/ai/status`

```json
{"enabled":true,"configured":true,"ready":true,"model":"gpt-4.1-mini"}
```

`configured`는 키 문자열이 설정됐다는 의미이고 `ready`는 활성화와 키 설정 조건을 충족했다는 의미다. **키 유효성·모델 권한·잔액·외부 연결 성공을 보장하지 않는다.** 이 조회는 OpenAI를 호출하지 않는다.

### POST `/api/v1/admin/ai/connection-test`

본문 없이 고정 문구를 OpenAI에 전송한다. 성공 응답 예시:

```json
{"model":"gpt-4.1-mini-2025-04-14","text":"Sikbi AI 연결 성공","inputTokens":25,"outputTokens":8}
```

텍스트와 토큰 수는 실제 응답에 따라 달라진다. **테스트도 API 비용이 발생한다.** 서버 프로세스당 1분에 최대 5회이며 외부 호출 실패도 횟수에 포함한다. 제한은 재시작 시 초기화되고 여러 인스턴스 사이에서 공유하지 않는다. 사용자 AI 기능을 공개할 때는 별도의 사용자별/전체 사용량 제한을 추가해야 한다.

서비스 관리자로 Sikbi에 로그인한 뒤 브라우저 개발자도구 Console에서 확인한다:

```javascript
const statusResponse = await fetch('/api/v1/admin/ai/status');
console.log(statusResponse.status, await statusResponse.json());

const csrfResponse = await fetch('/api/v1/auth/csrf');
const csrf = await csrfResponse.json();
const testResponse = await fetch('/api/v1/admin/ai/connection-test', {
  method: 'POST',
  headers: { [csrf.headerName]: csrf.token },
});
console.log(testResponse.status, await testResponse.json());
```

## 실패 응답

기존 ProblemDetail 형식의 `code`로 원인을 구분한다. 외부 오류 본문, 입력 문장, API 키는 반환하지 않는다.

| 코드 | HTTP | 조치 |
|---|---|---|
| `AI_DISABLED` | 503 | `OPENAI_ENABLED=true` 등록 |
| `AI_NOT_CONFIGURED` | 503 | 서버에 API 키 등록 |
| `AI_PROVIDER_AUTH_FAILED` | 502 | 키 및 프로젝트 권한 확인 |
| `AI_PROVIDER_LIMITED` | 503 | 잔액·사용 한도·호출 제한 확인 |
| `AI_PROVIDER_REQUEST_FAILED` | 502 | 모델 이름·권한·요청 설정 확인 |
| `AI_PROVIDER_UNAVAILABLE` | 502 | 외부 장애 또는 예상하지 못한 HTTP 상태 확인 |
| `AI_TIMEOUT` | 504 | 외부 지연·요청 시간 설정 확인 |
| `AI_CONNECTION_FAILED` | 502 | 서버 외부 통신 확인 |
| `AI_INTERRUPTED` | 503 | 서버 요청 중단 상태 확인 |
| `AI_INCOMPLETE_RESPONSE` | 502 | 출력 토큰 상한·선택 모델 확인 |
| `AI_EMPTY_RESPONSE` / `AI_INVALID_RESPONSE` | 502 | 거절·빈 텍스트·응답 형식 확인 |
| `AI_TEST_RATE_LIMITED` | 429 | 1분 제한 구간이 지난 뒤 재시도 |

## 후속 기능에서 사용

애플리케이션 서비스에 `OpenAiGateway`를 주입하고 `generate(instructions, input)`을 호출하면 `AiTextResult`를 받는다. 입력과 지시는 각각 1~4000자로 제한한다. 브라우저가 모델이나 지시 문구를 임의로 정하는 공개 프록시 API는 제공하지 않는다.

외부 요청은 `store:false`와 출력 토큰 상한을 포함한다. `store:false`는 Responses API 응답 저장을 비활성화하며 모든 종류의 보존을 없애는 설정은 아니다. 자동 재시도와 리다이렉트는 하지 않는다. HTTP 클라이언트를 재사용하고, 외부 호출 중 DB 트랜잭션은 열지 않는다.

## 검증

```bash
cd backend
./gradlew test --tests 'com.mealbudgetdiet.ai.*' --no-daemon
```

테스트는 로컬 HTTP 모의 서버와 MockMvc를 사용해 실제 키나 유료 호출이 필요 없다. 운영 키로 연결 테스트에 성공해야 실제 OpenAI 연결이 확인된다.

공식 문서: [Responses API](https://developers.openai.com/api/reference/python/resources/responses/methods/create), [GPT-4.1 Mini](https://developers.openai.com/api/docs/models/gpt-4.1-mini), [API 데이터 보존](https://developers.openai.com/api/docs/guides/your-data).
