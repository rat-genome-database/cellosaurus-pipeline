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

    private final Logger log = LogManager.getLogger("status");

    private final Set<Alias> incoming = new HashSet<>();

    public void addIncoming(Alias alias) {
        incoming.add(alias);
    }

    /// we load only new aliases; old aliases are never deleted -- that's our policy
    public void qc(Dao dao) throws Exception {

        List<Alias> aliasesInRgd = dao.getAliases();

        // determine and insert new aliases
        Collection<Alias> forInsert = CollectionUtils.subtract(incoming, aliasesInRgd);
        if( !forInsert.isEmpty() ) {
            dao.insertAliases(forInsert);
            log.info("ALIASES_INSERTED: "+forInsert.size());
        }

        // the rest of the incoming aliases are already in RGD
        int matching = incoming.size() - forInsert.size();
        if( matching!=0 ) {
            log.info("ALIASES_MATCHED: "+matching);
        }
    }
}
