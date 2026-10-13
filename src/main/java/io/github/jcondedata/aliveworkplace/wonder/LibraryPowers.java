package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.work.Pace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The Great Library's power (ROADMAP 35.14), {@code research_tier}: while a finished, standing Wonder of the village
 * grants it ({@link Wonders#power}: a damaged one, or Wonders off, closes it), the research tree opens its second tier
 * ({@link Research#SECOND_TIER}), a row of its own on the scholar's screen. Its topics' bonuses are read where each
 * feature works out its numbers, through {@link Research#bonus}, so they pause with the Library's powers while the
 * village keeps the levels:
 * <ul>
 * <li>Swift Hands IV and V: every job 5% faster a level ({@code VillageNeeds.swiftHands});</li>
 * <li>Scholarship: scholars 25% faster a level ({@link #SCHOLARSHIP}, a pace source), and the second level makes
 * research cost a quarter less ({@code Research.Topic.cost});</li>
 * <li>Engineering: builders' bags hold 9 more stacks a level ({@code BuilderWork.engineeringSlots}), and a Wonder's crew
 * takes 2 more builders a level ({@code WonderCrews.limit});</li>
 * <li>Cartography: explorers range twice as far ({@code ExplorerWork.range}), and their maps can lead to trial
 * chambers and ancient cities ({@code Explorers.DEEP_MAP_PLACES});</li>
 * <li>Trade Law: caravans carry 2 more stacks a level ({@code Caravans.cargo}), and the treasury takes 10% more a level
 * ({@code Treasury.tradeLaw});</li>
 * <li>Pharmacy: the ill get well in a day ({@code Sickness.recovery}), and each Nurse cures one villager a day without a
 * remedy ({@code NurseWork.cure}).</li>
 * </ul>
 * Every speed bonus here is a source of {@link Pace}, under its shared cap (twice the pace by default). Nothing new is
 * saved but each Nurse's last remedy-free day ({@code pharmacy_cure_day}); the levels live in the hall's research.
 */
public final class LibraryPowers {
	public static final String TYPE = "research_tier";

	/** Scholarship's pace: scholars 25% faster a level, under the shared cap. */
	public static final Pace.Source SCHOLARSHIP = new Pace.Source("scholarship", Pace.Kind.BONUS,
		v -> v.getVillagerData().getProfession() == ModVillagers.SCHOLAR ? scholarship(Research.bonus(v, Research.Topic.SCHOLARSHIP)) : 1f);

	public static void init() {
		Research.SECOND_TIER = LibraryPowers::stands;
		Pace.register(SCHOLARSHIP);
	}

	/** Whether the village round {@code hall} has a finished, standing Wonder with {@code research_tier} (the Great Library). */
	public static boolean stands(ServerLevel level, BlockPos hall) {
		return Wonders.power(level, hall, TYPE).isPresent();
	}

	/** Scholarship's work time factor: 25% faster a level. */
	public static float scholarship(int level) {
		return 1f / (1f + 0.25f * Math.max(0, level));
	}

	private LibraryPowers() {
	}
}
