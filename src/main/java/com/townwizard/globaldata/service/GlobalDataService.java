package com.townwizard.globaldata.service;

import java.util.List;

import com.townwizard.globaldata.model.Event;
import com.townwizard.globaldata.model.Location;
import com.townwizard.globaldata.model.directory.Place;

/**
 * Service which contains method global data retrieval.
 * This service talks to different specific provides such as FB, Google, etc,
 * and gets the data from them.  
 * 
 * If particular data comes from different sources, the final list will contain the merged data
 */
public interface GlobalDataService {

    /**
     * Get events by either zip info, or location, or client IP
     */
    List<Event> getEvents(Location location);
    
    /**
     * Get places by either zip info, or location, or client IP, and also by
     * distance, optional main category, and optional comma-separated list of categories.
     * If main category and/or categories is null places of all categories are retrieved
     */    
    List<Place> getPlaces(Location location,
            int distanceInMeters, String mainCategory, String categories);
    
    /**
     * Get sorted place categories (such as restaurants, dental, pizza, etc) from places
     * retrieved by zip/location/ip and distance for some optional main category.
     * If main category is null place categories for all places are retrieved
     */
    List<String> getPlaceCategories(Location location, 
            int distanceInMeters, String mainCategory);
    
    /**
     * Get zip code by either location (latitude and longitude) or IP
     * @param params The location params object which should have ether location or IP parts populated
     */
    String getZipCode(Location location);

}