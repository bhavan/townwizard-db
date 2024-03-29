package com.townwizard.globaldata.model.directory;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

/**
 * Place category objects are Hibernate entities and relate to Place objects in many-to-many
 * fashion.
 */
@Entity
@Table(name = "Category")
public class PlaceCategory {
    
    @Id @GeneratedValue @Column(nullable = false, updatable = false)
    private Long id;
    private String name;
    
    @ManyToMany (fetch=FetchType.LAZY)    
    @JoinTable (
            name = "Location_Category",
            joinColumns= {@JoinColumn (name="category_id")},
            inverseJoinColumns = {@JoinColumn(name="location_id")}
    )    
    private Set<Place> places;

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Set<Place> getPlaces() {
        return places;
    }
    public void setPlaces(Set<Place> places) {
        this.places = places;
    }
    
    /**
     * Convenience method to add place to place category.  This method will not set
     * both sides of the relationships.  This is done on the Place side.
     */
    public void addPlace(Place l) {
        if(places == null) {
            places = new HashSet<>();
        }
        places.add(l);        
    }
    
    // since this class does not extend AbstractEntity, it needs hashCode() and equals()
    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PlaceCategory other = (PlaceCategory) obj;
        boolean idsEqual = compareWithNulls(id, other.id);
        boolean namesEqual = compareWithNulls(name, other.name);
        return idsEqual && namesEqual;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    private boolean compareWithNulls(Object o1, Object o2) {
        if(o1 != null && o2 != null) return o1.equals(o2);
        if(o1 == null && o2 != null || o1 != null && o2 == null) return false;
        return true;
    }

}
