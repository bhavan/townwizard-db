package com.townwizard.globaldata.service;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.townwizard.db.constants.Constants;
import com.townwizard.db.logger.Log;
import com.townwizard.db.util.DateUtils;
import com.townwizard.globaldata.dao.LocationDao;
import com.townwizard.globaldata.model.DistanceComparator;
import com.townwizard.globaldata.model.Event;
import com.townwizard.globaldata.model.Location;
import com.townwizard.globaldata.model.LocationIngest;

@Component("globalDataService")
public class GlobalDataServiceImpl implements GlobalDataService {
    
    @Autowired
    private FacebookService facebookService;
    @Autowired
    private GoogleService googleService;
    @Autowired
    private YellowPagesService yellowPagesService;
    @Autowired
    private LocationService locationService;
    @Autowired
    private LocationDao locationDao;
    
    private ExecutorService executors = Executors.newFixedThreadPool(60);
    
    @Override
    public List<Event> getEvents(String zip, String countryCode) {
        Location origin = locationService.getPrimaryLocation(zip, countryCode);
        return getEvents(zip, countryCode, origin);
    }

    @Override
    public List<Event> getEvents(double latitude, double longitude) {
        Location origin = locationService.getLocation(latitude, longitude);
        return getEvents(origin.getZip(), origin.getCountryCode(), origin);
    }    

    @Override
    @Transactional
    public List<Location> getLocations(String zip, String countryCode, int distanceInMeters) {
        Location origin = locationService.getPrimaryLocation(zip, countryCode);
        return getLocations(zip, countryCode, distanceInMeters, origin);        
    }
    
    @Override
    @Transactional
    public List<Location> getLocations(double latitude, double longitude, int distanceInMeters) {
        Location orig = locationService.getLocation(latitude, longitude);
        return getLocations(orig.getZip(), orig.getCountryCode(), distanceInMeters, orig);
    }
    
    private List<Event> getEvents(String zip, String countryCode, Location origin) {
        List<String> terms = locationService.getCities(zip, countryCode);
        List<Event> events = facebookService.getEvents(terms);
        if(origin != null) {
            populateEventDistances(origin, countryCode, events);
            Collections.sort(events, new DistanceComparator());
        }
        return events;        
    }
    
    private List<Location> getLocations(String zip, String countryCode, int distanceInMeters, Location origin) {
        List<Location> locations = null;
        LocationIngest ingest = locationDao.getLocationIngest(zip, countryCode);
        if(!locationIngestRequired(ingest, distanceInMeters)) {
            Set<Location> ingestLocations = ingest.getLocations();
            locations = new ArrayList<>(ingestLocations.size());
            locations.addAll(ingestLocations);
        } else {
            locations = getLocationsFromSource(zip, countryCode, distanceInMeters);
            if(!locations.isEmpty()) {
                LocationIngest updatedIngest = 
                        createOrUpdateLocationIngest(ingest, zip, countryCode, distanceInMeters);
                locationDao.saveLocations(locations, updatedIngest);
            }
        }
        
        for(Location l : locations) {
            l.setDistance(locationService.distance(origin, l));
        }        
        Collections.sort(locations, new DistanceComparator());        
        
        return locations;
    }
    
    private boolean locationIngestRequired(LocationIngest ingest, int distanceInMeters) {
        return ingest == null ||
               ingest.getDistance() < distanceInMeters ||
               DateUtils.addDays(ingest.getUpdated(), Constants.REFRESH_LOCATIONS_PERIOD_IN_DAYS).before(new Date());  
    }
    
    private LocationIngest createOrUpdateLocationIngest(
            LocationIngest ingest, String zip, String countryCode, int distanceInMeters) {
        if(ingest != null) {
            ingest.setDistance(distanceInMeters);
            locationDao.update(ingest);
            return ingest;
        }
        
        LocationIngest newIngest = new LocationIngest();
        newIngest.setZip(zip);
        newIngest.setCountryCode(countryCode);
        newIngest.setDistance(distanceInMeters);
        locationDao.create(newIngest);
        return newIngest;
    }
    
    private List<Location> getLocationsFromSource(
            final String zip, String countryCode, int distanceInMeters) {
        
        if(Log.isInfoEnabled()) Log.info("Getting locations from source for zip: " + zip);
        
        List<String> terms = getSearchTerms(zip, countryCode, distanceInMeters);
        List<Future<List<Location>>> results = new ArrayList<>(terms.size());
        
        final double distanceInMiles = distanceInMeters / Constants.METERS_IN_MILE;
        
        for(final String term : terms) {
            Callable<List<Location>> worker = new Callable<List<Location>>() {
                @Override
                public List<Location> call() throws Exception {
                    return yellowPagesService.getLocations(term, zip, distanceInMiles);                    
                }                
            };            
            results.add(executors.submit(worker));
        }
        
        List<Location> finalList = new ArrayList<>();
        
        long start = System.currentTimeMillis();
        try {
            for(Future<List<Location>> r : results) {
                List<Location> ypLocationsForName = r.get();
                finalList.addAll(ypLocationsForName);
            }
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
        long end = System.currentTimeMillis();
        if(Log.isInfoEnabled()) Log.info(
                "Executed " + terms.size() + " requests and brought: " + 
                        finalList.size()  + " locations in " + (end - start) + " ms");        
        
        for(Location l : finalList) {
            l.setCountryCode(countryCode);
        }

        return finalList;        
    }
    
    private List<String> getSearchTerms(String zip, String countryCode, int distanceInMeters) {
        Location origin = locationService.getPrimaryLocation(zip, countryCode);
        if(origin != null) {
            List<Location> googleLocations = googleService.getLocations(
                    origin.getLatitude().doubleValue(), origin.getLongitude().doubleValue(), distanceInMeters);            
            List<String> terms = new ArrayList<>(googleLocations.size());
            for(final Location gLocation : googleLocations) {
                terms.add(gLocation.getName());
            }        
            return terms;            
        }
        return Collections.emptyList();
    }
    
    private void populateEventDistances(Location origLocation, String countryCode, List<Event> events) {
        for(Event e : events) {
            Double eLat = e.getLatitude();
            Double eLon = e.getLongitude();
            String eZip = e.getZip();
            Location eventLocation = null;
            if(eLat != null && eLon != null) {
                eventLocation = new Location();
                eventLocation.setLatitude(eLat.floatValue());
                eventLocation.setLongitude(eLon.floatValue());
            } else if (eZip != null) {
                eventLocation = locationService.getPrimaryLocation(eZip, countryCode);
            }
            if(eventLocation != null) {
                e.setDistance(locationService.distance(origLocation, eventLocation));
            }            
        }
    }
}
