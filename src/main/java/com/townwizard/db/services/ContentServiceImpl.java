package com.townwizard.db.services;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.townwizard.db.dao.ContentDao;
import com.townwizard.db.dao.EventDao;
import com.townwizard.db.dao.RatingDao;
import com.townwizard.db.model.Content;
import com.townwizard.db.model.Content.ContentType;
import com.townwizard.db.model.Event;
import com.townwizard.db.model.EventResponse;
import com.townwizard.db.model.Rating;
import com.townwizard.db.model.User;
import com.townwizard.db.util.DateUtils;

@Component("contentService")
@Transactional
public class ContentServiceImpl implements ContentService {
    
    @Autowired
    private ContentDao contentDao;
    @Autowired
    private RatingDao ratingDao;
    @Autowired
    private EventDao eventDao;
    

    @Override
    public Long saveRating(Long userId, Integer siteId,
            ContentType contentType, Long externalContentId, Float value) {
        Content c = contentDao.getContent(siteId, contentType, externalContentId);
        if(c == null) {
            c = createContent(siteId, contentType, externalContentId); 
            contentDao.create(c);
        }
        User u = new User();
        u.setId(userId);
        Rating r = ratingDao.getRating(u, c);
        if(r == null) {
            r = createRating(u, c, value);
            ratingDao.create(r);
        } else {
            r.setValue(new Float(value));
            ratingDao.update(r);
        }
        return r == null ? null : r.getId();
    }
    
    @Override
    public Float getUserRating(Long userId, Integer siteId,
            ContentType contentType, Long externalContentId) {
        Float rating = null;
        Content c = contentDao.getContent(siteId, contentType, externalContentId);
        if(c != null) {
            User u = new User();
            u.setId(userId);
            Rating r = ratingDao.getRating(u, c);
            if(r != null) {
                rating = r.getValue();
            }
        }
        return rating;
    }
    
    @Override
    public Float[] getUserRatings(Long userId, Integer siteId,
            ContentType contentType, Long[] externalContentIds) {
        Float[] retVal = new Float[externalContentIds.length];
        for(int i = 0; i < externalContentIds.length; i++) {
            retVal[i] = getUserRating(userId, siteId, contentType, externalContentIds[i]);
        }
        return retVal;
    } 

    @Override
    public Float getAverageRating(Integer siteId, ContentType contentType,
            Long externalContentId) {
        Float rating = 0F;
        Content c = contentDao.getContent(siteId, contentType, externalContentId);
        if(c != null) {
            rating = ratingDao.getAverageRating(c);            
        }
        return rating;
    }
    
    @Override
    public List<EventResponse> getUserEventResponses(Long userId, Date from, Date to) {
        Date fromDate = DateUtils.floor(from == null ? BEGINNING_OF_TIME : from);
        Date toDate = DateUtils.ceiling(to == null ? new Date() : to);
        User u = new User();
        u.setId(userId);
        return eventDao.getUserEventResponses(u, fromDate, toDate);
    }
    
    @Override
    public List<EventResponse> getEventResponses(Integer siteId, Long eventId, Date eventDate) {
        Event event = createEventIfAbsent(siteId, eventId, eventDate);
        return eventDao.getEventResponses(event);
    }
    
    @Override
    public Long saveEventResponse(
            Long userId, Integer siteId, Long eventId, Date eventDate, Character value) {
        Event e = createEventIfAbsent(siteId, eventId, eventDate);
        User u = new User();
        u.setId(userId);

        EventResponse er = eventDao.getEventResponse(u, e);
        if(er == null) {
            er = createEventResponse(u, e, value);
            eventDao.create(er);
        } else {
            er.setValue(value);
            eventDao.update(er);
        }

        return er == null ? null : er.getId();
    }
    
    private Event createEventIfAbsent(Integer siteId, Long eventId, Date eventDate) {
        Event event = eventDao.getEvent(siteId, eventId);
        if(event == null) {
            event = createEvent(siteId, eventId, eventDate); 
            eventDao.create(event);
        }
        return event;
    }
    
    private Content createContent(Integer siteId, ContentType contentType, Long externalContentId) {
        Content c = new Content();
        c.setSiteId(siteId);
        c.setContentType(contentType);
        c.setExternalId(externalContentId);
        return c;
    }
    
    private Rating createRating(User user, Content content, Float value) {
        Rating r = new Rating();
        r.setUser(user);
        r.setContent(content);
        r.setValue(value);
        return r;
    }
    
    private Event createEvent(Integer siteId, Long eventId, Date eventDate) {
        Event e = new Event();
        e.setSiteId(siteId);
        e.setExternalId(eventId);
        e.setDate(eventDate);
        return e;
    }
    
    private EventResponse createEventResponse(User u, Event e, Character value) {
        EventResponse er = new EventResponse();
        er.setUser(u);
        er.setEvent(e);
        er.setValue(value);
        return er;
    }
    
    private static Date BEGINNING_OF_TIME = null;
    static {
        Calendar c = Calendar.getInstance();
        c.set(2012, 0, 1);
        BEGINNING_OF_TIME = c.getTime();
    }

}
