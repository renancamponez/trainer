package com.sub130.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Auth is OFF unless APP_PASSWORD is set. Locally (no password) everything is open, so
 * dev is unchanged; in production you set APP_USERNAME / APP_PASSWORD and the whole app
 * (page + API) sits behind HTTP Basic auth over Render's HTTPS.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           @Value("${app.password:}") String password) throws Exception {
        boolean secured = password != null && !password.isBlank();
        http.csrf(csrf -> csrf.disable())            // stateless Basic auth, no cookies
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/health").permitAll();
                if (secured) auth.anyRequest().authenticated();
                else auth.anyRequest().permitAll();
            });
        if (secured) http.httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public UserDetailsService users(@Value("${app.username:admin}") String username,
                                    @Value("${app.password:}") String password) {
        String pw = (password == null || password.isBlank()) ? "unused" : password;
        return new InMemoryUserDetailsManager(
                User.withUsername(username).password("{noop}" + pw).roles("USER").build());
    }
}
