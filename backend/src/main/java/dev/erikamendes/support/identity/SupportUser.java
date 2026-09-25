package dev.erikamendes.support.identity;

import java.util.UUID;
import org.springframework.security.core.userdetails.User;

public final class SupportUser extends User {
    private final UUID organizationId;
    private final String organizationName;

    public SupportUser(String username, String encodedPassword, UUID organizationId, String organizationName) {
        super(username, encodedPassword, java.util.List.of(() -> "ROLE_AGENT"));
        this.organizationId = organizationId;
        this.organizationName = organizationName;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public String organizationName() {
        return organizationName;
    }
}
