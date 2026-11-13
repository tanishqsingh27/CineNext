package com.cinenext.catalogue;

import com.cinenext.catalogue.api.CatalogueDtos.Filters;
import com.cinenext.catalogue.api.CatalogueDtos.PageResult;
import com.cinenext.catalogue.api.CatalogueDtos.SeatMap;
import com.cinenext.catalogue.api.CatalogueDtos.SeatRow;
import com.cinenext.catalogue.api.CatalogueDtos.SeatView;
import com.cinenext.catalogue.api.CatalogueDtos.ShowtimeView;
import com.cinenext.catalogue.model.Movie;
import com.cinenext.catalogue.model.ShowSeat;
import com.cinenext.catalogue.model.Theatre;
import com.cinenext.common.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CatalogueService {
    public static final ZoneId CINEMA_ZONE = ZoneId.of("Asia/Kolkata");
    private final CatalogueRepository repository;
    private final Clock clock;

    public CatalogueService(CatalogueRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PageResult<Movie> movies(String q, String genre, String language, String city, int page, int size) {
        var query = repository.movieQuery(q, genre, language, city, clock.instant());
        long total = repository.countMovies(query);
        return new PageResult<>(repository.movies(query, page, size), page, size, total, (int) ((total + size - 1) / size));
    }
    public Movie movie(String id) {
        var movie = repository.movie(id);
        if (movie == null) throw new NotFoundException("Movie not found.");
        return movie;
    }
    public List<Theatre> theatres(String city) { return repository.theatres(city); }
    public List<ShowtimeView> showtimes(String movieId, String city, LocalDate requestedDate) {
        var date = requestedDate == null ? LocalDate.now(clock.withZone(CINEMA_ZONE)) : requestedDate;
        var start = date.atStartOfDay(CINEMA_ZONE).toInstant();
        var end = date.plusDays(1).atStartOfDay(CINEMA_ZONE).toInstant();
        // Past shows cannot be selected even if the client asks for today's elapsed slots.
        var from = start.isBefore(clock.instant()) ? clock.instant() : start;
        if (!from.isBefore(end)) return List.of();
        var shows = repository.showtimes(movieId, city, from, end);
        var counts = repository.availableCounts(shows.stream().map(s -> s.id()).toList());
        return shows.stream().map(s -> new ShowtimeView(s.id(), s.movieId(), s.theatreId(), s.theatreName(),
                s.screenName(), s.city(), s.startsAt(), s.format(), s.language(), s.priceFromPaise(), s.currency(),
                counts.getOrDefault(s.id(), 0L), s.totalSeats())).toList();
    }
    public SeatMap seatMap(String showtimeId) {
        var showtime = repository.showtime(showtimeId);
        if (showtime == null) throw new NotFoundException("Showtime not found.");
        var rows = repository.seats(showtimeId).stream().collect(Collectors.groupingBy(ShowSeat::row, LinkedHashMap::new, Collectors.toList()));
        return new SeatMap(showtimeId, rows.entrySet().stream().map(row -> new SeatRow(row.getKey(), row.getValue().stream()
                .map(seat -> new SeatView(seat.id(), seat.label(), seat.row(), seat.number(), seat.category(), seat.pricePaise(),
                        "HELD".equals(seat.status()) && seat.holdExpiresAt()!=null && !seat.holdExpiresAt().isAfter(clock.instant()) ? "AVAILABLE" : seat.status(), seat.accessible()))
                .toList())).toList(), "SCREEN", showtime.currency());
    }
    public Filters filters() {
        return new Filters(repository.distinct("city", Theatre.class), repository.distinct("genres", Movie.class), repository.distinct("language", Movie.class));
    }
}
