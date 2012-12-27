package calc;
import java.awt.event.KeyEvent;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_2D;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.process.Blitter;
import ij.process.ImageProcessor;



public class LBP_Texture  {

	Roi roi; 
	ImagePlus imp;
	private double[] feature;
	
	public LBP_Texture( ImagePlus imp) {
		
		this.imp = imp;
		roi = imp.getRoi();
		IJ.run(imp, "Crop", "");
		imp.setRoi(roi);
//		return DOES_8G+ROI_REQUIRED;
	}
	
	public boolean run(boolean show, int radius, int neighbors) {
		// TODO Auto-generated method stub
		ImageProcessor ip = imp.getProcessor();
//		int radius = 2;
//		int neighbors = 16;
		
		double[][] spoints = new double[2][neighbors];
		
		//Angle step.++ Determine the dimensions of the input image.
		double a = 2*Math.PI/neighbors;
		
		double minx = Double.MAX_VALUE;
		double maxx = Double.MIN_VALUE;
		double miny = Double.MAX_VALUE;
		double maxy = Double.MIN_VALUE;
		
		for (int i = 0; i<neighbors; i++){
			spoints[0][i] = this.round(-radius*Math.sin((i)*a),4);
			
			if (spoints[0][i]>maxy)maxy = spoints[0][i];
			if (spoints[0][i]<miny)miny = spoints[0][i];
			
			spoints[1][i] = this.round( radius*Math.cos((i)*a),4);
			if (spoints[1][i]>maxx)maxx = spoints[1][i];
			if (spoints[1][i]<minx)minx = spoints[1][i];
			
		}
		
		int ysize = ip.getHeight();
		int xsize = ip.getWidth();
		
		
		//Block size, each LBP code is computed within a block of size bsizey*bsizex
		double bsizey = Math.ceil(Math.max(maxy,0)) - Math.floor(Math.min(miny,0))+1;
		double bsizex = Math.ceil(Math.max(maxx,0)) - Math.floor(Math.min(minx,0))+1;
		
		// Coordinates of origin (0,0) in the block
		int origy = (int) (1 - Math.floor(Math.min(miny,0))) -1;
		int origx = (int) (1 - Math.floor(Math.min(minx,0))) -1 ;
		
		if(xsize < bsizex || ysize < bsizey){
//			IJ.error("Too small input image. Should be at least (2*radius+1) x (2*radius+1)");
			return false;
		}
		
		// Calculate dx and dy;
		double dx = xsize - bsizex + 1;
		double dy = ysize - bsizey + 1;
		
		//Fill the center pixel matrix C.
		ImagePlus imp_center = NewImage.createByteImage("threshold", (int)dx, (int)dy,1, NewImage.FILL_BLACK);
		
		for (int i = origx ; i < origx+dx; i++){
			for (int j = origy ; j < origy+dy; j++){
//				if (roi.contains(i, j))	imp_center.getProcessor().putPixel( i-origx, j-origy, ip.getPixel(i, j));
				imp_center.getProcessor().putPixel( i-origx, j-origy, ip.getPixel(i, j));
			}
		}
		if (show ==true)imp_center.show();

		int bins = (int) Math.pow(2, neighbors);
		
		ImagePlus impResult = NewImage.createByteImage("Results", (int)dx, (int)dy,1, NewImage.FILL_BLACK);
		int[][] result = new int[(int)dx][(int)dy]; 
		
		for(int i = 0; i < neighbors; i++){
			double y = spoints[0][i] + origx; 
			double x = spoints[1][i] + origy;
			
			//Calculate floors, ceils and rounds for the x and y.
			int fy = (int) Math.floor(y); int cy = (int) Math.ceil(y); int ry = (int) Math.round(y);
			int fx = (int) Math.floor(x); int cx = (int) Math.ceil(x); int rx = (int) Math.round(x);
			
//			ImagePlus impN = NewImage.createByteImage("N", (int)dx, (int)dy,1, NewImage.FILL_BLACK);
			double[][] N = new double[(int)dx][(int)dy];
			
			
			if ((Math.abs(x - rx) < 1e-6) && (Math.abs(y - ry) < 1e-6)){
			    //Interpolation is not needed, use original datatypes
				
				for (int xn = rx ; xn < rx+dx; xn++){
					for (int yn = ry ; yn < ry+dy; yn++){
//						impN.getProcessor().putPixel( xn-rx, yn-ry, ip.getPixel(xn, yn));
						N[xn-rx][yn-ry] =  ip.getPixel(xn, yn);
					}
				}
				
				for (int xn = 0; xn<imp_center.getWidth(); xn++){
					for (int yn = 0; yn<imp_center.getHeight(); yn++){
//						if (imp_center.getProcessor().getPixel(xn, yn) <= impN.getProcessor().getPixel(xn, yn)){
						if (imp_center.getProcessor().getPixel(xn, yn) <= N[xn][yn]){
							N[xn][yn] = 1;
						}
						else{
//							impN.getProcessor().putPixel(xn, yn, 0);
							N[xn][yn] = 0;
						}
					}
				}

			}
			else{
				// Interpolation needed
			    double ty = y - fy;
			    double tx = x - fx;
				
			    //Calculate the interpolation weights.
			    double w1 = (1 - tx) * (1 - ty);
			    double w2 =      tx  * (1 - ty);
			    double w3 = (1 - tx) *      ty ;
			    double w4 =      tx  *      ty ;
			    
			    //Compute interpolated pixel values
				
				for (int xn = fx ; xn < fx+dx; xn++){
					for (int yn = fy ; yn < fy+dy; yn++){
//						impN.getProcessor().putPixel( xn-fx, yn-fy, (int)(w1*ip.getPixel(xn, yn)));
						N[xn-fx][yn-fy] =  (int)(w1*ip.getPixel(xn, yn));
					}
				}
				
				for (int xn = cx ; xn < cx+dx; xn++){
					for (int yn = fy ; yn < fy+dy; yn++){
//						int pixel = (int) (ip.getPixel(xn, yn)*w2) + impN.getProcessor().getPixel(xn-cx, yn-fy) ;
//						impN.getProcessor().putPixel( xn-cx, yn-fy, pixel);
						double pixel = ip.getPixel(xn, yn)*w2 + N[xn-cx][yn-fy] ;
						N[xn-cx][yn-fy] =  pixel;
					}
				}
				
				for (int xn = fx ; xn < fx+dx; xn++){
					for (int yn = cy ; yn < cy+dy; yn++){
//						int pixel = (int) (ip.getPixel(xn, yn)*w3) + impN.getProcessor().getPixel(xn-fx, yn-cy) ;
//						impN.getProcessor().putPixel( xn-fx, yn-cy, pixel);
						double pixel = ip.getPixel(xn, yn)*w3 + N[xn-fx][yn-cy] ;
						N[xn-fx][yn-cy] =  pixel;
					}
				}
				
				for (int xn = cx ; xn < cx+dx; xn++){
					for (int yn = cy ; yn < cy+dy; yn++){
//						int pixel = (int) (ip.getPixel(xn, yn)*w4) + impN.getProcessor().getPixel(xn-cx, yn-cy) ;
//						impN.getProcessor().putPixel( xn-cx, yn-cy, pixel);
						double pixel = ip.getPixel(xn, yn)*w4 + N[xn-cx][yn-cy] ;
						N[xn-cx][yn-cy] =  pixel;
					}
				}
			   
				for (int xn = 0; xn<imp_center.getWidth(); xn++){
					for (int yn = 0; yn<imp_center.getHeight(); yn++){
//						if (imp_center.getProcessor().getPixel(xn, yn) <= impN.getProcessor().getPixel(xn, yn)){
//							impN.getProcessor().putPixel(xn, yn, 1);
//						}
//						else{
//							impN.getProcessor().putPixel(xn, yn, 0);
//						}
						if (imp_center.getProcessor().getPixel(xn, yn) <= N[xn][yn]){
							N[xn][yn] = 1;
						}
						else{
//							impN.getProcessor().putPixel(xn, yn, 0);
							N[xn][yn] = 0;
						}
					}
				}
			}

			//Update the result matrix.
			int v = (int) Math.pow(2, i);
			
			for (int xn = 0; xn<dx; xn++){
				for (int yn = 0; yn<dy; yn++){
//					impResult.getProcessor().putPixel(xn, yn,impResult.getProcessor().getPixel(xn, yn) + v *  (int)N[xn][yn] );
					result[xn][yn] = result[xn][yn] + v * (int)N[xn][yn];
				}
			}
			
		}
	    
	    
	    // map Result to 60 differnt numbers
	    
	    int[] table = this.map(neighbors);
	    int[][] orbits = this.orbits(neighbors);
	    int newMax = this.getNewMax(neighbors);

	    
	    roi.setLocation(-radius, -radius);
	    impResult.setRoi(roi);
	    ImageProcessor ipResult = impResult.getProcessor();
	    for (int i = 0 ; i < impResult.getHeight(); i++){
	    	for (int j = 0; j < impResult.getWidth(); j++){
	    		
	    		if (roi.contains(j, i)){
	    			int pixel = table[result[j][i]];
	    			ipResult.putPixel(j, i, pixel );
	    		}
	    		else{
	    			ipResult.putPixel(j, i, 255 );
	    		}
	    	}
	    }
	    
	    if (show ==true){
	    	impResult.show();
	    	IJ.setKeyDown(KeyEvent.VK_ALT);
		    IJ.run(impResult,"Histogram", "bins="+newMax+" x_min=0 x_max="+newMax+" y_max=Auto");
	    }
	    
	    
	    int [] hist = ipResult.getHistogram();
	    int [] hist_small = new int[newMax];
	    double[] hist_n = new double[newMax];
	    System.arraycopy(hist, 0, hist_small, 0, hist_small.length);
	    
	    double sum = ipResult.getWidth() * ipResult.getHeight(); 
	    for (int i = 0; i<hist_n.length; i++){
	    	hist_n[i] = hist_small[i] / sum; 
	    }
	    
	    // fourier Transformation for rotation invarianz
	    int FVLEN=(int) ((neighbors-1)*(Math.floor(neighbors/2)+1)+3);
	    
	    double [] feature = new double[FVLEN];
	    
	    int  k = 0; 
	    
	    
	    for (int i = 0; i < orbits.length; i++){
	    	
	    	if (orbits[i][1] != -1){
	    		double[] b = new double[orbits[i].length];
	    	
		    	for (int j = 0 ; j < orbits[i].length; j++){
		    		b[j] = hist_n[orbits[i][j]];
//		    		System.out.print(b[j]+" ");
		    	}
//		    	System.out.println();
		    	

		    	double[] erg = new double[(int) (Math.floor(b.length/2)+1)];
		    	DoubleFFT_1D test = new DoubleFFT_1D(b.length);
		    	test.realForward(b);
		    	
//		    	for (int j = 0 ; j < b.length; j++){
//		    		System.out.print(b[j]+" ");
//		    	}
//		    	System.out.println();
		    	
		    	
		    	
		    	erg[0] = b[0];
		    	erg[erg.length-1] = Math.abs(b[1]);
		    	
		    	int abs = 2; 
		    	for (int j = 1 ; j < erg.length-1; j++){
		    		erg[j] =  getAbs(b[abs], b[abs+1]);
		    		abs = abs+ 2;
		    	}

//		    	for (int j = 0 ; j < erg.length; j++){
//		    		System.out.print(erg[j]+" ");
//		    	}
//		    	System.out.println();
		    	
		    	for (int j = 0; j < erg.length; j++){
		    		feature[k]	= erg[j];
		    		k++;
		    	}
	    	
	    	}
	    	else{
	    		double b = hist_n[orbits[i][0]];
	    		feature[k] = b;
	    		k++;
	    	}
	    }
	    
	    double[] x = new double[feature.length];
	    for (int j = 0; j< feature.length; j++){
//    		System.out.print(feature[j]+"; ");
    		x[j] = j;
    	}
//    	System.out.println();
	    
    	this.setFeature(feature);
    	
    	if (show == true){
    		Plot plot = new Plot("LBP-Feature", "x ", "Frequency ", x, feature ); 
    		plot.addPoints(x, feature, Plot.CIRCLE);
        	plot.show();
    	}
    	
    	return true;
	}

	private void setFeature(double[] feature) {
		this.feature = feature;
	}

	private double getAbs(double r, double i) {
		return Math.sqrt(Math.pow(r, 2) + Math.pow(i, 2));
	}

	private int[][] orbits(int neighbors) {
		int [][] orbits= new int[neighbors+2][neighbors];
		int newMax = getNewMax(neighbors);
//		System.out.println(neighbors+2);
		int count = 0; 
		for (int i = 0; i<neighbors-1; i++){
			for (int j = 0; j<neighbors; j++){
				orbits[i][j] = count;
				count++;
			}
		}
		
		orbits[neighbors-1][0] = newMax-3;
		orbits[neighbors][0] = newMax-2;
		orbits[neighbors+1][0] = newMax-1;
		

		orbits[neighbors-1][1] = -1;
		orbits[neighbors][1] = -1;
		orbits[neighbors+1][1] = -1;

		return orbits;
	}

	private int[] map(int neighbors) {
		 int [] table = new int[(int) Math.pow(2, neighbors)];
		 
		 for (int i = 0; i< table.length; i++){
			 table[i] = i;
		 }
		 
		 int index   = 0;

		 int newMax = getNewMax(neighbors);
		 table[0] = newMax - 3;
		 table[table.length-1] = newMax - 2;
//		 int test = (byte)254 << 1;
		
		 for (int i = 1; i<table.length-1; i++ ){
			 
			int j = setBit(shiftBit(1,i,neighbors),0,testBit(i,neighbors-1));//rotate left
			int numt = 0;
			for (int m =0; m<neighbors; m++){
				numt = numt + testBit(i^j, m);
			}
			
			if (numt == 2){
				
				int n = 0;
				for (int m =0; m<neighbors; m++){
					n = n + testBit(i, m);
				}
				
				int k =  i & bitCmp(j, neighbors);
				int r = 0; 
				for (int m =0; m<neighbors; m++){
					if (testBit(k, m) == 1){
						r = m+1;
						break; 
					}
				}
				
				r= (int) ((Math.floor(n/2)+r) % neighbors);
				index = (n-1)*neighbors + r;
				table[i] = index;
				
			}
			else{
				table[i] = newMax - 1;
			}

		 }
		
		return table;
	}

	private int getNewMax(int neighbors) {
		return neighbors*(neighbors-1) + 3;
	}

	private int bitCmp(int j, int neighbors) {
		return (0xFFFFFFFF << neighbors)  ^ (~j); 
	}

	private int setBit(int n, int i, int testo) {
		if (testo == 0) return n & ~(1 << i);
		else			return n | (1 << i);
	}

	

	
	private double round(double d, int c) {
		int temp=(int)((d*Math.pow(10,c)));
		return (((double)temp)/Math.pow(10,c));
	}
	
	static int testBit( int n, int pos )
	{
	  int mask = 1 << pos;
	  
	  if ((n & mask) == mask)	return 1;
	  else 						return 0;
//	  return (n & mask) == mask;
	  // alternativ: return (n & 1<<pos) != 0;
//	  return (n & 1<<pos) != 0;
	}
	
	static int flipBit( int n, int pos )
	{
	  return n ^ (1 << pos);
	}

	static int shiftBit(int bits, int k, int width){
		if (Integer.toBinaryString(k).length()<width) return k << bits;
		else return (1 << width) ^ (k << bits);
	}

	public double[] getFeature() {
		return this.feature;
	}
	
	
}