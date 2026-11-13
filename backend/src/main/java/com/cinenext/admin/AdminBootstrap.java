package com.cinenext.admin;

import com.cinenext.identity.AuthService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Local administrative CLI, never an HTTP registration route. */
@Component @Order(1000) @ConditionalOnProperty(name="cinenext.bootstrap-admin",havingValue="true")
public class AdminBootstrap implements ApplicationRunner {
    private final AuthService auth;private final ConfigurableApplicationContext context;
    public AdminBootstrap(AuthService auth,ConfigurableApplicationContext context) { this.auth=auth;this.context=context; }
    @Override public void run(ApplicationArguments arguments) {
        String email=System.getenv("BOOTSTRAP_ADMIN_EMAIL"),password=System.getenv("BOOTSTRAP_ADMIN_PASSWORD");
        if(email==null||!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")||password==null) throw new IllegalStateException("Use scripts/Create-Admin.ps1 with a new administrator email and password.");
        auth.create("Cinema administrator",email,password,"ADMIN");
        System.out.println("Administrator created. Sign in with the email and password you supplied.");
        System.exit(SpringApplication.exit(context));
    }
}
