package com.cinenext.catalogue;

import java.time.Clock;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Archives pre-publication demo records in place; tickets and financial history are never deleted. */
@Component @Order(10)
public class CataloguePublicationMigration implements ApplicationRunner {
    public static final String MIGRATION_ID="admin-catalogue-v1";
    private final MongoTemplate mongo;
    private final Clock clock;
    public CataloguePublicationMigration(MongoTemplate mongo,Clock clock) { this.mongo=mongo;this.clock=clock; }
    @Override public void run(ApplicationArguments arguments) {
        if(mongo.exists(Query.query(Criteria.where("_id").is(MIGRATION_ID)),"catalogue_migrations")) return;
        var movies=auditedIds(List.of("MOVIE_CREATED","MOVIE_UPDATED"));
        var theatres=auditedIds(List.of("THEATRE_CREATED"));
        var shows=auditedIds(List.of("SHOWTIME_CREATED"));
        var screens=new HashSet<String>();
        // Publishing a show explicitly adopts its selected movie, cinema and screen.
        for(var show:mongo.find(Query.query(Criteria.where("_id").in(shows)),Document.class,"showtimes")) {
            add(movies,show.getString("movieId"));add(theatres,show.getString("theatreId"));add(screens,show.getString("screenId"));
        }
        for(var screen:mongo.find(Query.query(Criteria.where("theatreId").in(theatres)),Document.class,"screens")) add(screens,screen.getString("_id"));
        migrate("movies",movies);migrate("theatres",theatres);migrate("screens",screens);migrate("showtimes",shows);
        mongo.upsert(Query.query(Criteria.where("_id").is(MIGRATION_ID)),new Update().setOnInsert("completedAt",Date.from(clock.instant())),"catalogue_migrations");
    }
    private Set<String> auditedIds(List<String> actions) {
        return new HashSet<>(mongo.findDistinct(Query.query(Criteria.where("action").in(actions)),"resourceId","audit_logs",String.class));
    }
    private static void add(Set<String> ids,String value) { if(value!=null)ids.add(value); }
    private void migrate(String collection,Set<String> adopted) {
        // Each step is repeatable if startup is interrupted before the completion marker.
        mongo.updateMulti(Query.query(Criteria.where(CataloguePublication.FIELD).exists(false)),new Update().set(CataloguePublication.FIELD,CataloguePublication.LEGACY),collection);
        if(!adopted.isEmpty())mongo.updateMulti(Query.query(Criteria.where("_id").in(adopted)),new Update().set(CataloguePublication.FIELD,CataloguePublication.ADMIN),collection);
    }
}
