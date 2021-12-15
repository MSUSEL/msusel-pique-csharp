/**
 * This copy of Woodstox XML processor is licensed under the
 * Apache (Software) License, version 2.0 ("the License").
 * See the License for details about distribution rights, and the
 * specific rights regarding derivate works.
 *
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/
 *
 * A copy is also included in the downloadable source code package
 * containing Woodstox, in file "ASL2.0", under the same directory
 * as this file.
 */
package piquecsharp.runnable;
import java.io.IOException;
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
import pique.utility.PiqueProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Utility driver class responsible for running the calibration module's procedure.
 * This uses a benchmark repository, quality model description, directory of comparison matrices, and
 * instances of language-specific analysis tools as input to perform a 3 step process of
 * (1) Derive thresholds
 * (2) Elicitate weights
 * (3) Apply these results to the quality model to generate a fully derived quality model
 */
public class QualityModelDeriver {
    private static final Logger LOGGER = LoggerFactory.getLogger(QualityModelDeriver.class);

    public QualityModelDeriver(String propertiesPath) {
        init(propertiesPath);
    }

    public QualityModelDeriver() {
        init(null);
    }
    private void init(String propertiesPath){
        LOGGER.info("Begin deriver");

        Properties prop = null;
        try{
            prop = propertiesPath == null ? PiqueProperties.getProperties() : PiqueProperties.getProperties(propertiesPath);
        }catch (IOException e) {
            e.printStackTrace();
        }

        Path blankqmFilePath = Paths.get(prop.getProperty("blankqm.filepath"));
        Path derivedModelFilePath = Paths.get(prop.getProperty("results.directory"));
        String projectRootFlag = prop.getProperty("target.flag");
        Path benchmarkRepo = Paths.get(prop.getProperty("benchmark.repo"));
        Path resources = Paths.get(prop.getProperty("blankqm.filepath")).getParent();
        // run roslynator
        ITool roslynatorLoc = new RoslynatorLoc(Paths.get(prop.getProperty("roslynator.tool.root")), Paths.get(prop.getProperty("msbuild.bin")));
        ITool roslynator = new RoslynatorAnalyzer(Paths.get(prop.getProperty("roslynator.tool.root")), Paths.get(prop.getProperty("msbuild.bin")));
        Set<ITool> tools = Stream.of(roslynatorLoc, roslynator).collect(Collectors.toSet());
        QualityModelImport qmImport = new QualityModelImport(blankqmFilePath);
        QualityModel qmDescription = qmImport.importQualityModel();
        qmDescription = pique.utility.TreeTrimmingUtility.trimQualityModelTree(qmDescription);
        QualityModel derivedQualityModel = QualityModelDeriver.deriveModel(qmDescription, tools, benchmarkRepo, projectRootFlag);
        Path jsonOutput = new QualityModelExport(derivedQualityModel)
                .exportToJson(derivedQualityModel
                        .getName(), derivedModelFilePath);

        System.out.println("Quality Model derivation finished. You can find the file at " + jsonOutput.toAbsolutePath().toString());
        LOGGER.info("Quality Model derivation finished. You can find the file at " + jsonOutput.toAbsolutePath().toString());
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
