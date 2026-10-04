package io.github.jcondedata.aliveworkplace.legend;

/**
 * One of a Legend's powers, read from its file ({@link Powers#parse}). The shared ones ({@link PacePower},
 * {@link MoodPower}) are read where they act; each Legend item adds its named power.
 */
public interface Power {
	String type();
}
