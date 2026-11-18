package io.github.jcondedata.aliveworkplace.compat.create;

import io.github.jcondedata.aliveworkplace.build.TurnedBlockEntities;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.StandIns;
import io.github.jcondedata.aliveworkplace.mechanic.BeltLayer;
import java.util.Optional;

/**
 * The Create integration (Milestone 39). Most of it goes by block ids and needs no Create class; the village trains
 * (39.7a) drive Create's own railway, so they live here and fill {@link CaravanTrains#RAILWAY}; a block entity put up
 * turned (the Mover, a builder) turns its data the way Create's contraptions do ({@link TurnedBlockEntities}, B1137).
 */
public final class CreateCompat {
	/** The Create versions this was tested with: the owner's Fabric port of 6.0.10 (libs/). */
	public static final String TESTED = ">=6.0.10 <6.1";

	private CreateCompat() {
	}

	public static void init() {
		CaravanTrains.RAILWAY.register("create", new CreateTrains());
		TurnedBlockEntities.EXTENSION.register("create", new CreateTurns());
		BeltLayer.EXTENSION.register("create", new CreateBelts()); // B669: a broken belt is laid again whole
		// B843: a deployer a player placed acts for them; one nobody placed answers to Create's own "Deployer" id, nobody
		StandIns.ACTS_FOR.register("create", player -> !(player instanceof DeployerFakePlayer deployer) ? null
			: DeployerFakePlayer.fallbackID.equals(deployer.getUUID()) ? Optional.empty() : Optional.of(deployer.getUUID()));
	}
}
