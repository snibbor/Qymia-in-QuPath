# QIIMA in QuPath
### Quantitative Immunofluorescence/Immunohistochemistry Molecular Analysis (QIIMA, "kee-ma" or "k-EYE-ma") in QuPath

Fast, high-throughput quantification of multiplex immunofluorescence and/or immunohistochemistry staining in clinical specimens (tissue microarrays, whole tissue sections) by molecular compartmentalization techniques (tissue and cell-based) in QuPath.

QIIMA is currently designed as a 2 part GUI extension where you make compartments with QIIMA Compartment Builder and quantify the target expression within these compartments with QIIMA-Quant. The QIIMA Compartment Builder portion of the extension is under construction, however QuPath natively provides many ways to define molecular compartments of interest.

## Workflow Demo Version 0.0.1:

This workflow uses the QuPath built-in SimpleThresholder as well as QIIMA-Quant

## Whole Tissue Sections:

### 1) Thresholding Whole Tissue Section with PixelClassifiers

<!-- <video width="50%" height="50%" src="https://user-images.githubusercontent.com/28576964/183573868-0d0fc0d3-2792-4cbb-b90f-fb3ae6cb93bd.mp4" type="video/mp4"></video> -->

<!-- <video src="https://user-images.githubusercontent.com/28576964/183573868-0d0fc0d3-2792-4cbb-b90f-fb3ae6cb93bd.mp4" controls="controls" style="max-width:640px;max-height:200px;"></video> -->
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

## TODO:
- ### QIIMA-Quant
  - #### Main features
    - [x] Grid/Tile calculation of compartment scores + overlay image (measurement map?)
      - [x] Export measurements to tab delim .csv ~~or .json?~~
    - [x] Minimize ImageJ, OpenCV image/matrix datatype conversions 
      - OpenCV implementation reduced memory consumption ~ same speed
    - [x] Need to fix memory leak from ~~ForkJoinPool~~ --> wasn't a memory leak due to thread pooling, it was the ImageRegion tile cache filling up!
  
  - #### Advanced settings
    - [ ] Options to convert intensity scores for an image or set of images in project using a function (i.e. intensity score --> concentration of target expression (amol or molecules per area) using a standardization array)
      - [ ] Regression options to calculate conversion function using standard index array
        - [ ] Visuallization of regression fit in QuPath
        - [ ] Outlier removal
        - [ ] Handle multiple conversion functions/standard curves per project.
        - [ ] Methods to save results to file --> set current project conversion functions on save
      - [ ] Apply conversion functions/standard curves to images in project
        - [ ] Load conversion function parameters from file --> apply based mapping of imagePath -> function name
          - [ ] Map functions and images based on CSV/TSV/Excel file
          - [ ] Interactive GUI to select images for each conversion function
    - [ ] Change ignore classes
    - [x] Simpler measurement export options
      - [x] Change GUI controls to use the default QuPath measurement exporter.
    - [ ] Export measurement maps/heatmaps for project
      - [ ] Extend QuPath measurement map UI to export map images for project
    - [ ] Modifiable rescale value (if image data was already normalized but rescaled to an unsigned integer)
    - [ ] Export masks as binary masks or segmentation masks for downstream applications
    - [x] Improve "finished" asthetics/state of progress bar
      - [x] Completed message for task --> used CompletableFutures to do this
      - [x] Cancelled message --> progress value is set to 0, no color change
      
  - #### Fix known bugs/problems, improve backend
    - [ ] Remove ignore classes, ROI, and Unclassified PathClasses from available compartment choices
    - [ ] Trim tiles to image dimensions (for asthetics)  
    - [ ] On GUI close, prompt user with dialog if running a task and cancel running tasks.
    - [ ] On switching projects or images, make sure GUI is updated. Handle running tasks.
      - [ ] Restrict switching projects during a run? Dialog box to cancel tasks?
      - [ ] Restrict swithing images during a run? Dialog box to cancel tasks?
    - [ ] Check for problems on missing target channels (ColorTransforms)
    - [ ] Improve scoring (getTargetsIntensity function) to be even faster through ImageOps? Notes in code.
    - [ ] Make the backend more accessible outside GUI for use in scripting
      - [x] Separated QIIMAQuantBackend into it's own class. 
      - [ ] Make a QIIMAQuantBackend builder and then configure it so that the methods can be used to run on the images via scripting (like other scripting extensions in QuPath [StarDist, Cellpose, etc.])
  
  - #### Test for more bugs and interface & workflow usability

- ### QIIMA Compartment Builder
  - [ ] Streamline CompMaker UI, integrate with PixelClassificationOverlays, work on backend functions
    - [ ] Extend ImageOps classes to support bitwise operations for computing fast union, difference, or intersection of masks
    - [x] Improve QuPath ROI to OpenCV Mat casting/transformation functions to not rely on ImageJ
    - [ ] Make any new operations fully scriptable in QuPath outside GUI backend
  - [ ] Save/load through serialization of GUI state/parameters into a reloadable preferably human readable protocol file
  - [ ] Undo/redo system that can be integrated with QuPath's scripting workflows
  - [ ] Resultant validator to throw errors for binding loops or other invalid operations before computing mask resultant ImageOps
  - [ ] Determine best execution order of dependent resultants
    - [ ] Handle drag and drop of operations?

- ### Future workflows
  - Integrate cell segmentation and subcellular compartment tools into QuPath-CompAnalysis workflow
  - Integrate custom or ML-generated segmentation masks into QuPath-CompAnalysis workflow

