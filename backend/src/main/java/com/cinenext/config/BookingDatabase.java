package com.cinenext.config;

import java.util.List;
import com.cinenext.identity.UserAccount;
import com.cinenext.catalogue.model.*;
import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import com.mongodb.TransactionOptions;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration @Order(0)
public class BookingDatabase implements ApplicationRunner {
    private final MongoTemplate mongo;
    public BookingDatabase(MongoTemplate mongo) { this.mongo=mongo; }
    @Bean TransactionTemplate bookingTransactions(MongoDatabaseFactory factory) {
        return new TransactionTemplate(new MongoTransactionManager(factory,TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY).build()));
    }
    @Override public void run(ApplicationArguments args) {
        for (String name:new String[]{"users","seat_holds","bookings","payments","audit_logs","movies","theatres","screens","seats","showtimes","show_seats","catalogue_migrations"})
            if(!mongo.collectionExists(name)) mongo.createCollection(name);
        // Archived demo slots must not reserve times in the administrator's programme.
        // Confirmed historical bookings still participate in the transactional overlap check.
        if(mongo.indexOps("showtimes").getIndexInfo().stream().anyMatch(index -> "screen_start_unique".equals(index.getName())))
            mongo.indexOps("showtimes").dropIndex("screen_start_unique");
        var resolver=new org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext());
        for(Class<?> type:List.of(UserAccount.class,Movie.class,ShowSeat.class,Showtime.class,Screen.class,Seat.class,Theatre.class))
            resolver.resolveIndexFor(type).forEach(index -> mongo.indexOps(type).ensureIndex(index));
        mongo.indexOps("bookings").ensureIndex(new Index().on("userId",Sort.Direction.ASC).on("createdAt",Sort.Direction.DESC));
        mongo.indexOps("bookings").ensureIndex(new Index().on("ticketCode",Sort.Direction.ASC).unique());
        mongo.indexOps("show_seats").ensureIndex(new Index().on("status",Sort.Direction.ASC).on("holdExpiresAt",Sort.Direction.ASC));
    }
}
