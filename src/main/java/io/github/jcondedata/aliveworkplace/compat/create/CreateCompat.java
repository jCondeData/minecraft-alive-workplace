package io.github.jcondedata.aliveworkplace.compat.create;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;

/**
 * The Create integration (Milestone 39). Most of it goes by block ids and needs no Create class; the village trains
 * (39.7a) drive Create's own railway, so they live here and fill {@link CaravanTrains#RAILWAY}.
 */
public final class CreateCompat {
	/** The Create versions this was tested with: the owner's Fabric port of 6.0.10 (libs/). */
	public static final String TESTED = ">=6.0.10 <6.1";

	private CreateCompat() {
	}

	public static void init() {
		CaravanTrains.RAILWAY.register("create", new CreateTrains());
	}
}
