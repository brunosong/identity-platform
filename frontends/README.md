# 프론트엔드

auth-service 에 브라우저에서 직접 로그인해 보는 앱 두 개.

```
frontends/
  customer-portal/    고객 포털   :5173   비밀번호 로그인
  employee-admin/     직원 관리자 :5174   이메일 OTP 로그인 + RBAC 조회
  shared/             두 앱이 함께 쓰는 코드
  serve.py            앱 하나를 자기 포트로 띄우는 개발 서버
```

빌드 도구가 없다. ES 모듈과 `fetch` 만 쓰므로 정적 서버만 있으면 바로 뜬다.

## 왜 앱이 둘인가

**realm 이 둘이기 때문이다.** 직원(EMPLOYEE)과 고객(CUSTOMER)은 계정도, 로그인 방식도, 서명 키도
다르다. 화면이 하나면 그 경계가 안 보인다.

포트를 나눈 것도 의도적이다. 브라우저에게 포트가 다르면 **다른 출처(origin)** 이고, 그래야
CORS 가 실제로 동작하는지 볼 수 있다 — 실제 배포에서도 두 앱은 다른 도메인에 놓인다.

| | 고객 포털 | 직원 관리자 |
|---|---|---|
| realm | `customer` | `employee` |
| 로그인 | 비밀번호 (이메일이 아이디) | **이메일 OTP** |
| 가입 | 스스로 가입 | 관리자가 등록 |
| 기본 권한 | `CUSTOMER` 역할 자동 부여 | 없음 (관리자가 배정) |

직원이 OTP 인 것은 화면의 취향이 아니라 **서버가 그렇게 생겼기 때문이다** —
`RegisterEmployeeAccountService` 는 직원에게 이메일 계정만 만들고 비밀번호 계정을 만들지 않는다.

## 띄우기

### 1. auth-service

`examples/README.md` 참고. 요약하면:

```bash
docker run -d --rm --name auth-pg \
  -e POSTGRES_DB=identity -e POSTGRES_USER=identity -e POSTGRES_PASSWORD=identity \
  -p 55432:5432 postgres:16-alpine

./mvnw -pl auth-service/auth-bootstrap -am install -DskipTests

cd auth-service/auth-bootstrap
DB_URL=jdbc:postgresql://localhost:55432/identity \
SPRING_PROFILES_ACTIVE=local \
java -jar target/auth-bootstrap-0.0.1-SNAPSHOT.jar
```

### 2. 두 앱

터미널 두 개에서:

```bash
python frontends/serve.py customer-portal 5173
python frontends/serve.py employee-admin 5174
```

- 고객 포털 → http://localhost:5173
- 직원 관리자 → http://localhost:5174

**두 창을 나란히 띄워놓고 보는 것을 권한다.** 같은 auth-service 를 상대하는데 서로의 realm 에는
들어가지 못하는 것이 이 데모의 핵심이다.

## 눌러볼 것

### 고객 포털 (:5173)

1. **새 이메일 채우기 → 가입 → 로그인**
   토큰이 오고, 그 안에 실린 클레임(`realm`, `authLs`, `exp`)이 그대로 보인다.
2. **내 권한 조회 / 재발급 / 로그아웃**
3. **직원 realm 으로 로그인 시도** ← 여기가 핵심
   같은 이메일·비밀번호인데 401 이다. 자격증명 조회가 `(subject_type, login_id)` 로 좁혀져 있어
   직원 서랍에는 이 계정이 아예 없다.
4. **JWKS 보기**
   다른 서비스가 검증에 쓸 공개키. **두 realm 의 키가 함께 나온다** —
   그래서 서명이 맞다고 realm 이 맞는 것은 아니다.

### 직원 관리자 (:5174)

1. **인증번호 발송 → 로그인** (`admin@example.com` / `123456`)
   로컬 시드가 심어둔 부트스트랩 관리자다. 직원 계정은 관리자만 만들 수 있어
   (`AUTHZ_MANAGE` 필요) 최초 한 명은 데이터로 심어야 한다 — 닭과 달걀 문제다.
   local 프로파일은 메일을 보내지 않고 고정코드 `123456` 을 쓴다.
2. **역할 목록 / 권한 목록 / 리비전** — `AUTHZ_MANAGE` 가 있어야 열린다
3. **직원 등록**
   새 직원을 만든다. 그 사람도 바로 OTP 로그인이 되지만 역할이 없어 관리 화면은 열리지 않는다.
   **다른 브라우저 창에서 그 이메일로 로그인해 보면** 권한 없는 직원이 어떻게 보이는지 알 수 있다.
4. **고객 realm 으로 OTP 로그인 시도** — 같은 이메일인데 실패한다
5. **직원 realm 에서 소셜 로그인 시도** — 404. 소셜은 신원을 새로 만들기 때문에 고객 realm 에서만 연다

각 화면 아래 **"주고받은 요청"** 에 모든 호출과 상태코드가 쌓인다. 무슨 요청이 나갔는지가
이 데모의 절반이다.

## 알아둘 것

**토큰은 메모리에만 둔다.** `localStorage` 에 넣지 않는다 — 쿠키의 `httpOnly` 보호를 포기한
대가로, 토큰이 오래 남는 자리에 두지 않는 것이 최소한의 완화다. 새로고침하면 로그아웃된다.

**화면을 감추는 것은 방어가 아니다.** 직원 앱은 `AUTHZ_MANAGE` 가 없으면 관리 패널을 숨기지만,
그건 편의일 뿐이다. 실제 방어는 서버가 한다(`RbacAdminAccess`). 브라우저에서 저 코드를 고쳐
패널을 열어도 API 는 403 이다.

**`shared/auth-client.js` 는 백엔드의 `auth-client` 모듈과 같은 자리다.** auth 의 내부를 모르고
토큰과 클레임만 주고받는다. 실제 프로젝트라면 사내 npm 패키지가 될 자리이고, 지금은
`serve.py` 가 두 앱에 같은 파일을 내보내 한 벌만 유지한다.

## 아직 없는 것

- **게이트웨이** — 두 앱 모두 auth-service 를 직접 부른다. URL 단위 인가
  (`authz_url_access`, `CheckAccessUseCase`)를 시행할 주체가 아직 없다.
- **auth 가 아닌 다른 서비스** — 그래서 "토큰을 들고 다른 서비스를 부른다"는 부분이 아직 안 보인다.
  `auth-client`(백엔드)가 그 검증을 맡지만 그것을 쓰는 소비 서비스가 없다.
- **소셜 로그인** — provider 검증 어댑터가 없어 호출하면 500 이다.
