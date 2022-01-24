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
package tool;
import pique.analysis.ITool;
import pique.evaluation.DefaultDiagnosticEvaluator;
import pique.model.Diagnostic;
import pique.model.Finding;
import pique.utility.FileUtility;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import piquecsharp.runnable.SingleProjectEvaluator;

/**
 * IToolLOC implementation using Roslynator CLI
 *
 * Roslynator download: https://marketplace.visualstudio.com/items?itemName=josefpihrt.Roslynator2019
 * Roslynator repo: https://github.com/JosefPihrt/Roslynator
 *
 * The .exe should be kept in resources/tools.
 */
public class RoslynatorLoc extends RoslynatorTool implements ITool {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoslynatorLoc.class);

    private Path msBuild;

    /**
     * Constructor.
     * Roslynator analysis needs the MSBuild.exe path
     * (e.g. "C:/Program Files (x86)/Microsoft Visual Studio/2019/Community/MSBuild/Current/Bin")
     *
     * @param toolRoot
     *      pique-csharp tools directory location
     * @param msBuild
     *      Path to Bin folder containing MSBuild.exe
     */
    public RoslynatorLoc(Path toolRoot, Path msBuild) {
        super("RoslynatorLoc", toolRoot);
        this.msBuild = msBuild;
    }
    @Override
    public Path analyze(Path path) {
        path = path.toAbsolutePath();
        LOGGER.info("Path To Analyze " + path);
        File tempResults = new File(System.getProperty("user.dir") +"/out/roslynator_loc_output.txt");
        tempResults.getParentFile().mkdirs();
        // Append .sln or .csproj file to path
        Set<String> targetFiles = FileUtility.findFileNamesFromExtension(path, ".sln", 1);
        if (targetFiles.size() == 1) {
            path = Paths.get(path.toString(), targetFiles.iterator().next() + ".sln");
        }
        else if (targetFiles.size() > 1) {
            throw new RuntimeException("More than one .sln file exists in the given path root directory at path: " +
                    path.toString() + "\nEnsure the directory has only one .sln file to target.");
        }
        else {
            targetFiles = FileUtility.findFileNamesFromExtension(path, ".csproj", 1);
            if (targetFiles.size() == 1) {
                path = Paths.get(path.toString(), targetFiles.iterator().next() + ".csproj");
            }
            else if (targetFiles.size() > 1) {
                throw new RuntimeException("A .sln file not found and more than one .csproj file exists in the give path root directory. " +
                        "Ensure the directory has only one .csproj file to target.");
            }
        }
        // Strings for CLI call
        String tool = getExecutable().toAbsolutePath().toString();
        String command = "loc";
        String msBuild = "--msbuild-path=" + this.msBuild.toString();
        String target = path.toString();
        String output = tempResults.toString();
        // Assert windows environment
        if(System.getProperty("os.name").contains("Windows")){
        } else {
            throw new RuntimeException("Roslynator C# analysis not supported on non-Windows machines.");
        }
        // Run the cmd command
        System.out.println("roslynator LoC: beginning analysis.\n\tTarget: " + path.toString());
        ProcessBuilder pb = null;
        Process p = null;
        try {
            LOGGER.info("Tool used: " + tool.toString());
            pb = new ProcessBuilder(tool, command, msBuild, target, ">", output);
            LOGGER.info("Process Builder to get line of code " + pb.toString());
            pb.redirectOutput(new File(output));
            p = pb.start();
            p.waitFor();
        }
        catch (IOException | InterruptedException e) { e.printStackTrace(); }
        // Assert result file was created
        if (!tempResults.isFile()) {
            throw new RuntimeException("Roslynator.analyze() did not generate a results file in the expected location");
        }
        return tempResults.toPath();
    }
    @Override
    public Path initialize(Path path) {
        return roslynatorInitializeToTempFolder();
    }
    @Override
    public Map<String, Diagnostic> parseAnalysis(Path path) {
        String targetLine = null;
        int loc;
        LOGGER.info("Roslynator Loc Output txt file: " + path.toString());
        try (BufferedReader br = new BufferedReader(new FileReader(path.toString()))) {
            String line = br.readLine();
            while (line != null) {
                if (line.contains("% lines of code")) {
                    targetLine = line;
                    br.close();
                    break;
                }
                line = br.readLine();
            }
        }
        catch (IOException e) { e.printStackTrace(); }
        // parse the line of code integer
        assert targetLine != null;
        LOGGER.info("Line of Code By Project Scan by RoslynatorLoc is " +  targetLine);
        Pattern p = Pattern.compile("\\d*,*\\d+");
        Matcher m = p.matcher(targetLine);
        if (m.find()) {
            loc = Integer.parseInt(m.group().replaceAll(",", ""));
        }
        else throw new RuntimeException("LoC expected output from tool was not found by regex");
        // create finding, diagnostic and return
        Map<String, Diagnostic> diagnostics = new HashMap<>();
        // TODO: Set loc value by value instead of by severity (temporary hack)
        Finding locFinding = new Finding("n/a", 0, 0, loc);
        Diagnostic locDiagnostic = new Diagnostic("loc", "Lines of Code", getName(),
                new DefaultDiagnosticEvaluator());
        locDiagnostic.setChild(locFinding);
        diagnostics.put(locDiagnostic.getName(), locDiagnostic);
        return diagnostics;
    }
}
