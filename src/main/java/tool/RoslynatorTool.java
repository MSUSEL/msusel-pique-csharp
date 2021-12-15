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
import pique.analysis.Tool;
import pique.utility.FileUtility;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
/**
 * Abstract class for common functions of Roslynator tool needs
 */
public abstract class RoslynatorTool extends Tool {
    public RoslynatorTool(String name, Path toolRoot) {
        super(name, toolRoot);
    }
    protected Path roslynatorInitializeToTempFolder() {
        String protocol = RoslynatorTool.class.getResource("").getProtocol();
        Path tempResourceDirectory = Paths.get(System.getProperty("user.dir"), "resources");
        switch (protocol) {
            case "file":
                Path roslynatorResource = getToolRoot();
                tempResourceDirectory = FileUtility.extractResourcesAsIde(tempResourceDirectory, roslynatorResource);
                break;
            case "jar":
                try {
                    File jarFile = new File(RoslynatorAnalyzer
                            .class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI());
                    String resourceName = "Roslynator";
                    tempResourceDirectory = FileUtility.extractResourcesAsJar(jarFile, tempResourceDirectory, resourceName);
                }
                catch (URISyntaxException e) { e.printStackTrace(); }
                break;
            default:
                throw new RuntimeException("Protocol did not match with 'file' or 'jar'");
        }
        setToolRoot(Paths.get(tempResourceDirectory.toString(), "Roslynator"));
        //return Paths.get(getToolRoot().toString(), "Roslynator", "Roslynator.exe");
        return Paths.get(getToolRoot().toString(), "bin", "Roslynator.exe");
    }
}