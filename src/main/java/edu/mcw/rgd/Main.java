package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.CellLine;
import edu.mcw.rgd.datamodel.RgdId;
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

        List<DataRecord> dataRecords = getParser().parse(localFile);

        // convert DataRecord objects into CellLine objects
        List<CellLine> incomingRecords = new ArrayList<>(dataRecords.size());
        for( DataRecord rec: dataRecords ) {
            incomingRecords.add( qc(rec) );
        }
        log.info("CELL LINES INCOMING: "+incomingRecords.size());

        // compare incoming CellLine objects against DB and load the changes
        List<CellLine> inRgdRecords = dao.getCellLines(getSourcePipeline());

        Collection<CellLine> toBeInserted = CollectionUtils.subtract(incomingRecords, inRgdRecords);
        Collection<CellLine> toBeDeleted = CollectionUtils.subtract(inRgdRecords, incomingRecords);
        Collection<CellLine> matching = CollectionUtils.intersection(inRgdRecords, incomingRecords);

        log.info("CELL LINES MATCHING: "+matching.size());
        if( toBeInserted.size()!=0 ) {
            log.info("CELL LINES INSERTED: " + toBeInserted.size());
            dao.insertCellLines(toBeInserted);
        }
        if( toBeDeleted.size()!=0 ) {
            log.info("CELL LINES DELETED: " + toBeDeleted.size());
            dao.deleteCellLines(toBeDeleted);
        }

        log.info("OK -- time elapsed: "+Utils.formatElapsedTime(time0, System.currentTimeMillis()));
    }

    String downloadCellosaurusOboFile() throws Exception {
        FileDownloader downloader = new FileDownloader();
        downloader.setExternalFile(getOboFile());
        downloader.setLocalFile("data/cellosaurus.obo");
        downloader.setUseCompression(true);
        downloader.setPrependDateStamp(true);
        return downloader.downloadNew();
    }

    CellLine qc( DataRecord rec ) throws Exception {

        CellLine recIncoming = new CellLine();
        recIncoming.setSrcPipeline(getSourcePipeline());
        recIncoming.setSymbol(rec.getSymbol());
        recIncoming.setName(rec.getName());
        recIncoming.setGender(rec.getGender());
        recIncoming.setObjectType(rec.getCellLineType());
        recIncoming.setObjectKey(RgdId.OBJECT_KEY_CELL_LINES);
        recIncoming.setObjectStatus("ACTIVE");
        recIncoming.setSoAccId("CL:0000010");

        return recIncoming;
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

