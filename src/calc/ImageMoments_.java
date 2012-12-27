package calc;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.Properties;

import ij.*;
import ij.gui.*;
import ij.measure.Calibration;
import ij.plugin.filter.*;
import ij.process.*;
 


/**
 * @version  	1.1	24 Nov 2008
 * 				- request by Gabriel Landini to allow also calibrated pixel values
 * 				1.0 11 Sept 2008
 *            
 * @author Dimiter Prodanov
 * 		   IMEC
 *
 *
 * @contents This plugin calculates image moments of n-th order.
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

public class ImageMoments_ implements PlugInFilter {
	private Roi roi;
	ImagePlus imp;
    boolean isProcessibleRoi=false, procStack=false;
	double volume=0;
	double xbar=0;
	double ybar=0;
	double [] centroid=new double[2];
	
	boolean raw_calculated=false, centr_calculated=false, scale_calculated=false;
	double[][] rawmoments;
	double[][] centralmoments;
	double[][] scaledmoments;
	double [][] cov=new double[2][2];
	double lambda1=0;
	double lambda2=0;
	double theta=0;
	public static final String CALIBRATE="mscalibrate", ORDER="msorder", NORMALIZE="msnorm";
	private static boolean calibrate=Prefs.getBoolean(CALIBRATE,false);
	private static int order=Prefs.getInt(ORDER,4);
	private static boolean normalize=Prefs.getBoolean(NORMALIZE,false);
	/* (non-Javadoc)
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	int width=-1, height=-1;
	//@Override
	public void run(ImageProcessor ip) {
		
		int offsetx=0, offsety=0;
		//IJ.log("proc. roi "+ isProcessibleRoi);
		float[] pixels;
		Rectangle r;
		Calibration cal=imp.getCalibration();
		float [] ctable=cal.getCTable();
		
	/*	if (ctable!=null)
			for (int i=0; i<ctable.length; i++) {
				Log("c["+i + "]= "+ ctable[i]);
			}
*/	
		if (isProcessibleRoi) {
			r=ip.getRoi();
			ImageProcessor ip2=this.getMask(ip, r);
			width=ip2.getWidth();
			height=ip2.getHeight();
			offsetx=r.x;	
			offsety=r.y;	
	
			//IJ.log("offset: "+ offsetx + " ; "+ offsety);
			
			if (calibrate) {
				if (ctable!=null) {
					//Log("calibrating");
					pixels=MomentStatistics.calibratePixels(ip2.getPixels(), ctable);
					//LogPix(pixels);
				} else {
					pixels=this.toFloatPixels(ip2.getPixels());
				}
			
			} else {
				pixels=this.toFloatPixels(ip2.getPixels());
			}
			 
		} else {
			width=ip.getWidth();
			height=ip.getHeight();
			if (calibrate) {
				if (ctable!=null)	{
					//Log("calibrating");
					pixels=MomentStatistics.calibratePixels(ip.getPixels(), ctable);
					//LogPix(pixels);
				} else {
					pixels=this.toFloatPixels(ip.getPixels());
				}
			} else {
				pixels=this.toFloatPixels(ip.getPixels());
			}
     		
			r=new Rectangle(width, height);
			
		}
		Log("rect: " +r.x + " "+ r.y+ " "+ r.width+ " "+ r.height);

		MomentStatistics ms=new MomentStatistics(order,r);
		ms.setCalibration(cal);
		ms.calculateRawMoments(pixels, width);
		ms.calculateCentralMoments(pixels, width);
		ms.calculateScaledMoments(pixels, width);
		
		String unit=cal.getValueUnit()+"."+cal.getUnit();
		String unit2=cal.getUnit();
		centralmoments=ms.getCentralMoments();
		rawmoments=ms.getRawMoments();
 		scaledmoments=ms.getScaledMoments();
	 
 		boolean print = false; 
 		
 		if (print == true){
		IJ.setColumnHeadings("parameter\tvalue\tunit");
		StringBuffer sb=new StringBuffer (200);
		centroid=ms.getCentroid();
		xbar=centroid[0];
		ybar=centroid[1];
		sb.append("xbar\t"+  centroid[0]+ "\t"+unit2+"\n" );
		sb.append("ybar\t"+  centroid[1]+ "\t"+unit2+"\n" );
		
		for (int i=0; i<rawmoments.length-1; i++) {
			for (int j=0; j<rawmoments[0].length-1; j++) {
				//sb.append("M["+i+"]["+j +"]\t"+rawmoments[i][j] +"\n" );
				int k=i+j;
				sb.append("m["+i+"]["+j +"]\t"+centralmoments[i][j]+ "\t"+unit+"^" +k+ "\n" );

				//sb.append("n["+i+"]["+j +"]\t"+scaledmoments[i][j]+"\n" );
			}
		}
		
		for (int i=0; i<rawmoments.length-1; i++) {
			for (int j=0; j<rawmoments[0].length-1; j++) {
				int k=i+j;
				sb.append("M["+i+"]["+j +"]\t"+rawmoments[i][j] + "\t"+unit+"^" +k+"\n" );
				//sb.append("m["+i+"]["+j +"]\t"+centralmoments[i][j]+"\n" );

				//sb.append("n["+i+"]["+j +"]\t"+scaledmoments[i][j]+"\n" );
			}
		}
		
		for (int i=0; i<rawmoments.length-1; i++) {
			for (int j=0; j<rawmoments[0].length-1; j++) {
				int k=i+j;
				//sb.append("M["+i+"]["+j +"]\t"+rawmoments[i][j] +"\n" );
				//sb.append("m["+i+"]["+j +"]\t"+centralmoments[i][j]+"\n" );

				sb.append("n["+i+"]["+j +"]\t"+scaledmoments[i][j]+ "\t"+unit+"^" +k+"\n" );
			}
		}
		
		
		 
		//orientationStats(ms, offsetx, offsety);
		
		// Calculate Rotation invariant moments based on Hu 1962
		
		if (order >= 4){
			double I1 = scaledmoments[2][0] + scaledmoments[0][2];
			double I2 = Math.pow((scaledmoments[2][0] - scaledmoments[0][2] ), 2) + Math.pow(2*scaledmoments[1][1], 2);
			double I3 = Math.pow(scaledmoments[3][0] - 3 * scaledmoments[1][2], 2) + Math.pow(3 * scaledmoments[2][1] - scaledmoments[0][3], 2);
			double I4 = Math.pow(scaledmoments[3][0] + scaledmoments[1][2], 2) + Math.pow(scaledmoments[2][1] + scaledmoments[0][3], 2);
			double I5 = (scaledmoments[3][0] - 3 * scaledmoments[1][2]) * (scaledmoments[3][0] + scaledmoments[1][2]) * (Math.pow(scaledmoments[3][0] + scaledmoments[1][2], 2) - 3 * Math.pow(scaledmoments[2][1] + scaledmoments[0][3], 2)) +
						(3 * scaledmoments[2][1] - scaledmoments[0][3]) * (scaledmoments[2][1] + scaledmoments[0][3]) * (3 * Math.pow(scaledmoments[3][0] + scaledmoments[1][2], 2) - Math.pow(scaledmoments[2][1] + scaledmoments[0][3], 2));
			double I6 = (scaledmoments[2][0] - scaledmoments[0][2] ) * (Math.pow(scaledmoments[3][0] + scaledmoments[1][2], 2) -  Math.pow(scaledmoments[2][1] + scaledmoments[0][3], 2)) + 4 * scaledmoments[1][1] * (scaledmoments[3][0] + scaledmoments[1][2]) * (scaledmoments[2][1] + scaledmoments[0][3]);
			double I7 = (3 * scaledmoments[2][1] - scaledmoments[0][3]) * (scaledmoments[3][0] + scaledmoments[1][2]) * (Math.pow(scaledmoments[3][0] + scaledmoments[1][2], 2) - 3 * Math.pow(scaledmoments[2][1] + scaledmoments[0][3], 2)) -
						(scaledmoments[3][0] - 3 * scaledmoments[1][2]) * (scaledmoments[2][1] + scaledmoments[0][3]) * (3 * Math.pow(scaledmoments[3][0] + scaledmoments[1][2], 2) - Math.pow(scaledmoments[2][1] + scaledmoments[0][3], 2));
			
			
			sb.append("I1\t"+I1+ "\t"+"\n");
			sb.append("I2\t"+I2+ "\t"+"\n");
			sb.append("I3\t"+I3+ "\t"+"\n");
			sb.append("I4\t"+I4+ "\t"+"\n");
			sb.append("I5\t"+I5+ "\t"+"\n");
			sb.append("I6\t"+I6+ "\t"+"\n");
			sb.append("I7\t"+I7+ "\t"+"\n");
		}
		
		
		IJ.write(sb.toString());
 		}
		
	}

	public float[]  toFloatPixels(Object pixels) {
		
		if (pixels instanceof float[]) 
			return (float[]) pixels;
		
		if (pixels instanceof byte[]) {
			byte[] b=(byte[]) pixels;
			//int sz=((byte[]) bytes).length;
			int sz=b.length;
			
			float[] ret=new float [sz];
			for (int i=0; i<sz; i++ ) {
				ret[i]=(float)(b[i] & 0xFF);
				//IJ.log(i+" "+ ret[i] +" - " +b[i]);
				
			}
			return ret;
		} // end byte[]
		
		if (pixels instanceof short[]) {
			short[] b=(short[]) pixels;
			//int sz=((byte[]) bytes).length;
			int sz=b.length;
			
			float[] ret=new float [sz];
			for (int i=0; i<sz; i++ ) {
				ret[i]=(float)(b[i] & 0xFFF);
			}
			return ret;
		} // end short[]

		if (pixels instanceof int[]) {
			int[] b=(int[]) pixels;
			//int sz=((byte[]) bytes).length;
			int sz=b.length;
			
			float[] ret=new float [sz];
			for (int i=0; i<sz; i++ ) {
				ret[i]=(float)(b[i] & 0xFFFF);
			}
			return ret;
		} // end byte[]

		
		return null;
	}
	
	
  	double k=-1;

	
	
	/* (non-Javadoc)
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	//@Override
	public int setup(String arg, ImagePlus imp) {
		this.imp=imp;
//		if (arg.equals("about")){
//            showAbout();
//            return DONE;
//        }
//        try {
//        	isProcessibleRoi=processibleRoi(imp);
//        } catch (NullPointerException ex) { 
//        	return DONE;
//        }
//        
//        if(IJ.versionLessThan("1.35") || !showDialog(imp)) {
//            return DONE;
//        }
//        else {
//            return DOES_8G+DOES_16+NO_CHANGES+DOES_32+NO_UNDO +DOES_STACKS;
//        }
		return DOES_8G+DOES_16+NO_CHANGES+DOES_32+NO_UNDO +DOES_STACKS;
	} //

	/**
	 * @param args
	 */
	public static void main(String[] args) {
    	try {
    		System.setProperty("plugins.dir", args[0]);
    		new ImageJ();
    	}
    	catch (Exception ex) {
    		Log("plugins.dir misspecified");
    	}

	}
	
    /* general support for debug variables 
     */
     private static boolean debug=false;
     public static void Log(String astr) {
     	if (debug) IJ.log(astr);
     }
     
     
     public boolean processibleRoi(ImagePlus imp) {
    	// try {
    	   	roi = imp.getRoi();
    	       boolean ret=(roi!=null && !(roi.getType()==Roi.LINE || 
    	       						 roi.getType()==Roi.POLYLINE ||
    	       						 roi.getType()==Roi.ANGLE ||
    	       						 roi.getType()==Roi.FREELINE ||
    	       						 roi.getType()==Roi.POINT
    	       						 )
    	       		   );
    	       //Log("roi ret "+ ret);
    	       return ret;
    	 //} catch (NullPointerException ex) { 
    	//	 return false;
    	// }
    }
     
     void showAbout() {
         IJ.showMessage("About ImageMoments...",
         ""
         );
     }
     
     /*------------------------------------------------------------------*/
     boolean showDialog(ImagePlus imp)   {
         
         if (imp==null) return true;
         GenericDialog gd=new GenericDialog("Parameters");
         
         // Dialog box for user input
         gd.addMessage("This plugin performs image moments calculation\n");
         gd.addNumericField("order", order, 0);
         gd.addCheckbox("Calibrate?", calibrate);
         gd.addCheckbox("Normalize?", normalize);
         // radius=size/2-1;
         gd.showDialog();
         order=(int)gd.getNextNumber();
         calibrate = gd.getNextBoolean();   
         normalize = gd.getNextBoolean(); 
         //Log("order "+order);
        // rawmoments=new double[order][order];
        // centralmoments=new double[order][order];
         
         if (gd.wasCanceled())
             return false;
         
          
         return true;
     } /* showDialog */
     
     /*------------------------------------------------------------------*/
     /* Saves the current setings of the plugin for further use
      * 
      *
     * @param prefs
     */
    public static void savePreferences(Properties prefs) {
        prefs.put(CALIBRATE,Boolean.toString(calibrate));
        prefs.put(ORDER, Integer.toString(order));
    }
     
     /* Extracts the ImageProcessor within a rectangular roi
      * 
      * @param ip
      * @param r
      * @return ImageProcessor
      */
     
     public ImageProcessor getMask(ImageProcessor ip, Rectangle r) {
    	 
    	 if (ip instanceof ByteProcessor) {
    		 return getByteMask((ByteProcessor) ip, r);
    	 }
    	     	   
         if (ip instanceof ShortProcessor) {
        	 return getShortMask((ShortProcessor) ip, r);
         }
        	 
         if (ip instanceof FloatProcessor) {
        	 return getFloatMask((FloatProcessor) ip, r);
         }	 
		 
         return null;
     }
     
     
     /* Extracts the ByteProcessor within a rectangular roi
      * 
      * @param ip
      * @param r
      * @return ByteProcessor
      */
     public ByteProcessor getByteMask(ByteProcessor ip, Rectangle r) {
     	
         int width = ip.getWidth();
       	 byte[] pixels = (byte[])ip.getPixels();

       	 
       	 int xloc=(int)r.getX(); int yloc=(int)r.getY();
         int w=(int)r.getWidth();
         int h=(int)r.getHeight();
         //IJ.log("roi length " +w*h);
         byte[] mask=new byte[w*h];
     
         for (int cnt=0; cnt<mask.length;cnt++) {
            int index=xloc+cnt%w + (cnt/w)*width +yloc*width;
            mask[cnt]=(byte)(pixels[index] & 0xFF);  
                  	
         }

         return new ByteProcessor(w, h, mask,ip.getColorModel());

        }
     
     
     /* Extracts the FloatProcessor within a rectangular roi
      * 
      * @param ip
      * @param r
      * @return FloatProcessor
      */
     public ShortProcessor getShortMask(ShortProcessor ip, Rectangle r) {
     	
      int width = ip.getWidth();
    	 short[] pixels = (short[])ip.getPixels();

    	 
    	int xloc=(int)r.getX(); int yloc=(int)r.getY();
      int w=(int)r.getWidth();
      int h=(int)r.getHeight();
      //IJ.log("roi length " +w*h);
      short[] mask=new short[w*h];
  
      for (int cnt=0; cnt<mask.length;cnt++) {
         int index=xloc+cnt%w + (cnt/w)*width +yloc*width;
         mask[cnt]=pixels[index];  
               	
      }

      return new ShortProcessor(w, h, mask, ip.getColorModel());

     }
     
     /* Extracts the FloatProcessor within a rectangular roi
      * 
      * @param ip
      * @param r
      * @return FloatProcessor
      */
     public FloatProcessor getFloatMask(FloatProcessor ip, Rectangle r) {
     	
      int width = ip.getWidth();
      float[] pixels = (float[])ip.getPixels();

    	 
      int xloc=(int)r.getX(); int yloc=(int)r.getY();
      int w=(int)r.getWidth();
      int h=(int)r.getHeight();
      //IJ.log("roi length " +w*h);
      float[] mask=new float[w*h];
  
      for (int cnt=0; cnt<mask.length;cnt++) {
         int index=xloc+cnt%w + (cnt/w)*width +yloc*width;
         mask[cnt]=pixels[index];  
               	
      }

      return new FloatProcessor(w, h, mask, ip.getColorModel());

     }
     
     public double[][] getRawMoments(){
    	 return rawmoments;
     }
     
     public double[][] getCentralMoments(){
    	 return centralmoments;
     }
     
     public double[][] getScaledMoments(){
    	 return scaledmoments;
     }
     
     public double[] getInvariantMoments(){
    	 double[] inv = new double[8];
    	 
    	 inv[0] = scaledmoments[2][0] + scaledmoments[0][2];
    	 inv[1] = Math.pow((scaledmoments[2][0] - scaledmoments[0][2] ), 2) + Math.pow(2*scaledmoments[1][1], 2);
    	 inv[2] = Math.pow(scaledmoments[3][0] - 3 * scaledmoments[1][2], 2) + Math.pow(3 * scaledmoments[2][1] - scaledmoments[0][3], 2);
    	 inv[3] = Math.pow(scaledmoments[3][0] + scaledmoments[1][2], 2) + Math.pow(scaledmoments[2][1] + scaledmoments[0][3], 2);
    	 inv[4]	= (scaledmoments[3][0] - 3 * scaledmoments[1][2]) * (scaledmoments[3][0] + scaledmoments[1][2]) * (Math.pow(scaledmoments[3][0] + scaledmoments[1][2], 2) - 3 * Math.pow(scaledmoments[2][1] + scaledmoments[0][3], 2)) +
				  (3 * scaledmoments[2][1] - scaledmoments[0][3]) * (scaledmoments[2][1] + scaledmoments[0][3]) * (3 * Math.pow(scaledmoments[3][0] + scaledmoments[1][2], 2) - Math.pow(scaledmoments[2][1] + scaledmoments[0][3], 2));
    	 inv[5] = (scaledmoments[2][0] - scaledmoments[0][2] ) * (Math.pow(scaledmoments[3][0] + scaledmoments[1][2], 2) -  Math.pow(scaledmoments[2][1] + scaledmoments[0][3], 2)) + 4 * scaledmoments[1][1] * (scaledmoments[3][0] + scaledmoments[1][2]) * (scaledmoments[2][1] + scaledmoments[0][3]);
    	 inv[6] = (3 * scaledmoments[2][1] - scaledmoments[0][3]) * (scaledmoments[3][0] + scaledmoments[1][2]) * (Math.pow(scaledmoments[3][0] + scaledmoments[1][2], 2) - 3 * Math.pow(scaledmoments[2][1] + scaledmoments[0][3], 2)) -
				  (scaledmoments[3][0] - 3 * scaledmoments[1][2]) * (scaledmoments[2][1] + scaledmoments[0][3]) * (3 * Math.pow(scaledmoments[3][0] + scaledmoments[1][2], 2) - Math.pow(scaledmoments[2][1] + scaledmoments[0][3], 2));                       
    	 inv[7] = scaledmoments[1][1] * (Math.pow(scaledmoments[3][0] + scaledmoments[1][2], 2) - Math.pow(scaledmoments[0][3] + scaledmoments[2][1], 2)) - (scaledmoments[2][0] - scaledmoments[0][2]) * (scaledmoments[3][0] + scaledmoments[1][2]) * (scaledmoments[0][3] + scaledmoments[2][1]); 
    	 return inv;
     }

}