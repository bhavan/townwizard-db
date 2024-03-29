package com.townwizard.globaldata.model;


import java.util.Calendar;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import com.townwizard.db.constants.Constants;

/**
 * Generic (that is not provider specific) event class. Implements DistanceComparable, so instances
 * can be sorted by distance/name.
 * 
 * Event objects from different sources will be converted to objects of this class, and depending
 * on the source not all the fields of this class will be necessarily populated.
 */
@JsonSerialize (include = JsonSerialize.Inclusion.NON_EMPTY)
public class Event implements DistanceComparable {

    private String id;
    private String name;
    private String location;        //location name, not latitude and longitude
    private String description;
    private String street;
    private String city;
    private String state;
    private String country;
    private String zip;
    private String locationId;      //vendor specific location (venue) id
    private String picture;
    private String privacy;
    private Double latitude;
    private Double longitude;
    private Integer distance;       //distance is populated on our side 
    private Double distanceInMiles; //this is set together with distance
    private String startTime;       //event start time string passed as is from the provider
    private String endTime;         //event end time string passed as is from the provider
    private String link;
    @JsonIgnore
    private Calendar startDate;     //this is calculated from startDate on our side, and is time zone specific
    @JsonIgnore
    private Calendar endDate;       //this is calculated from endDate on our side, and is time zone specific
                                    //startDate and endDate are used to sort events by dates
    public String getStreet() {
        return street;
    }
    public void setStreet(String street) {
        this.street = street;
    }
    public String getCity() {
        return city;
    }
    public void setCity(String city) {
        this.city = city;
    }
    public String getState() {
        return state;
    }
    public void setState(String state) {
        this.state = state;
    }
    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }
    public String getZip() {
        return zip;
    }
    public void setZip(String zip) {
        this.zip = zip;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getLocation() {
        return location;
    }
    public void setLocation(String location) {
        this.location = location;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getLocationId() {
        return locationId;
    }
    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }
    public String getPicture() {
        return picture;
    }
    public void setPicture(String picture) {
        this.picture = picture;
    }
    public String getPrivacy() {
        return privacy;
    }
    public void setPrivacy(String privacy) {
        this.privacy = privacy;
    }
    public Integer getDistance() {
        return distance;
    }
    public void setDistance(Integer distance) {
        this.distance = distance;
        this.distanceInMiles = distance / Constants.METERS_IN_MILE;
    }
    public Double getLatitude() {
        return latitude;
    }
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    public Double getLongitude() {
        return longitude;
    }
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    public Double getDistanceInMiles() {
        return distanceInMiles;
    }
    public void setDistanceInMiles(Double distanceInMiles) {
        this.distanceInMiles = distanceInMiles;
        this.distance = new Double(distanceInMiles * Constants.METERS_IN_MILE).intValue();
    }
    public String getStartTime() {
        return startTime;
    }
    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }
    public String getEndTime() {
        return endTime;
    }
    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
    public String getLink() {
        return link;
    }
    public void setLink(String link) {
        this.link = link;
    }
    public Calendar getStartDate() {
        return startDate;
    }
    public void setStartDate(Calendar startDate) {
        this.startDate = startDate;
    }
    public Calendar getEndDate() {
        return endDate;
    }
    public void setEndDate(Calendar endDate) {
        this.endDate = endDate;
    }
    
}
