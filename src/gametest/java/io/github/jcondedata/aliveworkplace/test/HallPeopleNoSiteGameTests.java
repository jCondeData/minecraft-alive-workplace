package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * B1173 (owner, 2026-11-11): the Village Hall's People table listed an Undertaker among the jobless ("No job") when he
 * had no workstation memory, so his job showed nowhere. A worker at his workstation is listed at his job; one whose
 * workstation was broken still shows his job, with "(no workstation)", and the header counts him working; every job of
 * ours does the same.
 */
public class HallPeopleNoSiteGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos TABLE = new BlockPos(9, 2, 9);

	private static CompoundTag row(GameTestHelper helper, ChoiceMenu menu, Villager villager) {
		return HallPeople.row(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, villager)));
	}

	/**
	 * An Undertaker at his table is listed as an Undertaker; with the table broken (and the memory of it gone, as the
	 * brain drops it) he is still an Undertaker, "(no workstation)", counted working, and a free block is his for nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"b1173Undertaker"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b1173Undertaker")
	public void anUndertakerWithoutHisTableKeepsHisJob(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(TABLE, ModBlocks.UNDERTAKERS_TABLE);
		Villager ezra = QaHallScreenGameTests.villager(helper, "Ezra", TABLE.offset(1, 0, 1));
		Villager pip = QaHallScreenGameTests.villager(helper, "Pip", new BlockPos(12, 2, 18));
		ServerPlayer player = QaHallScreenGameTests.payer(helper, new ArrayList<>(), 0);
		helper.runAfterDelay(3, () -> {
			Jobs.employ(helper.getLevel(), ezra, helper.absolutePos(TABLE), ModVillagers.UNDERTAKERS_TABLE_POI, ModVillagers.UNDERTAKER);
			ezra.setVillagerData(ezra.getVillagerData().setLevel(2));
			ezra.setVillagerXp(70); // traded: he keeps his job without his table, as vanilla keeps an experienced villager's
			helper.assertTrue(ezra.getVillagerData().getProfession() == ModVillagers.UNDERTAKER, "setup: Ezra isn't an Undertaker: " + ezra.getVillagerData());
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			CompoundTag at = row(helper, menu, ezra);
			helper.assertTrue(at.getString("kind").equals("worker") && at.getString("job").equals("entity.minecraft.villager.undertaker")
				&& !at.getBoolean("no_site") && HallPeople.job(at).getString().equals("Undertaker"), "Ezra at his table: " + at);
			helper.assertTrue(menu.state(HallSections.PEOPLE_WORKING) == 1 && menu.state(HallSections.PEOPLE_JOBLESS) == 1,
				"header with Ezra at work: " + menu.state(HallSections.PEOPLE_WORKING) + " working, " + menu.state(HallSections.PEOPLE_JOBLESS) + " jobless");
			player.closeContainer();

			helper.setBlock(TABLE, Blocks.AIR);
			ezra.getBrain().eraseMemory(MemoryModuleType.JOB_SITE); // what the brain does when the block is gone
			helper.assertTrue(ezra.getVillagerData().getProfession() == ModVillagers.UNDERTAKER, "setup: Ezra lost his job with his table");
			ChoiceMenu after = QaHallScreenGameTests.openPeople(helper, player);
			CompoundTag gone = row(helper, after, ezra);
			helper.assertTrue(!gone.getString("kind").equals("jobless") && gone.getString("job").equals("entity.minecraft.villager.undertaker")
				&& gone.getBoolean("no_site"), "Ezra without his table: " + gone);
			helper.assertTrue(HallPeople.job(gone).getString().equals("Undertaker (no workstation)"), "his Job column reads " + HallPeople.job(gone).getString());
			helper.assertTrue(gone.getString("job_action").equals("assign"), "a free block isn't offered to Ezra: " + gone);
			helper.assertTrue(row(helper, after, pip).getString("kind").equals("jobless"), "Pip isn't jobless: " + row(helper, after, pip));
			helper.assertTrue(after.state(HallSections.PEOPLE_WORKING) == 1 && after.state(HallSections.PEOPLE_JOBLESS) == 1,
				"header without the table: " + after.state(HallSections.PEOPLE_WORKING) + " working, " + after.state(HallSections.PEOPLE_JOBLESS) + " jobless");
			helper.assertTrue(VillageHalls.census(helper.getLevel(), helper.absolutePos(new BlockPos(15, 2, 15))).jobless().contains(ezra),
				"the census no longer counts Ezra among those a free block is for");
			helper.assertTrue(Language.getInstance().has("screen.aliveworkplace.hall.people.no_site"), "no_site isn't in the language file");
			helper.succeed();
		});
	}

	/**
	 * Every job of ours (but the Steward, dismissed without his hall, and the retired Crafter) without a workstation:
	 * none is listed as jobless, each shows its own job's name with the note, and the header counts them all working. A
	 * Legend, who never has one, shows "Legend" with no note.
	 */
	//$ gametest_ticks_batch AREA '200' '"b1173EveryJob"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b1173EveryJob")
	public void everyJobOfOursKeepsItsNameWithoutAWorkstation(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		List<VillagerProfession> jobs = BuiltInRegistries.VILLAGER_PROFESSION.stream().filter(p -> {
			ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(p);
			return id != null && id.getNamespace().equals("aliveworkplace") && p != ModVillagers.STEWARD && p != ModVillagers.CRAFTER;
		}).toList();
		helper.assertTrue(jobs.contains(ModVillagers.UNDERTAKER) && jobs.contains(ModVillagers.LEGEND), "setup: our jobs: " + jobs);
		List<Villager> people = new ArrayList<>();
		for (int i = 0; i < jobs.size(); i++) {
			people.add(QaHallScreenGameTests.villager(helper, "Worker " + (i + 1), new BlockPos(9 + (i % 7) * 2, 2, 9 + ((i / 7) % 7) * 2)));
		}
		ServerPlayer player = QaHallScreenGameTests.payer(helper, new ArrayList<>(), 0);
		helper.runAfterDelay(3, () -> {
			for (int i = 0; i < jobs.size(); i++) {
				Villager v = people.get(i);
				v.setVillagerData(v.getVillagerData().setProfession(jobs.get(i)).setLevel(2));
				v.setVillagerXp(70);
			}
			List<String> wrong = new ArrayList<>();
			for (int i = 0; i < jobs.size(); i++) {
				Villager v = people.get(i);
				VillagerProfession job = jobs.get(i);
				String key = "entity.minecraft.villager." + job.name();
				boolean legend = job == ModVillagers.LEGEND;
				if (v.getVillagerData().getProfession() != job) {
					wrong.add(job.name() + " didn't take (" + v.getVillagerData().getProfession().name() + ")");
				} else if (HallPeople.kind(v) == HallPeople.Kind.JOBLESS || HallPeople.noWorkstation(v) == legend || !Language.getInstance().has(key)) {
					wrong.add(job.name() + ": " + HallPeople.kind(v) + ", no workstation " + HallPeople.noWorkstation(v) + ", named " + Language.getInstance().has(key));
				}
			}
			helper.assertTrue(wrong.isEmpty(), "jobs listed wrong without a workstation: " + wrong);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			helper.assertTrue(menu.state(HallSections.PEOPLE_WORKING) == jobs.size() && menu.state(HallSections.PEOPLE_JOBLESS) == 0,
				"header: " + menu.state(HallSections.PEOPLE_WORKING) + " working, " + menu.state(HallSections.PEOPLE_JOBLESS) + " jobless of " + jobs.size());
			for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) { // the rows on the first page
				CompoundTag r = HallPeople.row(menu.icon(slot));
				if (r != null) {
					String shown = HallPeople.job(r).getString();
					helper.assertTrue(!r.getString("kind").equals("jobless") && (r.getBoolean("no_site") ? shown.endsWith(" (no workstation)") : shown.equals("Legend")),
						"a row reads '" + shown + "': " + r);
				}
			}
			helper.succeed();
		});
	}
}
