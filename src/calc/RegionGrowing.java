package calc;

import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.NewImage;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
//import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
//import ij.plugin.filter.BackgroundSubtracter;
//import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
//import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;


public class RegionGrowing {

	private int height, width; 

	private Integer[] keys = new Integer[0];
	private ImageProcessor ipb, ipGrow;

	private ImagePlus impGrow, hsb;
	private HashSet<Point> A = new HashSet<Point>();
	private Vector<Integer> I = new Vector<Integer>();
	
	private HashMap<Integer,HashSet <Point >> Nmap = new HashMap<Integer,HashSet<Point>> ();
	private RoiManager rm ; 
	double stddev;
	private int modekey; 
	
	
	public RegionGrowing(ImagePlus hsb){
		this.hsb = hsb;
		this.height = hsb.getHeight();
		this.width = hsb.getWidth();
		this.ipb = hsb.getStack().getProcessor(3);	
		this.hsb.setSlice(3);
	}
	
	public void initialize(){
		HashMap<Integer,HashSet <PolygonRoi >> RoiSort = new HashMap<Integer,HashSet<PolygonRoi>> ();
		HashMap<Integer, Integer> Area = new HashMap<Integer, Integer>();
		Wand wand;
		PolygonRoi roi = null;
		ByteProcessor ipSeg;
		
		// find edges
		ImagePlus imp = new ImagePlus("edge", ipb);
		ipb.findEdges();
		
		IJ.run(imp, "Enhance Contrast", "saturated=0.4 normalize");
		ipb.invert();
		
		// find maxima and segment image with Watershed algorithm
		IJ.run(imp, "Find Maxima...", "noise=4 output=[Segmented Particles]");
		ipSeg = (ByteProcessor) WindowManager.getImage("edge Segmented").getProcessor();
		WindowManager.getImage("edge Segmented").close();
		int mode = 0;
		
		// create image for grwoing;
		this.impGrow = NewImage.createByteImage("Shapes", this.width, this.height, 1, NewImage.FILL_BLACK);
		this.ipGrow = impGrow.getProcessor();
		ipGrow.setColor(255);
		
		// wand segmented parts and determine mode
		int n = 0; 
		int m = 0; 
		for (int y = m; y < height; y++){
			for (int x = n; x < width; x++){
				if (ipSeg.get(x, y) == 255){ //local minimum
					wand = new Wand(ipSeg);
					wand.autoOutline(x, y, 255 , 255, 8);
					roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, 2);
					ipb.setRoi(roi);
					ipSeg.fill(roi);
					
					mode = ipb.getStatistics().mode;
					if (RoiSort.containsKey(mode)){
						Area.put(mode, new Integer(Area.get(mode).intValue() + (int)ipb.getStatistics().area ));
						RoiSort.get(mode).add((PolygonRoi) roi.clone());
					}
					else{
						RoiSort.put(mode,new HashSet<PolygonRoi>());
						Area.put(mode, new Integer((int)ipb.getStatistics().area ));
						RoiSort.get(mode).add((PolygonRoi) roi.clone());
					}				
					
					m = y;
				}
			}
	    }
		
		
		// find mode with greatest area
		int roiamount = Integer.MIN_VALUE; 
		modekey = -1;
		keys = Area.keySet().toArray(new Integer[0]);
		for (int i = 0; i<keys.length; i++){
			if (Area.get(keys[i])>roiamount) {roiamount = Area.get(keys[i]); modekey = keys[i];}
		}
		// initialize pixel in mode area for region growing
		Roi[] rois = RoiSort.get(modekey).toArray(new PolygonRoi[0]);
		stddev = 0;
		Rectangle r = null; 
		int heightroi = 0;
		int widthroi = 0; 
		int yr = 0;
		int xr = 0;
		ipSeg.setColor(100);
		for (int i = 0; i< rois.length; i++){
			ipb.setRoi(rois[i]);
			stddev = stddev + ipb.getStatistics().stdDev;
			r = rois[i].getBounds();
			xr = r.x; 
			yr = r.y;
			heightroi = r.height;
			widthroi = r.width; 
			ipSeg.fill(rois[i]);
			for (int x = xr; x<xr+widthroi ; x++){
				for (int y = yr; y<yr+heightroi; y++){
					if(ipb.get(x, y) == modekey && ipSeg.getPixel(x, y) == 100){ // && rois[i].contains(x, y)
						A.add(new Point(x, y));
						ipGrow.putPixel(x, y, 255);
					}
				}
			}
		}
		
		stddev = stddev/rois.length;
		Area.clear();
		RoiSort.clear();
	}
	
	public void start(){
		int step = 1; 
		boolean a = true; 
		
		do {
			switch (step){
			
			case 1:
					if (A.isEmpty()) {
						IJ.run(impGrow,"Median...", "radius=2");
						a = false; 
						int options =  2048+1024+ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
						ParticleAnalyzer particle = new ParticleAnalyzer(options, Analyzer.getMeasurements(), null , 100, 100000000, 0, 1);
						particle.analyze(impGrow);
						if (WindowManager.getFrame("ROI Manager") != null ){
							Frame frame = WindowManager.getFrame("ROI Manager");
							frame.setLocation(1600, 100);
							rm = (RoiManager)frame;
						}
						hsb.close();
						I.clear();
						A.clear();
						break;
					}
					
					this.grow();
			case 2:	
					this.updateNA();
					step = 1;
			}
		}while ( a == true);
	}
	


	private void grow() {
		
		int dist = 0; 
		Point[] Aa = A.toArray(new Point[0]);
		Point p ;
		int pix = 0; 
		int pos = 0; 
		
		for (int i = 0; i<Aa.length; i++){
			p = new Point(Aa[i]);
			I.add(ipb.get(p.x, p.y));
			ipGrow.putPixel(p.x, p.y, 255);

			pos = 1;
			done1: while (!(pos==9) ){ 
				
			 switch ( pos )
			  { 
				case 1: p.y--;	if (p.y>=0 ) break; 						else pos++;
				case 2: p.x--; 	if (p.y>=0 && p.x>=0 ) break; 				else pos++;
				case 3: p.y++;  if (p.x>=0 && p.y<=height-1) break;			else pos++;
				case 4: p.y++; 	if (p.x>=0 && p.y<=height-1) break; 		else pos++;
				case 5: p.x++; 	if (p.x<=width-1 && p.y<=height-1) break; 	else pos++;
				case 6: p.x++;	if (p.x<=width-1 && p.y<=height-1) break; 	else pos++;
				case 7: p.y--; 	if (p.x<=width-1 && p.y>=0)  break; 		else pos++;
				case 8: p.y--; 	if (p.x<=width-1 && p.y>=0)  break; 		else break done1;
			  }
					
				if ( ipGrow.get(p.x, p.y) !=255 && !A.contains(p)  ){ // !labels[p.x][p.y].contains(labelnr)
						pix = ipb.get(p.x, p.y);
						dist = Math.abs(modekey - pix);
						if (Nmap.containsKey(dist)){
							Nmap.get(dist).add(new Point(p));
						}
						else{
							Nmap.put(dist,new HashSet<Point>());
							Nmap.get(dist).add(new Point(p));
						}
				}
				pos++;
			}
		}	
		modekey = RegionGrowing.mode(I);
	}
	

	private void updateNA() {
	A.clear();
	keys = Nmap.keySet().toArray(new Integer[0]);
	java.util.Arrays.sort( keys );
	for (int i = 0; i < keys.length; i++){
		
		if ( keys[i] <= stddev*10){
			A.addAll(Nmap.get(keys[i]));
			Nmap.remove(keys[i]);
		}
	}
	
	}
	
	public void closeWindows(){
		this.impGrow.close();
		this.hsb.close();
	}
	
	//================================================= mean
	public static double mean(Vector<Integer>  p) {
		Integer[] Ia = p.toArray(new Integer[0]);
	    double sum = 0;  // sum of all the elements
	    for (int i=0; i<Ia.length; i++) {
	        sum += Ia[i];
	    }
	    return sum / Ia.length;
	}//end method mean
	
	static double stdDev(Vector<Integer> p, double mean ){
		Integer[] Ia = p.toArray(new Integer[0]);
		double s = 0; 
		
		for (int i = 0; i<Ia.length; i++){
			s = s + Math.pow(Ia[i] - mean, 2.0); 
		}
		s = Math.sqrt(s/(Ia.length-1));
		
		return s;
	} 
	
	private static int mode(Vector<Integer> I) {
		Integer[] amount = new Integer[256];
		Integer[] Ia = I.toArray(new Integer[0]);
		int high = 0;
		
		for (int i = 0; i<amount.length; i++){
			amount[i]=0;
		}

		for (int i = 0; i<Ia.length; i++){
			amount[Ia[i]]++;
		}
		
		for (int i = 1; i<amount.length; i++){
			if (amount[i] > amount[high]){ high = i;} 
		}
		return high;
		
		
	}

	public RoiManager getRM() {
		return rm;
		
	}
	
	
}
