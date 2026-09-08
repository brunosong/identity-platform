package com.brunosong.identityplatform.customer.service.dataaccess.adapter;

import com.brunosong.identityplatform.customer.service.application.ports.out.CustomerProfileRepository;
import com.brunosong.identityplatform.customer.service.dataaccess.entity.CustomerProfileJpaEntity;
import com.brunosong.identityplatform.customer.service.dataaccess.repository.CustomerProfileJpaRepository;
import com.brunosong.identityplatform.customer.service.domain.CustomerProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** {@link CustomerProfileRepository} 영속성 어댑터. */
@Component
@RequiredArgsConstructor
public class CustomerProfilePersistenceAdapter implements CustomerProfileRepository {

    private final CustomerProfileJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerProfile> findById(String customerId) {
        return repository.findById(customerId).map(CustomerProfilePersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public CustomerProfile save(CustomerProfile profile) {
        return toDomain(repository.save(toEntity(profile)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerProfile> search(String keyword, int limit) {
        String normalized = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return repository.search(normalized, Limit.of(limit)).stream()
                .map(CustomerProfilePersistenceAdapter::toDomain)
                .toList();
    }

    private static CustomerProfileJpaEntity toEntity(CustomerProfile profile) {
        CustomerProfileJpaEntity e = new CustomerProfileJpaEntity();
        e.setCustomerId(profile.getCustomerId());
        e.setName(profile.getName());
        e.setPhoneNumber(profile.getPhoneNumber());
        e.setEmail(profile.getEmail());
        e.setCreatedAt(profile.getCreatedAt());
        e.setUpdatedAt(profile.getUpdatedAt());
        return e;
    }

    private static CustomerProfile toDomain(CustomerProfileJpaEntity e) {
        return CustomerProfile.restore(e.getCustomerId(), e.getName(), e.getPhoneNumber(),
                e.getEmail(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
