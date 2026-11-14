package com.cinenext.catalogue;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Publication metadata is server-owned; it is deliberately absent from request DTOs. */
@Component
public class CataloguePublication {
    public static final String FIELD = "catalogueSource";
    public static final String ADMIN = "ADMIN";
    public static final String LEGACY = "LEGACY_DEMO";
    private final MongoTemplate mongo;
    public CataloguePublication(MongoTemplate mongo) { this.mongo=mongo; }
    public static Criteria published() { return Criteria.where(FIELD).is(ADMIN); }
    public static Query publishedId(String id) { return Query.query(published().and("_id").is(id)); }
    public boolean isPublished(String id,Class<?> type) { return mongo.exists(publishedId(id),type); }
    public void publish(String collection,String id) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set(FIELD,ADMIN),collection);
    }
}
