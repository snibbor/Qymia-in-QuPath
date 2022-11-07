package qupath.extension.qiimia;

import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.objects.classes.PathClass;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QiimiaQuantPreset implements Serializable {
    private List<ColorTransforms.ColorTransform> targetTransforms = new ArrayList<>();
    private List<Double> exposureTimes = new ArrayList<>();
    private Set<PathClass> compartments;
    private Set<PathClass> ignoreClasses;
    private Set<PathClass> roiClasses;
    private Map<String, Object> params = new HashMap<>();
    public QiimiaQuantPreset(LinkedHashMap<ColorTransforms.ColorTransform, Double> selTargets,
                             Set<PathClass> selCompartments,
                             Set<PathClass> ignoreClasses,
                             Set<PathClass> roiClasses,
                             Map<String, Object> params){
        for(Map.Entry<ColorTransforms.ColorTransform, Double> entry : selTargets.entrySet()){
            this.targetTransforms.add(entry.getKey());
            this.exposureTimes.add(entry.getValue());
        }
        this.compartments = selCompartments;
        this.ignoreClasses = ignoreClasses;
        this.roiClasses = roiClasses;
        this.params = params;
    }

    Map<ColorTransforms.ColorTransform, Double> getTargets(){
//      rebuild map
        Map<ColorTransforms.ColorTransform, Double> targets = new HashMap<>();
        for(int i = 0; i < targetTransforms.size(); i++){
            targets.put(targetTransforms.get(i), exposureTimes.get(i));
        }
        return targets;
    }
    Set<PathClass> getCompartments(){
        return compartments;
    }
    Set<PathClass> getIgnoreClasses(){
        return ignoreClasses;
    }
    Set<PathClass> getROIClasses(){
        return roiClasses;
    }
    Map<String, Object> getParams(){
        return params;
    }
}
