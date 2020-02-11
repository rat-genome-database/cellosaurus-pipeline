package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.*;

import java.util.ArrayList;
import java.util.List;

/// represents data parsed from cellosaurus obo file for one cell line object
public class DataRecord extends CellLine {

    private List<Alias> aliases = new ArrayList<>();
    private List<XdbId> xdbIds = new ArrayList<>();

    public DataRecord() {
        setObjectKey(RgdId.OBJECT_KEY_CELL_LINES);
        setObjectStatus("ACTIVE");
        setSoAccId("CL:0000010");
        setSpeciesTypeKey(SpeciesType.UNKNOWN);
    }

    public List<Alias> getAliases() {
        return aliases;
    }

    public List<XdbId> getXdbIds() {
        return xdbIds;
    }
}

