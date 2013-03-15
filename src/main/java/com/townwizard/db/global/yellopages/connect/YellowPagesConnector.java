package com.townwizard.db.global.yellopages.connect;

import java.io.IOException;

import org.apache.http.client.ClientProtocolException;
import org.springframework.stereotype.Component;

import com.townwizard.db.constants.Constants;
import com.townwizard.db.logger.Log;
import com.townwizard.db.util.HttpUtils;

@Component("yellowPagesConnector")
public final class YellowPagesConnector {
    
    private static final String SEARCH_URL = "http://api2.yp.com/listings/v1/search?";
    
    public String executePlacesRequest(String term, String zip, int distanceInMeters)
            throws ClientProtocolException, IOException {
        
        StringBuilder sb = new StringBuilder(SEARCH_URL);
        sb.append("searchloc=").append(zip)
          .append("&term=").append(term)
          .append("&radius=").append(distanceInMeters / Constants.METERS_IN_MILE);
        
        appendMandatoryParameters(sb);
        
        String url = sb.toString();
        Log.debug(url);
        String response = HttpUtils.executeGetRequest(url);
        return response;
    }
    
    private void appendMandatoryParameters(StringBuilder sb) {
        sb.append("&sort=distance")
          .append("&listingcount=10")
          .append("&format=json")
          .append("&key=").append(Constants.YELLO_PAGES_API_KEY);
    }

}
