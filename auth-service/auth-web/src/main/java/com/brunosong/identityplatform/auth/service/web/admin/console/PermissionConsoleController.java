package com.brunosong.identityplatform.auth.service.web.admin.console;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.PermissionAlreadyExistsException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.CreatePermissionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.CreatePermissionCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * realm 의 권한을 보고 새로 만드는 운영 화면.
 *
 * <p>백오피스의 "인가 정책" 화면이 하던 일을 옮겨 왔다. auth 를 관리하는 것은 MASTER realm 이라,
 * 관리 화면도 MASTER 로 로그인한 운영 화면에 둔다. 로컬에서만 뜨는 이유는
 * {@link OAuthClientConsoleController} 와 같다.
 *
 * <p>서비스는 그 realm 에 등록된 것 중에서만 고른다. 서비스 이름이 토큰의 {@code resource_access} 칸이 되므로
 * 오타가 나면 그 권한은 실릴 곳이 없다.
 */
@Controller
@RequestMapping("/page/permissions")
@Profile("local")
@RequiredArgsConstructor
public class PermissionConsoleController {

    private final FindPermissionsUseCase findPermissions;
    private final CreatePermissionUseCase createPermission;

    @GetMapping
    public String list(@RequestParam(defaultValue = "ADMIN") Realm realm, Model model) {
        model.addAttribute("realm", realm);
        model.addAttribute("realms", Realm.values());
        model.addAttribute("services", findPermissions.services(realm));
        model.addAttribute("permissions", findPermissions.search(realm, null));
        return "console/permissions";
    }

    /** 만들고 그 realm 의 목록으로 돌려보낸다. 실패는 목록 위의 문구로 돌려준다. */
    @PostMapping
    public String create(@RequestParam Realm realm,
                         @RequestParam(required = false) String serviceId,
                         @RequestParam String permissionCode,
                         @RequestParam String permissionName,
                         @RequestParam(required = false) String category,
                         @RequestParam(required = false) String description,
                         RedirectAttributes attributes) {
        try {
            PermissionView created = createPermission.create(new CreatePermissionCommand(
                    realm, serviceId, permissionCode, permissionName, category, description));
            attributes.addFlashAttribute("message", created.permissionCode() + " 권한을 만들었다.");
        } catch (PermissionAlreadyExistsException | IllegalArgumentException e) {
            attributes.addFlashAttribute("error", e.getMessage());
        }
        attributes.addAttribute("realm", realm);
        return "redirect:/page/permissions";
    }
}
