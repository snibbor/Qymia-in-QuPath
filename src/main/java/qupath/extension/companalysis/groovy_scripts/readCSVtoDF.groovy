package qupath.extension.companalysis.groovy_scripts

import java.io.BufferedReader;
import java.io.FileReader;

def readCSVtoDF(String csvpath, String indexName){
    // Create BufferedReader
    BufferedReader csvReader = new BufferedReader(new FileReader(csvpath));
    Map<String, ArrayList<String>> dataframe = new LinkedHashMap<String, ArrayList<String>>();
    header = csvReader.readLine();
//    header = "test,test1,test2";
    ArrayList<String> headerContent = new ArrayList<String>(header.split(",").toList());
//    println headerContent
    int index = headerContent.indexOf(indexName);
//    println index
//    println headerContent[index]
    int r = 0;
    useRowNumbers = false;
    if(index == -1){
        prinln String.format('Header does not contain %s! Defaulting to using row numbers...', indexName)
        useRowNumbers = true;
    }
    dataframe.put('Header', headerContent);
    while((row = csvReader.readLine()) != null){
//        println row
        ArrayList<String> rowContent = new ArrayList<String>(row.split(",").toList());
        if (useRowNumbers){
            dataframe.put(r, rowContent);
            r+=1;
        } else {
            rowName = rowContent[index];
            int j = 1;
            while (true){
                if (dataframe.containsKey(rowName)){
                    println String.format('rowName %s is duplicated! Resolving by appending integer...', rowName);
                    rowName = String.format('%1$s_%2$x',rowContent[index],j);
                    j+=1;
                } else {
                    break;
                }
            }
            dataframe.put(rowName, rowContent);
        }
    }
//    println dataframe;
    return dataframe;
}

def csvpath = "E:/AQUA/HER2-V2/02-09-22/YTMA263-17-39_29D8_200/AQUA_20220210_202707/YTMA263-17-39_29D8_200.csv"
//println csvpath
def indexName = "Spot #"
//println indexName

dataframe = readCSVtoDF(csvpath, indexName)

dataframe.get('Header')

// Get column layout
j=0;
dataframe.get('Header').forEach{it->
    println String.format("%s, %s", j, it);
    j +=1;
}
