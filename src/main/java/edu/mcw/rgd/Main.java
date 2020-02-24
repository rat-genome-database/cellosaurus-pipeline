package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.datamodel.CellLine;
import edu.mcw.rgd.process.CounterPool;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.log4j.Logger;
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

    Logger log = Logger.getLogger("status");


    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        Main instance = (Main) (bf.getBean("main"));

        try {
            instance.run();
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

        qcCellLines(incomingRecords, inRgdRecords);

        qcAndLoadAliases(incomingRecords);
        qcAndLoadAssociations();
        qcAndLoadXdbIds();

        log.info("OK -- time elapsed: "+Utils.formatElapsedTime(time0, System.currentTimeMillis()));
    }

    void qcCellLines(List<DataRecord> incomingRecords, List<CellLine> inRgdRecords) throws Exception {

        List<CellLine> toBeDeletedCellLines = new ArrayList<>();

        // determine to-be-inserted/deleted cell lines by looking at the symbol
        log.info("CELL LINES INCOMING: "+incomingRecords.size());

        Map<String, DataRecord> incoming = new HashMap<>();
        for( DataRecord rec: incomingRecords ) {
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

    void qcAndLoadAssociations() throws Exception {
        throw new Exception("TODO qc associations");
    }

    void qcAndLoadXdbIds() throws Exception {
        throw new Exception("TODO qc xdb ids");
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

