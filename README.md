# cellosaurus-pipeline

Imports cell lines from the Cellosaurus database (Expasy) into RGD.

## Modules

### Cell Line Loader (default)

Downloads the Cellosaurus OBO file and syncs cell line records with RGD.

1. **Parse** — reads cellosaurus.obo, extracting cell line symbols, names, species, synonyms,
   cross-references (NCI, ORDO, ATCC, etc.), and hierarchical relationships
2. **QC cell lines** — compares incoming records against RGD; inserts new, updates changed,
   withdraws obsolete cell lines (cell-line rows are never physically deleted, only marked
   `WITHDRAWN`)
3. **Sync aliases** — loads new cell line aliases (synonyms); aliases are never deleted by policy
4. **Sync associations** — maintains both cell-line-to-gene associations (parsed from
   HGNC / UniProtKB / MGI / VGNC / RGD references in the OBO) and cell-line-to-cell-line
   parent-child relationships
5. **Sync XDB IDs** — loads/updates/deletes external database cross-references
6. **NCI collection QC** — maintains NCI Thesaurus collection mappings

### Disease Annotator (`--annotator`)

Creates disease (RDO) annotations for cell lines based on NCI and ORDO cross-references.

1. Maps NCI and ORDO accessions from cell line XDB IDs to RDO terms via ontology synonyms
2. Inserts new annotations, updates existing ones, deletes stale annotations
   (subject to a configurable deletion threshold)

## Notes

- If a cell line has one species, that species is used. If multiple or none, species is set to 'All'.

## Build and run

Requires Java 17. Built with Gradle:
```
./gradlew clean assembleDist
```