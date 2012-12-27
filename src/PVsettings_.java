/**
Copyright (C) 2012  Katja Schulze (kschulze@th-wildau.de)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/



import java.io.File;

import ij.IJ;
import ij.Prefs;
import ij.plugin.PlugIn;

public class PVsettings_ implements PlugIn {
	
	String workDir = Prefs.getString(".dir.plankton");
	
	public void run(String arg) {
		
		if (workDir == null){
			setDirectory();
		}
		else{
			boolean choose = IJ.showMessageWithCancel("Set workspace", "Your worspace is already set to "+workDir+" Do you want to change it?");
			if (choose == true){
				setDirectory();
			}
			else return;
		}
	}


	private void setDirectory() {
		workDir =  IJ.getDirectory("Choose the directory for your workspace");
		if (workDir == null)return;
		// check if dir exists 
		File file = new File(workDir);
		if (!file.exists()){
			
			boolean yes = IJ.showMessageWithCancel("", "Create new directory?");
			if (yes == true){
				boolean success = file.mkdir();
				if(success == false){
					IJ.showMessage("Could not create new directory");
					return;
				}
				else{
					new File (workDir+"/results").mkdir();
					new File (workDir+"/pictures").mkdir(); 
					
					Prefs.set("dir.plankton", workDir);
					Prefs.savePreferences();
					IJ.showMessage("The directory "+workDir+" was new created and set as working directory.");
				}
			}
			else{return;}
			
		}
		else{	
			
			//check if all folders exist
			File result = new File (workDir+"/results");
			if (!result.exists())result.mkdir();
			File picture = new File (workDir+"/pictures");
			if (!picture.exists())picture.mkdir();
			
			//Save workdir
			Prefs.set("dir.plankton", workDir);
			Prefs.savePreferences();
		
			IJ.showMessage("The directory "+workDir+" was set as working directory.");
		}
		
	}
	
}