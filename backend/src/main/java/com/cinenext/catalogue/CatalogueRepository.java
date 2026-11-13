package com.cinenext.catalogue;

import com.cinenext.catalogue.model.Movie;
import com.cinenext.catalogue.model.ShowSeat;
import com.cinenext.catalogue.model.Showtime;
import com.cinenext.catalogue.model.Theatre;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogueRepository {
    private final MongoTemplate mongo;
    private final java.time.Clock clock;

    public CatalogueRepository(MongoTemplate mongo,java.time.Clock clock) { this.mongo = mongo;this.clock=clock; }

    public Query movieQuery(String q, String genre, String language, String city, Instant now) {
        var query = new Query();
        if (hasText(q)) query.addCriteria(Criteria.where("title").regex(Pattern.quote(q.trim()), "i"));
        if (hasText(genre)) query.addCriteria(Criteria.where("genres").is(genre.trim()));
        if (hasText(language)) query.addCriteria(Criteria.where("language").is(language.trim()));
        if (hasText(city)) {
            var showQuery = Query.query(Criteria.where("city").is(city.trim()).and("startsAt").gte(now));
            var movieIds = mongo.findDistinct(showQuery, "movieId", Showtime.class, String.class);
            query.addCriteria(Criteria.where("_id").in(movieIds));
        }
        return query;
    }

    public long countMovies(Query query) { return mongo.count(query, Movie.class); }
    public List<Movie> movies(Query query, int page, int size) {
        return mongo.find(Query.of(query).with(Sort.by(Sort.Order.desc("featured"), Sort.Order.asc("title")))
                .skip((long) page * size).limit(size), Movie.class);
    }
    public Movie movie(String id) { return mongo.findById(id, Movie.class); }
    public Showtime showtime(String id) { return mongo.findById(id, Showtime.class); }
    public List<Theatre> theatres(String city) {
        var query = new Query().with(Sort.by("name"));
        if (hasText(city)) query.addCriteria(Criteria.where("city").is(city.trim()));
        return mongo.find(query, Theatre.class);
    }
    public List<Showtime> showtimes(String movieId, String city, Instant from, Instant until) {
        var query = Query.query(Criteria.where("startsAt").gte(from).lt(until)).with(Sort.by("startsAt", "theatreName"));
        if (hasText(movieId)) query.addCriteria(Criteria.where("movieId").is(movieId.trim()));
        if (hasText(city)) query.addCriteria(Criteria.where("city").is(city.trim()));
        return mongo.find(query.limit(250), Showtime.class);
    }
    public Map<String, Long> availableCounts(List<String> ids) {
        if (ids.isEmpty()) return Map.of();
        var aggregation = Aggregation.newAggregation(
                Aggregation.match(new Criteria().andOperator(Criteria.where("showtimeId").in(ids),new Criteria().orOperator(
                        Criteria.where("status").is("AVAILABLE"),Criteria.where("status").is("HELD").and("holdExpiresAt").lte(java.util.Date.from(clock.instant()))))),
                Aggregation.group("showtimeId").count().as("count"));
        var counts = new HashMap<String, Long>();
        for (var document : mongo.aggregate(aggregation, "show_seats", Document.class)) {
            counts.put(document.getString("_id"), ((Number) document.get("count")).longValue());
        }
        return counts;
    }
    public List<ShowSeat> seats(String showtimeId) {
        return mongo.find(Query.query(Criteria.where("showtimeId").is(showtimeId)).with(Sort.by("row", "number")), ShowSeat.class);
    }
    public List<String> distinct(String field, Class<?> type) {
        return mongo.findDistinct(new Query(), field, type, String.class).stream().sorted().toList();
    }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
