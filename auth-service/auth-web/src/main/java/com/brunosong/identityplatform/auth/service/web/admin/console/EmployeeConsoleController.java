package com.brunosong.identityplatform.auth.service.web.admin.console;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.AssignSubjectRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindRolesUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterEmployeeAccountUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterEmployeeAccountCommand;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 직원 계정을 만드는 운영 화면. 백오피스의 "직원 등록" 이 하던 일을 옮겨 왔다.
 *
 * <p>직원 realm 은 셀프 가입이 닫혀 있어서 계정이 생기는 길은 여기와 관리 API 뿐이다. 둘 다 MASTER
 * 관리자의 일이다. 직원 realm 은 업무 시스템을 쓰는 사람들의 자리이지 auth 를 관리하는 자리가 아니다.
 *
 * <p>비밀번호를 받지 않는다. 직원은 이메일 인증번호로만 로그인한다. 역할을 함께 고르지 않으면 계정은
 * 생기지만 로그인해도 할 수 있는 일이 없다.
 *
 * <p>로컬에서만 뜨는 이유는 {@link OAuthClientConsoleController} 와 같다.
 */
@Controller
@RequestMapping("/page/employees")
@Profile("local")
@RequiredArgsConstructor
public class EmployeeConsoleController {

    private final RegisterEmployeeAccountUseCase registerEmployeeAccount;
    private final AssignSubjectRolesUseCase assignSubjectRoles;
    private final FindRolesUseCase findRoles;

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("roles", findRoles.of(Realm.ADMIN, null));
        return "console/employees";
    }

    /**
     * 만들고 등록 화면으로 돌려보낸다(POST 뒤 redirect). 실패는 화면의 문구로 돌려준다.
     *
     * <p>이미 쓰이는 이메일이면 DB 의 유일 제약에서 걸린다. 화면에서는 그 사실만 알려 준다.
     */
    @PostMapping
    public String register(@RequestParam String employeeId,
                           @RequestParam String name,
                           @RequestParam String email,
                           @RequestParam(required = false) String mobile,
                           @RequestParam(required = false) List<Long> roleIds,
                           RedirectAttributes attributes) {
        try {
            String subjectId = registerEmployeeAccount.register(new RegisterEmployeeAccountCommand(
                    employeeId.trim(), name.trim(), email.trim(), blankToNull(mobile), null, null, null));
            if (roleIds != null && !roleIds.isEmpty()) {
                assignSubjectRoles.assign(Realm.ADMIN, subjectId, roleIds);
            }
            attributes.addFlashAttribute("message", name.trim() + " 계정을 만들었다. " + email.trim()
                    + " 로 인증번호를 받아 바로 로그인할 수 있다.");
            attributes.addFlashAttribute("subjectId", subjectId);
        } catch (DataIntegrityViolationException e) {
            attributes.addFlashAttribute("error", "이미 쓰이는 이메일입니다: " + email.trim());
        } catch (IllegalArgumentException e) {
            attributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/page/employees/new";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
