package qupath.extension.qiimia.backend;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;

public class ImageOpsExtras extends ImageOps {
	
	private final static Logger logger = LoggerFactory.getLogger(ImageOpsExtras.class);
	
	
	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	private @interface OpType {
		String value();
	}
	
	public static class Threshold extends ImageOps.Threshold{
		
	}
	
	public static class Core extends ImageOps.Core{
		
	}
	
	@OpType("bitwise")
	public static class Bitwise {
		
		public static ImageOp intersect(Mat source2) {
			return new AndBitwiseOp(source2);
		}
		
		public static ImageOp union(Mat source2) {
			return new OrBitwiseOp(source2);
		}
		
		public static ImageOp difference(Mat source2) {
			return new DiffBitwiseOp(source2);
		}
		
//		public static ImageOp addWithin(Mat source2, int radius) {
//			ImageOps.Filters.closing(radius);
//		}
		

		static class AndBitwiseOp implements ImageOp {
			private Mat source2;
			
			AndBitwiseOp(Mat source2){
				this.source2 = source2;
			}

			@Override
			public Mat apply(Mat input) {
//				opencv_core.and(input, source2);
				Mat dst = new Mat();
				opencv_core.bitwise_and(input, source2, dst);
				return dst;
			}
		}
		
		static class OrBitwiseOp implements ImageOp {
			private Mat source2;
			
			OrBitwiseOp(Mat source2){
				this.source2 = source2;
			}

			@Override
			public Mat apply(Mat input) {
				Mat dst = new Mat();
				opencv_core.bitwise_or(input, source2, dst);
				return dst;
			}
		}
		
		static class DiffBitwiseOp implements ImageOp {
			private Mat source2 = new Mat();
			
			DiffBitwiseOp(Mat source2){
				opencv_core.bitwise_not(source2, this.source2);;
			}

			@Override
			public Mat apply(Mat input) {
				Mat dst = new Mat();
				opencv_core.bitwise_and(input, source2, dst);
				return dst;
			}
		}
		
		
		
	}

}
