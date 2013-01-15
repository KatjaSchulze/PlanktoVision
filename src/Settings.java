import ij.IJ;
import ij.ImageJ;
import ij.Prefs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;




public class Settings {
	
	public static final String PROPS_NAME = "Props.txt";
	public static String networkName = "network";
	public static Properties props = new Properties() ;
	
	
//	public static final String workingDirectory = 
	
	/** Finds an int in IJ_Props or IJ_Prefs.txt. */
	public static int getInt(String key, int defaultValue) {
			loadPreferences();
			if (props==null) //workaround for Netscape JIT bug
				return defaultValue;
			String s = props.getProperty(key);
			if (s!=null) {
				try {
					return Integer.decode(s).intValue();
				} catch (NumberFormatException e) {IJ.write(""+e);}
			}
			return defaultValue;
		}
		
	public static long getLong(String key, long defaultValue) {
			loadPreferences();
			if (props==null) //workaround for Netscape JIT bug
				return defaultValue;
			String s = props.getProperty(key);
			if (s!=null) {
				try {
					return Long.decode(s).longValue();
				} catch (NumberFormatException e) {IJ.write(""+e);}
			}
			return defaultValue;
		}
		
	static void loadPreferences() {
			String path = Prefs.getString(".dir.plankton")+"/networks/"+networkName+"_pref.txt";
			boolean ok =  loadPrefs(path);
//			if (!ok && !IJ.isWindows()) {
//				path = System.getProperty("user.home")+separator+PREFS_NAME;
//				ok = loadPrefs(path); // look in home dir
//				if (ok) new File(path).delete();
//			}

		}

	static boolean loadPrefs(String path) {
			try {
				InputStream is = new BufferedInputStream(new FileInputStream(path));
				props.load(is);
				is.close();
				return true;
			} catch (Exception e) {
				return false;
			}
		}
		
	public static void savePrefs(Properties prefs, String path) throws IOException{
			FileOutputStream fos = new FileOutputStream(path);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			prefs.store(bos, "ImageJ "+ImageJ.VERSION+" Preferences");
			bos.close();
		}
	
	
	
	
	
}