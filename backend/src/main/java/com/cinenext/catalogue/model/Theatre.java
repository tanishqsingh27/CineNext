package com.cinenext.catalogue.model;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("theatres")
public record Theatre(@Id String id, String name, @Indexed String city, String address, List<String> amenities) {}
