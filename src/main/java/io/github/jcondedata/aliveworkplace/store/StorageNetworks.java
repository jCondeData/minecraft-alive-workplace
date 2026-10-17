package io.github.jcondedata.aliveworkplace.store;

import io.github.jcondedata.aliveworkplace.Expansions;

/**
 * Modded storage networks as one store (ROADMAP 37.9 and 37.10, design note {@code docs/design/M37.md}): a
 * Sophisticated Storage controller or a Tom's Simple Storage inventory connector in the Storehouse's area stands for
 * every inventory it links, read and filled through its own item storage. Config {@code moddedStorageNetworks}, gated by
 * {@link Expansions#M37}; off, every linked chest is its own store and the network blocks are passed over, as before.
 */
public final class StorageNetworks {
	/** Controllers and connectors count as one store (config {@code moddedStorageNetworks}). */
	public static volatile boolean ENABLED = Expansions.on(Expansions.M37);

	private StorageNetworks() {
	}
}
