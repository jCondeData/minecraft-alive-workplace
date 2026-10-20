package io.github.jcondedata.aliveworkplace.craft;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * The Crafter (ROADMAP 37.14): a villager at a Crafter's Workbench who makes what the village's workers are short of,
 * through whole recipe chains ({@link CrafterShift}). Behind {@code Expansions.M37} and config {@code crafters}.
 */
public final class Crafters {
	/** Config {@code crafters}: off, no villager takes a Crafter's Workbench, and Crafters already hired stand idle. */
	public static boolean ENABLED = true;

	/** What each Crafter last couldn't make, and why (the line above the head says it too). */
	private static final Map<Villager, Component> LACKS = new WeakHashMap<>();

	public static boolean isCrafter(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.CRAFTER;
	}

	/** The Crafter's WORK activity. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new CrafterShift()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	/** What {@code crafter} said they're short of the last time they couldn't make something asked for, or null. */
	@Nullable
	public static Component lacking(Villager crafter) {
		synchronized (LACKS) {
			return LACKS.get(crafter);
		}
	}

	static void setLacking(Villager crafter, @Nullable Component what) {
		synchronized (LACKS) {
			if (what == null) {
				LACKS.remove(crafter);
			} else {
				LACKS.put(crafter, what);
			}
		}
	}

	private Crafters() {
	}
}
