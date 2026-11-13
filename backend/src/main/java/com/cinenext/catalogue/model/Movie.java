package com.cinenext.catalogue.model;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("movies")
public record Movie(@Id String id, @Indexed(unique = true) String slug, String title, String tagline,
                    String synopsis, List<String> genres, String language, int durationMinutes,
                    String certificate, LocalDate releaseDate, double rating, String posterUrl,
                    String backdropUrl, boolean featured) {}
