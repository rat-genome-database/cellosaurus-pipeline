# cellosaurus-pipeline
Pipeline to import cell lines from cellosaurus at Expasy.

Notes:

1) If there is one species assigned to the cell line, this species is used. If there are multiple species, or none, 'All' species will be used for this cell line object.

2) 'Creation_date' field from cellosaurus.obo file is currently ignored by the parser.