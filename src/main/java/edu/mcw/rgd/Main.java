package edu.mcw.rgd;

import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
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

        List<DataRecord> incomingRecords = downloadAndParse();
        log.info("INCOMING CELL LINES: "+incomingRecords.size());

        log.info("OK -- time elapsed: "+Utils.formatElapsedTime(time0, System.currentTimeMillis()));
    }

    List<DataRecord> downloadAndParse() throws Exception {

        String localFile = downloadCellosaurusOboFile();

        List<DataRecord> dataRecords = getParser().parse(localFile);


        return dataRecords;
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

