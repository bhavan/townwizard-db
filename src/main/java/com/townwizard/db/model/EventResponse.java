package com.townwizard.db.model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

@Entity
public class EventResponse extends AuditableEntity {
    
    private static final long serialVersionUID = 4334139873708624469L;

    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "userId")
    private User user;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "eventId")
    private Event event;
    private Character value;
    
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }
    public Event getEvent() {
        return event;
    }
    public void setEvent(Event event) {
        this.event = event;
    }
    public Character getValue() {
        return value;
    }
    public void setValue(Character value) {
        this.value = value;
    }
        
}