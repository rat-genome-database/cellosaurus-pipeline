package edu.mcw.rgd;

import edu.mcw.rgd.process.Utils;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * parse the obo file
 */
public class Parser {

    private Map<String,String> sexTypes;
    private Map<String,String> cellLineTypes;

    public List<DataRecord> parse(String fileName) throws Exception {

        List<DataRecord> dataRecords = new ArrayList<DataRecord>();

        BufferedReader in = Utils.openReader(fileName);
        String line;
        DataRecord rec = null;
        while( (line=in.readLine())!=null ) {

            // detect record boundaries
            if( line.startsWith("[") && line.endsWith("]") ) {
                // save the previous record
                if( rec!=null ) {
                    dataRecords.add(rec);
                }
                if( line.equals("[Term]") ) {
                    rec = new DataRecord();
                } else {
                    // non-Term entries, f.e. [Typedef]: ignore
                    rec = null;
                }
                continue;
            }

            if( rec==null ) {
                // skip header lines and trailer records (like [Typedef])
                continue;
            }

            // id: => CellLine symbol
            if( line.startsWith("id: ") ) {
                rec.setSymbol( line.substring(4).trim() );
            }
            // name: CellLine name
            else if( line.startsWith("name: ") ) {
                rec.setName( line.substring(6).trim() );
            }
            // subset: CellLine Gender or Type
            else if( line.startsWith("subset: ") ) {
                parseSubset( line.substring(8).trim(), rec );
            }
            // xref:
            else if( line.startsWith("xref: ") ) {
                parseXref( line.substring(6).trim(), rec );
            }
            else {
                if( rec!=null ) {
                    //throw new Exception("todo: implement parsing for line " + line);
                }
            }
        }
        in.close();

        // add final data record to the list
        if( rec!=null ) {
            dataRecords.add(rec);
        }
        return dataRecords;
    }

    /// a subset could be either a sex type, or a cell line type
    void parseSubset( String subset, DataRecord rec ) throws Exception {

        for( String sexType: getSexTypes().keySet() ) {
            if( subset.equals(sexType) ) {
                rec.setGender( getSexTypes().get(subset) );
                return;
            }
        }

        for( String cellLineType: getCellLineTypes().keySet() ) {
            if( subset.equals(cellLineType) ) {
                rec.setCellLineType( getCellLineTypes().get(subset) );
                return;
            }
        }

        //throw new Exception( "unexpected subset ["+subset+"]");
    }

    void parseXref( String xref, DataRecord rec ) throws Exception {

        //throw new Exception( "unexpected xref ["+xref+"]");
    }

    public void setSexTypes(Map sexTypes) {
        this.sexTypes = sexTypes;
    }

    public Map<String,String> getSexTypes() {
        return sexTypes;
    }

    public void setCellLineTypes(Map cellLineTypes) {
        this.cellLineTypes = cellLineTypes;
    }

    public Map<String,String> getCellLineTypes() {
        return cellLineTypes;
    }
}

