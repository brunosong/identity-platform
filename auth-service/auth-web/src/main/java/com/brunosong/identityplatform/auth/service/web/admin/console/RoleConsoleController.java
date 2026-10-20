package com.brunosong.identityplatform.auth.service.web.admin.console;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.exception.RoleAlreadyExistsException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.CreateRoleUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindRolePermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ReplaceRolePermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.CreateRoleCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RolePermissionView;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 역할을 보고 새로 만드는 운영 화면.
 *
 * <p>로컬에서만 뜨는 이유는 {@link OAuthClientConsoleController} 와 같다. 로그인은 보지만 권한은
 * 아직 보지 않는다.
 *
 * <p>한 번에 한 realm 만 보여 준다. 역할은 realm 으로 스코프되고 코드도 realm 안에서만 유일해서,
 * 두 realm 을 한 표에 섞으면 같은 코드가 두 줄 나와도 이상한 줄 모른다.
 */
@Controller
@RequestMapping("/page/roles")
@Profile("local")
@RequiredArgsConstructor
public class RoleConsoleController {

    private final FindRolesUseCase findRoles;
    private final CreateRoleUseCase createRole;
    private final FindRolePermissionsUseCase findRolePermissions;
    private final ReplaceRolePermissionsUseCase replaceRolePermissions;

    @GetMapping
    public String list(@RequestParam(defaultValue = "ADMIN") Realm realm, Model model) {
        model.addAttribute("realm", realm);
        model.addAttribute("realms", Realm.values());
        model.addAttribute("roles", findRoles.search(realm, null));
        return "console/roles";
    }

    /** 만들고 그 realm 의 목록으로 돌려보낸다. 실패는 목록 위의 문구로 돌려준다. */
    @PostMapping
    public String create(@RequestParam Realm realm,
                         @RequestParam String roleCode,
                         @RequestParam String roleName,
                         @RequestParam(required = false) String description,
                         RedirectAttributes attributes) {
        try {
            RoleView created = createRole.create(new CreateRoleCommand(realm, roleCode, roleName, description));
            attributes.addFlashAttribute("message", created.roleCode() + " 역할을 만들었다.");
        } catch (RoleAlreadyExistsException | IllegalArgumentException e) {
            attributes.addFlashAttribute("error", e.getMessage());
        }
        attributes.addAttribute("realm", realm);
        return "redirect:/page/roles";
    }

    /**
     * 역할 하나에 묶인 권한을 고치는 화면. 그 realm 의 권한을 서비스별로 묶어 보여 준다. 서비스가 곧
     * 토큰의 {@code resource_access} 칸이라, 여기서 고친 것이 토큰의 어느 칸으로 가는지 보이게 하려는 것이다.
     */
    @GetMapping("/{roleId}")
    public String detail(@PathVariable Long roleId, Model model) {
        RoleView role = findRoles.byId(roleId).orElseThrow(() -> AuthorizationNotFoundException.role(roleId));
        List<RolePermissionView> permissions = findRolePermissions.forRole(roleId).permissions();

        model.addAttribute("role", role);
        model.addAttribute("permissionsByService", groupByService(permissions));
        return "console/role-permissions";
    }

    /** 체크된 것으로 통째로 바꾼다. 하나도 체크하지 않으면 칸이 오지 않으니 빈 목록으로 받는다. */
    @PostMapping("/{roleId}/permissions")
    public String replacePermissions(@PathVariable Long roleId,
                                     @RequestParam(required = false) List<Long> permissionIds,
                                     RedirectAttributes attributes) {
        replaceRolePermissions.replace(roleId, permissionIds == null ? List.of() : permissionIds);
        attributes.addFlashAttribute("message", "권한을 저장했다. 다음 토큰 발급부터 실린다.");
        return "redirect:/page/roles/" + roleId;
    }

    /** 서비스 이름 순. 서비스가 없는 권한은 토큰에 실리지 않으니 따로 맨 뒤에 둔다(키는 빈 문자열). */
    private static Map<String, List<RolePermissionView>> groupByService(List<RolePermissionView> permissions) {
        List<RolePermissionView> withService = permissions.stream()
                .filter(permission -> permission.serviceId() != null)
                .toList();
        List<RolePermissionView> withoutService = permissions.stream()
                .filter(permission -> permission.serviceId() == null)
                .toList();

        Map<String, List<RolePermissionView>> grouped = new LinkedHashMap<>(
                new TreeMap<>(withService.stream().collect(Collectors.groupingBy(RolePermissionView::serviceId))));
        if (!withoutService.isEmpty()) {
            grouped.put("", withoutService);
        }
        return grouped;
    }
}
