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

서비스 워커는 개발 중 캐시 간섭을 막기 위해 프로덕션 빌드에서만 등록됩니다.
