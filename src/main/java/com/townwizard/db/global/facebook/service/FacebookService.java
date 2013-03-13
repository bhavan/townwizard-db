package com.townwizard.db.global.facebook.service;

import java.util.List;

import com.townwizard.db.global.model.Event;
import com.townwizard.db.global.model.Location;

public interface FacebookService {

    List<Event> getEvents(String searchText);
    List<Event> getEvents(String zip, String countryCode, Integer distanceInMeters);
    List<Location> getLocations(String zip, String countryCode, Integer distanceInMeters);
    
}
