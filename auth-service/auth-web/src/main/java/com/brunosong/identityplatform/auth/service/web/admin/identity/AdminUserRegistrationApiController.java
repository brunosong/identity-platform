package com.brunosong.identityplatform.auth.service.web.admin.identity;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.AssignSubjectRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterEmployeeAccountUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterEmployeeAccountCommand;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자가 계정을 만드는 API — 셀프 가입이 닫힌 realm 에 계정이 생기는 유일한 경로다.
 * 계정을 만들고(esntlId 채번 + Principal) 어드민이 고른 초기 역할까지 배정한다.
 *
 * <h2>realm 이 두 번 나온다</h2>
 * <pre>
 * POST /api/admin/realms/admin/users
 *                            ^^^^^
 *                            계정을 만들 realm (대상)
 *      호출자의 realm 은 경로가 아니라 토큰이 정한다. 언제나 MASTER 다.
 * </pre>
 *
 * <b>인증하는 realm 과 조작하는 realm 은 다른 값이다.</b> MASTER 관리자가 직원 계정을 만드는 것은 정상이므로
 * 둘을 한 값으로 묶으면 안 된다. Keycloak 의 {@code /admin/realms/{realm}/users} 도 같은 모양이다 —
 * 호출자는 보통 master realm 토큰이고, 경로의 realm 은 관리 대상이다.
 *
 * <p>대상 realm 은 지금 어드민뿐이다. 포털에는 셀프 가입이 있고, 관리자가 포털 계정을 대신 만드는
 * 유스케이스({@code RegisterEmployeeAccountUseCase} 에 대응하는 포털판)가 아직 없다. 다른 realm 은
 * 404 다 — 있는 척하고 500 을 내는 것보다 없다고 답하는 편이 정직하다.
 *
 * <p>운영자만 부를 수 있다. 계정을 만들고 역할까지 배정하는 API 라서 열려 있으면 누구나 자기 자신에게
 * 직원 권한을 줄 수 있다. RBAC 편집과 같은 관리 권한을 요구한다.
 */
@RestController
@RequestMapping("/api/admin/realms/{realm}")
public class AdminUserRegistrationApiController {

    private final RegisterEmployeeAccountUseCase registerEmployeeAccount;
    private final AssignSubjectRolesUseCase assignSubjectRoles;
    private final AuthenticationRealm authenticationRealm;

    public AdminUserRegistrationApiController(
            RegisterEmployeeAccountUseCase registerEmployeeAccount,
            AssignSubjectRolesUseCase assignSubjectRoles,
            AuthenticationRealm authenticationRealm) {
        this.registerEmployeeAccount = registerEmployeeAccount;
        this.assignSubjectRoles = assignSubjectRoles;
        this.authenticationRealm = authenticationRealm;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterUserResponse createUser(@PathVariable String realm,
                                           @Valid @RequestBody AdminUserRequest body) {
        // 대상 realm 은 경로에서, 호출자 realm 은 토큰에서 — 서로 다른 값이다.
        Realm target = authenticationRealm.requireRealm(realm, Realm.ADMIN);

        String essentialId = registerEmployeeAccount.register(new RegisterEmployeeAccountCommand(
                body.employeeId(), body.name(), body.email(), body.mobile(),
                body.positionName(), body.organizationId(), body.statusCode()));

        if (body.roleIds() != null && !body.roleIds().isEmpty()) {
            assignSubjectRoles.assign(target, essentialId, body.roleIds());
        }
        return new RegisterUserResponse(essentialId);
    }

    public record AdminUserRequest(@NotBlank String employeeId,
                                   @NotBlank String name,
                                   @NotBlank @Email String email,
                                   String mobile,
                                   String positionName,
                                   String organizationId,
                                   String statusCode,
                                   List<Long> roleIds) {
    }

    public record RegisterUserResponse(String essentialId) {
    }
}
