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

public class Wrapper {
    public static void main(String[] args){
        try{
            if (args == null || args.length < 1) {
                throw new IllegalArgumentException("Incorrect input parameters given. Be sure to include " +
                        "\n\t(0) The parameter to specify derivation (-d) or evaluation (-e)," +
                        "\n\t(1) (optional) Path to config file. See the config.properties file in src/test/resources/config for an example.");
            }

            for (int i = 0; i < args.length; i++) {
                switch(args[i]){
                    case "--version":
                    case "-v":
                        System.out.println("PIQUE version 0.9.2 PIQUE-Csharp Demo");
                        break;
                    case "--derive-model":
                    case "-d":
                        //kick off new Deriver
                        if (args.length >i+1) {
                            new QualityModelDeriver(args[i+1]);
                        }
                        else {
                            new QualityModelDeriver();
                        }

                        i++; //properties file is read as input, need to increment i to jump past it to the next argument
                        break;
                    case "--evaluate-model":
                    case "-e":
                        //kick off model assessment
                        if (args.length >i+1) {
                            new SingleProjectEvaluator(args[i+1]);
                        }
                        else {
                            new SingleProjectEvaluator();
                        }

                        i++;
                        break;
                    case "--help":
                    case "-h":
                    case "":
                        System.out.println("Run the jar file with the --derive-model (-d) to derive a quality model. ");
                        System.out.println("\t\tModel derivation involves populating a model with proper edge weights, threshold values, and structure" +
                                " to prepare for a model evaluation");
                        System.out.println("Run the jar file with the --evaluate-model (-e) to evaluate a quality model");
                        System.out.println("\t\tModel evaluation involves executing the model on a system under analysis to generate quality scores.");
                        break;
                    default:
                        System.out.println("System arguments not recognized, try --help or -h");

                }
            }


        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
