package com.brunosong.identityplatform.customer.service.web.api;

import com.brunosong.identityplatform.customer.service.domain.CustomerProfile;

import java.time.Instant;

/**
 * 프로필 응답.
 *
 * <p>도메인 객체를 그대로 내보내지 않는다. 도메인이 바뀔 때마다 API 계약이 함께 흔들리면
 * 부르는 쪽이 이유 없이 깨진다.
 */
public record ProfileResponse(
        String customerId,
        String name,
        String phoneNumber,
        String email,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProfileResponse of(CustomerProfile profile) {
        return new ProfileResponse(profile.getCustomerId(), profile.getName(), profile.getPhoneNumber(),
                profile.getEmail(), profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
