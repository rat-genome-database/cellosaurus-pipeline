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

    private final Logger log = LogManager.getLogger("status");

    private final Set<XdbId> incoming = new HashSet<>();

    public void addIncoming(XdbId xdbId) {
        incoming.add(xdbId);
    }

    public void qc(Dao dao, String srcPipeline) throws Exception {

        List<XdbId> xdbIdsInRgd = dao.getXdbIds(srcPipeline);

        Collection<XdbId> forInsert = CollectionUtils.subtract(incoming, xdbIdsInRgd);
        if( !forInsert.isEmpty() ) {
            dao.insertXdbIds(forInsert);
            log.info("XDB_IDS_INSERTED: "+forInsert.size());
        }

        Collection<XdbId> forDelete = CollectionUtils.subtract(xdbIdsInRgd, incoming);
        if( !forDelete.isEmpty() ) {
            dao.deleteXdbIds(forDelete);
            log.info("XDB_IDS_DELETED: "+forDelete.size());
        }

        // the rest of the incoming xdb ids are already in RGD
        int matching = incoming.size() - forInsert.size();
        if( matching!=0 ) {
            log.info("XDB_IDS_MATCHED: "+matching);
        }
    }
}
