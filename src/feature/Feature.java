package feature;
public interface Feature {
	public static final int 
		AREA=1<<0,
		WIDTH=1<<1,
		HEIGHT=1<<2,
		AR=1<<3,
		PERI=1<<4,
		FERET=1<<5,
		MINFERET=1<<6,
		MINFERET_PERI=1<<7,
		FERETANG=1<<8,
		MAJOR=1<<9,
		MINOR=1<<10,
		ANG=1<<11,
		CIRC=1<<12,
		SOL=1<<13,
		SKEW=1<<14,
		ROUND=1<<15,
		KURT=1<<16,
		INTDEN=1<<17, 
		DIRECT_HIST=1<<18, 
		ROT_SYM=1<<19, 
		REFL_SYM=1<<20;
}