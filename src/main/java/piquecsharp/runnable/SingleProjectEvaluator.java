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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pique.analysis.ITool;
import pique.evaluation.Project;
import pique.model.Diagnostic;
import pique.model.QualityModel;
import pique.model.QualityModelImport;
import tool.RoslynatorAnalyzer;
import tool.RoslynatorLoc;
import utilities.PiqueProperties;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Behavioral class responsible for running TQI evaluation of a single project
 */
// TODO (1.0): turn into static methods (maybe unless logger problems)
public class SingleProjectEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleProjectEvaluator.class);

    private Project project;

    // constructor
    public SingleProjectEvaluator(String propertiesPath){
        init(propertiesPath);
    }

    public SingleProjectEvaluator(){
        init(null);
    }


   public void init (String propertiesLocation) {
       LOGGER.info("Starting Analysis");
       Properties prop = PiqueProperties.getProperties(propertiesLocation);

       Path projectRoot = Paths.get(prop.getProperty("project.root"));
       Path resultsDir = Paths.get(prop.getProperty("results.directory"));

       LOGGER.info("Project to analyze: " + projectRoot.toString());

       Path qmLocation = Paths.get(prop.getProperty("derived.qm"));

       ITool roslynatorLoc = new RoslynatorLoc(Paths.get(prop.getProperty("roslynator.tool.root")), Paths.get(prop.getProperty("msbuild.bin")));
       ITool roslynator = new RoslynatorAnalyzer(Paths.get(prop.getProperty("roslynator.tool.root")), Paths.get(prop.getProperty("msbuild.bin")));
       Set<ITool> tools = Stream.of(roslynatorLoc, roslynator).collect(Collectors.toSet());

       Set<Path> projectRoots = new HashSet<>();
       File[] filesToAssess = projectRoot.toFile().listFiles();
       for (File f : filesToAssess) {
           if (f.isFile()) {
               projectRoots.add(f.toPath());
           }
       }
       for (Path projectPath : projectRoots) {
           Path outputPath = runEvaluator(projectPath, resultsDir, qmLocation, tools);
           LOGGER.info("output: " + outputPath.getFileName());
           System.out.println("output: " + outputPath.getFileName());
       }
   }

    //region Get / Set
    public Project getEvaluatedProject() {
        return project;
    }

    /**
     * Entry point for running single project evaluation. The library assumes the user has extended PIQUE
     * by implementing ITool with language-specific functionality.
     *
     * This method then evaluates the measures, properties, characteristics, and TQI according to the provided
     * quality model.
     *
     * @param projectDir
     *      Path to root directory of project to be analyzed.
     * @param resultsDir
     *      Directory to place the analysis results in. Does not needy to exist initially.
     * @param qmLocation
     *      Path to a completely derived quality model (likely .xml format).
     * @return
     *      The path to the produced quality analysis file on the hard disk.
     */
    public Path runEvaluator(Path projectDir, Path resultsDir, Path qmLocation, Set<ITool> tools) {

        // Initialize data structures
        initialize(projectDir, resultsDir, qmLocation);
        QualityModelImport qmImport = new QualityModelImport(qmLocation);
        QualityModel qualityModel = qmImport.importQualityModel();
        project = new Project(projectDir.getFileName().toString(), projectDir, qualityModel);

        // Validate State
        // TODO: validate more objects such as if the quality model has thresholds and weights, are there expected diagnostics, etc
        validatePreEvaluationState(project);

        // Run the static analysis tools process
        Map<String, Diagnostic> allDiagnostics = new HashMap<>();
        tools.forEach(tool -> {
            allDiagnostics.putAll(runTool(projectDir, tool));
        });

        // Apply tool results to Project object
        project.updateDiagnosticsWithFindings(allDiagnostics);

        BigDecimal tqiValue = project.evaluateTqi();

        // Create a file of the results and return its path
        return project.exportToJson(resultsDir,true);
    }


    /**
     * Assert input parameters are valid and create the output folder
     *
     * @param projectDir
     *      Path to directory holding the project to be evaluated. Must exist.
     * @param resultsDir
     *      Directory to place the analysis results in. Does not need to exist initially.
     * @param qmLocation
     *      Path to the quality model file. Must exist.
     */
    private void initialize(Path projectDir, Path resultsDir, Path qmLocation) {
        if (!projectDir.toFile().exists()) {
            throw new IllegalArgumentException("Invalid projectDir path given.");
        }
        if (!qmLocation.toFile().exists() || !qmLocation.toFile().isFile()) {
            throw new IllegalArgumentException("Invalid qmLocation path given.");
        }

        resultsDir.toFile().mkdirs();
    }


    /**
     * Run static analysis tool evaluation process:
     *   (1) run static analysis tool
     *   (2) parse: get object representation of the diagnostics described by the QM
     *   (3) make collection of diagnostic objects
     *
     * @param projectDir
     *      Path to root directory of project to be analyzed.
     * @param tool
     *      Analyzer provided by language-specific instance necessary to find findings of the project.
     * @return
     *      A mapping of (Key: property name, Value: measure object) where the measure objects contain the
     *      static analysis findings for that measure.
     */
    private Map<String, Diagnostic> runTool(Path projectDir, ITool tool) {

        // (1) run static analysis tool
        // TODO: turn this into a temp file that always deletes on/before program exit
        Path analysisOutput = tool.analyze(projectDir);
        LOGGER.info(analysisOutput.toString());

        // (2) prase output: make collection of {Key: diagnostic name, Value: diagnostic objects}  b
        return tool.parseAnalysis(analysisOutput);
    }


    /**
     * Sequence of state checks of the project's quality model before running evaluation.
     * Throws runtime error if any expected state is not achieved.
     *
     * @param project
     *      The project under evaluation. This project should have a contained qualityModel with
     *      weight and threshold instances.
     */
    // TODO (1.0) Update once basic tests passing
    private void validatePreEvaluationState(Project project) {
        QualityModel projectQM = project.getQualityModel();

        if (projectQM.getTqi().getWeights() == null) {
            throw new RuntimeException("The project's quality model does not have any weights instantiated to its TQI node");
        }

        projectQM.getQualityAspects().values().forEach(characteristic -> {

            if (characteristic.getWeights() == null) {
                throw new RuntimeException("The project's quality model does not have any weights instantiated to its characteristic node");
            }
        });
    }
}
