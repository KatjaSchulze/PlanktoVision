
				
//=====================================================


//===========imports===================================
import ij.*;
import ij.gui.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;

import ij.plugin.PlugIn;
import ij.text.*;
import ij.measure.ResultsTable;

//===========source====================================
public class GLCM_Texture_ implements PlugInFilter {
	static int step = 1;
	static String selectedStep = "0 degrees";
	static boolean doIcalculateASM = true;
	static boolean doIcalculateContrast = true;
	static boolean doIcalculateCorrelation = true;
	static boolean doIcalculateIDM = true;
	static boolean doIcalculateEntropy = true;
	ResultsTable rt = ResultsTable.getResultsTable();
	ImagePlus imp; 

	public int setup(String arg, ImagePlus imp) {
		if (imp!=null && !showDialog()) return DONE;
//		rt.reset();
		this.imp = imp;
		return DOES_8G+DOES_STACKS+SUPPORTS_MASKING;
	}


public void run(ImageProcessor ip) {
// This part get al the pixel values into the pixel [ ] array via the Image Processor

	  byte [] pixels =(byte []) ip.getPixels();
		int width = ip.getWidth();
		Rectangle r = ip.getRoi();

//}


		
// The variable a holds the value of the pixel where the Image Processor is sitting its attention
// The varialbe b holds the value of the pixel which is the neighbor to the  pixel where the Image Processor is sitting its attention

		int a;
		int b;
		double pixelCounter=0;
		

//====================================================================================================
// This part computes the Gray Level Correlation Matrix based in the step selected by the user
	
	Roi roi = imp.getRoi();
	int offset, i;
	double [] [] glcm= new double [257][257];

	int degree = 1;
//	rt.incrementCounter();
	switch(degree){
		case 1:
//			System.out.println("0°");
			for (int y=r.y; y<(r.y+r.height); y++) 	{
				offset = y*width;
				for (int x=r.x; x<(r.x+r.width); x++)	 {
						if (ip.getPixel(x+step, y) != 0 && ip.getPixel(x, y) != 0){
							i = offset + x;
							
							a = 0xff & pixels[i];
							b = 0xff &	(ip.getPixel (x+step, y));		
							
							glcm [a][b] +=1;
							glcm [b][a] +=1;
							
//							System.out.println(a+" "+b+" "+glcm [a][b]+" "+glcm [b][a]);
//							pixelCounter +=2;
							
//							pixels[i] = 0;
//							ip.putPixel(x+step, y, 255);
//							imp.updateAndDraw();
//							IJ.wait(10);

						}
					}	
			}
			
			calculate(glcm, pixelCounter, "0", step);
			glcm= new double [257][257];
			pixelCounter = 0;
			degree++;
			
		case 2:
//			System.out.println("45°");
			for (int y=r.y; y<(r.y+r.height); y++) 	{
				offset = y*width;
				for (int x=r.x; x<(r.x+r.width); x++)	 {
					if (ip.getPixel(x+step, y+step) != 0 && ip.getPixel(x, y) != 0){
						i = offset + x;
						
						a = 0xff & pixels[i];
						b = 0xff &	(ip.getPixel (x+step, y+step));					
						glcm [a][b] +=1;
						glcm [b][a] +=1;
						pixelCounter +=2;
						
//						pixels[i] = 0;
//						ip.putPixel(x+step, y+step, 255);
//						imp.updateAndDraw();
//						IJ.wait(10);
					}
				}	
			}
			
			calculate(glcm, pixelCounter, "45", step);
			glcm= new double [257][257];
			pixelCounter = 0;
			degree++;
			
		case 3:
//			System.out.println("90°");
			for (int y=r.y; y<(r.y+r.height); y++) 	{
				offset = y*width;
				for (int x=r.x; x<(r.x+r.width); x++)	 {
					if (ip.getPixel(x, y+step) != 0 && ip.getPixel(x, y) != 0){
						i = offset + x;
						
						a = 0xff & pixels[i];
						b = 0xff &	(ip.getPixel (x, y+step));					
						glcm [a][b] +=1;
						glcm [b][a] +=1;
						pixelCounter +=2;
						
//						pixels[i] = 0;
//						ip.putPixel(x, y+step, 255);
//						imp.updateAndDraw();
//						IJ.wait(10);
					}
				}
			}
			calculate(glcm, pixelCounter, "90", step);
			glcm= new double [257][257];
			pixelCounter = 0; 
			degree++;
			
		case 4:
//			System.out.println("135°");
			for (int y=r.y; y<(r.y+r.height); y++) 	{
				offset = y*width;
				for (int x=r.x; x<(r.x+r.width); x++)	 {
					if (ip.getPixel(x-step, y+step) != 0 && ip.getPixel(x, y) != 0){
						i = offset + x;
						a = 0xff & pixels[i];
						b = 0xff &	(ip.getPixel (x-step, y+step));					
						glcm [a][b] +=1;
						glcm [b][a] +=1;
						pixelCounter +=2;
									
//						pixels[i] = 10;
//						ip.putPixel(x-step, y+step, 255);
//						imp.updateAndDraw();
//						IJ.wait(10);
						}
					}
				}
			
			calculate(glcm, pixelCounter, "135", step);
			pixelCounter = 0;
			glcm= new double [257][257];
			degree++;
			
	}
	





//===============================================================================================
//		TextWindow tw = new TextWindow("Haralick's texture features   ", "", 400, 200);
//		tw.append("  ");
//		tw.append ("Total pixels analyzed  "+ pixelCounter);
//		tw.append ( "Selected Step   " + selectedStep);
//		tw.append ("Size of the step   "+ step);
//		tw.append ("3 a la quinta   "+ Math.pow(3,5));
    
   }

	private void calculate(double[][] glcm, double pixelCounter, String degree, int step) {
		//=====================================================================================================

		// This part divides each member of the glcm matrix by the number of pixels. The number of pixels was stored in the pixelCounter variable
		// The number of pixels is used as a normalizing constant
		for (int a=0;  a<257; a++)  {

							for (int b=0; b<257;b++) {
													glcm[a][b]=(glcm[a][b])/(pixelCounter);
													
											}
						}
		

		
		int row = rt.getCounter()-1;	
		//=====================================================================================================
		//This part calculates the angular second moment; the value is stored in asm

		if (doIcalculateASM==true){
			double asm=0.0;
			for (int a=0;  a<257; a++)  {
				for (int b=0; b<257;b++) {
					asm=asm+ (glcm[a][b]*glcm[a][b]);
				}
			}
			
			if (!Double.isNaN(asm)){
				rt.setValue("Angular Second Moment_"+degree+"_"+step, row, asm);
			}
			else{
				rt.setValue("Angular Second Moment_"+degree+"_"+step, row, Double.MIN_VALUE);
			}
		}

		//=====================================================================================================
		//This part calculates the contrast; the value is stored in contrast


		
		
		if (doIcalculateContrast==true){
			double contrast=0.0;
			for (int a=0;  a<257; a++)  {
				for (int b=0; b<257;b++) {
					contrast=contrast+ (a-b)*(a-b)*(glcm[a][b]);
				}
			}
			
			if (!Double.isNaN(contrast)){
				rt.setValue("Contrast_"+degree+"_"+step, row, contrast);
			}
			else{
				rt.setValue("Contrast_"+degree+"_"+step, row, Double.MIN_VALUE);
			}
			
			
			
		}

		//=====================================================================================================
		//This part calculates the correlation; the value is stored in correlation
		//px []  and py [] are arrays to calculate the correlation
		//meanx and meany are variables  to calculate the correlation
		//stdevx and stdevy are variables to calculate the correlation

		if (doIcalculateCorrelation==true){

//			glcm= new double [4][4];
//			double [][]glcm2 = {  
//					  { 0.166 , 0.083, 	0.042, 	0 },
//				      { 0.083 , 0.166, 	0	, 	0 }, 
//				      { 0.042 , 0,		0.249,	0.042 },
//				      { 0,		0,		0.042, 	0.083 }
//				    };
			
			
			//First step in the calculations will be to calculate px [] and py []
			double correlation=0.0;
			double px=0;
			double py=0;
			double meanx=0.0;
			double meany=0.0;
			double stdevx=0.0;
			double stdevy=0.0;

			for (int a=0; a<257;a++){
				for (int b=0; b <257; b++){
						px=px+a*glcm [a][b];  
                        py=py+b*glcm [a][b];
						} 
			}



			//Now calculate the standard deviations
			for (int a=0; a<257; a++){
				for (int b=0; b <257; b++){
						stdevx=stdevx+(a-px)*(a-px)*glcm [a][b];
						stdevy=stdevy+(b-py)*(b-py)*glcm [a][b];
						}
			}


			//Now finally calculate the correlation parameter

			for (int a=0;  a<257; a++)  {
				for (int b=0; b<257;b++) {
					correlation=correlation+( (a-px)*(b-py)*glcm [a][b]/(stdevx*stdevy)) ;
				}
			}

			if (!Double.isNaN(correlation)){
				rt.setValue("Correlation_"+degree+"_"+step, row, correlation);
			}
			else{
				rt.setValue("Correlation_"+degree+"_"+step, row, Double.MIN_VALUE);
			}
			
		}

		//===============================================================================================
		//This part calculates the inverse difference moment

		if (doIcalculateIDM==true){
			double IDM=0.0;
			for (int a=0;  a<257; a++)  {
				for (int b=0; b<257;b++) {
								IDM=IDM+ (glcm[a][b]/(1+(a-b)*(a-b)))  ;
				}
			}
			
			if (!Double.isNaN(IDM)){
				rt.setValue("Inverse Difference Moment_"+degree+"_"+step, row, IDM);
			}
			else{
				rt.setValue("Inverse Difference Moment_"+degree+"_"+step, row, Double.MIN_VALUE);
			}
		}

		//===============================================================================================
		//This part calculates the entropy

		if (doIcalculateEntropy==true){
			double entropy=0.0;
			for (int a=0;  a<257; a++)  {
				for (int b=0; b<257;b++) {
					if (glcm[a][b]==0) {}
					else {entropy=entropy-(glcm[a][b]*(Math.log(glcm[a][b])));}
				}
			}
			
			if (!Double.isNaN(entropy)){
				rt.setValue("Entropy_"+degree+"_"+step, row, entropy);
			}
			else{
				rt.setValue("Entropy_"+degree+"_"+step, row, Double.MIN_VALUE);
			}
			
			
			
			
		}



//
//		double suma=0.0;
//		for (int a=0;  a<257; a++)  {
//			for (int b=0; b<257;b++) {
//				suma= suma + glcm[a][b];
//			}
//		}
//		rt.setValue("Sum of all GLCM elements_"+degree+"_"+step, row, suma);
		
	
	}


	// This part is the implementation of the Dialog box (called gd here)
	boolean showDialog() {
     		GenericDialog gd = new GenericDialog("Textural features based in GLCM. Version 0.4");
    		gd.addMessage("This plug-in calculates textural features\n" +" based in Gray Level Correlation Matrices.");
	
		gd.addNumericField ("Enter the size of the step in pixels",  step, 0);
	
		String [] stepOptions={"0 degrees", "45 degrees", "90 degrees", "135 degrees"};
		gd.addChoice("Select the direction of the step", stepOptions, selectedStep);

		gd.addMessage("Check in the following boxes\n" +"for the parameters you want to compute \n"+"and click OK.");   
		gd.addCheckbox("Angular Second Moment  ", doIcalculateASM);
   		gd.addCheckbox("Contrast  ", doIcalculateContrast);
    		gd.addCheckbox ("Correlation  ", doIcalculateCorrelation);
    		gd.addCheckbox ("Inverse Difference Moment  ", doIcalculateIDM);
    		gd.addCheckbox ("Entropy   ", doIcalculateEntropy);
    	
		gd.showDialog();
		if (gd.wasCanceled())
             		 return false;
	
		step=(int) gd.getNextNumber();
		selectedStep=gd.getNextChoice ();
		doIcalculateASM=gd.getNextBoolean ();
		doIcalculateContrast=gd.getNextBoolean ();
		doIcalculateCorrelation=gd.getNextBoolean();
		doIcalculateIDM=gd.getNextBoolean();
		doIcalculateEntropy=gd.getNextBoolean();

		return true;
	}

}