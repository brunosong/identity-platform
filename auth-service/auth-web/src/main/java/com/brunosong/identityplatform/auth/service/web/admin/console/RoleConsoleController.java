package com.brunosong.identityplatform.auth.service.web.admin.console;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.RoleAlreadyExistsException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.CreateRoleUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.CreateRoleCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
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
 * 역할을 보고 새로 만드는 운영 화면.
 *
 * <p>로컬에서만 뜨는 이유는 {@link OAuthClientConsoleController} 와 같다. 화면 요청을 막을 로그인
 * 세션이 아직 없다.
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

    @GetMapping
    public String list(@RequestParam(defaultValue = "ADMIN") Realm realm, Model model) {
        model.addAttribute("realm", realm);
        model.addAttribute("realms", Realm.values());
        model.addAttribute("roles", findRoles.of(realm, null));
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
}
