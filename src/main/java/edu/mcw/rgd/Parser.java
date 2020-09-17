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
                    //System.out.println("#REC "+dataRecords.size());
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

                // use cell line symbol to create an xdb id to Cellosaurus
                XdbId xdbId = new XdbId();
                xdbId.setXdbKey(128); // Cellosaurus
                xdbId.setAccId(rec.getSymbol());
                xdbId.setSrcPipeline(srcPipeline);
                rec.getXdbIds().add(xdbId);
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
            // relationship:
            else if( line.startsWith("relationship: ") ) {
                parseRelationship( line.substring(14).trim(), rec );
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
            //System.out.println("#REC "+dataRecords.size());
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
            // fixup for BTO:  'BTO:BTO:0006002' => 'BTO:BTO_0006002'
            if( pair.length==3 && pair[0].equals("BTO") && pair[1].equals("BTO") ) {
                pair[1] = "BTO_" + pair[2];

            } else if( pair.length==3 && pair[0].equals("MCCL") && pair[1].equals("MCC")) {
                // fixup for MCCL:  'MCCL:MCC:0000361'
                pair[1] += ":" + pair[2];

            } else if( pair.length==3 && pair[0].equals("CLS") ) {
                pair[1] += ":" + pair[2];

            } else if( pair.length==3 && pair[0].equals("MMRRC") ) {
                pair[1] += ":" + pair[2];

            } else if( pair.length==3 && pair[0].equals("DOI") ) {
                // fixup for DOI:xxx with ':' in the doid id
                pair[1] += ":" + pair[2];

            } else if( pair.length==3 && pair[0].startsWith("ORDO") ) {
                pair[1] += ":" + pair[2];

            } else if( pair.length==5 && pair[0].equals("DOI") ) {
                pair[1] += ":" + pair[2] + ":" + pair[3]+ ":" + pair[4];

                // fixup for malformed http
            } else if( pair.length>2 && pair[0].startsWith("http") ) {
                for (int i = 2; i < pair.length; i++) {
                    pair[1] += pair[i];
                }
            } else {
                throw new Exception("unexpected xref [" + xref + "]");
            }
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

        } else if( xrefDb.equals("NCIt") ) {
            // extract only acc id: 'C21619 ! Mouse mesothelioma' ==> 'C21619'
            int exPos = xrefAcc.indexOf(" ! ");
            if( exPos>0 ) {
                String nciTermName = xrefAcc.substring(exPos+3);
                xrefAcc = xrefAcc.substring(0, exPos);
                NciCollection.getInstance().addIncoming("NCI:"+xrefAcc, nciTermName);
            }
        } else if( xrefDb.equals("ORDO") ) {
            // extract only acc id: 'Orphanet_360 ! Glioblastoma' ==> '360'
            int exPos = xrefAcc.indexOf(" ! ");
            if( exPos>0 && xrefAcc.startsWith("Orphanet_") ) {

                xrefAcc = xrefAcc.substring(9, exPos);
                XdbId xdbId = new XdbId();
                xdbId.setXdbKey(62);
                xdbId.setAccId(xrefAcc);
                xdbId.setSrcPipeline(srcPipeline);
                rec.getXdbIds().add(xdbId);
                return;
            }
        }

        if( getIgnoredXrefDatabases().contains(xrefDb) ) {
            counters.increment("XDB_IDS_IGNORED_FOR "+xrefDb);
            return;
        }
        Integer xdbKey = getProcessedXrefDatabases().get(xrefDb);
        if( xdbKey==null ) {
            throw new Exception("unexpected xref db [" + xrefDb + "]  acc ["+xrefAcc+"]");
        }

        XdbId xdbId = new XdbId();
        xdbId.setXdbKey(xdbKey);
        xdbId.setAccId(xrefAcc);
        xdbId.setSrcPipeline(srcPipeline);
        rec.getXdbIds().add(xdbId);
    }


    void parseComment( String comment, DataRecord rec ) throws Exception {

        List<String> pairs = splitCommentIntoPairs(comment);
        for( String pair: pairs ) {

            if( pair.startsWith("Group: ")
             || pair.startsWith("Part of: ")
             || pair.startsWith("Registration: ") ) {
                // CELL_LINES.GROUPS
                rec.setGroups(merge(rec.getGroups(), pair));
            }
            else if( pair.startsWith("Characteristics: ")
                  || pair.startsWith("Contains 11 integrated HTLV-1 proviruses: ")
                  || pair.startsWith("Monoclonal antibody isotype: ")
                  || pair.startsWith("Monoclonal antibody target: ")
                  || pair.startsWith("The resulting cell line expresses two ligA variants: ") ) {
                // CELL_LINES.CHARACTERISTICS
                rec.setCharacteristics(merge(rec.getCharacteristics(), pair));
            }
            else if( pair.startsWith("Breed/subspecies: ")
                  || pair.startsWith("Derived from metastatic site: ")
                  || pair.startsWith("Derived from sampling site: ")
                  || pair.startsWith("Population: ")
                  || pair.startsWith("Karyotypic information: ")
                  || pair.startsWith("Selected for resistance to: ")
                  || pair.startsWith("Transformant: ") ) {
                // CELL_LINES.ORIGIN
                rec.setOrigin(merge(rec.getOrigin(), pair));
            }
            else if( pair.startsWith("Discontinued: ") ) {
                // CELL_LINES.AVAILABILITY
                rec.setAvailability(merge(rec.getAvailability(), pair));
            }
            else if( pair.startsWith("Doubling time: ")
                  || pair.startsWith("Microsatellite instability: ") ) {
                // CELL_LINES.PHENOTYPE
                rec.setPhenotype(merge(rec.getPhenotype(), pair));
            }
            else if( pair.startsWith("Omics: ")
                  || pair.startsWith("Biotechnology: ") ) {
                // CELL_LINES.RESEARCH_USE
                rec.setResearchUse(merge(rec.getResearchUse(), pair));
            }
            else if( pair.startsWith("Caution: ")
                  || pair.startsWith("Problematic cell line: ")
                  || pair.startsWith("Shown to be a HeLa derivative (PubMed=1246601; PubMed=6451928: ") // fake
                  || pair.startsWith("Compared to the STR values obtained by other distributors it differed from having: ") // fake
                  || pair.startsWith("Lacks the expression of three protein markers: ") // fake
                  || pair.startsWith("The major changes were: ") ) { // this is a fake pair that is in fact a part of 'Caution:' tag
                // CELL_LINES.CAUTION
                rec.setCaution(merge(rec.getCaution(), pair));
            }
            else if( pair.startsWith("Genome ancestry: ")
                  || pair.startsWith("HLA typing: ") ) {
                // GENOMIC_ELEMENTS.DESCRIPTION
                rec.setDescription(merge(rec.getDescription(), pair));
            }
            else if( pair.startsWith("From: ") ) {
                // GENOMIC_ELEMENTS.SOURCE
                rec.setSource(merge(rec.getSource(), pair));
            }
            else if( pair.startsWith("Anecdotal: ")
                  || pair.startsWith("Miscellaneous: ") ) {
                // GENOMIC_ELEMENTS.NOTES
                rec.setNotes(merge(rec.getNotes(), pair));
            }
            else if( pair.startsWith("Sequence variation: ")
                  || pair.startsWith("Knockout cell: ")
                  || pair.startsWith("Transfected with: ") ) {
                // GENOMIC_ELEMENTS.GENOMIC_ALTERATION
                rec.setGenomicAlteration(merge(rec.getGenomicAlteration(), pair));

                if( pair.startsWith("Transfected with: ") ) {
                    String oldValue = rec.getGeneAssocs().put(pair.substring(18), "transfected_gene");
                    if( oldValue!=null ) {
                        throw new Exception("unexpected transfected with");
                    }
                }

                if( pair.startsWith("Knockout cell: ") ) {
                    String oldValue = rec.getGeneAssocs().put(pair.substring(15), "knockout_gene");
                    if( oldValue!=null ) {
                        throw new Exception("unexpected knockout cell");
                    }
                }
            }
            else if( pair.startsWith("Misspelling: ") ) {

                // ALIAS
                //
                // f.e. Misspelling: NB-Ebcl-1; Occasionally. ==> 'NB-Ebcl-1'
                parseMisspelling(pair, rec);

            } else {
                throw new Exception("comment parse error: unknown tag: "+pair);
            }
        }
    }

    List<String> splitCommentIntoPairs(String comment) {
        // get rid of surrounding double quotes
        if( comment.startsWith("\"") && comment.endsWith("\"") ) {
            comment = comment.substring(1, comment.length()-1);
        }

        String[] pairs = comment.split("\\. ");
        List<String> result = new ArrayList<>();
        for( String pair: pairs ) {
            if( pair.contains(": ") ) {
                result.add(pair);
            } else {
                // not a pair: append this string to the last pair
                int index = result.size()-1;
                result.set(index, result.get(index)+" "+pair+".");
            }
        }
        return result;
    }

    void parseRelationship(String info, DataRecord rec) throws Exception {

        // parse 'derived_from'
        // f.e. derived_from CVCL_4032 ! P3X63Ag8.653
        if( info.startsWith("derived_from ") ) {
            int symbolEnd = info.indexOf(" ! ");
            String cellLineSymbol = info.substring(13, symbolEnd);
            rec.getCellLineAssocs().put(cellLineSymbol, "derived_from");
            return;
        }

        if( info.startsWith("originate_from_same_individual_as ") ) {
            int symbolEnd = info.indexOf(" ! ");
            String cellLineSymbol = info.substring(34, symbolEnd);
            rec.getCellLineAssocs().put(cellLineSymbol, "originate_from_same_individual_as");
            return;
        }

        throw new Exception("unexpected relationship: "+info);
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

    void parseMisspelling(String line, DataRecord rec) {
        // ALIAS
        //
        // f.e. Misspelling: NB-Ebcl-1; Occasionally. ==> 'NB-Ebcl-1'
        int symbolStart = 13; // length of 'Misspelling: '
        int symbolEnd = line.indexOf("; ", symbolStart);
        String symbol = line.substring(symbolStart, symbolEnd);

        Alias alias = new Alias();
        alias.setTypeName("old_cell_line_symbol");
        alias.setValue(symbol);
        rec.getAliases().add(alias);
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

