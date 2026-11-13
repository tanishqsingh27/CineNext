package com.cinenext.catalogue.seed;

import com.cinenext.catalogue.CatalogueService;
import com.cinenext.catalogue.model.Movie;
import com.cinenext.catalogue.model.Screen;
import com.cinenext.catalogue.model.Seat;
import com.cinenext.catalogue.model.ShowSeat;
import com.cinenext.catalogue.model.Showtime;
import com.cinenext.catalogue.model.Theatre;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
@ConditionalOnProperty(name = "cinenext.seed.enabled", havingValue = "true")
public class CatalogueSeeder implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(CatalogueSeeder.class);
    private final MongoTemplate mongo;
    private final Clock clock;

    public CatalogueSeeder(MongoTemplate mongo, Clock clock) { this.mongo = mongo; this.clock = clock; }

    @Override
    public void run(ApplicationArguments args) {
        var movies = DemoProgramme.movies();
        var theatres = DemoProgramme.theatres();
        insertMissing(movies, Movie.class);
        insertMissing(theatres, Theatre.class);
        var screens = new ArrayList<Screen>();
        var seats = new ArrayList<Seat>();
        var shows = new ArrayList<Showtime>();
        var showSeats = new ArrayList<ShowSeat>();
        var today = LocalDate.now(clock.withZone(CatalogueService.CINEMA_ZONE));
        for (var theatre : theatres) {
            for (int screenNumber = 1; screenNumber <= 2; screenNumber++) {
                String screenId = theatre.id() + "-screen-" + screenNumber;
                String format = screenNumber == 1 ? "LASER 2D" : "DOLBY ATMOS";
                screens.add(new Screen(screenId, theatre.id(), "Screen " + screenNumber, format, 96));
                var screenSeats = buildSeats(screenId);
                seats.addAll(screenSeats);
                for (int day = 0; day < 7; day++) {
                    var date = today.plusDays(day);
                    for (int slot = 0; slot < 4; slot++) {
                        var movie = movies.get((screenNumber - 1) * 4 + slot);
                        var start = date.atTime(10 + slot * 3, 0).atZone(CatalogueService.CINEMA_ZONE).toInstant();
                        String showId = screenId + "-" + date + "-" + slot;
                        shows.add(new Showtime(showId, movie.id(), theatre.id(), screenId, theatre.name(), "Screen " + screenNumber,
                                theatre.city(), start, format, movie.language(), 22000, "INR", 96));
                        for (var seat : screenSeats) {
                            long price = switch (seat.category()) { case "PREMIUM" -> 30000; case "RECLINER" -> 38000; default -> 22000; };
                            // Maintenance blocks are not bookings; no fake customer ownership is seeded.
                            String status = seat.label().equals("A1") || seat.label().equals("A12") ? "BLOCKED" : "AVAILABLE";
                            showSeats.add(new ShowSeat(showId + "-" + seat.label().toLowerCase(), showId, seat.id(), seat.label(), seat.row(),
                                    seat.number(), seat.category(), price, status, seat.accessible()));
                        }
                    }
                }
            }
        }
        insertMissing(screens, Screen.class);
        insertMissing(seats, Seat.class);
        insertMissing(shows, Showtime.class);
        insertMissing(showSeats, ShowSeat.class);
        LOG.info("Local fictional catalogue prepared: {} movies, {} theatres, 7 programme days; existing documents preserved", movies.size(), theatres.size());
    }

    static List<Seat> buildSeats(String screenId) {
        var seats = new ArrayList<Seat>();
        for (char row = 'A'; row <= 'H'; row++) {
            for (int number = 1; number <= 12; number++) {
                String label = "" + row + number;
                String category = row <= 'C' ? "STANDARD" : row <= 'F' ? "PREMIUM" : "RECLINER";
                boolean accessible = row == 'A' && (number == 5 || number == 6);
                seats.add(new Seat(screenId + "-" + label.toLowerCase(), screenId, label, String.valueOf(row), number, category, accessible));
            }
        }
        return seats;
    }

    private <T> void insertMissing(List<T> documents, Class<T> type) {
        // $setOnInsert is restart-safe and never resets later ownership or administrator changes.
        // Chunking bounds the size of each driver bulk request.
        for (int offset = 0; offset < documents.size(); offset += 500) {
            var bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, type);
            for (var value : documents.subList(offset, Math.min(offset + 500, documents.size()))) {
                var document = new Document();
                mongo.getConverter().write(value, document);
                var update = new Update();
                document.forEach(update::setOnInsert);
                bulk.upsert(Query.query(Criteria.where("_id").is(document.get("_id"))), update);
            }
            bulk.execute();
        }
    }
}
