package com.cinenext.identity;

import com.cinenext.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth; private final Clock clock;
    private final ConcurrentHashMap<String,Attempt> attempts=new ConcurrentHashMap<>();
    private record Attempt(long window,int count) {}
    public record Registration(@NotBlank @Size(max=80) String name,@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(min=10,max=72) String password) {}
    public record Login(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=72) String password) {}
    public AuthController(AuthService auth,Clock clock) { this.auth=auth;this.clock=clock; }
    private void limit(String ip) {
        long window=clock.instant().getEpochSecond()/60;
        if(attempts.size()>10000) attempts.entrySet().removeIf(e->e.getValue().window()!=window);
        if(attempts.size()>10000) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"Please wait a minute and try again.");
        var attempt=attempts.compute(ip,(key,old)->old==null||old.window()!=window?new Attempt(window,1):new Attempt(window,old.count()+1));
        if(attempt.count()>20) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"Too many sign-in attempts. Please wait a minute.");
    }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthService.Session register(@Valid @RequestBody Registration body,HttpServletRequest request) { limit(request.getRemoteAddr());return auth.register(body.name(),body.email(),body.password()); }
    @PostMapping("/login")
    public AuthService.Session login(@Valid @RequestBody Login body,HttpServletRequest request) { limit(request.getRemoteAddr());return auth.login(body.email(),body.password()); }
    @GetMapping("/me") @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
    public AuthService.PublicUser me(@AuthenticationPrincipal Jwt principal) { return auth.current(principal.getSubject()); }
}
