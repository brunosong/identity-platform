package com.brunosong.identityplatform.customer.service.application.ports.in;

import com.brunosong.identityplatform.customer.service.domain.CustomerProfile;

import java.util.List;
import java.util.Optional;

/**
 * 고객 프로필 유스케이스.
 *
 * <p>여기에는 "누가 부를 수 있는가"가 없다. 그 판단은 토큰을 읽을 수 있는 웹 계층이 한다 —
 * 응용 계층이 토큰을 알면 이 서비스가 auth 의 토큰 형식에 묶인다.
 */
public interface CustomerProfileUseCase {

    Optional<CustomerProfile> find(String customerId);

    /** 없으면 만들고 있으면 고친다. 프로필은 주체당 하나뿐이라 생성과 수정을 가르지 않는다. */
    CustomerProfile save(String customerId, String name, String phoneNumber, String email);

    List<CustomerProfile> search(String keyword, int limit);
}
