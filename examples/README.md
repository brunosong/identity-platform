# 예제

auth-service 를 실제로 띄우고 고객(CUSTOMER) 로그인을 처음부터 끝까지 밟아보는 예제.

- `customer-login.sh` — 터미널에서 전 구간을 순서대로 밟는다. 각 단계가 왜 그렇게 되는지 함께 찍는다.
- `customer-web/` — 브라우저에서 직접 눌러보는 페이지. **다른 출처**에서 부르므로 CORS 까지 포함된다.

## 1. 띄우기

### PostgreSQL

```bash
docker run -d --rm --name auth-pg \
  -e POSTGRES_DB=identity -e POSTGRES_USER=identity -e POSTGRES_PASSWORD=identity \
  -p 55432:5432 postgres:16-alpine
```

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
| 3 | 내 권한 조회 | 이후 요청은 `Authorization: Bearer` |
| 4 | 직원 realm 로그인 | **같은 아이디·비밀번호인데 401** — realm 격리 |
| 5 | 없는 realm / 없는 방식 | 404 |
| 6 | 재발급 | 고객 refresh 를 직원 realm 에 내밀면 서명에서 걸린다 |
| 7 | JWKS | 다른 서비스가 검증에 쓸 공개키 |
| 8 | 로그아웃 | |

## 3. 브라우저 예제

정적 페이지라 아무 정적 서버로나 띄우면 된다. **8080 이 아닌 다른 포트여야** 한다 — 출처가 달라야
CORS 가 실제로 동작하는지 볼 수 있기 때문이다.

```bash
cd examples/customer-web
python -m http.server 3000
```

`http://localhost:3000` 을 연다.

`auth-service` 의 `local` 프로파일에 이 출처가 이미 허용돼 있다:

```yaml
app:
  cors:
    allowed-origins: http://localhost:3000,http://127.0.0.1:3000
```

**다른 포트로 띄우려면 이 목록에 추가해야 한다.** 없으면 브라우저가 요청을 막고, 페이지에 그
사실이 안내된다. (허용되지 않은 출처는 서버가 403 으로 거절한다.)

### 페이지에서 눌러볼 것

- **가입 → 로그인** — 토큰을 받고 그 안에 실린 클레임(`realm`, `authLs`, `exp`)이 그대로 보인다
- **직원 realm 으로 로그인 시도** — 같은 자격증명인데 거부된다
- **내 권한 조회 / 재발급 / 로그아웃**
- **JWKS 보기** — 두 realm 의 공개키가 함께 나온다

아래 "주고받은 요청" 칸에 모든 호출과 상태코드가 쌓인다.

> 토큰은 **페이지의 메모리에만** 둔다. `localStorage` 에 넣지 않는다 — 쿠키의 `httpOnly` 보호를
> 포기한 대가로, 토큰이 오래 남는 자리에 두지 않는 것이 최소한의 완화다. 새로고침하면 로그아웃된다.

## 4. 정리

```bash
docker rm -f auth-pg
```

## 이 예제로 보이지 않는 것

- **소셜 로그인 / OTP 메일 발송** — provider 검증 어댑터와 메일 발송 어댑터가 아직 없어 호출하면 500 이다.
  (OTP 는 `local` 에서 발송을 건너뛰므로 고정코드로 로그인 자체는 된다.)
- **다른 서비스가 이 토큰을 검증하는 모습** — `auth-client` 가 그 일을 하지만, 아직 그것을 쓰는
  소비 서비스가 이 저장소에 없다. 지금은 `JwksVerificationIntegrationTest` 가 "남의 서비스" 역할을 대신한다.
- **게이트웨이** — URL 단위 인가(`authz_url_access`, `CheckAccessUseCase`)를 시행할 주체가 아직 없다.
