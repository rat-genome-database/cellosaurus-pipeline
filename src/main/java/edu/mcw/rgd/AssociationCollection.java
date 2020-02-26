package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Association;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mtutaj
 * @since 4/16/12
 * collection of associations for cell lines
 */
public class AssociationCollection {

    // THREAD SAFE SINGLETON -- start
    // private instance, so that it can be accessed by only by getInstance() method
    private static AssociationCollection instance;

    private AssociationCollection() {
        // private constructor
    }

    //synchronized method to control simultaneous access
    synchronized public static AssociationCollection getInstance() {
        if (instance == null) {
            // if instance is null, initialize
            instance = new AssociationCollection();
        }
        return instance;
    }
    // THREAD SAFE SINGLETON -- end


    Logger log = Logger.getLogger("status");

    private final Map<String, Association> incoming = new ConcurrentHashMap<>();
    private final Map<String, Object> assocTypes = new ConcurrentHashMap<>();

    private String computeAssocKey(Association a) {
        return a.getDetailRgdId()+"|"+a.getMasterRgdId()+"|"+a.getAssocType()+"|"+a.getAssocSubType();
    }

    public void addIncoming(Association assoc) throws Exception {
        incoming.put(computeAssocKey(assoc), assoc);
        assocTypes.put(assoc.getAssocType(), "");
    }

    synchronized public void qc(Dao dao, String source) throws Exception {

        List<Association> inRgdAssocs = new ArrayList<>();
        for( String assocType: assocTypes.keySet() ) {
            inRgdAssocs.addAll(dao.getAssociations(assocType, source));
        }

        // build assoc map for in-rgd assocs
        Map<String, Association> inRgdAssocMap = new HashMap<>();
        for( Association a: inRgdAssocs ) {
            inRgdAssocMap.put(computeAssocKey(a), a);
        }

        // determine new associations for insertion
        Collection<String> forInsert = CollectionUtils.subtract(incoming.keySet(), inRgdAssocMap.keySet());

        // determine new associations for deletion
        Collection<String> forDelete = CollectionUtils.subtract(inRgdAssocMap.keySet(), incoming.keySet());

        Collection<String> matching = CollectionUtils.intersection(inRgdAssocMap.keySet(), incoming.keySet());


        // update the database
        if( !forInsert.isEmpty() ) {
            for( String assocKey: forInsert ) {
                dao.insertAssociation(incoming.get(assocKey));
            }
            log.info("ASSOC_INSERTED: "+forInsert.size());
        }

        if( !forDelete.isEmpty() ) {
            for( String assocKey: forDelete ) {
                dao.deleteAssociation(inRgdAssocMap.get(assocKey));
            }
            log.info("ASSOC_DELETED: "+forDelete.size());
        }

        int matchingAssocs = matching.size();
        if( matchingAssocs!=0 ) {
            log.info("ASSOC_MATCHED: "+matchingAssocs);
        }
    }
}
