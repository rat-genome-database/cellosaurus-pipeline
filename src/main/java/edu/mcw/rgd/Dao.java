package edu.mcw.rgd;

import edu.mcw.rgd.dao.AbstractDAO;
import org.apache.log4j.Logger;

/**
 * @author mtutaj
 * @since 01/28/2020
 * wrapper to handle all DAO code
 */
public class Dao {

    AbstractDAO dao = new AbstractDAO();

    Logger logInserted = Logger.getLogger("insertedIds");
    Logger logDeleted = Logger.getLogger("deletedIds");

    public String getConnectionInfo() {
        return dao.getConnectionInfo();
    }

}
