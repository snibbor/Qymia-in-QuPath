package qupath.extension.qiimia;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.UnaryOperator;

public class QiimiaUtils {


    public QiimiaUtils(){};

    public static TextField formatTextFields(TextField textField, String format, String defaultValue) {
        switch(format.toLowerCase()) {
            case "string": {
                break;
            }
            case "pos_double":{
                UnaryOperator<TextFormatter.Change> filter = change -> {
                    String newText = change.getControlNewText();
                    if (newText.matches("^(([1-9][0-9]*)|0)?(\\.[0-9]*)?$|^$")) {
                        return change;
                    }
                    return null;
                };

                StringConverter<Double> converter = new DoubleStringConverter() {
                    @Override
                    public Double fromString(String s) {
                        if (s.isEmpty()) return null;
                        else if (Double.parseDouble(s) == 0.0) return 0.0;
                        return super.fromString(s);
                    }
                };

                TextFormatter<Double> textFormatter;
                if(defaultValue!=null) {
                    textFormatter = new TextFormatter<Double>(converter, Double.parseDouble(defaultValue), filter);
                } else{
                    textFormatter = new TextFormatter<Double>(converter, null, filter);
                }

                textField.setTextFormatter(textFormatter);
                break;

            }
            case "integer": {
                UnaryOperator<TextFormatter.Change> filter = change -> {
                    String newText = change.getControlNewText();
                    if (newText.matches("^\\d{0,4}$|^$")) {
                        return change;
                    }
                    return null;
                };

                StringConverter<Integer> converter = new IntegerStringConverter() {
                    @Override
                    public Integer fromString(String s) {
                        if (s.isEmpty()) return null;
                        else if (Integer.parseInt(s) == 0.0) return 0;
                        return super.fromString(s);
                    }
                };

                TextFormatter<Integer> textFormatter;
                if(defaultValue!=null) {
                    textFormatter = new TextFormatter<Integer>(converter, Integer.parseInt(defaultValue), filter);
                } else{
                    textFormatter = new TextFormatter<Integer>(converter, null, filter);
                }

                textField.setTextFormatter(textFormatter);
                break;
            }
            case "percent": {
                UnaryOperator<TextFormatter.Change> filter = change -> {
                    String newText = change.getControlNewText();
                    if (newText.matches("^100(\\.0{0,2})?$|^\\d{0,2}(\\.\\d{0,2})?$")) {
                        return change;
                    }
                    return null;
                };
                StringConverter<Double> converter = new DoubleStringConverter() {
                    @Override
                    public Double fromString(String s) {
                        if (s.isEmpty()) return 0.0 ;
//    		                else if(Double.parseDouble(s) == 0) return 0.0;
                        return super.fromString(s);
                    }
                };

                TextFormatter<Double> textFormatter;
                if(defaultValue!=null) {
                    textFormatter = new TextFormatter<Double>(converter, Double.parseDouble(defaultValue), filter);
                } else{
                    textFormatter = new TextFormatter<Double>(converter, null, filter);
                }

                textField.setTextFormatter(textFormatter);
                break;
            }
            case "0-1": {
                UnaryOperator<TextFormatter.Change> filter = change -> {
                    String newText = change.getControlNewText();
                    if (newText.matches("^0{0,1}(\\.\\d{0,3})?$|^1(\\.0{0,3})?$")) {
                        return change;
                    }
                    return null;
                };

                StringConverter<Double> converter = new DoubleStringConverter() {
                    @Override
                    public Double fromString(String s) {
                        if (s.isEmpty()) return 0.0 ;
//    		                else if(Double.parseDouble(s) == 0) return 0.0;
                        return super.fromString(s);
                    }
                };

                TextFormatter<Double> textFormatter;
                if(defaultValue!=null) {
                    textFormatter = new TextFormatter<Double>(converter, Double.parseDouble(defaultValue), filter);
                } else{
                    textFormatter = new TextFormatter<Double>(converter, null, filter);
                }

                textField.setTextFormatter(textFormatter);
                break;
            }
        }
        return textField;
    }

    /**
     * Modified to accept batch mean, std dev, min, max, and num of values as input to update running stats.
     *
     * Helper class for computing basic statistics from values as they are added.
     * <p>
     * This is useful e.g. when iterating through pixels, computing statistics from masked/labelled values.
     * <p>
     * Warning! This maintains a sum as a double - for many pixels and/or 16-bit data this may lead to imprecision
     * (although for small regions, and especially optical densities having low values, it should be fine).
     * <p>
     * A warning is logged for particularly large values.
     *
     * @author Jack Robbins
     *
     */
    public static class RunningStatistics {

        private static Logger logger = LoggerFactory.getLogger(qupath.lib.analysis.stats.RunningStatistics.class);

        // See http://www.johndcook.com/standard_deviation.html

        private static double LARGE_DOUBLE_THRESHOLD = Math.pow(2, 53) - 1; // Largest integer that can be stored, maintaining accuracy of all smaller integers?

        private int numNaNs = 0;

        protected long size = 0;
        private double sum = 0, min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;

        private double m1 = 0, s1 = 0;

        /**
         * Default constructor.
         */
        public RunningStatistics() {}

        /**
         * Get count of the number of non-NaN values added.
         * @return
         *
         * @see #getNumNaNs()
         */
        public long size() {
            return size;
        }

        /**
         * Add another value; NaN values are counted but do not contribute to the statistics.
         *
         * @param val
         *
         * @see #getNumNaNs()
         */
        public void addValue(double val) {
            if (Double.isNaN(val)) {
                numNaNs++;
                return;
            }
            // Update the count
            size++;
            // Update the running sum, min & max
            sum += val;
            if (val < min)
                min = val;
            if (val > max)
                max = val;
            // Update values for variance calculation
            if (size == 1) {
                m1 = val;
            } else {
                double mNew = m1 + (val - m1) / size;
                s1 = s1 + (val - m1)*(val - mNew);
                m1 = mNew;
            }
        }

        public void addBatchStats(double bMean, double bStdDev, double bMin, double bMax, int batchNumOfValues){
            if (Double.isNaN(bMean) || Double.isNaN(bStdDev) || Double.isNaN(bMin) || Double.isNaN(bMax)) {
                logger.error("batch stat values cannot be null! skipping...");
                return;
            }
            // adding the first batch stats
            if(size == 0){
                size += batchNumOfValues;
                sum += bMean*batchNumOfValues;
                m1 = bMean;
                s1 = Math.pow(bStdDev, 2)*batchNumOfValues;
                min = bMin;
                max = bMax;
                return;
            }
            // need this for combined variance calculation
            double newVariance = Math.pow(bStdDev, 2);
            double oldVariance = s1 / size;
            double oldMean = m1;
            double oldSize = size;
            double batchNum = batchNumOfValues;
            // update count
            size += batchNumOfValues;
            // Update the running sum
            sum += bMean*batchNum;
            // update combined mean
            m1 = (oldSize/size)*m1 + (batchNum/size)*bMean;
            // update combined variance
//          https://notmatthancock.github.io/2017/03/23/simple-batch-stat-updates.html
            double combVariance =  newVariance*(batchNum/size) + oldVariance*(oldSize/size) + Math.pow(bMean - oldMean, 2)*(oldSize*batchNum)/Math.pow(oldSize + batchNum, 2);
            // to maintain consistency when you add a new value...
            s1 = combVariance*(size-1);
            // update min and max
            if(bMin < min){
                min = bMin;
            }
            if(bMax > max){
                max = bMax;
            }
        }

        /**
         * Get count of the number of NaN values added.
         * @return
         *
         * @see #size()
         */
        public long getNumNaNs() {
            return numNaNs;
        }

        /**
         * Get the sum of all non-NaN values that were added.
         * @return
         */
        public double getSum() {
            if (Math.abs(sum) > LARGE_DOUBLE_THRESHOLD)
                logger.warn("Sum in {} is particularly large ({}), beware imprecision!", getClass().getSimpleName(), sum);
            return sum;
        }

        /**
         * Get the mean of all non-NaN values that were added.
         * @return
         */
        public double getMean() {
            double testMean = getSum()/size;
            return (size == 0) ? Double.NaN : m1;
        }

        /**
         * Get the variance of all non-NaN values that were added.
         * @return
         */
        public double getVariance() {
            if (Math.abs(s1) > LARGE_DOUBLE_THRESHOLD)
                logger.warn("Variance parameter s1 in {} is particularly large ({}), beware imprecision!", getClass().getSimpleName(), sum);
            return (size <= 1) ? Double.NaN : s1 / (size - 1);
        }

        /**
         * Get the standard deviation of all non-NaN values that were added.
         * @return
         */
        public double getStdDev() {
            return Math.sqrt(getVariance());
        }

        /**
         * Get the minimum non-NaN value added.
         * @return the minimum value, or NaN if no values are available.
         */
        public double getMin() {
            return (size == 0) ? Double.NaN : min;
        }

        /**
         * Get the maximum non-NaN value added.
         * @return the maximum value, or NaN if no values are available.
         */
        public double getMax() {
            return (size == 0) ? Double.NaN : max;
        }

        /**
         * Get the range, i.e. maximum - minimum values.
         * @return
         */
        public double getRange() {
            return (size == 0) ? Double.NaN : max - min;
        }

        @Override
        public String toString() {
            return String.format("%s Mean: %.2f, Std.dev: %.2f, Min: %.2f, Max: %.2f", qupath.lib.analysis.stats.RunningStatistics.class.getSimpleName(), getMean(), getStdDev(), getMin(), getMax());
        }

    }
}
