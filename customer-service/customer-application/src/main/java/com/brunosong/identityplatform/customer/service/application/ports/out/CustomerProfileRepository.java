package com.brunosong.identityplatform.customer.service.application.ports.out;

import com.brunosong.identityplatform.customer.service.domain.CustomerProfile;

import java.util.Optional;

/** 고객 프로필 저장 드리븐 포트. */
public interface CustomerProfileRepository {

    Optional<CustomerProfile> findById(String customerId);

    CustomerProfile save(CustomerProfile profile);
}
