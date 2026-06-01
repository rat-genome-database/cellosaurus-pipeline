package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Association;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * @author mtutaj
 * @since 4/16/12
 * collection of associations for cell lines
 */
public class AssociationCollection {

    private final Logger log = LogManager.getLogger("status");

    // incoming associations keyed explicitly (Association.equals omits assocSubType, so we cannot diff by the object)
    private final Map<String, Association> incoming = new HashMap<>();

    private String computeAssocKey(Association a) {
        return a.getDetailRgdId()+"|"+a.getMasterRgdId()+"|"+a.getAssocType()+"|"+a.getAssocSubType();
    }

    public void addIncoming(Association assoc) {
        incoming.put(computeAssocKey(assoc), assoc);
    }

    public void qc(Dao dao, String source) throws Exception {

        // load in-rgd associations for every assoc type present among incoming
        Set<String> assocTypes = new HashSet<>();
        for( Association a: incoming.values() ) {
            assocTypes.add(a.getAssocType());
        }
        Map<String, Association> inRgd = new HashMap<>();
        for( String assocType: assocTypes ) {
            for( Association a: dao.getAssociations(assocType, source) ) {
                inRgd.put(computeAssocKey(a), a);
            }
        }

        Collection<String> forInsert = CollectionUtils.subtract(incoming.keySet(), inRgd.keySet());
        if( !forInsert.isEmpty() ) {
            for( String key: forInsert ) {
                dao.insertAssociation(incoming.get(key));
            }
            log.info("ASSOC_INSERTED: "+forInsert.size());
        }

        Collection<String> forDelete = CollectionUtils.subtract(inRgd.keySet(), incoming.keySet());
        if( !forDelete.isEmpty() ) {
            for( String key: forDelete ) {
                dao.deleteAssociation(inRgd.get(key));
            }
            log.info("ASSOC_DELETED: "+forDelete.size());
        }

        // the rest of the incoming associations are already in RGD
        int matching = incoming.size() - forInsert.size();
        if( matching!=0 ) {
            log.info("ASSOC_MATCHED: "+matching);
        }
    }
}
