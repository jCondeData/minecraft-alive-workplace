package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.gametest.framework.GameTestServer;

/**
 * Test-server shims. Architectury fires its "server starting" event only for dedicated and integrated servers, not the
 * game test server, so mods that set up on it (Mega Showdown fills its datapack registries there) would crash; here it's
 * fired for the test server too. By reflection: Architectury is only on the test runtime classpath.
 */
public class CompatTestSetup implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (!(server instanceof GameTestServer)) {
				return;
			}
			try {
				Class<?> lifecycle = Class.forName("dev.architectury.event.events.common.LifecycleEvent");
				Object event = lifecycle.getField("SERVER_STARTING").get(null);
				Object invoker = Class.forName("dev.architectury.event.Event").getMethod("invoker").invoke(event);
				Class<?> state = Class.forName("dev.architectury.event.events.common.LifecycleEvent$ServerState");
				// A generic listener (InstanceState<MinecraftServer>): stateChanged(Object) after erasure.
				java.util.Arrays.stream(state.getMethods()).filter(m -> m.getName().equals("stateChanged")).findFirst()
					.orElseThrow(NoSuchMethodException::new).invoke(invoker, server);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("could not fire Architectury's server starting event", e);
			}
		});
	}
}
