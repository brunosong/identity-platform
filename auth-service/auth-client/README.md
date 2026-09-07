# auth-client

다른 서비스가 auth-service 가 발급한 토큰을 검증할 때 쓰는 라이브러리.

## 어떻게 신뢰가 서는가

```
                    ┌───────────────┐
                    │  auth-service │  개인키로 서명
                    └───────┬───────┘
                            │ GET /.well-known/jwks.json  (공개키, 인증 불필요)
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ order-svc│  │ pay-svc  │  │ ship-svc │   각자 서명을 검증
        └──────────┘  └──────────┘  └──────────┘
```

**소비 서비스는 요청마다 auth 에 묻지 않는다.** 공개키만 받아다 자기가 서명을 확인한다.

- 요청당 왕복이 없다
- auth 가 잠시 죽어도 이미 발급된 토큰은 계속 검증된다
- auth 가 모든 요청의 경로에 놓이지 않는다(단일 장애점이 되지 않는다)

공개키를 설정 파일로 뿌리지 않는 이유는 **키 교체**다. 파일로 배포하면 키를 바꿀 때 모든 서비스를
다시 배포해야 한다. JWKS 로 두면 auth 만 바꾸고, 소비 서비스는 모르는 `kid` 를 만났을 때 다시 받아온다.

## 쓰는 법

```xml
<dependency>
    <groupId>com.brunosong</groupId>
    <artifactId>auth-client</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

```yaml
auth:
  client:
    jwks-uri: http://auth-service:8080/.well-known/jwks.json
```

이것만 있으면 `AuthTokenVerifier` 빈이 생긴다. `jwks-uri` 가 없으면 자동설정이 켜지지 않으므로,
auth 토큰을 쓰지 않는 서비스에 이 의존이 섞여도 부팅이 깨지지 않는다.

```java
@RestController
@RequiredArgsConstructor
class OrderController {

    private final AuthTokenVerifier verifier;

    @GetMapping("/api/orders")
    List<Order> myOrders(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        AuthenticatedToken token = verifier.verifyAuthorizationHeader(authorization)
                .filter(t -> t.isRealm("CUSTOMER"))          // ← 이 줄을 빼면 안 된다
                .orElseThrow(() -> new UnauthorizedException("로그인이 필요합니다."));

        if (!token.hasPermission("ORDER_READ")) {
            throw new ForbiddenException("권한이 없습니다.");
        }
        return orders.findBySubjectId(token.subjectId());
    }
}
```

## 반드시 알아야 할 것 세 가지

### 1. 서명이 맞다고 realm 이 맞는 것은 아니다

두 realm(EMPLOYEE / CUSTOMER)의 공개키가 **같은 JWKS 에 함께** 들어 있다. 그래서 직원 토큰도
고객 서비스에서 서명 검증은 통과한다. **`realm` 클레임을 반드시 확인해야 한다.**

```java
verifier.verify(token, "CUSTOMER")   // 또는 token.isRealm("CUSTOMER")
```

이 확인을 빠뜨리면 직원 토큰으로 고객 API 가 열린다.

### 2. subjectId 는 realm 안에서만 유일하다

realm 마다 다른 체계에서 발급된다(직원은 사번 성격의 값, 고객은 UUID). **realm 없이 subjectId 만으로
사람을 특정하지 말 것.** 저장할 때도 `(realm, subjectId)` 를 함께 두는 편이 안전하다.

### 3. 권한은 발급 시점의 값이다

`permissions` 는 토큰에 실려 있다. 그래서 빠르지만, **발급 뒤의 권한 변경은 이 토큰에 반영되지 않는다** —
다음 갱신 때 들어온다. 즉시 회수가 필요하면 access 토큰 TTL 을 짧게 잡아야 한다.

`rbacRev` 는 realm 전역 정책의 리비전이다. auth 가 역할-권한 매핑을 바꾸면 올라가므로, 엄격한 서비스는
이 값이 자기가 아는 최신보다 낮으면 갱신을 요구할 수 있다.

## 키 교체를 어떻게 견디나

`JwksKeySource` 는 **모르는 `kid` 를 만나면 캐시 TTL 과 무관하게 한 번 다시 받아온다.** auth 가 새 키로
서명하기 시작하면 소비 서비스는 처음 보는 kid 를 만나는데, 그때 바로 새 키를 얻는다. TTL 만료를
기다렸다면 그 사이 모든 요청이 401 이 된다.

다만 재조회에는 최소 간격(30초)이 있다. 없으면 아무 문자열이나 kid 로 넣어 보내는 것만으로 auth 에
요청을 무한히 발생시킬 수 있다.

받아온 키에서 사라진 kid 도 지우지 않는다 — 교체 중에는 옛 kid 로 서명된 토큰이 아직 만료 전이다.

## 이 라이브러리가 auth 의 도메인을 의존하지 않는 이유

`auth-domain`, `auth-application` 을 하나도 의존하지 않는다. 소비 서비스는 auth 의 도메인 모델
(`Principal`, `Realm`, `SubjectType`…)을 알 필요가 없고, 알게 되면 auth 의 내부 변경이 남의 서비스
컴파일을 깨뜨린다. 주고받는 것은 **토큰 문자열과 그 안의 클레임뿐**이고 그것이 이 경계의 계약이다.
그래서 realm 도 enum 이 아니라 문자열이다.

저장소 안에 두는 이유는 토큰 형식을 **발급하는 쪽이 소유**하기 때문이다. 클레임 이름이 바뀌면 발급기와
이 검증기가 같은 커밋에서 함께 바뀌어야 한다. 그것을 `JwksVerificationIntegrationTest` 가 지킨다 —
실제로 발급하고 실제 HTTP 로 JWKS 를 받아 검증한다.

## 아직 없는 것

- **서블릿 필터 / Spring Security 연동** — 지금은 검증기만 준다. 인증 결과를 `SecurityContext` 에
  넣는 방식은 서비스마다 달라 일부러 정하지 않았다.
- **토큰 폐기 확인** — 로그아웃해도 access 토큰은 만료까지 유효하다(무상태 JWT). 즉시 회수가 필요하면
  auth 에 폐기 목록이 필요하고, 그러면 이 라이브러리도 그것을 물어봐야 한다.
