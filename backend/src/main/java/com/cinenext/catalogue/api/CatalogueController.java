package com.cinenext.catalogue.api;

import com.cinenext.catalogue.CatalogueService;
import com.cinenext.catalogue.api.CatalogueDtos.Filters;
import com.cinenext.catalogue.api.CatalogueDtos.PageResult;
import com.cinenext.catalogue.api.CatalogueDtos.SeatMap;
import com.cinenext.catalogue.api.CatalogueDtos.ShowtimeView;
import com.cinenext.catalogue.model.Movie;
import com.cinenext.catalogue.model.Theatre;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalogue", description = "Public, read-only catalogue containing only administrator-published entries.")
public class CatalogueController {
    private final CatalogueService catalogue;
    public CatalogueController(CatalogueService catalogue) { this.catalogue = catalogue; }

    @GetMapping("/movies")
    @Operation(summary = "Search and paginate movies", description = "Literal case-insensitive title search. Other filters are exact. Page starts at 0, size is 1–50.")
    public PageResult<Movie> movies(@RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 100) String genre,
            @RequestParam(required = false) @Size(max = 100) String language,
            @RequestParam(required = false) @Size(max = 100) String city,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return catalogue.movies(q, genre, language, city, page, size);
    }

    @GetMapping("/movies/{id}")
    @Operation(summary = "Get one movie")
    public Movie movie(@PathVariable @Pattern(regexp = "[a-z0-9-]{1,100}") String id) { return catalogue.movie(id); }

    @GetMapping("/theatres")
    @Operation(summary = "List theatres, optionally by city")
    public List<Theatre> theatres(@RequestParam(required = false) @Size(max = 100) String city) { return catalogue.theatres(city); }

    @GetMapping("/showtimes")
    @Operation(summary = "Find future showtimes on one date", description = "Date defaults to today in Asia/Kolkata. Past showtimes are omitted. Result is capped at 250 shows per date.")
    public List<ShowtimeView> showtimes(@RequestParam(required = false) @Size(max = 100) String movieId,
            @RequestParam(required = false) @Size(max = 100) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return catalogue.showtimes(movieId, city, date);
    }

    @GetMapping("/showtimes/{id}/seats")
    @Operation(summary = "Preview a showtime's actual seat inventory", description = "Read-only. Displayed availability can change; this endpoint does not reserve seats.")
    public SeatMap seats(@PathVariable @Pattern(regexp = "[a-z0-9-]{1,150}") String id) { return catalogue.seatMap(id); }

    @GetMapping("/catalogue/filters")
    @Operation(summary = "Get available cities, genres and languages")
    public Filters filters() { return catalogue.filters(); }
}
