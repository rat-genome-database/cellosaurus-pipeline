package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.process.CounterPool;
import edu.mcw.rgd.process.Utils;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * parse the obo file
 */
public class Parser {

    private CounterPool counters;
    private String srcPipeline;

    private Map<String,String> sexTypes;
    private Map<String,String> cellLineTypes;
    private Set<String> ignoredXrefDatabases;
    private Map<String,Integer> processedXrefDatabases;

    public List<DataRecord> parse(String fileName, CounterPool counters, String sourcePipeline) throws Exception {

        this.counters = counters;
        this.srcPipeline = sourcePipeline;


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
                    System.out.println("#REC "+dataRecords.size());
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

            // skip empty lines
            if( line.equals("") ) {
                continue;
            }
            // id: => CellLine symbol
            else if( line.startsWith("id: ") ) {
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
            // synonym
            else if( line.startsWith("synonym: ") ) {
                parseSynonym( line.substring(9).trim(), rec );
            }
            // xref:
            else if( line.startsWith("xref: ") ) {
                parseXref( line.substring(6).trim(), rec );
            }
            // comment:
            else if( line.startsWith("comment: ") ) {
                parseComment( line.substring(9).trim(), rec );
            }
            // creation_date:
            else if( line.startsWith("creation_date: ") ) {
                counters.increment("PARSER ignored field 'creation_date'");
            }
            else {
                if( rec!=null ) {
                    throw new Exception("todo: implement parsing for line " + line);
                }
            }
        }
        in.close();

        // add final data record to the list
        if( rec!=null ) {
            dataRecords.add(rec);
            System.out.println("#REC "+dataRecords.size());
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
                rec.setObjectType( getCellLineTypes().get(subset) );
                return;
            }
        }

        throw new Exception( "unexpected subset ["+subset+"]");
    }

    void parseSynonym( String line, DataRecord rec ) throws Exception {

        // f.e. "15310-LN" RELATED []
        final String SYNONYM_END_PATTERN = "\" RELATED []";
        if( line.startsWith("\"") && line.endsWith(SYNONYM_END_PATTERN) ) {
            String synonym = line.substring(1, line.length() - SYNONYM_END_PATTERN.length());

            Alias alias = new Alias();
            alias.setValue(synonym);
            alias.setTypeName("old_cell_line_name");
            rec.getAliases().add(alias);
            return;
        }

        throw new Exception( "unexpected synonym ["+line+"]");
    }

    void parseXref( String xref, DataRecord rec ) throws Exception {

        // sample xrefs:
        //   Wikidata:Q54398957
        //   http://pathology.ucla.edu/workfiles/370cx.pdf

        String[] pair = xref.split("[:]");
        if( pair.length!=2 ) {
            throw new Exception( "unexpected xref ["+xref+"]");
        }
        String xrefDb = pair[0];
        String xrefAcc = pair[1];

        if( xrefDb.equals("NCBI_TaxID") ) {
            // f.e.:  NCBI_TaxID:9606 ! Homo sapiens
            int spacePos = xrefAcc.indexOf(' ');
            String taxonId = "taxon:"+xrefAcc.substring(0, spacePos).trim();
            int speciesTypeKey = SpeciesType.parse(taxonId);

            if( rec.getSpeciesTypeKey()==SpeciesType.UNKNOWN ) {
                rec.setSpeciesTypeKey(speciesTypeKey);
            } else {
                rec.setSpeciesTypeKey(SpeciesType.ALL);
            }
            return;
        }

        if( getIgnoredXrefDatabases().contains(xrefDb) ) {
            counters.increment("XDB_IDS_IGNORED_FOR "+xrefDb);
            return;
        }
        Integer xdbKey = getProcessedXrefDatabases().get(xrefDb);
        if( xdbKey==null ) {
            throw new Exception("unexpected xref db [" + xrefDb + "]");
        }

        XdbId xdbId = new XdbId();
        xdbId.setXdbKey(xdbKey);
        xdbId.setAccId(xrefAcc);
        xdbId.setSrcPipeline(srcPipeline);
        rec.getXdbIds().add(xdbId);
    }


    void parseComment( String comment, DataRecord rec ) throws Exception {
        // get rid of surrounding double quotes
        if( comment.startsWith("\"") && comment.endsWith("\"") ) {
            comment = comment.substring(1, comment.length()-1);
        }

        String[] pairs = comment.split("\\. ");
        for( int i=0; i<pairs.length; i++ ) {
            String pair = pairs[i];
            if( pair.startsWith("Part of: ") ) {
                // CELL_LINES.GROUPS
                rec.setGroups(merge(rec.getGroups(), pair));
            }
            else if( pair.startsWith("HLA typing: ") ) {
                // GENOMIC_ELEMENTS.DESCRIPTION
                rec.setDescription(merge(rec.getDescription(), pair));
            }
            else if( pair.startsWith("Transformant: ") ) {
                // CELL_LINES.ORIGIN
                rec.setOrigin(merge(rec.getOrigin(), pair));
            }
            else {
                throw new Exception("comment parse error: unknown tag: "+pair);
            }
        }
    }

    String merge(String s1, String s2) {
        if( s1==null ) {
            return s2;
        }
        if( s2==null ) {
            return s1;
        }
        return s1 + "; " + s2;
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

    public void setIgnoredXrefDatabases(Set ignoredXrefDatabases) {
        this.ignoredXrefDatabases = ignoredXrefDatabases;
    }

    public Set getIgnoredXrefDatabases() {
        return ignoredXrefDatabases;
    }

    public void setProcessedXrefDatabases(Map<String,Integer> processedXrefDatabases) {
        this.processedXrefDatabases = processedXrefDatabases;
    }

    public Map<String,Integer> getProcessedXrefDatabases() {
        return processedXrefDatabases;
    }
}

