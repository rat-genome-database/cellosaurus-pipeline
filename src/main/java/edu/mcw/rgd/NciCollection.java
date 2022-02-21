package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import edu.mcw.rgd.process.CounterPool;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * collection of all NCIt ids mapped to actual term names;
 * assign NCI:Cxxx ids to RDO: terms as xrefs
 */
public class NciCollection {

    // THREAD SAFE SINGLETON -- start
    // private instance, so that it can be accessed by only by getInstance() method
    private static NciCollection instance;

    private NciCollection() {
        // private constructor
    }

    //synchronized method to control simultaneous access
    synchronized public static NciCollection getInstance() {
        if (instance == null) {
            // if instance is null, initialize
            instance = new NciCollection();
        }
        return instance;
    }
    // THREAD SAFE SINGLETON -- end


    Logger log = LogManager.getLogger("status");

    private final Map<String,String> incoming = new HashMap<>();

    public void addIncoming(String accession, String termName) {

        // there is only one instance of this class
        synchronized (incoming) {
            incoming.put(accession, termName);
        }
    }

    synchronized public void qc(Dao dao, CounterPool counters) throws Exception {

        Map<String, String> termNameMap = buildNormalizedTermNamesForRDO(dao);
        Map<String, String> synonymMap = buildNormalizedSynonymsForRDO(dao);

        // build list of term name prefixes, f.e. 'Rat ' etc based on the current species list
        List<String> speciesPrefixes = new ArrayList<>();
        for( int sp: SpeciesType.getSpeciesTypeKeys() ) {
            if( !SpeciesType.isSearchable(sp) ) {
                continue;
            }
            String speciesName = SpeciesType.getCommonName(sp);
            // capitalize first char and add a trailing space
            String speciesPrefix = speciesName.substring(0, 1).toUpperCase() + speciesName.substring(1) + " ";
            speciesPrefixes.add(speciesPrefix);
        }
        speciesPrefixes.add("Canine "); // for dog
        speciesPrefixes.add("Porcine "); // for pig

        // NCI ids in RGD
        Map<String, String> doTermMap = dao.getRdoTermsWithSynonymPattern("NCI%");
        counters.add("NCI_IDS_INCOMING", incoming.size());
        for( Map.Entry<String,String> entry: incoming.entrySet() ) {
            String acc = entry.getKey();
            String termName = entry.getValue();
            if( doTermMap.containsKey(acc) ) {
                counters.increment("NCI_IDS_ALREADY_IN_RGD");
                continue;
            }

            // NCI id not in RGD -- remove from term name the possible species prefix
            //
            String termName2 = termName;
            for( String speciesPrefix: speciesPrefixes ) {
                if( termName.startsWith(speciesPrefix) ) {
                    termName2 = termName.substring(speciesPrefix.length());
                    break;
                }
            }
            String normalizedTermName = normalizeName(termName2);

            String termAcc = termNameMap.get(normalizedTermName);
            if( termAcc==null ) {
                termAcc = synonymMap.get(normalizedTermName);
            }
            if( termAcc==null ) {
                counters.increment("NCI_IDS_NO_RDO_MATCH");
                continue;
            }

            counters.increment("NCI_IDS_INSERTED");
            TermSynonym tsyn = new TermSynonym();
            tsyn.setSource("CELLOSAURUS");
            tsyn.setType("xref");
            tsyn.setTermAcc(termAcc);
            tsyn.setName(acc);
            tsyn.setCreatedDate(new Date());
            tsyn.setLastModifiedDate(new Date());
            dao.insertTermSynonym(tsyn);
        }
    }

    Map<String,String> buildNormalizedTermNamesForRDO(Dao dao) throws Exception {

        Map<String, String> result = new HashMap<>();
        List<Term> terms = dao.getActiveTerms("RDO");
        for( Term t: terms ) {
            String normalizedName = normalizeName(t.getTerm());
            String previousAcc = result.put(normalizedName, t.getAccId());
            if( previousAcc!=null && !previousAcc.equals(t.getAccId())) {
                log.info("name conflict: "+t.getTerm()+" "+previousAcc+" "+t.getAccId());
            }
        }
        return result;
    }

    Map<String,String> buildNormalizedSynonymsForRDO(Dao dao) throws Exception {

        Map<String, String> result = new HashMap<>();
        List<TermSynonym> synonyms = dao.getActiveSynonymsByType("RDO", "exact_synonym");
        for( TermSynonym ts: synonyms ) {
            String normalizedName = normalizeName(ts.getName());

            String previousAcc = result.put(normalizedName, ts.getTermAcc());
            if( previousAcc!=null && !previousAcc.equals(ts.getTermAcc())) {
                log.info("synonym conflict: "+ts.getName()+" "+previousAcc+" "+ts.getTermAcc());
            }
        }
        return result;
    }

    String normalizeName(String synonym) {
        if( synonym==null )
            return "";
        String processedName = synonym.toLowerCase();

        String[] words = processedName.replace('-',' ').replace(',',' ').replace('(',' ').replace(')',' ').replace('/',' ')
                .toLowerCase().split("[\\s]");
        Arrays.sort(words);
        return Utils.concatenate(words, ".");
    }
}
