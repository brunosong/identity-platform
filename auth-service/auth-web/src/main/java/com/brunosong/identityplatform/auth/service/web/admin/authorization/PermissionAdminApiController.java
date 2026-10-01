package com.brunosong.identityplatform.auth.service.web.admin.authorization;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.CreatePermissionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.DeletePermissionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.RenamePermissionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.CreatePermissionCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.RenamePermissionCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionView;
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
 * 권한 운영 API.
 *
 * <p>전체 목록과 페이지를 다른 경로로 가른다 — 같은 URL 이 파라미터 유무로 응답 모양을 바꾸면
 * 호출자가 두 모양을 모두 다뤄야 한다.
 */
@RestController
@RequestMapping("/api/admin/rbac/permissions")
@RequiredArgsConstructor
public class PermissionAdminApiController {

    private final CreatePermissionUseCase createPermission;
    private final RenamePermissionUseCase renamePermission;
    private final DeletePermissionUseCase deletePermission;
    private final FindPermissionsUseCase findPermissions;

    @GetMapping
    public List<PermissionView> list(@RequestParam Realm realm,
                                     @RequestParam(required = false) String keyword) {
        return findPermissions.of(realm, keyword);
    }

    @GetMapping("/page")
    public PageResult<PermissionView> page(@RequestParam Realm realm,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return findPermissions.page(realm, keyword, PageQuery.of(page, size));
    }

    @GetMapping("/{permissionId}")
    public PermissionView detail(@PathVariable Long permissionId) {
        return findPermissions.byId(permissionId)
                .orElseThrow(() -> AuthorizationNotFoundException.permission(permissionId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PermissionView create(@RequestParam Realm realm,
                                 @Valid @RequestBody CreatePermissionRequest body) {
        return createPermission.create(new CreatePermissionCommand(realm, body.permissionCode(),
                body.permissionName(), body.category(), body.description()));
    }

    @PutMapping("/{permissionId}")
    public PermissionView rename(@PathVariable Long permissionId,
                                 @Valid @RequestBody RenamePermissionRequest body) {
        return renamePermission.rename(new RenamePermissionCommand(permissionId, body.permissionName(),
                body.category(), body.description()));
    }

    @DeleteMapping("/{permissionId}")
    public ResponseEntity<Void> delete(@PathVariable Long permissionId) {
        deletePermission.delete(permissionId);
        return ResponseEntity.noContent().build();
    }

    /** 화면 필터용 분류 목록. */
    @GetMapping("/categories")
    public List<String> categories(@RequestParam Realm realm) {
        return findPermissions.categories(realm);
    }

    public record CreatePermissionRequest(@NotBlank String permissionCode, @NotBlank String permissionName,
                                          String category, String description) {
    }

    public record RenamePermissionRequest(@NotBlank String permissionName, String category, String description) {
    }
}
