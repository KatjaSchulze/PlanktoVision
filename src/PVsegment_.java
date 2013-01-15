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
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.ColorModel;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Vector;

import calc.Directionality;
import calc.EllipticFD;
import calc.ImageMoments_;
import calc.LBP_Texture;
import calc.ReadImages;
import calc.RegionGrowing;
import calc.Symmetry;


import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
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
import ij.text.TextWindow;

public class PVsegment_ implements PlugIn {

	private ResultsTable rt;
	private boolean[] flucount; 
	private String workDir;
	
	public void run(String arg) {

		//check for working directory
		//===================================================================================================================================
		if (Prefs.getString(".dir.plankton")!=null){
			workDir = Prefs.getString(".dir.plankton");
		}
		else{
			IJ.showMessage("Please define your working directory under Plugins>PlanktoVision>PVsettings");
			return;
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
			
			rm.runCommand("Show All");
			//***********************************************************************************************************************************	
			
			//feature calculation & classification
			//===================================================================================================================================
			getFeature(images, rm, imageList[j]);
			
			rm.runCommand("Show None");
			IJ.wait(2000);
			
			images.close();
			
		    rm.close();
			rt.reset();
		}
		
	}

	//***********************************************************************************************************************************	
	
	
	
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
		double meanbright = hsb2.getStack().getProcessor(3).getStatistics().mean;
		double meansat =  hsb2.getStack().getProcessor(2).getStatistics().mean;
		
		BackgroundSubtracter backsub = new BackgroundSubtracter();
		ImageProcessor s1 = hsb2.getStack().getProcessor(2);
		backsub.rollingBallBackground(s1, 50, false, false, true, true, true);
		s1.threshold(25);
		IJ.run(hsb2, "RGB Color", "");
		
		ColorProcessor cp = (ColorProcessor) hsb2.getProcessor();
		ImagePlus hu_norm = new ImagePlus("hu norm", cp.getHSBStack().getProcessor(1));
		
		
//		Analyze Rois
	//*******************************************************************************************
		
		Roi[] rois =  rm.getRoisAsArray();

		Analyzer.setMeasurements(2091775);
		
		if (rois.length>0){
		
			for (int i = 0; i < rois.length; i++){
				
				currentImages.setSlice(1);
				
				currentImages.setRoi(rois[i]);
				rois[i] = currentImages.getRoi();
				roi = new PolygonRoi(rois[i].getPolygon(), rois[i].getType());
				Roi roi2 = new PolygonRoi(rois[i].getPolygon(), rois[i].getType());
				
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
					
				// calculate colored parts
				//**********************************************************************************************************************
				int count = 0 ; 
				double mean = 0 ; 
				Vector<Integer> stdevV = new Vector<Integer>();
				hu_norm.setRoi(rois[i]);
					
				int xr = rois[i].getBounds().x;
				int yr = rois[i].getBounds().y;
				for (int x = xr ; x< xr + rois[i].getBounds().getWidth();x++){
					for (int y = yr; y<yr + rois[i].getBounds().getHeight(); y++){
						int pixel = hu_norm.getProcessor().getPixel(x, y);
						if (pixel > 0 && rois[i].contains(x, y)){
							
							count++;
							mean = mean + pixel;
							stdevV.add(pixel);
						}
					}
				}
					
				if (count != 0){
					rt.setValue("colored parts", i, count);
				}
				else{
					rt.setValue("colored parts", i, 0);
				}
									
				//brightness (mean, stdev, histogram...)
				//**********************************************************************************************************************
				ImagePlus hsb = new ImagePlus("hsb", impClip.getProcessor());
				IJ.run(hsb, "HSB Stack", "");
				ImageProcessor b = hsb.getStack().getProcessor(3);
				b.setRoi(roi);
				rt.setValue("mean bright", i, b.getStatistics().mean);
				rt.setValue("stdev bright", i, b.getStatistics().stdDev);
				rt.setValue("mode bright", i, b.getStatistics().mode);
				rt.setValue("min bright", i, b.getStatistics().min);
				rt.setValue("max bright", i, b.getStatistics().max);
					
				int[] hb = b.getHistogram();
				double countb = b.getStatistics().area;
				for(int i1 = 0 ; i1<255; i1++){
					rt.setValue("bright_hist_"+i1, i, (double)hb[i1]/countb);
				}
				
				//**********************************************************************************************************************
					
				//Saturation (mean, stdev, histogram...)
				//**********************************************************************************************************************
				ImageProcessor sat = hsb.getStack().getProcessor(2);
				sat.setRoi(roi);
				rt.setValue("mean sat", i, sat.getStatistics().mean);
				rt.setValue("stdev sat", i, sat.getStatistics().stdDev);
				rt.setValue("mode sat", i, sat.getStatistics().mode);
				rt.setValue("min sat", i, sat.getStatistics().min);
				rt.setValue("max sat", i, sat.getStatistics().max);
					
				int[] hs = sat.getHistogram();
				double counts = sat.getStatistics().area;
				for(int i1 = 0 ; i1<255; i1++){
					rt.setValue("sat_hist_"+i1, i, (double)hs[i1]/counts);
				}
					
				// get hu histogram
				//************************************************************************************************************************
					
				ImageProcessor hu = hsb.getStack().getProcessor(1);
				hu.setRoi(roi);
				rt.setValue("mean hu", i, hu.getStatistics().mean);
				rt.setValue("stdev hu", i, hu.getStatistics().stdDev);
				rt.setValue("mode hu", i, hu.getStatistics().mode);
				rt.setValue("min hu", i, hu.getStatistics().min);
				rt.setValue("max hu", i, hu.getStatistics().max);
					
				int[] hhu = hu.getHistogram();
				double counthu = hu.getStatistics().area;
				for(int i1 = 0 ; i1<255; i1++){
					rt.setValue("hu_hist_"+i1, i, (double)hhu[i1]/counthu);
				}
				
				//**********************************************************************************************************************
					
				//GLMC
				//**********************************************************************************************************************
				// rotate brightness Image for glcm analysis
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
				IJ.run(bright, "GLCM Texture ", "enter=1 select=[0 degrees] angular contrast correlation inverse entropy");
				IJ.run(bright, "GLCM Texture ", "enter=2 select=[0 degrees] angular contrast correlation inverse entropy");
				IJ.run(bright, "GLCM Texture ", "enter=3 select=[0 degrees] angular contrast correlation inverse entropy");
				IJ.run(bright, "GLCM Texture ", "enter=4 select=[0 degrees] angular contrast correlation inverse entropy");
				IJ.run(bright, "GLCM Texture ", "enter=5 select=[0 degrees] angular contrast correlation inverse entropy");
//				IJ.run(bright, "GLCM Texture ", "enter=6 select=[0 degrees] angular contrast correlation inverse entropy");
//				IJ.run(bright, "GLCM Texture ", "enter=7 select=[0 degrees] angular contrast correlation inverse entropy");
//				IJ.run(bright, "GLCM Texture ", "enter=8 select=[0 degrees] angular contrast correlation inverse entropy");
//				IJ.run(bright, "GLCM Texture ", "enter=9 select=[0 degrees] angular contrast correlation inverse entropy");
//				IJ.run(bright, "GLCM Texture ", "enter=10 select=[0 degrees] angular contrast correlation inverse entropy");
				rt.show("Results");
				//**********************************************************************************************************************
			
				// Directionality Histogram
				//**********************************************************************************************************************
				ImagePlus huimp = new ImagePlus("HU", hsb.getStack().getProcessor(3));
				huimp.getProcessor().setColor(255);
				huimp.getProcessor().fillOutside(roi);
				Directionality da = new Directionality();
				da.run("nbins=20, start=0, method=gradient", huimp);
				da.displayResultsTable();
				//**********************************************************************************************************************
				
				// Fourier Analysis of Contour
				//**********************************************************************************************************************
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
				//**********************************************************************************************************************
					
				// get binary mask of Roi
				ImagePlus mask = new ImagePlus("mask", bright.getMask());
				mask.getProcessor().invert();
					
				// Symmetry Measurements;
				//**********************************************************************************************************************
				Symmetry sym = new Symmetry(mask, roi2);
				sym.run();
				rt.setValue("Rot2Sym", i, sym.getRot2Sym());
				rt.setValue("BThresh", i, sym.belowThreshold() );
					
				for (int j= 0 ; j <sym.getReflSym().size(); j++){
					rt.setValue("ReflSym"+j, i,sym.getReflSym().get(j)+0.01); // +0.01 um von 0 WErten unterscheiden zu können!!!!!
				}
				//**********************************************************************************************************************
					
				// Optical Density
				//**********************************************************************************************************************
				double OD = rt.getValue("mean bright",i) / meanbright;
				double OD_sat = (rt.getValue("mean sat",i)-meansat) / (255-meansat);
				rt.setValue("OD_bright", i, OD );
				rt.setValue("OD_sat", i, OD_sat );
				rt.setValue("class", i, 1.0);
				//**********************************************************************************************************************
					
				// Local binary pattern
				//**********************************************************************************************************************
				ImagePlus bright2 = new ImagePlus("lbp", b);
				bright2.setRoi(roi);
				LBP_Texture lbp = new LBP_Texture(bright2);
					
				boolean a =  lbp.run(false, 1, 8);
					
				if (a == true){
					double[] feature = lbp.getFeature(); 
					for (int f = 0; f< feature.length; f++){
						rt.setValue("lbp"+f+"_"+1+""+8, i, feature[f]);
					}
				}else{
					for (int f = 0; f< 38; f++){
						rt.setValue("lbp"+f+"_"+1+""+8, i, -1);
					}
				}

				lbp = new LBP_Texture(bright2);
				a =lbp.run(false, 2, 8);
				if (a == true){
					double[] feature = lbp.getFeature(); 
					for (int f = 0; f< feature.length; f++){
						rt.setValue("lbp"+f+"_"+2+""+8, i, feature[f]);
					}
				}else{
					for (int f = 0; f< 38; f++){
						rt.setValue("lbp"+f+"_"+2+""+8, i, -1);
					}
				}

				lbp = new LBP_Texture(bright2);
				a = lbp.run(false, 3, 8);
					
				if (a == true){
					double[] feature = lbp.getFeature(); 
					for (int f = 0; f< feature.length; f++){
						rt.setValue("lbp"+f+"_"+3+""+8, i, feature[f]);
					}
				}else{
					for (int f = 0; f< 38; f++){
						rt.setValue("lbp"+f+"_"+3+""+8, i, -1);
					}
				}
					
				lbp = new LBP_Texture(bright2);
				a = lbp.run(false, 4, 8);
					
				if (a == true){
					double[] feature = lbp.getFeature(); 
					for (int f = 0; f< feature.length; f++){
						rt.setValue("lbp"+f+"_"+4+""+8, i, feature[f]);
					}
				}else{
					for (int f = 0; f< 38; f++){
						rt.setValue("lbp"+f+"_"+4+""+8, i, -1);
					}
				}
					
				lbp = new LBP_Texture(bright2);
				a = lbp.run(false, 2, 16);
				if (a == true){
					double[] feature = lbp.getFeature(); 
					for (int f = 0; f< feature.length; f++){
						rt.setValue("lbp"+f+"_"+2+""+16, i, feature[f]);
					}
				}else{
					for (int f = 0; f<138; f++){
						rt.setValue("lbp"+f+"_"+2+""+16, i, -1);
					}
				}
					
				lbp = new LBP_Texture(bright2);
				a = lbp.run(false, 3, 16);
				if (a == true){
					double[] feature = lbp.getFeature(); 
					for (int f = 0; f< feature.length; f++){
						rt.setValue("lbp"+f+"_"+3+""+16, i, feature[f]);
					}
				}else{
					for (int f = 0; f<138; f++){
						rt.setValue("lbp"+f+"_"+3+""+16, i, -1);
					}
				}
					
				//**********************************************************************************************************************
				// ImageMoments
				//**********************************************************************************************************************
				ImageMoments_ mom = new ImageMoments_(); 
				mom.setup("", bright2);
				mom.run(bright.getProcessor());
				double[] inv = mom.getInvariantMoments();
//					double[][] sm = mom.getScaledMoments();
//					double[][] cm = mom.getCentralMoments();
				for (int m = 0; m < inv.length; m++){ 
					rt.setValue("I"+m, i, inv[m]);
				}
				//**********************************************************************************************************************
					
				//Add resolution
				//**********************************************************************************************************************
				rt.setValue("resolution", i, resolution);
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
		String name = null;
		ImagePlus threshold = NewImage.createByteImage("threshold", currentImages.getWidth(), currentImages.getHeight(),1, NewImage.FILL_BLACK);
		double   ratio, back, TFI;  
		rois =  rm.getRoisAsArray();
				
		for (int j = 1; j <flucount.length; j++){
			if (flucount[j] == true){
						
				name = "bright"+j;
				currentImages.setSlice(j+1);
				int width = currentImages.getWidth();
				int height = currentImages.getHeight(); 
				ColorProcessor cpI = (ColorProcessor)currentImages.getProcessor();
						
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
//				threshold.show();
//				threshold.getWindow().setLocation(1000, 0);
						
				threshold.killRoi();
				ImageProcessor ipt = threshold.getProcessor();
					
						
				//threshold image
				if (j == 3){
					AutoThresholder adjust = new AutoThresholder();
					int globalThreshold = adjust.getThreshold("Default",ipt.getHistogram());
					if (globalThreshold < 35) ipt.threshold(globalThreshold);
					else ipt.threshold(35);
//					threshold.updateAndDraw();
				}
				else{
					ipt.threshold(45);
//					threshold.updateAndDraw();	
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
//				threshold.updateAndDraw();
											
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
//		threshold.changes = false;
//		threshold.close();
			
		// save results
		try {
			for (int i = 0; i<rt.getCounter(); i++){
				String path =  workDir+"/results/"+filename+"_"+i+".xls";
				PrintWriter pw = null;
				FileOutputStream fos = new FileOutputStream(path);
				BufferedOutputStream bos = new BufferedOutputStream(fos);
				pw = new PrintWriter(bos);
				pw.println(rt.getColumnHeadings());
				pw.println(rt.getRowAsString(i));
				pw.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		currentImages.setSlice(1);
				
		// Save Roi Picture
		//**********************************************************************************************************************
		int count = 1; 
		for (int f = 1; f<flucount.length;f++){
			if (flucount[f]==true){count++;}
		}
		
		
		Roi[] r = rm.getRoisAsArray();
		for (int i = 0 ; i < rm.getCount(); i++){
			roi = new PolygonRoi(rois[i].getPolygon(), rois[i].getType());
			roi.setLocation(0, 0);
			Roi box = new Roi(r[i].getBounds().x, r[i].getBounds().y, r[i].getBounds().width, r[i].getBounds().height);
			currentImages.setSlice(1);
			currentImages.setRoi(box);
			int width0 = currentImages.getRoi().getBounds().width;
			int height0 = currentImages.getRoi().getBounds().height;
					 
			IJ.run(currentImages, "Copy", "");
			IJ.run("Internal Clipboard", "");
			WindowManager.getImage("Clipboard").getProcessor().draw(roi);
			ImagePlus imp = IJ.createImage("Roi3Kanal", "RGB White" ,  width0, height0*(count), 1);
			imp.getProcessor().copyBits(WindowManager.getImage("Clipboard").getProcessor(), 0, 0, Blitter.COPY);
			imp.updateAndDraw();
			WindowManager.getImage("Clipboard").changes=false; 
			WindowManager.getImage("Clipboard").close(); 
			count=1;
			
			if (flucount[1] == true){
				currentImages.setSlice(2);
				IJ.run(currentImages, "Copy", "");
				IJ.run("Internal Clipboard", "");
				WindowManager.getImage("Clipboard").getProcessor().setColor(Color.white);
				WindowManager.getImage("Clipboard").getProcessor().drawString("PC", 0, 15);
				imp.getProcessor().copyBits(WindowManager.getImage("Clipboard").getProcessor(),0, height0*count, Blitter.COPY);
				imp.getProcessor().drawString("PC", 0, 0);
				imp.updateAndDraw();
				WindowManager.getImage("Clipboard").changes=false; 
				WindowManager.getImage("Clipboard").close(); 
				count++;
			}

			if (flucount[2] == true){
				currentImages.setSlice(3);
				IJ.run(currentImages, "Copy", "");
				IJ.run("Internal Clipboard", "");
				WindowManager.getImage("Clipboard").getProcessor().setColor(Color.white);
				WindowManager.getImage("Clipboard").getProcessor().drawString("PE", 0, 15);
				imp.getProcessor().copyBits(WindowManager.getImage("Clipboard").getProcessor(), 0, height0*count, Blitter.COPY);
				imp.updateAndDraw();
				WindowManager.getImage("Clipboard").changes=false; 
				WindowManager.getImage("Clipboard").close(); 
				count++;
			}	
			
			if (flucount[3] == true){
				currentImages.setSlice(4);
				IJ.run(currentImages, "Copy", "");
				IJ.run("Internal Clipboard", "");
				WindowManager.getImage("Clipboard").getProcessor().setColor(Color.white);
				WindowManager.getImage("Clipboard").getProcessor().drawString("CHL", 0, 15);
				imp.getProcessor().copyBits(WindowManager.getImage("Clipboard").getProcessor(), 0, height0*count, Blitter.COPY);
				imp.updateAndDraw();
				WindowManager.getImage("Clipboard").changes=false; 
				WindowManager.getImage("Clipboard").close(); 
				count++;
			}			
			String path = workDir+"/pictures/";
			String filepath = path+filename+"_"+i+".tif";
					
			IJ.saveAs(imp, "Tiff", filepath);
					
		}
		
		rt.show("Results");
		rt.reset();
		TextWindow win = ResultsTable.getResultsWindow(); 
		if (win!=null) win.close(false); 
	}	
}	
	