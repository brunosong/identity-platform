package com.brunosong.identityplatform.auth.service.web.admin.authorization;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.CreateRoleUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.DeleteRoleUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.RenameRoleUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.CreateRoleCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.RenameRoleCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 역할 운영 API.
 *
 * <p>생성과 수정을 다른 엔드포인트로 가른다 — 하나의 저장 엔드포인트가 id 유무로 갈리면 호출자가
 * 무엇을 하려는지 요청만 보고는 알 수 없고, 새 역할을 만들려다 남의 역할을 덮어쓰는 실수도 막지 못한다.
 *
 * <p>realm 은 필수 파라미터다. 기본값을 두면 realm 을 빠뜨린 호출이 조용히 다른 realm 의 정책을 건드린다.
 */
@RestController
@RequestMapping("/api/admin/rbac/roles")
@RequiredArgsConstructor
public class RoleAdminApiController {

    private final CreateRoleUseCase createRole;
    private final RenameRoleUseCase renameRole;
    private final DeleteRoleUseCase deleteRole;
    private final FindRolesUseCase findRoles;

    @GetMapping
    public List<RoleView> list(@RequestParam Realm realm,
                               @RequestParam(required = false) String keyword) {
        return findRoles.of(realm, keyword);
    }

    @GetMapping("/{roleId}")
    public RoleView detail(@PathVariable Long roleId) {
        return findRoles.byId(roleId).orElseThrow(() -> AuthorizationNotFoundException.role(roleId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleView create(@RequestParam Realm realm,
                           @Valid @RequestBody CreateRoleRequest body) {
        return createRole.create(new CreateRoleCommand(realm, body.roleCode(), body.roleName(), body.description()));
    }

    @PutMapping("/{roleId}")
    public RoleView rename(@PathVariable Long roleId,
                           @Valid @RequestBody RenameRoleRequest body) {
        return renameRole.rename(new RenameRoleCommand(roleId, body.roleName(), body.description()));
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> delete(@PathVariable Long roleId) {
        deleteRole.delete(roleId);
        return ResponseEntity.noContent().build();
    }

    public record CreateRoleRequest(@NotBlank String roleCode, @NotBlank String roleName, String description) {
    }

    public record RenameRoleRequest(@NotBlank String roleName, String description) {
    }
}
