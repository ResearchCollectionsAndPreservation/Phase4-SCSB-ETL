package org.recap.report;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.recap.BaseTestCase;
import org.recap.BaseTestCaseUT;
import org.recap.RecapConstants;
import org.recap.repositoryrw.ReportDetailRepository;
import org.recap.util.datadump.DataExportHeaderUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CommonReportGeneratorUT extends BaseTestCaseUT {

    @Mock
    ReportDetailRepository mockReportDetailRepository;

    @Mock
    DataExportHeaderUtil dataExportHeaderUtil;

    @InjectMocks
    CommonReportGenerator commonReportGenerator;

    @Before
    public void init() {
        commonReportGenerator = new CommonReportGenerator();
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testProcess() {
        CamelContext ctx = new DefaultCamelContext();
        Exchange ex = new DefaultExchange(ctx);
        Message in = ex.getIn();
        ex.setMessage(in);
        ex.setProperty(RecapConstants.INST_NAME, "CUL");
        GenericFile<Object> camelFileExchangeFile=new GenericFile<>();
        camelFileExchangeFile.setFileName("file");
        ex.setProperty(RecapConstants.CAMEL_EXCHANGE_FILE, camelFileExchangeFile);
        Map<String, Object> mapdata = new HashMap<>();
        mapdata.put("institutionName", "CUL");
        in.setHeaders(mapdata);
        ex.setIn(in);
        commonReportGenerator.process(ex, "institutionName", mockReportDetailRepository);
        try{commonReportGenerator.processRecordFailures(ex, Arrays.asList("Test"), "batchHeaders", "requestId", dataExportHeaderUtil);}catch (Exception e){e.printStackTrace();}
    }
}
