package com.townwizard.globaldata.service.provider;

import java.util.List;

import com.townwizard.globaldata.model.directory.Place;

/**
 * Contains methods to retrieve data from Yellow Pages
 */
public interface YellowPagesService {

    /**
     * Get Yellow Pages places by search term, zip, and distance
     */
    List<Place> getPlaces(String zip, double distanceInMiles, String term);
    
}
