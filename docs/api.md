# MealBudgetDiet REST API 설계

- 문서 버전: 1.1
- Base path: `/api/v1`
- Content-Type: `application/json`
- 오류 Content-Type: `application/problem+json`
- 인증: Secure HttpOnly 세션 쿠키
- 관련 문서: [requirements.md](requirements.md), [architecture.md](architecture.md), [erd.md](erd.md)

이 문서는 구현 전 API 계약 초안이다. 구현 단계에서는 이 내용을 기준으로 OpenAPI 3 명세를 함께 관리한다.

## 1. 공통 규칙

### 1.1 데이터 형식

| 데이터 | 형식 | 예시 |
|---|---|---|
| ID | UUID 문자열 | `7e9f6a3f-0e72-4ced-9b64-c1de64d235cb` |
| 날짜 | YYYY-MM-DD | `2026-09-08` |
| 월 | YYYY-MM | `2026-09` |
| 시각 | ISO 8601 | `2026-09-08T16:20:30+09:00` |
| 금액 | 원 단위 정수 | `12500` |
| 비율 | 소수점 숫자 | `82.35` |

### 1.2 인증 쿠키

로그인과 회원가입 성공 시 서버가 `MBD_SESSION` 쿠키를 설정한다.

브라우저 요청은 다음 설정을 사용한다.

```text
credentials: include
```

인증이 필요한 API에서 유효한 세션이 없으면 HTTP 401을 반환한다.

### 1.3 CSRF

쿠키 기반 인증을 사용하므로 POST, PUT, PATCH, DELETE 요청에는 CSRF 토큰이 필요하다.

```http
GET /api/v1/auth/csrf
X-XSRF-TOKEN: <token>
```

CSRF 토큰이 없거나 올바르지 않으면 HTTP 403을 반환한다.

### 1.4 오류 형식

```json
{
  "type": "https://mealbudgetdiet.app/problems/validation",
  "title": "Request validation failed",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "입력값을 확인해 주세요.",
  "instance": "/api/v1/expenses",
  "traceId": "01J...",
  "errors": [
    {
      "field": "amount",
      "reason": "금액은 0보다 커야 합니다."
    }
  ]
}
```

### 1.5 주요 상태 코드

| 상태 | 의미 |
|---:|---|
| 200 | 조회·수정 성공 |
| 201 | 생성 성공 |
| 204 | 응답 본문 없는 성공 |
| 400 | 형식 또는 값 검증 실패 |
| 401 | 로그인 필요 |
| 403 | 권한 또는 CSRF 검증 실패 |
| 404 | 리소스 없음 |
| 409 | 이메일 중복, 상태 충돌, optimistic lock 충돌 |
| 410 | 취소되거나 사용할 수 없는 초대 코드 |
| 429 | 요청 횟수 제한 초과 |

## 2. Endpoint 요약

### 2.1 Bootstrap과 인증

| Method | Path | 인증 | 설명 |
|---|---|:---:|---|
| GET | `/bootstrap/status` | X | 최초 관리자 생성 가능 여부 |
| POST | `/bootstrap/admin` | bootstrap token | 최초 관리자와 장부 생성 |
| GET | `/auth/csrf` | X | CSRF 토큰 발급 |
| POST | `/auth/register` | X | 초대 코드 기반 회원가입 |
| POST | `/auth/login` | X | 로그인 |
| POST | `/auth/logout` | O | 로그아웃 |
| GET | `/auth/me` | O | 현재 사용자 조회 |
| POST | `/auth/password-reset-requests` | X | 재설정 이메일 요청 |
| POST | `/auth/password-resets` | X | 새 비밀번호 설정 |

### 2.2 장부·회원·초대

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/ledger` | MEMBER | 현재 공용 장부 조회 |
| GET | `/members` | MEMBER | 참여자 목록 |
| PATCH | `/members/{memberId}/role` | ADMIN | 관리자 지정·해제 |
| POST | `/account/withdrawal` | MEMBER | 본인 탈퇴 또는 마지막 관리자 장부 종료 |
| GET | `/invitations` | MEMBER | 활성·취소 초대 목록 |
| POST | `/invitations` | MEMBER | 초대 코드 발급 |
| DELETE | `/invitations/{invitationId}` | MEMBER | 초대 코드 취소 |

### 2.3 식비·카테고리·예산

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/expenses` | MEMBER | 식비 목록과 검색 |
| POST | `/expenses` | MEMBER | 식비 등록 |
| GET | `/expenses/{expenseId}` | MEMBER | 식비 상세 |
| PUT | `/expenses/{expenseId}` | MEMBER | 식비 전체 수정 |
| DELETE | `/expenses/{expenseId}` | MEMBER | 식비 영구 삭제 |
| GET | `/categories` | MEMBER | 카테고리 목록 |
| POST | `/categories` | ADMIN | 카테고리 추가 |
| PUT | `/categories/{categoryId}` | ADMIN | 카테고리 수정 |
| DELETE | `/categories/{categoryId}` | ADMIN | 카테고리 삭제 |
| GET | `/budgets/{yearMonth}` | MEMBER | 적용 예산 조회 |
| PUT | `/budgets/default` | ADMIN | 기본 월 예산 변경 |
| PUT | `/budgets/{yearMonth}` | ADMIN | 특정 월 예산 설정 |
| DELETE | `/budgets/{yearMonth}` | ADMIN | 특정 월 설정 제거 |

### 2.4 대시보드·통계·내보내기

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/dashboard` | MEMBER | 이번 달 대시보드 |
| GET | `/statistics` | MEMBER | 선택 기간 통계 |
| GET | `/expenses/export.csv` | MEMBER | 식비 CSV 다운로드 |

### 2.5 PWA 푸시

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/push/vapid-public-key` | MEMBER | Web Push 공개키 조회 |
| PUT | `/push-subscriptions` | MEMBER | 현재 기기 구독 등록·갱신 |
| DELETE | `/push-subscriptions/{subscriptionId}` | MEMBER | 현재 사용자 기기 구독 해제 |

## 3. Bootstrap과 인증 API

### 3.1 Bootstrap 상태

```http
GET /api/v1/bootstrap/status
```

응답:

```json
{
  "available": true
}
```

사용자가 한 명이라도 생성되면 `available`은 false가 된다.

### 3.2 최초 관리자 생성

```http
POST /api/v1/bootstrap/admin
X-Bootstrap-Token: <one-time-token>
```

요청:

```json
{
  "email": "owner@example.com",
  "password": "password",
  "displayName": "힘찬",
  "ledgerName": "우리집 식비",
  "defaultMonthlyBudget": 800000
}
```

성공: HTTP 201

보안 규칙:

- 사용자가 없는 경우에만 허용
- bootstrap token 불일치 시 404와 동일한 일반 응답 사용
- 성공 후 bootstrap 기능 비활성화
- 동시에 두 요청이 들어와도 한 명만 생성되도록 DB에서 직렬화

### 3.3 회원가입

```http
POST /api/v1/auth/register
```

요청:

```json
{
  "inviteCode": "MBD-...",
  "email": "member@example.com",
  "password": "password",
  "displayName": "사용자"
}
```

성공: HTTP 201과 로그인 세션 생성

```json
{
  "id": "user-uuid",
  "email": "member@example.com",
  "displayName": "사용자",
  "role": "MEMBER"
}
```

오류:

- `INVITATION_INVALID`
- `INVITATION_REVOKED`
- `EMAIL_ALREADY_EXISTS`

### 3.4 로그인

```http
POST /api/v1/auth/login
```

요청:

```json
{
  "email": "member@example.com",
  "password": "password"
}
```

성공 응답:

```json
{
  "id": "user-uuid",
  "email": "member@example.com",
  "displayName": "사용자",
  "role": "MEMBER"
}
```

이메일 또는 비밀번호가 틀리면 존재 여부를 구분하지 않고 `INVALID_CREDENTIALS`를 반환한다.

### 3.5 로그아웃

```http
POST /api/v1/auth/logout
```

성공: HTTP 204

현재 세션과 쿠키를 무효화한다.

### 3.6 현재 사용자

```http
GET /api/v1/auth/me
```

응답:

```json
{
  "id": "user-uuid",
  "email": "member@example.com",
  "displayName": "사용자",
  "role": "MEMBER"
}
```

### 3.7 비밀번호 재설정 요청

```http
POST /api/v1/auth/password-reset-requests
```

요청:

```json
{
  "email": "member@example.com"
}
```

성공: HTTP 202

계정 존재 여부와 관계없이 동일한 응답을 반환한다.

### 3.8 비밀번호 재설정

```http
POST /api/v1/auth/password-resets
```

요청:

```json
{
  "token": "raw-one-time-token",
  "newPassword": "new-password"
}
```

성공: HTTP 204

완료 후 해당 사용자의 기존 세션을 모두 종료한다.

## 4. 장부·회원 API

### 4.1 장부 조회

```http
GET /api/v1/ledger
```

응답:

```json
{
  "id": "ledger-uuid",
  "name": "우리집 식비",
  "defaultMonthlyBudget": 800000,
  "memberCount": 3,
  "currentUserRole": "ADMIN",
  "version": 2
}
```

### 4.2 참여자 목록

```http
GET /api/v1/members
```

응답:

```json
{
  "items": [
    {
      "id": "member-user-uuid",
      "displayName": "힘찬",
      "role": "ADMIN",
      "joinedAt": "2026-09-08T10:00:00+09:00"
    }
  ]
}
```

탈퇴 사용자는 기본 참여자 목록에 표시하지 않는다.

### 4.3 관리자 역할 변경

```http
PATCH /api/v1/members/{memberId}/role
```

요청:

```json
{
  "role": "ADMIN"
}
```

또는:

```json
{
  "role": "MEMBER"
}
```

마지막 ACTIVE ADMIN을 MEMBER로 변경하려는 요청은 HTTP 409 `LAST_ADMIN_REQUIRED`로 거절한다.

### 4.4 본인 탈퇴

```http
POST /api/v1/account/withdrawal
```

일반 참여자 또는 복수 관리자 중 한 명의 요청:

```json
{
  "password": "current-password"
}
```

마지막 관리자의 장부 종료 요청:

```json
{
  "password": "current-password",
  "confirmation": "우리집 식비 삭제"
}
```

성공: HTTP 204와 모든 현재 세션 종료

## 5. 초대 API

### 5.1 초대 목록

```http
GET /api/v1/invitations?status=ACTIVE
```

응답:

```json
{
  "items": [
    {
      "id": "invitation-uuid",
      "maskedCode": "MBD-****7K2P",
      "status": "ACTIVE",
      "useCount": 2,
      "createdAt": "2026-09-08T11:00:00+09:00",
      "lastUsedAt": "2026-09-08T12:00:00+09:00"
    }
  ]
}
```

원문 코드는 목록 API에서 다시 반환하지 않는다.

### 5.2 초대 코드 발급

```http
POST /api/v1/invitations
```

성공: HTTP 201

```json
{
  "id": "invitation-uuid",
  "code": "MBD-...",
  "joinUrl": "https://service.example.com/join?code=MBD-...",
  "createdAt": "2026-09-08T11:00:00+09:00"
}
```

원문 code와 joinUrl은 생성 응답에서만 제공한다.

### 5.3 초대 코드 취소

```http
DELETE /api/v1/invitations/{invitationId}
```

성공: HTTP 204

이미 취소된 코드에 다시 요청해도 HTTP 204를 반환한다.

## 6. 식비 API

### 6.1 식비 등록

```http
POST /api/v1/expenses
```

요청:

```json
{
  "amount": 18500,
  "spentOn": "2026-09-08",
  "categoryId": "category-uuid",
  "merchant": "동네마트",
  "memo": "주말 장보기"
}
```

성공: HTTP 201

```json
{
  "id": "expense-uuid",
  "amount": 18500,
  "spentOn": "2026-09-08",
  "category": {
    "id": "category-uuid",
    "name": "장보기"
  },
  "merchant": "동네마트",
  "memo": "주말 장보기",
  "version": 0,
  "createdAt": "2026-09-08T14:00:00+09:00",
  "updatedAt": "2026-09-08T14:00:00+09:00"
}
```

등록한 식비의 사용 날짜가 현재 월이면 응답 트랜잭션 안에서 월 예산 초과 위험 알림 조건을 평가한다. 조건을 만족해도 푸시 발송은 커밋 이후 비동기로 수행하며 식비 등록 응답을 지연시키지 않는다.

### 6.2 식비 목록

```http
GET /api/v1/expenses?from=2026-09-01&to=2026-09-30&categoryId=...&keyword=마트&size=20&cursor=...
```

Query:

| 이름 | 필수 | 기본값 | 설명 |
|---|:---:|---|---|
| from | X | 없음 | 시작일 |
| to | X | 없음 | 종료일 |
| categoryId | X | 없음 | 카테고리 |
| uncategorized | X | false | 분류 없음만 조회 |
| keyword | X | 없음 | 상호명·메모 검색 |
| size | X | 20 | 1~100 |
| cursor | X | 없음 | 다음 페이지 커서 |

응답:

```json
{
  "items": [
    {
      "id": "expense-uuid",
      "amount": 18500,
      "spentOn": "2026-09-08",
      "category": {
        "id": "category-uuid",
        "name": "장보기"
      },
      "merchant": "동네마트",
      "memo": "주말 장보기",
      "version": 0,
      "createdAt": "2026-09-08T14:00:00+09:00",
      "updatedAt": "2026-09-08T14:00:00+09:00"
    }
  ],
  "nextCursor": "opaque-cursor",
  "hasNext": true
}
```

정렬:

```text
spentOn DESC, createdAt DESC, id DESC
```

### 6.3 식비 상세

```http
GET /api/v1/expenses/{expenseId}
```

응답은 식비 등록 응답과 같다.

### 6.4 식비 수정

```http
PUT /api/v1/expenses/{expenseId}
```

요청:

```json
{
  "amount": 20000,
  "spentOn": "2026-09-08",
  "categoryId": "category-uuid",
  "merchant": "동네마트",
  "memo": "수정한 메모",
  "version": 0
}
```

성공 응답에는 증가한 version을 포함한다.

version이 현재 DB 값과 다르면 HTTP 409:

```json
{
  "type": "https://mealbudgetdiet.app/problems/version-conflict",
  "title": "Expense was changed",
  "status": 409,
  "code": "EXPENSE_VERSION_CONFLICT",
  "detail": "다른 사용자가 이 식비 내역을 먼저 변경했습니다."
}
```

### 6.5 식비 삭제

```http
DELETE /api/v1/expenses/{expenseId}?version=1
```

성공: HTTP 204

version 충돌 시 삭제하지 않고 HTTP 409를 반환한다.

## 7. 카테고리 API

### 7.1 목록

```http
GET /api/v1/categories
```

응답:

```json
{
  "items": [
    {
      "id": "category-uuid",
      "name": "장보기",
      "sortOrder": 1,
      "version": 0
    }
  ]
}
```

### 7.2 생성

```http
POST /api/v1/categories
```

요청:

```json
{
  "name": "회사 점심",
  "sortOrder": 6
}
```

성공: HTTP 201

### 7.3 수정

```http
PUT /api/v1/categories/{categoryId}
```

요청:

```json
{
  "name": "점심",
  "sortOrder": 6,
  "version": 0
}
```

### 7.4 삭제

```http
DELETE /api/v1/categories/{categoryId}?version=0
```

성공: HTTP 204

이 카테고리를 사용하던 식비의 category는 null이 되고 화면과 통계에서는 ‘분류 없음’으로 표시한다.

## 8. 예산 API

### 8.1 적용 예산 조회

```http
GET /api/v1/budgets/2026-09
```

응답:

```json
{
  "yearMonth": "2026-09",
  "amount": 900000,
  "source": "MONTHLY_OVERRIDE",
  "version": 1
}
```

source:

- `DEFAULT`
- `MONTHLY_OVERRIDE`

### 8.2 기본 월 예산 변경

```http
PUT /api/v1/budgets/default
```

요청:

```json
{
  "amount": 800000,
  "version": 2
}
```

### 8.3 특정 월 예산 설정

```http
PUT /api/v1/budgets/2026-09
```

요청:

```json
{
  "amount": 900000,
  "version": 0
}
```

해당 월 설정이 없으면 생성하고 있으면 version을 확인한 후 변경한다.

### 8.4 특정 월 설정 제거

```http
DELETE /api/v1/budgets/2026-09?version=1
```

성공: HTTP 204

이후 해당 월에는 기본 월 예산이 적용된다.

## 9. 대시보드 API

```http
GET /api/v1/dashboard?yearMonth=2026-09&recentSize=5
```

응답:

```json
{
  "yearMonth": "2026-09",
  "budget": 800000,
  "spent": 658800,
  "remaining": 141200,
  "usageRate": 82.35,
  "status": "WARNING",
  "recentExpenses": [
    {
      "id": "expense-uuid",
      "amount": 18500,
      "spentOn": "2026-09-08",
      "categoryName": "장보기",
      "merchant": "동네마트",
      "version": 0
    }
  ]
}
```

status:

- `NORMAL`: 80% 미만
- `WARNING`: 80% 이상 100% 미만
- `EXCEEDED`: 100% 이상

remaining은 초과 시 음수가 될 수 있다.

## 10. 통계 API

```http
GET /api/v1/statistics?from=2026-09-01&to=2026-09-30
```

응답:

```json
{
  "period": {
    "from": "2026-09-01",
    "to": "2026-09-30"
  },
  "totalAmount": 658800,
  "budget": {
    "amount": 800000,
    "usageRate": 82.35
  },
  "comparison": {
    "from": "2026-08-01",
    "to": "2026-08-31",
    "totalAmount": 610000,
    "changeAmount": 48800,
    "changeRate": 8.0
  },
  "daily": [
    {
      "date": "2026-09-01",
      "amount": 32000
    }
  ],
  "categories": [
    {
      "categoryId": "category-uuid",
      "categoryName": "장보기",
      "amount": 250000,
      "ratio": 37.95
    },
    {
      "categoryId": null,
      "categoryName": "분류 없음",
      "amount": 15000,
      "ratio": 2.28
    }
  ]
}
```

비교 기간:

- 한 달 전체 조회: 직전 달
- 최근 3개월: 직전 3개월
- 올해: 전년도 동일 기간
- 직접 지정: 바로 이전의 동일 일수 기간

여러 달에 걸친 임의 기간은 월 예산 합계 대신 기간에 포함된 각 날짜 비율로 예산을 일할 계산한다. 구현 전 통계 UX 검토에서 이 규칙을 다시 확인한다.

## 11. CSV 내보내기

```http
GET /api/v1/expenses/export.csv?from=2026-09-01&to=2026-09-30&categoryId=...&keyword=...
```

응답:

```http
Content-Type: text/csv; charset=UTF-8
Content-Disposition: attachment; filename="meal-expenses-2026-09-01_2026-09-30.csv"
```

컬럼:

```text
사용일,금액,카테고리,상호명,메모
```

- Excel 한글 호환을 위해 UTF-8 BOM 포함 여부를 구현 테스트에서 결정한다.
- formula injection을 방지하기 위해 `=`, `+`, `-`, `@`로 시작하는 문자열을 안전하게 이스케이프한다.
- CSV 데이터 가져오기는 지원하지 않는다.

## 12. PWA 푸시 API

### 12.1 VAPID 공개키 조회

```http
GET /api/v1/push/vapid-public-key
```

응답:

```json
{
  "publicKey": "base64url-vapid-public-key"
}
```

VAPID 키가 서버에 설정되지 않은 환경에서는 `PUSH_NOT_CONFIGURED`와 HTTP 503을 반환한다.

### 12.2 기기 구독 등록·갱신

브라우저가 Notification 권한을 받은 뒤 service worker의 PushSubscription을 전달한다.

```http
PUT /api/v1/push-subscriptions
```

요청:

```json
{
  "endpoint": "https://push-service.example/...",
  "expirationTime": null,
  "keys": {
    "p256dh": "base64url-key",
    "auth": "base64url-secret"
  }
}
```

성공: 신규이면 HTTP 201, 같은 endpoint 갱신이면 HTTP 200

```json
{
  "id": "push-subscription-uuid",
  "status": "ACTIVE",
  "createdAt": "2026-09-08T15:00:00+09:00"
}
```

- 현재 로그인 사용자 소유로 저장한다.
- endpoint는 전체 시스템에서 unique하다.
- 알려진 브라우저 Web Push 서비스의 HTTPS endpoint만 허용한다.
- endpoint와 key를 API 로그에 기록하지 않는다.

### 12.3 기기 구독 해제

```http
DELETE /api/v1/push-subscriptions/{subscriptionId}
```

성공: HTTP 204

현재 사용자 소유가 아닌 subscription은 존재 여부를 노출하지 않고 HTTP 404를 반환한다.

### 12.4 월 예산 초과 위험 알림 판정

식비 신규 등록 직후 다음 두 조건을 모두 평가한다.

```text
monthlyUsageRate >= 80
AND
totalSpent / elapsedDays * daysInMonth > monthlyBudget
```

정의:

- monthlyBudget: 신규 식비가 속한 현재 월의 적용 예산
- totalSpent: 신규 식비까지 포함한 현재 월 누적 식비
- elapsedDays: Asia/Seoul 기준 해당 월 1일부터 오늘까지, 오늘 포함
- daysInMonth: 현재 월의 전체 일수
- monthlyUsageRate: totalSpent / monthlyBudget × 100

알림 생성 조건:

- POST `/expenses` 성공 시에만 평가
- 식비 spentOn이 현재 월에 속해야 함
- PUT과 DELETE에서는 평가하지 않음
- `MONTHLY_BUDGET_OVERRUN_RISK` 알림은 장부와 월 기준 한 번만 생성
- 활성 상태이며 푸시를 허용한 모든 참여자 기기로 발송

푸시 payload 예시:

```json
{
  "notificationId": "budget-alert-uuid",
  "type": "MONTHLY_BUDGET_OVERRUN_RISK",
  "title": "이번 달 식비 예산 초과가 예상돼요",
  "body": "현재 소비 속도라면 이번 달 약 1,200,000원을 사용할 것으로 예상돼요.",
  "data": {
    "url": "/",
    "yearMonth": "2026-09"
  }
}
```

service worker는 notificationId가 이미 표시된 알림이면 다시 표시하지 않는다.

### 12.5 전송 상태

- 예산 알림 이벤트는 식비와 같은 DB 트랜잭션에서 한 번만 생성한다.
- 기기별 delivery는 PENDING으로 생성한다.
- 커밋 이후 dispatcher가 Web Push를 발송한다.
- 일시적 실패는 backoff 후 제한된 횟수만 재시도한다.
- push service가 endpoint 만료를 반환하면 subscription을 EXPIRED로 변경한다.
- 푸시 권한이 없거나 전송이 실패해도 이메일로 대체 발송하지 않는다.

## 13. Rate limit 대상

초기에는 다음 공개 또는 민감 API에 IP와 계정 기준 제한을 적용한다.

- `POST /auth/login`
- `POST /auth/password-reset-requests`
- `POST /auth/password-resets`
- `POST /auth/register`
- `POST /bootstrap/admin`

제한 초과 시 HTTP 429와 `Retry-After` 헤더를 반환한다.

## 14. OpenAPI 관리

구현 단계에서 다음 원칙을 적용한다.

- OpenAPI 3 명세를 CI에서 생성 또는 검증한다.
- Controller DTO와 문서가 달라지면 빌드를 실패시킨다.
- 운영 Swagger UI는 인증된 사용자에게만 제공하거나 비활성화한다.
- README에는 데모 API 문서 링크를 제공한다.

## 15. 구현 순서

1. bootstrap, register, login, logout, me
2. ledger, members, invitations
3. categories
4. expenses CRUD와 커서 조회
5. default/monthly budgets
6. dashboard
7. statistics
8. Web Push 구독과 월 예산 초과 위험 알림
9. CSV export
10. withdrawal과 장부 종료
11. password reset와 email provider
