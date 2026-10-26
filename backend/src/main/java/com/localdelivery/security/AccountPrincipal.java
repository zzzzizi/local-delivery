package com.localdelivery.security;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** No email or token in the session; password erased by ProviderManager after authentication. */
public class AccountPrincipal extends org.springframework.security.core.userdetails.User {
    private final long sessionVersion;
    private final boolean verifiedForLogin;
    public AccountPrincipal(com.localdelivery.model.User user, boolean requireVerification) {
        super(user.getId().toString(), user.getPasswordHash(),
                true, true, true, true,
                List.of(new SimpleGrantedAuthority("USER")));
        this.sessionVersion = user.getSessionVersion();
        this.verifiedForLogin = !requireVerification || user.isEmailVerified();
    }
    public boolean verifiedForLogin() { return verifiedForLogin; }
    public long sessionVersion() { return sessionVersion; }
}
