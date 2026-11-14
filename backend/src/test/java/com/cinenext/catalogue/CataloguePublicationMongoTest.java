package com.cinenext.catalogue;

import com.cinenext.CineNextApplication;
import com.cinenext.admin.AdminController;
import com.cinenext.booking.BookingService;
import com.cinenext.catalogue.model.*;
import com.cinenext.common.BusinessException;
import com.cinenext.identity.AuthService;
import com.cinenext.identity.TokenService;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mongo") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CataloguePublicationMongoTest {
    private final String database="cinenext_test_publication_"+UUID.randomUUID().toString().replace("-","");
    private ConfigurableApplicationContext context;
    private MongoTemplate mongo;
    private CatalogueService catalogue;
    private CataloguePublication publication;
    private CataloguePublicationMigration migration;
    private BookingService bookings;
    private int port;
    private final HttpClient http=HttpClient.newHttpClient();

    @BeforeAll void start() {
        context=new SpringApplicationBuilder(CineNextApplication.class).profiles("local").run(
                "--server.port=0","--server.address=127.0.0.1","--cinenext.seed.enabled=true",
                "--spring.mongodb.uri=mongodb://127.0.0.1:27018/"+database+"?replicaSet=cinenext-rs&serverSelectionTimeoutMS=5000",
                "--cinenext.jwt.secret="+Base64.getEncoder().encodeToString((UUID.randomUUID().toString()+UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        mongo=context.getBean(MongoTemplate.class);catalogue=context.getBean(CatalogueService.class);
        publication=context.getBean(CataloguePublication.class);migration=context.getBean(CataloguePublicationMigration.class);bookings=context.getBean(BookingService.class);
        port=Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));
        assertEquals(0,mongo.count(new Query(),Movie.class),"Startup must not seed even with an obsolete seed=true setting");
        assertEquals(0,mongo.count(new Query(),Theatre.class));assertEquals(0,mongo.count(new Query(),Showtime.class));
    }
    @BeforeEach void clearOnlyTestRecords() {
        assertEquals(database,mongo.getDb().getName());assertTrue(database.startsWith("cinenext_test_"));
        for(String collection:List.of("movies","theatres","screens","seats","showtimes","show_seats","users","seat_holds","bookings","payments","audit_logs","catalogue_migrations"))mongo.remove(new Query(),collection);
    }
    @AfterAll void stop() {
        try {if(mongo!=null){assertEquals(database,mongo.getDb().getName());mongo.getDb().drop();}}
        finally {if(context!=null)context.close();}
    }
    private Movie movie(String id) {
        var value=new Movie(id,id,"Title "+id,"A saved story","A story entered by a cinema administrator.",List.of("Drama"),"English",100,"U",LocalDate.now(),0,"/posters/orbit-nine.svg","/posters/orbit-nine.svg",false);
        return mongo.insert(value);
    }
    private Theatre theatre(String id,String city) {
        var value=mongo.insert(new Theatre(id,"Cinema "+id,city,"Local test address",List.of()));
        mongo.insert(new Screen(id+"-screen",id,"Screen 1","LASER 2D",1));
        mongo.insert(new Seat(id+"-seat",id+"-screen","A1","A",1,"STANDARD",false));
        return value;
    }
    private Showtime show(String id,String movieId,Theatre theatre) {
        var start=Instant.now().plusSeconds(172800);
        var value=mongo.insert(new Showtime(id,movieId,theatre.id(),theatre.id()+"-screen",theatre.name(),"Screen 1",theatre.city(),start,"LASER 2D","English",22000,"INR",1,start.plusSeconds(7200)));
        mongo.insert(new ShowSeat(id+"-a1",id,theatre.id()+"-seat","A1","A",1,"STANDARD",22000,"AVAILABLE",false));
        return value;
    }
    private void audit(String action,String id) {bookings.audit("local-owner",action,id);}
    private HttpResponse<String> request(String path,String method,Document body,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json");
        if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body.toJson())).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void emptyProductionCatalogueStaysEmptyAcrossRepeatedStartupMigration() throws Exception {
        migration.run(new DefaultApplicationArguments());migration.run(new DefaultApplicationArguments());
        assertEquals(0,catalogue.movies(null,null,null,null,0,50).totalElements());
        assertTrue(catalogue.theatres(null).isEmpty());assertTrue(catalogue.filters().cities().isEmpty());
        assertTrue(catalogue.showtimes(null,null,LocalDate.now().plusDays(1)).isEmpty());
        assertEquals(0,Document.parse(request("/api/v1/movies","GET",null,null).body()).getInteger("totalElements"));
        assertEquals("[]",request("/api/v1/theatres","GET",null,null).body());
    }
    @Test void migrationPublishesOnlyAuditedSavesAndShowParentsAndPreservesBookings() throws Exception {
        var created=movie("created");var edited=movie("edited");var selected=movie("selected");movie("untouched-demo");
        var selectedVenue=theatre("selected-venue","Selected City");var unusedVenue=theatre("unused-venue","Hidden City");theatre("created-venue","Created City");
        var savedShow=show("saved-show",selected.id(),selectedVenue);var demoShow=show("demo-show","untouched-demo",unusedVenue);
        publication.publish("showtimes",demoShow.id());
        var owner=context.getBean(AuthService.class).create("Existing customer","existing@example.test",UUID.randomUUID().toString(),"USER");
        var hold=bookings.hold(owner.id(),demoShow.id(),List.of("demo-show-a1"),UUID.randomUUID().toString());
        var booking=bookings.pay(owner.id(),hold.id(),"SUCCESS",UUID.randomUUID().toString()).booking();
        mongo.updateMulti(new Query(),new Update().unset(CataloguePublication.FIELD),"showtimes");
        audit("MOVIE_CREATED",created.id());audit("MOVIE_UPDATED",edited.id());audit("THEATRE_CREATED","created-venue");audit("SHOWTIME_CREATED",savedShow.id());audit("MOVIE_CREATED","deleted-qa-movie");
        var beforeBooking=mongo.findById(booking.id(),Document.class,"bookings");
        long payments=mongo.count(new Query(),"payments"),users=mongo.count(new Query(),"users"),inventory=mongo.count(new Query(),"show_seats");
        migration.run(new DefaultApplicationArguments());
        assertEquals(Set.of("created","edited","selected"),new HashSet<>(catalogue.movies(null,null,null,null,0,50).items().stream().map(Movie::id).toList()));
        assertEquals(Set.of("selected-venue","created-venue"),new HashSet<>(catalogue.theatres(null).stream().map(Theatre::id).toList()));
        assertFalse(catalogue.filters().cities().contains("Hidden City"));
        assertEquals(Set.of("selected-venue-screen","created-venue-screen"),new HashSet<>(context.getBean(AdminController.class).screens().stream().map(Screen::id).toList()));
        assertEquals(404,request("/api/v1/movies/untouched-demo","GET",null,null).statusCode());
        assertEquals(404,request("/api/v1/showtimes/demo-show/seats","GET",null,null).statusCode());
        assertTrue(publication.isPublished(savedShow.id(),Showtime.class));assertFalse(publication.isPublished(demoShow.id(),Showtime.class));
        assertNull(mongo.findById("deleted-qa-movie",Movie.class));
        assertEquals(beforeBooking,mongo.findById(booking.id(),Document.class,"bookings"));
        assertEquals(payments,mongo.count(new Query(),"payments"));assertEquals(users,mongo.count(new Query(),"users"));assertEquals(inventory,mongo.count(new Query(),"show_seats"));
        assertEquals("CONFIRMED",bookings.booking(owner.id(),booking.id()).status());
        migration.run(new DefaultApplicationArguments());
        assertEquals(3,catalogue.movies(null,null,null,null,0,50).totalElements());
        assertEquals(booking.id(),bookings.verifyTicket(booking.ticketCode()).id());
        assertEquals("CANCELLED",bookings.cancel(owner.id(),booking.id()).status());
    }
    @Test void archivedShowCannotAcquireOrConfirmHoldsButCanReleaseThem() {
        movie("film");var venue=theatre("venue","Test City");var show=show("show","film",venue);
        assertEquals(404,assertThrows(BusinessException.class,()->bookings.hold("owner",show.id(),List.of("show-a1"),UUID.randomUUID().toString())).status().value());
        publication.publish("showtimes",show.id());String holdKey=UUID.randomUUID().toString();
        var hold=bookings.hold("owner",show.id(),List.of("show-a1"),holdKey);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(show.id())),new Update().set(CataloguePublication.FIELD,CataloguePublication.LEGACY),Showtime.class);
        assertEquals(404,assertThrows(BusinessException.class,()->bookings.hold("owner",show.id(),List.of("show-a1"),holdKey)).status().value());
        assertEquals(404,assertThrows(BusinessException.class,()->bookings.getHold("owner",hold.id())).status().value());
        assertEquals(404,assertThrows(BusinessException.class,()->bookings.pay("owner",hold.id(),"SUCCESS",UUID.randomUUID().toString())).status().value());
        assertEquals(0,mongo.count(new Query(),"payments"));assertEquals(0,mongo.count(new Query(),"bookings"));
        bookings.release("owner",hold.id());assertEquals("AVAILABLE",mongo.findById("show-a1",ShowSeat.class).status());
    }
    @Test void schedulingIgnoresUnusedArchivedSlotsButPreservesConfirmedHistoricalConflicts() {
        movie("film");var venue=theatre("venue","Test City");var old=show("old-show","film",venue);
        publication.publish("movies","film");publication.publish("theatres",venue.id());publication.publish("screens",venue.id()+"-screen");publication.publish("showtimes",old.id());
        var hold=bookings.hold("owner",old.id(),List.of("old-show-a1"),UUID.randomUUID().toString());
        var booked=bookings.pay("owner",hold.id(),"SUCCESS",UUID.randomUUID().toString()).booking();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(old.id())),new Update().set(CataloguePublication.FIELD,CataloguePublication.LEGACY),Showtime.class);
        var admin=context.getBean(AdminController.class);
        var principal=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-token").header("alg","none").subject("admin").build();
        var newShow=new AdminController.ShowInput("film",venue.id()+"-screen",old.startsAt(),22000);
        assertEquals(409,assertThrows(BusinessException.class,()->admin.addShow(principal,newShow)).status().value());
        bookings.cancel("owner",booked.id());
        var published=admin.addShow(principal,newShow);
        assertTrue(publication.isPublished(published.id(),Showtime.class));
        assertEquals(1,catalogue.seatMap(published.id()).rows().getFirst().seats().size());
    }
}
