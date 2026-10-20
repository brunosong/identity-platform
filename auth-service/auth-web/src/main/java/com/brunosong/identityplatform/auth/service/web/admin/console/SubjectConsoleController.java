package com.brunosong.identityplatform.auth.service.web.admin.console;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.AssignSubjectRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.FindSubjectByLoginUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.UserInfo;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 사람을 찾아 그 사람의 역할을 고치는 운영 화면. 로컬에서만 뜨는 이유는 {@link OAuthClientConsoleController} 와 같다.
 *
 * <p>운영자는 subjectId 를 모르니 이메일이나 로그인 아이디로 찾는다. 역할은 그 realm 의 것만 보여 준다.
 */
@Controller
@RequestMapping("/page/subjects")
@Profile("local")
@RequiredArgsConstructor
public class SubjectConsoleController {

    private final FindSubjectByLoginUseCase findSubjectByLogin;
    private final FindRolesUseCase findRoles;
    private final AssignSubjectRolesUseCase assignSubjectRoles;

    @GetMapping
    public String find(@RequestParam(defaultValue = "PORTAL") Realm realm,
                       @RequestParam(required = false) String login,
                       Model model) {
        model.addAttribute("realm", realm);
        model.addAttribute("realms", Realm.values());
        model.addAttribute("login", login);
        if (login == null || login.isBlank()) {
            return "console/subjects";
        }

        Optional<UserInfo> person = findSubjectByLogin.byLogin(realm, login);
        if (person.isEmpty()) {
            model.addAttribute("notFound", true);
            return "console/subjects";
        }

        String subjectId = person.get().subjectId();
        Set<Long> assignedRoleIds = findRoles.ofSubject(realm, subjectId).stream()
                .map(RoleView::roleId)
                .collect(Collectors.toSet());

        model.addAttribute("person", person.get());
        model.addAttribute("roles", findRoles.search(realm, null));
        model.addAttribute("assignedRoleIds", assignedRoleIds);
        return "console/subjects";
    }

    /** 체크된 역할로 통째로 바꾸고 같은 사람을 다시 연다. 하나도 체크하지 않으면 칸이 오지 않는다. */
    @PostMapping("/roles")
    public String assignRoles(@RequestParam Realm realm,
                              @RequestParam String subjectId,
                              @RequestParam String login,
                              @RequestParam(required = false) List<Long> roleIds,
                              RedirectAttributes attributes) {
        assignSubjectRoles.assign(realm, subjectId, roleIds == null ? List.of() : roleIds);
        attributes.addFlashAttribute("message", "역할을 저장했다. 다음 토큰 발급부터 실린다.");
        attributes.addAttribute("realm", realm);
        attributes.addAttribute("login", login);
        return "redirect:/page/subjects";
    }
}
