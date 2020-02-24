package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.AliasDAO;
import edu.mcw.rgd.dao.impl.CellLineDAO;
import edu.mcw.rgd.dao.impl.RGDManagementDAO;
import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.datamodel.CellLine;
import edu.mcw.rgd.datamodel.RgdId;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * @author mtutaj
 * @since 01/28/2020
 * wrapper to handle all DAO code
 */
public class Dao {

    AliasDAO aliasDAO = new AliasDAO();
    CellLineDAO cellLineDAO = new CellLineDAO();
    RGDManagementDAO rgdIdDAO = new RGDManagementDAO();

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
}
