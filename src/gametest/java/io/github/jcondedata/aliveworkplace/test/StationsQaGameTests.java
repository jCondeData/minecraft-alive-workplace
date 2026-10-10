package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane (qa-1003-1433), bug B8 from its spec: "a GameTest of that sequence leaves the second worker's block taken".
 * The same sequence, with the second worker not loaded when the first is given a new job: in a real village the block's
 * new owner is often in a chunk no player is near, while the player stands by the first worker.
 */
public class StationsQaGameTests implements FabricGameTest {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-test");
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * A librarian's lectern is broken while they're away and put back; a second librarian takes the new one; the second
	 * one's chunk unloads (saved and removed, as an unload does); the first is given a farm job by the player; the
	 * second comes back. The lectern must still be the second's: its POI ticket taken, not free for a third villager.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaB8SecondWorkerUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB8SecondWorkerUnloaded")
	public void qaReassigningKeepsTheBlockOfAnUnloadedOwnerTaken(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lecternAt = new BlockPos(3, 2, 3);
		BlockPos lectern = helper.absolutePos(lecternAt);
		BlockPos composter = helper.absolutePos(new BlockPos(9, 2, 9));
		helper.setBlock(lecternAt, Blocks.LECTERN);
		helper.setBlock(new BlockPos(9, 2, 9), Blocks.COMPOSTER);
		Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 10));
		first.setNoAi(true); // (away: their brain doesn't see the block go)
		Jobs.employ(level, first, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		helper.setBlock(lecternAt, Blocks.AIR);
		helper.setBlock(lecternAt, Blocks.LECTERN);
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
		second.setNoAi(true);
		Jobs.employ(level, second, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0, "setup: the second librarian didn't take the new lectern");

		// The second worker's chunk unloads: saved, then removed from the world the way an unload removes it.
		CompoundTag saved = new CompoundTag();
		second.save(saved);
		second.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);

		Stations.assign(level, first, composter, VillagerProfession.FARMER);
		helper.assertTrue(StationsSpecGameTests.job(first) == VillagerProfession.FARMER, "the first worker isn't a farmer");

		// The chunk loads again.
		Villager back = (Villager) EntityType.loadEntityRecursive(saved, level, e -> e);
		helper.assertTrue(back != null && level.addFreshEntity(back), "the second worker didn't come back");
		helper.assertTrue(StationsSpecGameTests.site(back).map(lectern::equals).orElse(false), "setup: the second worker forgot their lectern");
		helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0,
			"giving the first worker a new job freed the lectern of the second worker, who was unloaded at the time: a third villager can now share it");
		helper.succeed();
	}

	/** A job's config switch, as the test sets it and puts it back. */
	private record Switch(String key, java.util.function.Supplier<VillagerProfession> job, java.util.function.BooleanSupplier get,
		java.util.function.Consumer<Boolean> set) {
	}

	/**
	 * QA lane (qa-1010-0533), bug B97 from its spec and the wiki's sentence: "A job whose switch is off isn't listed" on
	 * its workstation's tooltip. For each of the five job switches that need no other mod (tailors, printers,
	 * jewellers, vintners, gemGrowers): with everything on, some workstation lists the job; with only that switch off, no
	 * workstation lists it, and every workstation lists exactly what it did before apart from it (nothing else goes,
	 * nothing moves); back on, every list is as it was.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaB97EachJobSwitchTakesOnlyItsOwnJobOffTheTooltips(GameTestHelper helper) {
		java.util.List<Switch> switches = java.util.List.of(
			new Switch("tailors", () -> io.github.jcondedata.aliveworkplace.registry.ModVillagers.TAILOR,
				() -> io.github.jcondedata.aliveworkplace.tailor.Tailors.ENABLED, v -> io.github.jcondedata.aliveworkplace.tailor.Tailors.ENABLED = v),
			new Switch("printers", () -> io.github.jcondedata.aliveworkplace.registry.ModVillagers.PRINTER,
				() -> io.github.jcondedata.aliveworkplace.printer.Printers.ENABLED, v -> io.github.jcondedata.aliveworkplace.printer.Printers.ENABLED = v),
			new Switch("jewellers", () -> io.github.jcondedata.aliveworkplace.registry.ModVillagers.JEWELLER,
				() -> io.github.jcondedata.aliveworkplace.jeweller.Jewellers.ENABLED, v -> io.github.jcondedata.aliveworkplace.jeweller.Jewellers.ENABLED = v),
			new Switch("vintners", () -> io.github.jcondedata.aliveworkplace.registry.ModVillagers.VINTNER,
				() -> io.github.jcondedata.aliveworkplace.vintner.Vintners.ENABLED, v -> io.github.jcondedata.aliveworkplace.vintner.Vintners.ENABLED = v),
			new Switch("gemGrowers", () -> io.github.jcondedata.aliveworkplace.registry.ModVillagers.GEM_GROWER,
				() -> io.github.jcondedata.aliveworkplace.gem.GemGrowers.ENABLED, v -> io.github.jcondedata.aliveworkplace.gem.GemGrowers.ENABLED = v));
		// (set and put back within this one call: tests beside this one never see a switch off)
		java.util.List<Boolean> before = switches.stream().map(s -> s.get().getAsBoolean()).toList();
		try {
			switches.forEach(s -> s.set().accept(true));
			java.util.List<java.util.List<VillagerProfession>> allOn = listed();
			for (Switch s : switches) {
				VillagerProfession job = s.job().get();
				long stations = allOn.stream().filter(l -> l.contains(job)).count();
				helper.assertTrue(stations > 0, s.key() + " on: no workstation's tooltip lists the " + job);
				s.set().accept(false);
				java.util.List<java.util.List<VillagerProfession>> off = listed();
				for (int i = 0; i < allOn.size(); i++) {
					java.util.List<VillagerProfession> want = allOn.get(i).stream().filter(p -> p != job).toList();
					helper.assertTrue(off.get(i).equals(want), s.key() + " off: workstation " + i + " lists " + off.get(i) + ", expected " + want);
				}
				s.set().accept(true);
				helper.assertTrue(listed().equals(allOn), s.key() + " back on: the tooltips aren't as they were");
				LOG.info("[qa B97] {} off takes the {} off {} workstation tooltip(s) and nothing else", s.key(), job, stations);
			}
		} finally {
			for (int i = 0; i < switches.size(); i++) {
				switches.get(i).set().accept(before.get(i));
			}
		}
		helper.succeed();
	}

	/** What each workstation's tooltip lists, in Stations.ALL's order (StationTooltip: the station's jobs that are available). */
	private static java.util.List<java.util.List<VillagerProfession>> listed() {
		return Stations.ALL.stream().map(s -> s.jobs().stream().filter(Stations::available).map(j -> j.profession().get()).toList()).toList();
	}
}
