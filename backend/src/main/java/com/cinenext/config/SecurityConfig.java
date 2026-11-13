package com.cinenext.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import com.cinenext.identity.TokenService;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokens) throws Exception {
        var jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(jwt -> List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
        return http.cors(cors -> {}).csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,"/api/v1/auth/register","/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/bookings/**","/api/v1/holds/**","/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST,"/api/v1/showtimes/*/holds").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/movies", "/api/v1/movies/*", "/api/v1/theatres",
                                "/api/v1/showtimes", "/api/v1/showtimes/*/seats", "/api/v1/catalogue/filters",
                                "/actuator/health", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/actuator/health").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> unauthorized(response))
                        .accessDeniedHandler((request, response, exception) -> forbidden(response)))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(tokens.decoder()).jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint((request,response,error) -> unauthorized(response)))
                .build();
    }

    // Explicitly disable Boot's generated development user: no login endpoint exists in Phase 1.
    @Bean
    UserDetailsService users() {
        return username -> { throw new UsernameNotFoundException("Authentication is not enabled in this milestone"); };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${cinenext.cors.allowed-origins}") String origins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "HEAD", "OPTIONS", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Accept", "Content-Type", "Authorization", "Idempotency-Key"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static void forbidden(HttpServletResponse response) throws IOException {
        response.setStatus(403);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"This operation is not available.\"}");
    }
    private static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401); response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Please sign in to continue.\"}");
    }
}
