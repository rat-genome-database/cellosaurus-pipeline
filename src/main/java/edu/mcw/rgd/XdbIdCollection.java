package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.XdbId;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XdbIdCollection {

    // THREAD SAFE SINGLETON -- start
    // private instance, so that it can be accessed by only by getInstance() method
    private static XdbIdCollection instance;

    private XdbIdCollection() {
        // private constructor
    }

    //synchronized method to control simultaneous access
    synchronized public static XdbIdCollection getInstance() {
        if (instance == null) {
            // if instance is null, initialize
            instance = new XdbIdCollection();
        }
        return instance;
    }
    // THREAD SAFE SINGLETON -- end


    Logger log = LogManager.getLogger("status");

    private final Set<XdbId> incoming = new HashSet<>();

    public void addIncoming(XdbId xdbId) throws Exception {

        // there is only one instance of this class
        synchronized (incoming) {
            incoming.add(xdbId);
        }
    }

    synchronized public void qc(Dao dao, String srcPipeline) throws Exception {

        List<XdbId> xdbIdsInRgd = dao.getXdbIds(srcPipeline);

        Collection<XdbId> forInsert = CollectionUtils.subtract(incoming, xdbIdsInRgd);
        Collection<XdbId> forDelete = CollectionUtils.subtract(xdbIdsInRgd, incoming);
        Collection<XdbId> matching = CollectionUtils.intersection(incoming, xdbIdsInRgd);

        if( !forInsert.isEmpty() ) {
            dao.insertXdbIds(forInsert);
            log.info("XDB_IDS_INSERTED: "+forInsert.size());
        }

        if( !forDelete.isEmpty() ) {
            dao.deleteXdbIds(forDelete);
            log.info("XDB_IDS_DELETED: "+forDelete.size());
        }

        int matchingAliases = matching.size();
        if( matchingAliases!=0 ) {
            log.info("XDB_IDS_MATCHED: "+matchingAliases);
        }
    }
}

