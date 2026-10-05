package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * Born, the third way Legends come (29.7): when a child of two schooled Masters grows up, one time in
 * {@link #LEGEND_ONE_IN} they are a Rare Legend whose {@code born} way names a parent's trade (if the village meets its
 * conditions and its slot is free), taking the Legend's trade as a Master; otherwise one time in {@link #GIFTED_ONE_IN}
 * they are Gifted. The dice are thrown in that order from the one {@link RandomSource}, so a test can fix them.
 */
public final class BornGifts {
	public static final int LEGEND_ONE_IN = 20;
	public static final int GIFTED_ONE_IN = 4;

	/** What a grown-up child turned out: a Legend, Gifted, or neither (what they are by the roll). */
	public record Outcome(@Nullable Legend legend, @Nullable Gifted.Gift gift) {
		public static final Outcome NEITHER = new Outcome(null, null);
	}

	/**
	 * The rolls for {@code villager}, who has just grown up in the village round {@code hall}: nothing unless both parents
	 * were schooled Masters at the birth. A Legend is made at once ({@link Legends#make}, way {@code born}); a gift is
	 * written into {@code GIFTED}.
	 */
	public static Outcome grownUp(ServerLevel level, BlockPos hall, Villager villager, Families.Parents parents, RandomSource random) {
		if (!parents.motherSchooledMaster() || !parents.fatherSchooledMaster()) {
			return Outcome.NEITHER;
		}
		if (random.nextInt(LEGEND_ONE_IN) == 0) {
			List<Legend> open = candidates(level, hall, villager, parents);
			if (!open.isEmpty()) {
				Legend legend = open.size() == 1 ? open.get(0) : open.get(random.nextInt(open.size()));
				Legends.make(level, villager, legend, "born");
				return new Outcome(legend, null);
			}
		}
		if (Gifted.CHANCE > 0 && random.nextInt(GIFTED_ONE_IN) == 0) {
			Gifted.Gift gift = Gifted.pick(random);
			if (gift != null) {
				Gifted.set(villager, gift.id());
				return new Outcome(null, gift);
			}
		}
		return Outcome.NEITHER;
	}

	/**
	 * The Rare Legends a child of these parents may grow up to be in the village round {@code hall}: born to a parent's
	 * trade, every condition met, and their slot free.
	 */
	public static List<Legend> candidates(ServerLevel level, BlockPos hall, Villager villager, Families.Parents parents) {
		List<Legend> out = new ArrayList<>();
		for (Legend legend : Legends.all()) {
			if (legend.rarity() != Rarity.RARE || !bornTo(legend, parents)) {
				continue;
			}
			boolean met = true;
			for (Condition c : legend.conditions()) {
				met &= c.met(level, hall);
			}
			if (met && LegendSlots.whyNot(level, hall, legend, villager.getUUID()).isEmpty()) {
				out.add(legend);
			}
		}
		return out;
	}

	/** Whether one of {@code legend}'s {@code born} ways names the mother's or the father's trade. */
	static boolean bornTo(Legend legend, Families.Parents parents) {
		for (JsonObject way : legend.ways("born")) {
			if (!way.has("trades")) {
				continue;
			}
			for (JsonElement e : way.getAsJsonArray("trades")) {
				net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(e.getAsString());
				String trade = id == null ? e.getAsString() : id.toString();
				if (trade.equals(parents.motherJob()) || trade.equals(parents.fatherJob())) {
					return true;
				}
			}
		}
		return false;
	}

	/** The chronicle's line for a grown-up child who became a Legend: "Wren, child of Dara and Tom, has grown up to be the village's Bard Laureate". */
	public static Component legendLine(Villager villager, Families.Parents parents, Legend legend) {
		return Component.translatable("chronicle.aliveworkplace.grown_up_legend", villager.getDisplayName(), parents.mother(), parents.father(), legend.titleText());
	}

	private BornGifts() {
	}
}
