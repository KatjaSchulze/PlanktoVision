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

import java.awt.Checkbox;
import java.awt.Frame;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Properties;
import java.util.Vector;

import org.encog.NullStatusReportable;
import org.encog.engine.network.activation.ActivationElliottSymmetric;
import org.encog.mathutil.randomize.ConsistentRandomizer;
import org.encog.mathutil.randomize.RangeRandomizer;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.train.MLTrain;
import org.encog.ml.train.strategy.HybridStrategy;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.layers.BasicLayer;
import org.encog.neural.networks.training.CalculateScore;
import org.encog.neural.networks.training.TrainingSetScore;
import org.encog.neural.networks.training.genetic.NeuralGeneticAlgorithm;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.persist.EncogDirectoryPersistence;
import org.encog.platformspecific.j2se.TrainingDialog;
import org.encog.util.csv.CSVFormat;
import org.encog.util.normalize.DataNormalization;
import org.encog.util.normalize.input.InputField;
import org.encog.util.normalize.input.InputFieldCSV;
import org.encog.util.normalize.output.OutputField;
import org.encog.util.normalize.output.OutputFieldDirect;
import org.encog.util.normalize.output.OutputFieldRangeMapped;
import org.encog.util.normalize.output.nominal.OutputOneOf;
import org.encog.util.normalize.segregate.index.IndexSampleSegregator;
import org.encog.util.normalize.target.NormalizationStorageCSV;
import org.encog.util.obj.SerializeObject;
import org.encog.util.simple.EncogUtility;

import ij.IJ;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.PlugInFrame;
import ij.text.TextWindow;

import feature.Feature;
import feature.FeatureEFD;
import feature.FeatureFlu;
import feature.FeatureGLCM;
import feature.FeatureHSB;
import feature.FeatureTexture;


public class PVtraining_ implements PlugIn, Measurements {
	
	static Properties props = new Properties();
	
	private static int systemFeature ;
	private static long fluFeature ;
	private static int hsbFeature;
	private static int texFeature;
	private static int efdFeature;
	private static int glcmFeature;
	
	public void run(String arg) {
		
		String workDir;
		//check for workspace
		//===================================================================================================================================
		if (Prefs.getString(".dir.plankton")!=null){
			workDir = Prefs.getString(".dir.plankton");
		}
		else{
			IJ.showMessage("Please define your working directory under Plugins>PlanktoVision>PVsettings");
			return;
		}
		
//		System.out.println(Prefs.getString(".dir.plankton"));
		//***********************************************************************************************************************************
				
		Settings.props = props; 
		systemFeature = Settings.getInt("feature", Feature.AREA+Feature.MINFERET+Feature.CIRC+Feature.MINFERET_PERI+Feature.SOL+Feature.ROUND+Feature.KURT);
		fluFeature = Settings.getLong("fluorescence", FeatureFlu.PE_MEAN+FeatureFlu.PC_MEAN+FeatureFlu.REDFLU_MEAN+FeatureFlu.GREENFLU_MEAN);
		hsbFeature = Settings.getInt("hsb",FeatureHSB.HU_HIST);
		texFeature = Settings.getInt("texture", FeatureTexture.LBP_18+FeatureTexture.LBP_28+FeatureTexture.LBP_38+FeatureTexture.LBP_216+FeatureTexture.LBP_316+FeatureTexture.IM1); 
		efdFeature = Settings.getInt("efd", FeatureEFD.EFD2+FeatureEFD.EFD3+FeatureEFD.EFD4+FeatureEFD.EFD5+FeatureEFD.EFD6+FeatureEFD.EFD7+FeatureEFD.EFD8
												+FeatureEFD.EFD9+FeatureEFD.EFD10+FeatureEFD.EFD11+FeatureEFD.EFD12+FeatureEFD.EFD13);
		glcmFeature = Settings.getInt("glcm", 0);
//		System.out.println(Feature.AREA+Feature.MINFERET+Feature.CIRC+Feature.MINFERET_PERI+Feature.SOL+Feature.ROUND+Feature.KURT);
		
		
		//Ask for features to use:
		GenericDialog gd = createDialaog();
        gd.showDialog();
        if (gd.wasCanceled()) return;
                
        Vector<Checkbox> check = gd.getCheckboxes();
        boolean[] usedFeature = new boolean[check.size()];
        String [] label = new String[check.size()];
        for (int i = 0; i<check.size(); i++){
        	usedFeature[i] = check.get(i).getState();
        	label[i] = check.get(i).getLabel();
        	
        	System.out.println(usedFeature[i]+" "+label[i]+ "	"+i);
        }
        
        // Ask for network structure
        GenericDialog gd2 = new GenericDialog("Define network ");
        gd2.addNumericField("Klassen", 12, 0, 6, null);
        gd2.addNumericField("Hiddenlayer1", 50, 0, 6, null);
        gd2.addNumericField("Hiddenlayer2", 30, 0, 6, null);
        gd2.addStringField("Network name", "network");
        gd2.showDialog();
        if (gd2.wasCanceled()) return;
        
        int output = (int) gd2.getNextNumber();
        int layer1 = (int) gd2.getNextNumber();
        int layer2 = (int) gd2.getNextNumber();
        Settings.networkName = gd2.getNextString();
//        System.out.println(output+" "+layer1+" "+layer2+" "+Settings.networkName);
        
        DirectoryChooser dc = new DirectoryChooser("Choose the Directory for the reference images");
        String dir = dc.getDirectory();
        if (dir == null)return;
		
        File dir1 = new File(dir); 
		String[] subdir = dir1.list();
		
		
		
		//filter for files with .tif
		FilenameFilter filter  = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.toLowerCase().endsWith(".tif") ;// && (name.toLowerCase().contains("_qff_")  || name.toLowerCase().contains("_h_")) ;
		}};
		
		// create new ResultsTable
		Analyzer.setPrecision(4);
		ResultsTable rt = new ResultsTable();
		Analyzer.setResultsTable(rt);
		
		// collect data for training
		FileWriter writer = null;
		try {
			writer = new FileWriter(workDir+"train.csv");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		int size = 0; 
		int input = 0; int rangemapped = 0; 
		int feature = 0, hsb = 0, texture = 0,  efd = 0, glcm = 0;
		long flu = 0L;
		for (int i = 0; i<subdir.length; i++){
			if (subdir[i].matches("Thumbs.db")){continue;}
			size = 0; 
			String[] imageList = new File(dir1+"/"+subdir[i]).list(filter);
			int lenght  = imageList.length;
			for(int j = 0; j<lenght; j++){
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
			
				input = 0; rangemapped = 0;
				feature = 0; efd= 0;hsb=0; texture=0;flu=0L;glcm=0;
				for (int l = 0; l< 1; l++){
					try {
						//feature for nomrilization
						//***********************************************************************************************************************
						//Image Moments
						if (usedFeature[22] == true){System.out.println(label[22]+" I0"); writer.append(rt.getValue("I0", l)+","); texture= texture+FeatureTexture.IM0;input++;rangemapped++;}//IM0
						if (usedFeature[28] == true){System.out.println(label[28]+" I1"); writer.append(rt.getValue("I1", l)+","); texture= texture+FeatureTexture.IM1;input++;rangemapped++;}//IM1
						if (usedFeature[34] == true){System.out.println(label[34]+" I2"); writer.append(rt.getValue("I2", l)+","); texture= texture+FeatureTexture.IM2;input++;rangemapped++;}//IM2
						if (usedFeature[39] == true){System.out.println(label[39]+" I3"); writer.append(rt.getValue("I3", l)+","); texture= texture+FeatureTexture.IM3;input++;rangemapped++;}//IM3
						if (usedFeature[44] == true){System.out.println(label[44]+" I4"); writer.append(rt.getValue("I4", l)+","); texture= texture+FeatureTexture.IM4;input++;rangemapped++;}//IM4
						if (usedFeature[49] == true){System.out.println(label[49]+" I5"); writer.append(rt.getValue("I5", l)+","); texture= texture+FeatureTexture.IM5;input++;rangemapped++;}//IM5
						if (usedFeature[23] == true){System.out.println(label[23]+" I6"); writer.append(rt.getValue("I6", l)+","); texture= texture+FeatureTexture.IM6;input++;rangemapped++;}//IM6
						if (usedFeature[29] == true){System.out.println(label[29]+" I7"); writer.append(rt.getValue("I7", l)+","); texture= texture+FeatureTexture.IM7;input++;rangemapped++;}//IM7
						
						// Elliptic Fourier Descriptor
						if (usedFeature[53] == true){System.out.println(label[53]+" EFD2"); writer.append(rt.getValue("efd2", l)+","); efd= efd+FeatureEFD.EFD2;input++; rangemapped++;}//EFD2
						if (usedFeature[54] == true){System.out.println(label[54]+" EFD3"); writer.append(rt.getValue("efd3", l)+","); efd= efd+FeatureEFD.EFD3;input++;rangemapped++;}//EFD3
						if (usedFeature[55] == true){System.out.println(label[55]+" EFD4"); writer.append(rt.getValue("efd4", l)+","); efd= efd+FeatureEFD.EFD4;input++;rangemapped++;}//EFD4
						if (usedFeature[56] == true){System.out.println(label[56]+" EFD5");writer.append(rt.getValue("efd5", l)+","); efd= efd+FeatureEFD.EFD5;input++;rangemapped++;} //EFD5
						if (usedFeature[57] == true){System.out.println(label[57]+" EFD6");writer.append(rt.getValue("efd6", l)+","); efd= efd+FeatureEFD.EFD6;input++;rangemapped++;}//EFD6
						if (usedFeature[58] == true){System.out.println(label[58]+" EFD7");writer.append(rt.getValue("efd7", l)+","); efd= efd+FeatureEFD.EFD7;input++;rangemapped++;}//EFD7
						if (usedFeature[59] == true){System.out.println(label[59]+" EFD8"); writer.append(rt.getValue("efd8", l)+","); efd= efd+FeatureEFD.EFD8;input++;rangemapped++;}//EFD8
						if (usedFeature[60] == true){System.out.println(label[60]+" EFD9"); writer.append(rt.getValue("efd9", l)+","); efd= efd+FeatureEFD.EFD9;input++;rangemapped++;}//EFD9
						if (usedFeature[61] == true){System.out.println(label[61]+" EFD10");writer.append(rt.getValue("efd10", l)+",");efd= efd+FeatureEFD.EFD10; input++;rangemapped++;}//EFD10
						if (usedFeature[62] == true){System.out.println(label[62]+" EFD11");writer.append(rt.getValue("efd11", l)+","); efd= efd+FeatureEFD.EFD11;input++;rangemapped++;}//EFD11
						if (usedFeature[63] == true){System.out.println(label[63]+" EFD12");writer.append(rt.getValue("efd12", l)+",");efd= efd+FeatureEFD.EFD12; input++;rangemapped++;}//EFD12
						if (usedFeature[64] == true){System.out.println(label[64]+" EFD13");writer.append(rt.getValue("efd13", l)+",");efd= efd+FeatureEFD.EFD13; input++;rangemapped++;}//EFD13
						if (usedFeature[65] == true){System.out.println(label[65]+" EFD14"); writer.append(rt.getValue("efd14", l)+","); efd= efd+FeatureEFD.EFD14;input++;rangemapped++;}//EFD14
						if (usedFeature[66] == true){System.out.println(label[66]+" EFD15"); writer.append(rt.getValue("efd15", l)+","); efd= efd+FeatureEFD.EFD15;input++;rangemapped++;}//EFD15
						if (usedFeature[67] == true){System.out.println(label[67]+" EFD16");writer.append(rt.getValue("efd16", l)+",");efd= efd+FeatureEFD.EFD16; input++;rangemapped++;}//EFD16
						if (usedFeature[68] == true){System.out.println(label[68]+" EFD17");writer.append(rt.getValue("efd17", l)+",");efd= efd+FeatureEFD.EFD17; input++;rangemapped++;}//EFD17
						if (usedFeature[69] == true){System.out.println(label[69]+" EFD18");writer.append(rt.getValue("efd18", l)+",");efd= efd+FeatureEFD.EFD18; input++;rangemapped++;}//EFD18
						if (usedFeature[70] == true){System.out.println(label[70]+" EFD19");writer.append(rt.getValue("efd19", l)+","); efd= efd+FeatureEFD.EFD19;input++;rangemapped++;}//EFD19
						if (usedFeature[71] == true){System.out.println(label[71]+" EFD20"); writer.append(rt.getValue("efd20", l)+","); efd= efd+FeatureEFD.EFD20;input++;rangemapped++;}//EFD20
						if (usedFeature[72] == true){System.out.println(label[72]+" EFD21"); writer.append(rt.getValue("efd21", l)+",");efd= efd+FeatureEFD.EFD21; input++;rangemapped++;}//EFD21
						if (usedFeature[73] == true){System.out.println(label[73]+" EFD22");  	writer.append(rt.getValue("efd22", l)+","); efd= efd+FeatureEFD.EFD22;input++;rangemapped++;}//EFD22
						if (usedFeature[74] == true){System.out.println(label[74]+" EFD23");  	writer.append(rt.getValue("efd23", l)+",");efd= efd+FeatureEFD.EFD23; input++;rangemapped++;}//EFD23
						if (usedFeature[75] == true){System.out.println(label[75]+" EFD24");  	writer.append(rt.getValue("efd24", l)+","); efd= efd+FeatureEFD.EFD24;input++;rangemapped++;}//EFD24
						if (usedFeature[76] == true){System.out.println(label[76]+" EFD25");  	writer.append(rt.getValue("efd25", l)+",");efd= efd+FeatureEFD.EFD25; input++;rangemapped++;}//EFD25
						if (usedFeature[77] == true){System.out.println(label[77]+" EFD26");  	writer.append(rt.getValue("efd26", l)+","); efd= efd+FeatureEFD.EFD26;input++;rangemapped++;}//EFD26
						if (usedFeature[78] == true){System.out.println(label[78]+" EFD27");  	writer.append(rt.getValue("efd27", l)+","); efd= efd+FeatureEFD.EFD27;input++;rangemapped++;}//EFD27
						if (usedFeature[79] == true){System.out.println(label[79]+" EFD28");  	writer.append(rt.getValue("efd28", l)+","); efd= efd+FeatureEFD.EFD28;input++;rangemapped++;}//EFD28
						if (usedFeature[80] == true){System.out.println(label[80]+" EFD29");  	writer.append(rt.getValue("efd29", l)+","); efd= efd+FeatureEFD.EDF29;input++;rangemapped++;}//EFD29
						
						if (usedFeature[17]== true) {System.out.println(label[17]+" Kurt");	writer.append(rt.getValue("Kurt", l)+",");	feature= feature+Feature.KURT; input++; rangemapped++;}
						if (usedFeature[10]== true){System.out.println(label[10]+" skew");	writer.append(rt.getValue("Skew", l)+","); feature= feature+Feature.SKEW; input++; rangemapped++;}
						if (usedFeature[1]== true) {System.out.println(label[1]+" minferet");writer.append(rt.getValue("MinFeret", l)+","); feature= feature+Feature.MINFERET;	input++; rangemapped++;} //MinFeret
						if (usedFeature[0]== true) {System.out.println(label[0]+" area");	writer.append(rt.getValue("Area", l)+","); feature= feature+Feature.AREA;	input++; rangemapped++;} //Area
						if (usedFeature[4]== true) {System.out.println(label[4]+" widtj");	writer.append(rt.getValue("Width", l) +",");	feature= feature+Feature.WIDTH;	input++; rangemapped++;}//width
						if (usedFeature[8]== true) {System.out.println(label[8]+" height");	writer.append(rt.getValue("Height", l) +",");	feature= feature+Feature.HEIGHT;		input++; rangemapped++;}
						if (usedFeature[13]== true) {System.out.println(label[13]+" Major");	writer.append(rt.getValue("Major", l)+","); feature= feature+Feature.MAJOR;			input++;rangemapped++; }//Major
						
						if (usedFeature[15]== true) {System.out.println(label[15]+" Perim");	writer.append(rt.getValue("Perim.", l)+","); feature= feature+Feature.PERI;		input++; rangemapped++;}
						if (usedFeature[16]== true) {System.out.println(label[16]+" Minor");	writer.append(rt.getValue("Minor", l)+",");	feature= feature+Feature.MINOR;		input++; rangemapped++;}//Minor
						if (usedFeature[18]== true) {System.out.println(label[18]+" Feret");	writer.append(rt.getValue("Feret", l)+",");	feature= feature+Feature.FERET;		input++;rangemapped++;} 
						if (usedFeature[19]== true) {System.out.println(label[19]+" Angle");    writer.append(rt.getValue("Angle", l)+",");	feature= feature+Feature.ANG;		input++; rangemapped++;}//Angle
						
						if (usedFeature[7]== true){
							System.out.println(label[7]+" rot sym");
							feature= feature+Feature.ROT_SYM;
							if (Double.isNaN(rt.getValue("Rot2Sym", l))){writer.append("-1,"); }
							else{writer.append(rt.getValue("Rot2Sym", l)+","); }
							input++; rangemapped++;
						}
						if (usedFeature[11]== true){
							feature= feature+Feature.REFL_SYM;
							System.out.println(label[11]+" reflsym");
							if (rt.getColumnHeadings().contains("ReflSym0") == true && rt.getValue("ReflSym0", l) != -1 && rt.getValue("ReflSym0", l) != 0 ) {writer.append(rt.getValue("ReflSym0", l)/180.0+",");}
							else {writer.append("-1,");}
							input++; rangemapped++;
							
							if (rt.getColumnHeadings().contains("ReflSym1") == true && rt.getValue("ReflSym1", l) != -1 && rt.getValue("ReflSym1", l) != 0 ) {writer.append(rt.getValue("ReflSym1", l)/180.0+","); }
							else {writer.append("-1,");}
							input++; rangemapped++;
							
							if (rt.getColumnHeadings().contains("ReflSym2") == true && rt.getValue("ReflSym2", l) != -1 && rt.getValue("ReflSym2", l) != 0 ) {writer.append(rt.getValue("ReflSym2", l)/180.0+","); }
							else {writer.append("-1,");}
							input++; rangemapped++;
							
							if (rt.getColumnHeadings().contains("ReflSym3") == true &&rt.getValue("ReflSym3", l) != -1 && rt.getValue("ReflSym3", l) != 0 ) {writer.append(rt.getValue("ReflSym3", l)/180.0+",");}
							else {writer.append("-1,");}
							input++; rangemapped++;
						}
						
						//GLCM
						if (usedFeature[81] == true){
//							writer.append(rt.getValue("Angular Second Moment_0_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_45_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_90_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_135_1", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.ASM1;
							System.out.println(label[81]+" ASM1");
						}
						
						if (usedFeature[82] == true){
//							writer.append(rt.getValue("Angular Second Moment_0_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_45_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_90_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_135_2", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.ASM2;
							System.out.println(label[82]+" ASM2");
						}
						
						if (usedFeature[83] == true){
//							writer.append(rt.getValue("Angular Second Moment_0_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_45_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_90_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_135_3", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.ASM3;
							System.out.println(label[83]+" ASM3");
						}
						
						if (usedFeature[84] == true){
//							writer.append(rt.getValue("Angular Second Moment_0_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_45_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_90_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_135_4", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.ASM4;
							System.out.println(label[84]+" ASM4");
						}
						
						if (usedFeature[85] == true){
//							writer.append(rt.getValue("Angular Second Moment_0_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_45_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_90_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Angular Second Moment_135_5", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.ASM5;
							System.out.println(label[85]+" ASM5");
						}
						
						//contrast
						if (usedFeature[86] == true){
//							writer.append(rt.getValue("Contrast_0_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_45_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_90_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_135_1", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.CONTRAST1;
							
							System.out.println(rt.getValue("Contrast_0_1", l)+ " "+rt.getValue("Contrast_45_1", l)+" "+rt.getValue("Contrast_90_1", l)
									+" "+rt.getValue("Contrast_135_1", l));
							
							
							System.out.println(label[86]+" Con1");
						}
						
						if (usedFeature[87] == true){
//							writer.append(rt.getValue("Contrast_0_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_45_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_90_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_135_2", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.CONTRAST2;
							System.out.println(label[87]+" Con2");
						}
						
						if (usedFeature[88] == true){
//							writer.append(rt.getValue("Contrast_0_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_45_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_90_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_135_3", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.CONTRAST3;
							System.out.println(label[88]+" Con3");
						}
						
						if (usedFeature[89] == true){
//							writer.append(rt.getValue("Contrast_0_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_45_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_90_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_135_4", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.CONTRAST4;
							System.out.println(label[89]+" Con4");
						}
						
						if (usedFeature[90] == true){
//							writer.append(rt.getValue("Contrast_0_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_45_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_90_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Contrast_135_5", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.CONTRAST5;
							System.out.println(label[90]+" Con5");
						}
						
						//Correlation
						if (usedFeature[91] == true){
//							writer.append(rt.getValue("Correlation_0_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_45_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_90_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_135_1", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.CORR1;
							System.out.println(label[91]+" Corr1");
						}
						
						if (usedFeature[92] == true){
//							writer.append(rt.getValue("Correlation_0_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_45_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_90_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_135_2", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.CORR2;
							System.out.println(label[92]+" Corr2");
						}
						
						if (usedFeature[93] == true){
//							writer.append(rt.getValue("Correlation_0_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_45_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_90_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_135_3", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.CORR3;
							System.out.println(label[93]+" Corr3");
						}
						
						if (usedFeature[94] == true){
//							writer.append(rt.getValue("Correlation_0_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_45_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_90_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_135_4", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.CORR4;
							System.out.println(label[94]+" Corr4");
						}
						
						if (usedFeature[95] == true){
//							writer.append(rt.getValue("Correlation_0_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_45_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_90_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Correlation_135_5", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.CORR5;
							System.out.println(label[95]+" Corr5");
						}
						
						//IDM
						if (usedFeature[96] == true){
//							writer.append(rt.getValue("Inverse Difference Moment_0_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_45_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_90_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_135_1", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.IDM1;
							System.out.println(label[96]+" IDM1");
						}
						
						if (usedFeature[97] == true){
//							writer.append(rt.getValue("Inverse Difference Moment_0_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_45_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_90_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_135_2", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.IDM2;
							System.out.println(label[97]+" IDM2");
						}
						
						if (usedFeature[98] == true){
//							writer.append(rt.getValue("Inverse Difference Moment_0_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_45_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_90_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_135_3", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.IDM3;
							System.out.println(label[98]+" IDM3");
						}
						
						if (usedFeature[99] == true){
//							writer.append(rt.getValue("Inverse Difference Moment_0_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_45_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_90_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_135_4", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.IDM4;
							System.out.println(label[99]+" IDM4");
						}
						
						if (usedFeature[100] == true){
//							writer.append(rt.getValue("Inverse Difference Moment_0_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_45_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_90_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Inverse Difference Moment_135_5", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.IDM5;
							System.out.println(label[100]+" IDM5");
						}
						
						//Entropy
						if (usedFeature[101] == true){
//							writer.append(rt.getValue("Entropy_0_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_45_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_90_1", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_135_1", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.ENTROPY1;
							System.out.println(label[101]+" Ent1");
						}
						
						if (usedFeature[102] == true){
//							writer.append(rt.getValue("Entropy_0_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_45_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_90_2", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_135_2", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.ENTROPY2;
							System.out.println(label[102]+" Ent2");
						}
						
						if (usedFeature[103] == true){
//							writer.append(rt.getValue("Entropy_0_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_45_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_90_3", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_135_3", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.ENTROPY3;
							System.out.println(label[103]+" Ent3");
						}
						
						if (usedFeature[104] == true){
//							writer.append(rt.getValue("Entropy_0_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_45_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_90_4", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_135_4", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.ENTROPY4;
							System.out.println(label[104]+" Ent4");
						}
						
						if (usedFeature[105] == true){
//							writer.append(rt.getValue("Entropy_0_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_45_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_90_5", l)+",");input++;rangemapped++;
							writer.append(rt.getValue("Entropy_135_5", l)+",");input++;rangemapped++;
							glcm = glcm + FeatureGLCM.ENTROPY5;
							System.out.println(label[105]+" Ent5");
						}
						
						//***********************************************************************************************************************
						
						//Local binary pattern
						if (usedFeature[21] == true){ 
							System.out.println(label[21]+" 18");
							texture= texture+FeatureTexture.LBP_18;
							for(int i1 = 0 ; i1<38; i1++){
								writer.append(rt.getValue("lbp"+i1+"_18", l)+","); input++;
							}
						}
						
						if (usedFeature[27] == true){
							System.out.println(label[27]+" 28");
							texture= texture+FeatureTexture.LBP_28;
							for(int i1 = 0 ; i1<38; i1++){
								writer.append(rt.getValue("lbp"+i1+"_28", l)+","); input++;
							}
						}
						
						if (usedFeature[33] == true){
							System.out.println(label[33]+" 38");
							texture= texture+FeatureTexture.LBP_38;
							for(int i1 = 0 ; i1<38; i1++){
								writer.append(rt.getValue("lbp"+i1+"_38", l)+","); input++;
							}
						}
						
						if (usedFeature[38] == true){
							System.out.println(label[38]+" 48");
							texture= texture+FeatureTexture.LBP_48;
							for(int i1 = 0 ; i1<38; i1++){
								writer.append(rt.getValue("lbp"+i1+"_48", l)+",");input++;
							}
						}
						
						if (usedFeature[43] == true){
							System.out.println(label[43]+" 216");
							texture= texture+FeatureTexture.LBP_216;
							for(int i1 = 0 ; i1<138; i1++){
								writer.append(rt.getValue("lbp"+i1+"_216", l)+",");input++;
							}
						}
						
						if (usedFeature[48] == true){
							System.out.println(label[48]+" 316");
							texture= texture+FeatureTexture.LBP_316;
							for(int i1 = 0 ; i1<138; i1++){
								writer.append(rt.getValue("lbp"+i1+"_316", l)+","); input++;
							}
						}
						
						
						
						//single feature
						if (usedFeature[2]== true) {System.out.println(label[2]+" circ");	writer.append(rt.getValue("Circ.", l)+",");	feature= feature+Feature.CIRC;			input++;  }//Circ
						if (usedFeature[14]== true) {System.out.println(label[14]+" Round");	writer.append(rt.getValue("Round", l)+",");	feature= feature+Feature.ROUND;		input++; }
						if (usedFeature[6]== true) {System.out.println(label[6]+" soliditiy"); writer.append(rt.getValue("Solidity", l)+",");	feature= feature+Feature.SOL;	input++; }
						if (usedFeature[5]== true) {System.out.println(label[5]+" feret/perim");writer.append(rt.getValue("MinFeret", l)/rt.getValue("Perim.", l)+","); feature= feature+Feature.MINFERET_PERI;input++; }//minFeret/perim
						if (usedFeature[9]== true) {System.out.println(label[9]+" fereta");	writer.append(rt.getValue("FeretAngle", l)/360.0+","); feature= feature+Feature.FERETANG;	input++; }
						if (usedFeature[12]== true) {System.out.println(label[12]+" AR");	writer.append(rt.getValue("AR", l)+",");feature= feature+Feature.AR;				input++; }
						if (usedFeature[20]== true) {System.out.println(label[20]+" intdem");writer.append(rt.getValue("IntDen", l)+",");	feature= feature+Feature.INTDEN;		input++; }//IntDen
						
						if (usedFeature[3]== true){
							System.out.println(label[3]+" directhist");
							feature= feature+Feature.DIRECT_HIST;
							for (int j1 = 0; j1<20; j1++){
								writer.append(rt.getValue(j1+"DR-norm", l)+","); input++;
							}
						}
												
					
							
						//PE
						if (usedFeature[106] == true){System.out.println(label[106]+" mean norm pe");	writer.append(rt.getValue("bright1", l)/255.0+","); flu= flu+FeatureFlu.PE_MEAN_NORM; input++;} //meannorm
						if (usedFeature[112] == true){System.out.println(label[112]+" mean pe"); 		writer.append(rt.getValue("bright1_mean", l)/255.0+ ",");flu= flu+FeatureFlu.PE_MEAN;input++;}//mean
						if (usedFeature[118] == true){System.out.println(label[118]+" mode pe"); 		writer.append(rt.getValue("bright1_mode", l)/255.0+ ",");flu= flu+FeatureFlu.PE_MODE;input++;}//mode
						if (usedFeature[124] == true){System.out.println(label[124]+" min pe");		writer.append(rt.getValue("bright1_min", l)/255.0+ ",");flu= flu+FeatureFlu.PE_MIN;input++;} //min
						if (usedFeature[130] == true){System.out.println(label[130]+" max pe" );		writer.append(rt.getValue("bright1_max", l)/255.0+ ",");flu= flu+FeatureFlu.PE_MAX;input++;}//max
						if (usedFeature[136] == true){System.out.println(label[136]+" stddev pe");		writer.append(rt.getValue("bright1_stdev", l)/255.0+",");flu= flu+FeatureFlu.PE_STDDEV;input++;} //
						if (usedFeature[139] == true){System.out.println(label[139]+" fluparts pe");	writer.append(rt.getValue("ratio flu1", l)+",");flu= flu+FeatureFlu.PE_FLUPARTS;input++; }//
						
						//PC
						if (usedFeature[107] == true){System.out.println(label[107]+" mean norm pc");	writer.append(rt.getValue("bright2", l)/255.0+",");flu= flu+FeatureFlu.PC_MEAN_NORM; input++;} //meannorm
						if (usedFeature[113] == true){System.out.println(label[113]+" mean pc"); 		writer.append(rt.getValue("bright2_mean", l)/255.0+ ",");flu= flu+FeatureFlu.PC_MEAN;input++;}//mean
						if (usedFeature[119] == true){System.out.println(label[119]+" mode  pc"); 		writer.append(rt.getValue("bright2_mode", l)/255.0+ ",");flu= flu+FeatureFlu.PC_MODE;input++;}//mode
						if (usedFeature[125] == true){System.out.println(label[125]+" min pc");		writer.append(rt.getValue("bright2_min", l)/255.0+ ",");flu= flu+FeatureFlu.PC_MIN;input++;} //min
						if (usedFeature[131] == true){System.out.println(label[131]+" max pc"); 		writer.append(rt.getValue("bright2_max", l)/255.0+ ",");flu= flu+FeatureFlu.PC_MAX;input++;}///max
						if (usedFeature[137] == true){System.out.println(label[137]+" stddev pc");		writer.append(rt.getValue("bright2_stdev", l)/255.0+",");flu= flu+FeatureFlu.PC_STDDEV;input++;} //
						if (usedFeature[140] == true){System.out.println(label[140]+" fluparts pc"); 	writer.append(rt.getValue("ratio flu2", l)+",");flu= flu+FeatureFlu.PC_FLUPARTS;input++;} //
						
						//CHL
						if (usedFeature[108] == true){System.out.println(label[108]+" mean norm chl"); writer.append(rt.getValue("bright3", l)/255.0+",");flu= flu+FeatureFlu.CHL_MEAN_NORM; input++;}//meannorm
						if (usedFeature[114] == true){System.out.println(label[114]+" mean  chl"); 	writer.append(rt.getValue("bright3_mean", l)/255.0+ ",");flu= flu+FeatureFlu.CHL_MEAN;input++;}//mean
						if (usedFeature[120] == true){System.out.println(label[120]+" mode chl"); 		writer.append(rt.getValue("bright3_mode", l)/255.0+ ",");flu= flu+FeatureFlu.CHL_MODE;input++;}//mode
						if (usedFeature[126] == true){System.out.println(label[126]+" min chl"); 		writer.append(rt.getValue("bright3_min", l)/255.0+ ",");flu= flu+FeatureFlu.CHL_MIN;input++;}//min
						if (usedFeature[132] == true){System.out.println(label[132]+" max chl"); 		writer.append(rt.getValue("bright3_max", l)/255.0+ ",");flu= flu+FeatureFlu.CHL_MAX;input++;}//max
						if (usedFeature[138] == true){System.out.println(label[138]+" stddev chl"); 	writer.append(rt.getValue("bright3_stdev", l)/255.0+",");flu= flu+FeatureFlu.CHL_STDDEV;input++;}//
						if (usedFeature[141] == true){System.out.println(label[141]+" fluparts chl"); 	writer.append(rt.getValue("ratio flu3", l)+",");flu= flu+FeatureFlu.CHL_FLUPARTS;input++;} //
						
						if (usedFeature[109] == true){System.out.println(label[109]+" mean red chl"); writer.append(rt.getValue("redbrmean_Chl", l)/255.0+",");flu= flu+FeatureFlu.REDFLU_MEAN;input++; }
						if (usedFeature[115] == true){System.out.println(label[115]+" mode red chl"); writer.append(rt.getValue("redbrmode_Chl", l)/255.0+",");flu= flu+FeatureFlu.REDFLU_MODE;input++; }
						if (usedFeature[121] == true){System.out.println(label[121]+" max red chl"); writer.append(rt.getValue("redbrmax_Chl", l)/255.0+",");flu= flu+FeatureFlu.REDFLU_MAX;input++; }
						if (usedFeature[127] == true){System.out.println(label[127]+" min red chl"); writer.append(rt.getValue("redbrmin_Chl", l)/255.0+",");flu= flu+FeatureFlu.REDFLU_MIN;input++; }
						if (usedFeature[133] == true){System.out.println(label[133]+" stdev red chl"); writer.append(rt.getValue("redbrstdv_Chl", l)/255.0+",");flu= flu+FeatureFlu.REDFLU_STDDEV;input++; }
						
						if (usedFeature[110] == true){System.out.println(label[110]+" mean green chl"); writer.append(rt.getValue("greenbrmean_Chl", l)/255.0+",");flu= flu+FeatureFlu.GREENFLU_MEAN;input++;} 
						if (usedFeature[116] == true){System.out.println(label[116]+" mode green chl"); writer.append(rt.getValue("greenbrmode_Chl", l)/255.0+",");flu= flu+FeatureFlu.GREENFLU_MODE;input++; }
						if (usedFeature[122] == true){System.out.println(label[122]+" max green chl"); writer.append(rt.getValue("greenbrmax_Chl", l)/255.0+",");flu= flu+FeatureFlu.GREENFLU_MAX;input++; }
						if (usedFeature[128] == true){System.out.println(label[128]+" min green chl"); writer.append(rt.getValue("greenbrmin_Chl", l)/255.0+",");flu= flu+FeatureFlu.GREENFLU_MIN;input++; }
						if (usedFeature[134] == true){System.out.println(label[134]+" stdev green chl"); writer.append(rt.getValue("greenbrstdv_Chl", l)/255.0+",");flu= flu+FeatureFlu.GREENFLU_STDDEV;input++; }
						
						if (usedFeature[111] == true){System.out.println(label[111]+" mean color chl"); writer.append(rt.getValue("humean_Chl", l)/255.0+",");flu= flu+FeatureFlu.HUFLU_MEAN;input++; }
						if (usedFeature[117] == true){System.out.println(label[117]+" mode color chl"); writer.append(rt.getValue("humode_Chl", l)/255.0+",");flu= flu+FeatureFlu.HUFLU_MODE;input++; }
						if (usedFeature[123] == true){System.out.println(label[123]+" max color chl"); writer.append(rt.getValue("humax_Chl", l)/255.0+",");flu= flu+FeatureFlu.HUFLU_MAX;input++; }
						if (usedFeature[129] == true){System.out.println(label[129]+" min color chl"); writer.append(rt.getValue("humin_Chl", l)/255.0+",");flu= flu+FeatureFlu.HUFLU_MIN;input++; }
						if (usedFeature[135] == true){System.out.println(label[135]+" stdev color chl"); writer.append(rt.getValue("hustdv_Chl", l)/255.0+",");flu= flu+FeatureFlu.HUFLU_STDDEV;input++; }
						
						//Color
						if (usedFeature[24] == true){
							hsb = hsb+FeatureHSB.HU_HIST;
							System.out.println(label[24]+" Hist hu"); //Histogramm
							for(int i1 = 0 ; i1<255; i1++){
								writer.append(rt.getValue("hu_hist_"+i1, l)+","); input++;
							}
						}
						if (usedFeature[30] == true){System.out.println(label[30]+" min hu");	writer.append(rt.getValue("min hu", l)/256.0+",");hsb = hsb+FeatureHSB.HU_MIN; input++;}//min
						if (usedFeature[35] == true){System.out.println(label[35]+" max hu"); 	writer.append(rt.getValue("max hu", l)/256.0+",");hsb = hsb+FeatureHSB.HU_MAX; input++;}//max
						if (usedFeature[40] == true){System.out.println(label[40]+" mean hu");	writer.append(rt.getValue("mean hu", l)/256.0+","); hsb = hsb+FeatureHSB.HU_MEAN;input++;} //mean
						if (usedFeature[45] == true){System.out.println(label[45]+" mode hu");	writer.append(rt.getValue("mode hu",l)/256.0+",");hsb = hsb+FeatureHSB.HU_MODE; input++;} //mode
						if (usedFeature[50] == true){System.out.println(label[50]+" stdev hu");	writer.append(rt.getValue("stdev hu", l)/256.0+","); hsb = hsb+FeatureHSB.HU_STDDEV;input++; }//stdev

						//Saturation
						if (usedFeature[25] == true){
							hsb = hsb+FeatureHSB.SAT_HIST;
							System.out.println(label[25]+" Hist sat"); //Histogramm
							for(int i1 = 0 ; i1<255; i1++){
								writer.append(rt.getValue("sat_hist_"+i1, l)+","); input++;
							}
						}
						if (usedFeature[31] == true){System.out.println(label[31]+" min sat"); writer.append(rt.getValue("min bright", l)/256.0+",");hsb = hsb+FeatureHSB.SAT_MIN;input++;}//min
						if (usedFeature[36] == true){System.out.println(label[36]+" max sat"); writer.append(rt.getValue("max bright", l)/256.0+",");hsb = hsb+FeatureHSB.SAT_MAX;input++;}//max
						if (usedFeature[41] == true){System.out.println(label[41]+" mean sat"); writer.append(rt.getValue("mean bright", l)/256.0+",");hsb = hsb+FeatureHSB.SAT_MEAN;input++;}//mean
						if (usedFeature[46] == true){System.out.println(label[46]+" mode sat"); writer.append(rt.getValue("mode bright",l)/256.0+",");hsb = hsb+FeatureHSB.SAT_MODE;input++;}//mode
						if (usedFeature[51] == true){System.out.println(label[51]+" stdev sat"); writer.append(rt.getValue("stdev bright", l)/256.0+",");hsb = hsb+FeatureHSB.SAT_STDDEV;input++;}//stdev
						
						//Brightness
						if (usedFeature[26] == true){
							hsb = hsb+FeatureHSB.BRIGHT_HIST;
							System.out.println(label[26]+" Hist bright"); //Histogramm
							for(int i1 = 0 ; i1<255; i1++){
								writer.append(rt.getValue("bright_hist_"+i1, l)+",");input++;
							}
						}
						if (usedFeature[32] == true){System.out.println(label[32]+" min bright"); writer.append(rt.getValue("min bright", l)/256.0+",");hsb = hsb+FeatureHSB.BRIGHT_MIN;input++;}//min
						if (usedFeature[37] == true){System.out.println(label[37]+" max bright"); writer.append(rt.getValue("max bright", l)/256.0+",");hsb = hsb+FeatureHSB.BRIGHT_MAX;input++;}//max
						if (usedFeature[42] == true){System.out.println(label[42]+" mean bright");writer.append(rt.getValue("mean bright", l)/256.0+",");hsb = hsb+FeatureHSB.BRIGHT_MEAN;input++;} //mean
						if (usedFeature[47] == true){System.out.println(label[47]+" mode bright");writer.append(rt.getValue("mode bright",l)/256.0+","); hsb = hsb+FeatureHSB.BRIGHT_MODE;input++;}//mode
						if (usedFeature[52] == true){System.out.println(label[52]+" stdev bright"); writer.append(rt.getValue("stdev bright", l)/256.0+",");hsb = hsb+FeatureHSB.BRIGHT_STDDEV;input++;}//stdev
						
						
						
						size++;
						writer.append(subdir[i]);
						writer.append('\n');
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
				rt.reset();
			}
		}

		try {
			writer.flush();
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//create the network
		BasicNetwork network = new BasicNetwork();
		network.addLayer(new BasicLayer(null,true,input));
		network.addLayer(new BasicLayer(new ActivationElliottSymmetric(),true,layer1));
		if (layer2!=0)network.addLayer(new BasicLayer(new ActivationElliottSymmetric(),true,layer2));//27
		network.addLayer(new BasicLayer(new ActivationElliottSymmetric(),false,output));
		network.getStructure().finalizeStructure();
		network.reset();
		
		System.out.println(rangemapped);
		// Normalization
		DataNormalization norm = this.normalize(workDir+"train.csv", input, output, workDir+"TrainNorm.csv",0,9,10, rangemapped);
		
		// save the normalization
		System.out.println("Saving norm");
		try {
			SerializeObject.save(new File (workDir+"/networks/"+Settings.networkName+"_norm.ser"), norm);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Training
		MLDataSet NormTrainingSet = EncogUtility.loadCSV2Memory(workDir+"TrainNorm.csv", input, output, false, CSVFormat.DECIMAL_POINT, false);
		
		CalculateScore score = new TrainingSetScore(NormTrainingSet);
		MLTrain train = new ResilientPropagation(network, NormTrainingSet);
		train.addStrategy(new HybridStrategy(new  NeuralGeneticAlgorithm( network, new RangeRandomizer(-1,1), score, 50, 0.2, 0.10)));
		(new ConsistentRandomizer(-1,1,1000)).randomize(network);
		TrainingDialog.trainDialog(train, network, NormTrainingSet);

		Frame FR = WindowManager.getFrame("Results"); 
	    if (FR instanceof TextWindow)	((TextWindow)FR).close(); 
	    else if (FR instanceof PlugInFrame)	((PlugInFrame)FR).close(); 
	    
	    //save the network
		EncogDirectoryPersistence.saveObject(new File(workDir+"/networks/"+Settings.networkName), network);
		
		props.put("feature", Integer.toString(feature));
		props.put("fluorescence", Long.toString(flu));
		props.put("hsb", Integer.toString(hsb));
		props.put("texture", Integer.toString(texture));
		props.put("efd", Integer.toString(efd));
		props.put("glcm", Integer.toString(glcm));
		props.put("input", Integer.toString(input));
		props.put("output", Integer.toString(output));
		try {
			Settings.savePrefs(props, workDir+"/networks/"+Settings.networkName+"_pref.txt");
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		IJ.showMessage("training completed");
	}
	
private GenericDialog createDialaog() {
		

	
	
	
		GenericDialog gd = new GenericDialog("Use following feature: ");
	    
	    String[] head = new String[1];
	    head[0] = "single feature";
	    String[] labels_stand= {"Area", 		 	"Min Feret",			"Circularity",	"Direc Histogram",
	    						"Width", 			"MinFeret/Perim",		"Solidity",		"rot Sym",	
	    						"Height",			"Feret Angle",			"Skewness",		"refl Sym",		
	    						"Aspect Ratio",		"Major",				"Roundness",	"",					
	    						"Perimeter",		"Minor",				"Kurtosity",	"",
	    						"Feret Diameter",	"Angle",				"Int Density",	""
	    };
	    		 
	    		
	    boolean[] bool_stand = {(systemFeature&Feature.AREA)!=0, (systemFeature&Feature.MINFERET)!=0,	(systemFeature&Feature.CIRC)!=0,	(systemFeature&Feature.DIRECT_HIST)!=0,
	    						(systemFeature&Feature.WIDTH)!=0, (systemFeature&Feature.MINFERET_PERI)!=0,	(systemFeature&Feature.SOL)!=0,	(systemFeature&Feature.ROT_SYM)!=0,
	    						(systemFeature&Feature.HEIGHT)!=0, (systemFeature&Feature.FERETANG)!=0,	(systemFeature&Feature.SKEW)!=0,	(systemFeature&Feature.REFL_SYM)!=0,
	    						(systemFeature&Feature.AR)!=0, (systemFeature&Feature.MAJOR)!=0,	(systemFeature&Feature.ROUND)!=0,	false,
	    						(systemFeature&Feature.PERI)!=0, (systemFeature&Feature.MINOR)!=0,	(systemFeature&Feature.KURT)!=0,	false,
	    						(systemFeature&Feature.FERET)!=0, (systemFeature&Feature.ANG)!=0,	(systemFeature&Feature.INTDEN)!=0,	false,
	    };
	   
	    gd.addCheckboxGroup(6,4, labels_stand , bool_stand, head ); 
	    
	  
	    head = new String[6];
	    head[0] = "LBP";
	    head[1] = "Moments";
	    head[3] = "Color (hu)";
	    head[4] = "Saturation";
	    head[5] = "Brightness";
	    String[] labels = {	"R = 1; ? = 8",	"IM 0", "IM6",	"Histogram c",	"Histogram s",	"Histogram b", 	
	    					"R = 2; ? = 8",	"IM 1", "IM7",	"min c",			"min s",			"min b",	
	    					"R = 3; ? = 8", "IM 2",	"",		"max c",			"max s",			"max b",	
	    					"R = 4; ? = 8", "IM 3", "",		"mean c",			"mean s",			"mean b",	
	    					"R = 2; ? = 16","IM 4", "",		"mode c", 		"mode s",			"mode b",	
	    					"R = 3; ? = 16","IM 5", "", 	"stddev c",		"stddev s",		"stddev b"
	    					};
	    
	    boolean[] bool = {	(texFeature&FeatureTexture.LBP_18)!=0,	(texFeature&FeatureTexture.IM0)!=0, (texFeature&FeatureTexture.IM6)!=0,	(hsbFeature&FeatureHSB.HU_HIST)!=0,	(hsbFeature&FeatureHSB.SAT_HIST)!=0, (hsbFeature&FeatureHSB.BRIGHT_HIST)!=0, 	
	    					(texFeature&FeatureTexture.LBP_28)!=0,	(texFeature&FeatureTexture.IM1)!=0, (texFeature&FeatureTexture.IM7)!=0,	(hsbFeature&FeatureHSB.HU_MIN)!=0,	(hsbFeature&FeatureHSB.SAT_MIN)!=0, (hsbFeature&FeatureHSB.BRIGHT_MIN)!=0, 	
	    					(texFeature&FeatureTexture.LBP_38)!=0,	(texFeature&FeatureTexture.IM2)!=0, false,								(hsbFeature&FeatureHSB.HU_MAX)!=0,	(hsbFeature&FeatureHSB.SAT_MAX)!=0, (hsbFeature&FeatureHSB.BRIGHT_MAX)!=0, 	
	    					(texFeature&FeatureTexture.LBP_48)!=0,	(texFeature&FeatureTexture.IM3)!=0, false,								(hsbFeature&FeatureHSB.HU_MEAN)!=0,	(hsbFeature&FeatureHSB.SAT_MEAN)!=0, (hsbFeature&FeatureHSB.BRIGHT_MEAN)!=0, 	
	    					(texFeature&FeatureTexture.LBP_216)!=0,	(texFeature&FeatureTexture.IM4)!=0, false,								(hsbFeature&FeatureHSB.HU_MODE)!=0,	(hsbFeature&FeatureHSB.SAT_MODE)!=0, (hsbFeature&FeatureHSB.BRIGHT_MODE)!=0, 	
	    					(texFeature&FeatureTexture.LBP_316)!=0,	(texFeature&FeatureTexture.IM5)!=0, false,								(hsbFeature&FeatureHSB.HU_STDDEV)!=0,	(hsbFeature&FeatureHSB.SAT_STDDEV)!=0, (hsbFeature&FeatureHSB.BRIGHT_STDDEV)!=0, 	
				};
	    
	    gd.addCheckboxGroup(6, 6, labels, bool, head); 
	   
	    
	    head = new String[1];
	    head[0] = "EFD";
	    String[] labels_efd = {	"2",	"3",	"4", 	"5",	"6","7",	"8",	"9", 	"10",	"11",
								"12",	"13",	"14",	"15",	"16","17",	"18",	"19", 	"20",	"21",
								"22",	"23",	"24",	"25",	"26","27",	"28",	"29", 	"",	"",
								
				};
	    boolean[] bool_efd = {	(efdFeature&FeatureEFD.EFD2)!=0,	(efdFeature&FeatureEFD.EFD3)!=0, 	(efdFeature&FeatureEFD.EFD4)!=0,	(efdFeature&FeatureEFD.EFD5)!=0,	(efdFeature&FeatureEFD.EFD6)!=0, 	
	    						(efdFeature&FeatureEFD.EFD7)!=0,	(efdFeature&FeatureEFD.EFD8)!=0, 	(efdFeature&FeatureEFD.EFD9)!=0,	(efdFeature&FeatureEFD.EFD10)!=0,	(efdFeature&FeatureEFD.EFD11)!=0, 	
	    						(efdFeature&FeatureEFD.EFD12)!=0,	(efdFeature&FeatureEFD.EFD13)!=0, 	(efdFeature&FeatureEFD.EFD14)!=0,	(efdFeature&FeatureEFD.EFD15)!=0,	(efdFeature&FeatureEFD.EFD16)!=0, 	
	    						(efdFeature&FeatureEFD.EFD17)!=0,	(efdFeature&FeatureEFD.EFD18)!=0, 	(efdFeature&FeatureEFD.EFD19)!=0,	(efdFeature&FeatureEFD.EFD20)!=0,	(efdFeature&FeatureEFD.EFD21)!=0, 	
	    						(efdFeature&FeatureEFD.EFD22)!=0,	(efdFeature&FeatureEFD.EFD23)!=0, 	(efdFeature&FeatureEFD.EFD24)!=0,	(efdFeature&FeatureEFD.EFD25)!=0,	(efdFeature&FeatureEFD.EFD26)!=0, 	
	    						(efdFeature&FeatureEFD.EFD27)!=0,	(efdFeature&FeatureEFD.EFD28)!=0, 	(efdFeature&FeatureEFD.EDF29)!=0,	false,	false, 	
	    };
	    
	    gd.addCheckboxGroup(3, 10, labels_efd, bool_efd, head); 
	  
	    head = new String[5];
	    head[0] = "GLCM-1";
	    head[1] = "GLCM-2";
	    head[2] = "GLCM-3";
	    head[3] = "GLCM-4";
	    head[4] = "GLCM-5";
	    String[] labelsGLMC = {	"ASM1","ASM2","ASM3","ASM4","ASM5", 	
	    						"Contrast1",				"Contrast2", 			"Contrast3",				"Contrast4",				"Contrast5",	
	    						"Correlation",			"Correlation",			"Correlation",			"Correlation",			"Correlation",	
	    						"IDM", 	"IDM", 	"IDM",	"IDM",	"IDM",		
	    						"Entropy",				"Entropy", 				"Entropy",				"Entropy", 				"Entropy",	
	    						};
	    
	    boolean[] boolGLMC = {	(glcmFeature&FeatureGLCM.ASM1)!=0,	(glcmFeature&FeatureGLCM.ASM2)!=0, (glcmFeature&FeatureGLCM.ASM3)!=0,	(glcmFeature&FeatureGLCM.ASM4)!=0,	(glcmFeature&FeatureGLCM.ASM5)!=0, 	
	    						(glcmFeature&FeatureGLCM.CONTRAST1)!=0,	(glcmFeature&FeatureGLCM.CONTRAST2)!=0,(glcmFeature&FeatureGLCM.CONTRAST3)!=0,(glcmFeature&FeatureGLCM.CONTRAST4)!=0,(glcmFeature&FeatureGLCM.CONTRAST5)!=0,
	    						(glcmFeature&FeatureGLCM.CORR1)!=0, (glcmFeature&FeatureGLCM.CORR2)!=0, (glcmFeature&FeatureGLCM.CORR3)!=0, (glcmFeature&FeatureGLCM.CORR4)!=0, (glcmFeature&FeatureGLCM.CORR5)!=0,
	    						(glcmFeature&FeatureGLCM.IDM1)!=0,(glcmFeature&FeatureGLCM.IDM2)!=0,(glcmFeature&FeatureGLCM.IDM3)!=0,(glcmFeature&FeatureGLCM.IDM4)!=0,(glcmFeature&FeatureGLCM.IDM5)!=0,
	    						(glcmFeature&FeatureGLCM.ENTROPY1)!=0, (glcmFeature&FeatureGLCM.ENTROPY2)!=0, (glcmFeature&FeatureGLCM.ENTROPY3)!=0, (glcmFeature&FeatureGLCM.ENTROPY4)!=0, (glcmFeature&FeatureGLCM.ENTROPY5)!=0,
	    };
	    
	    gd.addCheckboxGroup(6, 5, labelsGLMC, boolGLMC, head); 
	    
	    gd.addMessage("------------------------------- " +
	  			  "Only use fluorescence feature if data are availabel for all organisms!" +
	  			  " -------------------------------");
	  
	  head = new String[3];
	  head[0] = "PE";
	  head[1] = "PC";
	  head[2] = "CHL";
	  
	  String[] labels_flu = {	"mean norm pe",	"mean norm pc",	"mean norm chl", 	"mean red",		"mean green", 	"mean color",
	  						"mean  pe",			"mean pc",			"mean chl",			"mode red",		"mode green",	"mode color ",
	  						"mode  pe",			"mode pc",			"mode chl",			"max red",		"max green",	"max color",
	  						"min  pe",			"min pc",			"min chl",			"min red",		"min green",	"min color",
	  						"max pe", 			"max pc",			"max chl",			"stddev red",	"stddev green",	"stddev color",
	  						"stddev pe",		"stddev pc",		"stddev chl",		"",	"","",
	  						"flu parts pe",	"flu parts pc",	"flu parts", 	"",	"",""
	  };
	  
	  boolean[] bool_flu = {	(fluFeature&FeatureFlu.PE_MEAN_NORM)!=0,	(fluFeature&FeatureFlu.PC_MEAN_NORM)!=0, (fluFeature&FeatureFlu.CHL_MEAN_NORM)!=0, (fluFeature&FeatureFlu.REDFLU_MEAN)!=0,	(fluFeature&FeatureFlu.GREENFLU_MEAN)!=0, (fluFeature&FeatureFlu.HUFLU_MEAN)!=0,
	  						(fluFeature&FeatureFlu.PE_MEAN)!=0,	(fluFeature&FeatureFlu.PC_MEAN)!=0, (fluFeature&FeatureFlu.CHL_MEAN)!=0, (fluFeature&FeatureFlu.REDFLU_MODE)!=0,	(fluFeature&FeatureFlu.GREENFLU_MODE)!=0, (fluFeature&FeatureFlu.HUFLU_MODE)!=0,
	  						(fluFeature&FeatureFlu.PE_MODE)!=0,	(fluFeature&FeatureFlu.PC_MODE)!=0, (fluFeature&FeatureFlu.CHL_MODE)!=0, (fluFeature&FeatureFlu.REDFLU_MAX)!=0,	(fluFeature&FeatureFlu.GREENFLU_MAX)!=0, (fluFeature&FeatureFlu.HUFLU_MAX)!=0,
	  						(fluFeature&FeatureFlu.PE_MIN)!=0,	(fluFeature&FeatureFlu.PC_MIN)!=0, (fluFeature&FeatureFlu.CHL_MIN)!=0, (fluFeature&FeatureFlu.REDFLU_MIN)!=0,	(fluFeature&FeatureFlu.GREENFLU_MIN)!=0, (fluFeature&FeatureFlu.HUFLU_MIN)!=0,
	  						(fluFeature&FeatureFlu.PE_MAX)!=0,	(fluFeature&FeatureFlu.PC_MAX)!=0, (fluFeature&FeatureFlu.CHL_MAX)!=0, (fluFeature&FeatureFlu.REDFLU_STDDEV)!=0,	(fluFeature&FeatureFlu.GREENFLU_STDDEV)!=0, (fluFeature&FeatureFlu.HUFLU_STDDEV)!=0,
	  						(fluFeature&FeatureFlu.PE_STDDEV)!=0,	(fluFeature&FeatureFlu.PC_STDDEV)!=0, (fluFeature&FeatureFlu.CHL_STDDEV)!=0, 	false							,	false, false,
	  						(fluFeature&FeatureFlu.PE_FLUPARTS)!=0,	(fluFeature&FeatureFlu.PC_FLUPARTS)!=0, (fluFeature&FeatureFlu.CHL_FLUPARTS)!=0, 	false						,	false, false,
	  };
	  
	  gd.addCheckboxGroup(7, 6, labels_flu, bool_flu, head);
	  
	    
	    
		return gd;
	}

public DataNormalization normalize(String file, int inputsize, int outputsize, String fileOutput, int start, int end, int size, int rangemapped )
	{
		InputField  inputField[] = new InputField[inputsize];
		InputField coverType;
		DataNormalization norm = new DataNormalization();
		norm.setReport(new NullStatusReportable());
		norm.setTarget(new NormalizationStorageCSV(new File(fileOutput)));
		
		for (int i = 0; i < inputsize; i++ ){
			inputField[i] =  new InputFieldCSV(true, new File(file),i);
			norm.addInputField(inputField[i]);
			
			OutputField outputField;
			if (i<rangemapped){	outputField = new OutputFieldRangeMapped(inputField[i]);}		
			else outputField = new OutputFieldDirect(inputField[i]);
			norm.addOutputField(outputField);
		}
		
		norm.addInputField(coverType=new InputFieldCSV(false,new File(file), inputsize));
		OutputOneOf outType = new OutputOneOf();
		
		for (int i = 0; i<outputsize; i++){
			outType.addItem(coverType, i);
		}

		norm.addOutputField(outType, true);
		
		IndexSampleSegregator segregator = 	new IndexSampleSegregator(start, end, size);
		norm.addSegregator(segregator);
			
		
		norm.process();
		
		return norm; 
		
	}
	

	
}
