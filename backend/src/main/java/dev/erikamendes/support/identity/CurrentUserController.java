package dev.erikamendes.support.identity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class CurrentUserController {
    @GetMapping
    public CurrentUser currentUser(@AuthenticationPrincipal SupportUser user) {
        return new CurrentUser(user.getUsername(), user.organizationId().toString(), user.organizationName());
    }

    @GetMapping("/csrf")
    public void csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        token.getToken(); // Forces Spring Security to send the XSRF-TOKEN cookie.
    }

    public record CurrentUser(String username, String organizationId, String organizationName) { }
}
