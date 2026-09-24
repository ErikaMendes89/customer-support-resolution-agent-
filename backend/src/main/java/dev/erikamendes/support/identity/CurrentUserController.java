package dev.erikamendes.support.identity;

import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class CurrentUserController {
    @GetMapping
    public Map<String, String> currentUser(Principal principal) {
        return Map.of("username", principal.getName());
    }
}
