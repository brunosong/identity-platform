package com.brunosong.identityplatform.auth.service.domain.identity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import lombok.Getter;

import java.time.Instant;

/**
 * 신원의 표시 속성 — <b>이 사람을 뭐라고 부르는가.</b>
 *
 * <h2>왜 {@link Principal} 에 열로 붙이지 않나</h2>
 * {@code Principal} 은 인증 커널이다. 거기에 표시용 값이 섞이면 "이름이 바뀌었다" 는 이유로 인증
 * 애그리거트를 열게 되고, 가장 중심 테이블에 잠금 경합과 감사 소음이 쌓인다. 자격증명을 별도
 * 애그리거트로 뗀 것과 같은 이유다.
 *
 * <h2>왜 auth 가 이것을 갖나</h2>
 * 가입 폼이 받는 이름·전화번호가 갈 곳이 필요하다. 전에는 {@code SubjectRegisteredEvent} 에 실려
 * 나가기만 하고 <b>아무도 저장하지 않아 증발했다.</b>
 *
 * <p>그리고 운영이 이것을 요구한다. 관리 콘솔에서 {@code a3f9e1c2-…} 만 보이면 계정 잠금 해제도,
 * 부정 로그인 조사도, 고객센터 대응도 전부 "이 UUID 가 누구냐" 를 다른 시스템에 묻는 일이 된다.
 * Keycloak 의 {@code USER_ENTITY} 가 {@code FIRST_NAME}/{@code LAST_NAME} 을 갖는 것과 같은 자리다.
 *
 * <h2>연락처가 아니다</h2>
 * 여기 전화번호는 <b>표시용</b>이다. 로그인 수단으로서의 번호(SMS 인증)는 자격증명이라 별도
 * 애그리거트가 되어야 하고, 배송지 연락처는 업무 서비스({@code customer_profile}) 것이다.
 * 같은 값이 여러 곳에 있어도 중복이 아니다 — <b>쓰임이 소유를 정한다.</b>
 *
 * <p>그래서 여기 값을 고쳐도 로그인 수단은 바뀌지 않는다. 반대 방향도 마찬가지다.
 */
@Getter
public class PrincipalProfile {

    private final PrincipalId principalId;
    private final String name;
    private final String phoneNumber;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PrincipalProfile(PrincipalId principalId, String name, String phoneNumber,
                             Instant createdAt, Instant updatedAt) {
        this.principalId = principalId;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PrincipalProfile create(PrincipalId principalId, String name, String phoneNumber) {
        if (principalId == null) {
            throw new IllegalArgumentException("principalId must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Instant now = Instant.now();
        return new PrincipalProfile(principalId, name.trim(), normalize(phoneNumber), now, now);
    }

    public static PrincipalProfile restore(PrincipalId principalId, String name, String phoneNumber,
                                           Instant createdAt, Instant updatedAt) {
        return new PrincipalProfile(principalId, name, phoneNumber, createdAt, updatedAt);
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
