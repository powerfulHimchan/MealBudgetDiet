# Sikbi 운영 배포 가이드

- 서비스명: `Sikbi`
- 사용자 표시명: `Sikbi - 함께 쓰는 식비 관리`
- Web origin: `https://sikbi.app`
- API origin: `https://api.sikbi.app`
- Android package ID: `app.sikbi`
- DNS: Cloudflare
- Application / PostgreSQL: Railway
- 배포 리전: Singapore 권장

애플리케이션은 브라우저에서 same-origin `/api`를 호출하고, `sikbi-web`이 Railway private network의 `sikbi-api`로 전달한다. `api.sikbi.app`은 운영 확인과 API 직접 접근에 사용하되 PWA의 기본 호출 경로는 계속 `sikbi.app/api/*`로 유지한다.

## 1. Railway 프로젝트

같은 Railway 프로젝트에 다음 서비스를 생성한다.

| 서비스 | 유형 | Root Directory | Config file |
|---|---|---|---|
| `sikbi-web` | GitHub / Dockerfile | `/frontend` | `/frontend/railway.toml` |
| `sikbi-api` | GitHub / Dockerfile | `/backend` | `/backend/railway.toml` |
| `Postgres` | Railway PostgreSQL | 해당 없음 | Railway 관리 |
| Object Storage | 비공개 S3 호환 스토리지 | 해당 없음 | 사업자 확정 후 연결 |

두 애플리케이션 서비스는 모두 `powerfulHimchan/MealBudgetDiet`의 `main` 브랜치를 연결한다. Railway 서비스 설정에서 Root Directory와 Config File Path를 표의 값으로 지정한다.

## 2. 환경 변수

### sikbi-web

```dotenv
BACKEND_INTERNAL_URL=http://${{sikbi-api.RAILWAY_PRIVATE_DOMAIN}}:8080
```

`BACKEND_INTERNAL_URL`은 Next.js rewrite에 포함되므로 build와 runtime에서 모두 사용할 수 있어야 한다.

### sikbi-api

```dotenv
DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DB_USERNAME=${{Postgres.PGUSER}}
DB_PASSWORD=${{Postgres.PGPASSWORD}}
PUBLIC_BASE_URL=https://sikbi.app
PROBLEM_BASE_URL=https://sikbi.app/problems
SESSION_COOKIE_SECURE=true
BOOTSTRAP_TOKEN=<long-random-secret>
AUTH_RATE_LIMIT_SECRET=<at-least-32-random-characters>
```

나머지 SMTP, VAPID, Object Storage 값은 [운영 환경 변수 예시](../.env.production.example)를 기준으로 Railway Variables에 등록한다. 비밀번호와 비공개 키는 저장소에 커밋하지 않는다.

PostgreSQL은 외부 Public Access를 켜지 않고 같은 Railway 프로젝트의 private network로만 연결한다. 정기 백업과 보존 기간은 프로덕션 오픈 전에 Railway에서 활성화하고 개인정보처리방침에 동일하게 반영한다.

## 3. Cloudflare DNS

1. Railway의 `sikbi-web` 서비스에 custom domain `sikbi.app`을 추가한다.
2. Railway가 표시하는 CNAME과 TXT 검증 레코드를 Cloudflare DNS에 그대로 생성한다.
3. 인증서 검증이 완료될 때까지 레코드는 `DNS only`로 둔다.
4. `sikbi-api` 서비스에도 `api.sikbi.app`을 추가하고 Railway가 제공하는 CNAME과 TXT 레코드를 등록한다.
5. `www.sikbi.app`은 Cloudflare Redirect Rule로 `https://sikbi.app`에 영구 리다이렉트한다.
6. Railway에서 두 custom domain이 모두 Active이고 HTTPS 인증서가 발급됐는지 확인한다.

Railway가 제공하는 실제 CNAME 대상과 TXT 값은 프로젝트 생성 시 결정되므로 저장소에 하드코딩하지 않는다.

## 4. 운영 확인

```text
https://sikbi.app/manifest.webmanifest
https://sikbi.app/privacy
https://sikbi.app/account-deletion
https://sikbi.app/icons/icon-512.png
https://sikbi.app/api/v1/system/health
https://api.sikbi.app/api/v1/system/health
```

확인 항목:

- 모든 HTTP 요청이 HTTPS로 이동
- 세션 쿠키에 Secure, HttpOnly, SameSite=Lax 적용
- 프론트엔드의 `/api/*`가 private backend로 정상 전달
- Flyway migration 성공
- 이미지 저장소가 public bucket이 아니며 권한 검사 후에만 조회
- 회원가입, 로그인, 초대, 식비 CRUD, 이미지, 통계, Push E2E 통과
- PostgreSQL 백업 생성과 복구 절차 확인

## 5. Android TWA

운영 PWA가 외부에서 정상 동작한 다음 Bubblewrap 프로젝트를 `android/`에 생성한다.

```bash
npx --yes @bubblewrap/cli@latest init \
  --manifest="https://sikbi.app/manifest.webmanifest" \
  --directory="android"
```

초기화 값:

| 항목 | 값 |
|---|---|
| Application name | `Sikbi - 함께 쓰는 식비 관리` |
| Launcher name | `Sikbi` |
| Package ID | `app.sikbi` |
| Host | `sikbi.app` |
| Start URL | `/` |
| Notifications | enabled |
| Fallback | `customtabs` |

Play Console에서 앱을 만든 뒤 패키지 ID는 변경할 수 없는 값으로 취급한다. 첫 AAB 업로드 후 Play App Signing SHA-256 지문을 `/.well-known/assetlinks.json`에 반영한다.
