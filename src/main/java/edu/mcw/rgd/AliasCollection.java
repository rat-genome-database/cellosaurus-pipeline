package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.process.Utils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AliasCollection {

    // THREAD SAFE SINGLETON -- start
    // private instance, so that it can be accessed by only by getInstance() method
    private static AliasCollection instance;

    private AliasCollection() {
        // private constructor
    }

    //synchronized method to control simultaneous access
    synchronized public static AliasCollection getInstance() {
        if (instance == null) {
            // if instance is null, initialize
            instance = new AliasCollection();
        }
        return instance;
    }
    // THREAD SAFE SINGLETON -- end


    Logger log = Logger.getLogger("status");

    private final Set<Alias> incoming = new HashSet<>();

    public void addIncoming(Alias alias) throws Exception {

        // there is only one instance of this class
        synchronized (incoming) {
            incoming.add(alias);
        }
    }

    /// we load only new aliases; old aliases are never deleted -- that's our policy
    synchronized public void qc(Dao dao) throws Exception {

        throw new Exception("TODO:");
        /*
        // note: for better performance, only some fields are loaded: rgd-id, seq-type and seq-md5
        List<Sequence> inRgdSeqs = dao.getPromoterSequences();

        // determine new sequences for insertion
        Collection<Sequence> forInsert = CollectionUtils.subtract(incoming, inRgdSeqs);

        // determine new sequences for deletion
        Collection<Sequence> forDelete = CollectionUtils.subtract(inRgdSeqs, incoming);

        Collection<Sequence> matching = CollectionUtils.intersection(inRgdSeqs, incoming);


        // insert new sequences
        if( !forInsert.isEmpty() ) {
            for( Sequence seq: forInsert ) {
                dao.insertSequence(seq);
            }
            log.info("SEQ_INSERTED: "+forInsert.size());
        }

        // delete obsolete sequences
        if( !forDelete.isEmpty() ) {
            for( Sequence seq: forDelete ) {
                dao.deleteSequence(seq);
            }
            log.info("SEQ_DELETED: "+forDelete.size());
        }

        int matchingSeqs = matching.size();
        if( matchingSeqs!=0 ) {
            log.info("SEQ_MATCHED: "+matchingSeqs);
        }
        */
    }
}

