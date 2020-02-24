package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.*;

import java.util.*;

/// represents data parsed from cellosaurus obo file for one cell line object
public class DataRecord extends CellLine {

    private List<Alias> aliases = new ArrayList<>();
    private java.util.Map<String, String> cellLineAssocs = new HashMap<>();
    private java.util.Map<String, String> geneAssocs = new HashMap<>();
    private List<XdbId> xdbIds = new ArrayList<>();

    public DataRecord() {
        setObjectKey(RgdId.OBJECT_KEY_CELL_LINES);
        setObjectStatus("ACTIVE");
        setSoAccId("CL:0000010");
        setSpeciesTypeKey(SpeciesType.UNKNOWN);
        setSrcPipeline("CELLOSAURUS");
    }

    public List<Alias> getAliases() {
        return aliases;
    }

    public java.util.Map<String,String> getCellLineAssocs() {
        return cellLineAssocs;
    }

    public java.util.Map<String,String> getGeneAssocs() {
        return geneAssocs;
    }

    public List<XdbId> getXdbIds() {
        return xdbIds;
    }
}

