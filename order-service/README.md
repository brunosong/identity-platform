# order-service

주문 API. **포털 realm 의 두 번째 소비 서비스**이고, 이 저장소에서 이 서비스가 있는 이유는
**서비스가 둘이 되고 나서야 보이는 것들**을 드러내려는 것이다.

customer-service 와 같은 레이어 구조, 같은 발급자, 같은 로그인으로 받은 같은 토큰을 쓴다.
그 서비스를 먼저 읽고 오면 여기서는 다른 점만 보면 된다.

## customer-service 와 무엇이 다른가

| | customer-service | order-service |
|---|---|---|
| 발급자(`issuer-uri`) | `.../realms/portal` | **같다** |
| 대상(`audiences`) | `shop` | **같다** (시스템이다) |
| 조회 키 | 토큰의 `sub` 뿐 | `sub` + **경로의 주문번호** |
| DB | `customer`(55433) | `orders`(55434) |
| 포트 | 8081 | 8082 |

두 가지가 여기서 처음 보인다.

## 1. 한 번 로그인한 토큰 하나가 두 서비스에 통한다

`aud` 가 **시스템**이기 때문이다. 두 서비스가 같은 `shop` 을 요구한다.

```yaml
# auth
token:
  realms:
    PORTAL:
      system: shop      # 이 realm 의 토큰이 향하는 시스템
```

```yaml
# customer-service 와 order-service 가 똑같이
spring.security.oauth2.resourceserver.jwt.audiences: shop
```

```json
{
  "iss": "http://localhost:8080/realms/portal",
  "aud": ["shop"],
  "sub": "cust-..."
}
```

토큰에 역할도 권한도 없다. 두 서비스를 가르는 것은 `aud` 가 아니라 **각자의 코드**다.
지금은 둘 다 인증만 요구한다.

> **시스템 이름을 realm 과 다르게 지었다.** 로그인 경로는 `/realms/portal` 인데 시스템은 `shop` 이다.
> 같은 글자를 쓰면 둘이 다른 개념이라는 사실이 이름에서 지워진다. realm 은 "어느 서랍에서 계정을
> 찾나" 이고 시스템은 "이 토큰을 누가 받아주나" 다.

### 네 층을 구분해야 이 표가 읽힌다

| | 예 | 토큰 어디 | 늘어날 때 auth 설정 |
|---|---|---|---|
| realm | `PORTAL` | `iss` (서명키가 가른다) | 바뀐다 (키페어) |
| 시스템 | `shop` | `aud` **단일값** | 바뀐다 |
| 서비스 | `customer-service`, `order-service` | **토큰에 없다** | **안 바뀐다** |
| 앱 | `customer-portal` | 토큰에 없다 | **auth 는 앱을 모른다** |

**시스템은 마이크로서비스의 집합이다.** 그 안에 서비스가 몇 개인지는 토큰도 앱도 모르고
DB(`authz_service`)만 안다.

realm 과 시스템이 1:1 이라 **auth 는 앱이 누구인지 묻지 않는다.** 한때 로그인 요청이 `clientId` 를
보내고 그 앱이 속한 시스템을 골랐는데, realm 이 경로에 있으니 새 정보가 없었다. 한 realm 에
시스템이 둘 이상 필요해지면 그때 고를 값을 다시 들인다.

### 왜 aud 에 서비스를 적으면 안 되나

처음에는 `aud: customer-service,order-service` 로 나열했다. 그때 두 가지가 아팠다.

**하나.** 서비스를 하나 붙일 때마다 **auth 를 재배포**해야 했다. 내부 분해가 토큰에 새어나가서,
포털 앱의 설정이 "뒤에 마이크로서비스가 몇 개인지" 를 알아야 했다.

**둘.** **이미 발급된 access 토큰에는 새 서비스가 없다.** order-service 를 띄워도 기존 로그인
사용자는 그 토큰이 만료돼 재발급될 때까지(기본 2시간) 새 서비스에 닿지 못했다. 재발급은
`issue()` 를 다시 타므로 다시 로그인할 필요까지는 없지만, 배포 직후 최대 2시간의 창이 생긴다.

지금은 `aud` 가 `shop` 으로 고정이라 둘 다 없다. 서비스를 붙이는 일은 DB 에 한 줄이다.

```sql
INSERT INTO authz_service (service_id, realm, system_id, service_name, ...)
VALUES ('order-service', 'PORTAL', 'shop', 'Order service', ...);
```

### 그래도 aud 를 버리지는 않는다

`aud` 가 아예 없으면 발급자만 맞는 토큰이 realm 의 **모든 것**에 통한다. 침해된 한 곳으로 들어온
토큰을 다른 곳에 그대로 재생할 수 있고, 늘수록 **가장 약한 하나가 realm 전체의 보안 수준**이 된다.
경계는 그대로 두고 그 눈금만 서비스에서 시스템으로 옮긴 것이다.

### 시스템을 여럿 적지 않는다

realm 하나에 시스템이 여럿일 수 있다. 하지만 **토큰 하나는 시스템 하나를 향한다.**
`aud: shop,loyalty` 같은 것을 쓰지 않는다.

통합 로그인은 **세션**이 맡는 일이다. 표준 흐름은 이렇다.

```
시스템 A 로 로그인  ->  auth 세션 생김  ->  A 용 토큰 (aud=A)
시스템 B 로 이동    ->  세션이 이미 있음 ->  화면 없이 B 용 토큰 (aud=B)
```

사용자 눈에는 로그인 한 번이고 토큰은 둘이다. OIDC 도 Keycloak 도 이렇게 돈다.

aud 에 나열하면 방금 고친 문제가 한 층 위에서 그대로 재현된다. 시스템을 늘릴 때 설정을 고쳐야
하고 기존 토큰은 새 시스템에 닿지 못한다. 거기에 시스템 경계까지 잃는다. 시스템은 보통 팀도
배포 주기도 다른데, `loyalty` 가 뚫리면 거기 들어온 토큰이 `shop` 에 그대로 통하게 된다.

> 두 번째 시스템이 실제로 생기면 refresh 로 다른 시스템용 access 를 받는 경로를 연다.
> 지금 미리 만들지 않았다.

### 설정을 빠뜨리면 부팅에서 죽는다

`token.realms.*.system` 이 없으면 뜨지 않는다. `aud` 없는 토큰은 만들 이유가 없는데, 설정 누락으로
조용히 약해지는 것보다 부팅에서 걸리는 편이 낫다.

**적는 것은 시스템이지 서비스가 아니다.** 서비스 목록은 DB 가 쥔다.

## 2. 남의 주문번호는 403 이 아니라 404 다

`/api/customers/me` 에는 조회 키를 적어 넣을 자리가 **아예 없었다.** 토큰의 `sub` 하나뿐이라
남의 것을 가리킬 방법이 없었다.

주문은 다르다. 사람당 여럿이라 주문번호를 경로로 받고, 그 자리에 **남의 주문번호를 적어 넣을 수
있다.** 그래서 조회 조건에 `sub` 를 함께 건다.

```java
orders.findOwned(token.getSubject(), orderId)
        .map(order -> ResponseEntity.ok(OrderResponse.of(order)))
        .orElseGet(() -> ResponseEntity.notFound().build());
```

주문번호만으로 찾는 길은 이 서비스 어디에도 없다. `OrderRepository` 에 그런 메서드를 두지
않았고, `JpaRepository.findById` 도 부르지 않는다. **잊을 자리를 만들지 않는 편이 확인을 잊지
말자고 다짐하는 것보다 낫다.**

**403 이 아니라 404 인 이유.** 403 은 "그 주문은 있는데 당신 것이 아니다" 를 알려준다.
주문번호를 훑으면서 403 과 404 를 세면 남의 주문이 몇 건인지, 어느 번호가 살아 있는지 알아낼 수
있다. 소유자를 조건에 함께 걸면 "없는 주문" 과 "남의 주문" 이 같은 결과가 되고, 응답은 그 둘을
구분하지 못한다.

**게이트웨이가 있어도 이 확인은 대신해 줄 수 없다.** 게이트웨이는 "이 URL 에 들어와도 되는가"
까지 알지만 "이 데이터가 이 사람 것인가" 는 모른다.

## 아직 인가는 없다

토큰에서 인가 클레임을 걷어냈다. 전에는 `resource_access` 의 이 서비스 칸을 authority 로 옮겨
`ORDER_READ` / `ORDER_WRITE` 를 요구했는데, 그 칸이 더 이상 오지 않는다.

```java
.authorizeHttpRequests(requests -> requests
        .anyRequest().authenticated())
```

**지금은 인증만 통과하면 이 API 들이 열린다.** 인가를 어디서 판정할지(게이트웨이, 이 서비스의
auth 조회, 토큰 재적재)는 아직 정하지 않았다.

권한 데이터 자체는 auth 에 그대로 있다(`authz_permission` 의 `(PORTAL, order-service, ORDER_READ)`
행). 없어진 것은 그것을 토큰으로 나르던 길뿐이다.

**위의 소유권 확인은 그대로 선다.** 그것은 역할이 아니라 조회 키의 문제였기 때문이다. 인가를
붙이든 빼든 남의 주문은 404 다.

## API

| | 조건 | 하는 일 |
|---|---|---|
| `GET /api/orders` | 인증 | 내 주문 목록 (최근 순) |
| `GET /api/orders/{orderId}` | 인증 + **소유권** | 내 주문 하나 (없거나 남의 것이면 404) |
| `POST /api/orders` | 인증 | 주문 |

주문자를 본문으로 받지 않는다. 받으면 남의 이름으로 주문할 수 있다. 금액도 받지 않는다.
단가 곱하기 수량으로 서버가 계산한다. 받으면 낼 값을 스스로 정할 수 있다.

**주문 취소는 없다.** 이 저장소의 주제가 인증과 인가라 결제나 배송 같은 상태 전이는 다루지
않는다. `OrderStatus` 에 값이 하나뿐인 것이 그 사실을 그대로 말한다.

## 자기 DB 를 쓴다

auth 와도, customer 와도 다른 데이터베이스다. `orders.customer_id` 는 customer-service 의
`customer_profile.customer_id` 와 같은 값이지만 **두 서비스는 서로의 표를 보지 않는다.**
같은 사람을 가리키는 이유는 토큰이 하나이기 때문이지, 어느 한쪽이 상대의 데이터를 읽기
때문이 아니다.

그래서 프로필 없이 주문만 있는 상태도 정상이다. 두 서비스는 서로를 기다리지 않는다.

## 띄우기

```bash
docker compose up -d      # order-pg 가 auth, customer 것과 함께 뜬다

./mvnw -pl order-service/order-bootstrap -am install -DskipTests

cd order-service/order-bootstrap
DB_URL=jdbc:postgresql://localhost:55434/orders \
DB_USERNAME=orders DB_PASSWORD=orders \
AUTH_ISSUER=http://localhost:8080/realms/portal \
SPRING_PROFILES_ACTIVE=local \
java -jar target/order-bootstrap-0.0.1-SNAPSHOT.jar
```

**auth-service 보다 먼저 떠도 된다.** `issuer-uri` 를 쓰면 Spring 이 디코더 생성을 첫 검증까지
미룬다(`SupplierJwtDecoder`). 그동안 들어온 요청은 검증에 실패해 401 이 된다.

> auth 를 다른 포트로 옮겼다면 `AUTH_ISSUER` 도 함께 바꾼다. 발급자는 문자열 그대로
> 대조되므로 auth 의 `TOKEN_ISSUER` 와 **짝**이어야 한다.

## 직접 확인해 보기

auth 와 customer-service, order-service 를 모두 띄우고 시작한다. 고객 계정은 시드에 없으므로
(부트스트랩되는 것은 직원뿐이다) 둘을 가입시켜 놓는다. 하나는 내 것, 하나는 남의 것이다.

```bash
AUTH=http://localhost:8080/api/auth
CUST=http://localhost:8081/api/customers
ORDER=http://localhost:8082/api/orders

login() {
    curl -s -X POST $AUTH/realms/portal/login -H 'Content-Type: application/json' \
        -d "{\"loginId\":\"$1\",\"password\":\"pw12345678\"}" \
        | python -c "import json,sys;print(json.load(sys.stdin)['tokens']['accessToken'])"
}

for who in hong kim; do
    curl -s -o /dev/null -X POST $AUTH/realms/portal/register \
        -H 'Content-Type: application/json' \
        -d "{\"email\":\"$who@example.com\",\"password\":\"pw12345678\",\"name\":\"$who\"}"
done

# 한 번만 로그인한다
TOKEN=$(login hong@example.com)

# 토큰 안을 들여다본다
python -c "
import base64, json, sys
p = sys.argv[1].split('.')[1]; p += '=' * (-len(p) % 4)
print(json.dumps(json.loads(base64.urlsafe_b64decode(p)), indent=2))
" "$TOKEN"
```

```json
{
  "iss": "http://localhost:8080/realms/portal",
  "aud": ["shop"],
  "sub": "2f231b51-7703-42b8-aa13-fa906fd57dc6",
  "type": "access",
  "iat": 1789476440,
  "exp": 1789483640
}
```

역할도 권한도 없다. `aud` 가 `shop` 하나라서 이 토큰이 두 서비스에 통하고, 서비스가 하나 더
붙어도 `aud` 는 그대로다.

그 토큰 하나로 두 서비스를 부른다.

```bash
curl -s $CUST/me    -H "Authorization: Bearer $TOKEN"    # 8081
curl -s $ORDER      -H "Authorization: Bearer $TOKEN"    # 8082, []

# 주문한다
ORDER_ID=$(curl -s -X POST $ORDER -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"productName":"키보드","quantity":2,"unitPrice":45000}' \
  | python -c "import json,sys;print(json.load(sys.stdin)['orderId'])")

curl -s $ORDER/$ORDER_ID -H "Authorization: Bearer $TOKEN"
```

다른 계정으로 로그인해 그 주문번호를 그대로 넣어 본다. **인증은 멀쩡히 통과했는데도 404 다.**

```bash
OTHER=$(login kim@example.com)

curl -s -o /dev/null -w '%{http_code}\n' $ORDER/$ORDER_ID -H "Authorization: Bearer $OTHER"
curl -s -o /dev/null -w '%{http_code}\n' $ORDER/does-not-exist -H "Authorization: Bearer $OTHER"
```

```
404
404
```

둘이 같다. 응답만 봐서는 그 주문번호가 있는지 없는지 알 수 없다.

어드민 토큰은 **401** 이다. customer-service 와 같은 이유로, 이 서비스는 어드민 공개키가 없어
서명조차 확인하지 못한다.

```bash
curl -s -o /dev/null -X POST $AUTH/realms/admin/login/email-otp/send-code \
  -H 'Content-Type: application/json' -d '{"email":"admin@example.com"}'
ADMIN=$(curl -s -X POST $AUTH/realms/admin/login/email-otp \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","verificationCode":"123456"}' \
  | python -c "import json,sys;print(json.load(sys.stdin)['tokens']['accessToken'])")

curl -s -o /dev/null -w '%{http_code}\n' $ORDER -H "Authorization: Bearer $ADMIN"   # 401
```

## 아직 없는 것

- **주문의 생애.** 결제도 배송도 취소도 없다. 이 저장소의 주제가 아니다.
- **customer-service 와의 연결.** 주문 화면에 고객 이름을 보여주려면 서비스 간 호출이 필요하고,
  그때 토큰을 어떻게 넘길지(전파냐 자체 자격증명이냐)는 아직 다루지 않았다. 지금은 두 서비스가
  서로를 부르지 않는다.
- **게이트웨이.** 프론트엔드가 세 서비스를 각각 직접 부른다. 그래서 셋 다 각자 CORS 를 밝혀야
  한다. 서비스를 하나 붙일 때마다 그 비용이 어디서 늘어나는지가 `CorsPolicy` 에 보인다.
- **프론트엔드 화면.** `frontends/customer-portal` 에 주문 화면은 아직 없다.
