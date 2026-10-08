package com.jmifx.demo.security;

import com.jmifx.demo.entity.User;
import io.jmix.securitydata.user.AbstractDatabaseUserRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Users live in the main database (USER_ table) — Studio-template shape.
 * Anonymous gets no authorities: every REST call must carry a token.
 */
@Primary
@Component("UserRepository")
public class DatabaseUserRepository extends AbstractDatabaseUserRepository<User> {

    @Override
    protected Class<User> getUserClass() {
        return User.class;
    }

    @Override
    protected void initAnonymousUser(User anonymousUser) {
        // empty set — no anonymous access in this milestone (spec §2)
        Collection<GrantedAuthority> authorities = getGrantedAuthoritiesBuilder().build();
        anonymousUser.setAuthorities(authorities);
    }
}
