package calc;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NewImage;
import ij.io.OpenDialog;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

public class ReadImages {
	
	private ImagePlus[] allImages = new ImagePlus[4]; 
	private ColorProcessor[] cp_all = new ColorProcessor[4];
	private ColorProcessor[] Frame = new ColorProcessor[4];
	private String filepath_hf;
	private String[] filepath = new String[3];
	private ImageStack stack;
	private ImagePlus bilder;
	private byte[][] HSB;
	private boolean[] images = new boolean[4];
	
	public ReadImages(String path){
		
		filepath_hf = path;
		
		if (path.toLowerCase().contains("_qff_")){
		filepath[0] = path.replaceFirst("_QFF_", "_PC_"); 
		filepath[1] = path.replaceFirst("_QFF_", "_PE_");
		filepath[2]= path.replaceFirst("_QFF_", "_CHL_");
		}
		else if(path.toLowerCase().contains("_h_")){
			filepath[0] = path.replaceFirst("_H_", "_PC_"); 
			filepath[1] = path.replaceFirst("_H_", "_PE_");
			filepath[2] = path.replaceFirst("_H_", "_CHL_");
		}
		
		allImages[0] = new ImagePlus(filepath_hf);
		
		final Thread[] threads = newThreadArray();  
		  
	        for (int ithread = 0; ithread < threads.length; ithread++) {  
	  
	            // Concurrently run in as many threads as CPUs  
	  
	            threads[ithread] = new Thread() {  
	                          
	                { setPriority(Thread.NORM_PRIORITY); }  
	  
	                public void run() {  
	                
	                	for (int i = 1; i<4; i++){
	                		if (filepath[i-1]!=null)allImages[i] = new ImagePlus(filepath[i-1]);
	                	}
	                	
	                	
	                }};  
	        }
		
	        startAndJoin(threads);  
		
		bilder = NewImage.createRGBImage(filepath_hf,  allImages[0].getWidth(), allImages[0].getHeight(), 4, 0);
		stack = bilder.getStack();

		
		for (int i=0; i<4; i++){
			
			if (allImages[i] != null && allImages[i].getImage() != null){
				cp_all[i] = new ColorProcessor(allImages[i].getImage());
				Frame[i] = (ColorProcessor) stack.getProcessor(i+1);
				Frame[i].insert(cp_all[i], 0, 0);
				images[i] = true; 
			}
			else{
				images[i] = false;
			}
		}
		 System.gc();
		 System.gc();
		 System.gc();
		 System.gc();
		 System.gc();
	}
	
	public boolean[] getBoolean() {
		return images;
	}

	public ImagePlus getImages(){
		return bilder;
	}
	
    private Thread[] newThreadArray() {  
        int n_cpus = Runtime.getRuntime().availableProcessors();  
        return new Thread[n_cpus];  
    } 
    
    public static void startAndJoin(Thread[] threads)  
    {  
        for (int ithread = 0; ithread < threads.length; ++ithread)  
        {  
            threads[ithread].setPriority(Thread.NORM_PRIORITY);  
            threads[ithread].start();  
        }  
  
        try  
        {     
            for (int ithread = 0; ithread < threads.length; ++ithread)  
                threads[ithread].join();  
        } catch (InterruptedException ie)  
        {  
            throw new RuntimeException(ie);  
        }  
    } 


}
