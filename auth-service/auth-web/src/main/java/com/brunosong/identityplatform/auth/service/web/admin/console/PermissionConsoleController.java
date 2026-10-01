package com.brunosong.identityplatform.auth.service.web.admin.console;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * realm 의 권한을 보는 운영 화면. 지금은 보기만 한다.
 *
 * <p>백오피스의 "인가 정책" 화면이 하던 일을 옮겨 왔다. auth 를 관리하는 것은 MASTER realm 이라,
 * 관리 화면도 MASTER 로 로그인한 운영 화면에 둔다. 로컬에서만 뜨는 이유는
 * {@link OAuthClientConsoleController} 와 같다.
 */
@Controller
@RequestMapping("/page/permissions")
@Profile("local")
@RequiredArgsConstructor
public class PermissionConsoleController {

    private final FindPermissionsUseCase findPermissions;

    @GetMapping
    public String list(@RequestParam(defaultValue = "ADMIN") Realm realm, Model model) {
        model.addAttribute("realm", realm);
        model.addAttribute("realms", Realm.values());
        model.addAttribute("permissions", findPermissions.of(realm, null));
        return "console/permissions";
    }
}
