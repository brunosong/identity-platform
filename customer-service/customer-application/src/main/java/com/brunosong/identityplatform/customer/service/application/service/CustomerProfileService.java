package com.brunosong.identityplatform.customer.service.application.service;

import com.brunosong.identityplatform.customer.service.application.ports.in.CustomerProfileUseCase;
import com.brunosong.identityplatform.customer.service.application.ports.out.CustomerProfileRepository;
import com.brunosong.identityplatform.customer.service.domain.CustomerProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 고객 프로필 응용 서비스.
 *
 * <p>토큰도 realm 도 모른다. 받은 customerId 로 일할 뿐이고, 그 값이 믿을 만한지는 부르는 쪽이 이미
 * 확인했다. 그래서 이 계층은 auth 가 어떻게 생겼는지 몰라도 되고, 인증 방식이 바뀌어도 손대지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CustomerProfileService implements CustomerProfileUseCase {

    /** 목록 조회 상한. 없으면 한 번의 호출이 테이블 전체를 끌고 온다. */
    private static final int MAX_LIMIT = 100;

    private final CustomerProfileRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerProfile> find(String customerId) {
        return repository.findById(customerId);
    }

    @Override
    @Transactional
    public CustomerProfile save(String customerId, String name, String phoneNumber, String email) {
        CustomerProfile profile = repository.findById(customerId)
                .map(existing -> {
                    existing.change(name, phoneNumber, email);
                    return existing;
                })
                .orElseGet(() -> CustomerProfile.create(customerId, name, phoneNumber, email));
        return repository.save(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerProfile> search(String keyword, int limit) {
        return repository.search(keyword, Math.clamp(limit, 1, MAX_LIMIT));
    }
}
