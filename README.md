# identity-platform

MSA 에서 인증·인가를 어떻게 나누는지 직접 만들어보는 샘플.

- **auth-service** — 로그인시키고 토큰을 발급한다. 공개키를 JWKS 로 내보낸다
- **customer-service** — 그 토큰을 **auth 에 묻지 않고** 스스로 검증해 쓰는 소비 서비스
- **frontends** — 브라우저에서 실제로 눌러보는 화면

두 realm(**ADMIN** / **PORTAL**)이 한 auth 를 공유하되 서로의 영역에는 들어가지 못한다.
그 격리가 어떻게 지켜지는지가 이 저장소의 주제다.

> **realm 과 주체 유형은 다른 값이다.** realm(`ADMIN`/`PORTAL`)은 *영역*이 어떤 정책을 갖는지를,
> 주체 유형(`EMPLOYEE`/`CUSTOMER`)은 *사람*이 누구인지를 말한다. 어드민 영역에 직원이 살고,
> 포털 영역에 고객이 산다. `ADMIN.failOpen()` 은 읽히지만 `EMPLOYEE.failOpen()` 은 읽히지 않는다 —
> 사람이 fail-open 일 수는 없기 때문이다.

---

## 포트 한눈에

| | 주소 | |
|---|---|---|
| auth 데이터베이스 | `localhost:55432` | DB `identity` |
| customer 데이터베이스 | `localhost:55433` | DB `customer` |
| auth-service | `localhost:8080` | |
| customer-service | `localhost:8081` | |
| 고객 포털 | `localhost:5173` | Vite + React |
| 직원 관리자 | `localhost:5174` | 정적 HTML |

> **8080 이 이미 쓰이고 있다면** 아래 "포트가 겹칠 때" 를 보라. 흔한 상황이고, 바꿔야 할 곳이 몇 군데 있다.

---

## 1. 데이터베이스 띄우기

```bash
docker compose up -d
```

이것 하나로 둘 다 뜬다. 상태 확인:

```bash
docker compose ps
```

```
NAME          STATUS                    PORTS
auth-pg       Up 11 seconds (healthy)   0.0.0.0:55432->5432/tcp
customer-pg   Up 11 seconds (healthy)   0.0.0.0:55433->5432/tcp
```

`healthy` 가 될 때까지 기다렸다가 앱을 띄운다. `starting` 인 동안 앱을 올리면 커넥션 오류로 죽는다.

### 왜 두 개인가

**서비스마다 자기 DB 를 갖는 것이 MSA 의 기본 규칙이다.** 하나로 합치면 서비스는 나눴는데
스키마로 다시 묶여서, 테이블 하나 바꾸려면 남의 배포를 기다려야 한다.

대가도 있다. `customer_profile.customer_id` 는 auth 의 `identity_principal` 을 가리키지만
**외래키를 걸 수 없다** — 다른 데이터베이스이기 때문이다. 서비스를 나눈다는 것은 참조 무결성을
DB 에 맡기지 못하게 된다는 뜻이기도 하다. 그래서 "서명된 토큰이 가리키는 주체" 를 믿는다.

### 접속 정보

| | auth | customer |
|---|---|---|
| 호스트 포트 | `55432` | `55433` |
| 데이터베이스 | `identity` | `customer` |
| 사용자 / 비밀번호 | `identity` / `identity` | `customer` / `customer` |

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

# customer 쪽
docker exec -it customer-pg psql -U customer -d customer
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
Migrating schema "public" to version "9000 - local seed data"
Migrating schema "public" to version "9001 - local seed employee"
Migrating schema "public" to version "9002 - local seed customer service permission"
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

> auth-service 보다 먼저 떠도 된다. 공개키는 첫 검증 때 받아온다. 그동안 들어온 요청은
> 검증에 실패해 401 이 된다 — 열린 채로 남지 않는 것이 중요하다.

---

## 3. 프론트엔드 띄우기

```bash
cd frontends/customer-portal
npm install        # 처음 한 번
npm run dev        # → http://localhost:5173
```

직원 화면은 아직 정적 HTML 이다.

```bash
python frontends/serve.py employee-admin 5174
```

자세한 내용은 [`frontends/README.md`](frontends/README.md).

---

## 포트가 겹칠 때

`8080` 은 흔히 다른 앱이 쓰고 있다. auth 를 옮기면 **따라서 바꿔야 할 곳이 셋**이다.

```bash
# ① auth-service — 뜨는 포트와 토큰에 실릴 발급자(iss)를 같이 옮긴다
SERVER_PORT=8090 TOKEN_ISSUER=http://localhost:8090 \
DB_URL=jdbc:postgresql://localhost:55432/identity SPRING_PROFILES_ACTIVE=local \
java -jar target/auth-bootstrap-0.0.1-SNAPSHOT.jar

# ② customer-service — 공개키를 받아올 주소 (realm 마다 다르다)
AUTH_JWKS_URI=http://localhost:8090/realms/portal/.well-known/jwks.json \
DB_URL=jdbc:postgresql://localhost:55433/customer \
DB_USERNAME=customer DB_PASSWORD=customer SPRING_PROFILES_ACTIVE=local \
java -jar target/customer-bootstrap-0.0.1-SNAPSHOT.jar
```

**③ 프론트엔드** — 화면 오른쪽 아래 **"연결 대상"** 에서 바꾸거나,
`frontends/customer-portal/.env.local` 에:

```
VITE_AUTH_BASE_URL=http://localhost:8090
```

> 하나라도 빠뜨리면 조용히 401 이 된다. 특히 `TOKEN_ISSUER` — issuer 는 문자열 그대로
> 비교되므로 `http://localhost:8080` 과 `http://localhost:8090` 은 다른 값이다.

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
| 고객 (PORTAL realm) | 없음 — 화면에서 직접 가입 |

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
| [`customer-service/README.md`](customer-service/README.md) | **소비 서비스가 토큰을 검증하는 법** |
| [`auth-service/auth-client/README.md`](auth-service/auth-client/README.md) | 다른 서비스가 쓰는 검증 라이브러리 |
