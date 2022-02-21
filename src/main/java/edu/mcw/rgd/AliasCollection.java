package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Alias;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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


    Logger log = LogManager.getLogger("status");

    private final Set<Alias> incoming = new HashSet<>();

    public void addIncoming(Alias alias) throws Exception {

        // there is only one instance of this class
        synchronized (incoming) {
            incoming.add(alias);
        }
    }

    /// we load only new aliases; old aliases are never deleted -- that's our policy
    synchronized public void qc(Dao dao) throws Exception {

        List<Alias> aliasesInRgd = dao.getAliases();

        // determine new aliases for insertion
        Collection<Alias> forInsert = CollectionUtils.subtract(incoming, aliasesInRgd);

        Collection<Alias> matching = CollectionUtils.intersection(incoming, aliasesInRgd);

        // insert new aliases
        if( !forInsert.isEmpty() ) {
            dao.insertAliases(forInsert);
            log.info("ALIASES_INSERTED: "+forInsert.size());
        }

        int matchingAliases = matching.size();
        if( matchingAliases!=0 ) {
            log.info("ALIASES_MATCHED: "+matchingAliases);
        }
    }
}

