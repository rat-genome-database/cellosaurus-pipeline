package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.Map;

/**
 * @author mtutaj
 * @since 01/28/2020
 * wrapper to handle all DAO code
 */
public class Dao {

    AliasDAO aliasDAO = new AliasDAO();
    AnnotationDAO adao = new AnnotationDAO();
    AssociationDAO assocDAO = new AssociationDAO();
    CellLineDAO cellLineDAO = new CellLineDAO();
    OntologyXDAO odao = new OntologyXDAO();
    RGDManagementDAO rgdIdDAO = new RGDManagementDAO();
    XdbIdDAO xdao = new XdbIdDAO();

    Logger logWarnings = LogManager.getLogger("warnings");

    // dry-run mode: when true, all DB-mutating calls become no-ops and detail log lines are prefixed
    private static boolean dryRun = false;
    // synthetic negative rgd_ids handed out for inserts in dry-run mode so downstream code has something to attach to
    private static int dryRunRgdIdCounter = -1;

    public static boolean isDryRun() { return dryRun; }
    public static void setDryRun(boolean v) { dryRun = v; }
    private static String tag() { return dryRun ? "[DRY RUN] " : ""; }

    public String getConnectionInfo() {
        return cellLineDAO.getConnectionInfo();
    }

    public List<CellLine> getCellLines(String srcPipeline) throws Exception {
        return cellLineDAO.getActiveCellLines(srcPipeline);
    }

    public void insertCellLines( Collection<CellLine> cellLines ) throws Exception {

        Logger log = LogManager.getLogger("inserted_cell_lines");

        for( CellLine cl: cellLines ) {

            if( dryRun ) {
                // hand out a synthetic negative rgd_id so downstream (associations, xdb_ids) still has something to attach to
                cl.setRgdId(dryRunRgdIdCounter--);
            } else {
                RgdId id = rgdIdDAO.createRgdId(RgdId.OBJECT_KEY_CELL_LINES, "ACTIVE", cl.getSpeciesTypeKey());
                cl.setRgdId(id.getRgdId());
                cellLineDAO.insertCellLine(cl);
            }

            log.debug(tag() + cl.dump("|"));
        }
    }

    public void withdrawCellLines( Collection<CellLine> cellLines ) throws Exception {

        Logger log = LogManager.getLogger("discontinued_cell_lines");

        for( CellLine cl: cellLines ) {
            log.debug(tag() + cl.dump("|"));

            if( !dryRun ) {
                // cell line, once created, should never be deleted; you can only withdraw it
                rgdIdDAO.withdraw(cl);
            }
        }
    }

    public void updateRgdId( int rgdId, int objectKey, int speciesTypeKey, String objectStatus ) throws Exception {

        Logger log = LogManager.getLogger("updated_rgd_ids");

        RgdId id = rgdIdDAO.getRgdId2(rgdId);
        log.debug(tag() + "OLD> RGD:"+rgdId+", OBJECT_KEY="+id.getObjectKey()+", SPECIES_TYPE_KEY="+id.getSpeciesTypeKey()+", OBJECT_STATUS="+id.getObjectStatus());

        id.setObjectKey(objectKey);
        id.setSpeciesTypeKey(speciesTypeKey);
        id.setObjectStatus(objectStatus);
        id.setLastModifiedDate(new Date());

        log.debug(tag() + "NEW> RGD:"+rgdId+", OBJECT_KEY="+id.getObjectKey()+", SPECIES_TYPE_KEY="+id.getSpeciesTypeKey()+", OBJECT_STATUS="+id.getObjectStatus());

        if( !dryRun ) {
            rgdIdDAO.updateRgdId(id);
        }
    }

    public void updateCellLine( CellLine oldCellLine, CellLine newCellLine ) throws Exception {

        Logger log = LogManager.getLogger("updated_cell_lines");

        log.debug(tag() + "OLD> "+oldCellLine.dump("|"));
        log.debug(tag() + "NEW> "+newCellLine.dump("|"));

        if( !dryRun ) {
            cellLineDAO.updateCellLine(newCellLine);
        }
    }

    /**
     * load all aliases in RGD, of type 'old_cell_line_name' and 'old_cell_line_symbol'
     * @return collection of Alias objects
     */
    public List<Alias> getAliases() throws Exception {
        List<Alias> aliases = new ArrayList<>();
        aliases.addAll(aliasDAO.getAliasesByType("old_cell_line_name"));
        aliases.addAll(aliasDAO.getAliasesByType("old_cell_line_symbol"));
        return aliases;
    }

    public void insertAliases(Collection<Alias> aliasesForInsert) throws Exception {

        Logger log = LogManager.getLogger("aliases");
        for( Alias a: aliasesForInsert ) {
            log.debug(tag() + "INSERT "+a.dump("|"));
        }

        if( !dryRun ) {
            aliasDAO.insertAliases(new ArrayList<>(aliasesForInsert));
        }
    }

    public int getGeneRgdIdByXdbId(int xdbKey, String accId) throws Exception {
        String cacheKey = xdbKey+"|"+accId;
        List<Gene> genes = _xdbGeneMap.get(cacheKey);
        boolean doWarn = false;
        if( genes==null ) {
            genes = xdao.getActiveGenesByXdbId(xdbKey, accId);
            _xdbGeneMap.put(cacheKey, genes);
            doWarn = true;
        }

        if( genes.isEmpty() ) {
            if( doWarn ) {
                logWarnings.debug("cannot resolve " + accId);
            }
            return 0;
        } else if( genes.size()>1 ) {
            if( doWarn ) {
                logWarnings.warn("multiple genes for " + accId);
            }
            return 0;
        }
        return genes.get(0).getRgdId();
    }
    java.util.Map<String, List<Gene>> _xdbGeneMap = new HashMap<>();


    public List<Association> getAssociations(String assocType, String source) throws Exception {
        return assocDAO.getAssociationsByTypeAndSource(assocType, source);
    }

    public int insertAssociation( Association assoc ) throws Exception {
        Logger log = LogManager.getLogger("inserted_associations");
        log.debug(tag() + assoc.dump("|"));
        if( dryRun ) {
            return 1;
        }
        return assocDAO.insertAssociation(assoc);
    }

    public int deleteAssociation( Association assoc ) throws Exception {
        Logger log = LogManager.getLogger("deleted_associations");
        log.debug(tag() + assoc.dump("|"));
        if( dryRun ) {
            return 1;
        }
        return assocDAO.deleteAssociationByKey(assoc.getAssocKey());
    }

    public List<XdbId> getXdbIds(String srcPipeline) throws Exception {
        XdbId filter = new XdbId();
        filter.setSrcPipeline(srcPipeline);
        return xdao.getXdbIds(filter);
    }

    public List<XdbId> getXdbIds(String srcPipeline, int xdbKey) throws Exception {
        XdbId filter = new XdbId();
        filter.setXdbKey(xdbKey);
        filter.setSrcPipeline(srcPipeline);
        return xdao.getXdbIds(filter);
    }

    public int insertXdbIds(Collection<XdbId> xdbs) throws Exception {
        Logger log = LogManager.getLogger("inserted_xdb_ids");
        for( XdbId id: xdbs ) {
            log.debug(tag() + id.dump("|"));
        }
        if( dryRun ) {
            return xdbs.size();
        }
        return xdao.insertXdbs(new ArrayList<>(xdbs));
    }

    public int deleteXdbIds(Collection<XdbId> xdbs) throws Exception {
        Logger log = LogManager.getLogger("deleted_xdb_ids");
        for( XdbId id: xdbs ) {
            log.debug(tag() + id.dump("|"));
        }
        if( dryRun ) {
            return xdbs.size();
        }
        return xdao.deleteXdbIds(new ArrayList<>(xdbs));
    }

    /// ONTOLOGIES

    public Map<String, String> getRdoTermsWithSynonymPattern(String pattern) throws Exception {

        Map<String, String> results = new HashMap<>();
        List<TermSynonym> synonyms = odao.getActiveSynonymsByNamePattern("RDO", pattern);
        for( TermSynonym syn: synonyms ) {
            String termAcc = results.put(syn.getName(), syn.getTermAcc());
            if( termAcc!=null && !termAcc.equals(syn.getTermAcc()) ) {
                System.out.println("CONFLICT: "+syn.getName()+" "+termAcc+" "+syn.getTermAcc());
            }
        }
        return results;
    }

    public List<Term> getActiveTerms(String ontologyId) throws Exception {
        return odao.getActiveTerms(ontologyId);
    }

    public Term getTermByAcc(String termAcc) throws Exception {
        return odao.getTermByAccId(termAcc);
    }

    public List<TermSynonym> getActiveSynonymsByType(String ontologyId, String synonymType) throws Exception {
        return odao.getActiveSynonymsByType(ontologyId, synonymType);
    }

    public int insertTermSynonym(TermSynonym synonym) throws Exception {
        if( dryRun ) {
            LogManager.getLogger("warnings").debug(tag() + "insertTermSynonym " + synonym.getTermAcc() + " " + synonym.getName());
            return 0;
        }
        return odao.insertTermSynonym(synonym);
    }

    /// ANNOTATIONS

    public List<Annotation> getAnnotations(int refRgdId) throws Exception {
        return adao.getAnnotationsByReference(refRgdId);
    }

    public int insertAnnotation(Annotation a) throws Exception {
        Logger log = LogManager.getLogger("annot_inserted");
        if( dryRun ) {
            log.debug(tag() + a.dump("|"));
            return 0;
        }
        int key = adao.insertAnnotation(a);
        log.debug(a.dump("|"));
        return key;
    }

    public void updateAnnotation(Annotation newAnnot, Annotation oldAnnot) throws Exception {

        Logger log = LogManager.getLogger("annot_updated");
        log.debug(tag() + "OLD_ANNOT: "+oldAnnot.dump("|"));
        log.debug(tag() + "NEW_ANNOT: "+newAnnot.dump("|"));

        if( !dryRun ) {
            adao.updateAnnotation(newAnnot);
        }
    }

    public int getCountOfAnnotationsByReference(int refRgdId) throws Exception {
        return adao.getCountOfAnnotationsByReference(refRgdId);
    }

    public int deleteAnnotations(List<Annotation> staleAnnots, int staleAnnotThreshold) throws Exception {

        // get to-be-deleted stale annots and check if their nr does not exceed the threshold
        Logger log = LogManager.getLogger("annot_deleted");
        if( staleAnnots.size() > staleAnnotThreshold ) {
            for( Annotation annot: staleAnnots ) {
                log.debug("TO-BE-DELETED "+annot.dump("|"));
            }
            return staleAnnots.size();
        }

        // dump all to be deleted annotation to 'deleted_annots' log
        List<Integer> fullAnnotKeys = new ArrayList<>(staleAnnots.size());
        for( Annotation annot: staleAnnots ) {
            log.debug(tag() + "DELETED "+annot.dump("|"));
            fullAnnotKeys.add(annot.getKey());
        }

        if( dryRun ) {
            return staleAnnots.size();
        }
        return adao.deleteAnnotations(fullAnnotKeys);
    }
}
