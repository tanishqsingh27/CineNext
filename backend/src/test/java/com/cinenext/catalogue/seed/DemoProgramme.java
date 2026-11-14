package com.cinenext.catalogue.seed;

import com.cinenext.catalogue.model.Movie;
import com.cinenext.catalogue.model.Theatre;
import java.time.LocalDate;
import java.util.List;

/** Original fictional programme. Ratings are illustrative sample data, not real audience votes. */
public final class DemoProgramme {
    private DemoProgramme() {}

    public static List<Movie> movies() {
        return List.of(
            movie("orbit-nine", "Orbit Nine", "Beyond the familiar.", "At the edge of a silent solar system, a cartographer receives a message sent in her own voice. A voyage into the unknown becomes a journey home.", List.of("Sci-Fi", "Adventure"), "English", 148, "UA 13+", 8.7, true),
            movie("monsoon-letters", "Monsoon Letters", "Some stories arrive with the rain.", "An archivist in Mumbai discovers a bundle of letters never delivered. Following their trail through the city, she finds a story that changes her own.", List.of("Drama", "Romance"), "Hindi", 126, "U", 8.4, false),
            movie("the-last-signal", "The Last Signal", "Someone is still listening.", "A radio engineer on an isolated mountain receives a distress call from a station closed thirty years ago. One night remains to learn who is calling.", List.of("Thriller"), "English", 112, "UA 16+", 8.1, false),
            movie("wild-at-heart", "Wild at Heart", "Take the long way home.", "Two estranged siblings inherit a struggling wildlife rescue and embark on an unforgettable road trip to return its most unusual resident to the forest.", List.of("Adventure", "Comedy"), "Kannada", 118, "U", 8.2, false),
            movie("midnight-in-jaipur", "Midnight in Jaipur", "A city. A night. A second chance.", "A musician and a night-shift guide cross paths during the city's longest festival. Before sunrise, an unexpected friendship asks them both to choose a new direction.", List.of("Romance", "Drama"), "Hindi", 132, "UA 13+", 8.5, false),
            movie("velocity", "Velocity", "Every second has a price.", "A young engineer enters an electric endurance rally with a car she built herself, confronting a powerful team and the limits of her own invention.", List.of("Action", "Drama"), "English", 139, "UA 13+", 7.9, false),
            movie("paper-moons", "Paper Moons", "A little imagination goes a long way.", "A child who builds paper worlds discovers that one of them comes alive each night. Together with an unlikely paper explorer, she must save its disappearing moon.", List.of("Animation", "Adventure"), "English", 97, "U", 8.6, false),
            movie("the-deep-blue", "The Deep Blue", "The ocean remembers.", "An oceanographer returns to her coastal hometown to investigate an unexplained underwater sound, uncovering a fragile ecosystem and a family story left untold.", List.of("Adventure", "Drama"), "English", 121, "UA 13+", 8.3, false)
        );
    }

    public static List<Theatre> theatres() {
        return List.of(
            new Theatre("cinenext-harbour", "CineNext Harbour", "Mumbai", "18 Harbour Walk, Bandra West, Mumbai", List.of("Laser projection", "Dolby Atmos", "Wheelchair access", "Cafe")),
            new Theatre("cinenext-gardens", "CineNext Gardens", "Bengaluru", "42 Garden Avenue, Indiranagar, Bengaluru", List.of("Laser projection", "Dolby Atmos", "Wheelchair access", "Parking")),
            new Theatre("cinenext-capital", "CineNext Capital", "Delhi", "7 Arts Quarter, Saket, Delhi", List.of("Laser projection", "Dolby Atmos", "Wheelchair access", "Cafe"))
        );
    }

    private static Movie movie(String slug, String title, String tagline, String synopsis, List<String> genres,
            String language, int minutes, String certificate, double rating, boolean featured) {
        return new Movie(slug, slug, title, tagline, synopsis, genres, language, minutes, certificate,
                LocalDate.of(2026, 11, 6), rating, "/posters/" + slug + ".svg", "/posters/" + slug + ".svg", featured);
    }
}
