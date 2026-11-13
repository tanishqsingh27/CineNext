package com.cinenext.catalogue.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("screens")
@CompoundIndex(name = "theatre_screen_unique", def = "{'theatreId':1,'name':1}", unique = true)
public record Screen(@Id String id, String theatreId, String name, String format, int totalSeats) {}
