package com.cinenext.identity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

@Document("users")
public record UserAccount(@Id String id, @Indexed(unique=true) String email, String name, String passwordHash, String role, Instant createdAt) {}
