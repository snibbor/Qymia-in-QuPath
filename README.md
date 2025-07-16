# Qymia in QuPath
### Quantitative Immunofluorescence/Immunohistochemistry Molecular Image Analysis (Qymia) in QuPath

Fast, high-throughput quantification of multiplex immunofluorescence and/or immunohistochemistry staining in clinical specimens (tissue microarrays, whole tissue sections) by molecular compartmentalization techniques (tissue and cell-based) in QuPath.

To use (see below for example):
1) Create compartments/segmentations/annotations inside of QuPath
2) Run Qymia Quant to quantify target biomarker expression within compartments
3) (optionally) Construct calibration curves and convert Qymia scores to adjust for batch-to-batch intensity variation

References:
- Dr. David Rimm's Laboratory at Yale School of Medicine (https://medicine.yale.edu/lab/rimm/)
- Camp RL, Dolled-Filhart M, King BL, Rimm DL. Quantitative analysis of breast cancer tissue microarrays shows that both high and normal levels of HER2 expression are associated with poor outcome. Cancer Res. 2003 Apr 1;63(7):1445-8. PMID: 12670887.
- McCabe A, Dolled-Filhart M, Camp RL, Rimm DL. Automated quantitative analysis (AQUA) of in situ protein expression, antibody concentration, and prognosis. J Natl Cancer Inst. 2005 Dec 21;97(24):1808-15. doi: 10.1093/jnci/dji427. PMID: 16368942.

## Workflow Demo:

This workflow uses the QuPath built-in SimpleThresholder as well as Qymia Quant

### 1) Thresholding TMA with PixelClassifiers

[![Thresholding CK](video_examples/thumbnail.png)](https://yaleedu-my.sharepoint.com/:v:/g/personal/jack_robbins_yale_edu/EQWPRGGFIVNFg4MhVb80TYEBdxo2VMW5C6ABOdb0-dqU6Q?nav=eyJyZWZlcnJhbEluZm8iOnsicmVmZXJyYWxBcHAiOiJPbmVEcml2ZUZvckJ1c2luZXNzIiwicmVmZXJyYWxBcHBQbGF0Zm9ybSI6IldlYiIsInJlZmVycmFsTW9kZSI6InZpZXciLCJyZWZlcnJhbFZpZXciOiJNeUZpbGVzTGlua0NvcHkifX0&e=zGagPE)

[![Ignore artifacts](video_examples/thumbnail.png)](https://yaleedu-my.sharepoint.com/:v:/g/personal/jack_robbins_yale_edu/EeSU_w8hDdVClHCuIVu_DhABFwJbIKD-5av7sbVsFODOFg?nav=eyJyZWZlcnJhbEluZm8iOnsicmVmZXJyYWxBcHAiOiJPbmVEcml2ZUZvckJ1c2luZXNzIiwicmVmZXJyYWxBcHBQbGF0Zm9ybSI6IldlYiIsInJlZmVycmFsTW9kZSI6InZpZXciLCJyZWZlcnJhbFZpZXciOiJNeUZpbGVzTGlua0NvcHkifX0&e=ZtWiu7)


### 2) Within each TMA core, quantify the expression of targets for each compartment

 [![Quantifying expression](video_examples/thumbnail.png)](https://yaleedu-my.sharepoint.com/:v:/g/personal/jack_robbins_yale_edu/EYiFl0U6apFMqKLb7F7mFPEBRKDtKqcC8SRfW19TSg5yww?nav=eyJyZWZlcnJhbEluZm8iOnsicmVmZXJyYWxBcHAiOiJPbmVEcml2ZUZvckJ1c2luZXNzIiwicmVmZXJyYWxBcHBQbGF0Zm9ybSI6IldlYiIsInJlZmVycmFsTW9kZSI6InZpZXciLCJyZWZlcnJhbFZpZXciOiJNeUZpbGVzTGlua0NvcHkifX0&e=oKpXnY)
