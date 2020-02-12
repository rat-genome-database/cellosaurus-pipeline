package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Association;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mtutaj
 * @since 4/16/12
 * collection of associations for cell lines:
 *    derived_from_cell_line
 */
abstract public class AssociationCollection {

    abstract public String getAssocType(); // f.e. "promoter_to_gene"
    abstract public String getLogPrefix(); // f.e. "GENE_ASSOC"
    abstract public String getLogName(); // f.e. "assoc_genes"

    Logger log = Logger.getLogger("status");

    private final Map<Association, Object> incoming = new ConcurrentHashMap<>();

    public void addIncoming(Association assoc) throws Exception {
        incoming.put(assoc, assoc);
    }

    synchronized public void qc(Dao dao, String[] sources) throws Exception {

        /*
        List<Association> inRgdAssocs = new ArrayList<>();
        for( String source: sources ) {
            inRgdAssocs.addAll(dao.getAssociations(getAssocType(), source));
        }

        Set<Association> incomingAssocs = incoming.keySet();

        // determine new associations for insertion
        Collection<Association> forInsert = CollectionUtils.subtract(incomingAssocs, inRgdAssocs);

        // determine new associations for deletion
        Collection<Association> forDelete = CollectionUtils.subtract(inRgdAssocs, incomingAssocs);

        Collection<Association> matching = CollectionUtils.intersection(inRgdAssocs, incomingAssocs);


        // update the database
        if( !forInsert.isEmpty() ) {
            dao.insertAssociations(forInsert, getLogName());
            log.info(getLogPrefix()+"_INSERTED: "+forInsert.size());
        }

        if( !forDelete.isEmpty() ) {
            dao.deleteAssociations(forDelete, getLogName());
            log.info(getLogPrefix()+"_DELETED: "+forDelete.size());
        }

        int matchingAssocs = matching.size();
        if( matchingAssocs!=0 ) {
            log.info(getLogPrefix()+"_MATCHED: "+matchingAssocs);
        }
        */
    }
}
