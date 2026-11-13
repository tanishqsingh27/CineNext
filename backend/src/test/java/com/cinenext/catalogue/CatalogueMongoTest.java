package com.cinenext.catalogue;

import com.cinenext.CineNextApplication;
import com.cinenext.booking.BookingService;
import com.cinenext.identity.AuthService;
import com.cinenext.identity.TokenService;
import com.cinenext.identity.UserAccount;
import com.cinenext.common.BusinessException;
import com.cinenext.catalogue.model.Movie;
import com.cinenext.catalogue.model.ShowSeat;
import com.cinenext.catalogue.seed.CatalogueSeeder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.Base64;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import static org.junit.jupiter.api.Assertions.*;

/** Real MongoDB integration; intentionally opt-in and isolated from cinenext_dev. */
@Tag("mongo")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogueMongoTest {
    private static final String DATABASE = "cinenext_test_" + UUID.randomUUID().toString().replace("-", "");
    private ConfigurableApplicationContext context;
    private MongoTemplate mongo;
    private CatalogueService catalogue;
    private CatalogueSeeder seeder;
    private int port;
    private BookingService reservations;
    private AuthService auth;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeAll void startRealApplication() {
        context = new SpringApplicationBuilder(CineNextApplication.class).profiles("local").run(
                "--server.port=0", "--server.address=127.0.0.1",
                "--spring.mongodb.uri=mongodb://127.0.0.1:27018/" + DATABASE + "?replicaSet=cinenext-rs&serverSelectionTimeoutMS=5000",
                "--cinenext.jwt.secret="+Base64.getEncoder().encodeToString((UUID.randomUUID().toString()+UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        mongo = context.getBean(MongoTemplate.class);
        catalogue = context.getBean(CatalogueService.class);
        seeder = context.getBean(CatalogueSeeder.class);
        reservations=context.getBean(BookingService.class);auth=context.getBean(AuthService.class);
        port = Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));
    }
    @AfterAll void removeOnlyOwnedTestDatabase() {
        try {
            if (mongo != null) {
                assertEquals(DATABASE, mongo.getDb().getName());
                assertTrue(DATABASE.startsWith("cinenext_test_"));
                mongo.getDb().drop();
            }
        } finally { if (context != null) context.close(); }
    }
    private HttpResponse<String> request(String path, String method, String origin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));
        if (origin != null) builder.header("Origin",origin);
        return http.send(builder.method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void realRepositorySearchEscapesMetacharactersAndHonoursFilters() {
        assertTrue(catalogue.movies(null,null,null,"Mumbai",0,50).totalElements()>=8);
        assertEquals(1, catalogue.movies("orbit",null,"English",null,0,12).totalElements());
        assertEquals(0, catalogue.movies("[",null,null,null,0,12).totalElements());
        assertEquals(0, catalogue.movies(".*",null,null,null,0,12).totalElements());
        assertEquals(2, catalogue.movies(null,null,"Hindi",null,0,12).totalElements());
        assertEquals(0, catalogue.movies(null,null,null,"Unseeded City",0,12).totalElements());
    }
    @Test void actualInventoryMatchesCapacityAndAvailability() {
        var date = LocalDate.now(CatalogueService.CINEMA_ZONE).plusDays(1);
        var shows = catalogue.showtimes("orbit-nine","Mumbai",date);
        assertEquals(1,shows.size());
        var show = shows.getFirst(); var map = catalogue.seatMap(show.id());
        var seats = map.rows().stream().flatMap(row -> row.seats().stream()).toList();
        assertEquals(show.totalSeats(),seats.size());
        assertEquals(94,show.availableSeats());
        assertEquals(2,seats.stream().filter(seat -> seat.status().equals("BLOCKED")).count());
        assertEquals(2,seats.stream().filter(seat -> seat.accessible()).count());
        assertEquals(96,seats.stream().map(seat -> seat.id()).distinct().count());
    }
    @Test void uniqueShowSeatIndexRejectsDuplicatePhysicalSeat() {
        var seat = mongo.findOne(new Query(),ShowSeat.class);
        assertNotNull(seat);
        var duplicate = new ShowSeat("duplicate-id",seat.showtimeId(),seat.seatId(),seat.label(),seat.row(),seat.number(),seat.category(),seat.pricePaise(),seat.status(),seat.accessible());
        assertThrows(DuplicateKeyException.class, () -> mongo.insert(duplicate));
    }
    @Test void seedingAgainPreservesExistingDataAndDoesNotDuplicateInventory() {
        var query = Query.query(Criteria.where("_id").is("orbit-nine"));
        var original = mongo.findOne(query,Movie.class); assertNotNull(original);
        long inventory = mongo.count(new Query(),ShowSeat.class);
        try {
            mongo.updateFirst(query,new Update().set("tagline","Preserved administrator edit"),Movie.class);
            seeder.run(new DefaultApplicationArguments());
            assertEquals("Preserved administrator edit",catalogue.movie("orbit-nine").tagline());
            assertEquals(inventory,mongo.count(new Query(),ShowSeat.class));
        } finally { mongo.updateFirst(query,new Update().set("tagline",original.tagline()),Movie.class); }
    }
    @Test void httpCatalogueAndOpenApiWork() throws Exception {
        var response = request("/api/v1/movies?q=Orbit","GET",null);
        assertEquals(200,response.statusCode()); assertTrue(response.body().contains("orbit-nine"));
        var docs = request("/v3/api-docs","GET",null);
        assertEquals(200,docs.statusCode()); assertTrue(docs.body().contains("/api/v1/showtimes/{id}/seats"));
    }
    @Test void httpValidationAndNotFoundAreProblemResponses() throws Exception {
        for (var path : new String[]{"/api/v1/movies?page=-1","/api/v1/movies?size=51","/api/v1/showtimes?date=not-a-date"}) {
            var response = request(path,"GET",null);
            assertEquals(400,response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/problem+json"));
        }
        assertEquals(404,request("/api/v1/movies/missing","GET",null).statusCode());
    }
    @Test void anonymousWritesAndUntrustedOriginsAreDenied() throws Exception {
        assertEquals(401,request("/api/v1/movies","POST",null).statusCode());
        assertEquals(403,request("/api/v1/movies","GET","https://untrusted.example").statusCode());
        var allowed = request("/api/v1/movies","GET","http://127.0.0.1:5173");
        assertEquals(200,allowed.statusCode());
        assertEquals("http://127.0.0.1:5173",allowed.headers().firstValue("access-control-allow-origin").orElse(""));
        assertEquals(401,request("/actuator/env","GET",null).statusCode());
    }

    private String key() { return UUID.randomUUID().toString(); }
    private UserAccount user(String role) { return auth.create("Integration tester",key()+"@example.test",key(),role); }
    private String showId() { return catalogue.showtimes("orbit-nine","Mumbai",LocalDate.now(CatalogueService.CINEMA_ZONE).plusDays(1)).getFirst().id(); }
    private String seat(String label) { return showId()+"-"+label.toLowerCase(); }
    private String seatStatus(String label) { return mongo.findById(seat(label),Document.class,"show_seats").getString("status"); }
    private HttpResponse<String> json(String path,String method,Document body,String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
        if(token!=null)builder.header("Authorization","Bearer "+token);
        return http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body.toJson())).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void registrationHashesPasswordCannotGrantAdminAndLoginChecksCredentials() throws Exception {
        String email=key()+"@example.test",password=key();
        var response=json("/api/v1/auth/register","POST",new Document("name","New customer").append("email",email).append("password",password).append("role","ADMIN"),null);
        assertEquals(201,response.statusCode(),response.body());var session=Document.parse(response.body());
        assertEquals("USER",session.get("user",Document.class).getString("role"));
        var account=mongo.findOne(Query.query(Criteria.where("email").is(email)),UserAccount.class);
        assertNotNull(account);assertNotEquals(password,account.passwordHash());assertTrue(account.passwordHash().startsWith("$2"));
        assertEquals(401,json("/api/v1/auth/login","POST",new Document("email",email).append("password",key()),null).statusCode());
        assertEquals(200,json("/api/v1/auth/login","POST",new Document("email",email).append("password",password),null).statusCode());
        assertEquals(403,json("/api/v1/admin/overview","GET",null,session.getString("accessToken")).statusCode());
        assertEquals(401,json("/api/v1/bookings","GET",null,"invalid-token").statusCode());
    }
    @Test void multiSeatBookingIsIdempotentOwnerScopedAndCancellable() {
        var owner=user("USER");var other=user("USER");String holdKey=key(),paymentKey=key();
        var hold=reservations.hold(owner.id(),showId(),List.of(seat("B2"),seat("B3")),holdKey);
        assertEquals(44000,hold.totalPaise());assertEquals(hold.id(),reservations.hold(owner.id(),showId(),List.of(seat("B3"),seat("B2")),holdKey).id());
        assertThrows(BusinessException.class,()->reservations.hold(owner.id(),showId(),List.of(seat("B4")),holdKey));
        assertEquals(404,assertThrows(BusinessException.class,()->reservations.getHold(other.id(),hold.id())).status().value());
        var result=reservations.pay(owner.id(),hold.id(),"SUCCESS",paymentKey);var booking=result.booking();
        try {
            assertEquals("BOOKED",seatStatus("B2"));assertEquals("BOOKED",seatStatus("B3"));
            assertEquals(booking.id(),reservations.pay(owner.id(),hold.id(),"SUCCESS",paymentKey).booking().id());
            assertThrows(BusinessException.class,()->reservations.pay(owner.id(),hold.id(),"DECLINED",paymentKey));
            assertEquals(404,assertThrows(BusinessException.class,()->reservations.booking(other.id(),booking.id())).status().value());
            assertEquals(404,assertThrows(BusinessException.class,()->reservations.cancel(other.id(),booking.id())).status().value());
            assertEquals(booking.id(),reservations.verifyTicket(booking.ticketCode()).id());
        } finally { reservations.cancel(owner.id(),booking.id()); }
        assertEquals("CANCELLED",reservations.cancel(owner.id(),booking.id()).status());assertEquals("AVAILABLE",seatStatus("B2"));
        assertThrows(BusinessException.class,()->reservations.verifyTicket(booking.ticketCode()));
        assertEquals(1,mongo.count(Query.query(Criteria.where("bookingId").is(booking.id()).and("status").is("REFUNDED")),"payments"));
    }
    @Test void conflictingMultiSeatClaimRollsBackEverySeat() {
        var first=user("USER");var second=user("USER");var hold=reservations.hold(first.id(),showId(),List.of(seat("B5")),key());
        try {
            assertEquals(409,assertThrows(BusinessException.class,()->reservations.hold(second.id(),showId(),List.of(seat("B5"),seat("B6")),key())).status().value());
            assertEquals("AVAILABLE",seatStatus("B6"));assertEquals("HELD",seatStatus("B5"));
        } finally {reservations.release(first.id(),hold.id());}
    }
    @Test void expiredHoldCannotConfirmAndReclaimDoesNotNeedCleanupWorker() {
        var first=user("USER");var second=user("USER");var old=reservations.hold(first.id(),showId(),List.of(seat("B7")),key());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(old.id())),new Update().set("expiresAt",java.util.Date.from(java.time.Instant.now().minusSeconds(5))),"seat_holds");
        mongo.updateFirst(Query.query(Criteria.where("_id").is(seat("B7"))),new Update().set("holdExpiresAt",java.util.Date.from(java.time.Instant.now().minusSeconds(5))),"show_seats");
        assertEquals(410,assertThrows(BusinessException.class,()->reservations.pay(first.id(),old.id(),"SUCCESS",key())).status().value());
        var current=reservations.hold(second.id(),showId(),List.of(seat("B7")),key());
        try {reservations.release(first.id(),old.id());assertEquals("HELD",seatStatus("B7"));assertEquals(second.id(),mongo.findById(seat("B7"),Document.class,"show_seats").getString("userId"));}
        finally {reservations.release(second.id(),current.id());}
    }
    @Test void declinedPaymentReleasesSeatsAndCannotCreateBooking() {
        var owner=user("USER");var hold=reservations.hold(owner.id(),showId(),List.of(seat("B8"),seat("B9")),key());String paymentKey=key();
        assertEquals("DECLINED",reservations.pay(owner.id(),hold.id(),"DECLINED",paymentKey).status());
        assertEquals("DECLINED",reservations.pay(owner.id(),hold.id(),"DECLINED",paymentKey).status());
        assertEquals("AVAILABLE",seatStatus("B8"));assertTrue(reservations.history(owner.id()).isEmpty());
        assertThrows(BusinessException.class,()->reservations.pay(owner.id(),hold.id(),"SUCCESS",key()));
    }
    @Test void twelveConcurrentCustomersProduceOneWinnerAndOneConfirmedOwner() throws Exception {
        var ready=new CountDownLatch(12);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(12)) {
            var futures=new ArrayList<Future<BookingService.HoldView>>();
            for(int i=0;i<12;i++) {String userId="race-"+key();futures.add(pool.submit(()->{ready.countDown();start.await();try{return reservations.hold(userId,showId(),List.of(seat("B10")),key());}catch(BusinessException conflict){assertEquals(409,conflict.status().value());return null;}}));}
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();var winners=new ArrayList<BookingService.HoldView>();
            for(var future:futures){var hold=future.get(30,TimeUnit.SECONDS);if(hold!=null)winners.add(hold);}
            assertEquals(1,winners.size());var hold=winners.getFirst();String owner=mongo.findById(hold.id(),Document.class,"seat_holds").getString("userId");
            var booking=reservations.pay(owner,hold.id(),"SUCCESS",key()).booking();
            try {assertEquals(1,mongo.count(Query.query(Criteria.where("seatIds").is(seat("B10")).and("status").is("CONFIRMED")),"bookings"));assertEquals(booking.id(),mongo.findById(seat("B10"),Document.class,"show_seats").getString("bookingId"));}
            finally {reservations.cancel(owner,booking.id());}
        }
    }
    @Test void invalidMovieFieldsHaveActionableErrorsWithoutExposingSubmittedValues() throws Exception {
        var admin=user("ADMIN");String token=context.getBean(TokenService.class).issue(admin),slug="validation-"+key();
        var valid=new Document("title","Validation screening").append("slug",slug).append("tagline","Test tagline").append("synopsis","A test of invalid movie fields.")
            .append("genres",List.of("Drama")).append("language","English").append("durationMinutes",100).append("certificate","U").append("releaseDate","2026-11-06").append("rating",0).append("posterUrl","/posters/orbit-nine.svg").append("featured",false);
        for(var invalid:List.of(new Document("field","slug").append("value","PRIVATE-INVALID-VALUE").append("label","URL slug"),
                new Document("field","releaseDate").append("value","PRIVATE-INVALID-VALUE").append("label","Release date"),
                new Document("field","durationMinutes").append("value","PRIVATE-INVALID-VALUE").append("label","Duration"),
                new Document("field","rating").append("value",11).append("label","Rating"),
                new Document("field","genres").append("value",List.of()).append("label","Genres"))) {
            var body=new Document(valid);body.put(invalid.getString("field"),invalid.get("value"));
            var response=json("/api/v1/admin/movies","POST",body,token);
            assertEquals(400,response.statusCode(),response.body());
            var problem=Document.parse(response.body());
            assertTrue(problem.getString("detail").contains(invalid.getString("label")),response.body());
            assertTrue(problem.get("fieldErrors",Document.class).containsKey(invalid.getString("field")),response.body());
            assertFalse(response.body().contains("PRIVATE-INVALID-VALUE"));
            assertNull(mongo.findById(slug,Movie.class));
        }
        assertEquals(201,json("/api/v1/admin/movies","POST",valid,token).statusCode());
        mongo.remove(Query.query(Criteria.where("_id").is(slug)),Movie.class);
    }
    @Test void administratorCanCreateMovieCinemaAndShowAndOverlapIsRejected() throws Exception {
        var admin=user("ADMIN");String token=context.getBean(TokenService.class).issue(admin),slug="test-"+key();
        var movie=new Document("title","Test premiere").append("slug",slug).append("tagline","Integration screening").append("synopsis","A fictional film used to verify administrative publication.")
            .append("genres",List.of("Drama")).append("language","English").append("durationMinutes",100).append("certificate","U").append("releaseDate","2026-11-06").append("rating",0).append("posterUrl","/posters/orbit-nine.svg").append("featured",false);
        assertEquals(201,json("/api/v1/admin/movies","POST",movie,token).statusCode());
        movie.put("title","Edited premiere");assertEquals(200,json("/api/v1/admin/movies/"+slug,"PUT",movie,token).statusCode());
        var cinemaResponse=json("/api/v1/admin/theatres","POST",new Document("name","Integration Cinema").append("city","Test City").append("address","Test address").append("amenities",List.of("Wheelchair access")).append("screenName","Screen 1").append("format","LASER 2D"),token);
        assertEquals(201,cinemaResponse.statusCode(),cinemaResponse.body());String cinemaId=Document.parse(cinemaResponse.body()).getString("id");
        var screen=mongo.findOne(Query.query(Criteria.where("theatreId").is(cinemaId)),com.cinenext.catalogue.model.Screen.class);
        var show=new Document("movieId",slug).append("screenId",screen.id()).append("startsAt",java.time.Instant.now().plusSeconds(172800).toString()).append("priceFromPaise",25000);
        var created=json("/api/v1/admin/showtimes","POST",show,token);assertEquals(201,created.statusCode(),created.body());
        assertEquals(409,json("/api/v1/admin/showtimes","POST",show,token).statusCode());
        assertEquals(96,catalogue.seatMap(Document.parse(created.body()).getString("id")).rows().stream().mapToInt(row->row.seats().size()).sum());
        assertEquals(200,json("/api/v1/admin/overview","GET",null,token).statusCode());
    }
}
