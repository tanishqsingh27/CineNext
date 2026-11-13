package com.cinenext.catalogue.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("show_seats")
@CompoundIndex(name = "show_seat_unique", def = "{'showtimeId':1,'seatId':1}", unique = true)
public record ShowSeat(@Id String id, String showtimeId, String seatId, String label, String row,
                       int number, String category, long pricePaise, String status, boolean accessible, java.time.Instant holdExpiresAt) {
    public ShowSeat(String id,String showtimeId,String seatId,String label,String row,int number,String category,long pricePaise,String status,boolean accessible) {
        this(id,showtimeId,seatId,label,row,number,category,pricePaise,status,accessible,null);
    }
}
