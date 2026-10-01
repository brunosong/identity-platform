package com.brunosong.identityplatform.auth.service.web.admin.console;

import com.brunosong.identityplatform.auth.service.application.oauth.exception.OAuthClientAlreadyExistsException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.FindOAuthClientsUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.RegisterOAuthClientUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.RegisterOAuthClientCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.result.RegisteredOAuthClient;
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

/**
 * 등록된 앱을 보고 새로 등록하는 운영 화면. 서버가 직접 그린다.
 *
 * <h2>아직 로컬에서만 뜬다</h2>
 * 화면은 MASTER 로 로그인해야 열린다({@link com.brunosong.identityplatform.auth.service.web.admin.console.login.ConsoleLogin}). 하지만 아직 권한은 보지 않는다.
 * MASTER 에 계정만 있으면 누구든 들어온다. 그래서 {@code local} 프로파일에서만 등록된다. 다른
 * 환경에서는 이 경로 자체가 존재하지 않는다.
 *
 * <p>권한 검사가 생기면 이 제한을 푼다. 그때까지 열어둔 채로 배포되는 일이 없도록,
 * 조건을 코드 밖(설정)이 아니라 여기에 둔다.
 *
 * <p>경로를 {@code /page/} 아래 둔 것은 이 저장소에서 화면이 쓰는 접두어이기 때문이다
 * ({@code authorization.access-control.protected-path-prefixes}). 보호를 켜는 날 규칙이 없는
 * {@code /page/} 경로는 막히는 쪽이 기본값이다.
 *
 * <p>패키지는 admin 채널 아래다. 부르는 주체가 운영자이기 때문이다 - 화면이냐 JSON 이냐는
 * 채널을 가르는 축이 아니다. 다만 경로가 {@code /api/admin/**} 밖이라 게이트웨이의 채널
 * 규칙에는 걸리지 않는다. 화면 접두어를 어떻게 가져갈지는 로그인·동의 화면까지 나온 뒤에 정한다.
 */
@Controller
@RequestMapping("/page/oauth-clients")
@Profile("local")
@RequiredArgsConstructor
public class OAuthClientConsoleController {

    private final FindOAuthClientsUseCase findClients;
    private final RegisterOAuthClientUseCase registerClient;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("clients", findClients.findAll());
        model.addAttribute("realms", Realm.values());
        return "console/oauth-clients";
    }

    /**
     * 등록하고 목록으로 돌려보낸다(POST 뒤 redirect). 새로고침으로 같은 등록이 다시 날아가는 것을 막는다.
     *
     * <p>실패는 예외를 그대로 올리지 않고 화면의 문구로 돌려준다. 주소를 잘못 적는 것은 사고가
     * 아니라 흔한 일이고, 쓰던 값이 사라진 오류 화면보다 목록 위의 한 줄이 고치기 쉽다.
     *
     * <p>시크릿 원문은 flash 로 한 번만 넘긴다. 목록을 새로고침하면 사라지고 다시 볼 방법이 없다.
     * 저장소에 해시만 남기 때문이다.
     */
    @PostMapping
    public String register(@RequestParam String name,
                           @RequestParam Realm realm,
                           @RequestParam String redirectUris,
                           @RequestParam(defaultValue = "false") boolean confidential,
                           RedirectAttributes attributes) {
        try {
            RegisteredOAuthClient registered = registerClient.register(
                    new RegisterOAuthClientCommand(realm, name, lines(redirectUris), confidential));
            attributes.addFlashAttribute("message", registered.client().getName() + " 을(를) 등록했다.");
            attributes.addFlashAttribute("clientId", registered.client().getClientId());
            attributes.addFlashAttribute("secret", registered.secret());
        } catch (OAuthClientAlreadyExistsException | IllegalArgumentException e) {
            attributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/page/oauth-clients";
    }

    /** 주소는 한 줄에 하나씩 적는다. 빈 줄은 버린다. */
    private static List<String> lines(String redirectUris) {
        return redirectUris.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
