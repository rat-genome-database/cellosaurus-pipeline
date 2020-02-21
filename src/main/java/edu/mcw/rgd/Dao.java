package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.CellLineDAO;
import edu.mcw.rgd.dao.impl.RGDManagementDAO;
import edu.mcw.rgd.datamodel.CellLine;
import edu.mcw.rgd.datamodel.RgdId;
import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.List;

/**
 * @author mtutaj
 * @since 01/28/2020
 * wrapper to handle all DAO code
 */
public class Dao {

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
            log.debug(cl);

            // cell line, once created, should never be deleted; you can only withdraw it
            rgdIdDAO.withdraw(cl);
        }
    }
}
