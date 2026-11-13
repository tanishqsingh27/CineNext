package com.cinenext.catalogue.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("seats")
@CompoundIndex(name = "screen_seat_unique", def = "{'screenId':1,'label':1}", unique = true)
public record Seat(@Id String id, String screenId, String label, String row, int number, String category, boolean accessible) {}
