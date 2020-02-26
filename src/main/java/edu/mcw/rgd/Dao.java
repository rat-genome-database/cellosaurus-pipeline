package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.datamodel.*;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * @author mtutaj
 * @since 01/28/2020
 * wrapper to handle all DAO code
 */
public class Dao {

    AliasDAO aliasDAO = new AliasDAO();
    AssociationDAO assocDAO = new AssociationDAO();
    CellLineDAO cellLineDAO = new CellLineDAO();
    RGDManagementDAO rgdIdDAO = new RGDManagementDAO();
    XdbIdDAO xdao = new XdbIdDAO();

    public String getConnectionInfo() {
        return cellLineDAO.getConnectionInfo();
    }

    public List<CellLine> getCellLines(String srcPipeline) throws Exception {
        return cellLineDAO.getActiveCellLines(srcPipeline);
    }

    public void insertCellLines( Collection<CellLine> cellLines ) throws Exception {

        Logger log = Logger.getLogger("insertedCellLines");

        for( CellLine cl: cellLines ) {

            RgdId id = rgdIdDAO.createRgdId(RgdId.OBJECT_KEY_CELL_LINES, "ACTIVE", cl.getSpeciesTypeKey());
            cl.setRgdId(id.getRgdId());

            cellLineDAO.insertCellLine(cl);

            log.debug(cl.dump("|"));
        }
    }

    public void deleteCellLines( Collection<CellLine> cellLines ) throws Exception {

        Logger log = Logger.getLogger("discontinuedCellLines");

        for( CellLine cl: cellLines ) {
            log.debug(cl.dump("|"));

            // cell line, once created, should never be deleted; you can only withdraw it
            rgdIdDAO.withdraw(cl);
        }
    }

    public void updateRgdId( int rgdId, int objectKey, int speciesTypeKey, String objectStatus ) throws Exception {

        Logger log = Logger.getLogger("updatedRgdIds");

        RgdId id = rgdIdDAO.getRgdId2(rgdId);
        log.debug("OLD> RGD:"+rgdId+", OBJECT_KEY="+id.getObjectKey()+", SPECIES_TYPE_KEY="+id.getSpeciesTypeKey()+", OBJECT_STATUS="+id.getObjectStatus());

        id.setObjectKey(objectKey);
        id.setSpeciesTypeKey(speciesTypeKey);
        id.setObjectStatus(objectStatus);
        id.setLastModifiedDate(new Date());

        log.debug("NEW> RGD:"+rgdId+", OBJECT_KEY="+id.getObjectKey()+", SPECIES_TYPE_KEY="+id.getSpeciesTypeKey()+", OBJECT_STATUS="+id.getObjectStatus());

        rgdIdDAO.updateRgdId(id);
    }

    public void updateCellLine( CellLine oldCellLine, CellLine newCellLine ) throws Exception {

        Logger log = Logger.getLogger("updatedCellLines");

        log.debug("OLD> "+oldCellLine.dump("|"));
        log.debug("NEW> "+newCellLine.dump("|"));

        cellLineDAO.updateCellLine(newCellLine);
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

        Logger log = Logger.getLogger("aliases");
        for( Alias a: aliasesForInsert ) {
            log.debug("INSERT "+a.dump("|"));
        }

        aliasDAO.insertAliases(new ArrayList<>(aliasesForInsert));
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
            Logger log = Logger.getLogger("status");
            if( doWarn ) {
                log.warn("cannot resolve " + accId);
            }
            return 0;
        } else if( genes.size()>1 ) {
            Logger log = Logger.getLogger("status");
            if( doWarn ) {
                log.warn("multiple genes for " + accId);
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
        int r = assocDAO.insertAssociation(assoc);
        Logger log = Logger.getLogger("insertedAssociations");
        log.debug(assoc.dump("|"));
        return r;
    }

    public int deleteAssociation( Association assoc ) throws Exception {
        Logger log = Logger.getLogger("deletedAssociations");
        log.debug(assoc.dump("|"));
        return assocDAO.deleteAssociationByKey(assoc.getAssocKey());
    }

    public List<XdbId> getXdbIds(String srcPipeline) throws Exception {
        XdbId filter = new XdbId();
        filter.setSrcPipeline(srcPipeline);
        return xdao.getXdbIds(filter);
    }

    public int insertXdbIds(Collection<XdbId> xdbs) throws Exception {
        Logger log = Logger.getLogger("insertedXdbIds");
        for( XdbId id: xdbs ) {
            log.debug(id.dump("|"));
        }
        return xdao.insertXdbs(new ArrayList<>(xdbs));
    }

    public int deleteXdbIds(Collection<XdbId> xdbs) throws Exception {
        Logger log = Logger.getLogger("deletedXdbIds");
        for( XdbId id: xdbs ) {
            log.debug(id.dump("|"));
        }
        return xdao.deleteXdbIds(new ArrayList<>(xdbs));
    }
}
