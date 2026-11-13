package com.cinenext.identity;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;
    public TokenService(@Value("${cinenext.jwt.secret}") String secret, Clock clock) {
        byte[] bytes;
        try { bytes=Base64.getDecoder().decode(secret); } catch (IllegalArgumentException error) { throw new IllegalStateException("JWT_SECRET must be base64."); }
        if(bytes.length<32) throw new IllegalStateException("JWT_SECRET requires at least 32 random bytes. Use Start-Backend.ps1 to create a local secret.");
        var key=new SecretKeySpec(bytes,"HmacSHA256");
        encoder=new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var configured=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        configured.setJwtValidator(JwtValidators.createDefaultWithIssuer("cinenext"));
        decoder=configured; this.clock=clock;
    }
    public String issue(UserAccount user) {
        var claims=JwtClaimsSet.builder().issuer("cinenext").subject(user.id()).issuedAt(clock.instant())
                .expiresAt(clock.instant().plus(Duration.ofMinutes(30))).claim("role",user.role()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    public JwtDecoder decoder() { return decoder; }
}
