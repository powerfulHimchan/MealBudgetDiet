# MealBudgetDiet frontend

Next.js App Router 기반의 모바일 우선 PWA입니다.

## Local development

Node.js 24를 사용합니다.

```bash
npm ci
npm run dev
```

개발 서버는 http://localhost:3000 에서 실행됩니다. `/api/*` 요청은 기본적으로 http://localhost:8080 의 백엔드로 전달됩니다. 다른 주소를 사용하려면 `BACKEND_INTERNAL_URL` 환경 변수를 설정하세요.

## Verification

```bash
npm run lint
npm run build
```

전체 Docker Compose 서비스를 실행한 상태에서는 실제 PostgreSQL, MinIO, 백엔드를 사용하는 핵심 E2E 테스트를 실행할 수 있습니다. E2E 데이터는 빈 데이터베이스에 최초 관리자를 생성하므로 CI처럼 전용 Compose project와 테스트 전용 환경 변수를 사용해야 합니다.

```bash
npm run e2e:full-stack
```

테스트는 최초 관리자 생성, 새 세션 로그인, 초대 코드 가입, 이미지가 포함된 식비 등록, 참여자의 수정, 관리자의 통계 확인과 참여자의 삭제까지 하나의 시나리오로 검증합니다. 반복 실행할 때는 테스트용 Compose project의 볼륨을 삭제하고 빈 환경으로 다시 시작합니다.

서비스 워커는 개발 중 캐시 간섭을 막기 위해 프로덕션 빌드에서만 등록됩니다.
