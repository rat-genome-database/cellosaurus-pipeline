package edu.mcw.rgd;

import edu.mcw.rgd.process.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * parse the obo file
 */
public class Parser {

    private Map<String,String> sexTypes;
    private Map<String,String> cellLineTypes;

    public List<DataRecord> parse(String fileName) throws IOException {
        List<DataRecord> dataRecords = new ArrayList<DataRecord>();

        BufferedReader in = Utils.openReader(fileName);
        String line;
        DataRecord rec = null;
        while( (line=in.readLine())!=null ) {

            // detect record boundaries
            if( line.equals("[Term]") ) {
                // save the previous record
                if( rec!=null ) {
                    dataRecords.add(rec);
                }
                rec = null;
                continue;
            }


        }
        in.close();

        // add final data record to the list
        if( rec!=null ) {
            dataRecords.add(rec);
        }
        return dataRecords;
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

