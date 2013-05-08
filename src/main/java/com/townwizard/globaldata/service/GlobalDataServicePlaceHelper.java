package com.townwizard.globaldata.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.townwizard.db.constants.Constants;
import com.townwizard.db.util.CollectionUtils;
import com.townwizard.db.util.StringUtils;
import com.townwizard.globaldata.dao.GlobalDataDao;
import com.townwizard.globaldata.model.CityLocation;
import com.townwizard.globaldata.model.DistanceComparator;
import com.townwizard.globaldata.model.Location;
import com.townwizard.globaldata.model.directory.Place;
import com.townwizard.globaldata.model.directory.PlaceIngest;
import com.townwizard.globaldata.service.geo.LocationService;

@Component("placeHelper")
public final class GlobalDataServicePlaceHelper {

    @Autowired private LocationService locationService;
    @Autowired private GlobalDataDao globalDataDao;
    @Autowired private PlaceService placeService;
    @Autowired private PlaceIngester placeIngester;    
    
    public List<Place> getPlacesByZipInfo(
            String zip, String countryCode, String categoryOrTerm, String mainCategory) {
        Location origin = locationService.getPrimaryLocation(zip, countryCode);
        return getPlaces(zip, countryCode, categoryOrTerm, mainCategory, origin);
    }

    public List<Place> getPlacesByLocation(
            double latitude, double longitude, String categoryOrTerm, String mainCategory) {
        Location orig = locationService.getLocation(latitude, longitude);
        return getPlaces(orig.getZip(), orig.getCountryCode(), categoryOrTerm, mainCategory, orig);
    }
    
    public List<Place> getPlacesByIp(String ip, String categoryOrTerm, String mainCategory) {
        CityLocation cityLocation = globalDataDao.getCityLocationByIp(ip);
        if(cityLocation != null) {
            if(cityLocation.hasPostalCodeAndCountry()) {
                return getPlacesByZipInfo(cityLocation.getPostalCode(), cityLocation.getCountryCode(),
                        categoryOrTerm, mainCategory);
            }
            if(cityLocation.hasLocation()) {
                return getPlacesByLocation(cityLocation.getLatitude(), cityLocation.getLongitude(),
                        categoryOrTerm, mainCategory);
            }             
        }
        return Collections.emptyList();
    }
    
    public List<String> getPlaceCategories(String mainCategory) {        
        List<String> categories = placeService.getAllPlaceCategoryNames();
        
        if(mainCategory != null && !mainCategory.isEmpty()) {
            if(Constants.RESTAURANTS.equals(mainCategory)) {
                categories = filterPlaceCategories(categories, getRestaurantsCategories(), false);
            } else if(Constants.DIRECTORY.equals(mainCategory)){
                categories = filterPlaceCategories(categories, getRestaurantsCategories(), true);
            }
        }        
        
        return categories;
    }


    ///////////////////////// private methods //////////////////////////
    
    private List<Place> getPlaces(String zip, String countryCode, String categoryOrTerm, 
            String mainCategory, Location origin) {

        Object[] ingestWithPlaces = placeIngester.ingestByZipAndCategory(zip, countryCode, categoryOrTerm);
        
        PlaceIngest ingest = (PlaceIngest)ingestWithPlaces[0];        
        
        @SuppressWarnings("unchecked")
        List<Place> places = (List<Place>)ingestWithPlaces[1];
        if(places == null) {
            places = placeService.getPlaces(ingest);
        }
        
        if(mainCategory != null && !mainCategory.isEmpty()) {
            if(Constants.RESTAURANTS.equals(mainCategory)) {
                places = filterPlacesByCategories(places, getRestaurantsCategories(), false);
            } else if(Constants.DIRECTORY.equals(mainCategory)){
                places = filterPlacesByCategories(places, getRestaurantsCategories(), true);
            }
        }

        for(Place p : places) {
            Location l = new Location(p.getLatitude(), p.getLongitude());
            p.setDistance(locationService.distance(origin, l));
        }
        
        Collections.sort(places, new DistanceComparator());
        
        placeIngester.ingestByZip(zip, countryCode);
        
        return places;
    }
    
    private List<String> filterPlaceCategories(
            List<String> placeCategories, String categories, boolean negate) {
        if(categories == null || categories.isEmpty()) return placeCategories;
        List<String> filtered = new ArrayList<>(placeCategories.size());

        Set<String> cats = StringUtils.split(categories, ",", true);
        outer: for(String c : placeCategories) {
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
    
    private List<Place> filterPlacesByCategories(
            List<Place> places, String categories, boolean negate) {
        if(categories == null || categories.isEmpty() || places.isEmpty()) {            
            return places;
        }
        
        List<Place> filtered = new ArrayList<>(places.size());
        Set<String> cats = StringUtils.split(categories, ",", true);
        outer: for(Place place : places) {
            String categoriesString = CollectionUtils.join(place.getCategoryNames()).toLowerCase();
            for(String c : cats) {
                boolean contains = categoriesString.contains(c); 
                if(contains && !negate || !contains && negate) {
                    filtered.add(place);
                    continue outer;
                }
            }            
        }
        return filtered;
    }
    
    private String getRestaurantsCategories() {
        return "restaurants";
    }
    
}
