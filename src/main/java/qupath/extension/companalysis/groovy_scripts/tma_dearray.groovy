package qupath.extension.companalysis.groovy_scripts

import qupath.lib.images.servers.ImageServer
import qupath.lib.objects.PathObject
import qupath.lib.images.writers.ome.OMEPyramidWriter
import static qupath.lib.gui.scripting.QPEx.*

import javafx.application.Platform
import qupath.lib.images.ImageData
import qupath.lib.images.servers.ImageServerProvider
import qupath.lib.images.servers.TransformedServerBuilder


import javax.imageio.ImageIO
import java.awt.Color
import java.awt.image.BufferedImage

// Get the main QuPath data structures
def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def server = imageData.getServer()
server.getPixelType().getBitsPerPixel()

// Request all objects from the hierarchy & filter only the annotations
def annotations = hierarchy.getAnnotationObjects()

// Define downsample value for export resolution & output directory, creating directory if necessary
def downsample = 1.0
def pathOutput = buildFilePath(PROJECT_BASE_DIR, 'tma_dearrayed')
def tilesize = 256
//def name = getProjectEntry().getImageName()
def name = getProjectEntry().getImageName().split('@')[0]

//def outputDownsample = 1
//def pyramidscaling = 2
def compression = OMEPyramidWriter.CompressionType.J2K
mkdirs(pathOutput)

getCurrentHierarchy().getTMAGrid().getTMACoreList().each{
    if(!it.isMissing()){
        File file = new File(pathOutput+'/'+it.getName()+'_'+name+'.ome.tif')
        println 'file exists: ' + file.exists()
        if(!file.exists()){
            def region = RegionRequest.createInstance(server.getPath(), downsample, it.getROI())
            new OMEPyramidWriter.Builder(server)
                .region(region)
                .compression(compression)
                .parallelize()
                .tileSize(tilesize)
                .downsamples(downsample)
                // .channelsInterleaved() // Usually faster
                // .scaledDownsampling(outputDownsample, pyramidscaling)
                .build()
                .writePyramid(pathOutput+'/'+it.getName()+'_'+name+'.ome.tif')
         } else{
            println 'exists.. skipping..'
         }
    } else{
        println it.getName() + " is missing. skipping..."
    }
}


