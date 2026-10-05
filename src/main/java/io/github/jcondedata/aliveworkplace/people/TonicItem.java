package io.github.jcondedata.aliveworkplace.people;

import net.minecraft.world.item.Item;

/**
 * One of our own tonics (ROADMAP 30.15): Miner's Brew, Builder's Tea. What it does is in its tonic file
 * ({@link Tonics}); being one of ours only means a villager it doesn't suit refuses it (and the player keeps it),
 * where an ordinary item a pack made a tonic goes on to its usual use. Never crafted: the alchemist and the chef make
 * them. The tooltip comes from the tonic file too (sent to the players, shown by the client's tooltip hook).
 */
public class TonicItem extends Item {
	public TonicItem(Properties properties) {
		super(properties);
	}
}
