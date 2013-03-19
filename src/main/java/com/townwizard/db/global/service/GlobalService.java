package com.townwizard.db.global.service;

import java.util.List;

import com.townwizard.db.global.model.Location;

public interface GlobalService {
    
    List<Location> getLocations(String zip, int distanceInMeters);
}
