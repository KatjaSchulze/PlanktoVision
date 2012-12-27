package calc;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.PolygonRoi;
//import ij.gui.NewImage;
//import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.process.ImageProcessor;

import java.awt.Point;
import java.util.Vector;

public class Symmetry {
	private ImagePlus imp;
	private ImageProcessor ip; 
//	private Line line;
	private Roi roi; 
	private double rotsym;
	private  Vector<Double> reflSym = new Vector<Double>();
	private int belowThreshold = 1;
	
	public Symmetry(ImagePlus mask, Roi roi){
		this.imp = mask; 
//		mask.show();
		this.ip = imp.getProcessor();
		roi = new PolygonRoi(roi.getPolygon(), roi.getType());
		roi.setLocation(0, 0);
		this.ip.setRoi(roi);
//		IJ.wait(1999);
	}
	
	public void run(){
		// get center of mass
		double xm = ip.getStatistics().xCenterOfMass;
	    double ym = ip.getStatistics().yCenterOfMass;
	    
	    // get start point
	    int startx = 0;
	    int starty = 0; 
	    done: for (int i = 0; i<ip.getWidth(); i++){
			for (int j = 0; j< ip.getHeight(); j++){
				if (ip.getPixel(i, j) == 0 ){
//					ip.putPixel(i, j, 100);
					startx = i; 
					starty = j;
					break done;
				}
			}
		}
	    
	    // get angle and distance of contour points
	    Vector<DistList> vd = new Vector<DistList>();
	    vd = this.getContour(startx, starty, xm, ym);
	    
	    //normalize distance
	    double max = Integer.MIN_VALUE;
		for (int i = 0; i<vd.size(); i++){
			if(vd.get(i).getDist()> max) max = vd.get(i).getDist();
		}
//		System.out.println(max);
		for (int i = 0; i<vd.size(); i++){
			vd.get(i).setDist(vd.get(i).getDist()/max);
		}
		
	    // seperate line in parts and calculate mean distance and mean angle
		
		Vector<Double> dist = new Vector<Double>();
		Vector<Double> angle = new Vector<Double>();
		int segangle = 1;
	    int color = 10;
	    int count = 1;
	    double mean = 0;
	    double meana = 0;
	    for (double i = 0; i<=359-segangle+1; i = i+segangle){
	    	mean = 0;
	    	meana = 0;
	    	count = 0;
	    	for (int j = 0; j<vd.size(); j++){
	    		if (vd.get(j).getAngle()>=i && vd.get(j).getAngle()<i+segangle){
//	    			ip.putPixel(vd.get(j).getx(), vd.get(j).gety(), color);
//	    			imp.updateAndDraw();
//	    			IJ.wait(5);
		    		mean = mean + vd.get(j).getDist();
		    		meana = meana + vd.get(j).getAngle();
		    		count++;
	    		}
	    	}
	    	if(count > 0){
		    	dist.add(mean/count);
		    	angle.add(meana/count);
//		    	System.out.println(i+" "+mean/count+" "+meana/count);
		    	if (color == 10)color = 240;
		    	else if (color == 240)color =10;
	    	}
	    	else{
//	    		dist.add(-1.0);
//	    		angle.add(i);
//	    		System.out.println(i+" "+-1.0+" "+i);
	    	}
//	    	IJ.wait(100);
	    	
	    }
	    
	    
	    
		Vector<Double> rs = new Vector<Double>();
		Vector<Double> ars = new Vector<Double>();
	    //reflectional symmetry
	    double a  = 0 ; 
	    for(int i = 0; i < dist.size()/2; i++ ){
	    	a = 0 ; 
	    	for (int j = 0; j<dist.size()/2;j++){
	    		if (j<=i){
	    				a = a + Math.abs(dist.get(i+j) - dist.get(i-j) );
	    		}
	    		else{
	    				a = a + Math.abs(dist.get(i+j) - dist.get(dist.size()-j+i));
	    		}
		    }
	    	ars.add(angle.get(i));
	    	rs.add(a/(dist.size()/2));
	    }
	    
	    
	    // threshold and find min
	    double min = Double.MAX_VALUE;
	    double mangle = 0.0; 
	    boolean findmin = false; 
	    boolean alwaysm = true;
	   
	    for(int i = 0; i < rs.size(); i++ ){
	    	// angle within threshold
//	    	System.out.println(ars.get(i)+"  "+rs.get(i));
		    	if (rs.get(i) <= 0.06 ){
		    		if (min > rs.get(i)){ min = rs.get(i); mangle = ars.get(i); findmin = true; }
		    	}
		    	else if (findmin == true){ 
//		    		System.out.println ("Min Distance: "+min+" Min Angle: "+mangle); 
		    		findmin = false; 
		    		min = Double.MAX_VALUE; 
		    		alwaysm = false; 
		    		
		    		reflSym.add(mangle);
		    	}
		    	else if (rs.get(i) > 0.06){
//		    		System.out.println("test");
		    		this.belowThreshold = 0; 
		    	}
		    	
		    	if (i ==rs.size()-1 && findmin == true){
//		    		System.out.println ("Min Distance: "+min+" Min Angle: "+mangle);
		    		reflSym.add(mangle);
		    	}
	    }
//	    if (alwaysm == true) System.out.println(" Alle werte unter schwellwert");
	    
	    
	    //rotational Symmetry of degree 2
	    double b = 0; 
	    for (int i = 0; i<dist.size()/2; i++){
	    	b = b + Math.abs(dist.get(i) - dist.get(i+dist.size()/2));
	    }
//	    System.out.println("Rotational Symmetry (deg 2) "+b/(dist.size()/2));
	    this.rotsym = b/(dist.size()/2);
	}
	
	//code nach http://www.miszalok.de/Lectures/L08_ComputerVision/CrackCode/CrackCode_d.htm#a1
	private Vector<DistList> getContour(int startx, int starty, double xm, double ym) {
		Vector<DistList> vd = new Vector<DistList>();
	    Point p = new Point(startx, starty+1);
	    int height = imp.getHeight();
	    int width = imp.getWidth();
		String cracks = new String();
//		cracks = cracks+"s";
		char last_crack = 's';
		
		do
		{ switch ( last_crack )
			{ case 'e': if (p.x==width){last_crack = 'n'; break;} 
						if (p.y<height && ip.getPixel(p.x, p.y) == 0)	{vd.add(calcAngle(p.x, p.y, xm, ym, imp)); last_crack = 's'; break;}
						if (ip.getPixel(p.x, p.y-1) == 0)				{vd.add(calcAngle(p.x, p.y-1,  xm, ym, imp)); last_crack = 'e'; break;} 
						last_crack = 'n'; break;
			  case 's': if (p.y==height) { last_crack = 'e'; break;}
			  			if (p.x>0  && ip.getPixel(p.x-1, p.y) == 0)	{vd.add(calcAngle(p.x-1, p.y, xm, ym, imp)); last_crack = 'w'; break;}
			  			if (ip.getPixel(p.x, p.y) == 0) 				{vd.add(calcAngle(p.x, p.y, xm, ym, imp)); last_crack = 's'; break;}
			  			last_crack = 'e'; break; 
			  case 'w': if (p.x==0) { last_crack = 's'; break;} 
			  			if (p.y>0 && ip.getPixel(p.x-1, p.y-1) == 0) 	{vd.add(calcAngle(p.x-1, p.y-1, xm, ym, imp)); last_crack = 'n'; break;}
			  			if (ip.getPixel(p.x-1, p.y) == 0) 				{vd.add(calcAngle(p.x-1, p.y, xm, ym, imp));	last_crack = 'w'; break;}
			  			last_crack = 's'; break;
			  case 'n': if (p.y==0 ) { last_crack = 'w'; break;}
			  			if (p.x<width && ip.getPixel(p.x, p.y-1) == 0) {vd.add(calcAngle(p.x, p.y-1, xm, ym, imp));	last_crack = 'e'; break;}  
			  			if (ip.getPixel(p.x-1, p.y-1) == 0) 			{vd.add(calcAngle(p.x-1, p.y-1, xm, ym, imp)); last_crack = 'n'; break;} 
			  			last_crack = 'w'; break;
			}
		  if (last_crack == 'e'){cracks = cracks+"e"; p.x++;continue;}
		  if (last_crack == 's'){cracks = cracks+"s"; p.y++; continue;}
		  if (last_crack == 'w'){cracks = cracks+"w"; p.x--;continue;}
		  if (last_crack == 'n'){cracks = cracks+"n"; p.y--;continue;}
		} while ( p.x != startx || p.y != starty );
		
		
		return vd;
	}
	
	private DistList calcAngle(int x, int y, double xm, double ym, ImagePlus cimp) {
		Line line = new Line(xm, ym, x, y);
		double angle = line.getAngle(line.x1, line.y1, line.x2, line.y2);
		
		if (angle <0)angle = angle+360;
		double distance = line.getLength();
		DistList d = new DistList(angle, distance, x, y);
		cimp.setRoi(line);
		return d; 
	}
	
	public double getRot2Sym(){
		return this.rotsym; 
	}
	
	public Vector<Double> getReflSym(){
		return this.reflSym;
	}
	
	public int belowThreshold(){
		return belowThreshold; 
	}
	
}
