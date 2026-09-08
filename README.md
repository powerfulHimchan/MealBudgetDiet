# MealBudgetDiet

공용 식비 내역과 월 예산을 함께 관리하고 기간별 소비 통계를 확인하는 모바일 중심 PWA 프로젝트입니다.

실제 생활에서 지속적으로 사용할 수 있는 서비스를 만들고, 요구사항 정의부터 설계·구현·테스트·배포까지의 과정을 백엔드 개발 포트폴리오로 기록하는 것을 목표로 합니다.

## Project status

요구사항과 시스템 설계, 프로젝트 골격, 반응형 UI, PostgreSQL 마이그레이션, 세션 기반 인증 및 공용 장부 협업 기능을 완료했습니다. 다음 단계는 식비와 카테고리 관리 기능입니다.

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

개별 개발 서버는 `frontend/README.md`와 `backend/HELP.md`를 참고하세요.

## Documentation

- [요구사항 명세](docs/requirements.md)
- [시스템 아키텍처](docs/architecture.md)
- [ERD 및 데이터 모델](docs/erd.md)
- [REST API 설계](docs/api.md)
