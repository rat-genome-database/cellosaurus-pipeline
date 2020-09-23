package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;

import java.text.SimpleDateFormat;
import java.util.*;

public class Annotator {

    private String version;
    private int createdBy;
    private int refRgdId;
    private String evidenceCode;
    private String sourcePipeline;
    private String staleAnnotThreshold;

    Logger log = Logger.getLogger("annot");
    Dao dao = new Dao();
    Date dtStart;
    AnnotCache annotCache = new AnnotCache();

    public void run() throws Exception {

        dtStart = new Date();
        log.info(getVersion());
        log.info("   "+dao.getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("   started at "+sdt.format(dtStart));

        int inRgdAnnotCount = annotCache.loadAnnotations(dao, getRefRgdId());
        log.info(inRgdAnnotCount+" in-rgd annotations preloaded");

        List<Annotation> incomingAnnots = loadIncomingAnnots();
        log.info(incomingAnnots.size()+" incoming annotations");

        int annotsInserted = 0;
        for( Annotation a: incomingAnnots ) {
            if( annotCache.insertOrUpdateAnnotation(a, dao) != 0 ) {
                annotsInserted++;
            }
        }
        log.info(annotsInserted+" inserted annotations");

        annotCache.updateAnnotations(dao);

        log.info(annotCache.getCountOfModifiedAnnots()+" modified annotation notes");
        log.info(annotCache.getCountOfUpToDateAnnots()+" up-to-date annotations");

        int staleAnnotThreshold = 0;
        if( getStaleAnnotThreshold().endsWith("%") ) {
            staleAnnotThreshold = Integer.parseInt(getStaleAnnotThreshold().substring(0, getStaleAnnotThreshold().length()-1));
        }
        annotCache.deleteStaleAnnotations(dao, getRefRgdId(), staleAnnotThreshold, dtStart, log);

        log.info("=== OK === elapsed "+Utils.formatElapsedTime(dtStart.getTime(), System.currentTimeMillis()));
    }

    List<Annotation> loadIncomingAnnots() throws Exception {

        // cell line rgd id to map of [do term acc -> NCI and/or ORDo ids]
        Map<Integer, Map<String, String>> results = new HashMap<>();
        processData(results, "NCI:", 74);
        processData(results, "ORDO:", 62);

        List<Annotation> annots = new ArrayList<>();

        for( Map.Entry<Integer, Map<String, String>> entry: results.entrySet() ) {
            int rgdId = entry.getKey();
            Map<String, String> doAccMap = entry.getValue();
            for( Map.Entry<String, String> entry1: doAccMap.entrySet() ) {
                String doTermAcc = entry1.getKey();
                String doTermName = dao.getTermByAcc(doTermAcc).getTerm();
                String notes = entry1.getValue();

                Annotation a = new Annotation();
                a.setCreatedBy(getCreatedBy());
                a.setLastModifiedBy(getCreatedBy());
                a.setCreatedDate(new Date());
                a.setLastModifiedDate(a.getCreatedDate());
                a.setAnnotatedObjectRgdId(rgdId);
                a.setAspect("D");
                a.setDataSrc(getSourcePipeline());
                a.setEvidence(getEvidenceCode());
                a.setRefRgdId(getRefRgdId());
                a.setNotes(notes);
                a.setTermAcc(doTermAcc);
                a.setTerm(doTermName);
                a.setRgdObjectKey(RgdId.OBJECT_KEY_CELL_LINES);
                a.setObjectName("RGD:"+rgdId);
                a.setObjectSymbol("RGD:"+rgdId);
                annots.add(a);
            }
        }
        return annots;
    }

    void processData(Map<Integer, Map<String, String>> results, String synonymPattern, int xdbKey) throws Exception {

        // map of NCI/ORDO acc to DOID acc
        Map<String, String> doTermMap = dao.getRdoTermsWithSynonymPattern(synonymPattern+"%");

        // xdb ids for cell lines and given xdb key (NCI or ORDO)
        List<XdbId> xdbIds = dao.getXdbIds(getSourcePipeline(), xdbKey);

        // cell line rgd id to map of [do term acc -> NCI and/or ORDo ids]
        for (XdbId id : xdbIds) {
            String nciAcc = synonymPattern + id.getAccId();
            String doTermAcc = doTermMap.get(nciAcc);
            if (doTermAcc == null) {
                continue;
            }

            Map<String, String> doAccMap = results.get(id.getRgdId());
            if (doAccMap == null) {
                doAccMap = new HashMap<>();
                results.put(id.getRgdId(), doAccMap);
            }

            String nciOrdoAccStr = doAccMap.get(doTermAcc);
            if (nciOrdoAccStr == null) {
                doAccMap.put(doTermAcc, nciAcc);
            } else {
                TreeSet<String> nciOrdoAccSet = new TreeSet<>(Arrays.asList(nciOrdoAccStr.split(",")));
                nciOrdoAccSet.add(nciAcc);
                String nciOrdoAccStr2 = Utils.concatenate(nciOrdoAccSet, ",");
                doAccMap.put(doTermAcc, nciOrdoAccStr2);
            }
        }
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setRefRgdId(int refRgdId) {
        this.refRgdId = refRgdId;
    }

    public int getRefRgdId() {
        return refRgdId;
    }

    public void setEvidenceCode(String evidenceCode) {
        this.evidenceCode = evidenceCode;
    }

    public String getEvidenceCode() {
        return evidenceCode;
    }

    public void setSourcePipeline(String sourcePipeline) {
        this.sourcePipeline = sourcePipeline;
    }

    public String getSourcePipeline() {
        return sourcePipeline;
    }

    public void setStaleAnnotThreshold(String staleAnnotThreshold) {
        this.staleAnnotThreshold = staleAnnotThreshold;
    }

    public String getStaleAnnotThreshold() {
        return staleAnnotThreshold;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}

