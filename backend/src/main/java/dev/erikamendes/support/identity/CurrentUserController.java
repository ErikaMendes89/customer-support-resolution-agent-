package dev.erikamendes.support.identity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class CurrentUserController {
    private final CookieCsrfTokenRepository csrfTokens;

    public CurrentUserController(CookieCsrfTokenRepository csrfTokens) {
        this.csrfTokens = csrfTokens;
    }

    @GetMapping
    public CurrentUser currentUser(@AuthenticationPrincipal SupportUser user) {
        return new CurrentUser(user.getUsername(), user.organizationId().toString(), user.organizationName());
    }

    @GetMapping("/csrf")
    public void csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokens.generateToken(request);
        csrfTokens.saveToken(token, request, response);
    }

    public record CurrentUser(String username, String organizationId, String organizationName) { }
}
