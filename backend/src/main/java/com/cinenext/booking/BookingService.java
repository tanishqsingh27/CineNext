package com.cinenext.booking;

import com.cinenext.catalogue.model.*;
import com.cinenext.common.BusinessException;
import com.mongodb.MongoException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingService {
    private final MongoTemplate mongo; private final TransactionTemplate transactions; private final Clock clock;
    public BookingService(MongoTemplate mongo,TransactionTemplate transactions,Clock clock) { this.mongo=mongo;this.transactions=transactions;this.clock=clock; }
    public record HoldView(String id,String showtimeId,List<String> seatIds,List<String> seatLabels,long totalPaise,String currency,Instant expiresAt,String status,String movieTitle,String theatreName,String screenName,Instant startsAt) {}
    public record BookingView(String id,String showtimeId,String movieTitle,String theatreName,String screenName,Instant startsAt,List<String> seatLabels,long totalPaise,String currency,String status,String ticketCode,Instant createdAt,boolean canCancel) {}
    public record PaymentView(String status,BookingView booking,String message) {}
    private BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT,message); }
    private static Instant instant(Document doc,String key) { return doc.getDate(key).toInstant(); }
    private static long amount(Document doc) { return ((Number)doc.get("totalPaise")).longValue(); }
    private static List<String> strings(Document doc,String key) { return doc.getList(key,String.class); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void key(String value) { if(value==null||!value.matches("[A-Za-z0-9_-]{8,100}")) throw new BusinessException(HttpStatus.BAD_REQUEST,"Provide a valid Idempotency-Key (8–100 letters, numbers, hyphens or underscores)."); }
    public <T> T transaction(Supplier<T> work) {
        for(int attempt=0;attempt<5;attempt++) {
            try { return transactions.execute(status->work.get()); }
            catch(RuntimeException error) {
                boolean retry=error instanceof DuplicateKeyException;
                for(Throwable cause=error;cause!=null;cause=cause.getCause())
                    if(cause instanceof MongoException failure && (failure.hasErrorLabel("TransientTransactionError")||failure.hasErrorLabel("UnknownTransactionCommitResult")||failure.getCode()==112)) retry=true;
                if(!retry||attempt==4) throw error;
                try { Thread.sleep(20L*(attempt+1)); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt();throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"Please retry your request."); }
            }
        }
        throw new IllegalStateException("Unreachable transaction state");
    }
    public HoldView hold(String user,String showId,List<String> requested,String idempotencyKey) {
        key(idempotencyKey);
        if(requested==null||requested.isEmpty()||requested.size()>8||new HashSet<>(requested).size()!=requested.size()) throw new BusinessException(HttpStatus.BAD_REQUEST,"Choose between one and eight distinct seats.");
        var ids=requested.stream().sorted().toList(); String fingerprint=hash(showId+"|"+String.join(",",ids)); String holdId=hash(user+"|hold|"+idempotencyKey);
        return transaction(()-> {
            var existing=mongo.findById(holdId,Document.class,"seat_holds");
            if(existing!=null) {
                if(!fingerprint.equals(existing.getString("requestHash"))) throw conflict("This request key was already used for different seats.");
                requireActive(existing);return holdView(existing);
            }
            var show=mongo.findById(showId,Showtime.class);
            if(show==null) throw new BusinessException(HttpStatus.NOT_FOUND,"Showtime not found.");
            Instant now=clock.instant();
            if(!show.startsAt().isAfter(now.plusSeconds(60))) throw conflict("Ticket sales for this show have closed.");
            Instant deadline=now.plusSeconds(600); if(deadline.isAfter(show.startsAt().minusSeconds(30))) deadline=show.startsAt().minusSeconds(30);
            var seats=mongo.find(Query.query(Criteria.where("_id").in(ids).and("showtimeId").is(showId)),ShowSeat.class);
            if(seats.size()!=ids.size()) throw new BusinessException(HttpStatus.BAD_REQUEST,"Some seats do not belong to this showtime.");
            var available=new Criteria().orOperator(Criteria.where("status").is("AVAILABLE"),Criteria.where("status").is("HELD").and("holdExpiresAt").lte(Date.from(now)));
            var claim=Query.query(new Criteria().andOperator(Criteria.where("_id").in(ids).and("showtimeId").is(showId),available));
            var update=new Update().set("status","HELD").set("holdId",holdId).set("userId",user).set("holdExpiresAt",Date.from(deadline)).unset("bookingId");
            if(mongo.updateMulti(claim,update,"show_seats").getModifiedCount()!=ids.size()) throw conflict("One or more seats were just taken. Refresh the seat map and choose again.");
            var movie=mongo.findById(show.movieId(),Movie.class);
            var labels=seats.stream().sorted(Comparator.comparing(ShowSeat::row).thenComparingInt(ShowSeat::number)).map(ShowSeat::label).toList();
            var hold=new Document("_id",holdId).append("userId",user).append("showtimeId",showId).append("seatIds",ids).append("seatLabels",labels)
                .append("totalPaise",seats.stream().mapToLong(ShowSeat::pricePaise).sum()).append("currency","INR").append("expiresAt",Date.from(deadline))
                .append("status","ACTIVE").append("requestHash",fingerprint).append("createdAt",Date.from(now))
                .append("movieTitle",movie==null?"Movie":movie.title()).append("theatreName",show.theatreName()).append("screenName",show.screenName()).append("startsAt",Date.from(show.startsAt()));
            mongo.insert(hold,"seat_holds"); audit(user,"SEATS_HELD",holdId);return holdView(hold);
        });
    }
    private Document owned(String collection,String id,String user) {
        var value=mongo.findOne(Query.query(Criteria.where("_id").is(id).and("userId").is(user)),Document.class,collection);
        if(value==null) throw new BusinessException(HttpStatus.NOT_FOUND,"Record not found."); return value;
    }
    private void requireActive(Document hold) {
        if(!instant(hold,"expiresAt").isAfter(clock.instant())) throw new BusinessException(HttpStatus.GONE,"Your seat hold has expired. Please choose your seats again.");
        if(!"ACTIVE".equals(hold.getString("status"))) throw conflict("This hold is no longer active.");
    }
    public HoldView getHold(String user,String id) { var hold=owned("seat_holds",id,user);requireActive(hold);return holdView(hold); }
    public void release(String user,String id) {
        transaction(()->{var hold=owned("seat_holds",id,user);if("ACTIVE".equals(hold.getString("status"))) { releaseSeats(id); mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("status","RELEASED"),"seat_holds"); } return null;});
    }
    private void releaseSeats(String holdId) { mongo.updateMulti(Query.query(Criteria.where("holdId").is(holdId).and("status").is("HELD")),new Update().set("status","AVAILABLE").unset("holdId").unset("holdExpiresAt").unset("userId"),"show_seats"); }
    public PaymentView pay(String user,String holdId,String outcome,String idempotencyKey) {
        key(idempotencyKey); if(!List.of("SUCCESS","DECLINED").contains(outcome)) throw new BusinessException(HttpStatus.BAD_REQUEST,"Choose a supported simulated payment outcome.");
        String paymentId=hash(user+"|payment|"+idempotencyKey); String fingerprint=hash(holdId+"|"+outcome);
        return transaction(()-> {
            var previous=mongo.findById(paymentId,Document.class,"payments");
            if(previous!=null) {
                if(!fingerprint.equals(previous.getString("requestHash"))) throw conflict("This payment key was already used for a different request.");
                return new PaymentView(previous.getString("status"),previous.getString("bookingId")==null?null:bookingView(owned("bookings",previous.getString("bookingId"),user)),"Previous payment result restored.");
            }
            var hold=owned("seat_holds",holdId,user); requireActive(hold);
            var payment=new Document("_id",paymentId).append("holdId",holdId).append("userId",user).append("requestHash",fingerprint).append("totalPaise",amount(hold)).append("createdAt",Date.from(clock.instant()));
            if(outcome.equals("DECLINED")) {
                releaseSeats(holdId);mongo.updateFirst(Query.query(Criteria.where("_id").is(holdId)),new Update().set("status","PAYMENT_FAILED"),"seat_holds");
                mongo.insert(payment.append("status","DECLINED"),"payments");audit(user,"PAYMENT_DECLINED",holdId);
                return new PaymentView("DECLINED",null,"Simulated payment declined. Your seats have been released; no money was charged.");
            }
            String bookingId=UUID.randomUUID().toString();
            var seatQuery=Query.query(Criteria.where("_id").in(strings(hold,"seatIds")).and("holdId").is(holdId).and("userId").is(user).and("status").is("HELD").and("holdExpiresAt").gt(Date.from(clock.instant())));
            long count=mongo.updateMulti(seatQuery,new Update().set("status","BOOKED").set("bookingId",bookingId).unset("holdId").unset("holdExpiresAt"),"show_seats").getModifiedCount();
            if(count!=strings(hold,"seatIds").size()) throw conflict("The hold is no longer valid. No payment was completed.");
            var booking=new Document("_id",bookingId).append("userId",user).append("holdId",holdId).append("showtimeId",hold.getString("showtimeId"))
                .append("movieTitle",hold.getString("movieTitle")).append("theatreName",hold.getString("theatreName")).append("screenName",hold.getString("screenName"))
                .append("startsAt",hold.getDate("startsAt")).append("seatIds",strings(hold,"seatIds")).append("seatLabels",strings(hold,"seatLabels"))
                .append("totalPaise",amount(hold)).append("currency","INR").append("status","CONFIRMED").append("ticketCode",UUID.randomUUID().toString()).append("createdAt",Date.from(clock.instant()));
            mongo.insert(booking,"bookings");mongo.insert(payment.append("status","SUCCESS").append("bookingId",bookingId),"payments");
            mongo.updateFirst(Query.query(Criteria.where("_id").is(holdId)),new Update().set("status","CONFIRMED").set("bookingId",bookingId),"seat_holds");
            audit(user,"BOOKING_CONFIRMED",bookingId);return new PaymentView("SUCCESS",bookingView(booking),"Booking confirmed. This was a simulated payment.");
        });
    }
    public List<BookingView> history(String user) { return mongo.find(Query.query(Criteria.where("userId").is(user)).with(Sort.by(Sort.Direction.DESC,"createdAt")).limit(100),Document.class,"bookings").stream().map(this::bookingView).toList(); }
    public BookingView booking(String user,String id) { return bookingView(owned("bookings",id,user)); }
    public BookingView cancel(String user,String id) {
        return transaction(()->{var booking=owned("bookings",id,user);
            if("CANCELLED".equals(booking.getString("status"))) return bookingView(booking);
            if(!instant(booking,"startsAt").isAfter(clock.instant().plusSeconds(1800))) throw conflict("Cancellations close 30 minutes before the show.");
            long released=mongo.updateMulti(Query.query(Criteria.where("bookingId").is(id).and("status").is("BOOKED")),new Update().set("status","AVAILABLE").unset("bookingId").unset("userId"),"show_seats").getModifiedCount();
            if(released!=strings(booking,"seatIds").size()) throw conflict("Seat ownership changed. Contact the cinema before cancelling.");
            mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("status","CANCELLED").set("cancelledAt",Date.from(clock.instant())),"bookings");
            mongo.insert(new Document("_id","refund-"+id).append("userId",user).append("bookingId",id).append("status","REFUNDED").append("totalPaise",amount(booking)).append("createdAt",Date.from(clock.instant())),"payments");
            booking.put("status","CANCELLED");audit(user,"BOOKING_CANCELLED",id);return bookingView(booking);
        });
    }
    public BookingView verifyTicket(String ticket) {
        var booking=mongo.findOne(Query.query(Criteria.where("ticketCode").is(ticket)),Document.class,"bookings");
        if(booking==null||!"CONFIRMED".equals(booking.getString("status"))) throw new BusinessException(HttpStatus.NOT_FOUND,"This ticket is invalid or cancelled.");
        if(instant(booking,"startsAt").plusSeconds(4*3600).isBefore(clock.instant())) throw conflict("This show has ended.");
        return bookingView(booking);
    }
    public void audit(String user,String action,String id) { mongo.insert(new Document("_id",UUID.randomUUID().toString()).append("actorId",user).append("action",action).append("resourceId",id).append("occurredAt",Date.from(clock.instant())),"audit_logs"); }
    private HoldView holdView(Document h) { return new HoldView(h.getString("_id"),h.getString("showtimeId"),strings(h,"seatIds"),strings(h,"seatLabels"),amount(h),h.getString("currency"),instant(h,"expiresAt"),h.getString("status"),h.getString("movieTitle"),h.getString("theatreName"),h.getString("screenName"),instant(h,"startsAt")); }
    private BookingView bookingView(Document b) { boolean confirmed="CONFIRMED".equals(b.getString("status")); return new BookingView(b.getString("_id"),b.getString("showtimeId"),b.getString("movieTitle"),b.getString("theatreName"),b.getString("screenName"),instant(b,"startsAt"),strings(b,"seatLabels"),amount(b),b.getString("currency"),b.getString("status"),confirmed?b.getString("ticketCode"):null,instant(b,"createdAt"),confirmed&&instant(b,"startsAt").isAfter(clock.instant().plusSeconds(1800))); }
}
