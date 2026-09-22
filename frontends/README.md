# 프론트엔드

브라우저에서 직접 로그인해 보는 앱 넷. 셋은 auth-service 를 상대하고, 하나는 같은 자리에
Keycloak 을 놓으면 무엇이 달라지는지 보려고 만든 것이다.

```
frontends/
  portal/    고객 포털   :5173   Vite + React (npm)
  backoffice/     직원 관리자 :5174   Vite + React (npm)
  portal-keycloak/    포털(Keycloak) :5175   Vite + React (npm)
  shop/               쇼핑몰     :5176   순수 HTML/JS (빌드 없음)
  shared/             (더 이상 쓰이지 않음 — 정적 backoffice 의 잔재)
  serve.py            (더 이상 쓰이지 않음 — 정적 backoffice 용 개발 서버)
```

`shop` 만 성격이 다르다. 포털과 **같은 realm(PORTAL)** 의 두 번째 앱이고, 통합 로그인이 실제로
도는지 눈으로 보려고 세운 것이다. 자세한 것은 [shop/README.md](shop/README.md).

## 띄우기

auth-service 가 먼저 떠 있어야 한다.

```
./mvnw -pl auth-service/auth-bootstrap spring-boot:run -Dspring-boot.run.profiles=local
```

앱은 각자 띄운다. 쇼핑몰만 빌드가 없어서 정적 서버로 띄운다.

```
cd frontends/portal     && npm run dev            # :5173
cd frontends/backoffice && npm run dev            # :5174
cd frontends/shop       && python -m http.server 5176
```

포트를 바꾸면 그 출처를 `application-local.yml` 의 `app.cors.allowed-origins` 에 넣어야 하고,
쇼핑몰은 돌아갈 주소가 `oauth_client` 에 등록된 값과 글자 그대로 같아야 한다.

두 앱이 같은 형태다. 전에는 backoffice 이 정적 HTML 한 장이었고 `serve.py` 가 그것을
띄웠다 — customer 쪽을 먼저 Vite + React 로 옮기고 employee 는 남겨뒀던 것이다. 지금은 둘 다
같은 구조라서, 한쪽에서 배운 것이 다른 쪽에서 그대로 읽힌다.

> `shared/` 와 `serve.py` 는 이제 아무도 쓰지 않는다. 지우지 않고 둔 것은 두 형태를 비교해 볼
> 수 있게 하기 위해서다.

## 채널 — 경로가 누가 부르는지를 말한다

auth-service 의 API 는 **두 채널**로 갈려 있고, 두 앱이 각각 다른 채널을 쓴다.

```
/api/admin/**          관리자 채널 — 전부 AUTHZ_MANAGE 필요
/api/**  (그 외)        사용자 채널 — 로그인 전 호출자가 있다
/realms/{realm}/.well-known/**   다른 서비스가 가져가는 공개 메타데이터
```

| | 고객 포털 | 직원 관리자 |
|---|---|---|
| 쓰는 채널 | 사용자 채널만 | 사용자 채널(로그인·본인 가입) **+** 관리자 채널 |

**접두어를 관리자 쪽에만 둔 것이 요점이다.** 둘 다 붙이면 어느 쪽도 아닌 경로가 생길 수 있고,
그런 경로는 게이트웨이 규칙 어디에도 걸리지 않는다. 한쪽만 두면 모든 경로가 반드시 둘 중
하나로 갈린다 — 게이트웨이는 `/api/admin/**` 한 줄만 알면 된다.

대가는 **잊으면 공개되는 쪽이 기본값**이라는 것이다. 그래서 관리자 채널은 경로에만 기대지 않고
서비스 안에서 한 번 더 막는다(`RbacAdminAccess`). 토큰 없이 `/api/admin/**` 을 부르면 401 이다 —
게이트웨이가 있든 없든.

## 왜 앱이 둘인가

**realm 이 둘이기 때문이다.** 어드민(ADMIN)과 포털(PORTAL)은 계정도, 로그인 방식도, 서명 키도
다르다. 화면이 하나면 그 경계가 안 보인다.

포트를 나눈 것도 의도적이다. 브라우저에게 포트가 다르면 **다른 출처(origin)** 이고, 그래야
CORS 가 실제로 동작하는지 볼 수 있다 — 실제 배포에서도 두 앱은 다른 도메인에 놓인다.

| | 고객 포털 | 직원 관리자 |
|---|---|---|
| realm | `portal` | `admin` |
| 로그인 | 비밀번호 · 이메일 OTP | **이메일 OTP** |
| 셀프 가입 | 비밀번호 **또는** 이메일 인증 | 이메일 인증만 |
| 관리자 등록 | 없음 | 있음 (`POST /api/admin/realms/admin/users`) |
| 기본 권한 | `CUSTOMER` 역할 자동 부여 | 없음 (관리자가 배정) |

직원에게 비밀번호 가입이 없는 것은 화면의 취향이 아니라 **서버가 그렇게 생겼기 때문이다** —
직원 계정은 이메일 계정만 갖는다. 어드민에 비밀번호 가입을 열면 이 서비스가 만들 수 없는
계정을 요구하는 경로가 생긴다.

**어느 realm 이 어떤 방식을 여는지는 `Realm` enum 이 들고 있다.**

```java
ADMIN (false, Set.of(EMAIL_OTP))
PORTAL(true,  Set.of(PASSWORD, EMAIL_OTP))
```

켜짐/꺼짐을 따로 두지 않는다 — **비어 있으면 닫힌 것**이다. 두 값을 따로 두면 "열려 있는데
방식이 없다" 는 모순된 상태가 표현된다. 자기 realm 에 열리지 않은 방식은 **404** 다(403 이 아니다 —
"여기에도 그 API 가 있긴 한데 막혀 있다" 를 알려줄 이유가 없다).

**어드민 셀프 가입을 열어도 되는 이유는 역할이 막기 때문이다.** 스스로 만든 직원 신원에는 어떤
역할도 붙지 않는다(기본 역할을 주는 리스너가 고객 realm 만 상대한다). 로그인은 되지만 아무것도
열지 못하는 껍데기이고, 쓸 수 있게 만드는 것은 여전히 `AUTHZ_MANAGE` 를 가진 운영자다 —
신원을 만드는 일과 권한을 주는 일이 갈려 있다는 것이 여기서 값을 한다.

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
cd frontends/portal
npm install          # 처음 한 번
npm run dev
```

포트를 바꿔 띄웠다면 `.env.local` 을 만들어 덮어쓰거나, 화면 오른쪽 아래
**"연결 대상"** 에서 바꿀 수 있다(브라우저에만 저장된다).

```
VITE_AUTH_BASE_URL=http://localhost:8090
VITE_CUSTOMER_BASE_URL=http://localhost:8081
```

### 3. 직원 관리자 (npm)

```bash
cd frontends/backoffice
npm install          # 처음 한 번
npm run dev
```

여기도 `.env.local` 이나 화면 오른쪽 아래 **"연결 대상"** 으로 주소를 바꿀 수 있다.
이 앱이 부르는 서버는 auth 하나뿐이다.

```
VITE_AUTH_BASE_URL=http://localhost:8090
```

### 4. 포털 (Keycloak)

이 앱만 상대가 다르다. auth-service 가 아니라 Keycloak 이다. 먼저 Keycloak 을 띄운다.

```bash
docker run -d --name keycloak -p 8999:8080   -e KC_BOOTSTRAP_ADMIN_USERNAME=admin   -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin   quay.io/keycloak/keycloak:26.0 start-dev
```

콘솔(http://localhost:8999, admin/admin)에서 `portal` realm 을 만들고 그 안에 client 를 만든다.

| 항목 | 값 |
|---|---|
| Client ID | `portal-keycloak` |
| Client authentication | Off (public client 라 시크릿이 없다) |
| Standard flow | 켬 |
| Direct access grants | 끔 (이 앱은 비밀번호를 직접 받지 않는다) |
| Valid redirect URIs | `http://localhost:5175/*` |
| Web origins | `http://localhost:5175` |

사용자도 한 명 만든다. Credentials 탭에서 비밀번호를 걸 때 **Temporary 를 끈다.**
켜두면 첫 로그인에서 비밀번호 변경 화면이 먼저 뜬다.

```bash
cd frontends/portal-keycloak
npm install
npm run dev
```

- 고객 포털 → http://localhost:5173
- 직원 관리자 → http://localhost:5174
- 포털(Keycloak) → http://localhost:5175

**두 창을 나란히 띄워놓고 보는 것을 권한다.** 같은 auth-service 를 상대하는데 서로의 realm 에는
들어가지 못하는 것이 이 데모의 핵심이다.

## 눌러볼 것

### 고객 포털 (:5173)

화면이 셋이다 — `/signup`, `/login`, `/me`.

1. **가입** — 방식이 둘이다. 탭으로 갈라 놓았다.
   - **비밀번호로 가입** — 즉시 끝나지만 **이메일 소유를 확인하지 않는다.** 남의 주소를 적어도 계정이 만들어진다
   - **이메일 인증으로 가입** — 인증번호를 받아내야 하므로 그 주소의 주인만 가입할 수 있다.
     대신 정한 비밀번호가 없어 이후 OTP 로만 로그인한다
   어느 쪽이든 가입해도 토큰은 안 나온다(가입과 로그인은 별개 요청)
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

화면이 다섯이다 — `/login`, `/`(홈), `/signup`(본인 가입), `/users/new`(관리자가 등록), `/rbac`.

**계정을 만드는 화면이 둘인 것**이 고객 포털과 다른 점이다. 본인이 만드는 것과 관리자가 만드는
것이 다른 화면이고, 만들어지는 계정의 성질도 다르다 — 앞은 이메일이 확인되지만 권한이 없고,
뒤는 권한을 줄 수 있지만 이메일이 확인되지 않는다.

1. **로그인 — 이메일 OTP** (`admin@example.com` / `123456`)
   두 단계다. 인증번호 발송에는 토큰이 없고, 토큰은 코드 검증에서만 나온다. 요청 로그에서
   두 번의 호출로 보이는 것이 요점이다.
   로컬 시드가 심어둔 부트스트랩 관리자다 — 직원 계정은 관리자만 만들 수 있어
   (`AUTHZ_MANAGE` 필요) 최초 한 명은 데이터로 심어야 한다. 닭과 달걀 문제다.
2. **홈** — 토큰을 뜯어서 본다. `realm` 클레임이 없는 것을 확인해 보라. realm 은 `iss` 안에
   있고, 무엇보다 어느 키로 검증됐는지가 곧 realm 이다.
   **JWKS 보기** 를 누르면 어드민 키 하나만 나온다 — 포털 주소에는 이 키가 없다.
3. **직원 가입** (`/signup`, 로그인 전에 누구나)
   **본인이 이메일만으로 스스로 만든다.** 비밀번호를 정하지 않는다. 만들고 나서 로그인해 보면
   **권한이 하나도 없다** — 그것이 이 화면을 열어둘 수 있는 이유다.
   같은 화면의 **"어드민 realm 에 비밀번호 가입 시도"** 는 404 다 — 403 이 아닌 것이 요점이다.
4. **직원 등록** (`/users/new`, `AUTHZ_MANAGE` 필요)
   관리자가 남의 계정을 만든다. 셀프 가입과 달리 **인증번호가 없어 이메일 소유가 확인되지 않고**,
   대신 **초기 역할을 함께 줄 수 있다.**
   **다른 브라우저 창에서 그 이메일로 로그인해 보면** 권한 있는/없는 직원이 어떻게 보이는지 알 수 있다.
5. **인가 정책** — 역할·권한·리비전 조회. `AUTHZ_MANAGE` 가 있어야 열린다.
6. **로그인 화면의 "포털 realm 으로 OTP 로그인 시도"** — 같은 이메일인데 실패한다.
   발송은 어느 쪽이든 202 다(계정 열거 방지). 갈리는 곳은 그다음이다.
7. **홈의 "어드민 realm 에서 소셜 로그인 시도"** — 소셜은 신원을 새로 만들기 때문에
   고객 realm 에서만 연다.

각 화면 아래 **"주고받은 요청"** 에 모든 호출과 상태코드가 쌓인다. 무슨 요청이 나갔는지가
이 데모의 절반이다.

### 포털 (Keycloak) (:5175)

화면이 하나다. 앞의 두 앱과 **로그인하는 방법이 다른 것**이 전부다.

1. **"Keycloak 으로 로그인"** 을 누르면 이 앱을 떠나 Keycloak 로그인 화면으로 간다.
   주소창이 8999 로 바뀌는 것을 보라. **아이디와 비밀번호를 받는 것이 이 앱이 아니다.**
   그래서 이 앱의 코드에는 비밀번호를 다루는 자리가 아예 없다.
2. 로그인하면 `?code=...` 를 달고 돌아온다. 그 code 를 토큰으로 바꾸는 요청이 한 번 더 나간다.
   **화면 맨 아래 "흐름 기록"** 에 떠나기 전과 돌아온 뒤가 이어서 쌓인다.
3. **토큰이 셋이다.** `access_token`, `id_token`, `refresh_token`. 앞의 두 앱에는 `id_token` 이
   없다. `scope` 에 `openid` 를 넣어야 나오고, 이것은 "누가 로그인했는가" 를 앱에게 알려주는
   토큰이라 API 호출에 쓰는 물건이 아니다.
4. `aud` 가 `account` 인 것을 확인해 보라. 서비스 이름이 아니다. Keycloak 기본값이라 그렇고,
   실제 서비스를 넣으려면 audience mapper 를 따로 붙여야 한다.
5. **로그아웃**은 토큰을 버리는 것으로 끝나지 않는다. Keycloak 에 SSO 세션이 남아 있어서,
   그냥 토큰만 버리고 다시 로그인하면 비밀번호를 묻지 않고 통과한다. 그래서 Keycloak 의
   로그아웃 주소로 브라우저를 보낸다.

**PKCE 가 무엇을 막는지는 `src/oidc.js` 주석에 적어두었다.** code 는 주소창에 실려 오기 때문에
흘릴 구멍이 많은데, 주워도 `code_verifier` 가 없으면 토큰으로 바꿀 수 없다는 것이 요점이다.

## 알아둘 것

**토큰은 메모리에만 둔다.** `localStorage` 에 넣지 않는다 — 쿠키의 `httpOnly` 보호를 포기한
대가로, 토큰이 오래 남는 자리에 두지 않는 것이 최소한의 완화다. 새로고침하면 로그아웃된다.

**화면을 감추는 것은 방어가 아니다.** 직원 앱은 `AUTHZ_MANAGE` 가 없으면 관리 패널을 숨기지만,
그건 편의일 뿐이다. 실제 방어는 서버가 한다(`RbacAdminAccess`). 브라우저에서 저 코드를 고쳐
패널을 열어도 API 는 403 이다.

**두 앱이 `src/api/` 를 각자 갖는다.** 지금은 거의 같은 코드가 두 벌 있다 — 실제
프로젝트라면 사내 npm 패키지로 뽑을 자리다. 다만 뽑고 나면 "이 앱이 auth 에 대해 무엇을
아는가" 가 패키지 안으로 숨어서, 학습용으로는 각자 들고 있는 편이 읽힌다.

## 아직 없는 것

- **게이트웨이** — 두 앱 모두 auth-service 를 직접 부른다. URL 단위 인가
  (`authz_url_access`, `CheckAccessUseCase`)를 시행할 주체가 아직 없다.
- **직원 앱에서 다른 서비스 부르기** — 고객 포털은 customer-service 를 부르지만(그래서 토큰을
  들고 다른 서비스로 가는 흐름이 보인다) 직원 앱이 상대하는 서비스는 auth 뿐이다.
- **소셜 로그인** — provider 검증 어댑터가 없어 호출하면 500 이다.
