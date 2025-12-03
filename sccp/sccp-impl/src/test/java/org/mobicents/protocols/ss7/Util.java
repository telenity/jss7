package org.mobicents.protocols.ss7;

import java.io.File;


public class Util {

    public static String getTmpTestDir() {
        try {
            String cp = System.getProperty("surefire.test.class.path");
            if (cp != null) {
                String[] paths = cp.split(File.pathSeparator);
                if (paths.length > 0) {
                    File f = new File(paths[0]);
                    if (f.exists() && f.isDirectory() && f.canWrite()) {
                        return f.getAbsolutePath();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    
}
