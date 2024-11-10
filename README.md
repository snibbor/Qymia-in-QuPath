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

## Tissue MicroArrays:

### 1) Thresholding TMA with PixelClassifiers

https://user-images.githubusercontent.com/28576964/183574975-aeb73c5b-1ce9-4315-8396-17a0987772f9.mp4

### 2) Within each TMA core, quantify the expression of targets for each compartment

https://user-images.githubusercontent.com/28576964/183575245-3d960cd8-a6e8-444d-adf5-6118fc9ada42.mp4


## Whole Tissue Sections:

### 1) Thresholding Whole Tissue Section with PixelClassifiers

<!-- <video width="50%" height="50%" src="https://user-images.githubusercontent.com/28576964/183573868-0d0fc0d3-2792-4cbb-b90f-fb3ae6cb93bd.mp4" type="video/mp4"></video> -->

<!-- <video src="https://user-images.githubusercontent.com/28576964/183573868-0d0fc0d3-2792-4cbb-b90f-fb3ae6cb93bd.mp4" controls="controls" style="max-width:640px;max-height:200px;"></video> -->
https://user-images.githubusercontent.com/28576964/183573868-0d0fc0d3-2792-4cbb-b90f-fb3ae6cb93bd.mp4

### 2) Within each user-annotated Regions of Interest (ROIs), quantify expression of targets for each compartment

https://user-images.githubusercontent.com/28576964/183574042-ec201410-09a5-42bf-9e2c-e939a6e99cfb.mp4

https://user-images.githubusercontent.com/28576964/183574493-1628455c-d40b-46e1-a7f1-7cf26b2f8bb0.mp4
