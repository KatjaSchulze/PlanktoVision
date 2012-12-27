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



import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.ColorModel;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.encog.ml.data.MLData;
import org.encog.neural.networks.BasicNetwork;
import org.encog.persist.EncogDirectoryPersistence;
import org.encog.util.normalize.DataNormalization;
import org.encog.util.normalize.output.nominal.OutputEquilateral;
import org.encog.util.obj.SerializeObject;

import calc.Directionality;
import calc.EllipticFD;
import calc.ImageMoments_;
import calc.LBP_Texture;
import calc.ReadImages;
import calc.RegionGrowing;
import calc.Symmetry;



import feature.Feature;
import feature.FeatureEFD;
import feature.FeatureFlu;
import feature.FeatureGLCM;
import feature.FeatureHSB;
import feature.FeatureTexture;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.NewImage;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.measure.ResultsTable;
import ij.plugin.Clipboard;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.filter.RankFilters;
import ij.plugin.frame.RoiManager;
import ij.process.AutoThresholder;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

public class PVanalysis_ implements PlugIn {

	private ResultsTable rt;
	private boolean[] flucount; 
	private static int systemFeature, systemFeature2 ;
	private static long fluFeature, fluFeature2 ;
	private static int hsbFeature, hsbFeature2 ;
	private static int texFeature, texFeature2;
	private static int efdFeature, efdFeature2;
	private static int glcmFeature, glcmFeature2;
	private static int input, input2; 
	private static int output, output2;
	String[] classes, colors;
	BasicNetwork network2;
	DataNormalization norm2;
	
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
		
		// check if two networks are used 
		boolean twoNetworks = false;
		GenericDialog gd = new GenericDialog("");
		gd.addMessage("How many networks do you want to use?");
		gd.enableYesNoCancel("one", "two");
		gd.showDialog();
		if (gd.wasCanceled())
	           return;
        else if (!gd.wasOKed())
	            twoNetworks = true;
		
		
		//load the normalisation & the network
		//===================================================================================================================================
		String label = "Choose your network"; 
		if (twoNetworks == true) {label = "Choose the first network";}
		
		OpenDialog odnw = new OpenDialog(label, workDir, "");
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
		input = Settings.getInt("input", 0);
		output = Settings.getInt("output", 12);
		//***********************************************************************************************************************************
		
		int classesnw1= 0;
		boolean[] choices = new boolean[output];
		HashMap<String, String> classname = new HashMap<String, String>();
		HashMap<String, String> classcolor = new HashMap<String, String>();
		Arrays.fill(choices, false);

		if (twoNetworks == true){
			GenericDialog gd2n = new GenericDialog("");
			gd2n.addMessage("Which classes of the first network do you want to classify with the second network?");
			for (int i = 0; i<output; i++){
				gd2n.addCheckbox(i+"", false);
			}
			gd2n.showDialog();
			for (int i = 0; i<output; i++){
				choices[i] = gd2n.getNextBoolean();
				if (choices[i]==true){
					classesnw1++;
				}
				else{classname.put("NW1-"+i, i+"");}
			}
			
			if (classesnw1 == 0){
				IJ.showMessage("Es wird nur ein netzwerk verwendet");
			}
			else{
			//load the normalisation & the network
			//===================================================================================================================================
			odnw = new OpenDialog("Choose the second network", workDir, "");
			if (odnw.getFileName() == null)return;
			Settings.networkName = odnw.getFileName();
			
			network2 = (BasicNetwork) EncogDirectoryPersistence.loadObject(new File(odnw.getDirectory()+"/"+Settings.networkName));
			
			norm2 = null;
			try {
				norm2 = (DataNormalization) SerializeObject.load(new File (odnw.getDirectory()+"/"+Settings.networkName+"_norm.ser"));
			} catch (ClassNotFoundException e1) {
				e1.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			//***********************************************************************************************************************************
			
			//load the feature settings
			//===================================================================================================================================
			Settings.loadPreferences();
			systemFeature2 = Settings.getInt("feature", Feature.AREA+Feature.MINFERET+Feature.CIRC+Feature.MINFERET_PERI+Feature.SOL+Feature.ROUND+Feature.KURT);
			fluFeature2 = Settings.getLong("fluorescence", FeatureFlu.PE_MEAN+FeatureFlu.PC_MEAN+FeatureFlu.REDFLU_MEAN+FeatureFlu.GREENFLU_MEAN);
			hsbFeature2 = Settings.getInt("hsb",FeatureHSB.HU_HIST);
			texFeature2 = Settings.getInt("texture", FeatureTexture.LBP_18+FeatureTexture.LBP_28+FeatureTexture.LBP_38+FeatureTexture.LBP_216+FeatureTexture.LBP_316+FeatureTexture.IM1); 
			efdFeature2 = Settings.getInt("efd", FeatureEFD.EFD2+FeatureEFD.EFD3+FeatureEFD.EFD4+FeatureEFD.EFD5+FeatureEFD.EFD6+FeatureEFD.EFD7+FeatureEFD.EFD8
													+FeatureEFD.EFD9+FeatureEFD.EFD10+FeatureEFD.EFD11+FeatureEFD.EFD12+FeatureEFD.EFD13);
			glcmFeature2 = Settings.getInt("glcm", 0);
			input2 = Settings.getInt("input", 669);
			output2 = Settings.getInt("output", 12);
			
			for (int i = 0; i<output2; i++){
				classname.put("NW2-"+i, i+"");
			}
			
			
			//***********************************************************************************************************************************
			}
		}
		else{
			for (int i = 0; i<output; i++){
				classname.put("NW1-"+i, i+"");
			}
		}
		
		
		//define names for the classes
		//===================================================================================================================================
		GenericDialog gdcn = new GenericDialog("Set class names.");
		String[] choice = {"red", "green", "blue", "white", "yellow", "orange"};
		
		String[] keys = classname.keySet().toArray(new String[0]);
		List<String> keylist = Arrays.asList(keys);
		Collections.sort(keylist, new AlphanumComparator());
		keys  = (String[]) keylist.toArray(new String[0]);
		
		for (int i = 0; i<classname.size(); i++){
			gdcn.addStringField("class", keys[i]); 
			gdcn.addChoice("color", choice, "white");
		}
		gdcn.showDialog();
		if (gdcn.wasCanceled())return;
		
		for (int i = 0; i<classname.size();i++){
			classname.put(keys[i], gdcn.getNextString() );
			classcolor.put(keys[i], gdcn.getNextChoice());
		}
		
		//***********************************************************************************************************************************
		
		
		// image for the lightning correction
		//===================================================================================================================================
		ImagePlus wb = null;
		ByteProcessor brightlight = null;
		File f = new File (workDir+"Ausleuchtung.TIF");
		boolean lightcorr = true;
		if(f.exists()) {
			wb = new ImagePlus(f.getPath());
			ColorProcessor cplight = (ColorProcessor)wb.getProcessor();
			byte[] H_l = new byte[wb.getWidth()*wb.getHeight()];
			byte[] S_l = new byte[wb.getWidth()*wb.getHeight()];
			byte[] B_l = new byte[wb.getWidth()*wb.getHeight()];
			cplight.getHSB(H_l, S_l, B_l);
			brightlight = new ByteProcessor(wb.getWidth(), wb.getHeight(), B_l, ColorModel.getRGBdefault());
		}
		else{
			IJ.showMessage("Kein Bild für Ausleuchtungskorrektur gefunden! Korrektur wird nicht durchgeführt"  );
			lightcorr = false; 
		}
		//***********************************************************************************************************************************
		
		//filter for image type
		//===================================================================================================================================
		FilenameFilter filter  = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.toLowerCase().endsWith(".tif") && name.toLowerCase().contains("_qff_")  ;//&&  name.toLowerCase().startsWith("d_pf"); // && !name.toLowerCase().startsWith("a");
					
		}};
		//***********************************************************************************************************************************
		
		//image directory
		//===================================================================================================================================
		OpenDialog od = new OpenDialog("Open image ...", arg);
		if (od.getDirectory() == null)return;
		File dir = new File(od.getDirectory());
		
		//***********************************************************************************************************************************
		
		// create new ResultsTable
		Analyzer.setPrecision(4);
		this.rt = new ResultsTable();
		Analyzer.setResultsTable(this.rt);
		
		
		final String[] imageList = new File(dir+"").list(filter);
		java.util.Arrays.sort(imageList);
		HashMap<String, Integer> classresults = new HashMap<String, Integer>();
		
		for (int j = 0; j < imageList.length; j++){
			String path = dir+"/"+imageList[j];	
			ReadImages im = new ReadImages(path);
			ImagePlus images = new ImagePlus(); 
			images = im.getImages();
		    images.show();
			images.getWindow().setLocation(10, 10); 
			flucount = im.getBoolean();
			im = null;
			//lightning correction
			//===================================================================================================================================
			if (lightcorr ==true){
				ColorProcessor cp = (ColorProcessor)images.getProcessor();
				byte[] H = new byte[images.getWidth()*images.getHeight()];
				byte[] S = new byte[images.getWidth()*images.getHeight()];
				byte[] B = new byte[images.getWidth()*images.getHeight()];
				cp.getHSB(H, S, B);
	
				ByteProcessor b = new ByteProcessor(images.getWidth(), images.getHeight(), B, ColorModel.getRGBdefault());
				lightCorr(b, brightlight, brightlight.getStatistics().mean, 0);
				cp.setHSB(H, S, (byte[]) b.getPixels());
				images.updateAndDraw();
			}
			//***********************************************************************************************************************************	
			
			//segmentation
			//===================================================================================================================================
			RoiManager rm = segment(images, imageList[j]);
			//***********************************************************************************************************************************	
			
			//feature calculation & classification
			//===================================================================================================================================
			getFeature(images, rm, imageList[j]);
			rm.runCommand("Show None");
			classresults = classify(images, rm, imageList[j], norm, network, choices, classcolor, classname, classresults);
		    images.updateAndDraw();
		    //***********************************************************************************************************************************	
			
	
			IJ.wait(2000);
			images.close();
		    rm.close();
			rt.reset();
		}
		
	    //show results
	    //===================================================================================================================================
		String res = new String();
		String classnames = new String();
		
		for (int r = 0; r<classname.size();r++){
			classnames = classnames+" \t"+classname.get(keys[r]);
			if (classresults.get(keys[r])==null){
				res = res+" \t"+0;
			}
			else {
				res = res+" \t"+classresults.get(keys[r]);
			}
		}
		IJ.log(classnames);
		IJ.log(res);
		//***********************************************************************************************************************************	
		
	    
		
	}

	//***********************************************************************************************************************************	
	
	
	public int determineTreeType(OutputEquilateral eqField, MLData output) {
		int result = 0;

		if (eqField != null) {
			result = eqField.getEquilateral().decode(output.getData());
		} else {
			double maxOutput = Double.NEGATIVE_INFINITY;
			result = -1;

			for (int i = 0; i < output.size(); i++) {
				if (output.getData(i) > maxOutput) {
					maxOutput = output.getData(i);
					result = i;
				}
			}
		}
		return result;
	}
	
	public ImageProcessor lightCorr(ImageProcessor ip1, ImageProcessor ip2, double k1, double k2){
		/* 	Berechnung aus CalculatorPlus plugin // nur für 8bit // nur mit Divide Funktion
	 	i1: experimental Image
	 	i2: lightning Image (without objects)
	 	k1: mean of Image 2
	 	K2: has to be set to zero
	 
	 	the currentSlice of i1 is corrected with image 2 
	 	
	 	*/ 
		
		
		double v1, v2=0;
	    int width  = ip1.getWidth();
	    int height = ip1.getHeight();
			
				for (int x=0; x<width; x++) {
					for (int y=0; y<height; y++) {
						v1 = ip1.getPixelValue(x,y);
						v2 = ip2.getPixelValue(x,y);
						//divide
						v2 = v2!=0.0?v1/v2:0.0;
						
						v2 = v2*k1 + k2;
						ip1.putPixelValue(x, y, v2);
					}  
				}
		return ip1;

	}
	
	private RoiManager segment(ImagePlus currentImages, String filename) {
		RoiManager rm = new RoiManager();

		// run Segmentation
		ImagePlus hsb = makeHSBStack(currentImages);
		RegionGrowing set  = new RegionGrowing(hsb);
		set.initialize();
		set.start();
		rm = set.getRM();

		return rm; 
	}
	

	
	public ImagePlus makeHSBStack(ImagePlus img) {
		/*returns an HSBStack Image for further analysis
		 *input: RGB image 
		 */
		ColorProcessor cp = (ColorProcessor) img.getProcessor();

		int width  = cp.getWidth();
	    int height = cp.getHeight();
	    
		ImagePlus hallo = NewImage.createByteImage("HSB Stack", width, height, 3, 1);
	    
		hallo.setStack(cp.getHSBStack());
		return hallo;
		
	}
	
	private void getFeature(ImagePlus currentImages,RoiManager rm, String filename){
		
		Roi roi = null; 
		
		int resolution = 0; 
		if (filename.toLowerCase().contains("20x_")) resolution = 20; 
		else if (filename.toLowerCase().contains("40x_")) resolution = 40; 
		else if (filename.toLowerCase().contains("60x_")) resolution = 60;
		else if (filename.toLowerCase().contains("100x_")) resolution = 100;
		
	// get "normalized" Color Image
	//************************************************************************************
		ImagePlus hsb2 = new ImagePlus("hsb2", currentImages.getProcessor());
		IJ.run(hsb2, "HSB Stack", "");
		
		BackgroundSubtracter backsub = new BackgroundSubtracter();
		ImageProcessor s1 = hsb2.getStack().getProcessor(2);
		backsub.rollingBallBackground(s1, 50, false, false, true, true, true);
		s1.threshold(25);
		IJ.run(hsb2, "RGB Color", "");
		
		

	//		Analyze Rois
	//*******************************************************************************************
		
		Roi[] rois =  rm.getRoisAsArray();

		Analyzer.setMeasurements(2091775);
		
		if (rois.length>0){
			
			for (int i = 0; i < rois.length; i++){
				
				currentImages.setSlice(1);
				currentImages.setRoi(rois[i]);
				roi = new PolygonRoi(rois[i].getPolygon(), rois[i].getType());
				Roi roi2 = new PolygonRoi(rois[i].getPolygon(), rois[i].getType());
				
				int counter = i;
				roi.setLocation(0, 0);
				roi2.setLocation(0, 0);
					
				// copy rectangel selection from original image and save it
				WindowManager.setCurrentWindow(currentImages.getWindow());
				Clipboard clipb = new Clipboard();
				clipb.run("copy");
					
				ImagePlus impClip = null; 
				try {
					impClip = new ImagePlus("impclip", (Image)clipb.getTransferData(DataFlavor.imageFlavor));
						
				} catch (UnsupportedFlavorException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
					
				// Standard Measurements Roi
				//**********************************************************************************************************************
				impClip.setRoi(roi);
				IJ.run(impClip, "Measure", "");
				impClip.killRoi();
				//**********************************************************************************************************************
					
				//brightness (mean, stdev, histogram...)
				//**********************************************************************************************************************
				ImagePlus hsb = new ImagePlus("hsb", impClip.getProcessor());
				IJ.run(hsb, "HSB Stack", "");
				ImageProcessor b = hsb.getStack().getProcessor(3);
				b.setRoi(roi);
				
				if ((hsbFeature&FeatureHSB.BRIGHT_MAX) != 0 | (hsbFeature&FeatureHSB.BRIGHT_MIN) != 0 | (hsbFeature&FeatureHSB.BRIGHT_MEAN) != 0
				   | (hsbFeature&FeatureHSB.BRIGHT_MODE) != 0 | (hsbFeature&FeatureHSB.BRIGHT_STDDEV) != 0 |
				   (hsbFeature2&FeatureHSB.BRIGHT_MAX) != 0 | (hsbFeature2&FeatureHSB.BRIGHT_MIN) != 0 | (hsbFeature2&FeatureHSB.BRIGHT_MEAN) != 0
				   | (hsbFeature2&FeatureHSB.BRIGHT_MODE) != 0 | (hsbFeature2&FeatureHSB.BRIGHT_STDDEV) != 0){
				
					rt.setValue("mean bright", counter, b.getStatistics().mean);
					rt.setValue("stdev bright", counter, b.getStatistics().stdDev);
					rt.setValue("mode bright", counter, b.getStatistics().mode);
					rt.setValue("min bright", counter, b.getStatistics().min);
					rt.setValue("max bright", counter, b.getStatistics().max);
				}
				
				if ((hsbFeature&FeatureHSB.BRIGHT_HIST) != 0 | (hsbFeature2&FeatureHSB.BRIGHT_HIST) != 0){
					int[] hb = b.getHistogram();
					double countb = b.getStatistics().area;
					for(int i1 = 0 ; i1<255; i1++){
						rt.setValue("bright_hist_"+i1, i, (double)hb[i1]/countb);
					}
					
				}
					
				//**********************************************************************************************************************
					
				//Saturation (mean, stdev, histogram...)
				//**********************************************************************************************************************
				ImageProcessor sat = hsb.getStack().getProcessor(2);
				sat.setRoi(roi);
				
				if ((hsbFeature&FeatureHSB.SAT_MAX) != 0 | (hsbFeature&FeatureHSB.SAT_MIN) != 0 | (hsbFeature&FeatureHSB.SAT_MEAN) != 0
					 | (hsbFeature&FeatureHSB.SAT_MODE) != 0 | (hsbFeature&FeatureHSB.SAT_STDDEV) != 0 |
					(hsbFeature2&FeatureHSB.SAT_MAX) != 0 | (hsbFeature2&FeatureHSB.SAT_MIN) != 0 | (hsbFeature2&FeatureHSB.SAT_MEAN) != 0
					 | (hsbFeature2&FeatureHSB.SAT_MODE) != 0 | (hsbFeature2&FeatureHSB.SAT_STDDEV) != 0){
					rt.setValue("mean sat", counter, sat.getStatistics().mean);
					rt.setValue("stdev sat", counter, sat.getStatistics().stdDev);
					rt.setValue("mode sat", counter, sat.getStatistics().mode);
					rt.setValue("min sat", counter, sat.getStatistics().min);
					rt.setValue("max sat", counter, sat.getStatistics().max);
				}
					
				if ((hsbFeature&FeatureHSB.SAT_HIST) != 0 | (hsbFeature2&FeatureHSB.SAT_HIST) != 0){
					int[] hs = sat.getHistogram();
					double counts = sat.getStatistics().area;
					for(int i1 = 0 ; i1<255; i1++){
						rt.setValue("sat_hist_"+i1, i, (double)hs[i1]/counts);
					}
				}
					
				// get hu histogram
				//************************************************************************************************************************
				ImageProcessor hu = hsb.getStack().getProcessor(1);
				hu.setRoi(roi);
				
				if ((hsbFeature&FeatureHSB.HU_MAX) != 0 | (hsbFeature&FeatureHSB.HU_MIN) != 0 | (hsbFeature&FeatureHSB.HU_MEAN) != 0
						   | (hsbFeature&FeatureHSB.HU_MODE) != 0 | (hsbFeature&FeatureHSB.HU_STDDEV) != 0 | 
					(hsbFeature2&FeatureHSB.HU_MAX) != 0 | (hsbFeature2&FeatureHSB.HU_MIN) != 0 | (hsbFeature2&FeatureHSB.HU_MEAN) != 0
						   | (hsbFeature2&FeatureHSB.HU_MODE) != 0 | (hsbFeature2&FeatureHSB.HU_STDDEV) != 0){
					rt.setValue("mean hu", counter, hu.getStatistics().mean);
					rt.setValue("stdev hu", counter, hu.getStatistics().stdDev);
					rt.setValue("mode hu", counter, hu.getStatistics().mode);
					rt.setValue("min hu", counter, hu.getStatistics().min);
					rt.setValue("max hu", counter, hu.getStatistics().max);
				}
				
				if ((hsbFeature&FeatureHSB.HU_HIST) != 0 | (hsbFeature2&FeatureHSB.HU_HIST) != 0){
					int[] hhu = hu.getHistogram();
					double counthu = hu.getStatistics().area;
					for(int i1 = 0 ; i1<255; i1++){
						rt.setValue("hu_hist_"+i1, counter, (double)hhu[i1]/counthu);
					}
				}
				
				//rotate image for rotation invarient analysis
				//**********************************************************************************************************************
				ImagePlus bright = new ImagePlus("bright", b);
				double angle = rt.getValue("Angle", rt.getCounter()-1); // +90 
				IJ.run(bright, "Rotate...", "angle=" + angle + " grid=1 interpolation=Bilinear fill enlarge"); 
				// set selection im middle of Clipboard
				int clipw = bright.getProcessor().getWidth();
				int cliph = bright.getProcessor().getHeight();
				int roiw = roi2.getBounds().width;
				int roih = roi2.getBounds().height;
				int rw  = (clipw - roiw)/2 ;
				int rh  = (cliph - roih)/2 ;
				roi2.setLocation(rw, rh);
				// Rotate Selection
				bright.setRoi(roi2);
				IJ.run(bright, "Rotate...", "angle=" + angle); 
				roi2 =  bright.getRoi();
				// remove outside
				bright.getProcessor().fillOutside(roi2);
				IJ.run(bright, "Crop", "");
				//**********************************************************************************************************************

				//GLMC
				//**********************************************************************************************************************
				
				if ((glcmFeature&FeatureGLCM.ASM1) != 0 | (glcmFeature&FeatureGLCM.CONTRAST1) != 0 | (glcmFeature&FeatureGLCM.CORR1) != 0
					| (glcmFeature&FeatureGLCM.ENTROPY1) != 0 | (glcmFeature&FeatureGLCM.IDM1) != 0
					| (glcmFeature2&FeatureGLCM.ASM1) != 0 | (glcmFeature2&FeatureGLCM.CONTRAST1) != 0 | (glcmFeature2&FeatureGLCM.CORR1) != 0
					| (glcmFeature2&FeatureGLCM.ENTROPY1) != 0 | (glcmFeature2&FeatureGLCM.IDM1) != 0){
						IJ.run(bright, "GLCM Texture ", "enter=1 select=[0 degrees] angular contrast correlation inverse entropy");
					}
				if ((glcmFeature&FeatureGLCM.ASM2) != 0 | (glcmFeature&FeatureGLCM.CONTRAST2) != 0 | (glcmFeature&FeatureGLCM.CORR2) != 0
						| (glcmFeature&FeatureGLCM.ENTROPY2) != 0 | (glcmFeature&FeatureGLCM.IDM2) != 0 |
						(glcmFeature2&FeatureGLCM.ASM2) != 0 | (glcmFeature2&FeatureGLCM.CONTRAST2) != 0 | (glcmFeature2&FeatureGLCM.CORR2) != 0
						| (glcmFeature2&FeatureGLCM.ENTROPY2) != 0 | (glcmFeature2&FeatureGLCM.IDM2) != 0){
						IJ.run(bright, "GLCM Texture ", "enter=2 select=[0 degrees] angular contrast correlation inverse entropy");
					}
				if ((glcmFeature&FeatureGLCM.ASM3) != 0 | (glcmFeature&FeatureGLCM.CONTRAST3) != 0 | (glcmFeature&FeatureGLCM.CORR3) != 0
						| (glcmFeature&FeatureGLCM.ENTROPY3) != 0 | (glcmFeature&FeatureGLCM.IDM3) != 0 |
						(glcmFeature2&FeatureGLCM.ASM3) != 0 | (glcmFeature2&FeatureGLCM.CONTRAST3) != 0 | (glcmFeature2&FeatureGLCM.CORR3) != 0
						| (glcmFeature2&FeatureGLCM.ENTROPY3) != 0 | (glcmFeature2&FeatureGLCM.IDM3) != 0){
						IJ.run(bright, "GLCM Texture ", "enter=3 select=[0 degrees] angular contrast correlation inverse entropy");
					}
				if ((glcmFeature&FeatureGLCM.ASM4) != 0 | (glcmFeature&FeatureGLCM.CONTRAST4) != 0 | (glcmFeature&FeatureGLCM.CORR4) != 0
						| (glcmFeature&FeatureGLCM.ENTROPY4) != 0 | (glcmFeature&FeatureGLCM.IDM4) != 0 |
						(glcmFeature2&FeatureGLCM.ASM4) != 0 | (glcmFeature2&FeatureGLCM.CONTRAST4) != 0 | (glcmFeature2&FeatureGLCM.CORR4) != 0
						| (glcmFeature2&FeatureGLCM.ENTROPY4) != 0 | (glcmFeature2&FeatureGLCM.IDM4) != 0){
						IJ.run(bright, "GLCM Texture ", "enter=4 select=[0 degrees] angular contrast correlation inverse entropy");
					}
				if ((glcmFeature&FeatureGLCM.ASM5) != 0 | (glcmFeature&FeatureGLCM.CONTRAST5) != 0 | (glcmFeature&FeatureGLCM.CORR5) != 0
						| (glcmFeature&FeatureGLCM.ENTROPY5) != 0 | (glcmFeature&FeatureGLCM.IDM5) != 0 |
						(glcmFeature2&FeatureGLCM.ASM5) != 0 | (glcmFeature2&FeatureGLCM.CONTRAST5) != 0 | (glcmFeature2&FeatureGLCM.CORR5) != 0
						| (glcmFeature2&FeatureGLCM.ENTROPY5) != 0 | (glcmFeature2&FeatureGLCM.IDM5) != 0){
						IJ.run(bright, "GLCM Texture ", "enter=5 select=[0 degrees] angular contrast correlation inverse entropy");
					}
				//**********************************************************************************************************************
			
				// Directionality Histogram
				//**********************************************************************************************************************
				if ((systemFeature&Feature.DIRECT_HIST)!=0 | (systemFeature2&Feature.DIRECT_HIST)!=0){
					ImagePlus huimp = new ImagePlus("HU", hsb.getStack().getProcessor(3));
					huimp.getProcessor().setColor(255);
					huimp.getProcessor().fillOutside(roi);
					Directionality da = new Directionality();
					da.run("nbins=20, start=0, method=gradient", huimp);
					da.displayResultsTable();
				}
				//**********************************************************************************************************************
				
				// Fourier Analysis of Contour
				//**********************************************************************************************************************
				if (efdFeature!=0 | efdFeature2!=0){
					int n = roi.getPolygon().npoints; 
					double[] x = new double[n];
					double[] y = new double[n];
					Rectangle rect = roi.getBounds();  
					int[] xp = roi.getPolygon().xpoints;
					int[] yp = roi.getPolygon().ypoints;
						  
					for (int i1 = 0; i1 < n; i1++){
						x[i1] = (double) (rect.x + xp[i1]);
					    y[i1] = (double) (rect.y + yp[i1]); 
					}
					
					EllipticFD fourier = new EllipticFD(x, y, 30);
				}
				//**********************************************************************************************************************
					
				// get binary mask of Roi
				ImagePlus mask = new ImagePlus("mask", bright.getMask());
				mask.getProcessor().invert();
					
				// Symmetry Measurements;
				//**********************************************************************************************************************
				if ((systemFeature&Feature.REFL_SYM)!=0 | (systemFeature&Feature.ROT_SYM)!=0 | (systemFeature2&Feature.REFL_SYM)!=0 | (systemFeature2&Feature.ROT_SYM)!=0){
					Symmetry sym = new Symmetry(mask, roi2);
					sym.run();
					rt.setValue("Rot2Sym", i, sym.getRot2Sym());
					rt.setValue("BThresh", i, sym.belowThreshold() );
					
					for (int j= 0 ; j <sym.getReflSym().size(); j++){
						rt.setValue("ReflSym"+j, i,sym.getReflSym().get(j)+0.01); // +0.01 um von 0 WErten unterscheiden zu können!!!!!
					}
				}
				//**********************************************************************************************************************
					
				// Local binary pattern
				//**********************************************************************************************************************
				ImagePlus bright2 = new ImagePlus("lbp", b);
				bright2.setRoi(roi);
				boolean a = false;
				LBP_Texture lbp;
					
				if ((texFeature&FeatureTexture.LBP_18)!=0 | (texFeature2&FeatureTexture.LBP_18)!=0){
					lbp = new LBP_Texture(bright2);
					a =  lbp.run(false, 1, 8);
						
					if (a == true){
						double[] feature = lbp.getFeature(); 
						for (int f = 0; f< feature.length; f++){
							rt.setValue("lbp"+f+"_"+1+""+8, counter, feature[f]);
						}
					}else{
						for (int f = 0; f< 38; f++){
							rt.setValue("lbp"+f+"_"+1+""+8, counter, -1);
						}
					}
				}
								
				if ((texFeature&FeatureTexture.LBP_28)!=0 | (texFeature2&FeatureTexture.LBP_28)!=0){
					lbp = new LBP_Texture(bright2);
					a =lbp.run(false, 2, 8);
					if (a == true){
						double[] feature = lbp.getFeature(); 
						for (int f = 0; f< feature.length; f++){
							rt.setValue("lbp"+f+"_"+2+""+8, counter, feature[f]);
						}
					}else{
						for (int f = 0; f< 38; f++){
							rt.setValue("lbp"+f+"_"+2+""+8, counter, -1);
						}
					}
				}
					
				if ((texFeature&FeatureTexture.LBP_38)!=0 | (texFeature2&FeatureTexture.LBP_38)!=0){
					lbp = new LBP_Texture(bright2);
					a = lbp.run(false, 3, 8);
						
					if (a == true){
						double[] feature = lbp.getFeature(); 
						for (int f = 0; f< feature.length; f++){
							rt.setValue("lbp"+f+"_"+3+""+8, counter, feature[f]);
						}
					}else{
						for (int f = 0; f< 38; f++){
							rt.setValue("lbp"+f+"_"+3+""+8, counter, -1);
						}
					}
				}
					
				if ((texFeature&FeatureTexture.LBP_48)!=0 | (texFeature2&FeatureTexture.LBP_48)!=0){
					lbp = new LBP_Texture(bright2);
					a = lbp.run(false, 4, 8);
						
					if (a == true){
						double[] feature = lbp.getFeature(); 
						for (int f = 0; f< feature.length; f++){
							rt.setValue("lbp"+f+"_"+4+""+8, counter, feature[f]);
						}
					}else{
						for (int f = 0; f< 38; f++){
							rt.setValue("lbp"+f+"_"+4+""+8, counter, -1);
						}
					}
				}
					
				if ((texFeature&FeatureTexture.LBP_216)!=0 | (texFeature2&FeatureTexture.LBP_216)!=0){
					lbp = new LBP_Texture(bright2);
					a = lbp.run(false, 2, 16);
					if (a == true){
						double[] feature = lbp.getFeature(); 
						for (int f = 0; f< feature.length; f++){
							rt.setValue("lbp"+f+"_"+2+""+16, counter, feature[f]);
						}
					}else{
						for (int f = 0; f<138; f++){
							rt.setValue("lbp"+f+"_"+2+""+16, counter, -1);
						}
					}
				}
					
				if ((texFeature&FeatureTexture.LBP_316)!=0 | (texFeature2&FeatureTexture.LBP_316)!=0){
					lbp = new LBP_Texture(bright2);
					a = lbp.run(false, 3, 16);
					if (a == true){
						double[] feature = lbp.getFeature(); 
						for (int f = 0; f< feature.length; f++){
							rt.setValue("lbp"+f+"_"+3+""+16, counter, feature[f]);
						}
					}else{
						for (int f = 0; f<138; f++){
							rt.setValue("lbp"+f+"_"+3+""+16, counter, -1);
						}
					}
				}
				//**********************************************************************************************************************
				// ImageMoments
				//**********************************************************************************************************************
					
				if ((texFeature&FeatureTexture.IM0)!=0 | (texFeature&FeatureTexture.IM1)!=0 | (texFeature&FeatureTexture.IM2)!=0 |(texFeature&FeatureTexture.IM3)!=0
				| (texFeature&FeatureTexture.IM4)!=0 | (texFeature&FeatureTexture.IM5)!=0 | (texFeature&FeatureTexture.IM6)!=0 | (texFeature&FeatureTexture.IM7)!=0 |
				  (texFeature2&FeatureTexture.IM0)!=0 | (texFeature2&FeatureTexture.IM1)!=0 | (texFeature2&FeatureTexture.IM2)!=0 |(texFeature2&FeatureTexture.IM3)!=0
				| (texFeature2&FeatureTexture.IM4)!=0 | (texFeature2&FeatureTexture.IM5)!=0 | (texFeature2&FeatureTexture.IM6)!=0 | (texFeature2&FeatureTexture.IM7)!=0){
								
					ImageMoments_ mom = new ImageMoments_(); 
					mom.setup("", bright2);
					mom.run(bright.getProcessor());
					double[] inv = mom.getInvariantMoments();
	//				double[][] sm = mom.getScaledMoments();
	//				double[][] cm = mom.getCentralMoments();
					for (int m = 0; m < inv.length; m++){ 
						rt.setValue("I"+m, counter, inv[m]);
					}
				}
				//**********************************************************************************************************************
					
				//Add resolution
				//**********************************************************************************************************************
				rt.setValue("resolution", counter, resolution);
				//**********************************************************************************************************************
			   }
			}
			

	//*****************************************************************************************************************************************************			
	// Fluorescence Measurements
	//*****************************************************************************************************************************************************			
		// check if respective fluorescence image is available and calculate Fluorescence values for Rois
		// 1 ==> pc
		// 2 ==> pe
		// 3 ==> ChloA
	//******************************************************************************************************************************************************
		if (fluFeature != 0 | fluFeature2 != 0){
				String name = null;
				ImagePlus threshold = NewImage.createByteImage("threshold", currentImages.getWidth(), currentImages.getHeight(),1, NewImage.FILL_BLACK);
				
				double   ratio, back, TFI;  
				rois =  rm.getRoisAsArray();
				
				for (int j = 1; j <flucount.length; j++){
					if (flucount[j] == true){
						
						name = "bright"+j;
//						currentImages.setSlice(j+1);
						int width = currentImages.getWidth();
						int height = currentImages.getHeight(); 
						ColorProcessor cpI = (ColorProcessor)currentImages.getStack().getProcessor(j+1);
						
						byte[] R = new byte[width*height];
						byte[] S = new byte[width*height];
						byte[] B = new byte[width*height];
						byte[] H = new byte[width*height];
						cpI.getHSB(H, S, B);
						cpI.getRGB(R, new byte[width*height], new byte[width*height]);
						
						ByteProcessor bp = new ByteProcessor(currentImages.getWidth(), currentImages.getHeight(), B, ColorModel.getRGBdefault());
						ByteProcessor hp = new ByteProcessor(currentImages.getWidth(), currentImages.getHeight(), H, ColorModel.getRGBdefault());
						
						ratio = 0; 
						back = bp.getStatistics().mode;
						
						for (int i = 0; i < rois.length; i++){
		
								bp.setRoi(rois[i]);
							    if (back == Double.MIN_VALUE) back = 0; 
								TFI = (bp.getStatistics().mean-back) / (255 - back);
								rt.setValue(name, i, TFI );
								rt.setValue(name+"_stdev", i, bp.getStatistics().stdDev);
								rt.setValue(name+"_mean", i, bp.getStatistics().mean );
								rt.setValue(name+"_mode", i, bp.getStatistics().mode );
								rt.setValue(name+"_min", i, bp.getStatistics().min );
								rt.setValue(name+"_max", i, bp.getStatistics().max );
								
						}
						
						// ratio non fluorescent/ fluorescent parts
						threshold.getProcessor().copyBits(bp, 0, 0, Blitter.COPY);
//						threshold.show();
//						threshold.getWindow().setLocation(1000, 0);
						
						threshold.killRoi();
						ImageProcessor ipt = threshold.getProcessor();
						
						
						//threshold image
						if (j == 3){
							AutoThresholder adjust = new AutoThresholder();
							int globalThreshold = adjust.getThreshold("Default",ipt.getHistogram());
							if (globalThreshold < 35) ipt.threshold(globalThreshold);
							else ipt.threshold(35);
							threshold.updateAndDraw();
						}
						else{
							ipt.threshold(45);
							threshold.updateAndDraw();	
						}
											
						
						// only red
						for (int l = 0 ; l<threshold.getProcessor().getHeight(); l++){
							int offset = l * threshold.getProcessor().getWidth();
							for (int i = 0 ; i<threshold.getProcessor().getWidth(); i++){
								if (hp.get(offset+i)>43 && hp.get(offset+i)<200 && threshold.getProcessor().get(i,l)==255){threshold.getProcessor().set(i, l, 0);}
							}
						}
						
						//remove noise
						RankFilters rank = new RankFilters();
						rank.rank(ipt, 1.0, RankFilters.OUTLIERS, 0, 50); 
						threshold.updateAndDraw();
											
						for (int i = 0; i < rois.length; i++){
							roi = new PolygonRoi(rois[i].getPolygon(), rois[i].getType());
							threshold.setRoi(roi);
							ipt.setRoi(roi);
							ratio = ipt.getHistogram()[255]/rt.getValue("Area", i) ;
							rt.setValue("ratio flu"+j, i, ratio);
							ipt.reset();
						}
						
						if (j == 3){
							
							R = new byte[width*height];
							byte[] G = new byte[width*height];
							byte[] Bl = new byte[width*height];
							cpI.getRGB(R, G, Bl);
							
							ByteProcessor rp = new ByteProcessor(currentImages.getWidth(), currentImages.getHeight(), R, ColorModel.getRGBdefault());
							ByteProcessor gp = new ByteProcessor(currentImages.getWidth(), currentImages.getHeight(), G, ColorModel.getRGBdefault());

							for (int i = 0; i < rois.length; i++){
								rp.setRoi(rois[i]);
								rt.setValue("redbrmean_Chl", i, rp.getStatistics().mean);
								rt.setValue("redbrstdv_Chl", i, rp.getStatistics().stdDev);
								rt.setValue("redbrmode_Chl", i, rp.getStatistics().mode);
								rt.setValue("redbrmin_Chl", i, rp.getStatistics().min);
								rt.setValue("redbrmax_Chl", i, rp.getStatistics().max);
							}
							
							for (int i = 0; i < rois.length; i++){
								gp.setRoi(rois[i]);
								rt.setValue("greenbrmean_Chl", i, gp.getStatistics().mean);
								rt.setValue("greenbrstdv_Chl", i, gp.getStatistics().stdDev);
								rt.setValue("greenbrmode_Chl", i, gp.getStatistics().mode);
								rt.setValue("greenbrmin_Chl", i, gp.getStatistics().min);
								rt.setValue("greenbrmax_Chl", i, gp.getStatistics().max);
							}
							
							cpI.getHSB(H, new byte[width*height], new byte[width*height]);
							
							hp = new ByteProcessor(currentImages.getWidth(), currentImages.getHeight(), H, ColorModel.getRGBdefault());
//							
							for (int i = 0; i < rois.length; i++){
								hp.setRoi(rois[i]);
								rt.setValue("humean_Chl", i, hp.getStatistics().mean);
								rt.setValue("hustdv_Chl", i, hp.getStatistics().stdDev);
								rt.setValue("humode_Chl", i, hp.getStatistics().mode);
								rt.setValue("humin_Chl", i, hp.getStatistics().min);
								rt.setValue("humax_Chl", i, hp.getStatistics().max);
							}
						}
					}
					else{
						for (int i = 0; i < rois.length; i++){
						
						rt.setValue("bright"+j, i, -1);
						rt.setValue("bright"+j+"_stdev", i, -1);	
						rt.setValue("bright"+j+"_mean", i, -1);
						rt.setValue("bright"+j+"_mode", i, -1);
						rt.setValue("bright"+j+"_min", i, -1);
						rt.setValue("bright"+j+"_max", i, -1);
						rt.setValue("ratio flu"+j, i, -1);
							
						if (j == 3)	{
							rt.setValue("redbrmean_Chl", i, -1);
							rt.setValue("redbrstdv_Chl", i, -1);
							rt.setValue("redbrmode_Chl", i, -1);
							rt.setValue("redbrmin_Chl", i, -1);
							rt.setValue("redbrmax_Chl", i, -1);
							
							rt.setValue("greenbrmean_Chl", i, -1);
							rt.setValue("greenbrstdv_Chl", i, -1);
							rt.setValue("greenbrmode_Chl", i, -1);
							rt.setValue("greenbrmin_Chl", i, -1);
							rt.setValue("greenbrmax_Chl", i, -1);
							
							rt.setValue("humean_Chl", i, -1); 
							rt.setValue("hustdev_Chl", i, -1);
							rt.setValue("humode_Chl", i, -1);
							rt.setValue("humin_Chl", i, -1);
							rt.setValue("humax_Chl", i, -1);
							}
						}
						
					}
					
				}
		}
			
		rt.show("Results");
		currentImages.setSlice(1);
//		TextWindow win = ResultsTable.getResultsWindow(); 
//		if (win!=null) win.close(false); 
				
	}
	
	private HashMap<String, Integer> classify(ImagePlus images, RoiManager rm, String string, DataNormalization norm, BasicNetwork network, boolean[] choices, HashMap<String, String> classcolor, HashMap<String, String> classname, HashMap<String, Integer> classresults) {
		Roi[] roi = rm.getRoisAsArray();
		
		
//		int[] results = new int[network.getOutputCount()];
		for (int i1 = 0; i1< rm.getCount(); i1++){
			double[] data = new double[input];
			
			data = getData(input, i1, texFeature, efdFeature ,fluFeature , systemFeature, hsbFeature , glcmFeature);
			
			MLData inputData = norm.buildForNetworkInput(data);
			int coverTypeActual = network.classify(inputData);
			String classified = "NW1-"+coverTypeActual;
			
//			if (rt.getValue("Area", i1)<400){
//				coverTypeActual=0;
//				classified = "NW1-"+0;
//			}
			
			for (int c = 0; c<choices.length;c++){
				if (coverTypeActual == c && choices[c] == true){
					double[] data2 = new double[input2];
					data2 = getData(input2, i1, texFeature2, efdFeature2 ,fluFeature2 , systemFeature2, hsbFeature2 , glcmFeature2);
					MLData inputData2 = norm2.buildForNetworkInput(data2);
					coverTypeActual = network2.classify(inputData2);
					classified = "NW2-"+coverTypeActual;
				}
			}
				
			
			Font font = new Font("TimesNewRoman", Font.BOLD, 18);
			images.getProcessor().setFont(font);
			
			Color color;
			try {
				Field field = Class.forName("java.awt.Color").getField(classcolor.get(classified));
				color = (Color)field.get(null);
			}catch (Exception e) {
				    color = Color.black; // Not defined
			}
			
			
			rm.select(i1);images.getProcessor().setColor(color);images.getProcessor().draw(roi[i1]);
			images.getProcessor().drawString(classname.get(classified), roi[i1].getBounds().x, roi[i1].getBounds().y);

			if (classresults.get(classified) == null){
				classresults.put(classified, 1);
			}
			else{
				classresults.put(classified, classresults.get(classified)+1);
			}

		}
		
 return classresults; 
 }
	

	private double[] getData(int input, int i1, int texFeature, int efdFeature ,long fluFeature , int systemFeature, int hsbFeature , int glcmFeature) {
		double[] data = new double[input];
		int index = 0; 
		
		//Image Moments
		if ((texFeature&FeatureTexture.IM0)!=0){ data[index] =rt.getValue("I0",i1); index++;}//IM0
		if ((texFeature&FeatureTexture.IM1)!=0){ data[index] =rt.getValue("I1",i1); index++;}//IM1
		if ((texFeature&FeatureTexture.IM2)!=0){ data[index] =rt.getValue("I2",i1); index++;}//IM2
		if ((texFeature&FeatureTexture.IM3)!=0){data[index] =rt.getValue("I3",i1); index++;}//IM3
		if ((texFeature&FeatureTexture.IM4)!=0){data[index] =rt.getValue("I4",i1); index++;}//IM4
		if ((texFeature&FeatureTexture.IM5)!=0){data[index] =rt.getValue("I5",i1); index++;}//IM5
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
		if ((efdFeature&FeatureEFD.EFD9)!=0){data[index] =rt.getValue("efd9",i1); index++;}//EFD9
		if ((efdFeature&FeatureEFD.EFD10)!=0){data[index] =rt.getValue("efd10",i1);index++;}//EFD10
		if ((efdFeature&FeatureEFD.EFD11)!=0){data[index] =rt.getValue("efd11",i1);index++;}//EFD11
		if ((efdFeature&FeatureEFD.EFD12)!=0){data[index] =rt.getValue("efd12",i1);index++;}//EFD12
		if ((efdFeature&FeatureEFD.EFD13)!=0){data[index] =rt.getValue("efd13",i1);index++;}//EFD13
		if ((efdFeature&FeatureEFD.EFD14)!=0){ data[index] =rt.getValue("efd14",i1); index++;}//EFD14
		if ((efdFeature&FeatureEFD.EFD15)!=0){data[index] =rt.getValue("efd15",i1); index++;}//EFD15
		if ((efdFeature&FeatureEFD.EFD16)!=0){data[index] =rt.getValue("efd16",i1);index++;}//EFD16
		if ((efdFeature&FeatureEFD.EFD17)!=0){data[index] =rt.getValue("efd17",i1);index++;}//EFD17
		if ((efdFeature&FeatureEFD.EFD18)!=0){data[index] =rt.getValue("efd18",i1);index++;}//EFD18
		if ((efdFeature&FeatureEFD.EFD19)!=0){data[index] =rt.getValue("efd19",i1); index++;}//EFD19
		if ((efdFeature&FeatureEFD.EFD20)!=0){ data[index] =rt.getValue("efd20",i1); index++;}//EFD20
		if ((efdFeature&FeatureEFD.EFD21)!=0){ data[index] =rt.getValue("efd21",i1);index++;}//EFD21
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
		if ((systemFeature&Feature.MINFERET)!=0) {	data[index] =rt.getValue("MinFeret", i1); index++;} //MinFeret
		if ((systemFeature&Feature.AREA)!=0) {data[index] =rt.getValue("Area", i1);index++; } //Area
		if ((systemFeature&Feature.WIDTH)!=0) {data[index] =rt.getValue("Width", i1); index++;}//width
		if ((systemFeature&Feature.HEIGHT)!=0) {data[index] =rt.getValue("Height", i1);index++;}
		if ((systemFeature&Feature.MAJOR)!=0) {data[index] =rt.getValue("Major", i1); index++; }//Major
		if ((systemFeature&Feature.PERI)!=0) {data[index] =rt.getValue("Perim.", i1); index++; }
		if ((systemFeature&Feature.MINOR)!=0) {	data[index] =rt.getValue("Minor", i1); index++;}//Minor
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
			System.out.println("hallo");
			data[index] =rt.getValue("Angular Second Moment_45_1", i1);index++;
			data[index] =rt.getValue("Angular Second Moment_90_1", i1);index++;
			data[index] =rt.getValue("Angular Second Moment_135_1", i1);index++;
			
		}
		
		if ((glcmFeature&FeatureGLCM.ASM2) != 0){
			System.out.println("hallo");
			System.out.println(rt.getValue("Angular Second Moment_45_2", i1));
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
		
		if ((fluFeature&FeatureFlu.PE_MEAN_NORM)!=0){data[index] =rt.getValue("bright1",i1)/255.0;index++;} //meannorm
		if ((fluFeature&FeatureFlu.PE_MEAN)!=0)		{data[index] =rt.getValue("bright1_mean",i1)/255.0;index++;}//mean
		if ((fluFeature&FeatureFlu.PE_MODE)!=0)		{data[index] =rt.getValue("bright1_mode",i1)/255.0;index++;}//mode
		if ((fluFeature&FeatureFlu.PE_MIN)!=0)		{data[index] =rt.getValue("bright1_min",i1)/255.0;index++;} //min
		if ((fluFeature&FeatureFlu.PE_MAX)!=0)		{data[index] =rt.getValue("bright1_max",i1)/255.0;index++;}//max
		if ((fluFeature&FeatureFlu.PE_STDDEV)!=0)	{data[index] =rt.getValue("bright1_stdev",i1)/255.0;index++;} //
		if ((fluFeature&FeatureFlu.PE_FLUPARTS)!=0)	{data[index] =rt.getValue("ratio flu1",i1); index++;}//
		
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
		if ((fluFeature&FeatureFlu.REDFLU_STDDEV)!=0){data[index] =rt.getValue("redbrstdv_Chl",i1)/255.0;index++; }
		
		if ((fluFeature&FeatureFlu.GREENFLU_MEAN)!=0){data[index] =rt.getValue("greenbrmean_Chl",i1)/255.0;index++;} 
		if ((fluFeature&FeatureFlu.GREENFLU_MODE)!=0){data[index] =rt.getValue("greenbrmode_Chl",i1)/255.0;index++; }
		if ((fluFeature&FeatureFlu.GREENFLU_MAX)!=0){data[index] =rt.getValue("greenbrmax_Chl",i1)/255.0; index++;}
		if ((fluFeature&FeatureFlu.GREENFLU_MIN)!=0){data[index] =rt.getValue("greenbrmin_Chl",i1)/255.0; index++;}
		if ((fluFeature&FeatureFlu.GREENFLU_STDDEV)!=0){ data[index] =rt.getValue("greenbrstdv_Chl",i1)/255.0;index++; }
		
		if ((fluFeature&FeatureFlu.HUFLU_MEAN)!=0){data[index] =rt.getValue("humean_Chl",i1)/255.0;index++; }
		if ((fluFeature&FeatureFlu.HUFLU_MODE)!=0){data[index] =rt.getValue("humode_Chl",i1)/255.0;index++; }
		if ((fluFeature&FeatureFlu.HUFLU_MAX)!=0){ data[index] =rt.getValue("humax_Chl",i1)/255.0; index++;}
		if ((fluFeature&FeatureFlu.HUFLU_MIN)!=0){ data[index] =rt.getValue("humin_Chl",i1)/255.0; index++;}
		if ((fluFeature&FeatureFlu.HUFLU_STDDEV)!=0){ data[index] =rt.getValue("hustdv_Chl",i1)/255.0;index++; }
		
		//Color
		
		if ((hsbFeature&FeatureHSB.HU_HIST)!=0){
			
			for(int h = 0 ; h<255; h++){
				data[index] =rt.getValue("hu_hist_"+h,i1); index++;
			}
		}
		if ((hsbFeature&FeatureHSB.HU_MIN)!=0){data[index] =rt.getValue("min hu",i1)/256.0;index++;}//min
		if ((hsbFeature&FeatureHSB.HU_MAX)!=0){data[index] =rt.getValue("max hu",i1)/256.0;index++;}//max
		if ((hsbFeature&FeatureHSB.HU_MEAN)!=0){data[index] =rt.getValue("mean hu",i1)/256.0; index++;} //mean
		if ((hsbFeature&FeatureHSB.HU_MODE)!=0){data[index] =rt.getValue("mode hu",i1)/256.0;index++;} //mode
		if ((hsbFeature&FeatureHSB.HU_STDDEV)!=0){data[index] =rt.getValue("stdev hu",i1)/256.0; index++; }//stdev

		//Saturation
		
		if ((hsbFeature&FeatureHSB.SAT_HIST)!=0){
		
			for(int s = 0 ; s<255; s++){
				data[index] =rt.getValue("sat_hist_"+s,i1); index++;
			}
		}
		
		if ((hsbFeature&FeatureHSB.SAT_MIN)!=0){data[index] =rt.getValue("min bright",i1)/256.0;index++;}//min
		if ((hsbFeature&FeatureHSB.SAT_MAX)!=0){data[index] =rt.getValue("max bright",i1)/256.0;index++;}//max
		if ((hsbFeature&FeatureHSB.SAT_MEAN)!=0){data[index] =rt.getValue("mean bright",i1)/256.0;index++;}//mean
		if ((hsbFeature&FeatureHSB.SAT_MODE)!=0){ data[index] =rt.getValue("mode bright",i1)/256.0;index++;}//mode
		if ((hsbFeature&FeatureHSB.SAT_STDDEV)!=0){ data[index] =rt.getValue("stdev bright",i1)/256.0;index++;}//stdev
		
		//Brightness
		
		if ((hsbFeature&FeatureHSB.BRIGHT_HIST)!=0){
			
			for(int b = 0 ; b<255; b++){
				data[index] =rt.getValue("bright_hist_"+b,i1);index++;
			}
		}
		if ((hsbFeature&FeatureHSB.BRIGHT_MIN)!=0){data[index] =rt.getValue("min bright",i1)/256.0;index++;}//min
		if ((hsbFeature&FeatureHSB.BRIGHT_MAX)!=0){data[index] =rt.getValue("max bright",i1)/256.0;index++;}//max
		if ((hsbFeature&FeatureHSB.BRIGHT_MEAN)!=0){data[index] =rt.getValue("mean bright",i1)/256.0;index++;} //mean
		if ((hsbFeature&FeatureHSB.BRIGHT_MODE)!=0){data[index] =rt.getValue("mode bright",i1)/256.0;index++;}//mode
		if ((hsbFeature&FeatureHSB.BRIGHT_STDDEV)!=0){data[index] =rt.getValue("stdev bright",i1)/256.0;index++;}//stdev
		
		return data;
	}
	
	

}	
	