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
package utilities;

import java.io.FileReader;
import java.util.Properties;

public class PiqueProperties {
    public static Properties getProperties(){

        Properties prop = new Properties();
        try {
            prop.load(new FileReader("src/main/resources/pique-properties.properties"));

        }catch(Exception e){
            try {
                prop.load(new FileReader("pique-properties.properties")); // this is the case when running from the .jar version of pique
            }
            catch(Exception e2){
                e2.printStackTrace();
            }
        }
        return prop;
    }
}
