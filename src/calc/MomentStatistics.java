package calc;
import java.awt.Rectangle;
import java.lang.reflect.Array;

import ij.IJ;
import ij.measure.Calibration;
import ij.process.ImageStatistics;

/**
 * @version  	1.1 20 Nov 2008
 * 				1.0 19 Oct 2008
 *            
 * @author Dimiter Prodanov
 * 		   IMEC
 *
 *
 * @contents This class calculates image moments and the principal axes of orientation.
 *		The code is based on http://en.wikipedia.org/wiki/Image_moments.
 *
 *
 * @license This library is free software; you can redistribute it and/or
 *      modify it under the terms of the GNU Lesser General Public
 *      License as published by the Free Software Foundation; either
 *      version 2.1 of the License, or (at your option) any later version.
 *
 *      This library is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *       Lesser General Public License for more details.
 *
 *      You should have received a copy of the GNU Lesser General Public
 *      License along with this library; if not, write to the Free Software
 *      Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */


public class MomentStatistics extends ImageStatistics {
	double volume=0;
	double xbar=0;
	double ybar=0;
	double [] centroid=new double[2];
	int order=1;
	boolean raw_calculated=false, 
			centr_calculated=false, 
			scale_calculated=false,
			calibrated=false;
	double[][] rawmoments;
	double[][] centralmoments;
	double[][] scaledmoments;
	static double [][] bincoefs;

	Rectangle rect;
	
	public MomentStatistics (int n, Rectangle r ){
		order=n;
		//IJ.log("n " +n);
		if (n <=0) {
			IJ.error("Illegal order");
			throw new IllegalArgumentException();
		}
		rawmoments=new double[n][n];
		centralmoments=new double[n][n];
		scaledmoments=new double[n][n];
		
		
		if ((r.width <=0) || (r.height <=0)) {
			IJ.error("Illegal Rectangle dimensions");
			throw new IllegalArgumentException();
		}
		rect=r;
		
		binCoefs(n);
		//printCoefs();
	}
	
	
	public void setCalibration(Calibration cal) {
		this.cal=cal;
		//IJ.log("pw " + cal.pixelWidth +" ph "+ cal.pixelHeight);
		if (cal!=null)
			calibrated=true;
		
		
	 

	}
	

	
	public double calculateRawMoment(float[] pixels, int p, int q){
		return calculateRawMoment(pixels, rect.width, p,  q);

	}
	
	public double calculateRawMoment(float[] pixels, int width, int p, int q){
		double ret=0.0f;
		double sum=0.0f;
		int sz=pixels.length;
		
		double factor=1.0;
		if (calibrated)
			factor*=Math.pow(cal.pixelWidth,p)*Math.pow(cal.pixelHeight,q);
		//IJ.log("width "+ width);
		for (int i=0; i<sz; i++) {
			int x=i%width;
			int y=i/width;
            
           // IJ.log(i+" x: "+x +" y: "+y);
			if  (p==0){
				if (q==0) {
					sum+=pixels[i]; //volume
				} else { // q>0
					sum+=pixels[i]*Math.pow(y,q);
				}
			} else {	// p>0 
				if (q==0) {
					sum+=pixels[i]*Math.pow(x,p);
				} else { // q>0
					sum+=pixels[i]*Math.pow(x,p)*Math.pow(y,q);
				}
			}
			
		} // end for
		ret=sum*factor;
		return ret;
	}
	
	public void calculateRawMoments(float[] pixels) {
		calculateRawMoments(pixels,  rect.width);
	}
	
	public void calculateRawMoments(float[] pixels, int width){
		int j=order+1;
		//double[][] ret=new double[j][j];
		double[][] sum=new double[j][j];
		int sz=pixels.length;
		//IJ.log("ord "+ j);
		double factor=1.0;
	
		for (int i=0; i<sz; i++) {
			int x=i%width;
			int y=i/width;
            
			double [] pc=new double [j*j];
			//IJ.log("sz "+pc.length);
           // IJ.log(i+" x: "+x +" y: "+y);
			for (int p=0; p<order; p++) {
				for (int q=0; q<order; q++) {
					int index=p*order+q;
					//IJ.log("index "+index);
					if  (p==0){
						if (q==0) {
							sum[0][0]+=pixels[i]; //volume
							pc[index]=1;
						} else { // q>0
							//sum[p][q]+=pixels[i]*Math.pow(y,q);
							pc[index]=y*pc[q-1];
							sum[p][q]+=pixels[i]*pc[index];
						}
					} else {	// p>0 
						if (q==0) {
							//sum[p][0]+=pixels[i]*Math.pow(x,p);
							pc[index]=x*pc[(p-1)*order];
							sum[p][q]+=pixels[i]*pc[index];
						} else { // q>0
							//sum[p][q]+=pixels[i]*Math.pow(x,p)*Math.pow(y,q);
							pc[index]=x*y*pc[(p-1)*order+q-1];
							sum[p][q]+=pixels[i]*pc[index];
						}
					}
				
					//sum[q][p]=sum[p][q];
				} // end for q
			} // end for p
			
			
		} // end for i
		//ret=sum;
		if (calibrated) {
			for (int p=0; p<order; p++) {
				//factor*=cal.pixelWidth;
				for (int q=0; q<order; q++) {
					//int index=p*order+q;
					//factor*=cal.pixelHeight;
						factor=Math.pow(cal.pixelWidth,p)*Math.pow(cal.pixelHeight,q);
					sum[p][q]*=factor;
				} // end for q
			} // end for p
		} // end if
		raw_calculated=true;
		volume=sum[0][0];
		rawmoments=sum;
		//return ret;
	}
	
 
	public double calculateCentralMoment(float[] pixels, int p, int q){
		return calculateCentralMoment(pixels, rect.width, p,  q);

	}
	
	public double calculateCentralMoment(float[] pixels, int width, int p, int q){
		//Volume: M00
		//Centroid: {\bar{x}, \bar{y} } = {M10/M00, M01/M00 }
		double ret=0;
		double sum=0;
		double factor=1.0;
		if (calibrated)
			factor*=Math.pow(cal.pixelWidth,p)*Math.pow(cal.pixelHeight,q);
	
		volume=calculateRawMoment(pixels, width, 0, 0);
		
		if ((p==0) && (q==0))
			return volume;
		
		
		double m10=calculateRawMoment(pixels, width, 1, 0);
		xbar=m10/volume;
			
		double m01=calculateRawMoment(pixels, width, 0, 1);
		ybar=m01/volume;
		
		//IJ.log("xbar: "+xbar);
		//IJ.log("ybar: "+ybar);
		
		if (q==0) return m10;
		if (p==0) return m01;
			
		int sz=pixels.length;
		for (int i=0; i<sz; i++) {
			float x=i%width;
            float y=i/width;
			if  (p==0){
				if (q==0) {
					sum+=pixels[i]; //volume
				} else { // q>0
					//float dy=y-ybar;
					sum+=pixels[i]* Math.pow(y-ybar, q);
				}
			} else {	// p>0 
				if (q==0) {
					//float dx=x-xbar;
					sum+=pixels[i]*Math.pow(x-xbar, p);
				} else { // q>0
					
					sum+=pixels[i]*Math.pow(x-xbar,p)*Math.pow(y-ybar,q);
				}
			}
			
		} // end for
		ret=sum;
		return ret;
	}
		
	public void calculateCentralMoments(float[] pixels) {
		calculateCentralMoments(pixels,  rect.width);
	}
	
	public void calculateCentralMoments(float[] pixels, int width){
		int j=order+1;
		double[][] ret=new double[j][j];

		if (!raw_calculated) {
			 calculateRawMoments(pixels, width);
		}
	
		//volume=rawmoments[0][0];
		 
		if (order<=1) {
			centralmoments[0][0]=rawmoments[0][0];
			return ;
		}	
		else {
			xbar=rawmoments[1][0]/volume - 0.5; //continuity correction
			ybar=rawmoments[0][1]/volume - 0.5; //continuity correction
			centroid[0]=xbar;
			centroid[1]=ybar;
			//IJ.log("xbar "+xbar+ " ybar "+ybar);
			for (int p=0; p<order; p++) {
				for (int q=0; q<order; q++) {
					double sum=0;
					for (int m=0; m<rawmoments.length; m++) {
						for (int n=0; n<rawmoments[0].length; n++) {
							//IJ.log("m "+m+ " n "+n);
							sum+= binCoef(p, m)* binCoef(q, n)
							*Math.pow(-xbar, p-m)*Math.pow(-ybar, q-n)
							*rawmoments[m][n];
						}
					}
					ret[p][q]=sum;
			
				} // end q
			} // end p
			
		} // end else
	
		centr_calculated=true;
		centralmoments=ret;
		//return ret;
		
	}
	
	public double binCoef(int n, int k) {
		if (k<0) return 0;
		if (k>n) return 0;
		if (k==0) return 1;
		if (k==n) return 1;
		//int a=n-1;
		//int b=k-1;
		//IJ.log("C^ ["+a + "] _["+b+"] =" +bincoefs[a][b]);
		return bincoefs[n][k];
	}
	
	public static void binCoefs( int n) {
		double[][] carray=new double[n][];
		//IJ.log("length 1: "+carray.length);
		for (int z=1; z<=n; z++){
			carray[z-1]=new double [z];
			//IJ.log("length 2: "+ carray[z-1].length);
			for (int k=1; k<=z; k++) {
				if ((k==z) || (k<=1) ){
					carray[z-1][k-1]=1;
				} else {
					carray[z-1][k-1]=carray[z-2][k-2]+ carray[z-2][k-1];
					//carray[z-1][z-k]=carray[z-1][k-1];
					//IJ.log("c ["+z + "]["+k +"] =" +carray[z-1][k-1]);
				}
			}
		}
	

		bincoefs= carray;
	}
	
	public void printCoefs() {
		for (int i=0; i<bincoefs.length; i++) {
			for (int j=0; j<bincoefs[i].length; j++) {
				int a=i+1;
				int b=j+1;
				IJ.log("C^ ["+a + "] _["+b+"] =" +bincoefs[i][j]);
			}
		}
	}
	/*
	private void printArr(Object array) {
		if (isArray(array)) {
			if (array instanceof long[]) {
				long[] obj= (long[]) array;
				for (int i=0; i<obj.length; i++) {
					if (isArray(obj)) {
						//printArr( obj);
						IJ.log (" array of long"  );
					} else {
						IJ.log ("x[" +i+" ]= "+ obj[i]);
					}
				}
			}
			if (array instanceof int[]) {
				int[] obj= (int[]) array;
				for (int i=0; i<obj.length; i++) {
					if (isArray(obj)) {
						//printArr( obj);
						IJ.log (" array of int"  );
					} else {
						IJ.log ("x[" +i+" ]= "+ obj[i]);
					}
				}
			}

			if (array instanceof short[]) {
				short[] obj= (short[]) array;
				for (int i=0; i<obj.length; i++) {
					if (isArray(obj)) {
						//printArr( obj);
						IJ.log (" array of short"  );
					} else {
						IJ.log ("x[" +i+" ]= "+ obj[i]);
					}
				}
			}
			if (array instanceof byte[]) {
				byte[] obj= (byte[]) array;
				for (int i=0; i<obj.length; i++) {
					if (isArray(obj)) {
						//printArr( obj);
						IJ.log (" array of byte"  );
					} else {
						IJ.log ("x[" +i+" ]= "+ obj[i]);
					}
				}
			}
			if (array instanceof float[]) {
				float[] obj= (float[]) array;
				for (int i=0; i<obj.length; i++) {
					if (isArray(obj)) {
						//printArr( obj);
						IJ.log (" array of float"  );
					} else {
						IJ.log ("x[" +i+" ]= "+ obj[i]);
					}
				}
			}
			if (array instanceof double[]) {
				double[] obj= (double[]) array;
				for (int i=0; i<obj.length; i++) {
					if (isArray(obj)) {
						//printArr( obj);
						IJ.log (" array of double"  );
					} else {
						IJ.log ("x[" +i+" ]= "+ obj[i]);
					}
				}
			}
			if (array instanceof boolean[]) {
				boolean[] obj= (boolean[]) array;
				for (int i=0; i<obj.length; i++) {
					if (isArray(obj)) {
						//printArr( obj);
						IJ.log (" array of boolean"  );
					} else {
						IJ.log ("x[" +i+" ]= "+ obj[i]);
					}
				}
			}
			if (array instanceof char[]) {
				char[] obj= (char[]) array;
				for (int i=0; i<obj.length; i++) {
					if (isArray(obj)) {
						//printArr( obj);
						IJ.log (" array of char"  );
					} else {
						IJ.log ("x[" +i+" ]= "+ obj[i]);
					}
				}
			}
			if (array instanceof Object[]) {
				Object[] obj= (Object[]) array;
				for (int i=0; i<obj.length; i++) {
					if (isArray(obj)) {
						//printArr( obj);
						IJ.log (" array of Object"  );
					} else {
						IJ.log ("x[" +i+" ]= "+ obj[i]);
					}
				}
			}

		}
	}
	
	private static boolean isArray(Object array) {
	    if (array.getClass().isArray()) {
	      return true;
	    }
	    return false;
	}
	
	// If `array' is an array object returns its dimensions; otherwise returns 0
    private static int getDim(Object array) {
        int dim = 0;
        Class cls = array.getClass();
        while (cls.isArray()) {
            dim++;
            cls = cls.getComponentType();
        }
        return dim;
    }

	
	*/

	public double calculateScaledMoment(float[] pixels, int p, int q){
		return calculateScaledMoment(pixels, rect.width, p,  q);

	}

	
	public static float[]  calibratePixels(Object pixels, float[]ctable) {
		 	//Log(" enter calibration" );
			if (pixels instanceof float[]) {
				//Log("calibrating float" );
				return (float[]) pixels;
			}
			
			if (pixels instanceof byte[]) {
				//Log("calibrating byte" );
				byte[] b=(byte[]) pixels;
				 
				int sz=b.length;
				//Log("length "+sz);
				float[] ret=new float [sz];
				for (int i=0; i<sz; i++ ) {
			 		ret[i]= ctable[b[i] & 0xFF];
					//IJ.log(i+" "+ ret[i] +" - " +b[i]);
					
				}
				//LogPix(ret);
				return ret;
			} // end byte[]
			
			if (pixels instanceof short[]) {
				//Log("calibrating short" );
				short[] b=(short[]) pixels;
				//int sz=((byte[]) bytes).length;
				int sz=b.length;
				
				float[] ret=new float [sz];
				for (int i=0; i<sz; i++ ) {
					ret[i]=ctable [b[i] & 0xFFF];
				}
				return ret;
			} // end short[]

			if (pixels instanceof int[]) {
				//Log("calibrating int" );
				int[] b=(int[]) pixels;
				//int sz=((byte[]) bytes).length;
				int sz=b.length;
				
				float[] ret=new float [sz];
				for (int i=0; i<sz; i++ ) {
					ret[i]= ctable[b[i] & 0xFFFF];
				}
				return ret;
			} // end byte[]
			
					
			return null;
		}

	
	public double calculateScaledMoment(float[] pixels, int width, int p, int q){
		double ret=0;
		
		if ((p+q)==0) return 1;
		if ((p+q)==1) return 0;
		
		if  (!centr_calculated) {
 			ret=calculateCentralMoment(pixels, width, p,q);
		}
		ret=ret/Math.pow(volume, 1+(p+q)/2);
		return ret;
	}
		
	
	public void calculateScaledMoments(float[] pixels) {
		calculateScaledMoments(pixels,  rect.width);
	}
	
	public void calculateScaledMoments(float[] pixels, int width) {
		int j=order+1;
		if  (!centr_calculated) {
 			 calculateCentralMoments(pixels, width);
		}
		double[][] ret=new double[j][j];
		ret[0][0]=1;
		//IJ.log("volume "+volume);
		for (int p=0; p<order; p++) {
			for (int q=0; q<order; q++) {
				if ((p+q)>=2)
					ret[p][q]=centralmoments[p][q]/Math.pow(volume, 1+(p+q)/2);
			}
		}
		scale_calculated=true;
		scaledmoments=ret;
		//return ret;
	}

	public void normalize(float factor) {
		for (int i=0; i<order; i++) {
			for (int j=0; i<order; i++) {
				if (raw_calculated){
					rawmoments[i][j]=rawmoments[i][j]/factor;
				}
				if (centr_calculated){
					centralmoments[i][j]=centralmoments[i][j]/factor;
				}
				if (scale_calculated){
					scaledmoments[i][j]=scaledmoments[i][j]/factor;
				}
			}
		}

	}
	
	public double[][] getCentralMoments() {
		return centralmoments;
	}

	public double[][] getRawMoments() {
		return rawmoments;
	}

	public double[][] getScaledMoments() {
		return scaledmoments;
	}
 
	public boolean isCentr_calculated() {
		return centr_calculated;
	}
	
	public double[] getCentroid() {
		return centroid;
	}
	
	public int getOrder() {
		return order;
	}
	
	public boolean isRaw_calculated() {
		return raw_calculated;
	}
	
	public boolean isScale_calculated() {
		return scale_calculated;
	}
	
	public double getVolume() {
		return volume;
	}


	public double[][] getBincoefs() {
		return bincoefs;
	}
	
}