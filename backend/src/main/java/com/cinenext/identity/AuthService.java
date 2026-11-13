package com.cinenext.identity;

import com.cinenext.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final MongoTemplate mongo; private final TokenService tokens; private final Clock clock;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
    private final String dummyHash=passwords.encode(UUID.randomUUID().toString());
    public AuthService(MongoTemplate mongo,TokenService tokens,Clock clock) { this.mongo=mongo;this.tokens=tokens;this.clock=clock; }
    public record PublicUser(String id,String name,String email,String role) {}
    public record Session(String accessToken,PublicUser user,int expiresIn) {}
    public PublicUser view(UserAccount user) { return new PublicUser(user.id(),user.name(),user.email(),user.role()); }
    public Session register(String name,String email,String password) { return session(create(name,email,password,"USER")); }
    public UserAccount create(String name,String email,String password,String role) {
        if(password.getBytes(StandardCharsets.UTF_8).length>72 || password.length()<10) throw new BusinessException(HttpStatus.BAD_REQUEST,"Use a password with at least 10 characters and no more than 72 UTF-8 bytes.");
        return mongo.insert(new UserAccount(UUID.randomUUID().toString(),email.trim().toLowerCase(Locale.ROOT),name.trim(),passwords.encode(password),role,clock.instant()));
    }
    public Session login(String email,String password) {
        var user=mongo.findOne(Query.query(Criteria.where("email").is(email.trim().toLowerCase(Locale.ROOT))),UserAccount.class);
        if(password.getBytes(StandardCharsets.UTF_8).length>72 || !passwords.matches(password,user==null?dummyHash:user.passwordHash()) || user==null)
            throw new BusinessException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect.");
        return session(user);
    }
    public PublicUser current(String id) {
        var user=mongo.findById(id,UserAccount.class);
        if(user==null) throw new BusinessException(HttpStatus.UNAUTHORIZED,"Please sign in again.");
        return view(user);
    }
    private Session session(UserAccount user) { return new Session(tokens.issue(user),view(user),1800); }
}
