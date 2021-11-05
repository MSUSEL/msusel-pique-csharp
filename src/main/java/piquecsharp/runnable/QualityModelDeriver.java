/**
 * MIT License
 * Copyright (c) 2019 Montana State University Software Engineering Labs
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package piquecsharp.runnable;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import pique.analysis.ITool;
import pique.calibration.IBenchmarker;
import pique.calibration.IWeighter;
import pique.calibration.WeightResult;
import pique.model.Measure;
import pique.model.ModelNode;
import pique.model.QualityModel;
import pique.model.QualityModelExport;
import pique.model.QualityModelImport;
import tool.*;
import utilities.PiqueProperties;
/**
 * Utility driver class responsible for running the calibration module's procedure.
 * This uses a benchmark repository, quality model description, directory of comparison matrices, and
 * instances of language-specific analysis tools as input to perform a 3 step process of
 * (1) Derive thresholds
 * (2) Elicitate weights
 * (3) Apply these results to the quality model to generate a fully derived quality model
 */
public class QualityModelDeriver {
    public static void main(String[] args){
        new QualityModelDeriver();
    }
    public QualityModelDeriver(){
        init();
    }
    private void init(){
        Properties prop = PiqueProperties.getProperties();
        Path blankqmFilePath = Paths.get(prop.getProperty("blankqm.filepath"));
        Path derivedModelFilePath = Paths.get(prop.getProperty("results.directory"));
        // Initialize objects
        //String projectRootFlag = "";
        String projectRootFlag = prop.getProperty("target.flag");
        Path benchmarkRepo = Paths.get(prop.getProperty("benchmark.repo"));
        Path resources = Paths.get(prop.getProperty("blankqm.filepath")).getParent();
        // run roslynator
        ITool roslynatorLoc = new RoslynatorLoc(Paths.get(prop.getProperty("roslynator.tool.root")), Paths.get(prop.getProperty("msbuild.bin")));
        ITool roslynator = new RoslynatorAnalyzer(Paths.get(prop.getProperty("roslynator.tool.root")), Paths.get(prop.getProperty("msbuild.bin")));
        Set<ITool> tools = Stream.of(roslynatorLoc, roslynator).collect(Collectors.toSet());
        //ITool cvebinToolWrapper = new CVEBinToolWrapper();
        //ITool cweCheckerWrapper = new CWECheckerToolWrapper();
        //ITool yaraRulesWrapper = new YaraRulesToolWrapper(resources);
        //Set<ITool> tools = Stream.of(cvebinToolWrapper).collect(Collectors.toSet());
        QualityModelImport qmImport = new QualityModelImport(blankqmFilePath);
        QualityModel qmDescription = qmImport.importQualityModel();
        qmDescription = pique.utility.TreeTrimmingUtility.trimQualityModelTree(qmDescription);
        QualityModel derivedQualityModel = QualityModelDeriver.deriveModel(qmDescription, tools, benchmarkRepo, projectRootFlag);
        Path jsonOutput = new QualityModelExport(derivedQualityModel)
                .exportToJson(derivedQualityModel
                        .getName(), derivedModelFilePath);

        System.out.println("Quality Model derivation finished. You can find the file at " + jsonOutput.toAbsolutePath().toString());
    }
    public static QualityModel deriveModel(QualityModel qmDesign, Set<ITool> tools,
                                           Path benchmarkRepository, String projectRootFlag) {

        // (1) Derive thresholds
        IBenchmarker benchmarker = qmDesign.getBenchmarker();
        Map<String, BigDecimal[]> measureNameThresholdMappings = benchmarker.deriveThresholds(
                benchmarkRepository, qmDesign, tools, projectRootFlag);
        // (2) Elicitate weights
        IWeighter weighter = qmDesign.getWeighter();
        // TODO (1.0): Consider, instead of weighting all nodes in one sweep here, dynamically assigning IWeighter
        //  ojbects to each node to have them weight using JIT evaluation functions.
        Set<WeightResult> weights = weighter.elicitateWeights(qmDesign);
        // TODO: assert WeightResult names match expected TQI, QualityAspect, and ProductFactor names from quality model description
        // (3) Apply results to nodes in quality model by matching names
        // Thresholds (ProductFactor nodes)
        // TODO (1.0): Support now in place to apply thresholds to all nodes (if they exist), not just measures. Just
        //  need to implement.
        measureNameThresholdMappings.forEach((measureName, thresholds) -> {
            Measure measure = (Measure) qmDesign.getMeasure(measureName);
            measure.setThresholds(thresholds);
        });
        // Weights (TQI and QualityAspect nodes)
        weights.forEach(weightResult -> {
            Map<String, ModelNode> allNodes = qmDesign.getAllQualityModelNodes();
            allNodes.get(weightResult.getName()).setWeights(weightResult.getWeights());
        });
        return qmDesign;
    }

}
