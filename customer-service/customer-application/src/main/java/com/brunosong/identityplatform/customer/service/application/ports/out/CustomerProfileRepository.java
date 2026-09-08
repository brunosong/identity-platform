package com.brunosong.identityplatform.customer.service.application.ports.out;

import com.brunosong.identityplatform.customer.service.domain.CustomerProfile;

import java.util.List;
import java.util.Optional;

/** 고객 프로필 저장 드리븐 포트. */
public interface CustomerProfileRepository {

    Optional<CustomerProfile> findById(String customerId);

    CustomerProfile save(CustomerProfile profile);

    List<CustomerProfile> search(String keyword, int limit);
}
