package com.cinenext.catalogue.api;

import java.time.Instant;
import java.util.List;

public final class CatalogueDtos {
    private CatalogueDtos() {}
    public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
    public record Filters(List<String> cities, List<String> genres, List<String> languages) {}
    public record ShowtimeView(String id, String movieId, String theatreId, String theatreName, String screenName,
                               String city, Instant startsAt, String format, String language, long priceFromPaise,
                               String currency, long availableSeats, int totalSeats) {}
    public record SeatView(String id, String label, String row, int number, String category, long pricePaise,
                           String status, boolean accessible) {}
    public record SeatRow(String label, List<SeatView> seats) {}
    public record SeatMap(String showtimeId, List<SeatRow> rows, String screenLabel, String currency) {}
}
