# MealBudgetDiet REST API 설계

- 문서 버전: 1.2
- Base path: `/api/v1`
- Content-Type: 기본 `application/json`, 이미지 업로드 `multipart/form-data`
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
| 이미지 | JPEG, PNG, WebP | 원본 파일당 최대 5MB |

### 1.2 인증 쿠키

로그인과 회원가입 성공 시 서버가 `MBD_SESSION` 쿠키를 설정한다.

- 애플리케이션은 세션에 고정 만료 또는 미사용 만료 시간을 두지 않는다.
- 브라우저가 허용하는 장기 지속 쿠키를 사용하고 로그인 상태에서 만료 시점을 갱신한다.
- 로그아웃은 현재 세션만 종료한다.
- 비밀번호 변경·재설정 또는 탈퇴는 해당 사용자의 모든 세션을 종료한다.
- 브라우저 데이터 삭제, 시크릿 모드 종료 또는 브라우저 정책으로 쿠키가 제거되면 다시 로그인해야 한다.

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
| 413 | 이미지 파일 크기 제한 초과 |
| 415 | 지원하지 않거나 해석할 수 없는 이미지 형식 |
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
| PUT | `/account/profile-image` | O | 프로필 사진 등록·교체 |
| DELETE | `/account/profile-image` | O | 프로필 사진 삭제 |
| POST | `/account/password-change` | O | 비밀번호 변경과 전체 세션 종료 |
| POST | `/auth/password-reset-requests` | X | 재설정 이메일 요청 |
| POST | `/auth/password-resets` | X | 새 비밀번호 설정 |

### 2.2 장부·회원·초대

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/ledger` | MEMBER | 현재 공용 장부 조회 |
| PUT | `/ledger/settings/budget-cycle` | ADMIN | 월 예산 시작일 변경 |
| PUT | `/ledger/settings/push-threshold` | ADMIN | Push 기준 사용률 변경 |
| GET | `/members` | MEMBER | 참여자 목록 |
| PATCH | `/members/{memberId}/role` | ADMIN | 관리자 지정·해제 |
| POST | `/account/withdrawal` | MEMBER | 본인 탈퇴 또는 마지막 관리자 장부 종료 |
| GET | `/invitations` | MEMBER | 활성·취소 초대 목록 |
| POST | `/invitations` | MEMBER | 초대 코드 발급 |
| DELETE | `/invitations/{invitationId}` | MEMBER | 초대 코드 취소 |

### 2.3 식비·이미지·카테고리·예산

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
| PUT | `/budgets/{yearMonth}` | ADMIN | 특정 예산 주기 예산 설정 |
| DELETE | `/budgets/{yearMonth}` | ADMIN | 특정 예산 주기 설정 제거 |
| POST | `/uploads/images` | MEMBER | 임시 이미지 업로드 |
| GET | `/images/{imageId}/content` | MEMBER | 권한 확인 후 이미지 조회 |
| DELETE | `/uploads/images/{imageId}` | MEMBER | 본인의 임시 이미지 취소 |

### 2.4 대시보드·통계·내보내기

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/dashboard` | MEMBER | 현재 예산 주기 대시보드 |
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
  "profileImageUrl": null,
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
  "profileImageUrl": null,
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
  "profileImageUrl": "/api/v1/images/profile-image-uuid/content",
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

### 3.9 프로필 사진 등록·교체

먼저 `purpose=PROFILE`로 업로드한 본인 소유 `TEMP` 이미지 ID를 연결한다.

```http
PUT /api/v1/account/profile-image
```

요청:

```json
{
  "imageId": "profile-image-uuid"
}
```

응답:

```json
{
  "profileImageUrl": "/api/v1/images/profile-image-uuid/content"
}
```

연결 성공 시 이미지를 `ACTIVE`로 바꾸고 기존 프로필 이미지는 참조를 제거한 뒤 삭제한다. 다른 사용자의 이미지, 다른 장부 이미지 또는 `EXPENSE` 용도 이미지는 HTTP 400 또는 404로 거절한다.

### 3.10 프로필 사진 삭제

```http
DELETE /api/v1/account/profile-image
```

성공: HTTP 204. 프로필 사진이 없어도 같은 응답을 반환한다.

### 3.11 비밀번호 변경

```http
POST /api/v1/account/password-change
```

요청:

```json
{
  "currentPassword": "current-password",
  "newPassword": "new-password"
}
```

성공: HTTP 204와 해당 사용자의 모든 세션 종료. 현재 기기도 다시 로그인해야 한다.

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
  "budgetCycleStartDay": 1,
  "pushUsageThreshold": 80,
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
      "profileImageUrl": "/api/v1/images/profile-image-uuid/content",
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

### 4.5 월 예산 시작일 변경

```http
PUT /api/v1/ledger/settings/budget-cycle
```

요청:

```json
{
  "startDay": 25,
  "version": 2
}
```

`startDay`는 1~31의 정수다. 설정일이 없는 달에는 그 달의 마지막 날을 주기 시작일로 사용한다. 성공 응답은 증가한 장부 `version`과 새 설정을 반환한다.

```json
{
  "budgetCycleStartDay": 25,
  "version": 3
}
```

변경 후 새로 조회하는 대시보드, 월 통계, 적용 예산과 Push 판정은 새 주기 경계를 사용한다. 식비 원본과 기존 월별 예산 금액은 바꾸지 않는다.

### 4.6 Push 기준 사용률 변경

```http
PUT /api/v1/ledger/settings/push-threshold
```

요청:

```json
{
  "usageThreshold": 75,
  "version": 3
}
```

`usageThreshold`는 1~100의 정수이며 기본값은 80이다. 성공 응답:

```json
{
  "pushUsageThreshold": 75,
  "version": 4
}
```

변경된 기준은 이후 새 식비 등록의 Push 판정부터 적용한다. 대시보드의 `NORMAL`, `WARNING`, `EXCEEDED` 표시 기준은 변경하지 않는다.

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

## 6. 이미지 API

### 6.1 임시 이미지 업로드

```http
POST /api/v1/uploads/images?purpose=EXPENSE
Content-Type: multipart/form-data
```

Multipart field:

| 이름 | 필수 | 규칙 |
|---|:---:|---|
| file | O | JPEG, PNG, WebP, 최대 5MB |

서버는 Content-Type과 확장자만 신뢰하지 않고 실제 파일 signature와 디코딩 가능 여부를 확인한다. 메타데이터를 제거하고 표시 크기로 리사이징한 WebP로 변환한 뒤 `TEMP` 상태로 저장한다.

성공: HTTP 201

```json
{
  "id": "image-uuid",
  "purpose": "EXPENSE",
  "contentUrl": "/api/v1/images/image-uuid/content",
  "mimeType": "image/webp",
  "width": 1280,
  "height": 960,
  "status": "TEMP"
}
```

- `purpose`는 `EXPENSE` 또는 `PROFILE`이다.
- 크기 초과는 HTTP 413 `IMAGE_TOO_LARGE`, 형식·디코딩 실패는 HTTP 415 `UNSUPPORTED_IMAGE`를 반환한다.
- 실패하거나 중단된 업로드의 부분 객체는 즉시 삭제한다.
- 식비 또는 프로필에 연결되지 않은 `TEMP` 이미지는 생성 후 24시간이 지나면 정리한다.

### 6.2 이미지 조회

```http
GET /api/v1/images/{imageId}/content
```

`ACTIVE` 이미지는 같은 장부의 활성 참여자만 조회할 수 있다. `TEMP` 이미지는 업로더 본인만 미리 볼 수 있다. 권한이 없으면 이미지 존재 여부를 드러내지 않고 HTTP 404를 반환한다.

응답은 `Content-Type: image/webp`, 비공개 캐시 정책과 `X-Content-Type-Options: nosniff`를 사용한다.

### 6.3 임시 이미지 업로드 취소

```http
DELETE /api/v1/uploads/images/{imageId}
```

본인이 올린 `TEMP` 이미지만 취소할 수 있다. 성공: HTTP 204. 이미 리소스에 연결된 `ACTIVE` 이미지는 이 API로 삭제할 수 없다.

## 7. 식비 API

### 7.1 식비 등록

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
  "memo": "주말 장보기",
  "imageIds": ["image-uuid-1", "image-uuid-2"]
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
  "images": [
    {
      "id": "image-uuid-1",
      "contentUrl": "/api/v1/images/image-uuid-1/content",
      "sortOrder": 0
    },
    {
      "id": "image-uuid-2",
      "contentUrl": "/api/v1/images/image-uuid-2/content",
      "sortOrder": 1
    }
  ],
  "version": 0,
  "createdAt": "2026-09-08T14:00:00+09:00",
  "updatedAt": "2026-09-08T14:00:00+09:00"
}
```

`imageIds`는 순서가 있는 최대 3개의 고유 ID다. 본인이 올린 같은 장부의 `TEMP`, `EXPENSE` 이미지만 연결할 수 있으며 식비 저장과 이미지 활성화를 하나의 트랜잭션으로 처리한다.

등록한 식비의 사용 날짜가 현재 예산 주기에 속하면 응답 트랜잭션 안에서 예산 초과 위험 알림 조건을 평가한다. 조건을 만족해도 푸시 발송은 커밋 이후 비동기로 수행하며 식비 등록 응답을 지연시키지 않는다.

### 7.2 식비 목록

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
      "imageCount": 2,
      "thumbnailUrl": "/api/v1/images/image-uuid-1/content",
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

### 7.3 식비 상세

```http
GET /api/v1/expenses/{expenseId}
```

응답은 식비 등록 응답과 같다.

### 7.4 식비 수정

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
  "imageIds": ["image-uuid-2", "image-uuid-3"],
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

`imageIds`를 생략할 수 없으며 빈 배열은 모든 이미지를 제거한다. 기존 연결을 유지하거나 순서를 바꾸려면 응답에서 받은 기존 이미지 ID를 원하는 순서로 함께 보낸다. 제거된 미참조 이미지와 객체는 커밋 후 삭제한다.

### 7.5 식비 삭제

```http
DELETE /api/v1/expenses/{expenseId}?version=1
```

성공: HTTP 204

version 충돌 시 삭제하지 않고 HTTP 409를 반환한다.

삭제 성공 후 연결된 이미지 행과 Object Storage 객체도 영구 삭제한다.

## 8. 카테고리 API

### 8.1 목록

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

### 8.2 생성

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

### 8.3 수정

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

### 8.4 삭제

```http
DELETE /api/v1/categories/{categoryId}?version=0
```

성공: HTTP 204

이 카테고리를 사용하던 식비의 category는 null이 되고 화면과 통계에서는 ‘분류 없음’으로 표시한다.

## 9. 예산 API

### 9.1 적용 예산 조회

```http
GET /api/v1/budgets/2026-09
```

응답:

```json
{
  "yearMonth": "2026-09",
  "period": {
    "from": "2026-09-25",
    "to": "2026-10-24"
  },
  "amount": 900000,
  "source": "MONTHLY_OVERRIDE",
  "version": 1
}
```

source:

- `DEFAULT`
- `MONTHLY_OVERRIDE`

`yearMonth`는 달력 월이 아니라 예산 주기가 시작되는 날짜의 연·월이다. 예산 시작일이 25일이면 위 `period`처럼 다음 달 24일까지를 한 주기로 반환한다.

### 9.2 기본 월 예산 변경

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

### 9.3 특정 예산 주기 예산 설정

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

해당 예산 주기 설정이 없으면 생성하고 있으면 version을 확인한 후 변경한다.

### 9.4 특정 예산 주기 설정 제거

```http
DELETE /api/v1/budgets/2026-09?version=1
```

성공: HTTP 204

이후 해당 예산 주기에는 기본 월 예산이 적용된다.

## 10. 대시보드 API

```http
GET /api/v1/dashboard?yearMonth=2026-09&recentSize=5
```

응답:

```json
{
  "yearMonth": "2026-09",
  "period": {
    "from": "2026-09-25",
    "to": "2026-10-24"
  },
  "budget": 800000,
  "spent": 658800,
  "remaining": 141200,
  "usageRate": 82.35,
  "pushUsageThreshold": 80,
  "status": "WARNING",
  "recentExpenses": [
    {
      "id": "expense-uuid",
      "amount": 18500,
      "spentOn": "2026-10-08",
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

`status`의 80% 기준은 대시보드 시각 상태용 고정값이다. `pushUsageThreshold`는 초과 위험 Push 판정에만 사용한다.

## 11. 통계 API

```http
GET /api/v1/statistics?yearMonth=2026-09
```

응답:

```json
{
  "period": {
    "from": "2026-09-25",
    "to": "2026-10-24"
  },
  "totalAmount": 658800,
  "budget": {
    "amount": 800000,
    "usageRate": 82.35
  },
  "comparison": {
    "from": "2026-08-25",
    "to": "2026-09-24",
    "totalAmount": 610000,
    "changeAmount": 48800,
    "changeRate": 8.0
  },
  "daily": [
    {
      "date": "2026-09-25",
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

- `yearMonth` 조회: 직전 예산 주기
- 최근 3개 예산 주기: 직전 3개 예산 주기
- 올해: 전년도 동일 기간
- 직접 지정: 바로 이전의 동일 일수 기간

`yearMonth`와 `from`/`to`는 함께 보낼 수 없다. `yearMonth`는 장부의 월 시작일에 따른 예산 주기를 사용하고, `from`/`to` 직접 지정은 입력한 달력 날짜를 그대로 사용한다. 여러 예산 주기에 걸친 임의 기간은 각 주기에 포함된 날짜 비율로 예산을 일할 계산한다.

## 12. CSV 내보내기

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

## 13. PWA 푸시 API

### 13.1 VAPID 공개키 조회

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

### 13.2 기기 구독 등록·갱신

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

### 13.3 기기 구독 해제

```http
DELETE /api/v1/push-subscriptions/{subscriptionId}
```

성공: HTTP 204

현재 사용자 소유가 아닌 subscription은 존재 여부를 노출하지 않고 HTTP 404를 반환한다.

### 13.4 예산 주기 초과 위험 알림 판정

식비 신규 등록 직후 다음 두 조건을 모두 평가한다.

```text
budgetUsageRate >= pushUsageThreshold
AND
totalSpent / elapsedDays * daysInCycle > cycleBudget
```

정의:

- cycleBudget: 신규 식비가 속한 현재 예산 주기의 적용 예산
- totalSpent: 신규 식비까지 포함한 현재 예산 주기 누적 식비
- elapsedDays: Asia/Seoul 기준 예산 주기 시작일부터 오늘까지, 오늘 포함
- daysInCycle: 현재 예산 주기의 전체 일수
- pushUsageThreshold: 장부 관리자가 설정한 1~100의 정수, 기본값 80
- budgetUsageRate: totalSpent / cycleBudget × 100

반올림 오차 없이 다음 교차 곱셈으로 비교한다.

```text
totalSpent * 100 >= cycleBudget * pushUsageThreshold
AND
totalSpent * daysInCycle > cycleBudget * elapsedDays
```

알림 생성 조건:

- POST `/expenses` 성공 시에만 평가
- 식비 spentOn이 현재 예산 주기에 속해야 함
- PUT과 DELETE에서는 평가하지 않음
- `MONTHLY_BUDGET_OVERRUN_RISK` 알림은 장부와 예산 주기 기준 한 번만 생성
- 기준 사용률을 바꿔도 이미 알림을 생성한 예산 주기에는 다시 생성하지 않음
- 활성 상태이며 푸시를 허용한 모든 참여자 기기로 발송

푸시 payload 예시:

```json
{
  "notificationId": "budget-alert-uuid",
  "type": "MONTHLY_BUDGET_OVERRUN_RISK",
  "title": "현재 식비 예산 초과가 예상돼요",
  "body": "현재 소비 속도라면 이번 예산 주기에 약 1,200,000원을 사용할 것으로 예상돼요.",
  "data": {
    "url": "/",
    "yearMonth": "2026-09",
    "cycleFrom": "2026-09-25",
    "cycleTo": "2026-10-24"
  }
}
```

service worker는 notificationId가 이미 표시된 알림이면 다시 표시하지 않는다.

### 13.5 전송 상태

- 예산 알림 이벤트는 식비와 같은 DB 트랜잭션에서 한 번만 생성한다.
- 기기별 delivery는 PENDING으로 생성한다.
- 커밋 이후 dispatcher가 Web Push를 발송한다.
- 일시적 실패는 backoff 후 제한된 횟수만 재시도한다.
- push service가 endpoint 만료를 반환하면 subscription을 EXPIRED로 변경한다.
- 푸시 권한이 없거나 전송이 실패해도 이메일로 대체 발송하지 않는다.

## 14. Rate limit 대상

초기에는 다음 공개 또는 민감 API에 IP와 계정 기준 제한을 적용한다.

- `POST /auth/login`
- `POST /auth/password-reset-requests`
- `POST /auth/password-resets`
- `POST /auth/register`
- `POST /bootstrap/admin`

제한 초과 시 HTTP 429와 `Retry-After` 헤더를 반환한다.

## 15. OpenAPI 관리

구현 단계에서 다음 원칙을 적용한다.

- OpenAPI 3 명세를 CI에서 생성 또는 검증한다.
- Controller DTO와 문서가 달라지면 빌드를 실패시킨다.
- 운영 Swagger UI는 인증된 사용자에게만 제공하거나 비활성화한다.
- README에는 데모 API 문서 링크를 제공한다.

## 16. 구현 순서

1. bootstrap, register, login, logout, me와 명시적 전체 세션 폐기
2. ledger, members, invitations
3. 이미지 업로드·변환·조회와 프로필 사진
4. categories
5. 이미지 첨부를 포함한 expenses CRUD와 커서 조회
6. default/monthly budgets와 장부 예산 주기 설정
7. dashboard
8. statistics
9. Web Push 구독, 기준 사용률 설정과 예산 주기 초과 위험 알림
10. CSV export
11. withdrawal과 장부 종료
12. password reset와 email provider
