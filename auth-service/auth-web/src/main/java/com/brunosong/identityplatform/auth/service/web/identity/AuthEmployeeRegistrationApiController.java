package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.AssignSubjectRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterEmployeeAccountUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterEmployeeAccountCommand;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticatedCaller;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 직원 등록 API — auth 가 직원 계정을 만들고(esntlId 채번 + Principal) 어드민이 고른 초기 역할을 배정한다.
 * 직원 전용이라 EMPLOYEE realm 호스트에서만 활성화한다.
 *
 * <p>운영자만 부를 수 있다. 계정을 만들고 역할까지 배정하는 API 라서 열려 있으면 누구나 자기 자신에게
 * 직원 권한을 줄 수 있다. RBAC 편집과 같은 관리 권한을 요구한다.
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "authorization.realm", havingValue = "EMPLOYEE")
public class AuthEmployeeRegistrationApiController {

    private final RegisterEmployeeAccountUseCase registerEmployeeAccount;
    private final AssignSubjectRolesUseCase assignSubjectRoles;
    private final AuthenticatedCaller caller;
    private final String managePermission;

    public AuthEmployeeRegistrationApiController(
            RegisterEmployeeAccountUseCase registerEmployeeAccount,
            AssignSubjectRolesUseCase assignSubjectRoles,
            AuthenticatedCaller caller,
            @Value("${authorization.manage-permission:AUTHZ_MANAGE}") String managePermission) {
        this.registerEmployeeAccount = registerEmployeeAccount;
        this.assignSubjectRoles = assignSubjectRoles;
        this.caller = caller;
        this.managePermission = managePermission;
    }

    @PostMapping("/employee/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterEmployeeResponse registerEmployee(HttpServletRequest request,
                                                     @Valid @RequestBody EmployeeRegisterRequest body) {
        caller.requirePermission(request, managePermission);

        String essentialId = registerEmployeeAccount.register(new RegisterEmployeeAccountCommand(
                body.employeeId(), body.name(), body.email(), body.mobile(),
                body.positionName(), body.organizationId(), body.statusCode()));

        if (body.roleIds() != null && !body.roleIds().isEmpty()) {
            assignSubjectRoles.assign(Realm.EMPLOYEE, essentialId, body.roleIds());
        }
        return new RegisterEmployeeResponse(essentialId);
    }

    public record EmployeeRegisterRequest(@NotBlank String employeeId,
                                          @NotBlank String name,
                                          @NotBlank @Email String email,
                                          String mobile,
                                          String positionName,
                                          String organizationId,
                                          String statusCode,
                                          List<Long> roleIds) {
    }

    public record RegisterEmployeeResponse(String essentialId) {
    }
}
