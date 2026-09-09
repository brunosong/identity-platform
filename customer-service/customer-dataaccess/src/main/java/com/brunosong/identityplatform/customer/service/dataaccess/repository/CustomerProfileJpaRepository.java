package com.brunosong.identityplatform.customer.service.dataaccess.repository;

import com.brunosong.identityplatform.customer.service.dataaccess.entity.CustomerProfileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 프로필은 주체당 하나뿐이라 PK 조회면 충분하다.
 *
 * <p>이름·이메일로 훑는 검색은 직원이 고객을 찾을 때 필요한 것이라 이 서비스에는 없다 —
 * 이 서비스는 포털 realm 만 상대하고, 포털 사용자는 자기 프로필만 본다.
 */
public interface CustomerProfileJpaRepository extends JpaRepository<CustomerProfileJpaEntity, String> {
}
