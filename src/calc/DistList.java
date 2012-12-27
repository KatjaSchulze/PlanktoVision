package calc;

public class DistList {

	private double angle; 
	private double distance;
	private int x;
	private int y;
	
	public DistList(double angle, double distance, int x, int y){
		this.angle = angle; 
		this.distance = distance;
		this.x = x;
		this.y = y; 
	}
	
	public double getDist(){
		return this.distance;
	}
	
	public double getAngle(){
		return this.angle; 
	}
	
	public int getx(){
		return this.x;
	}
	
	public int gety(){
		return this.y;
	}
	
	public void setDist(double distance){
		this.distance = distance;
	}
	
}