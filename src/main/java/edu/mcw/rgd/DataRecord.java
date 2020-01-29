package edu.mcw.rgd;

/// represents data parsed from cellosaurus obo file for one cell line object
public class DataRecord {

    private String symbol;
    private String name;
    private String cellLineType;
    private String gender;


    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCellLineType() {
        return cellLineType;
    }

    public void setCellLineType(String cellLineType) {
        this.cellLineType = cellLineType;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }
}

