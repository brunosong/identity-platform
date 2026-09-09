# 예제

auth-service 를 실제로 띄우고 고객(CUSTOMER) 로그인을 처음부터 끝까지 밟아보는 예제.

- `customer-login.sh` — 터미널에서 전 구간을 순서대로 밟는다. 각 단계가 왜 그렇게 되는지 함께 찍는다.

브라우저에서 눌러보려면 [`frontends/`](../frontends/README.md) 로 간다.

## 1. 띄우기

### PostgreSQL

저장소 루트에서:

```bash
docker compose up -d
```

`auth-pg`(55432)와 `customer-pg`(55433)가 함께 뜬다. `docker compose ps` 로 `healthy` 를
확인한 뒤 앱을 띄운다. 자세한 것은 [루트 README](../README.md#1-데이터베이스-띄우기).

### auth-service

```bash
./mvnw -pl auth-service/auth-bootstrap -am install -DskipTests

cd auth-service/auth-bootstrap
DB_URL=jdbc:postgresql://localhost:55432/identity \
SPRING_PROFILES_ACTIVE=local \
java -jar target/auth-bootstrap-0.0.1-SNAPSHOT.jar
```

뜰 때 이런 줄이 보여야 한다. 스키마는 Flyway 가 만들고, 개발용 역할·권한이 함께 들어간다.

```
Migrating schema "public" to version "1 - baseline schema"
Migrating schema "public" to version "9000 - local seed data"
Started AuthServiceApplication
```

> `local` 프로파일은 개발용 RSA 키가 설정에 박혀 있고 OTP 는 메일을 보내지 않고 고정코드(`123456`)를
> 쓴다. **운영에서는 절대 이 프로파일을 쓰지 않는다.**

## 2. 터미널 예제

```bash
./examples/customer-login.sh
```

밟는 순서:

| | 하는 일 | 보는 것 |
|---|---|---|
| 1 | 가입 | 신원(Principal) + 비밀번호 자격증명이 생기고 `CUSTOMER` 역할이 붙는다 |
| 2 | 로그인 | **realm 은 경로가 정한다.** 토큰이 쿠키가 아니라 **본문**으로 온다 |
| 3 | 내 권한 조회 | 이후 요청은 `Authorization: Bearer`. 같은 토큰을 어드민 경로에 내밀면 통하지 않는다 |
| 4 | 직원 realm 로그인 | **같은 아이디·비밀번호인데 401** — realm 격리 |
| 5 | 없는 realm / 없는 방식 | 404 |
| 6 | 재발급 | 고객 refresh 를 직원 realm 에 내밀면 서명에서 걸린다 |
| 7 | JWKS | 다른 서비스가 검증에 쓸 공개키. **realm 마다 주소가 다르고**, 합쳐진 문서는 404 |
| 8 | 로그아웃 | 이것도 realm 경로 위에 있다 |

## 3. 브라우저로 보기

이 예제는 터미널용이다. 화면에서 눌러보려면 [`frontends/customer-portal`](../frontends/README.md)
(Vite + React)을 띄운다 — 가입·로그인·마이페이지가 있고, 토큰을 클레임 단위로 풀어서 보여준다.

## 4. 정리

```bash
docker compose down       # 데이터를 남기려면
docker compose down -v    # 깨끗하게
```

## 이 예제로 보이지 않는 것

- **소셜 로그인 / OTP 메일 발송** — provider 검증 어댑터와 메일 발송 어댑터가 아직 없어 호출하면 500 이다.
  (OTP 는 `local` 에서 발송을 건너뛰므로 고정코드로 로그인 자체는 된다.)
- **다른 서비스가 이 토큰을 검증하는 모습** — `auth-client` 가 그 일을 하지만, 아직 그것을 쓰는
  소비 서비스가 이 저장소에 없다. 지금은 `JwksVerificationIntegrationTest` 가 "남의 서비스" 역할을 대신한다.
- **게이트웨이** — URL 단위 인가(`authz_url_access`, `CheckAccessUseCase`)를 시행할 주체가 아직 없다.
