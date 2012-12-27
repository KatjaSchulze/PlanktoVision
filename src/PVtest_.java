/**
Copyright (C) 2012  Katja Schulze (kschulze@th-wildau.de)

All other copyrights are the property of their respective owners.

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
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import org.encog.ml.data.MLData;
import org.encog.neural.networks.BasicNetwork;
import org.encog.persist.EncogDirectoryPersistence;
import org.encog.util.normalize.DataNormalization;
import org.encog.util.obj.SerializeObject;

import feature.Feature;
import feature.FeatureEFD;
import feature.FeatureFlu;
import feature.FeatureGLCM;
import feature.FeatureHSB;
import feature.FeatureTexture;

import ij.IJ;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.io.OpenDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;

public class PVtest_ implements PlugIn {

	private ResultsTable rt;
	private static int systemFeature ;
	private static long fluFeature ;
	private static int hsbFeature;
	private static int texFeature;
	private static int efdFeature;
	private static int glcmFeature;
	private static int input; 
	String[] classes, colors;
	
	public void run(String arg) {

		String workDir;
		
		//check for working directory
		//===================================================================================================================================
		if (Prefs.getString(".dir.plankton")!=null){
			workDir = Prefs.getString(".dir.plankton");
		}
		else{
			IJ.showMessage("Please define your working directory under Plugins>PVsettings");
			return;
		}
		
		//***********************************************************************************************************************************
		
		//Ask for Copy
		//===================================================================================================================================
		
		boolean choose = false;
		GenericDialog gd = new GenericDialog("");
		gd.addMessage("Sort Testset?");
		gd.enableYesNoCancel("yes", "no");
		gd.showDialog();
		if (gd.wasCanceled())
	           return;
        else if (gd.wasOKed())
	            choose = true;
		
		
		
		//load the normalisation & the network
		//===================================================================================================================================
		OpenDialog odnw = new OpenDialog("Choose your network", workDir, "");
		if (odnw.getFileName() == null)return;
		Settings.networkName = odnw.getFileName();
		
		final BasicNetwork network = (BasicNetwork) EncogDirectoryPersistence.loadObject(new File(odnw.getDirectory()+"/"+Settings.networkName));
		DataNormalization norm = null;
		try {
			norm = (DataNormalization) SerializeObject.load(new File (odnw.getDirectory()+"/"+Settings.networkName+"_norm.ser"));
		} catch (ClassNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		//***********************************************************************************************************************************
		
		//load the feature settings
		//===================================================================================================================================
//		Settings.props = props; 
		Settings.loadPreferences();
		systemFeature = Settings.getInt("feature", Feature.AREA+Feature.MINFERET+Feature.CIRC+Feature.MINFERET_PERI+Feature.SOL+Feature.ROUND+Feature.KURT);
		fluFeature = Settings.getLong("fluorescence", FeatureFlu.PE_MEAN+FeatureFlu.PC_MEAN+FeatureFlu.REDFLU_MEAN+FeatureFlu.GREENFLU_MEAN);
		hsbFeature = Settings.getInt("hsb",FeatureHSB.HU_HIST);
		texFeature = Settings.getInt("texture", FeatureTexture.LBP_18+FeatureTexture.LBP_28+FeatureTexture.LBP_38+FeatureTexture.LBP_216+FeatureTexture.LBP_316+FeatureTexture.IM1); 
		efdFeature = Settings.getInt("efd", FeatureEFD.EFD2+FeatureEFD.EFD3+FeatureEFD.EFD4+FeatureEFD.EFD5+FeatureEFD.EFD6+FeatureEFD.EFD7+FeatureEFD.EFD8
												+FeatureEFD.EFD9+FeatureEFD.EFD10+FeatureEFD.EFD11+FeatureEFD.EFD12+FeatureEFD.EFD13);
		glcmFeature = Settings.getInt("glcm", 0);
		input = Settings.getInt("input", 669);
		//***********************************************************************************************************************************
		
		//filter for image type
		//===================================================================================================================================
		FilenameFilter filter  = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.toLowerCase().endsWith(".tif")  ;//&&  name.toLowerCase().startsWith("d_pf"); // && !name.toLowerCase().startsWith("a");
					
		}};
		//***********************************************************************************************************************************
		
		//image directory
		//===================================================================================================================================
		DirectoryChooser dc = new DirectoryChooser("Choose the Directory for the reference images");
        String dir = dc.getDirectory();
        if (dir == null)return;
		
        File dir1 = new File(dir); 
		String[] subdir = dir1.list();
		
	    Arrays.sort(subdir, new Comparator<String>()
	    {
	      public int compare(String s1, String s2)
	      {
	        return Integer.valueOf(s1).compareTo(Integer.valueOf(s2));
	      }
	    });
		
	    String header = " \t";
	    for (int i = 0; i<subdir.length; i++){
	    	if (subdir[i].matches("Thumbs.db")){continue;}
	    	header = header+subdir[i]+" \t";
	    }
	    IJ.log(header);
	    
		//***********************************************************************************************************************************
		
		// create new ResultsTable
		Analyzer.setPrecision(4);
		this.rt = new ResultsTable();
		Analyzer.setResultsTable(this.rt);
		
		// collect data for training
		for (int i = 0; i<subdir.length; i++){
			if (subdir[i].matches("Thumbs.db")){continue;}
			int[] results = new int[network.getOutputCount()];
			String[] imageList = new File(dir1+"/"+subdir[i]).list(filter);
			int lenght  = imageList.length;
			
			for(int j = 0; j<lenght; j++){
				String path = dir+"/"+subdir[i]+"/"+imageList[j];	
				try {
					imageList[j] = imageList[j].substring(0, imageList[j].length()-4);
					rt = ResultsTable.open(workDir+"results/"+imageList[j]+".xls");
					rt.show("Results");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					IJ.showMessage("Can't read Features. Opening next image.");
					rt.reset();
					continue;
				}	
			
				for (int i1 = 0; i1< 1; i1++){
					double[] data = new double[input];
					int index = 0; 
					
					//Image Moments
					if ((texFeature&FeatureTexture.IM0)!=0){ data[index] =rt.getValue("I0",i1); index++;}//IM0
					if ((texFeature&FeatureTexture.IM1)!=0){ data[index] =rt.getValue("I1",i1); index++;}//IM1
					if ((texFeature&FeatureTexture.IM2)!=0){ data[index] =rt.getValue("I2",i1); index++;}//IM2
					if ((texFeature&FeatureTexture.IM3)!=0){ data[index] =rt.getValue("I3",i1); index++;}//IM3
					if ((texFeature&FeatureTexture.IM4)!=0){ data[index] =rt.getValue("I4",i1); index++;}//IM4
					if ((texFeature&FeatureTexture.IM5)!=0){ data[index] =rt.getValue("I5",i1); index++; }//IM5
					if ((texFeature&FeatureTexture.IM6)!=0){ data[index] =rt.getValue("I6",i1); index++;}//IM6
					if ((texFeature&FeatureTexture.IM7)!=0){ data[index] =rt.getValue("I7",i1); index++;}//IM7
					
					// Elliptic Fourier Descriptor
					if ((efdFeature&FeatureEFD.EFD2)!=0){ data[index] =rt.getValue("efd2",i1); index++;}//EFD2
					if ((efdFeature&FeatureEFD.EFD3)!=0){ data[index] =rt.getValue("efd3",i1); index++;}//EFD3
					if ((efdFeature&FeatureEFD.EFD4)!=0){ data[index] =rt.getValue("efd4",i1); index++;}//EFD4
					if ((efdFeature&FeatureEFD.EFD5)!=0){data[index] =rt.getValue("efd5",i1); index++;} //EFD5
					if ((efdFeature&FeatureEFD.EFD6)!=0){data[index] =rt.getValue("efd6",i1); index++;}//EFD6
					if ((efdFeature&FeatureEFD.EFD7)!=0){data[index] =rt.getValue("efd7",i1);index++;}//EFD7
					if ((efdFeature&FeatureEFD.EFD8)!=0){ data[index] =rt.getValue("efd8",i1); index++;}//EFD8
					if ((efdFeature&FeatureEFD.EFD9)!=0){ data[index] =rt.getValue("efd9",i1); index++;}//EFD9
					if ((efdFeature&FeatureEFD.EFD10)!=0){data[index] =rt.getValue("efd10",i1);index++;}//EFD10
					if ((efdFeature&FeatureEFD.EFD11)!=0){data[index] =rt.getValue("efd11",i1);index++;}//EFD11
					if ((efdFeature&FeatureEFD.EFD12)!=0){data[index] =rt.getValue("efd12",i1);index++;}//EFD12
					if ((efdFeature&FeatureEFD.EFD13)!=0){data[index] =rt.getValue("efd13",i1);index++;}//EFD13
					if ((efdFeature&FeatureEFD.EFD14)!=0){data[index] =rt.getValue("efd14",i1); index++;}//EFD14
					if ((efdFeature&FeatureEFD.EFD15)!=0){data[index] =rt.getValue("efd15",i1); index++;}//EFD15
					if ((efdFeature&FeatureEFD.EFD16)!=0){data[index] =rt.getValue("efd16",i1);index++;}//EFD16
					if ((efdFeature&FeatureEFD.EFD17)!=0){data[index] =rt.getValue("efd17",i1);index++;}//EFD17
					if ((efdFeature&FeatureEFD.EFD18)!=0){data[index] =rt.getValue("efd18",i1);index++;}//EFD18
					if ((efdFeature&FeatureEFD.EFD19)!=0){data[index] =rt.getValue("efd19",i1); index++;}//EFD19
					if ((efdFeature&FeatureEFD.EFD20)!=0){data[index] =rt.getValue("efd20",i1); index++;}//EFD20
					if ((efdFeature&FeatureEFD.EFD21)!=0){data[index] =rt.getValue("efd21",i1);index++;}//EFD21
					if ((efdFeature&FeatureEFD.EFD22)!=0){data[index] =rt.getValue("efd22",i1); index++;}//EFD22
					if ((efdFeature&FeatureEFD.EFD23)!=0){data[index] =rt.getValue("efd23",i1);index++;}//EFD23
					if ((efdFeature&FeatureEFD.EFD24)!=0){data[index] =rt.getValue("efd24",i1);index++; }//EFD24
					if ((efdFeature&FeatureEFD.EFD25)!=0){data[index] =rt.getValue("efd25",i1);index++;}//EFD25
					if ((efdFeature&FeatureEFD.EFD26)!=0){ data[index] =rt.getValue("efd26",i1);index++; }//EFD26
					if ((efdFeature&FeatureEFD.EFD27)!=0){data[index] =rt.getValue("efd27",i1);index++; }//EFD27
					if ((efdFeature&FeatureEFD.EFD28)!=0){data[index] =rt.getValue("efd28",i1);index++; }//EFD28
					if ((efdFeature&FeatureEFD.EDF29)!=0){data[index] =rt.getValue("efd29",i1); index++;}//EFD29
					
					if ((systemFeature&Feature.KURT)!=0){data[index] =rt.getValue("Kurt", i1);index++;}
					if ((systemFeature&Feature.SKEW)!=0){data[index] =rt.getValue("Skew", i1);index++;}
					if ((systemFeature&Feature.MINFERET)!=0) {data[index] =rt.getValue("MinFeret", i1); index++;} //MinFeret
					if ((systemFeature&Feature.AREA)!=0) {data[index] =rt.getValue("Area", i1);index++; } //Area
					if ((systemFeature&Feature.WIDTH)!=0) {data[index] =rt.getValue("Width", i1); index++;}//width
					if ((systemFeature&Feature.HEIGHT)!=0) {data[index] =rt.getValue("Height", i1);index++;}
					if ((systemFeature&Feature.MAJOR)!=0) {data[index] =rt.getValue("Major", i1); index++; }//Major
					if ((systemFeature&Feature.PERI)!=0) {data[index] =rt.getValue("Perim.", i1); index++; }
					if ((systemFeature&Feature.MINOR)!=0) {data[index] =rt.getValue("Minor", i1); index++;}//Minor
					if ((systemFeature&Feature.FERET)!=0) {data[index] =rt.getValue("Feret", i1);index++;	} 
					if ((systemFeature&Feature.ANG)!=0) {data[index] =rt.getValue("Angle", i1);index++;}//Angle
					
					if ((systemFeature&Feature.ROT_SYM)!=0){
						if (Double.isNaN(rt.getValue("Rot2Sym", i1))){data[index] =-1; }
						else{data[index] =rt.getValue("Rot2Sym",i1); }
						index++;
					}
					if ((systemFeature&Feature.REFL_SYM)!=0){
						if (rt.getColumnHeadings().contains("ReflSym0") == true && rt.getValue("ReflSym0",i1) != -1 && rt.getValue("ReflSym0",i1) != 0 ) {data[index] =rt.getValue("ReflSym0",i1)/180.0;}
						else {data[index] =-1;}
						index++;
						
						if (rt.getColumnHeadings().contains("ReflSym1") == true && rt.getValue("ReflSym1",i1) != -1 && rt.getValue("ReflSym1",i1) != 0 ) {data[index] =rt.getValue("ReflSym1",i1)/180.0; }
						else {data[index] =-1;}
						index++;
						
						if (rt.getColumnHeadings().contains("ReflSym2") == true && rt.getValue("ReflSym2",i1) != -1 && rt.getValue("ReflSym2",i1) != 0 ) {data[index] =rt.getValue("ReflSym2",i1)/180.0; }
						else {data[index] =-1;}
						index++;
						
						if (rt.getColumnHeadings().contains("ReflSym3") == true &&rt.getValue("ReflSym3",i1) != -1 && rt.getValue("ReflSym3",i1) != 0 ) {data[index] =rt.getValue("ReflSym3",i1)/180.0;}
						else {data[index] =-1;}
						index++;
					}
					
					//GLCM
					if ((glcmFeature&FeatureGLCM.ASM1) != 0){
						data[index] =rt.getValue("Angular Second Moment_45_1", i1);index++;
						data[index] =rt.getValue("Angular Second Moment_90_1", i1);index++;
						data[index] =rt.getValue("Angular Second Moment_135_1", i1);index++;
						
					}
					
					if ((glcmFeature&FeatureGLCM.ASM2) != 0){
						data[index] =rt.getValue("Angular Second Moment_45_2", i1);index++;
						data[index] =rt.getValue("Angular Second Moment_90_2", i1);index++;
						data[index] =rt.getValue("Angular Second Moment_135_2", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.ASM3) != 0){
						data[index] =rt.getValue("Angular Second Moment_45_3", i1);index++;
						data[index] =rt.getValue("Angular Second Moment_90_3", i1);index++;
						data[index] =rt.getValue("Angular Second Moment_135_3", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.ASM4) != 0){
						data[index] =rt.getValue("Angular Second Moment_45_4", i1);index++;
						data[index] =rt.getValue("Angular Second Moment_90_4", i1);index++;
						data[index] =rt.getValue("Angular Second Moment_135_4", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.ASM5) != 0){
						data[index] =rt.getValue("Angular Second Moment_45_5", i1);index++;
						data[index] =rt.getValue("Angular Second Moment_90_5", i1);index++;
						data[index] =rt.getValue("Angular Second Moment_135_5", i1);index++;
					}
					
					//contrast
					if ((glcmFeature&FeatureGLCM.CONTRAST1) != 0){
						data[index] =rt.getValue("Contrast_45_1", i1);index++;
						data[index] =rt.getValue("Contrast_90_1", i1);index++;
						data[index] =rt.getValue("Contrast_135_1", i1);index++;
					
					}
					
					if ((glcmFeature&FeatureGLCM.CONTRAST2) != 0){
						data[index] =rt.getValue("Contrast_45_2", i1);index++;
						data[index] =rt.getValue("Contrast_90_2", i1);index++;
						data[index] =rt.getValue("Contrast_135_2", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.CONTRAST3) != 0){
						data[index] =rt.getValue("Contrast_45_3", i1);index++;
						data[index] =rt.getValue("Contrast_90_3", i1);index++;
						data[index] =rt.getValue("Contrast_135_3", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.CONTRAST4) != 0){
						data[index] =rt.getValue("Contrast_45_4", i1);index++;
						data[index] =rt.getValue("Contrast_90_4", i1);index++;
						data[index] =rt.getValue("Contrast_135_4", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.CONTRAST5) != 0){
						data[index] =rt.getValue("Contrast_45_5", i1);index++;
						data[index] =rt.getValue("Contrast_90_5", i1);index++;
						data[index] =rt.getValue("Contrast_135_5", i1);index++;
					}
					
					//Correlation
					if ((glcmFeature&FeatureGLCM.CORR1) != 0){
						data[index] =rt.getValue("Correlation_45_1", i1);index++;
						data[index] =rt.getValue("Correlation_90_1", i1);index++;
						data[index] =rt.getValue("Correlation_135_1", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.CORR2) != 0){
						data[index] =rt.getValue("Correlation_45_2", i1);index++;
						data[index] =rt.getValue("Correlation_90_2", i1);index++;
						data[index] =rt.getValue("Correlation_135_2", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.CORR3) != 0){
						data[index] =rt.getValue("Correlation_45_3", i1);index++;
						data[index] =rt.getValue("Correlation_90_3", i1);index++;
						data[index] =rt.getValue("Correlation_135_3", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.CORR4) != 0){
						data[index] =rt.getValue("Correlation_45_4", i1);index++;
						data[index] =rt.getValue("Correlation_90_4", i1);index++;
						data[index] =rt.getValue("Correlation_135_4", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.CORR5) != 0){
						data[index] =rt.getValue("Correlation_45_5", i1);index++;
						data[index] =rt.getValue("Correlation_90_5", i1);index++;
						data[index] =rt.getValue("Correlation_135_5", i1);index++;
					}
					
					//IDM
					if ((glcmFeature&FeatureGLCM.IDM1) != 0){
						data[index] =rt.getValue("Inverse Difference Moment_45_1", i1);index++;
						data[index] =rt.getValue("Inverse Difference Moment_90_1", i1);index++;
						data[index] =rt.getValue("Inverse Difference Moment_135_1", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.IDM2) != 0){
						data[index] =rt.getValue("Inverse Difference Moment_45_2", i1);index++;
						data[index] =rt.getValue("Inverse Difference Moment_90_2", i1);index++;
						data[index] =rt.getValue("Inverse Difference Moment_135_2", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.IDM3) != 0){
						data[index] =rt.getValue("Inverse Difference Moment_45_3", i1);index++;
						data[index] =rt.getValue("Inverse Difference Moment_90_3", i1);index++;
						data[index] =rt.getValue("Inverse Difference Moment_135_3", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.IDM4) != 0){
						data[index] =rt.getValue("Inverse Difference Moment_45_4", i1);index++;
						data[index] =rt.getValue("Inverse Difference Moment_90_4", i1);index++;
						data[index] =rt.getValue("Inverse Difference Moment_135_4", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.IDM5) != 0){
						data[index] =rt.getValue("Inverse Difference Moment_45_5", i1);index++;
						data[index] =rt.getValue("Inverse Difference Moment_90_5", i1);index++;
						data[index] =rt.getValue("Inverse Difference Moment_135_5", i1);index++;
					}
					
					//Entropy
					if ((glcmFeature&FeatureGLCM.ENTROPY1) != 0){
						data[index] =rt.getValue("Entropy_45_1", i1);index++;
						data[index] =rt.getValue("Entropy_90_1", i1);index++;
						data[index] =rt.getValue("Entropy_135_1", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.ENTROPY2) != 0){
						data[index] =rt.getValue("Entropy_45_2", i1);index++;
						data[index] =rt.getValue("Entropy_90_2", i1);index++;
						data[index] =rt.getValue("Entropy_135_2", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.ENTROPY3) != 0){
						data[index] =rt.getValue("Entropy_45_3", i1);index++;
						data[index] =rt.getValue("Entropy_90_3", i1);index++;
						data[index] =rt.getValue("Entropy_135_3", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.ENTROPY4) != 0){
						data[index] =rt.getValue("Entropy_45_4", i1);index++;
						data[index] =rt.getValue("Entropy_90_4", i1);index++;
						data[index] =rt.getValue("Entropy_135_4", i1);index++;
					}
					
					if ((glcmFeature&FeatureGLCM.ENTROPY5) != 0){
						data[index] =rt.getValue("Entropy_45_5", i1);index++;
						data[index] =rt.getValue("Entropy_90_5", i1);index++;
						data[index] =rt.getValue("Entropy_135_5", i1);index++;
					}
					
					
					
					//Local binary pattern
					if ((texFeature&FeatureTexture.LBP_18)!=0){ 
						for(int l = 0 ; l<38; l++){
							data[index] =rt.getValue("lbp"+l+"_18",i1); index++;
						}
					}
					
					if ((texFeature&FeatureTexture.LBP_28)!=0){
						for(int l = 0 ; l<38; l++){
							data[index] =rt.getValue("lbp"+l+"_28",i1); index++;
						}
					}
					
					if ((texFeature&FeatureTexture.LBP_38)!=0){
						for(int l = 0 ; l<38; l++){
							data[index] =rt.getValue("lbp"+l+"_38",i1); index++;
						}
					}
					
					if ((texFeature&FeatureTexture.LBP_48)!=0){
						for(int l = 0 ; l<38; l++){
							data[index] =rt.getValue("lbp"+l+"_48",i1);index++;
						}
					}
					
					if ((texFeature&FeatureTexture.LBP_216)!=0){
						for(int l = 0 ; l<138; l++){
							data[index] =rt.getValue("lbp"+l+"_216",i1);index++;
						}
					}
					
					if ((texFeature&FeatureTexture.LBP_316)!=0){
						for(int l = 0 ; l<138; l++){
							data[index] =rt.getValue("lbp"+l+"_316",i1); index++;
						}
					}
					

					
					//single feature
					if ((systemFeature&Feature.CIRC)!=0) 		{data[index] =rt.getValue("Circ.", i1);  index++;}//Circ
					if ((systemFeature&Feature.ROUND)!=0)		{data[index] =rt.getValue("Round", i1);	index++; }
					if ((systemFeature&Feature.SOL)!=0) 		{ data[index] =rt.getValue("Solidity", i1);index++; }
					if ((systemFeature&Feature.MINFERET_PERI)!=0) {data[index] =rt.getValue("MinFeret", i1)/rt.getValue("Perim.", i1); index++;}//minFeret/perim
					if ((systemFeature&Feature.FERETANG)!=0) 	{data[index] =rt.getValue("FeretAngle", i1)/360.0;  index++;}
					if ((systemFeature&Feature.AR)!=0) 			{data[index] =rt.getValue("AR", i1);index++;}
					if ((systemFeature&Feature.INTDEN)!=0) 		{data[index] =rt.getValue("IntDen", i1); index++;}//IntDen
					
					if ((systemFeature&Feature.DIRECT_HIST)!=0){
						for (int j1 = 0; j1<20; j1++){
							data[index] =rt.getValue(j1+"DR-norm", i1);index++; 
						}
					}
											
					//PE
					
					if ((fluFeature&FeatureFlu.PE_MEAN_NORM)!=0){	data[index] =rt.getValue("bright1",i1)/255.0;index++;} //meannorm
					if ((fluFeature&FeatureFlu.PE_MEAN)!=0)		{	data[index] =rt.getValue("bright1_mean",i1)/255.0;index++;}//mean
					if ((fluFeature&FeatureFlu.PE_MODE)!=0)		{	data[index] =rt.getValue("bright1_mode",i1)/255.0;index++;}//mode
					if ((fluFeature&FeatureFlu.PE_MIN)!=0)		{	data[index] =rt.getValue("bright1_min",i1)/255.0;index++;} //min
					if ((fluFeature&FeatureFlu.PE_MAX)!=0)		{	data[index] =rt.getValue("bright1_max",i1)/255.0;index++;}//max
					if ((fluFeature&FeatureFlu.PE_STDDEV)!=0)	{	data[index] =rt.getValue("bright1_stdev",i1)/255.0;index++;} //
					if ((fluFeature&FeatureFlu.PE_FLUPARTS)!=0)	{	data[index] =rt.getValue("ratio flu1",i1); index++;}//
					
					//PC
					
					if ((fluFeature&FeatureFlu.PC_MEAN_NORM)!=0){data[index] =rt.getValue("bright2",i1)/255.0;index++;} //meannorm
					if ((fluFeature&FeatureFlu.PC_MEAN)!=0)		{data[index] =rt.getValue("bright2_mean",i1)/255.0;index++;}//mean
					if ((fluFeature&FeatureFlu.PC_MODE)!=0)		{data[index] =rt.getValue("bright2_mode",i1)/255.0;index++;}//mode
					if ((fluFeature&FeatureFlu.PC_MIN)!=0)		{data[index] =rt.getValue("bright2_min",i1)/255.0;index++;} //min
					if ((fluFeature&FeatureFlu.PC_MAX)!=0)		{data[index] =rt.getValue("bright2_max",i1)/255.0;index++;}///max
					if ((fluFeature&FeatureFlu.PC_STDDEV)!=0)	{data[index] =rt.getValue("bright2_stdev",i1)/255.0;index++;} //
					if ((fluFeature&FeatureFlu.PC_FLUPARTS)!=0)	{data[index] =rt.getValue("ratio flu2",i1);index++;} //
					
					//CHL
					
					if ((fluFeature&FeatureFlu.CHL_MEAN_NORM)!=0){data[index] =rt.getValue("bright3",i1)/255.0;index++;}//meannorm
					if ((fluFeature&FeatureFlu.CHL_MEAN)!=0)	{data[index] =rt.getValue("bright3_mean",i1)/255.0;index++;}//mean
					if ((fluFeature&FeatureFlu.CHL_MODE)!=0)	{data[index] =rt.getValue("bright3_mode",i1)/255.0;index++;}//mode
					if ((fluFeature&FeatureFlu.CHL_MIN)!=0)		{data[index] =rt.getValue("bright3_min",i1)/255.0;index++;}//min
					if ((fluFeature&FeatureFlu.CHL_MAX)!=0)		{data[index] =rt.getValue("bright3_max",i1)/255.0;index++;}//max
					if ((fluFeature&FeatureFlu.CHL_STDDEV)!=0)	{data[index] =rt.getValue("bright3_stdev",i1)/255.0;index++;}//
					if ((fluFeature&FeatureFlu.CHL_FLUPARTS)!=0){data[index] =rt.getValue("ratio flu3",i1);index++;} //
					
					if ((fluFeature&FeatureFlu.REDFLU_MEAN)!=0)	{data[index] =rt.getValue("redbrmean_Chl",i1)/255.0;index++; }
					if ((fluFeature&FeatureFlu.REDFLU_MODE)!=0)	{data[index] =rt.getValue("redbrmode_Chl",i1)/255.0; index++;}
					if ((fluFeature&FeatureFlu.REDFLU_MAX)!=0)	{data[index] =rt.getValue("redbrmax_Chl",i1)/255.0;index++; }
					if ((fluFeature&FeatureFlu.REDFLU_MIN)!=0)	{data[index] =rt.getValue("redbrmin_Chl",i1)/255.0; index++;}
					if ((fluFeature&FeatureFlu.REDFLU_STDDEV)!=0){ data[index] =rt.getValue("redbrstdv_Chl",i1)/255.0;index++; }
					
					if ((fluFeature&FeatureFlu.GREENFLU_MEAN)!=0){data[index] =rt.getValue("greenbrmean_Chl",i1)/255.0;index++;} 
					if ((fluFeature&FeatureFlu.GREENFLU_MODE)!=0){data[index] =rt.getValue("greenbrmode_Chl",i1)/255.0;index++; }
					if ((fluFeature&FeatureFlu.GREENFLU_MAX)!=0)	{data[index] =rt.getValue("greenbrmax_Chl",i1)/255.0; index++;}
					if ((fluFeature&FeatureFlu.GREENFLU_MIN)!=0)	{ data[index] =rt.getValue("greenbrmin_Chl",i1)/255.0; index++;}
					if ((fluFeature&FeatureFlu.GREENFLU_STDDEV)!=0){ data[index] =rt.getValue("greenbrstdv_Chl",i1)/255.0;index++; }
					
					if ((fluFeature&FeatureFlu.HUFLU_MEAN)!=0){data[index] =rt.getValue("humean_Chl",i1)/255.0;index++; }
					if ((fluFeature&FeatureFlu.HUFLU_MODE)!=0){data[index] =rt.getValue("humode_Chl",i1)/255.0;index++; }
					if ((fluFeature&FeatureFlu.HUFLU_MAX)!=0){data[index] =rt.getValue("humax_Chl",i1)/255.0; index++;}
					if ((fluFeature&FeatureFlu.HUFLU_MIN)!=0){data[index] =rt.getValue("humin_Chl",i1)/255.0; index++;}
					if ((fluFeature&FeatureFlu.HUFLU_STDDEV)!=0){data[index] =rt.getValue("hustdv_Chl",i1)/255.0;index++; }
					
					//Color
					
					if ((hsbFeature&FeatureHSB.HU_HIST)!=0){
						
						for(int h = 0 ; h<255; h++){
							data[index] =rt.getValue("hu_hist_"+h,i1); index++;
						}
					}
					if ((hsbFeature&FeatureHSB.HU_MIN)!=0){data[index] =rt.getValue("min hu",i1)/256.0;index++;}//min
					if ((hsbFeature&FeatureHSB.HU_MAX)!=0){	data[index] =rt.getValue("max hu",i1)/256.0;index++;}//max
					if ((hsbFeature&FeatureHSB.HU_MEAN)!=0){	data[index] =rt.getValue("mean hu",i1)/256.0; index++;} //mean
					if ((hsbFeature&FeatureHSB.HU_MODE)!=0){	data[index] =rt.getValue("mode hu",i1)/256.0;index++;} //mode
					if ((hsbFeature&FeatureHSB.HU_STDDEV)!=0){	data[index] =rt.getValue("stdev hu",i1)/256.0; index++; }//stdev

					//Saturation
					
					if ((hsbFeature&FeatureHSB.SAT_HIST)!=0){
					
						for(int s = 0 ; s<255; s++){
							data[index] =rt.getValue("sat_hist_"+s,i1); index++;
						}
					}
					
					if ((hsbFeature&FeatureHSB.SAT_MIN)!=0){data[index] =rt.getValue("min bright",i1)/256.0;index++;}//min
					if ((hsbFeature&FeatureHSB.SAT_MAX)!=0){data[index] =rt.getValue("max bright",i1)/256.0;index++;}//max
					if ((hsbFeature&FeatureHSB.SAT_MEAN)!=0){ data[index] =rt.getValue("mean bright",i1)/256.0;index++;}//mean
					if ((hsbFeature&FeatureHSB.SAT_MODE)!=0){ data[index] =rt.getValue("mode bright",i1)/256.0;index++;}//mode
					if ((hsbFeature&FeatureHSB.SAT_STDDEV)!=0){ data[index] =rt.getValue("stdev bright",i1)/256.0;index++;}//stdev
					
					//Brightness
					
					if ((hsbFeature&FeatureHSB.BRIGHT_HIST)!=0){
						
						for(int b = 0 ; b<255; b++){
							data[index] =rt.getValue("bright_hist_"+b,i1);index++;
						}
					}
					if ((hsbFeature&FeatureHSB.BRIGHT_MIN)!=0){ data[index] =rt.getValue("min bright",i1)/256.0;index++;}//min
					if ((hsbFeature&FeatureHSB.BRIGHT_MAX)!=0){data[index] =rt.getValue("max bright",i1)/256.0;index++;}//max
					if ((hsbFeature&FeatureHSB.BRIGHT_MEAN)!=0){data[index] =rt.getValue("mean bright",i1)/256.0;index++;} //mean
					if ((hsbFeature&FeatureHSB.BRIGHT_MODE)!=0){data[index] =rt.getValue("mode bright",i1)/256.0;index++;}//mode
					if ((hsbFeature&FeatureHSB.BRIGHT_STDDEV)!=0){data[index] =rt.getValue("stdev bright",i1)/256.0;index++;}//stdev
					
//					IJ .wait(19999999);
					
					MLData inputData = norm.buildForNetworkInput(data);
					
					int coverTypeActual = network.classify(inputData);
					results[coverTypeActual]++;
					
					if(choose == true){
						String goal = workDir+"Sorted/"+coverTypeActual+"/";
						
						File file = new File(goal);
						if (!file.exists()){
							file.mkdirs();
						}
						final String cmd = "cp "+path+" "+goal;
						IJ.wait(50);
						try {
						    // Run ls command
						    Process process = Runtime.getRuntime().exec(cmd);
						    process.waitFor();
						    process.getInputStream().close();
						    process.getErrorStream().close();
						    process.getOutputStream().close();
						    process.destroy();
						} catch (Exception e) {
						    e.printStackTrace(System.err);
						}
					}
					
					
					
				}
				
				rt.reset();
			}
			String log = subdir[i]+" \t";
			System.out.print(subdir[i]+"	");
			for (int i1 = 0; i1<results.length; i1++){
				System.out.print(results[i1]+"	");
				log= log+results[i1]+" \t";
			}
			System.out.println();
			IJ.log(log);
		}
	}

}	
	