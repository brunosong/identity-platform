package com.brunosong.identityplatform.customer.service.web.api;

import com.brunosong.identityplatform.customer.service.application.ports.in.CustomerProfileUseCase;
import com.brunosong.identityplatform.customer.service.web.support.AuthenticatedCaller;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 직원이 고객을 들여다보는 API.
 *
 * <p>같은 데이터를 다루지만 조건이 다르다 — 여기는 <b>직원 realm + 권한</b>을 요구하고, 대신
 * 남의 프로필을 볼 수 있다. 고객 본인 API 는 그 반대다(고객 realm, 자기 것만).
 * 같은 서비스 안에서 두 realm 을 함께 상대하는 모습이다.
 *
 * <p>권한 코드는 auth 가 관리한다. 이 서비스는 그 코드가 토큰에 실려 있는지만 본다 —
 * 어떤 역할에 이 권한이 붙어 있는지는 알지 못하고 알 필요도 없다.
 *
 * <p><b>이 권한은 이 서비스의 것이다.</b> auth 에 등록해 두지만 의미는 여기서 정한다 — 그래서
 * 코드 이름에 이 서비스의 자원이 들어간다(CUSTOMER_PROFILE_READ).
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerAdminApiController {

    private static final String EMPLOYEE_REALM = "EMPLOYEE";
    private static final String READ_PERMISSION = "CUSTOMER_PROFILE_READ";

    private final CustomerProfileUseCase customerProfiles;
    private final AuthenticatedCaller caller;

    @GetMapping
    public List<ProfileResponse> search(HttpServletRequest request,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(defaultValue = "20") int limit) {
        caller.requirePermission(request, EMPLOYEE_REALM, READ_PERMISSION);

        return customerProfiles.search(keyword, limit).stream().map(ProfileResponse::of).toList();
    }

    /**
     * 경로로 받은 식별자로 조회한다. 본인 API 와 달리 이것이 안전한 이유는 <b>여기까지 온 호출자가
     * 이미 직원이고 조회 권한을 가졌기 때문</b>이다 — 남의 것을 보는 것이 이 API 의 목적이다.
     */
    @GetMapping("/{customerId}")
    public ResponseEntity<ProfileResponse> detail(HttpServletRequest request, @PathVariable String customerId) {
        caller.requirePermission(request, EMPLOYEE_REALM, READ_PERMISSION);

        return customerProfiles.find(customerId)
                .map(profile -> ResponseEntity.ok(ProfileResponse.of(profile)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
