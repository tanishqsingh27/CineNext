package com.cinenext.catalogue;

import com.cinenext.common.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogueServiceTest {
    private CatalogueRepository repository;
    private CatalogueService service;
    private final Instant now = Instant.parse("2026-11-12T12:30:00Z");
    @BeforeEach void setup() {
        repository = mock(CatalogueRepository.class);
        service = new CatalogueService(repository, Clock.fixed(now, ZoneOffset.UTC));
    }
    @Test void missingMovieHasDeliberateNotFoundError() {
        assertThrows(NotFoundException.class, () -> service.movie("missing"));
    }
    @Test void historicalDatesNeverQueryForBookableShows() {
        assertTrue(service.showtimes(null, null, LocalDate.of(2026,11,11)).isEmpty());
        verifyNoInteractions(repository);
    }
    @Test void todayClampsStartToServerClockInsteadOfMidnight() {
        var end = Instant.parse("2026-11-12T18:30:00Z");
        when(repository.showtimes("orbit-nine", "Mumbai", now, end)).thenReturn(List.of());
        assertTrue(service.showtimes("orbit-nine", "Mumbai", null).isEmpty());
        verify(repository).showtimes("orbit-nine", "Mumbai", now, end);
    }
    @Test void tomorrowUsesIndianCalendarBoundaries() {
        var start = Instant.parse("2026-11-12T18:30:00Z");
        var end = Instant.parse("2026-11-13T18:30:00Z");
        when(repository.showtimes(null, null, start, end)).thenReturn(List.of());
        service.showtimes(null, null, LocalDate.of(2026,11,13));
        verify(repository).showtimes(null, null, start, end);
    }
    @Test void pageCountRoundsUpAndRetainsRequestedPage() {
        var query = new Query();
        when(repository.movieQuery(null,null,null,null,now)).thenReturn(query);
        when(repository.countMovies(query)).thenReturn(9L);
        when(repository.movies(query,1,8)).thenReturn(List.of());
        var result = service.movies(null,null,null,null,1,8);
        assertEquals(2,result.totalPages()); assertEquals(1,result.page()); assertEquals(9,result.totalElements());
    }
}
