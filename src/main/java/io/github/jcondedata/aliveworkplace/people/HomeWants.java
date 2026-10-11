package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.ClassesPage;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

/**
 * Households that want a grander home (ROADMAP 34.15): those whose class needs a home of a higher grade than the one
 * they sleep in ("Dara and Tomas are Burghers and want a grade II home"), and those one step below a class who have
 * everything it needs but the home ("... could be Burghers and want a grade II home"). The hall's "What next?" page
 * lists them ({@link #tips}) and the Steward's {@code households_want_grade} condition counts them, so the two agree.
 */
public final class HomeWants {
	/** Tips listed at most. */
	public static final int TIPS = 3;

	/**
	 * A household that wants a home of {@code grade}: in their class ({@code rising} false) or to rise into it (true),
	 * with the grade of the home they have (the lowest of the two of them, 0 for none) and that home's building.
	 */
	public record Want(Households.Household household, SocialClasses.SocialClass social, int grade, int has, boolean rising,
					   Optional<Homes.Building> home) {
	}

	/** The home grade a class needs: its highest {@code home} need, 0 for none. */
	public static int wanted(SocialClasses.SocialClass c) {
		int grade = 0;
		for (ClassNeeds.Need need : c.needs()) {
			if (need instanceof ClassNeeds.Home h) {
				grade = Math.max(grade, h.grade());
			}
		}
		return grade;
	}

	/** The households round {@code hall} that want a grander home, in the hall's fixed household order. None with classes off. */
	public static List<Want> of(ServerLevel level, BlockPos hall) {
		List<Want> out = new ArrayList<>();
		if (!SocialClasses.ENABLED || SocialClasses.ladder().isEmpty()) {
			return out;
		}
		List<ClassesPage.Placed> placed = ClassesPage.households(level, hall);
		if (placed.isEmpty()) {
			return out;
		}
		ClassNeeds.Village village = new ClassNeeds.Village(level, hall, Chronicle.day(level));
		for (ClassesPage.Placed p : placed) {
			List<Villager> members = p.household().members();
			int has = Integer.MAX_VALUE;
			for (Villager v : members) {
				has = Math.min(has, village.homeGrade(v));
			}
			Optional<Homes.Building> home = village.buildingAt(VillageNeeds.bed(level, p.household().lead()));
			int own = wanted(p.social());
			if (own > has) {
				out.add(new Want(p.household(), p.social(), own, has, false, home));
				continue;
			}
			SocialClasses.SocialClass next = SocialClasses.step(p.social(), 1);
			int grade = next == null ? 0 : wanted(next);
			if (grade > has && allButHomeHold(next, members, village)) {
				out.add(new Want(p.household(), next, grade, has, true, home));
			}
		}
		return out;
	}

	private static boolean allButHomeHold(SocialClasses.SocialClass c, List<Villager> members, ClassNeeds.Village village) {
		for (ClassNeeds.Need need : c.needs()) {
			if (!(need instanceof ClassNeeds.Home) && !ClassNeeds.holds(need, members, village)) {
				return false;
			}
		}
		return true;
	}

	/** The "What next?" tips: up to {@link #TIPS} households wanting a grander home. */
	public static List<VillageAdvice.Tip> tips(ServerLevel level, BlockPos hall) {
		List<VillageAdvice.Tip> out = new ArrayList<>();
		for (Want w : of(level, hall)) {
			if (out.size() >= TIPS) {
				break;
			}
			out.add(tip(w));
		}
		return out;
	}

	/** One household's tip: "Dara and Tomas are Burghers and want a grade II home", "Dara could be an Artisan and wants a grade I home". */
	public static VillageAdvice.Tip tip(Want w) {
		boolean one = w.household().members().size() < 2;
		String key = "grand_home" + (w.rising() ? "_rise" : "") + (one ? "_one" : "");
		return new VillageAdvice.Tip(key, Items.OAK_DOOR, SocialClasses.names(w.household().members()), className(w.social(), one),
			Component.translatable("enchantment.level." + w.grade()));
	}

	/** A class's name as the tip says it: "an Artisan" for one, "Artisans" for two ({@code <key>.one}, {@code <key>.plural}). */
	static Component className(SocialClasses.SocialClass c, boolean one) {
		if (c.name().getContents() instanceof TranslatableContents t) {
			return Component.translatableWithFallback(t.getKey() + (one ? ".one" : ".plural"), c.name().getString());
		}
		return c.name();
	}

	private HomeWants() {
	}
}
