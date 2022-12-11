package qupath.extension.qiimia;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.indexer.*;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_cudaarithm;
import org.bytedeco.opencv.opencv_core.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.lib.images.servers.ColorTransforms;
import qupath.opencv.tools.OpenCVTools;

import java.awt.image.BufferedImage;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QiimiaQuantBackendTest {

    @Test
    void opencvGPUCalc() {
        if(opencv_core.getCudaEnabledDeviceCount() == 0){
            System.out.println("No CUDA enabled device available");
//            no GPU available
            return;
        }

        System.out.println("Found CUDA enabled device:");
        opencv_core.printCudaDeviceInfo(0);
        try {
            System.out.println("Testing GPU calculation...");
            Mat maskMat = new Mat(100, 100, opencv_core.CV_8UC1);
            UByteRawIndexer maskIndx = maskMat.createIndexer();
            // Populate with some values
            for (int b = 0; b < maskMat.channels(); b++) {
                for (int y = 0; y < maskMat.rows(); y++) {
                    for (int x = 0; x < maskMat.cols(); x++) {
                        if (x < 50 && y < 50) {
                            maskIndx.put(y, x, b, 255);
                        } else {
                            maskIndx.put(y, x, b, 0);
                        }
                    }
                }
            }
            maskIndx.release();

            GpuMat gpuMaskMat = new GpuMat(maskMat);
//            BufferedImage maskImg = OpenCVTools.matToBufferedImage(maskMat);
            int nonZeroMaskNum = opencv_cudaarithm.countNonZero(maskMat);
            for (int j = 0; j < 4; j++) {
//            Mat imgMat = new Mat(100, 100, opencv_core.CV_8UC1);
                Mat imgMat = new Mat(100, 100, opencv_core.CV_32FC1);
                FloatRawIndexer imgIndx = imgMat.createIndexer();
//            UByteRawIndexer imgIndx = imgMat.createIndexer();
                // Populate with some values (trying to draw circle)
                double rsq = Math.pow(25, 2);
                for (int y = 0; y < imgMat.rows(); y++) {
                    for (int x = 0; x < imgMat.cols(); x++) {
                        if (Math.pow((y - 100 / 2), 2) + Math.pow((x - 100 / 2), 2) <= rsq) {
                            imgIndx.put(y, x, (float) 255.0);
//                        imgIndx.put(y, x, 255);
                        } else {
                            imgIndx.put(y, x, (float) 0.0);
//                        imgIndx.put(y, x, 0);
                        }
                    }
                }
                imgIndx.release();
//                BufferedImage imgImg = OpenCVTools.matToBufferedImage(imgMat);
                GpuMat gpuChannelMat = new GpuMat(imgMat);
                GpuMat statsGpu = new GpuMat();
                opencv_cudaarithm.meanStdDev(gpuChannelMat, statsGpu, gpuMaskMat);
                Mat channelStats = new Mat();

                statsGpu.download(channelStats);
                double tarMean = channelStats.createIndexer().getDouble(0, 0);
                double tarStdev = channelStats.createIndexer().getDouble(0, 1);
                System.out.println(MessageFormat.format("Ch: {0} Mean: {1} StdDev: {2}", j, tarMean, tarStdev));

                statsGpu.release();
                gpuChannelMat.release();
                imgMat.release();
                channelStats.release();
            }
        } catch(Exception e){
            Assertions.fail("GPU Calc Exception: " + e);
        }
    }
}