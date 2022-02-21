package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.datamodel.Association;
import edu.mcw.rgd.datamodel.CellLine;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.process.CounterPool;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author mtutaj
 * @since 01/28/2020
 */
public class Main {

    private Dao dao = new Dao();
    private String version;
    private String sourcePipeline;
    private String oboFile;
    private Parser parser;

    Logger log = LogManager.getLogger("status");


    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        Main instance = (Main) (bf.getBean("main"));

        boolean runAnnotator = false;
        for( String arg: args ) {
            switch (arg) {
                case "--annotator":
                    runAnnotator = true;
                    break;
            }
        }

        try {
            if( runAnnotator ) {
                Annotator annotator = (Annotator) (bf.getBean("annotator"));
                annotator.run();
            } else {
                instance.run();
            }
        }catch (Exception e) {
            Utils.printStackTrace(e, instance.log);
            throw e;
        }
    }

    public void run() throws Exception {

        long time0 = System.currentTimeMillis();

        log.info(getVersion());
        log.info("   "+dao.getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("   started at "+sdt.format(new Date(time0)));

        String localFile = downloadCellosaurusOboFile();

        CounterPool counters = new CounterPool();

        List<DataRecord> incomingRecords = getParser().parse(localFile, counters, getSourcePipeline());

        // compare incoming CellLine objects against DB and load the changes
        List<CellLine> inRgdRecords = dao.getCellLines(getSourcePipeline());

        Map<String, CellLine> inRgdMap = qcCellLines(incomingRecords, inRgdRecords);

        qcAndLoadAliases(incomingRecords);
        qcAndLoadAssociations(incomingRecords, inRgdMap);
        qcAndLoadXdbIds(incomingRecords);

        NciCollection.getInstance().qc(dao, counters);

        log.info(counters.dumpAlphabetically());

        log.info("OK -- time elapsed: "+Utils.formatElapsedTime(time0, System.currentTimeMillis()));
    }

    Map<String, CellLine> qcCellLines(List<DataRecord> incomingRecords, List<CellLine> inRgdRecords) throws Exception {

        List<CellLine> toBeDeletedCellLines = new ArrayList<>();

        // determine to-be-inserted/deleted cell lines by looking at the symbol
        log.info("CELL LINES INCOMING: "+incomingRecords.size());

        Map<String, DataRecord> incoming = new HashMap<>();
        for( DataRecord rec: incomingRecords ) {
            // fixup species type key (negative values were used by the parser)
            if( rec.getSpeciesTypeKey()<0 ) {
                rec.setSpeciesTypeKey(0);
            }
            incoming.put(rec.getSymbol(), rec);
        }
        if( incoming.size()!=incomingRecords.size() ) {
            throw new Exception("duplicate symbol for incoming");
        }

        Map<String, CellLine> inRgd = new HashMap<>();
        for( CellLine cl: inRgdRecords ) {
            CellLine oldCellLine = inRgd.put(cl.getSymbol(), cl);
            if( oldCellLine!=null ) {
                toBeDeletedCellLines.add(oldCellLine);
            }
        }

        Collection<String> toBeInserted = CollectionUtils.subtract(incoming.keySet(), inRgd.keySet());
        Collection<String> toBeDeleted = CollectionUtils.subtract(inRgd.keySet(), incoming.keySet());
        Collection<String> matching = CollectionUtils.intersection(inRgd.keySet(), incoming.keySet());
        log.info("CELL LINES MATCHING: "+matching.size());

        if( toBeInserted.size()!=0 ) {
            List<CellLine> toBeInsertedCellLines = new ArrayList<>();
            for( String symbol: toBeInserted ) {
                toBeInsertedCellLines.add(incoming.get(symbol));
            }
            dao.insertCellLines(toBeInsertedCellLines);

            log.info("CELL LINES INSERTED: " + toBeInsertedCellLines.size());
        }

        if( toBeDeleted.size()!=0 || toBeDeletedCellLines.size()!=0 ) {
            for( String symbol: toBeDeleted ) {
                toBeDeletedCellLines.add(inRgd.get(symbol));
            }
            dao.deleteCellLines(toBeDeletedCellLines);

            log.info("CELL LINES DELETED: " + toBeDeletedCellLines.size());
        }

        // handle matching data
        int rgdIdsUpdated = 0;
        int cellLinesUpdated = 0;

        for( String symbol: matching ) {
            CellLine clIncoming = incoming.get(symbol);
            CellLine clInRgd = inRgd.get(symbol);

            // RGD_IDS fields
            clIncoming.setRgdId(clInRgd.getRgdId());

            if( clIncoming.getSpeciesTypeKey()!=clInRgd.getSpeciesTypeKey()
             || !clIncoming.getObjectStatus().equals(clInRgd.getObjectStatus())
             || clIncoming.getObjectKey()!=clInRgd.getObjectKey() ) {

                dao.updateRgdId(clIncoming.getRgdId(), clIncoming.getObjectKey(), clIncoming.getSpeciesTypeKey(), clIncoming.getObjectStatus());

                clInRgd.setSpeciesTypeKey(clIncoming.getSpeciesTypeKey());
                clInRgd.setObjectStatus(clIncoming.getObjectStatus());
                clInRgd.setObjectKey(clIncoming.getObjectKey());

                rgdIdsUpdated++;
            }

            // genomic elements fields
            String cl1 = clIncoming.dump("|");
            String cl2 = clInRgd.dump("|");

            boolean isEqual = cl1.equals(cl2);
            if( !isEqual ) {
                dao.updateCellLine(clInRgd, clIncoming);
                cellLinesUpdated++;
            }
        }

        if( cellLinesUpdated!=0 ) {
            log.info("CELL LINES UPDATED: " + cellLinesUpdated);
        }
        if( rgdIdsUpdated!=0 ) {
            log.info("CELL LINES RGD IDS UPDATED: " + rgdIdsUpdated);
        }

        return inRgd;
    }

    void qcAndLoadAliases( List<DataRecord> incomingRecords ) throws Exception {

        AliasCollection aliases = AliasCollection.getInstance();

        for( DataRecord rec: incomingRecords ) {

            for( Alias a: rec.getAliases() ) {
                a.setRgdId(rec.getRgdId());
                aliases.addIncoming(a);
            }
        }

        aliases.qc(dao);
    }

    void qcAndLoadAssociations( List<DataRecord> incomingRecords, Map<String, CellLine> inRgdMap ) throws Exception {

        AssociationCollection assocs = AssociationCollection.getInstance();

        for( DataRecord rec: incomingRecords ) {

            for( java.util.Map.Entry<String,String> entry: rec.getGeneAssocs().entrySet() ) {
                String assocInfo = entry.getKey();
                String assocSubType = entry.getValue();

                int detailRgdId = 0;

                // sample info: HGNC; 5173; HRAS (with p.Gly12Val)
                if( assocInfo.contains("HGNC; ") ) {
                    // extract HGNC id
                    int startPos = assocInfo.indexOf("HGNC; ") + 6;
                    int endPos = assocInfo.indexOf("; ", startPos);
                    if( endPos<0 ) {
                        log.warn("assoc info: unexpected HGNC: "+assocInfo);
                    } else {
                        String hgncId = assocInfo.substring(startPos, endPos);
                        detailRgdId = dao.getGeneRgdIdByXdbId(XdbId.XDB_KEY_HGNC, "HGNC:" + hgncId);
                    }

                // sample info: UniProtKB; P00552; Transposon Tn5 neo
                } else if( assocInfo.contains("UniProtKB; ") ) {

                    // extract uniprot id
                    int startPos = assocInfo.indexOf("UniProtKB; ") + 11;
                    int endPos = assocInfo.indexOf("; ", startPos);
                    String uniProtId = assocInfo.substring(startPos, endPos);
                    detailRgdId = dao.getGeneRgdIdByXdbId(XdbId.XDB_KEY_UNIPROT, uniProtId);

                    // sample info: Method=KO mouse; MGI; MGI:97306; Nf1
                } else if( assocInfo.contains("MGI; MGI:") ) {

                    // extract MGI ID
                    int startPos = assocInfo.indexOf("MGI; MGI:") + 5;
                    int endPos = assocInfo.indexOf("; ", startPos);
                    String mgiId = assocInfo.substring(startPos, endPos);
                    detailRgdId = dao.getGeneRgdIdByXdbId(XdbId.XDB_KEY_MGD, mgiId);

                    // sample info: VGNC; 39653; Dog CSF1R
                } else if( assocInfo.contains("VGNC; ") ) {

                    // extract VGNC id
                    int startPos = assocInfo.indexOf("VGNC; ") + 6;
                    int endPos = assocInfo.indexOf("; ", startPos);
                    String vgncId = assocInfo.substring(startPos, endPos);
                    detailRgdId = dao.getGeneRgdIdByXdbId(127, "VGNC:"+vgncId);

                    // RGD; 2425; Csf1r
                } else if( assocInfo.startsWith("RGD; ") ) {

                    // extract RGD id
                    int startPos = 5;
                    int endPos = assocInfo.indexOf("; ", startPos);
                    String rgdId = assocInfo.substring(startPos, endPos);
                    detailRgdId = dao.getGeneRgdIdByXdbId(63, "RGD:"+rgdId);

                    // ignored assocs
                } else {
                    if( assocInfo.startsWith("tdTomato; ")
                     || assocInfo.startsWith("Method=Targeted integration; ")
                     || assocInfo.startsWith("Method=Homologous recombination; ")
                     || assocInfo.startsWith("FlyBase; ")
                     || assocInfo.startsWith("Lucia luciferase; ") ) {

                    } else {
                        log.debug("ASSOC QC: unparsed assoc info: [" + assocInfo + "]");
                    }
                }

                if( detailRgdId != 0 ) {
                    Association a = new Association();
                    a.setAssocType("cell_line_to_gene");
                    a.setAssocSubType(assocSubType);
                    a.setMasterRgdId(rec.getRgdId());
                    a.setDetailRgdId(detailRgdId);
                    a.setSrcPipeline(getSourcePipeline());
                    assocs.addIncoming(a);
                }
            }

            for( java.util.Map.Entry<String,String> entry: rec.getCellLineAssocs().entrySet() ) {
                String cellLineSymbol = entry.getKey();
                String assocSubType = entry.getValue();

                CellLine cl = inRgdMap.get(cellLineSymbol);
                if( cl==null ) {
                    log.warn("cannot find a cell line with symbol "+cellLineSymbol);
                } else {
                    Association a = new Association();
                    a.setAssocType("cell_line_to_cell_line");
                    a.setAssocSubType(assocSubType);
                    a.setMasterRgdId(rec.getRgdId());
                    a.setDetailRgdId(cl.getRgdId());
                    a.setSrcPipeline(getSourcePipeline());
                    assocs.addIncoming(a);
                }
            }
        }

        assocs.qc(dao, getSourcePipeline());
    }

    void qcAndLoadXdbIds( List<DataRecord> incomingRecords ) throws Exception {

        XdbIdCollection xdbIds = XdbIdCollection.getInstance();

        for( DataRecord rec: incomingRecords ) {

            for( XdbId id: rec.getXdbIds() ) {
                id.setRgdId(rec.getRgdId());
                xdbIds.addIncoming(id);
            }
        }

        xdbIds.qc(dao, getSourcePipeline());
    }

    String downloadCellosaurusOboFile() throws Exception {
        FileDownloader downloader = new FileDownloader();
        downloader.setExternalFile(getOboFile());
        downloader.setLocalFile("data/cellosaurus.obo");
        downloader.setUseCompression(true);
        downloader.setPrependDateStamp(true);
        return downloader.downloadNew();
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setSourcePipeline(String sourcePipeline) {
        this.sourcePipeline = sourcePipeline;
    }

    public String getSourcePipeline() {
        return sourcePipeline;
    }

    public void setOboFile(String oboFile) {
        this.oboFile = oboFile;
    }

    public String getOboFile() {
        return oboFile;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }

    public Parser getParser() {
        return parser;
    }
}

