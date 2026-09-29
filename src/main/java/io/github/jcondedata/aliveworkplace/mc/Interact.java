package io.github.jcondedata.aliveworkplace.mc;

import net.minecraft.world.InteractionResult;

/** Results of using things. Change: {@code InteractionResult} becomes an interface without {@code sidedSuccess} in 1.21.2. */
public final class Interact {
	/** It worked (the arm swings on the client). */
	public static InteractionResult success(boolean clientSide) {
		return InteractionResult.sidedSuccess(clientSide);
	}

	private Interact() {
	}
}
