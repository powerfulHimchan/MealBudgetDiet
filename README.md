# MealBudgetDiet

공용 식비 내역과 월 예산을 함께 관리하고 기간별 소비 통계를 확인하는 모바일 중심 PWA 프로젝트입니다.

실제 생활에서 지속적으로 사용할 수 있는 서비스를 만들고, 요구사항 정의부터 설계·구현·테스트·배포까지의 과정을 백엔드 개발 포트폴리오로 기록하는 것을 목표로 합니다.

## Project status

요구사항과 시스템 설계, 프로젝트 골격, 반응형 UI, PostgreSQL 마이그레이션, 로그인·초대 가입·최초 관리자·비밀번호 재설정 화면, 명시적 폐기형 영속 로그인, 공용 장부 협업, 식비·카테고리 관리, 사용자 지정 예산 주기·통계, 사용자 지정 기준의 조건부 PWA 푸시, 식비 이미지 첨부와 프로필·계정 관리를 완료했습니다.

## Tech stack

- Frontend: Next.js 16, React 19, TypeScript, Tailwind CSS, PWA
- Backend: Java 21, Spring Boot 4, Spring Security, Spring Data JPA, Flyway
- Data: PostgreSQL 18, Spring Session JDBC
- Test & delivery: JUnit 5, Testcontainers, Docker Compose, GitHub Actions

## Run locally

Docker가 설치되어 있다면 저장소 루트에서 전체 서비스를 실행할 수 있습니다.

```bash
cp .env.example .env
docker compose up --build
```

- Web: http://localhost:3000
- Backend health: http://localhost:8080/api/v1/system/health
- MinIO console: http://localhost:9001

식비·프로필 이미지는 PostgreSQL과 분리된 비공개 MinIO 버킷에 저장됩니다. 로컬 계정과 버킷은 `.env`의 `MEDIA_*` 값으로 변경할 수 있습니다.

개별 개발 서버는 `frontend/README.md`와 `backend/HELP.md`를 참고하세요.

### Web Push 설정

Web Push를 사용하려면 VAPID 키 쌍을 생성하고 `.env`에 공개키, 비공개키와 연락처를 설정합니다. 비공개키는 저장소에 커밋하지 않습니다.

```bash
npx web-push generate-vapid-keys
```

```dotenv
VAPID_PUBLIC_KEY=...
VAPID_PRIVATE_KEY=...
VAPID_SUBJECT=mailto:admin@example.com
```

키가 설정되지 않은 환경에서는 식비 관리 기능은 그대로 동작하고 푸시 전송만 비활성화됩니다.

### 비밀번호 재설정 이메일 설정

비밀번호 재설정 메일을 실제로 발송하려면 SMTP 계정을 설정하고 발송 기능을 활성화합니다. `PUBLIC_BASE_URL`은 사용자가 접속할 실제 HTTPS 주소로 지정해야 합니다.

```dotenv
PUBLIC_BASE_URL=https://meal.example.com
PASSWORD_RESET_EMAIL_ENABLED=true
PASSWORD_RESET_EMAIL_FROM=no-reply@example.com
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=...
SPRING_MAIL_PASSWORD=...
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

재설정 링크는 기본 30분 동안 한 번만 사용할 수 있고, 새 링크가 발급되면 이전 링크는 즉시 무효화됩니다. SMTP 비밀번호는 저장소에 커밋하지 않습니다.

## Documentation

- [요구사항 명세](docs/requirements.md)
- [시스템 아키텍처](docs/architecture.md)
- [ERD 및 데이터 모델](docs/erd.md)
- [REST API 설계](docs/api.md)
