package com.townwizard.globaldata.service;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.townwizard.db.constants.Constants;
import com.townwizard.db.logger.Log;
import com.townwizard.db.util.CollectionUtils;
import com.townwizard.db.util.DateUtils;
import com.townwizard.db.util.StringUtils;
import com.townwizard.globaldata.dao.GlobalDataDao;
import com.townwizard.globaldata.dao.PlaceDao;
import com.townwizard.globaldata.model.CityLocation;
import com.townwizard.globaldata.model.DistanceComparator;
import com.townwizard.globaldata.model.Event;
import com.townwizard.globaldata.model.Location;
import com.townwizard.globaldata.model.directory.Place;
import com.townwizard.globaldata.model.directory.PlaceCategory;
import com.townwizard.globaldata.model.directory.PlaceIngest;
import com.townwizard.globaldata.service.provider.FacebookService;
import com.townwizard.globaldata.service.provider.GoogleService;
import com.townwizard.globaldata.service.provider.YellowPagesService;

/**
 * GlobalDataService implementation
 */
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
    private PlaceDao placeDao;
    @Autowired
    private GlobalDataDao globalDataDao;
    
    private ExecutorService executors = Executors.newFixedThreadPool(60);

    /**
     * Currently events are retrieved from Facebook 
     */
    @Override
    public List<Event> getEvents(Location params) {
        if(params.isZipInfoSet()) {
            return getEventsByZipInfo(params.getZip(), params.getCountryCode());
        } else if(params.isLocationSet()) {
            return getEventsByLocation(params.getLatitude(), params.getLongitude());
        } else if(params.isIpSet()) {
            return getEventsByIp(params.getIp());
        }
        return Collections.emptyList();
    }
    
    /**
     * Having location params and distance, the local DB first is checked.  If no place ingest
     * exist for the params and distance, or the place ingest is expired, then places are
     * brought from the source, otherwise local DB is used
     * 
     * Currently the logic of retrieving a place from the source is this:
     * 1) Go to Google and get placess by location parameters.
     * 2) Collect place names
     * 3) Retrieve places from Yellow Pages using collected names as search terms
     * 
     * Retrieval of places from Yellow Pages is done in separate threads. 
     */
    @Override
    @Transactional("directoryTransactionManager")
    public List<Place> getPlaces(
            Location params, int distanceInMeters, String mainCategory, String categories) {
        if(params.isZipInfoSet()) {
            return getPlacesByZipInfo(params.getZip(), params.getCountryCode(),
                    mainCategory, categories, distanceInMeters);
        } else if(params.isLocationSet()) {
            return getPlacesByLocation(params.getLatitude(), params.getLongitude(),
                    mainCategory, categories, distanceInMeters);
        } else if(params.isIpSet()) {
            return getPlacesByIp(params.getIp(),
                    mainCategory, categories, distanceInMeters);
        }
        return Collections.emptyList();        
    }
    
    /**
     * Get places as in the method above, and collect categories from them.
     */
    @Override
    @Transactional("directoryTransactionManager")
    public List<String> getPlaceCategories(Location params, 
            int distanceInMeters, String mainCategory) {
        if(params.isZipInfoSet()) {
            return getLocationCategoriesByZipInfo(params.getZip(), params.getCountryCode(),
                    mainCategory, distanceInMeters);
        } else if(params.isLocationSet()) {
            return getLocationCategoriesByLocation(params.getLatitude(), params.getLongitude(),
                    mainCategory, distanceInMeters);
        } else if(params.isIpSet()) {
            return getLocationCategoriesByIp(params.getIp(),
                    mainCategory, distanceInMeters);
        }
        return Collections.emptyList();
    }
    
    @Override
    public String getZipCode(Location params) {
        if(params.isZipInfoSet()) {
            return params.getZip();
        } else if(params.isLocationSet()) {
            Location orig = locationService.getLocation(params.getLatitude(), params.getLongitude());
            return orig.getZip();
        } else if(params.isIpSet()) {
            CityLocation cityLocation = globalDataDao.getCityLocationByIp(params.getIp());
            return cityLocation.getPostalCode();
        }
        return null;
    }

    
    ///////////// Places private methods /////////////////////////////
    
    private List<Place> getPlacesByZipInfo(String zip, String countryCode,
            String mainCategory, String categories, int distanceInMeters) {
        Location origin = locationService.getPrimaryLocation(zip, countryCode);
        return getPlaces(zip, countryCode, mainCategory, categories, distanceInMeters, origin);        
    }

    private List<Place> getPlacesByLocation(double latitude, double longitude,
            String mainCategory, String categories, int distanceInMeters) {
        Location orig = locationService.getLocation(latitude, longitude);
        return getPlaces(orig.getZip(), orig.getCountryCode(),
                mainCategory, categories, distanceInMeters, orig);
    }
    
    private List<Place> getPlacesByIp(
            String ip, String mainCategory, String categories, int distanceInMeters) {
        CityLocation cityLocation = globalDataDao.getCityLocationByIp(ip);
        if(cityLocation != null) {
            if(cityLocation.hasPostalCodeAndCountry()) {
                return getPlacesByZipInfo(cityLocation.getPostalCode(), cityLocation.getCountryCode(),
                        mainCategory, categories, distanceInMeters);
            }
            if(cityLocation.hasLocation()) {
                return getPlacesByLocation(cityLocation.getLatitude(), cityLocation.getLongitude(),
                        mainCategory, categories, distanceInMeters);
            }             
        }
        return Collections.emptyList();
    }
    
    private List<String> getLocationCategoriesByZipInfo(String zip, String countryCode,
            String mainCategory, int distanceInMeters) {        
        return getLocationCategories(zip, countryCode, mainCategory, distanceInMeters);        
    }

    private List<String> getLocationCategoriesByLocation(double latitude, double longitude,
            String mainCategory, int distanceInMeters) {
        Location orig = locationService.getLocation(latitude, longitude);
        return getLocationCategories(orig.getZip(), orig.getCountryCode(), mainCategory, distanceInMeters);
    }
    
    private List<String> getLocationCategoriesByIp(
            String ip, String mainCategory, int distanceInMeters) {
        CityLocation cityLocation = globalDataDao.getCityLocationByIp(ip);
        if(cityLocation != null) {
            if(cityLocation.hasPostalCodeAndCountry()) {
                return getLocationCategoriesByZipInfo(cityLocation.getPostalCode(), cityLocation.getCountryCode(),
                        mainCategory, distanceInMeters);
            }
            if(cityLocation.hasLocation()) {
                return getLocationCategoriesByLocation(cityLocation.getLatitude(), cityLocation.getLongitude(),
                        mainCategory, distanceInMeters);
            }             
        }
        return Collections.emptyList();
    }
    
    //main location categories retrieval method
    private List<String> getLocationCategories(
            String zip, String countryCode, String mainCategory, int distanceInMeters) {
        PlaceIngest ingest = placeDao.getPlaceIngest(zip, countryCode);
        if(locationIngestRequired(ingest, distanceInMeters)) {
            List<Place> places = getPlacesFromSource(zip, countryCode, distanceInMeters);
            if(!places.isEmpty()) {
                PlaceIngest updatedIngest = 
                        createOrUpdateLocationIngest(ingest, zip, countryCode, distanceInMeters);
                placeDao.savePlaces(places, updatedIngest);
                ingest = updatedIngest;
            }
        }
        
        if(ingest != null) {
            List<String> categories = placeDao.getPlaceCategories(ingest.getId());
            if(mainCategory != null && !mainCategory.isEmpty()) {
                if(Constants.RESTAURANTS.equals(mainCategory)) {
                    categories = filterLocationCategories(categories, getRestaurantsCategories(), false);
                } else if(Constants.DIRECTORY.equals(mainCategory)){
                    categories = filterLocationCategories(categories, getRestaurantsCategories(), true);
                }
            }
            return categories;
        }
        return Collections.emptyList();
    }
    
    
    //main location retrieval method
    //it tries to get locations from the local DB first,
    //and if no ingest exists or ingest expired, gets the locations from the source
    private List<Place> getPlaces(
            String zip, String countryCode,
            String mainCategory, String categories, 
            int distanceInMeters, Location origin) {
        
        List<Place> places = null;
        PlaceIngest ingest = placeDao.getPlaceIngest(zip, countryCode);
        if(!locationIngestRequired(ingest, distanceInMeters)) {
            Set<Place> ingestPlaces = ingest.getPlaces();
            places = new ArrayList<>(ingestPlaces.size());
            places.addAll(ingestPlaces);
        } else {
            places = getPlacesFromSource(zip, countryCode, distanceInMeters);
            if(!places.isEmpty()) {
                PlaceIngest updatedIngest = 
                        createOrUpdateLocationIngest(ingest, zip, countryCode, distanceInMeters);
                placeDao.savePlaces(places, updatedIngest);
            }
        }
        
        for(Place p : places) {
            Location l = new Location(p.getLatitude(), p.getLongitude());
            p.setDistance(locationService.distance(origin, l));
        }
        
        places = filterPlacesByDistance(places, distanceInMeters);   
        places = filterPlacesByCategories(places, categories, false);
        
        
        if(mainCategory != null && !mainCategory.isEmpty()) {
            if(Constants.RESTAURANTS.equals(mainCategory)) {
                places = filterPlacesByCategories(places, getRestaurantsCategories(), false);
            } else if(Constants.DIRECTORY.equals(mainCategory)){
                places = filterPlacesByCategories(places, getRestaurantsCategories(), true);
            }
        }
        
        Collections.sort(places, new DistanceComparator());
        return places;
    }
    
    private boolean locationIngestRequired(PlaceIngest ingest, int distanceInMeters) {
        return ingest == null ||
               ingest.getDistance() < distanceInMeters ||
               DateUtils.addDays(ingest.getUpdated(), Constants.REFRESH_LOCATIONS_PERIOD_IN_DAYS).before(new Date());  
    }
    
    private PlaceIngest createOrUpdateLocationIngest(
            PlaceIngest ingest, String zip, String countryCode, int distanceInMeters) {
        if(ingest != null) {
            ingest.setDistance(distanceInMeters);
            placeDao.update(ingest);
            return ingest;
        }
        
        PlaceIngest newIngest = new PlaceIngest();
        newIngest.setZip(zip);
        newIngest.setCountryCode(countryCode);
        newIngest.setDistance(distanceInMeters);
        placeDao.create(newIngest);
        return newIngest;
    }
    
    // get location search terms from Google
    // get locations by terms from Yellow Pages
    private List<Place> getPlacesFromSource(
            final String zip, String countryCode, int distanceInMeters) {
        
        if(Log.isInfoEnabled()) Log.info("Getting locations from source for zip: " + zip);
        
        List<String> terms = getSearchTerms(zip, countryCode, distanceInMeters);
        //List<String> terms = getSearchTerms();
        List<Future<List<Place>>> results = new ArrayList<>(terms.size());
        
        final double distanceInMiles = distanceInMeters / Constants.METERS_IN_MILE;
        
        for(final String term : terms) {
            Callable<List<Place>> worker = new Callable<List<Place>>() {
                @Override
                public List<Place> call() throws Exception {
                    return yellowPagesService.getPlaces(term, zip, distanceInMiles);                    
                }                
            };            
            results.add(executors.submit(worker));
        }
        
        SortedSet<Place> places = new TreeSet<>(new Comparator<Place>() {
            @Override
            public int compare(Place l1, Place l2) {
                int result = l1.getExternalId().compareTo(l2.getExternalId());
                if(result == 0) result = l1.getSource().compareTo(l2.getSource());
                return result;
            }
        });
        
        long start = System.currentTimeMillis();
        try {
            for(Future<List<Place>> r : results) {
                List<Place> ypLocationsForTerm = null;
                try {
                    ypLocationsForTerm = r.get(5, TimeUnit.SECONDS);
                } catch(Exception e) {
                    Log.debug(e.getMessage());
                }
                if(ypLocationsForTerm != null) {
                    places.addAll(ypLocationsForTerm);
                }
            }
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
        long end = System.currentTimeMillis();
        if(Log.isInfoEnabled()) Log.info(
                "Executed " + terms.size() + " requests and brought: " + 
                        places.size()  + " places in " + (end - start) + " ms");
        
        
        
        for(Place l : places) {
            l.setCountryCode(countryCode);
        }

        return new ArrayList<>(places);
    }
    
    //the logic of getting the search terms is encapsulated in this method
    //currently the search terms are location names retrieved from Google, but it may change
    //to some predefined list of strings in the future
    
    private List<String> getSearchTerms(String zip, String countryCode, int distanceInMeters) {
        Location origin = locationService.getPrimaryLocation(zip, countryCode);
        if(origin != null) {
            List<Place> googlePlaces = googleService.getPlaces(
                    origin.getLatitude().doubleValue(), origin.getLongitude().doubleValue(), distanceInMeters);            
            List<String> terms = new ArrayList<>(googlePlaces.size());
            for(final Place gLocation : googlePlaces) {
                terms.add(gLocation.getName());
            }        
            return terms;            
        }
        return Collections.emptyList();
    }
    
    private List<String> getSearchTerms() {        
        List<PlaceCategory> categories = placeDao.getAllPlaceCategories();
        List<String> terms = new ArrayList<>(categories.size());
        for(PlaceCategory c : categories) {
            terms.add(c.getName());
        }
        return terms;
    }    
    
    private List<Place> filterPlacesByDistance(Collection<Place> places, int distanceInMeters) {
        List<Place> result = new ArrayList<>(places.size());        
        for(Place l : places) {
            Integer distance = l.getDistance();
            if(distance == null || distance <= distanceInMeters) {
                result.add(l);
            }
        }
        return result;
    }
    
    private List<Place> filterPlacesByCategories(
            List<Place> places, String categories, boolean negate) {
        if(categories == null || categories.isEmpty() || places.isEmpty()) {            
            return places;
        }
        
        List<Place> filtered = new ArrayList<>(places.size());
        Set<String> cats = StringUtils.split(categories, ",", true);
        outer: for(Place location : places) {
            String categoriesString = CollectionUtils.join(location.getCategoryNames()).toLowerCase();
            for(String c : cats) {
                boolean contains = categoriesString.contains(c); 
                if(contains && !negate || !contains && negate) {
                    filtered.add(location);
                    continue outer;
                }
            }            
        }
        return filtered;
    }
    
    private List<String> filterLocationCategories(
            List<String> locationCategories, String categories, boolean negate) {
        if(categories == null || categories.isEmpty()) return locationCategories;
        List<String> filtered = new ArrayList<>(locationCategories.size());

        Set<String> cats = StringUtils.split(categories, ",", true);
        outer: for(String c : locationCategories) {
            String categoriesString = c.toLowerCase();
            for(String cat : cats) {
                boolean contains = categoriesString.contains(cat); 
                if(contains && !negate || !contains && negate) {
                    filtered.add(c);
                    continue outer;
                }
            }            
        }
        return filtered;
    }
    
    private String getRestaurantsCategories() {
        return "restaurants";
    }    

    ///////////// Events private methods /////////////////////////////
    
    private List<Event> getEventsByZipInfo(String zip, String countryCode) {
        Location origin = locationService.getPrimaryLocation(zip, countryCode);
        return getEvents(zip, countryCode, origin);
    }
    
    private List<Event> getEventsByLocation(double latitude, double longitude) {
        Location origin = locationService.getLocation(latitude, longitude);
        return getEvents(origin.getZip(), origin.getCountryCode(), origin);
    }
    
    private List<Event> getEventsByIp(String ip) {
        CityLocation cityLocation = globalDataDao.getCityLocationByIp(ip);
        if(cityLocation != null) {
            if(cityLocation.hasPostalCodeAndCountry()) {
                return getEventsByZipInfo(cityLocation.getPostalCode(), cityLocation.getCountryCode());
            } else if(cityLocation.hasLocation()) {
                return getEventsByLocation(cityLocation.getLatitude(), cityLocation.getLongitude());
            }
        }
        return Collections.emptyList();
    }    
    
    private List<Event> getEvents(String zip, String countryCode, Location origin) {
        List<String> terms = locationService.getCities(zip, countryCode);
        List<Event> events = facebookService.getEvents(terms);
        List<Event> processedEvents = postProcessEvents(origin, countryCode, events);
        return processedEvents;
    }
    
    //this calculates and sets event distances as well as
    //properly set events date and time (with time zone) 
    //removes events in the past and sorts the remaining events by time/date
    private List<Event> postProcessEvents(Location origin, String countryCode, List<Event> events) {
        for(Event e : events) {
            if(origin != null) {
                setEventDistance(e, countryCode, origin);
            }
            setEventDates(e);
        }
        
        List<Event> eventsFiltered = filterEventsByDate(events);
        eventsFiltered = filterEventsByDistance(events);
        
        Collections.sort(eventsFiltered, new Comparator<Event>() {
            @Override
            public int compare(Event e1, Event e2) {
                Calendar e1Start = e1.getStartDate();
                Calendar e2Start = e2.getStartDate();
                if(e1Start != null && e2Start != null) {
                    return e1Start.compareTo(e2Start);
                } else if(e1Start != null && e2Start == null) {
                    return -1;
                } else if(e1Start == null && e2Start != null) {
                    return 1;
                }
                return 0;
            }
        });
        
        return eventsFiltered;
    }
    
    private void setEventDistance(Event e, String countryCode, Location origin) {
        Double eLat = e.getLatitude();
        Double eLon = e.getLongitude();
        String eZip = e.getZip();
        Location eventLocation = null;
        if(eLat != null && eLon != null) {
            eventLocation = new Location(eLat.floatValue(), eLon.floatValue());
        } else if (eZip != null) {
            eventLocation = locationService.getPrimaryLocation(eZip, countryCode);
        }
        if(eventLocation != null) {
            e.setDistance(locationService.distance(origin, eventLocation));
        }
    }    
    
    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    private static final DateFormat FB_EVENT_DATE_FORMAT_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    static { FB_EVENT_DATE_FORMAT_TIME.setTimeZone(GMT); }    
    private static final DateFormat FB_EVENT_DATE_FORMAT_DATE_ONLY = new SimpleDateFormat("yyyy-MM-dd");
    private static final int FB_EVENT_DATE_FORMAT_DATE_ONLY_LENGTH = "yyyy-MM-dd".length();    
    
    private void setEventDates(Event e) {
        e.setStartDate(calculateEventDate(e, e.getStartTime()));
        e.setEndDate(calculateEventDate(e, e.getEndTime()));
    }
    
    private Calendar calculateEventDate(Event e, String timeStr) {
        if(timeStr == null) return null;
        Calendar date = null;
        try {
            if(timeStr.length() > FB_EVENT_DATE_FORMAT_DATE_ONLY_LENGTH) {
                DateFormat format = FB_EVENT_DATE_FORMAT_TIME;
                date = Calendar.getInstance();
                date.setTimeZone(GMT);
                date.setTime(format.parse(timeStr));
            } else {
                DateFormat format = FB_EVENT_DATE_FORMAT_DATE_ONLY;
                format.setTimeZone(GMT);
                
                String zip = e.getZip();
                if(zip != null) {
                    String timeZone = globalDataDao.getTimeZoneByZip(e.getZip());
                    if(timeZone != null) {
                        format.setTimeZone(TimeZone.getTimeZone(timeZone));                        
                    }
                }
                date = Calendar.getInstance();
                date.setTimeZone(format.getTimeZone());
                date.setTime(format.parse(timeStr));            
            }
        } catch (Exception ex) {
          //nothing keep the date null
        }
        return date;
    }
        
    private List<Event> filterEventsByDate(List<Event> events) {
        List<Event> result = new ArrayList<>(events.size());        
        for(Event e : events) {
            Calendar startDate = e.getStartDate();
            Calendar endDate = e.getEndDate();
            
            Date now = startDate != null ? DateUtils.now(startDate.getTimeZone()) : 
                (endDate != null ? DateUtils.now(endDate.getTimeZone()) : null);
            
            Date latestTime = endDate != null ?
                endDate.getTime() : (startDate != null ? DateUtils.ceiling(startDate.getTime()) : null);
                
            if(latestTime == null || now == null || latestTime.after(now)) {
                result.add(e);
            }
        }
        return result;
    }
    
    private List<Event> filterEventsByDistance(Collection<Event> events) {
        List<Event> result = new ArrayList<>(events.size());        
        for(Event e : events) {
            Integer distance = e.getDistance();
            if(distance == null || distance <= Constants.DEFAULT_DISTANCE_IN_METERS) {
                result.add(e);
            }
        }
        return result;
    }
    
}
