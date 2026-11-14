package com.cinenext.catalogue.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("showtimes")
@CompoundIndexes({
    @CompoundIndex(name = "published_screen_start_unique", def = "{'screenId':1,'startsAt':1}", unique = true, partialFilter = "{'catalogueSource':'ADMIN'}"),
    @CompoundIndex(name = "movie_city_start", def = "{'movieId':1,'city':1,'startsAt':1}")
})
public record Showtime(@Id String id, String movieId, String theatreId, String screenId, String theatreName,
                       String screenName, String city, Instant startsAt, String format, String language,
                       long priceFromPaise, String currency, int totalSeats, Instant endsAt) {
    public Showtime(String id,String movieId,String theatreId,String screenId,String theatreName,String screenName,String city,Instant startsAt,String format,String language,long priceFromPaise,String currency,int totalSeats) {
        this(id,movieId,theatreId,screenId,theatreName,screenName,city,startsAt,format,language,priceFromPaise,currency,totalSeats,startsAt.plusSeconds(10800));
    }
}
