package org.recap.route;

import org.apache.camel.CamelContext;
import org.junit.Test;
import org.recap.BaseTestCase;
import org.recap.ReCAPCamelContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import static org.junit.Assert.assertNotNull;

/**
 * Created by pvsubrah on 6/21/16.
 */
public class ProcessETLTest extends BaseTestCase {

    @Autowired
    ReCAPCamelContext reCAPCamelContext;

    @Autowired
    CamelContext camelContext;

    int chunkSize = 1000;

    @Test
    public void process() throws Exception {
        assertNotNull(reCAPCamelContext);
        String endPoint = getEndPoint();
        reCAPCamelContext.addDynamicRoute(camelContext, endPoint, chunkSize);
        while(reCAPCamelContext.isRunning()){
            Thread.sleep(1000);
        }
    }

    public String getEndPoint() throws URISyntaxException {
        URL resource = getClass().getResource("nypl-10k.xml");
        File file = new File(resource.toURI());
        return file.getParent();
    }
}
