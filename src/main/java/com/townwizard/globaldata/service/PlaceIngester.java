package com.townwizard.globaldata.service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.townwizard.db.logger.Log;
import com.townwizard.globaldata.model.directory.Ingest;
import com.townwizard.globaldata.model.directory.Place;
import com.townwizard.globaldata.model.directory.PlaceIngest;
import com.townwizard.globaldata.model.directory.ZipIngest;
import com.townwizard.globaldata.service.provider.YellowPagesService;

@Component("placeIngester")
public final class PlaceIngester {
    
    @Autowired
    private YellowPagesService yellowPagesService;
    @Autowired
    private PlaceService placeService;
    
    @PostConstruct
    public void init() {
        dbExecutor.submit(new DbExecutor());
        queueMonitor.submit(new IngestQueueMonitor());
    }
    
    private static boolean shutdownFlag = false;
    
    //This executor is responsible for saving ingests in the DB.
    private static final ExecutorService dbExecutor = Executors.newFixedThreadPool(1);
    
    //This executor is responsible for monitoring the queue.
    private static final ExecutorService queueMonitor = Executors.newFixedThreadPool(1);    
    
    //These executors will bring places from the source in parallel.
    private static final ExecutorService httpExecutors = Executors.newFixedThreadPool(4);
    
    //The http executors will be placing category ingests in this queue, and the 
    //db thread will be taking ingest from it and save ingests in the DB
    //private static final BlockingQueue<IngestItem> ingestQueue = new LinkedBlockingQueue<>();
    private static final Queue<IngestItem> ingestQueue = new ConcurrentLinkedQueue<>();
    
    private final static class IngestItem {
        String zip, countryCode, category;
        List<Place> places;
        int countDown;
        
        IngestItem(String zip, String countryCode, String category, List<Place> places, int countDown) {
            this.zip = zip;
            this.countryCode = countryCode;            
            this.category = category;
            this.places = places;
            this.countDown = countDown;
        }
    }
    
    private final class HttpExecutor implements Runnable {
        
        private String zip, countryCode, category;
        private int countDown;
        
        HttpExecutor(String zip, String countryCode, String category, int countDown) {
            this.zip = zip;
            this.countryCode = countryCode;            
            this.category = category;
            this.countDown = countDown;
        }

        @Override
        public void run() {
            if(shutdownFlag) return;
            try {
                List<Place> places = null;
                try {
                    places = getPlacesFromSource(zip, countryCode, category);
                } catch(Exception e) {
                    places = Collections.emptyList();
                    Log.exception(e);
                    e.printStackTrace();
                }
                ingestQueue.add(new IngestItem(zip, countryCode, category, places, countDown));                
            } catch(Exception e) {
                Log.exception(e);
                e.printStackTrace();
            }
        }
    }
    
    private final class DbExecutor implements Runnable {
        
        DbExecutor() {
            if(Log.isInfoEnabled()) {
                Log.info("Place ingest DB executor started.");
            }
        }
        
        @Override
        public void run() {
            while(true) {
                if(shutdownFlag) {
                    Log.info("Exiting place ingest DB executor...");
                    return;
                }
                try {
                    IngestItem item = ingestQueue.poll();
                    if(item != null) {
                        //System.out.println("Ingesting: zip - " + item.zip + " item - " + item.countDown);
                        doIngestByZipAndCategory(item.zip, item.countryCode, item.category, item.places);
                        if(item.countDown == 0) {
                            ZipIngest ingest = placeService.getZipIngest(item.zip, item.countryCode);
                            if(ingest != null) {
                                if(Log.isInfoEnabled()) Log.info("Finishing ingest for zip: " + item.zip);
                                ingest.setStatus(Ingest.Status.R);
                                ingest.setFinished(new Date());
                                placeService.updateZipIngest(ingest);
                            }
                        }
                    } else {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            Log.info("Exiting place ingest DB executor...");
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.exception(e);
                    e.printStackTrace();
                }
            }                
        }        
    }
    
    private final class IngestQueueMonitor implements Runnable {
        
        IngestQueueMonitor() {
            if(Log.isInfoEnabled()) {
                Log.info("Place ingest queue monitor started.");
            }
        }
        
        @Override
        public void run() {
            while(true) {
                if(shutdownFlag) {
                    Log.info("Exiting place ingest queue monitor...");
                    return;
                }
                try {
                    int queueSize = ingestQueue.size();
                    if(queueSize > 0) {
                        if(Log.isInfoEnabled()) {
                            Log.info("Ingest queue size: " + queueSize);
                        }
                    }
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ie) {
                        Log.info("Exiting place ingest queue monitor...");
                        return;
                    }
                } catch (Exception e) {
                    Log.exception(e);
                }
            }                
        }        
    }
    
    public static void shutdownIngestThreads() {
        shutdownFlag = true;
        
        Log.info("About to shutdown executors...");        
        PlaceIngester.httpExecutors.shutdownNow();
        PlaceIngester.queueMonitor.shutdownNow();        
        PlaceIngester.dbExecutor.shutdownNow();
        
        int attempt = 1;
        while(!(httpExecutors.isTerminated() && dbExecutor.isTerminated() && queueMonitor.isTerminated())) {
            if(attempt++ > 5) break;
            try { Thread.sleep(1000); } catch(InterruptedException e) {
                Log.warning("Executors shutdown interrupted");
            }
            Log.info("Waiting for place ingest executors to exit...");
        }

        if(!httpExecutors.isTerminated()) {
            Log.error("Failed to shutdown place ingest http executors");
        }
        if(!dbExecutor.isTerminated()) {
            Log.error("Failed to shutdown place ingest DB executor");
        }
        if(!queueMonitor.isTerminated()) {
            Log.error("Failed to shutdown place ingest queue monitor");
        }
    }
    
    public void ingestByZip(String zipCode, String countryCode) {
        ZipIngest ingest = placeService.getZipIngest(zipCode, countryCode);
        if(ingest == null || ingest.getStatus() != ZipIngest.Status.N) return;
        
        if(Log.isInfoEnabled()) Log.info("Starting ingest for zip: " + zipCode);
        
        ingest.setStatus(Ingest.Status.I);
        placeService.updateZipIngest(ingest);
        
        List<String> categories = placeService.getAllPlaceCategoryNames();
        int countDown = categories.size();
        for(String category : categories) {
            if(--countDown == 0) {
                if(Log.isDebugEnabled()) Log.debug("Submitting zero item for zip: " +  zipCode);
            }
            httpExecutors.submit(new HttpExecutor(zipCode, countryCode, category, countDown));
        }
        
    }
    
    public Object[] ingestByZipAndCategory(String zipCode, String countryCode, String categoryOrTerm) {
        return doIngestByZipAndCategory(zipCode, countryCode, categoryOrTerm, null);
    } 
    
    private Object[] doIngestByZipAndCategory(
            String zipCode, String countryCode, String categoryOrTerm, List<Place> placeList) {
        
        PlaceIngest ingest = placeService.getIngest(zipCode, countryCode, categoryOrTerm);
        if(ingest != null) {        
            PlaceIngest.Status status = ingest.getStatus();
            
            List<Place> places = placeList;
            if(status != PlaceIngest.Status.R) {
                if(places == null) {
                    places = getPlacesFromSource(zipCode, countryCode, categoryOrTerm);
                }
                if(status == PlaceIngest.Status.N) {
                    placeService.saveIngest(ingest, places);
                }
            }
            
            return new Object[]{ingest, places};
        }
        return null;
    }
    
    private List<Place> getPlacesFromSource(
            String zipCode, String countryCode, String categoryOrTerm) {
        List<Place> places = yellowPagesService.getPlaces(zipCode, categoryOrTerm);
        for(Place p : places) p.setCountryCode(countryCode);
        return places;
    }

}
