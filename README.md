# CompAnalysis-in-QuPath
Molecular Compartment Quantification of Immunofluorescence/Immunohistochemistry Images in QuPath

CompAnalysis is currently designed as a 2 part GUI extension where you make compartments with CompMaker and quantify the target expression within these compartments with CompQuant. The CompMaker portion of the extension is under construction.

## TODO:
- CompQuant
  - Grid/Tile calculation of compartment scores + overlay image (measurement map?)
    - Export measurements to tab delim .csv or .json?
  - Export masks as binary masks or segmentation masks for downstream applications
  - Improve "finished" asthetics/state of progress bar
  - Minimize ImageJ, OpenCV image/matrix datatype conversions
  - Need to fix memory leak from ForkJoinPool termination 
  - Advanced settings
    - Change ignore classes
    - Simpler measurement export options
    - Change rescale value (if image data was already normalized but rescaled to an unsigned integer)
  - Test for bugs

- CompMaker
  - Streamline CompMaker UI, integrate with PixelClassificationOverlays, work on backend functions
    - Extend ImageOps classes to support bitwise operations for computing fast union, difference, or intersection of masks
    - Improve QuPath ROI to OpenCV Mat casting/transformation functions to not rely on ImageJ
  - Save/load through serialization of GUI state/parameters into a reloadable preferably human readable protocol file
  - Undo/redo system 
  - Resultant validator to throw errors for binding loops or other invalid operations before computing mask resultant ImageOps
  - Determine best execution order of dependent resultants
    - Handle drag and drop of operations?

- Integrate cell segmentation and subcellular compartment tools into QuPath-CompAnalysis workflow
- Integrate custom or ML-generated segmentation masks into QuPath-CompAnalysis workflow

## Workflow Demo Version 0.0.1:

This workflow uses the QuPath built-in SimpleThresholder as well as CompQuant

## Whole Tissue Sections:

### 1) Thresholding Whole Tissue Section with PixelClassifiers

https://user-images.githubusercontent.com/28576964/183573868-0d0fc0d3-2792-4cbb-b90f-fb3ae6cb93bd.mp4

### 2) Within each user-annotated Regions of Interest (ROIs), quantify expression of targets for each compartment

https://user-images.githubusercontent.com/28576964/183574042-ec201410-09a5-42bf-9e2c-e939a6e99cfb.mp4

https://user-images.githubusercontent.com/28576964/183574493-1628455c-d40b-46e1-a7f1-7cf26b2f8bb0.mp4


### 3) Grid/Tile calculation of compartment scores (comming soon!)

## Tissue MicroArrays:

### 1) Thresholding TMA with PixelClassifiers

https://user-images.githubusercontent.com/28576964/183574975-aeb73c5b-1ce9-4315-8396-17a0987772f9.mp4

### 1.5) Modifying compartments/masks or creating new compartments based on experiment (i.e adding stroma compartment as Tissue - Tumor)

https://user-images.githubusercontent.com/28576964/183575198-896ba7da-dab5-4433-b0fb-3727e17e02ae.mp4

### 2) Within each TMA core, quantify the expression of targets for each compartment

https://user-images.githubusercontent.com/28576964/183575245-3d960cd8-a6e8-444d-adf5-6118fc9ada42.mp4

