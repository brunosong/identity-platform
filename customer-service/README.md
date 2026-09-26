# customer-service

고객 프로필 API. **auth-service 가 발급한 토큰을 검증해 쓰는 소비 서비스**이고,
**Spring Security 리소스 서버**를 어떻게 붙이는지 보여주는 것이 이 서비스의 목적이다.

## auth 와 무엇을 주고받나

```
                  ┌───────────────┐
  ① 로그인 ────────>│ auth-service  │ 포털 개인키로 서명
                  └───────┬───────┘
  ② 토큰 <────────────────┘
                          │  ④ GET /realms/portal/.well-known/jwks.json
  ③ 토큰 ──────────┐      │       (포털 공개키만, 10분 캐시)
                  ▼      ▼
            ┌─────────────────────┐
            │  customer-service   │ 서명을 스스로 검증
            └──────────┬──────────┘
                       └─> 자기 DB (customer)
```

**요청마다 auth 를 부르지 않는다.** 공개키만 받아다 서명을 직접 확인한다. 그래서

- 요청당 왕복이 없다
- auth 가 잠시 죽어도 이미 발급된 토큰은 계속 통과한다
- auth 가 모든 요청 경로에 놓이지 않는다

이 서비스가 auth 에 대해 아는 것은 설정 두 줄뿐이다.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/portal   # realm 경계
          audiences: shop                                    # 시스템 경계
```

**첫 줄이 realm 경계다.** realm 이 발급자 이름 안에 있고, 공개키를 받아올 주소는 발급자의
discovery 문서(`{issuer}/.well-known/openid-configuration`)가 알려준다.

**둘째 줄이 시스템 경계다.** 발급자만 확인하면 그 realm 의 토큰이면 무엇이든 통한다.
`aud` 는 서비스가 아니라 **시스템**이라 order-service 도 같은 `shop` 을 요구한다. 그래서
서비스를 하나 붙여도 auth 설정은 바뀌지 않는다. 자세한 것은
[`docs/토큰-흐름.md`](../docs/토큰-흐름.md).

auth 의 모듈을 하나도 의존하지 않는다. **표준 라이브러리(Spring Security 리소스 서버)뿐이다.**
주고받는 것은 토큰 문자열과 그 안의 표준 클레임이고, 그것이 이 경계의 계약이다.

## 인증을 붙이는 자리

`SecurityConfiguration` **한 곳**이다. 필터가 모든 요청 앞에 있고 기본값이 거부다.

```java
.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
```

전에는 직접 만든 `AuthenticatedCaller` 를 컨트롤러마다 불렀다(`auth-client` 모듈). 그 방식은
**부르는 것을 잊으면 그대로 열린다** — 새 컨트롤러를 추가하면서 한 줄을 빠뜨리면 인증 없는 API 가
조용히 생긴다. 지금은 잊었을 때의 결과가 반대다: 아무것도 안 하면 401 이고, 열려면 명시해야 한다.

그래서 컨트롤러에 인증 코드가 **한 줄도 없다.** 거기 도달했다는 것이 곧 검증을 통과했다는 뜻이다.

```java
public ProfileResponse save(@AuthenticationPrincipal Jwt token, ...) {
    return ... customerProfiles.save(token.getSubject(), ...);
}
```

### 네 겹으로 막는다

| | 무엇을 보나 | 어디서 |
|---|---|---|
| **서명·만료·용도** | 이 토큰이 진짜 auth 가 만든 access 토큰인가 | Spring Security 필터 |
| **발급자·realm** | 우리 auth 의 포털 것인가 | **설정** (`issuer-uri`) |
| **대상·시스템** | 이 시스템 앞으로 발급된 토큰인가 | **설정** (`audiences`) |
| **소유권** | 이 데이터가 이 사람 것인가 | 컨트롤러 |

> **역할 검사는 없다.** 토큰에서 인가 클레임을 걷어내면서 함께 빠졌다. 지금은 인증만 통과하면
> 이 API 가 열린다. 소유권은 역할이 아니라 조회 키의 문제라 그대로 선다.

**realm 확인이 코드에 없다.** 사라진 것이 아니라 설정으로 옮겨갔다. JWKS 가 realm 별로 나뉘어 있어
이 서비스는 어드민 공개키를 아예 갖지 못하고, 그래서 어드민 토큰은 서명 검증에서 죽는다 —
이 서비스의 코드가 한 줄도 돌기 전에.

토큰에 `realm` 클레임은 없다. realm 마다 서명키가 다르니 *어느 키로 검증됐는지가 곧 realm* 이고,
그것을 표준 자리에서 밝히는 값이 `iss` 다. 그 `iss` 를 설정값과 대조하므로 **다른 배포**(staging 등)가
만든 토큰도 거부한다 — 그런 토큰은 realm 도 클레임도 다 같아서 발급자 이름만이 둘을 가른다.

**소유권은 토큰이 답할 수 없다.** 그래서 `/api/customers/me` 는 조회 키를 요청에서 받지 않고
토큰의 `sub` 로만 찾는다. 경로나 본문으로 받으면 남의 식별자를 적어 넣는 것으로 남의
프로필이 열린다. 게이트웨이가 있어도 이 확인은 대신해 줄 수 없다 — 게이트웨이는 "이 URL 에
들어와도 되는가"까지 알지만 "이 데이터가 이 사람 것인가"는 모른다.

## API

| | 조건 | 하는 일 |
|---|---|---|
| `GET /api/customers/me` | PORTAL 토큰 | 내 프로필 (없으면 404) |
| `PUT /api/customers/me` | PORTAL 토큰 | 내 프로필 생성/수정 |

**직원이 고객을 조회하는 API 는 여기 없다.** 그런 것이 필요해지면 **같은 코드를 어드민 realm
설정으로 한 벌 더 띄우는** 것이 표준적인 방법이다. 한 프로세스가 두 realm 의 키를 다 쥐기
시작하면 경계는 다시 코드의 몫이 되고, 한 곳에서 잊으면 뚫린다.

> 권한 코드는 auth 가 보관하지만 그 의미는 **자원을 가진 서비스**가 정한다. auth 는 그 코드가
> 무엇을 여는지 모르고, 이 서비스는 그 코드가 어떤 역할에 붙어 있는지 모른다. 그래서 코드
> 이름에 이 서비스의 자원을 넣는다(`PROFILE_READ`). 다만 <b>지금 그 코드를 읽는 곳은 없다</b>.
> 토큰에서 인가를 걷어냈고, 어디서 판정할지 정해지면 돌아온다.

## 자기 DB 를 쓴다

auth 와 **다른 데이터베이스**다. 서비스가 남의 테이블을 직접 읽기 시작하면 나눈 의미가 없어지고,
스키마를 바꿀 때마다 남의 배포를 기다려야 한다.

대가도 있다. `customer_profile.customer_id` 는 auth 의 `identity_principal` 을 가리키지만
**외래키를 걸 수 없다** — 다른 DB 이기 때문이다. 서비스를 나눈다는 것은 참조 무결성을 DB 에
맡기지 못하게 된다는 뜻이기도 하다. 그래서 "토큰이 가리키는 주체"만 믿고, 그 토큰은 auth 의
서명으로 확인한다.

## 띄우기

```bash
# 자기 DB — 저장소 루트의 docker compose 가 auth 것과 함께 띄운다
docker compose up -d

./mvnw -pl customer-service/customer-bootstrap -am install -DskipTests

cd customer-service/customer-bootstrap
DB_URL=jdbc:postgresql://localhost:55433/customer \
DB_USERNAME=customer DB_PASSWORD=customer \
AUTH_ISSUER=http://localhost:8080/realms/portal \
SPRING_PROFILES_ACTIVE=local \
java -jar target/customer-bootstrap-0.0.1-SNAPSHOT.jar
```

**auth-service 보다 먼저 떠도 된다.** `issuer-uri` 를 쓰면 Spring 이 디코더 생성을 첫 검증까지
미룬다(`SupplierJwtDecoder`) — 부팅할 때 auth 를 부르지 않는다. 그동안 들어온 요청은 검증에 실패해
401 이 된다 — 열린 채로 남지 않는 것이 중요하다.

> 포트가 겹치면 `SERVER_PORT` 로 옮기고, auth 를 옮겼다면 `AUTH_ISSUER` 도 함께 바꾼다.
> 발급자는 문자열 그대로 대조되므로 auth 의 `TOKEN_ISSUER` 와 **짝**이어야 한다.

## 직접 확인해 보기

```bash
AUTH=http://localhost:8080
CUST=http://localhost:8081/api/customers

# 토큰을 바로 내주는 로그인 API 는 없다. 앱이 하는 그대로 로그인 화면의 폼을 내고 code 를 바꾼다.
# PKCE 한 쌍은 RFC 7636 부록 B 의 예시다. 통합 테스트의 CodeFlowLogin 도 같은 걸음을 밟는다.
PKCE='-d code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM -d code_challenge_method=S256'
VERIFIER=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
PORTAL_APP='-d client_id=portal-17kqqi85h2ks -d redirect_uri=http://localhost:5173/login/callback'
ADMIN_APP='-d client_id=admin-ln4efwmg0tee -d redirect_uri=http://localhost:5174/login/callback'

# 폼 응답(303)의 Location 에서 code 를 꺼내 토큰 엔드포인트에서 바꾼다. $1=realm $2=앱 $3=폼 경로 $4..=폼 값
token() {
    realm=$1; app=$2; path=$3; shift 3
    code=$(curl -s -o /dev/null -w '%{redirect_url}' -X POST $AUTH/realms/$realm/$path $app $PKCE "$@" \
        | sed 's/.*code=\([^&]*\).*/\1/')
    curl -s -X POST $AUTH/realms/$realm/token -d grant_type=authorization_code -d code=$code \
        $app -d code_verifier=$VERIFIER \
        | python -c "import json,sys;print(json.load(sys.stdin)['access_token'])"
}
login()  { token portal "$PORTAL_APP" auth/login --data-urlencode "loginId=$1" -d password=pw12345678; }
signup() {   # 가입도 인증번호부터 받는다. local 은 고정코드 123456
    curl -s -o /dev/null -X POST $AUTH/realms/portal/auth/register/send-code $PORTAL_APP $PKCE \
        --data-urlencode "email=$1" -d name=$1
    token portal "$PORTAL_APP" auth/register --data-urlencode "email=$1" -d name=$1 -d code=123456 -d password=pw12345678
}
admin()  {
    curl -s -o /dev/null -X POST $AUTH/realms/admin/auth/send-code $ADMIN_APP $PKCE -d email=admin@example.com
    token admin "$ADMIN_APP" auth/login/otp -d email=admin@example.com -d code=123456   # local 은 고정코드
}

# 고객으로 가입한다. 가입하면 로그인까지 된 채로 code 가 나와서 토큰을 바로 받는다
TOKEN=$(signup hong@example.com)

# 그 토큰으로 이 서비스를 부른다 — auth 는 이 호출에 관여하지 않는다
curl -s $CUST/me -H "Authorization: Bearer $TOKEN"                       # 404 (아직 프로필 없음)
curl -s -X PUT $CUST/me -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Hong"}'               # 생성
curl -s $CUST/me -H "Authorization: Bearer $TOKEN"                       # 조회

curl -s -w ' [%{http_code}]' $CUST/me                                    # 401 토큰 없음
```

토큰 없이 부르면 표준 챌린지가 온다 — 직접 만든 401 본문이 아니다.

```
HTTP/1.1 401
WWW-Authenticate: Bearer
```

어드민 토큰으로 불러보면 **401** 이다. 이 서비스는 어드민 공개키가 없어 서명조차 확인하지 못한다.

```bash
ADMIN=$(admin)
curl -s -w ' [%{http_code}]' $CUST/me -H "Authorization: Bearer $ADMIN"   # 401
```

## 아직 없는 것

- **프로필이 만들어지는 경로** — 지금은 고객이 `PUT /me` 로 직접 만든다. 원래 설계는 auth 의
  `SubjectRegisteredEvent` 를 받아 만드는 것인데, 서비스 간 이벤트 전달(브로커)이 아직 없다.
  그래서 `customer-messaging` 모듈도 없다.
- **게이트웨이** — 프론트엔드가 auth 와 이 서비스를 각각 직접 부른다. 그래서 두 서비스가
  각자 CORS 를 밝혀야 한다.
- **서비스 간 호출** — 이 서비스가 또 다른 서비스를 부를 때 토큰을 어떻게 넘길지(전파냐
  자체 자격증명이냐)는 아직 다루지 않았다.
