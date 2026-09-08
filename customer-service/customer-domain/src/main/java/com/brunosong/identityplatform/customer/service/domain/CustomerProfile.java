package com.brunosong.identityplatform.customer.service.domain;

import lombok.Getter;

import java.time.Instant;

/**
 * 고객 프로필.
 *
 * <p><b>이 서비스는 자격증명을 갖지 않는다.</b> 비밀번호도 이메일 인증도 auth-service 의 것이고,
 * 여기 있는 것은 "그 사람이 누구인지"에 대한 이 서비스의 몫뿐이다. 그래서 로그인시키는 코드가 없다 —
 * 들어온 토큰이 진짜인지만 확인하고(auth-client) 그 안의 subjectId 로 이 프로필을 찾는다.
 *
 * <p>{@code customerId} 는 auth 가 채번한 subjectId 다. 이 서비스가 따로 채번하지 않는다 —
 * 두 곳에서 번호를 매기면 그 둘을 잇는 표가 또 필요해지고, 그 표가 틀어지는 날이 온다.
 *
 * <p>이메일은 표시용으로만 둔다. 로그인 식별자로서의 이메일은 auth 가 소유하므로, 여기 값이 낡아도
 * 로그인에는 영향이 없다. 바꿔 말해 <b>여기서 이메일을 고쳐도 로그인 이메일은 바뀌지 않는다.</b>
 */
@Getter
public class CustomerProfile {

    private final String customerId;
    private String name;
    private String phoneNumber;
    private String email;
    private final Instant createdAt;
    private Instant updatedAt;

    private CustomerProfile(String customerId, String name, String phoneNumber, String email,
                            Instant createdAt, Instant updatedAt) {
        this.customerId = customerId;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CustomerProfile create(String customerId, String name, String phoneNumber, String email) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        requireName(name);
        Instant now = Instant.now();
        return new CustomerProfile(customerId, name.trim(), normalize(phoneNumber), normalize(email), now, now);
    }

    public static CustomerProfile restore(String customerId, String name, String phoneNumber, String email,
                                          Instant createdAt, Instant updatedAt) {
        return new CustomerProfile(customerId, name, phoneNumber, email, createdAt, updatedAt);
    }

    /** 프로필 수정. 식별자와 생성 시각은 바뀌지 않는다. */
    public void change(String name, String phoneNumber, String email) {
        requireName(name);
        this.name = name.trim();
        this.phoneNumber = normalize(phoneNumber);
        this.email = normalize(email);
        this.updatedAt = Instant.now();
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /** 빈 문자열과 null 을 한 가지로 모은다. 둘을 섞어 두면 조회 조건이 갈린다. */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
