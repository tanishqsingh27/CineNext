package com.cinenext.admin;

import com.cinenext.booking.BookingService;
import com.cinenext.catalogue.CataloguePublication;
import com.cinenext.catalogue.model.*;
import com.cinenext.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class AdminController {
    private final MongoTemplate mongo; private final BookingService bookings;private final Clock clock;private final CataloguePublication publication;
    public AdminController(MongoTemplate mongo,BookingService bookings,Clock clock,CataloguePublication publication) { this.mongo=mongo;this.bookings=bookings;this.clock=clock;this.publication=publication; }
    public record MovieInput(@NotBlank @Size(max=120) String title,@NotBlank @Pattern(regexp="[a-z0-9-]{1,100}",message="Use 1–100 lowercase letters, numbers or hyphens.") String slug,
        @NotBlank @Size(max=160) String tagline,@NotBlank @Size(max=3000) String synopsis,@NotEmpty @Size(max=8) List<@NotBlank @Size(max=40) String> genres,
        @NotBlank @Size(max=40) String language,@Min(1) @Max(300) int durationMinutes,@NotBlank @Size(max=12) String certificate,
        @NotNull LocalDate releaseDate,@DecimalMin("0") @DecimalMax("10") double rating,
        @NotBlank @Pattern(regexp="/posters/(orbit-nine|monsoon-letters|the-last-signal|wild-at-heart|midnight-in-jaipur|velocity|paper-moons|the-deep-blue)\\.svg") String posterUrl,boolean featured) {}
    public record TheatreInput(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=80) String city,@NotBlank @Size(max=300) String address,@Size(max=10) List<@NotBlank @Size(max=50) String> amenities,@NotBlank @Size(max=50) String screenName,@NotBlank @Size(max=40) String format) {}
    public record ShowInput(@NotBlank String movieId,@NotBlank String screenId,@NotNull Instant startsAt,@Min(1000) @Max(1000000) long priceFromPaise) {}
    public record TicketInput(@NotBlank @Size(max=100) String ticketCode) {}
    private Movie movie(String id,MovieInput body) { return new Movie(id,body.slug(),body.title().trim(),body.tagline(),body.synopsis(),body.genres(),body.language().trim(),body.durationMinutes(),body.certificate(),body.releaseDate(),body.rating(),body.posterUrl(),body.posterUrl(),body.featured()); }
    @PostMapping("/movies") @ResponseStatus(HttpStatus.CREATED)
    public Movie addMovie(@AuthenticationPrincipal Jwt user,@Valid @RequestBody MovieInput body) {
        return bookings.transaction(()->{ var movie=mongo.insert(movie(body.slug(),body));publication.publish("movies",movie.id());bookings.audit(user.getSubject(),"MOVIE_CREATED",movie.id());return movie; });
    }
    @PutMapping("/movies/{id}") public Movie editMovie(@AuthenticationPrincipal Jwt user,@PathVariable String id,@Valid @RequestBody MovieInput body) {
        return bookings.transaction(()->{ if(!publication.isPublished(id,Movie.class)) throw new BusinessException(HttpStatus.NOT_FOUND,"Movie not found.");var movie=mongo.save(movie(id,body));publication.publish("movies",id);bookings.audit(user.getSubject(),"MOVIE_UPDATED",id);return movie; });
    }
    @GetMapping("/screens") public List<Screen> screens() { return mongo.find(Query.query(CataloguePublication.published()),Screen.class); }
    @PostMapping("/theatres") @ResponseStatus(HttpStatus.CREATED)
    public Theatre addTheatre(@AuthenticationPrincipal Jwt user,@Valid @RequestBody TheatreInput body) {
        String id="cinema-"+UUID.randomUUID();String screenId=id+"-screen-1";
        return bookings.transaction(()->{
            var theatre=mongo.insert(new Theatre(id,body.name().trim(),body.city().trim(),body.address().trim(),body.amenities()==null?List.of():body.amenities()));
            mongo.insert(new Screen(screenId,id,body.screenName().trim(),body.format().trim(),96));
            publication.publish("theatres",id);publication.publish("screens",screenId);
            var seats=new ArrayList<Seat>();
            for(char row='A';row<='H';row++) for(int number=1;number<=12;number++) {
                String label=""+row+number;seats.add(new Seat(screenId+"-"+label.toLowerCase(),screenId,label,""+row,number,row<='C'?"STANDARD":row<='F'?"PREMIUM":"RECLINER",row=='A'&&(number==5||number==6)));
            }
            mongo.insert(seats,Seat.class);bookings.audit(user.getSubject(),"THEATRE_CREATED",id);return theatre;
        });
    }
    @PostMapping("/showtimes") @ResponseStatus(HttpStatus.CREATED)
    public Showtime addShow(@AuthenticationPrincipal Jwt user,@Valid @RequestBody ShowInput body) {
        if(!body.startsAt().isAfter(clock.instant().plusSeconds(300))||body.startsAt().isAfter(clock.instant().plusSeconds(366L*86400))) throw new BusinessException(HttpStatus.BAD_REQUEST,"Schedule shows at least five minutes ahead and within the next year.");
        String id="show-"+UUID.randomUUID();
        return bookings.transaction(()-> {
            var movie=mongo.findOne(CataloguePublication.publishedId(body.movieId()),Movie.class);var screen=mongo.findOne(CataloguePublication.publishedId(body.screenId()),Screen.class);
            if(movie==null||screen==null) throw new BusinessException(HttpStatus.BAD_REQUEST,"Choose an existing movie and screen.");
            var theatre=mongo.findOne(CataloguePublication.publishedId(screen.theatreId()),Theatre.class);
            if(theatre==null) throw new BusinessException(HttpStatus.BAD_REQUEST,"Choose a screen belonging to a published cinema.");
            // Updating the screen serializes competing schedule writes, including distinct start times.
            mongo.updateFirst(Query.query(Criteria.where("_id").is(screen.id())),new Update().inc("scheduleVersion",1),Screen.class);
            Instant end=body.startsAt().plusSeconds((movie.durationMinutes()+20L)*60);
            var bookedShowIds=mongo.findDistinct(Query.query(Criteria.where("status").is("CONFIRMED")),"showtimeId","bookings",String.class);
            var occupiesScreen=new Criteria().orOperator(CataloguePublication.published(),Criteria.where("_id").in(bookedShowIds));
            if(mongo.exists(Query.query(new Criteria().andOperator(Criteria.where("screenId").is(screen.id()).and("startsAt").lt(end).and("endsAt").gt(body.startsAt()),occupiesScreen)),Showtime.class)) throw new BusinessException(HttpStatus.CONFLICT,"This screen already has an overlapping show (including the 20-minute turnaround).");
            var show=new Showtime(id,movie.id(),theatre.id(),screen.id(),theatre.name(),screen.name(),theatre.city(),body.startsAt(),screen.format(),movie.language(),body.priceFromPaise(),"INR",screen.totalSeats(),end);
            var seats=mongo.find(Query.query(Criteria.where("screenId").is(screen.id())),Seat.class);
            if(seats.size()!=screen.totalSeats()) throw new BusinessException(HttpStatus.CONFLICT,"Screen inventory is incomplete.");
            mongo.insert(show);publication.publish("showtimes",id);mongo.insert(seats.stream().map(seat -> new ShowSeat(id+"-"+seat.label().toLowerCase(),id,seat.id(),seat.label(),seat.row(),seat.number(),seat.category(),body.priceFromPaise()+(seat.category().equals("PREMIUM")?8000:seat.category().equals("RECLINER")?16000:0),"AVAILABLE",seat.accessible())).toList(),ShowSeat.class);
            bookings.audit(user.getSubject(),"SHOWTIME_CREATED",id);return show;
        });
    }
    @GetMapping("/overview") public Map<String,Object> overview() {
        var confirmed=mongo.find(Query.query(Criteria.where("status").is("CONFIRMED")),Document.class,"bookings");
        var days=new ArrayList<Map<String,Object>>();var today=LocalDate.now(clock.withZone(com.cinenext.catalogue.CatalogueService.CINEMA_ZONE));
        for(int index=6;index>=0;index--) {
            var date=today.minusDays(index);long total=confirmed.stream().filter(b->b.getDate("createdAt").toInstant().atZone(com.cinenext.catalogue.CatalogueService.CINEMA_ZONE).toLocalDate().equals(date)).mapToLong(b->((Number)b.get("totalPaise")).longValue()).sum();
            days.add(Map.of("date",date.toString(),"revenuePaise",total));
        }
        return Map.of("movies",mongo.count(Query.query(CataloguePublication.published()),Movie.class),"cinemas",mongo.count(Query.query(CataloguePublication.published()),Theatre.class),"confirmedBookings",confirmed.size(),"revenuePaise",confirmed.stream().mapToLong(b->((Number)b.get("totalPaise")).longValue()).sum(),"daily",days);
    }
    @PostMapping("/tickets/verify") public BookingService.BookingView verify(@Valid @RequestBody TicketInput body) { return bookings.verifyTicket(body.ticketCode()); }
}
