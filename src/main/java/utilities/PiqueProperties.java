package utilities;

import java.io.FileReader;
import java.util.Properties;

public class PiqueProperties {
    public static Properties getProperties(){

        Properties prop = new Properties();
        try {
            prop.load(new FileReader("src/main/resources/pique-bin.properties"));

        }catch(Exception e){
            try {
                prop.load(new FileReader("pique-bin.properties")); // this is the case when running from the .jar version of pique
            }
            catch(Exception e2){
                e2.printStackTrace();
            }
        }
        return prop;
    }
}
