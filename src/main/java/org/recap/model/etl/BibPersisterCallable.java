package org.recap.model.etl;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.recap.RecapCommonConstants;
import org.recap.model.jaxb.*;
import org.recap.model.jaxb.marc.CollectionType;
import org.recap.model.jaxb.marc.ContentType;
import org.recap.model.jaxb.marc.LeaderFieldType;
import org.recap.model.jaxb.marc.RecordType;
import org.recap.model.jpa.BibliographicEntity;
import org.recap.model.jpa.HoldingsEntity;
import org.recap.model.jpa.ItemEntity;
import org.recap.model.jpa.XmlRecordEntity;
import org.recap.model.jparw.ReportDataEntity;
import org.recap.model.jparw.ReportEntity;
import org.recap.util.DBReportUtil;
import org.recap.util.MarcUtil;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.Callable;


/**
 * Created by pvsubrah on 6/24/16.
 */
public class BibPersisterCallable implements Callable {

    private MarcUtil marcUtil;
    private BibRecord bibRecord;
    private XmlRecordEntity xmlRecordEntity;
    private Map<String, Integer> institutionEntitiesMap;
    private String institutionName;

    private Map itemStatusMap;
    private Map collectionGroupMap;

    private DBReportUtil dbReportUtil;
    private static final String bibliographicEntityname = "bibliographicEntity";

    @Override
    public Object call() {
        Map<String, Object> map = new HashMap<>();
        boolean processBib = false;

        List<HoldingsEntity> holdingsEntities = new ArrayList<>();
        List<ItemEntity> itemEntities = new ArrayList<>();
        List<ReportEntity> reportEntities = new ArrayList<>();

        getDbReportUtil().setInstitutionEntitiesMap(institutionEntitiesMap);
        getDbReportUtil().setCollectionGroupMap(collectionGroupMap);

        Integer owningInstitutionId = institutionEntitiesMap.get(bibRecord.getBib().getOwningInstitutionId());
        Date currentDate = new Date();
        Map<String, Object> bibMap = processAndValidateBibliographicEntity(owningInstitutionId,currentDate);
        BibliographicEntity bibliographicEntity = (BibliographicEntity) bibMap.get(bibliographicEntityname);
        ReportEntity bibReportEntity = (ReportEntity) bibMap.get("bibReportEntity");
        if (bibReportEntity != null) {
            reportEntities.add(bibReportEntity);
        } else {
            processBib = true;
        }

        List<Holdings> holdings = bibRecord.getHoldings();
        for (Iterator<Holdings> iterator = holdings.iterator(); iterator.hasNext(); ) {
            Holdings holdingsList = iterator.next();
            List<Holding> holding = holdingsList.getHolding();
            for (Iterator<Holding> holdingIterator = holding.iterator(); holdingIterator.hasNext(); ) {
                boolean processHoldings = false;
                Holding holdingEnt = holdingIterator.next();
                if (holdingEnt.getContent() != null) {
                    CollectionType holdingContentCollection = holdingEnt.getContent().getCollection();
                    List<RecordType> holdingRecordTypes = holdingContentCollection.getRecord();
                    RecordType holdingsRecordType = holdingRecordTypes.get(0);

                    Map<String, Object> holdingsMap = processAndValidateHoldingsEntity(bibliographicEntity, holdingEnt, holdingContentCollection,currentDate);
                    HoldingsEntity holdingsEntity = (HoldingsEntity) holdingsMap.get("holdingsEntity");
                    ReportEntity holdingsReportEntity = (ReportEntity) holdingsMap.get("holdingsReportEntity");
                    if (holdingsReportEntity != null) {
                        reportEntities.add(holdingsReportEntity);
                    } else {
                        processHoldings = true;
                        holdingsEntities.add(holdingsEntity);
                    }

                    String holdingsCallNumber = getMarcUtil().getDataFieldValue(holdingsRecordType, "852", null, null, "h");
                    String holdingsCallNumberType = getMarcUtil().getInd1(holdingsRecordType, "852", "h");

                    List<Items> items = holdingEnt.getItems();
                    for (Items item : items) {
                        ContentType itemContent = item.getContent();
                        CollectionType itemContentCollection = itemContent.getCollection();

                        List<RecordType> itemRecordTypes = itemContentCollection.getRecord();
                        for (RecordType itemRecordType : itemRecordTypes) {
                            Map<String, Object> itemMap = processAndValidateItemEntity(bibliographicEntity, holdingsEntity, owningInstitutionId, holdingsCallNumber, holdingsCallNumberType, itemRecordType,currentDate);
                            ItemEntity itemEntity = (ItemEntity) itemMap.get("itemEntity");
                            ReportEntity itemReportEntity = (ReportEntity) itemMap.get("itemReportEntity");
                            if (itemReportEntity != null) {
                                reportEntities.add(itemReportEntity);
                            } else if (processHoldings) {
                                if (holdingsEntity.getItemEntities() == null) {
                                    holdingsEntity.setItemEntities(new ArrayList<>());
                                }
                                holdingsEntity.getItemEntities().add(itemEntity);
                                itemEntities.add(itemEntity);
                            }
                        }
                    }
                }
            }
        }
        bibliographicEntity.setHoldingsEntities(holdingsEntities);
        bibliographicEntity.setItemEntities(itemEntities);

        if (!CollectionUtils.isEmpty(reportEntities)) {
            map.put("reportEntities", reportEntities);
        }
        if (processBib) {
            map.put(bibliographicEntityname, bibliographicEntity);
        }
        return map;
    }

    private Map<String, Object> processAndValidateBibliographicEntity(Integer owningInstitutionId,Date currentDate) {
        Map<String, Object> map = new HashMap<>();
        BibliographicEntity bibliographicEntity = new BibliographicEntity();
        StringBuilder errorMessage = new StringBuilder();

        ReportEntity reportEntity = new ReportEntity();
        reportEntity.setFileName(xmlRecordEntity.getXmlFileName());
        reportEntity.setInstitutionName(institutionName);
        reportEntity.setType(RecapCommonConstants.FAILURE);
        reportEntity.setCreatedDate(new Date());

        Bib bib = bibRecord.getBib();
        String owningInstitutionBibId = getOwningInstitutionBibId(bibRecord, bib);
        if (StringUtils.isNotBlank(owningInstitutionBibId)) {
            bibliographicEntity.setOwningInstitutionBibId(owningInstitutionBibId);
        } else {
            errorMessage.append("Owning Institution Bib Id cannot be null");
        }
        if (owningInstitutionId != null) {
            bibliographicEntity.setOwningInstitutionId(owningInstitutionId);
        } else {
            errorMessage.append("\n");
            errorMessage.append("Owning Institution Id cannot be null");
        }
        bibliographicEntity.setCreatedDate(currentDate);
        bibliographicEntity.setCreatedBy("etl");
        bibliographicEntity.setLastUpdatedDate(currentDate);
        bibliographicEntity.setLastUpdatedBy("etl");
        bibliographicEntity.setCatalogingStatus(RecapCommonConstants.COMPLETE_STATUS);

        ContentType bibContent = bib.getContent();
        CollectionType bibContentCollection = bibContent.getCollection();
        String bibXmlContent = bibContentCollection.serialize(bibContentCollection);
        if (StringUtils.isNotBlank(bibXmlContent)) {
            bibliographicEntity.setContent(bibXmlContent.getBytes());
        } else {
            errorMessage.append("\n");
            errorMessage.append("Bib Content cannot be empty");
        }

        boolean subFieldExistsFor245 = getMarcUtil().isSubFieldExists(bibContentCollection.getRecord().get(0), "245");
        if (!subFieldExistsFor245) {
            errorMessage.append("\n");
            errorMessage.append("Atleast one subfield should be there for 245 tag");
        }

        LeaderFieldType leader = bibContentCollection.getRecord().get(0).getLeader();
        if (!(leader != null && StringUtils.isNotBlank(leader.getValue()) && leader.getValue().length() == 24)) {
            errorMessage.append("\n");
            errorMessage.append("Leader Field value should be 24 characters");
        }

        List<ReportDataEntity> reportDataEntities = null;
        if (errorMessage.toString().length() > 1) {
            reportDataEntities = getDbReportUtil().generateBibFailureReportEntity(bibliographicEntity);
            ReportDataEntity errorReportDataEntity = new ReportDataEntity();
            errorReportDataEntity.setHeaderName(RecapCommonConstants.ERROR_DESCRIPTION);
            errorReportDataEntity.setHeaderValue(errorMessage.toString());
            reportDataEntities.add(errorReportDataEntity);
        }
        if(!CollectionUtils.isEmpty(reportDataEntities)) {
            reportEntity.addAll(reportDataEntities);
            map.put("bibReportEntity", reportEntity);
        }
        map.put(bibliographicEntityname, bibliographicEntity);
        return map;
    }

    private Map<String, Object> processAndValidateHoldingsEntity(BibliographicEntity bibliographicEntity, Holding holdingEnt, CollectionType holdingContentCollection, Date currentDate) {
        StringBuilder errorMessage = new StringBuilder();
        Map<String, Object> map = new HashMap<>();
        HoldingsEntity holdingsEntity = new HoldingsEntity();

        ReportEntity reportEntity = new ReportEntity();
        reportEntity.setFileName(xmlRecordEntity.getXmlFileName());
        reportEntity.setInstitutionName(institutionName);
        reportEntity.setType(RecapCommonConstants.FAILURE);
        reportEntity.setCreatedDate(new Date());

        String holdingsContent = holdingContentCollection.serialize(holdingContentCollection);
        if (StringUtils.isNotBlank(holdingsContent)) {
            holdingsEntity.setContent(holdingsContent.getBytes());
        } else {
            errorMessage.append("Holdings Content cannot be empty");
        }
        holdingsEntity.setCreatedDate(currentDate);
        holdingsEntity.setCreatedBy("etl");
        holdingsEntity.setLastUpdatedDate(currentDate);
        holdingsEntity.setLastUpdatedBy("etl");
        Integer owningInstitutionId = bibliographicEntity.getOwningInstitutionId();
        holdingsEntity.setOwningInstitutionId(owningInstitutionId);
        String owningInstitutionHoldingsId = holdingEnt.getOwningInstitutionHoldingsId();
        if (StringUtils.isBlank(owningInstitutionHoldingsId) || owningInstitutionHoldingsId.length() > 100) {
            owningInstitutionHoldingsId = UUID.randomUUID().toString();
        }
        holdingsEntity.setOwningInstitutionHoldingsId(owningInstitutionHoldingsId);
        List<ReportDataEntity> reportDataEntities = new ArrayList<>();
        if (errorMessage.toString().length() > 1) {
            getDbReportUtil().generateBibHoldingsFailureReportEntity(bibliographicEntity, holdingsEntity);
            ReportDataEntity errorReportDataEntity = new ReportDataEntity();
            errorReportDataEntity.setHeaderName(RecapCommonConstants.ERROR_DESCRIPTION);
            errorReportDataEntity.setHeaderValue(errorMessage.toString());
            reportDataEntities.add(errorReportDataEntity);
        }

        if(!(CollectionUtils.isEmpty(reportDataEntities))) {
            reportEntity.addAll(reportDataEntities);
            map.put("holdingsReportEntity", reportEntity);
        }
        map.put("holdingsEntity", holdingsEntity);
        return map;
    }

    private Map<String, Object> processAndValidateItemEntity(BibliographicEntity bibliographicEntity, HoldingsEntity holdingsEntity, Integer owningInstitutionId, String holdingsCallNumber, String holdingsCallNumberType, RecordType itemRecordType,Date currentDate) {
        StringBuilder errorMessage = new StringBuilder();
        Map<String, Object> map = new HashMap<>();
        ItemEntity itemEntity = new ItemEntity();

        ReportEntity reportEntity = new ReportEntity();
        reportEntity.setFileName(xmlRecordEntity.getXmlFileName());
        reportEntity.setInstitutionName(institutionName);
        reportEntity.setType(RecapCommonConstants.FAILURE);
        reportEntity.setCreatedDate(new Date());

        String itemBarcode = getMarcUtil().getDataFieldValue(itemRecordType, "876", null, null, "p");
        if (StringUtils.isNotBlank(itemBarcode)) {
            itemEntity.setBarcode(itemBarcode);
        } else {
            errorMessage.append("Item Barcode cannot be null");
        }
        String customerCode = getMarcUtil().getDataFieldValue(itemRecordType, "900", null, null, "b");
        if (StringUtils.isNotBlank(customerCode)) {
            itemEntity.setCustomerCode(customerCode);
        } else {
            errorMessage.append("\n");
            errorMessage.append("Customer Code cannot be null");
        }
        itemEntity.setCallNumber(holdingsCallNumber);
        itemEntity.setCallNumberType(holdingsCallNumberType);
        itemEntity.setItemAvailabilityStatusId((Integer) itemStatusMap.get("Available"));
        String copyNumber = getMarcUtil().getDataFieldValue(itemRecordType, "876", null, null, "t");
        if (StringUtils.isNoneBlank(copyNumber) && NumberUtils.isCreatable(copyNumber)) {
            itemEntity.setCopyNumber(Integer.valueOf(copyNumber));
        }
        if (owningInstitutionId != null) {
            itemEntity.setOwningInstitutionId(owningInstitutionId);
        } else {
            errorMessage.append("\n");
            errorMessage.append("Owning Institution Id cannot be null");
        }
        String collectionGroupCode = getMarcUtil().getDataFieldValue(itemRecordType, "900", null, null, "a");
        if (StringUtils.isNotBlank(collectionGroupCode) && collectionGroupMap.containsKey(collectionGroupCode)) {
            itemEntity.setCollectionGroupId((Integer) collectionGroupMap.get(collectionGroupCode));
        } else {
            itemEntity.setCollectionGroupId((Integer) collectionGroupMap.get("Open"));
        }
        itemEntity.setCreatedDate(currentDate);
        itemEntity.setCreatedBy("etl");
        itemEntity.setLastUpdatedDate(currentDate);
        itemEntity.setLastUpdatedBy("etl");
        itemEntity.setCatalogingStatus(RecapCommonConstants.COMPLETE_STATUS);

        String useRestrictions = getMarcUtil().getDataFieldValue(itemRecordType, "876", null, null, "h");
        if (StringUtils.isNotBlank(useRestrictions) && ("In Library Use".equalsIgnoreCase(useRestrictions) || "Supervised Use".equalsIgnoreCase(useRestrictions))) {
            itemEntity.setUseRestrictions(useRestrictions);
        }

        itemEntity.setVolumePartYear(getMarcUtil().getDataFieldValue(itemRecordType, "876", null, null, "3"));
        String owningInstitutionItemId = getMarcUtil().getDataFieldValue(itemRecordType, "876", null, null, "a");
        if (StringUtils.isNotBlank(owningInstitutionItemId)) {
            itemEntity.setOwningInstitutionItemId(owningInstitutionItemId);
        } else {
            errorMessage.append("\n");
            errorMessage.append("Item Owning Institution Id cannot be null");
        }
        itemEntity.setHoldingsEntities(Arrays.asList(holdingsEntity));

        List<ReportDataEntity> reportDataEntities = null;
        if (errorMessage.toString().length() > 1) {
            reportDataEntities = getDbReportUtil().generateBibHoldingsAndItemsFailureReportEntities(bibliographicEntity, holdingsEntity, itemEntity);
            ReportDataEntity errorReportDataEntity = new ReportDataEntity();
            errorReportDataEntity.setHeaderName(RecapCommonConstants.ERROR_DESCRIPTION);
            errorReportDataEntity.setHeaderValue(errorMessage.toString());
            reportDataEntities.add(errorReportDataEntity);
        }
        if(!CollectionUtils.isEmpty(reportDataEntities)) {
            reportEntity.addAll(reportDataEntities);
            map.put("itemReportEntity", reportEntity);
        }
        map.put("itemEntity", itemEntity);
        return map;
    }

    private String getOwningInstitutionBibId(BibRecord bibRecord, Bib bib) {
        return StringUtils.isBlank(bib.getOwningInstitutionBibId()) ? getControlFieldValue001(bibRecord) : bib.getOwningInstitutionBibId();
    }

    private String getControlFieldValue001(BibRecord bibRecord) {
        RecordType marcRecord = bibRecord.getBib().getContent().getCollection().getRecord().get(0);
        return getMarcUtil().getControlFieldValue(marcRecord, "001");
    }

    /**
     * Gets bib record.
     *
     * @return the bib record
     */
    public BibRecord getBibRecord() {
        return bibRecord;
    }

    /**
     * Sets bib record.
     *
     * @param bibRecord the bib record
     */
    public void setBibRecord(BibRecord bibRecord) {
        this.bibRecord = bibRecord;
    }

    /**
     * Gets institution entities map.
     *
     * @return the institution entities map
     */
    public Map<String, Integer> getInstitutionEntitiesMap() {
        return institutionEntitiesMap;
    }

    /**
     * Sets institution entities map.
     *
     * @param institutionEntitiesMap the institution entities map
     */
    public void setInstitutionEntitiesMap(Map institutionEntitiesMap) {
        this.institutionEntitiesMap = institutionEntitiesMap;
    }

    /**
     * Gets institution name.
     *
     * @return the institution name
     */
    public String getInstitutionName() {
        return institutionName;
    }

    /**
     * Sets institution name.
     *
     * @param institutionName the institution name
     */
    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    /**
     * Gets item status map.
     *
     * @return the item status map
     */
    public Map getItemStatusMap() {
        return itemStatusMap;
    }

    /**
     * Sets item status map.
     *
     * @param itemStatusMap the item status map
     */
    public void setItemStatusMap(Map itemStatusMap) {
        this.itemStatusMap = itemStatusMap;
    }

    /**
     * Gets collection group map.
     *
     * @return the collection group map
     */
    public Map getCollectionGroupMap() {
        return collectionGroupMap;
    }

    /**
     * Sets collection group map.
     *
     * @param collectionGroupMap the collection group map
     */
    public void setCollectionGroupMap(Map collectionGroupMap) {
        this.collectionGroupMap = collectionGroupMap;
    }

    /**
     * Gets xml record entity.
     *
     * @return the xml record entity
     */
    public XmlRecordEntity getXmlRecordEntity() {
        return xmlRecordEntity;
    }

    /**
     * Sets xml record entity.
     *
     * @param xmlRecordEntity the xml record entity
     */
    public void setXmlRecordEntity(XmlRecordEntity xmlRecordEntity) {
        this.xmlRecordEntity = xmlRecordEntity;
    }

    /**
     * Gets marc util.
     *
     * @return the marc util
     */
    public MarcUtil getMarcUtil() {
        if (null == marcUtil) {
            marcUtil = new MarcUtil();
        }
        return marcUtil;
    }

    /**
     * Gets db report util.
     *
     * @return the db report util
     */
    public DBReportUtil getDbReportUtil() {
        return dbReportUtil;
    }

    /**
     * Sets db report util.
     *
     * @param dbReportUtil the db report util
     */
    public void setDbReportUtil(DBReportUtil dbReportUtil) {
        this.dbReportUtil = dbReportUtil;
    }
}
