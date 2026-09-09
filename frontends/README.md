# 프론트엔드

auth-service 에 브라우저에서 직접 로그인해 보는 앱 두 개.

```
frontends/
  customer-portal/    고객 포털   :5173   Vite + React (npm)
  employee-admin/     직원 관리자 :5174   정적 HTML (serve.py)
  shared/             employee-admin 이 쓰는 공용 코드
  serve.py            employee-admin 용 개발 서버
```

두 앱의 형태가 다르다. **customer-portal 은 제대로 된 프론트엔드 프로젝트**(Vite + React,
가입/로그인/마이페이지 라우팅)이고, employee-admin 은 아직 정적 HTML 한 장이다 —
customer 쪽을 먼저 옮겼고 employee 는 그대로 남겨뒀다.

## 왜 앱이 둘인가

**realm 이 둘이기 때문이다.** 어드민(ADMIN)과 포털(PORTAL)은 계정도, 로그인 방식도, 서명 키도
다르다. 화면이 하나면 그 경계가 안 보인다.

포트를 나눈 것도 의도적이다. 브라우저에게 포트가 다르면 **다른 출처(origin)** 이고, 그래야
CORS 가 실제로 동작하는지 볼 수 있다 — 실제 배포에서도 두 앱은 다른 도메인에 놓인다.

| | 고객 포털 | 직원 관리자 |
|---|---|---|
| realm | `portal` | `admin` |
| 로그인 | 비밀번호 (이메일이 아이디) | **이메일 OTP** |
| 가입 | 스스로 가입 (`/realms/portal/register`) | 관리자가 등록 (`/admin/realms/admin/users`) |
| 기본 권한 | `CUSTOMER` 역할 자동 부여 | 없음 (관리자가 배정) |

직원이 OTP 인 것은 화면의 취향이 아니라 **서버가 그렇게 생겼기 때문이다** —
`RegisterEmployeeAccountService` 는 직원에게 이메일 계정만 만들고 비밀번호 계정을 만들지 않는다.

가입 경로도 하나다. `POST /api/auth/realms/{realm}/register` 가 그 realm 이 셀프 가입을 여는지
보고, 닫혀 있으면 **404** 다 — 어드민에서 열리면 아무나 자기 자신을 직원으로 만든다. 그 정책은
컨트롤러가 아니라 `Realm` enum 이 들고 있다(Keycloak 의 realm 설정 "User registration" 과 같다).
어드민 계정은 관리자가 `POST /api/auth/admin/realms/{realm}/users` 로 만든다 — 여기서는 경로의
realm 이 **대상**이고 호출자 realm 은 토큰이 정한다.

## 띄우기

### 1. auth-service

[루트 README](../README.md) 참고. 요약하면:

```bash
docker compose up -d          # DB 두 개

./mvnw -pl auth-service/auth-bootstrap -am install -DskipTests

cd auth-service/auth-bootstrap
DB_URL=jdbc:postgresql://localhost:55432/identity \
SPRING_PROFILES_ACTIVE=local \
java -jar target/auth-bootstrap-0.0.1-SNAPSHOT.jar
```

### 2. 고객 포털 (npm)

```bash
cd frontends/customer-portal
npm install          # 처음 한 번
npm run dev
```

포트를 바꿔 띄웠다면 `.env.local` 을 만들어 덮어쓰거나, 화면 오른쪽 아래
**"연결 대상"** 에서 바꿀 수 있다(브라우저에만 저장된다).

```
VITE_AUTH_BASE_URL=http://localhost:8090
VITE_CUSTOMER_BASE_URL=http://localhost:8081
```

### 3. 직원 관리자 (정적)

```bash
python frontends/serve.py employee-admin 5174
```

- 고객 포털 → http://localhost:5173
- 직원 관리자 → http://localhost:5174

**두 창을 나란히 띄워놓고 보는 것을 권한다.** 같은 auth-service 를 상대하는데 서로의 realm 에는
들어가지 못하는 것이 이 데모의 핵심이다.

## 눌러볼 것

### 고객 포털 (:5173)

화면이 셋이다 — `/signup`, `/login`, `/me`.

1. **가입** → 로그인 화면으로 넘어간다. 가입해도 토큰은 안 나온다(가입과 로그인은 별개 요청)
2. **로그인** → 토큰을 받고 마이페이지로
3. **마이페이지** — 여기가 본체다
   - **프로필** (customer-service): 처음엔 404 다. auth 에만 가입했고 customer 는 아직 모른다
   - **access / refresh 토큰**: 클레임을 하나씩 풀어서 무엇이고 왜 있는지 함께 보여준다.
     만료까지 남은 시간이 1초마다 줄어든다
   - **권한 조회 / 재발급 / 로그아웃**
   - **JWKS 보기** — 다른 서비스가 검증에 쓸 공개키. **포털 키 하나만 나온다.**
     JWKS 는 realm 마다 주소가 다르고 customer-service 는 그 주소만 알고 있어서,
     어드민 토큰은 검증할 수조차 없다(해당 `kid` 의 공개키가 없다)
4. **로그인 화면의 "직원 realm 으로 로그인 시도"**
   같은 이메일·비밀번호인데 401 이다. 조회가 `(subject_type, login_id)` 로 좁혀져 있어
   직원 서랍에는 이 계정이 아예 없다.
5. **로그인 화면의 "토큰 보관 방식" 스위치**
   기본은 메모리(새로고침하면 로그아웃)다. `localStorage` 로 바꿔보고 무엇을 내주는지 보라.

화면 아래 **요청 로그**에 어느 서버(auth/customer)를 언제 불렀는지 쌓인다.
**프로필을 부를 때 auth 가 등장하지 않는 것**을 확인해 보라 — customer-service 가
공개키로 직접 검증하기 때문이다.

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

**`shared/auth-client.js` 는 브라우저 쪽의 같은 자리다.** auth 의 내부를 모르고
토큰과 클레임만 주고받는다. 실제 프로젝트라면 사내 npm 패키지가 될 자리이고, 지금은
`serve.py` 가 두 앱에 같은 파일을 내보내 한 벌만 유지한다.

## 아직 없는 것

- **게이트웨이** — 두 앱 모두 auth-service 를 직접 부른다. URL 단위 인가
  (`authz_url_access`, `CheckAccessUseCase`)를 시행할 주체가 아직 없다.
- **직원 앱에서 다른 서비스 부르기** — 고객 포털은 customer-service 를 부르지만(그래서 토큰을
  들고 다른 서비스로 가는 흐름이 보인다) 직원 앱이 상대하는 서비스는 auth 뿐이다.
- **소셜 로그인** — provider 검증 어댑터가 없어 호출하면 500 이다.
