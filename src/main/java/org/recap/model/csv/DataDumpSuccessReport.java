package org.recap.model.csv;

import lombok.Data;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

import java.io.Serializable;
import java.util.List;

/**
 * Created by premkb on 30/9/16.
 */
@Data
@CsvRecord(generateHeaderColumns = true, separator = ",", quoting = true, crlf = "UNIX")
public class DataDumpSuccessReport implements Serializable{
    @DataField(pos = 1, columnName = "Institution Code")
    private String institutionCodes;
    @DataField(pos = 2, columnName = "Requesting Institution Code")
    private String requestingInstitution;
    @DataField(pos = 3, columnName = "Fetch Type")
    private String fetchType;
    @DataField(pos = 4, columnName = "Export From Date")
    private String exportFromDate;
    @DataField(pos = 5, columnName = "Collection Group Id(s)")
    private String collectionGroupIds;
    @DataField(pos = 6, columnName = "Transmission Type")
    private String transmissionType;
    @DataField(pos = 7, columnName = "Export Format")
    private String exportFormat;
    @DataField(pos = 8, columnName = "IMS Depository")
    private String imsDepositoryCodes;
    @DataField(pos = 9, columnName = "No of Bibs Exported")
    private String noOfBibsExported;
    @DataField(pos = 10, columnName = "No of Items Exported")
    private String exportedItemCount;


    private String fileName;
    private String reportType;
    private String institutionName;

    /**
     * The Data dump success report list.
     */
    private List<DataDumpSuccessReport> dataDumpSuccessReportList;

}
