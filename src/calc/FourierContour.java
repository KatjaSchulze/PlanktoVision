package calc;

import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import ij.measure.ResultsTable;
import ij.plugin.filter.*;
import java.awt.image.*;
import java.lang.*;
import java.text.NumberFormat;
import java.util.*;
import java.io.*;

/**
 *  Description of the Class
 *
 * @author     thomas.boudier@snv.jussieu.fr
 * @created    11 mars 2004
 */
public class FourierContour {
	ImagePlus imp;
    ResultsTable rt; 
    Roi roi;
	/**
	 *  setup
	 *
	 * @param  arg  arguments
	 * @param  imp  image plus
	 * @return      setup
	 */
//	public int setup(String arg, ImagePlus imp) {
//		this.imp = imp;
//		return DOES_ALL + ROI_REQUIRED;
//	}
	
	public FourierContour(ImagePlus imp, ResultsTable rt, Roi r) {
		this.imp = imp;
		this.imp.getProcessor().invert();
		this.rt = rt;
		this.roi = r;
//		this kval = ;
//		this.imp.show();
//		this.imp.setRoi(r);
//		this.sizes = boxes;
//		return DOES_8G+NO_CHANGES;
	}

	/**
	 *  Main processing method for the Fourier_ object
	 *
	 * @param  ip  image
	 */
	public void run() {
		ImageProcessor ip = this.imp.getProcessor();
		int NbPoints;
		int four = 5;
		double cou;
		boolean kval = true;

		int largeur = ip.getWidth();
		int hauteur = ip.getHeight();

		Fourier fourier = new Fourier();
		fourier.Init(roi);
		
		ImageProcessor res = ip.createProcessor(largeur, hauteur);
		res.insert(ip, 0, 0);
//		GenericDialog gd = new GenericDialog("Fourier", IJ.getInstance());
//
//		if (gd.wasCanceled()) {
//			return;
//		}

		Roi proi = roi;
		if (fourier.closed()) {
			//Calcul de Fourier
//			gd.addNumericField("Fourier descriptors:", four, 0);
//			gd.addCheckbox("Save curvature values", kval);
//			gd.showDialog();
			four = 5; // anzahl der Deskriptoren
			kval = false; // save curvature
			if (four > 0) {
				fourier.computeFourier(four);
				proi = fourier.drawFourier(res, four);
			}
		} else {
			IJ.write("the Roi must be closed");
		}

		NbPoints = fourier.getNbPoints();

////		ImagePlus plus = new ImagePlus("Fourier", res);
////		plus.setRoi(proi);
////		plus.show();

		//Kurve berechnen 
		double[] xc = new double[NbPoints];
		double[] yc = new double[NbPoints];

		for (int i = 0; i < NbPoints; i++) {
			xc[i] = i;
			cou = fourier.courbure(i, 5);
			yc[i] = cou;
		}

////		PlotWindow pw = new PlotWindow("Curvature values", "Point", "Curv.", xc, yc);
////		pw.setColor(Color.BLUE);
////		pw.draw();

		fourier.displayValues(four, this.rt);

//		if (kval) {
//			try {
//				BufferedWriter out = new BufferedWriter(new FileWriter("curvature.txt"));
//				out.write("nb\tX\tY\tCurv.");
//				for (int i = 0; i < NbPoints; i++) {
//					out.write("\n" + i + "\t" + fourier.getXPoint(i) + "\t" + fourier.getXPoint(i) + "\t" + yc[i]);
//				}
//				out.close();
//			} catch (IOException e) {}
//			;
//		}
	}

}

/**
 *  point 2D class
 *
 * @author     thomas.boudier@snv.jussieu.fr
 * @created    11 mars 2004
 */
class Point2d {


	double x;
	double y;


	/**
	 *  Constructor for the Point2d object
	 */
	public Point2d() {
		x = 0.0;
		y = 0.0;
	}

}

/**
 *  main fourier class
 *
 * @author     thomas.boudier@snv.jussieu.fr
 * @created    11 mars 2004
 */
class Fourier {


	double ax[], ay[], bx[], by[];
	Point2d points[];
	int NPT;
	boolean closed;
	int NMAX = 50000;


	/**
	 *  Constructor for the Fourier object
	 */
	public Fourier() { }


	/**
	 *  Gets the nbPoints attribute of the Fourier object
	 *
	 * @return    The nbPoints value
	 */
	public int getNbPoints() {
		return NPT;
	}


	/**
	 *  Gets the xPoint attribute of the Fourier object
	 *
	 * @param  i  Description of the Parameter
	 * @return    The xPoint value
	 */
	public double getXPoint(int i) {
		return points[i].x;
	}


	/**
	 *  Gets the yPoint attribute of the Fourier object
	 *
	 * @param  i  Description of the Parameter
	 * @return    The yPoint value
	 */
	public double getYPoint(int i) {
		return points[i].y;
	}


	/**
	 *  roi is closed
	 *
	 * @return    closed ?
	 */
	public boolean closed() {
		return closed;
	}


	/**
	 *  initialisation of the fourier points
	 *
	 * @param  R  Description of the Parameter
	 */
	public void Init(Roi R) {
		Double pos;
		double Rx;
		double Ry;
		int i = 1;
		double a;
		NPT = 0;

		points = new Point2d[NMAX];

		for (i = 0; i < NMAX; i++) {
			points[i] = new Point2d();
		}

		if ((R.getType() == Roi.OVAL) || (R.getType() == Roi.RECTANGLE)) {
			closed = true;
			Rectangle Rect = R.getBoundingRect();
			int xc = Rect.x + Rect.width / 2;
			int yc = Rect.y + Rect.height / 2;
			Rx = Rect.width / 2;
			Ry = Rect.height / 2;
			double theta = 2.0 / (double) ((Rx + Ry) / 2);

			i = 0;
			for (a = 0.0; a < 2 * Math.PI; a += theta) {
				points[i].x = (int) (xc + Rx * Math.cos(a));
				points[i].y = (int) (yc + Ry * Math.sin(a));
				i++;
			}

			NPT = i;
		} // Rectangle
		else if (R.getType() == Roi.LINE) {
			closed = false;
			Line l = (Line) R;
			Rx = (l.x2 - l.x1);
			Ry = (l.y2 - l.y1);
			a = Math.sqrt(Rx * Rx + Ry * Ry);
			Rx /= a;
			Ry /= a;
			int ind = 1;
			for (i = 0; i <= l.getLength(); i++) {
				points[ind].x = l.x1 + Rx * i;
				points[ind].y = l.y1 + Ry * i;
				ind++;

			}
			NPT = ind;
		} // Line
		else if (R.getType() == Roi.POLYGON) {
			closed = true;
			PolygonRoi pl = (PolygonRoi) R;
			closed = true;
			PolygonRoi p = (PolygonRoi) R;
			Rectangle rectBound = p.getBoundingRect();
			Point l = rectBound.getLocation();
			int NBPT = p.getNCoordinates();
			int pointsX[] = p.getXCoordinates();
			int pointsY[] = p.getYCoordinates();
			for (i = 0; i < NBPT; i++) {
				points[i].x = pointsX[i] + l.getX();
				points[i].y = pointsY[i] + l.getY();
			}
			NPT = i;
		} else if (R.getType() == Roi.FREEROI) {
			closed = true;
			PolygonRoi p = (PolygonRoi) (R);
			Rectangle rectBound = p.getBoundingRect();
			int NBPT = p.getNCoordinates();
			int pointsX[] = p.getXCoordinates();
			int pointsY[] = p.getYCoordinates();
			for (i = 0; i < NBPT; i++) {
				points[i].x = pointsX[i] + rectBound.x;
				points[i].y = pointsY[i] + rectBound.y;
			}
			NPT = i;
		} // PolyLine
		else {
			IJ.write("Selection type not supported");
		}
	}


	/**
	 *  curvature computation
	 *
	 * @param  iref   number of the point
	 * @param  scale  scale for curvature computation
	 * @return        curvature value
	 */
	public double courbure(int iref, int scale) {
		double dl;
		double da;
		double ares;
		double a;
		Point2d U;
		Point2d V;
		Point2d W;
		Point2d pos;
		Point2d norm;
		int i = iref;

		U = new Point2d();
		V = new Point2d();
		W = new Point2d();
		pos = new Point2d();
		norm = new Point2d();

		if ((iref > scale) && (iref < NPT - scale)) {
			U.x = points[i - scale].x - points[i].x;
			U.y = points[i - scale].y - points[i].y;
			V.x = points[i].x - points[i + scale].x;
			V.y = points[i].y - points[i + scale].y;
			W.x = points[i - scale].x - points[i + scale].x;
			W.y = points[i - scale].y - points[i + scale].y;
			pos.x = (points[i - scale].x + points[i].x + points[i + scale].x) / 3;
			pos.y = (points[i - scale].y + points[i].y + points[i + scale].y) / 3;
		}
		if ((iref <= scale) && (closed)) {
			U.x = points[NPT - 1 + i - scale].x - points[i].x;
			U.y = points[NPT - 1 + i - scale].y - points[i].y;
			V.x = points[i].x - points[i + scale].x;
			V.y = points[i].y - points[i + scale].y;
			W.x = points[NPT - 1 + i - scale].x - points[i + scale].x;
			W.y = points[NPT - 1 + i - scale].y - points[i + scale].y;
			pos.x = (points[NPT - 1 + i - scale].x + points[i].x + points[i + scale].x) / 3;
			pos.y = (points[NPT - 1 + i - scale].y + points[i].y + points[i + scale].y) / 3;
		}
		if ((iref > NPT - scale - 1) && (closed)) {
			U.x = points[i - scale].x - points[i].x;
			U.y = points[i - scale].y - points[i].y;
			V.x = points[i].x - points[(i + scale) % (NPT - 1)].x;
			V.y = points[i].y - points[(i + scale) % (NPT - 1)].y;
			W.x = points[i - scale].x - points[(i + scale) % (NPT - 1)].x;
			W.y = points[i - scale].y - points[(i + scale) % (NPT - 1)].y;
			pos.x = (points[i - scale].x + points[i].x + points[(i + scale) % (NPT - 1)].x) / 3;
			pos.y = (points[i - scale].y + points[i].y + points[(i + scale) % (NPT - 1)].y) / 3;
		}
		double l = Math.sqrt(W.x * W.x + W.y * W.y);
		da = ((U.x * V.x + U.y * V.y) / ((Math.sqrt(U.x * U.x + U.y * U.y) * (Math.sqrt(V.x * V.x + V.y * V.y)))));
		a = Math.acos(da);
		if (!inside(pos)) {
			return (-1.0 * a / l);
		} else {
			return (a / l);
		}
	}


	/**
	 *  Fourier descriptor X coeff a
	 *
	 * @param  k  number of fourier descriptor
	 * @return    the fourier value
	 */
	public double FourierDXa(int k) {
		double som = 0.0;
		for (int i = 0; i < NPT; i++) {
			som += points[i].x * Math.cos(2 * k * Math.PI * i / NPT);
		}
		return (som * 2 / NPT);
	}


	/**
	 *  Fourier descriptor X coeff b
	 *
	 * @param  k  number of fourier descriptor
	 * @return    the fourier value
	 */
	public double FourierDXb(int k) {
		double som = 0.0;
		for (int i = 0; i < NPT; i++) {
			som += points[i].x * Math.sin(2 * k * Math.PI * i / NPT);
		}
		return (som * 2 / NPT);
	}


	/**
	 *  Fourier descriptor Y coeff a
	 *
	 * @param  k  number of fourier descriptor
	 * @return    the fourier value
	 */
	public double FourierDYa(int k) {
		double som = 0.0;
		for (int i = 0; i < NPT; i++) {
			som += points[i].y * Math.cos(2 * k * Math.PI * i / NPT);
		}
		return (som * 2 / NPT);
	}


	/**
	 *  Fourier descriptor Y coeff b
	 *
	 * @param  k  number of fourier descriptor
	 * @return    the fourier value
	 */
	public double FourierDYb(int k) {
		double som = 0.0;
		for (int i = 0; i < NPT; i++) {
			som += points[i].y * Math.sin(2 * k * Math.PI * i / NPT);
		}
		return (som * 2 / NPT);
	}


	/**
	 *  Description of the Method
	 *
	 * @param  kmax  Description of the Parameter
	 */
	public void computeFourier(int kmax) {
		ax = new double[kmax + 1];
		bx = new double[kmax + 1];
		ay = new double[kmax + 1];
		by = new double[kmax + 1];
		for (int i = 0; i <= kmax; i++) {
			ax[i] = FourierDXa(i);
			bx[i] = FourierDXb(i);
			ay[i] = FourierDYa(i);
			by[i] = FourierDYb(i);
		}
	}


	/**
	 *  Description of the Method
	 *
	 * @param  kmax  Description of the Parameter
	 */
	public void displayValues(int kmax, ResultsTable rt) {
//		ResultsTable rt = ResultsTable.getResultsTable();
//		rt.reset();
		
//		int row = 0;
//		rt.setHeading(1, "ax");
//		rt.setHeading(2, "ay");
//		rt.setHeading(3, "bx");
//		rt.setHeading(4, "by");
		for (int i = 0; i <= kmax; i++) {
//			rt.incrementCounter();
			rt.addValue("ax"+i, ax[i]);
			rt.addValue("ay"+i, ay[i]);
			rt.addValue("bx"+i, bx[i]);
			rt.addValue("by"+i, by[i]);
		}
//		rt.show("Results");
	}


	/**
	 *  draw fourier dexcriptors curve
	 *
	 * @param  A     image
	 * @param  kmax  number of fourier desciptors
	 * @return       Description of the Return Value
	 */
	public Roi drawFourier(ImageProcessor A, int kmax) {
		double posx;
		double posy;
		double max = A.getMax();

		int tempx[] = new int[NPT];
		int tempy[] = new int[NPT];

		for (int l = 0; l < NPT; l++) {
			posx = ax[0] / 2.0;
			posy = ay[0] / 2.0;
			for (int k = 1; k <= kmax; k++) {
				posx += ax[k] * Math.cos(2 * Math.PI * k * l / NPT) + bx[k] * Math.sin(2 * Math.PI * k * l / NPT);
				posy += ay[k] * Math.cos(2 * Math.PI * k * l / NPT) + by[k] * Math.sin(2 * Math.PI * k * l / NPT);
			}
			tempx[l] = (int) Math.round(posx);
			tempy[l] = (int) Math.round(posy);
		}
		PolygonRoi proi = new PolygonRoi(tempx, tempy, NPT, Roi.FREEROI);

		return proi;
	}


	/**
	 *  check if point inside the roi
	 *
	 * @param  pos  point
	 * @return      inside ?
	 */
	boolean inside(Point2d pos) {
		int count;
		int i;
		double bden;
		double bnum;
		double bres;
		double ares;
		double lnorm;
		Point2d norm = new Point2d();
		Point2d ref = new Point2d();

		ref.x = 0.0;
		ref.y = 0.0;
		norm.x = ref.x - pos.x;
		norm.y = ref.y - pos.y;
		lnorm = Math.sqrt(norm.x * norm.x + norm.y * norm.y);
		norm.x /= lnorm;
		norm.y /= lnorm;

		count = 0;
		for (i = 1; i < NPT - 1; i++) {
			bden = (-norm.x * points[i + 1].y + norm.x * points[i].y + norm.y * points[i + 1].x - norm.y * points[i].x);
			bnum = (-norm.x * pos.y + norm.x * points[i].y + norm.y * pos.x - norm.y * points[i].x);
			if (bden != 0) {
				bres = (bnum / bden);
			} else {
				bres = 5.0;
			}
			if ((bres >= 0.0) && (bres <= 1.0)) {
				ares = -(-points[i + 1].y * pos.x + points[i + 1].y * points[i].x +
						points[i].y * pos.x + pos.y * points[i + 1].x - pos.y * points[i].x
						 - points[i].y * points[i + 1].x) / (-norm.x * points[i + 1].y
						 + norm.x * points[i].y + norm.y * points[i + 1].x - norm.y * points[i].x);
				if ((ares > 0.0) && (ares < lnorm)) {
					count++;
				}
			}
		}
		return (count % 2 == 1);
	}

}

