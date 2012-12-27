package feature;

public interface FeatureHSB {
	public static final int
		HU_HIST = 1<<0,
		HU_MIN	= 1<<1,
		HU_MAX	= 1<<2,
		HU_MEAN	= 1<<3,
		HU_MODE	= 1<<4,
		HU_STDDEV= 1<<5,
		SAT_HIST= 1<<6,
		SAT_MIN	= 1<<7,
		SAT_MAX	= 1<<8,
		SAT_MEAN= 1<<9,
		SAT_MODE= 1<<10,
		SAT_STDDEV= 1<<11,
		BRIGHT_HIST= 1<<12,
		BRIGHT_MIN= 1<<13,
		BRIGHT_MAX= 1<<14,
		BRIGHT_MEAN= 1<<15,
		BRIGHT_MODE= 1<<16,
		BRIGHT_STDDEV= 1<<17;
}