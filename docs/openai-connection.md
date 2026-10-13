# Sikbi GPT API 연결

백엔드 `OpenAiGateway`가 OpenAI Responses API (`POST https://api.openai.com/v1/responses`)를 호출한다. 관리자용 연결 테스트는 고정 문구만 전송하며, 소비 분석은 사용자가 버튼을 누를 때 해당 주기의 집계와 지출내역을 전송한다. 화면 조회만으로 사용자 데이터를 OpenAI에 자동 전송하지 않는다.

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

서비스 관리자로 Sikbi에 로그인한 뒤 **설정 → 계정 설정 → AI 연결 확인** (`/settings/ai`)에서 등록 상태를 조회하고 **연결 테스트**를 누른다. 화면 진입은 설정만 조회하며 OpenAI를 호출하지 않는다. 버튼을 눌러야 실제 테스트가 실행되며, 성공한 응답 모델·문구·토큰 수 또는 서버 오류 안내가 표시된다. API 키 값은 표시하지 않는다.

브라우저 개발자도구 Console에서도 확인할 수 있다:

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

애플리케이션 서비스에 `OpenAiGateway`를 주입하고 `generate(instructions, input)`을 호출하면 `AiTextResult`를 받는다. 입력은 1~32000자, 지시는 1~4000자로 제한한다. 브라우저가 모델이나 지시 문구를 임의로 정하는 공개 프록시 API는 제공하지 않는다.

외부 요청은 `store:false`와 출력 토큰 상한을 포함한다. `store:false`는 Responses API 응답 저장을 비활성화하며 모든 종류의 보존을 없애는 설정은 아니다. 자동 재시도와 리다이렉트는 하지 않는다. HTTP 클라이언트를 재사용하고, 외부 호출 중 DB 트랜잭션은 열지 않는다.

## 검증

```bash
cd backend
./gradlew test --tests 'com.mealbudgetdiet.ai.*' --no-daemon
```

테스트는 로컬 HTTP 모의 서버와 MockMvc를 사용해 실제 키나 유료 호출이 필요 없다. 운영 키로 연결 테스트에 성공해야 실제 OpenAI 연결이 확인된다.

공식 문서: [Responses API](https://developers.openai.com/api/reference/python/resources/responses/methods/create), [GPT-4.1 Mini](https://developers.openai.com/api/docs/models/gpt-4.1-mini), [API 데이터 보존](https://developers.openai.com/api/docs/guides/your-data).

## 주기별 소비 분석

통계의 종료된 단일 예산 주기에 `소비 분석` 버튼이 표시된다. `POST /api/v1/statistics/analysis`에 `from`, `to`를 보내면 한국어 약 300자(280~320자, 최대 320자)의 분석을 생성한다. 장부의 활성 멤버가 요청할 수 있으며 CSRF 토큰이 필요하다. 진행 중인 주기, 일부 날짜, 여러 주기를 묶은 기간은 `ANALYSIS_CYCLE_REQUIRED`로 거절한다.

입력은 기간·총지출·예산·이전 주기 대비 증감·상위 카테고리 비중·최다 지출일 등의 집계와 해당 주기의 개별 지출내역(사용일·금액·카테고리·상호명·메모)을 포함한다. 현재 활성 장부와 요청 주기의 내역만 조회하며, 회원 ID·이메일·표시 이름이나 사진은 별도 입력으로 추가하지 않는다. 사용자가 상호명·메모에 입력한 내용은 그대로 분석 데이터로 사용한다. 이 문자열 안의 문구를 지시로 따르지 않도록 모델 지침에 명시한다. 기존 Responses API 연결의 `store:false` 및 출력 토큰 상한을 적용한다.

집계는 전체 내역 기준이며, 상세내역은 고액순 최대 200건과 직렬화된 전체 입력 32000자 이내로 제한한다. 상호명·메모나 JSON을 중간에 자르지 않고 완전한 기록 단위로 포함한다. `지출기록건수`, `상세내역포함건수`, `상세내역일부만포함`을 함께 보내 일부 기록으로 전체 상호·메모를 분석했다고 단정하지 않도록 한다. 새 분석에 적용하며 기존 저장 결과는 삭제하거나 자동 재생성하지 않는다.

종료된 단일 주기에 식비 기록이 없으면 통계 조회는 `eligible:false`, `status:NO_EXPENSES`를 반환한다. 화면은 “이 주기에는 등록된 소비 기록이 없어 분석할 수 없습니다.”를 표시하며 분석 버튼은 숨긴다. 이 상태를 AI로 생성하거나 DB 분석 결과로 저장하지 않는다. 직접 분석 API를 호출해도 `ANALYSIS_NO_EXPENSES`(400)로 거절하며 OpenAI를 호출하지 않는다. 현재 기록이 없는 주기는 기존 저장 분석이 있어도 고정 안내를 표시한다.

`spending_analyses`에 장부 ID와 주기의 시작·종료일을 고유 키로 결과, 분석 시각, 응답 모델, 토큰 사용량을 저장한다. 통계 조회의 `spendingAnalysis`는 저장된 내용을 함께 반환하며, 같은 주기를 다시 POST해도 저장된 결과를 반환한다. 저장된 분석을 조회할 때는 OpenAI 호출이 없다. 결과는 분석 시점의 통계이며 이후 식비 수정으로 자동 재생성되지 않는다.

DB에서 생성 중인 주기를 선점해 동시 호출을 막는다. 생성 중에는 `ANALYSIS_IN_PROGRESS`(409)를 반환하며 화면의 `결과 확인`으로 저장 결과를 다시 조회할 수 있다. 실패한 요청과 5분이 지난 중단 요청은 버튼으로 다시 시도할 수 있다. 자동 재시도는 하지 않으며 OpenAI 호출 중 DB 트랜잭션을 유지하지 않는다.

## 홈의 소비 중간평가

한국 시간으로 현재 주기의 중간일부터 홈에 처음 접속하면 `POST /api/v1/dashboard/mid-cycle-analysis`가 CSRF 토큰과 현재 주기의 `from`, `to`를 받아 중간평가를 생성한다. 31일 주기는 16일째, 30일은 15일째, 7일 주기는 4일째부터 가능하며 사용자 지정 월·주 시작일도 반영한다. 중간일 이후 늦게 접속하면 최초 접속일까지의 기록을 분석한다. 클라이언트 날짜를 신뢰하지 않고 서버의 서울 날짜로 판정한다.

입력에는 최초 분석일까지의 집계·카테고리·상호명·메모와 전체 주기 예산, 같은 추이 계산식을 사용한 마지막 날 예상 지출을 포함한다. 미래 날짜 기록은 제외한다. 결과는 약 100자(90~110자, 최대 110자)이며 홈의 예상 소비액 카드 하단에 분석 기준일과 함께 표시한다. 홈 초기 조회는 AI를 기다리지 않으며 분석 중·실패에도 기존 홈 기능을 사용할 수 있다.

`mid_cycle_analyses`는 장부 ID와 전체 주기 시작·종료일을 키로 결과·분석 기준일·분석 시각·모델·토큰 사용량을 저장한다. 저장된 결과는 멤버 모두에게 공유되며 이후 날짜나 식비 변경에도 재생성하지 않는다. 새 주기는 별도 결과를 만든다. 기존 `spending_analyses`의 지난 주기 분석과는 독립적이며 기존 분석 데이터는 변경하지 않는다.

홈 GET의 `midCycleAnalysis`와 별도의 `GET /api/v1/dashboard/mid-cycle-analysis?from=...&to=...`는 저장 상태만 조회한다. 동시 요청은 DB의 5분 선점으로 AI 호출을 하나로 제한하며 `PROCESSING` 상태를 반환한다. 다른 멤버가 생성 중이면 화면은 읽기 조회만 반복해 결과를 확인한다. 실패 시 명시적인 다시 시도 버튼을 제공하고 자동 호출 반복은 하지 않는다. 기록이 없으면 `NO_EXPENSES`를 반환하며 AI를 호출하거나 빈 분석을 저장하지 않는다.
