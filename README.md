# Qymia in QuPath
### Quantitative Immunofluorescence/Immunohistochemistry Molecular Image Analysis (QYMIA, Qymia, "kee-m-ee-a" or "k-EYE-m-ee-a") in QuPath

Fast, high-throughput quantification of multiplex immunofluorescence and/or immunohistochemistry staining in clinical specimens (tissue microarrays, whole tissue sections) by molecular compartmentalization techniques (tissue and cell-based) in QuPath.

To use (see below for example):
1) Create compartments/segmentations/annotations inside of QuPath
2) Run Qymia Quant on those compartments to quantify target biomarker expression of interest using experiment parameters.
3) (optionally) Constuct calibration curves and convert Qymia scores to adjust for batch-to-batch measurements

References:
- Camp RL, Dolled-Filhart M, King BL, Rimm DL. Quantitative analysis of breast cancer tissue microarrays shows that both high and normal levels of HER2 expression are associated with poor outcome. Cancer Res. 2003 Apr 1;63(7):1445-8. PMID: 12670887.
- McCabe A, Dolled-Filhart M, Camp RL, Rimm DL. Automated quantitative analysis (AQUA) of in situ protein expression, antibody concentration, and prognosis. J Natl Cancer Inst. 2005 Dec 21;97(24):1808-15. doi: 10.1093/jnci/dji427. PMID: 16368942.
- Dr. David Rimm's Laboratory at Yale School of Medicine (https://medicine.yale.edu/lab/rimm/)

## Workflow Demo Version 0.0.1:

This workflow uses the QuPath built-in SimpleThresholder as well as Qymia Quant

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
- ### Qymia Quant
  - #### Main features
    - [X] Simplify data export for TMA spots and AQUAMine support.
    - [x] Quant Presets and Preset Panel alongside linear calibration curve support to further automate Qymia workflow.
    - [ ] Fast cell & subcellular compartment intensity quantification
      - [ ] Cell, nuclei, cytoplasm, and membrane --> QuPath default still.
      - [ ] Ability to quantify custom subcellular compartment definitions --> e.g. mitochondria (labeled with TOM20) compartments for every cell object. EE, MVE, RE, LE, & lysosomes for every cell object.
    - [x] Grid/Tile calculation of compartment scores + overlay image (measurement map?)
      - [x] Export measurements to tab delim .csv ~~or .json?~~
      - [ ] Grid/Tile calculation is very very slow for TMA because of the merge annotations step, which is unnecessary --> write a slightly different method for Grid/Tile calculation for TMA where the Tile bounds are for each TMA Core Objects and the computeTiledROIs are parallelized per core.
    - [x] Minimize ImageJ, OpenCV image/matrix datatype conversions 
      - OpenCV implementation reduced memory consumption ~ same speed
    - [x] Need to fix memory leak from ~~ForkJoinPool~~ --> wasn't a memory leak due to thread pooling, it was the ImageRegion tile cache filling up!
  
  - #### Advanced settings
    - [ ] Improve score normalization for all aspects of image brightness for fluoresence & trans-illumination microscopy. (Source intensity, transmission/fluorophore quantum efficiency, light collection, what else?)
      - [x] Normalize image brightness for fluorescence by microscope objective NA & Mag. (Brightness ~ (NA^2/M)^2 Because the objective is also the condenser lens)
      - [x] Does it matter if you calculate DAB OD for different NA/Mag objectives? Condenser optics? How to normalize brightness here? --> Yes. Similar adjustment to above.
      - [ ] Is there a way to make a normalized score for different microscopes with different optical systems (NA/Mag objective/condenser combinations)? Unlikely to be simple, but the scores likely would regress by a proportionality constant. --> perhaps empirically determinable.
    - [x] Options to convert intensity scores for an image or set of images in project using a function (i.e. intensity score --> concentration of target expression (amol or molecules per area) using a standardization array)
      - [x] Regression options to calculate conversion function using standard index array
        - [x] Visuallization of regression fit in QuPath
        - [x] Outlier removal --> through setting core missing/invalid
        - [x] Handle multiple conversion functions/standard curves per project.
        - [x] Methods to save results to file --> set current project conversion functions on save
        - [ ] **Allow multiple measurement converters for each standard index array** 
      - [x] Apply conversion functions/standard curves to images in project
        - [x] Load conversion function parameters from file --> apply based mapping of imagePath -> function name
          - [x] Map functions and images based on CSV/TSV/Excel file --> stain batch map file
          - [x] Interactive GUI to select images for each conversion function --> convert measurements option in QymiaAnalysis -> TMA standard tools
          - [x] Automatic conversion of measurements -> if batch map and measurement converters exist in project, this option is available in QymiaQuant panel
    - [x] Tile size option for px and um
    - [x] Calculate verbose/extra measurements option
    - [x] Permanent user settings/preferences
    - [x] Changeable ignore classes
    - [x] Changeable ROI classes
    - [x] Simpler measurement export options
      - [x] Change GUI controls to use the default QuPath measurement exporter.
      - [x] Put current opened image inside selected export list automatically.
      - [x] Save dialog (if project entry not saved) before export measurements dialog opens.
      - [ ] Option to export measurements as separate files for project entries.
      - [ ] Customize measurement exporter class to:
        - [ ] Export ROI class of annotations/detections only --> filter by PathObject.class and PathClass(es)
        - [ ] Remove empty/unscored measurement entries before export
    - [ ] Export measurement maps/heatmaps for project
      - [ ] Extend QuPath measurement map UI to export map images for project
    - [ ] Export ROI annotation classes as images
    - [ ] Modifiable rescale value (if image data was already normalized but rescaled to an unsigned integer)
    - [ ] Export masks as binary masks or segmentation masks for downstream applications
    - [x] Improve "finished" asthetics/state of progress bar
      - [x] Completed message for task --> used CompletableFutures to do this
      - [x] Cancelled message --> progress value is set to 0, no color change
    - [x] Tooltips --> in progress...
    - [ ] Help > About & Docs link
      
  - #### Fix known bugs/problems, improve backend
    - [x] IT network bug where Advanced Settings panel would not load because of filepath error.. --> fixed by loading all resource at extension entrypoint and initializing parameters using QymiaQuantModel object.
    - [x] May run slower in "Run for project" mode vs. "run" mode. I think this is because how thread pools are allocated during a "run for project" task. When threads > 16, this becomes very noticeable and makes "run for project" mode ~0.75-0.5 as fast. --> resolved.
    - [x] Ignore/exclude annotations displaces causes parent/child objects to be displaced when calculating ROI + Tile quantifications. The result is that either the ROI detection or Tile detections are displaced (not in propper parent/child object hierarchy). This is a problem because tiles are referenced by their parent object in downstream analysis. --> fixed this by editing how compartments are added to ROI during quantification.
      - [x] temporary solution --> run ROI then Tile quantification separately when ignore/exclude annotations are used. This displaces the ROI detection (which is named) and places the Tile detections under the parent ROI/object.
    - [ ] Wrap runQuant into a task so that cancel/force cancel can happen immediately. --> immediate cancellation can cause errors still --> prevented immediate cancellation, still can pose problems.
      - [x] Debug why cancelling sometimes causes image server exceptions for later runs... --> works after wrapping inside task
      - [x] Debug why sometimes the GUI stalls when running the first task but is fine for subsequent tasks --> I think this is from the thread executor. Perhaps using QuPath's thread executors and pools would be better than custom forkJoinPools. --> yes
      - [x] Bug with reloading images because task execution occurs on thread separate from JavaFX
    - [x] Remove ignore classes, ROI, and Unclassified PathClasses from available compartment choices
    - [ ] Trim tiles to image dimensions (for asthetics)?  
    - [ ] On GUI close, prompt user with dialog if running a task and cancel running tasks.
    - [x] On switching projects or images, make sure GUI is updated. Handle running tasks. --> current behavior processes running task and reloads the image on the selected viewer.
      - [ ] Restrict switching projects during a run? Dialog box to cancel tasks?
      - [ ] Restrict swithing images during a run? Dialog box to cancel tasks?
    - [ ] Check for problems on missing target channels (ColorTransforms)
      - [ ] Bug with channel name not found when switching projects, although GUI displays correct channel name. resolves on switching images within project. --> is targetTransforms list updated after switching projects?
    - [X] Improve scoring (getTargetsIntensity function) to be even faster --> rewrote code for OpenCV and added OpenCV CUDA GPU support.
    - [ ] Make the backend more accessible outside GUI for use in scripting
      - [x] Separated QYMIAQuantBackend into it's own class. 
      - [ ] Make a QYMIAQuantBackend builder and then configure it so that the methods can be used to run on the images via scripting (like other scripting extensions in QuPath [StarDist, Cellpose, etc.])
  
  - #### Test for more bugs and interface & workflow usability

- ### Future workflows
  - Integrate cell segmentation and subcellular compartment tools into Qymia workflow
  - Integrate custom or ML-generated segmentation masks into Qymia workflow

