# identity-platform

MSA 에서 인증·인가를 어떻게 나누는지 직접 만들어보는 샘플.

- **auth-service** — 로그인시키고 토큰을 발급한다. 공개키를 JWKS 로 내보낸다
- **customer-service** — 그 토큰을 **auth 에 묻지 않고** 스스로 검증해 쓰는 소비 서비스
- **order-service** — 같은 realm 의 **두 번째** 소비 서비스. 서비스가 둘이 되고 나서야 보이는 것들
- **frontends** — 브라우저에서 실제로 눌러보는 화면

두 realm(**ADMIN** / **PORTAL**)이 한 auth 를 공유하되 서로의 영역에는 들어가지 못한다.
그 격리가 어떻게 지켜지는지가 이 저장소의 주제다.

> **토큰은 네 층을 구분한다.** realm(`iss`, 서명키가 가른다) / 시스템(`aud`) /
> 서비스(`resource_access` 의 칸) / 앱(`azp`). **`aud` 는 시스템이지 서비스가 아니다.**
> 그래서 마이크로서비스를 늘려도 토큰 모양도 auth 설정도 바뀌지 않는다.
> [자세히](order-service/README.md#1-한-번-로그인한-토큰-하나가-두-서비스에-통한다)
>
> **realm 이 유일한 파티션 키다.** 신원·자격증명·역할·권한·URL규칙이 전부 이 값으로 나뉜다.
> 전에는 identity 쪽이 `SubjectType`(EMPLOYEE/CUSTOMER)으로, authz 쪽이 realm 으로 나뉘었는데
> 두 값은 끝까지 1:1 이었다 — 같은 분할선에 이름이 둘이었을 뿐이라 하나로 모았다.
> Keycloak 도 `USER_ENTITY.REALM_ID` 하나로 간다.
>
> **두 realm 모두 화이트리스트다.** 등록된 URL 규칙만 통과하고, 규칙 없는 보호 경로는 거부한다.
> 잊었을 때의 결과가 "막힌다" 여야지 "열린다" 여서는 안 되기 때문이다.

---

## 포트 한눈에

| | 주소 | |
|---|---|---|
| auth 데이터베이스 | `localhost:55432` | DB `identity` |
| customer 데이터베이스 | `localhost:55433` | DB `customer` |
| order 데이터베이스 | `localhost:55434` | DB `orders` |
| auth-service | `localhost:8080` | |
| customer-service | `localhost:8081` | |
| order-service | `localhost:8082` | |
| 고객 포털 | `localhost:5173` | Vite + React |
| 직원 관리자 | `localhost:5174` | Vite + React |

> **8080 이 이미 쓰이고 있다면** 아래 "포트가 겹칠 때" 를 보라. 흔한 상황이고, 바꿔야 할 곳이 몇 군데 있다.

---

## 1. 데이터베이스 띄우기

```bash
docker compose up -d
```

이것 하나로 셋 다 뜬다. 상태 확인:

```bash
docker compose ps
```

```
NAME          STATUS                    PORTS
auth-pg       Up 11 seconds (healthy)   0.0.0.0:55432->5432/tcp
customer-pg   Up 11 seconds (healthy)   0.0.0.0:55433->5432/tcp
order-pg      Up 11 seconds (healthy)   0.0.0.0:55434->5432/tcp
```

`healthy` 가 될 때까지 기다렸다가 앱을 띄운다. `starting` 인 동안 앱을 올리면 커넥션 오류로 죽는다.

### 왜 따로따로인가

**서비스마다 자기 DB 를 갖는 것이 MSA 의 기본 규칙이다.** 하나로 합치면 서비스는 나눴는데
스키마로 다시 묶여서, 테이블 하나 바꾸려면 남의 배포를 기다려야 한다.

대가도 있다. `customer_profile.customer_id` 는 auth 의 `identity_principal` 을 가리키지만
**외래키를 걸 수 없다** — 다른 데이터베이스이기 때문이다. 서비스를 나눈다는 것은 참조 무결성을
DB 에 맡기지 못하게 된다는 뜻이기도 하다. 그래서 "서명된 토큰이 가리키는 주체" 를 믿는다.

### 접속 정보

| | auth | customer | order |
|---|---|---|---|
| 호스트 포트 | `55432` | `55433` | `55434` |
| 데이터베이스 | `identity` | `customer` | `orders` |
| 사용자 / 비밀번호 | `identity` / `identity` | `customer` / `customer` | `orders` / `orders` |

호스트 포트를 `5432` 가 아니라 `55432` 로 옮겨둔 것은, 이미 PostgreSQL 이 깔려 있어도
부딪히지 않게 하기 위해서다.

### 안을 들여다보기

```bash
# 컨테이너 안에서 psql
docker exec -it auth-pg psql -U identity -d identity

# 테이블 목록
\dt

# 예: 심어둔 개발용 계정과 역할
select subject_type, subject_id, status from identity_principal;
select realm, role_code, role_name from authz_role;
select * from flyway_schema_history;      -- 어떤 마이그레이션이 적용됐나

\q
```

```bash
# 한 줄로 질의만
docker exec auth-pg psql -U identity -d identity -c "select count(*) from identity_principal;"

# customer / order 쪽
docker exec -it customer-pg psql -U customer -d customer
docker exec -it order-pg psql -U orders -d orders
```

### 스키마를 갈아엎고 다시 보기

스키마는 **Flyway 가 만든다**(`ddl-auto: validate`). 마이그레이션을 고쳐 다시 보고 싶으면
데이터를 통째로 지우고 앱을 다시 띄우면 된다.

```bash
docker compose down -v      # -v 가 볼륨(데이터)까지 지운다
docker compose up -d
```

> `-v` 없이 `down` 하면 **데이터가 남는다.** 그 상태로 이미 적용된 마이그레이션 파일을 고치면
> Flyway 가 체크섬 불일치로 부팅을 막는다 — 나간 마이그레이션은 고치지 말고 새로 추가하라는 뜻이다.

### 내리기

```bash
docker compose down         # 컨테이너만
docker compose down -v      # 데이터까지
```

<details>
<summary>compose 없이 <code>docker run</code> 으로 하려면</summary>

```bash
docker run -d --rm --name auth-pg \
  -e POSTGRES_DB=identity -e POSTGRES_USER=identity -e POSTGRES_PASSWORD=identity \
  -p 55432:5432 postgres:16-alpine

docker run -d --rm --name customer-pg \
  -e POSTGRES_DB=customer -e POSTGRES_USER=customer -e POSTGRES_PASSWORD=customer \
  -p 55433:5432 postgres:16-alpine

docker run -d --rm --name order-pg \
  -e POSTGRES_DB=orders -e POSTGRES_USER=orders -e POSTGRES_PASSWORD=orders \
  -p 55434:5432 postgres:16-alpine
```

`--rm` 이라 컨테이너를 지우면 데이터도 사라진다. 매번 깨끗한 상태로 시작하고 싶을 때는 이쪽이 편하다.
</details>

---

## 2. 백엔드 띄우기

```bash
./mvnw install -DskipTests
```

**auth-service** (터미널 1)

```bash
cd auth-service/auth-bootstrap
DB_URL=jdbc:postgresql://localhost:55432/identity \
SPRING_PROFILES_ACTIVE=local \
java -jar target/auth-bootstrap-0.0.1-SNAPSHOT.jar
```

로그에 이게 보여야 한다. 스키마와 개발용 데이터가 함께 들어간다.

```
Migrating schema "public" to version "1 - baseline schema"
Migrating schema "public" to version "2 - authz service"
Migrating schema "public" to version "9000 - local seed data"
Migrating schema "public" to version "9001 - local seed employee"
Migrating schema "public" to version "9002 - local seed order"
Started AuthServiceApplication
```

**customer-service** (터미널 2)

```bash
cd customer-service/customer-bootstrap
DB_URL=jdbc:postgresql://localhost:55433/customer \
DB_USERNAME=customer DB_PASSWORD=customer \
SPRING_PROFILES_ACTIVE=local \
java -jar target/customer-bootstrap-0.0.1-SNAPSHOT.jar
```

**order-service** (터미널 3)

```bash
cd order-service/order-bootstrap
DB_URL=jdbc:postgresql://localhost:55434/orders \
DB_USERNAME=orders DB_PASSWORD=orders \
SPRING_PROFILES_ACTIVE=local \
java -jar target/order-bootstrap-0.0.1-SNAPSHOT.jar
```

> auth-service 보다 먼저 떠도 된다. 공개키는 첫 검증 때 받아온다. 그동안 들어온 요청은
> 검증에 실패해 401 이 된다 — 열린 채로 남지 않는 것이 중요하다.

> 두 소비 서비스가 **같은 발급자**와 **같은 `aud`**(`shop`)를 본다. `aud` 는 서비스가 아니라
> **시스템**이라서, 고객은 한 번 로그인하고 그 토큰 하나로 둘 다 쓴다. 서비스를 하나 더 붙여도
> auth 설정은 바뀌지 않는다. 그 구조는 [`order-service/README.md`](order-service/README.md) 에 있다.

---

## 3. 프론트엔드 띄우기

고객 포털과 직원 관리자, 둘 다 같은 방식이다.

```bash
cd frontends/customer-portal
npm install        # 처음 한 번
npm run dev        # → http://localhost:5173
```

```bash
cd frontends/employee-admin
npm install        # 처음 한 번
npm run dev        # → http://localhost:5174
```

**두 창을 나란히 띄워놓고 보는 것을 권한다.** 같은 auth-service 를 상대하는데 서로의 realm 에는
들어가지 못하는 것이 이 저장소의 주제다.

자세한 내용은 [`frontends/README.md`](frontends/README.md).

---

## 포트가 겹칠 때

`8080` 은 흔히 다른 앱이 쓰고 있다. auth 를 옮기면 **따라서 바꿔야 할 곳이 셋**이다.

```bash
# ① auth-service — 뜨는 포트와 토큰에 실릴 발급자(iss)를 같이 옮긴다
SERVER_PORT=8090 TOKEN_ISSUER=http://localhost:8090 \
DB_URL=jdbc:postgresql://localhost:55432/identity SPRING_PROFILES_ACTIVE=local \
java -jar target/auth-bootstrap-0.0.1-SNAPSHOT.jar

# ② 소비 서비스 — 상대할 발급자 (realm 이 이름 안에 있고, 공개키 주소도 여기서 유도된다)
#    두 서비스가 같은 값을 본다. 둘 중 하나만 바꾸면 그쪽만 401 이 된다.
AUTH_ISSUER=http://localhost:8090/realms/portal \
DB_URL=jdbc:postgresql://localhost:55433/customer \
DB_USERNAME=customer DB_PASSWORD=customer SPRING_PROFILES_ACTIVE=local \
java -jar target/customer-bootstrap-0.0.1-SNAPSHOT.jar

AUTH_ISSUER=http://localhost:8090/realms/portal \
DB_URL=jdbc:postgresql://localhost:55434/orders \
DB_USERNAME=orders DB_PASSWORD=orders SPRING_PROFILES_ACTIVE=local \
java -jar target/order-bootstrap-0.0.1-SNAPSHOT.jar
```

**③ 프론트엔드** — 화면 오른쪽 아래 **"연결 대상"** 에서 바꾸거나,
`frontends/customer-portal/.env.local` 과 `frontends/employee-admin/.env.local` 에:

```
VITE_AUTH_BASE_URL=http://localhost:8090
```

> 하나라도 빠뜨리면 조용히 401 이 된다. 특히 `TOKEN_ISSUER` 와 `AUTH_ISSUER` 는 **짝**이다 —
> auth 가 토큰에 적는 발급자와 customer-service 가 기대하는 발급자가 같아야 한다. 문자열 그대로
> 비교되므로 `http://localhost:8080` 과 `http://localhost:8090` 은 다른 발급자다.

무엇이 8080 을 쓰고 있는지 확인하려면:

```bash
# Windows (PowerShell)
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object OwningProcess

# macOS / Linux
lsof -i :8080
```

---

## 개발용 계정

`local` 프로파일이 시드로 심어둔다. **운영에서는 절대 이 프로파일을 쓰지 않는다.**

| | |
|---|---|
| 직원 관리자 (ADMIN realm) | `admin@example.com` · 이메일 OTP, 고정코드 **`123456`** |
| 고객 (PORTAL realm) | 없음 — 화면에서 직접 가입(`POST /api/auth/realms/portal/register`) |

계정을 만드는 방법은 realm 이 정한다.

| realm | 셀프 가입 | 관리자 등록 |
|---|---|---|
| PORTAL | 비밀번호 **또는** 이메일 인증 | — |
| ADMIN | 이메일 인증만 | 있음 (역할까지 배정) |

어느 쪽이든 **비밀번호를 정하는 것은 포털뿐**이다 — 직원 계정은 이메일 계정만 갖는다.
어드민 셀프 가입으로 만든 계정에는 역할이 하나도 붙지 않아, 로그인은 되지만 아무것도 열지
못한다. 쓸 수 있게 만드는 것은 `AUTHZ_MANAGE` 를 가진 운영자다.

직원 계정은 관리자만 만들 수 있어서(`AUTHZ_MANAGE` 권한 필요) **최초 한 명은 데이터로 심어야 한다** —
닭과 달걀 문제다. 실제 운영에서도 최초 관리자는 이렇게 넣는다.

`local` 프로파일은 메일을 보내지 않고 고정코드 `123456` 을 쓴다.

---

## 전부 내리기

```bash
# 각 터미널 Ctrl+C
docker compose down       # 데이터를 남기려면
docker compose down -v    # 깨끗하게
```

---

## 더 읽을 것

| | |
|---|---|
| [`examples/README.md`](examples/README.md) | 터미널로 전 구간 밟아보기 (`customer-login.sh`) |
| [`frontends/README.md`](frontends/README.md) | 화면에서 눌러볼 것 |
| [`docs/토큰-흐름.md`](docs/토큰-흐름.md) | **로그인 요청이 토큰이 되고 401/403 으로 갈리기까지 전 구간** |
| [`customer-service/README.md`](customer-service/README.md) | **소비 서비스가 토큰을 검증하는 법** |
| [`order-service/README.md`](order-service/README.md) | **서비스가 둘이 되면 달라지는 것**: 통합 로그인, 소유권, 역할 분리 |
| [`auth-service/auth-client/README.md`](auth-service/auth-client/README.md) | 다른 서비스가 쓰는 검증 라이브러리 |
