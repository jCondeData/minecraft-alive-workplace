package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.fossil.Revival;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mail.Parcel;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.ranch.Daycare;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;

/**
 * Retirement and apprentices (ROADMAP 34.20): a full hand-over in order (the elder's own child chosen, two days of
 * shadowing with "learning from Bram", then the workstation, the job one level below and the assignments, two chronicle
 * entries, kept through a save); each "never while" case; nobody to take over (the hall's line) and one retirement a
 * village every three days; the level never below Apprentice; a retired elder never re-employed; a mentor's 50%; and
 * {@code elderRetirement} off. The world's clock is moved on within one tick and put back, as in {@link LifeStageGameTests}.
 */
public class RetirementGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** Bram (an elder Expert farmer) hands over to his own jobless child Tom, not to Ida, after two days of shadowing. */
	//$ gametest_ticks_batch AREA '100' '"retireHandOver"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "retireHandOver")
	public void aFullHandOverInOrder(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		BlockPos composter = new BlockPos(3, 2, 3);
		helper.setBlock(composter, Blocks.COMPOSTER);
		Villager ida = villager(helper, new BlockPos(9, 2, 6), "Ida", VillagerProfession.NONE, 1);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.NONE, 1);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			Jobs.employ(level, bram, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
			bram.setVillagerData(bram.getVillagerData().setLevel(4));
			bram.setVillagerXp(160);
			int bramTrades = bram.getOffers().size();
			List<BlockPos> route = List.of(helper.absolutePos(new BlockPos(1, 2, 1)), helper.absolutePos(new BlockPos(2, 2, 9)));
			List<ResourceLocation> orders = List.of(ResourceLocation.parse("minecraft:wheat"));
			Employer jesse = new Employer(UUID.nameUUIDFromBytes("jesse".getBytes()), "Jesse");
			ModAttachments.PATROL_ROUTE.set(bram, route);
			ModAttachments.BALL_ORDERS.set(bram, orders);
			ModAttachments.BUILDER_EMPLOYER.set(bram, jesse);
			ModAttachments.PARENTS.set(tom, new Families.Parents(Component.literal("Ann"), Component.literal("Bram"), "", "minecraft:farmer", true));
			long clock = level.getDayTime();
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 130);
			LifeStages.setAdultSince(tom, today - 20);
			LifeStages.setAdultSince(ida, today - 40);
			List<Villager> all = List.of(bram, tom, ida);
			GlobalPos site = GlobalPos.of(level.dimension(), helper.absolutePos(composter));
			try {
				helper.assertTrue(bram.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(site::equals).isPresent() && bramTrades > 0, "Bram's job to start with");
				Retirement.round(level, hallPos, all);
				Retirement.Shadow shadow = Retirement.shadow(tom);
				helper.assertTrue(shadow != null && shadow.elder().equals(bram.getUUID()) && shadow.since() == today, "Tom isn't shadowing Bram: " + shadow);
				helper.assertTrue(Retirement.shadow(ida) == null, "Ida chosen too");
				helper.assertTrue(chronicle(hall).contains("Tom is learning the Farmer trade from Bram, who would like to retire"), "the chronicle: " + chronicle(hall));
				helper.assertTrue(lore(level, hallPos, tom).contains("learning from Bram"), "Tom's card: " + lore(level, hallPos, tom));
				helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.FARMER, "Bram retired before the shadowing");

				// Day 1: still learning, and it's kept through a save.
				level.setDayTime(clock + VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(tom) != null && bram.getVillagerData().getProfession() == VillagerProfession.FARMER, "handed over after one day");
				Villager copy = reload(level, tom);
				helper.assertTrue(Retirement.shadow(copy) != null && Retirement.shadow(copy).elder().equals(bram.getUUID()), "the shadowing lost in a save");
				copy.discard();

				// Day 2: the hand-over.
				level.setDayTime(clock + 2 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(tom) == null, "Tom still shadowing");
				helper.assertTrue(tom.getVillagerData().getProfession() == VillagerProfession.FARMER && tom.getVillagerData().getLevel() == 3,
					"Tom's job: " + tom.getVillagerData());
				helper.assertTrue(tom.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(site::equals).isPresent(), "Tom hasn't Bram's composter");
				helper.assertTrue(route.equals(ModAttachments.PATROL_ROUTE.get(tom)) && orders.equals(ModAttachments.BALL_ORDERS.get(tom))
					&& jesse.equals(ModAttachments.BUILDER_EMPLOYER.get(tom)), "assignments not carried over");
				helper.assertTrue(!ModAttachments.PATROL_ROUTE.has(bram) && !ModAttachments.BALL_ORDERS.has(bram) && !ModAttachments.BUILDER_EMPLOYER.has(bram),
					"Bram kept assignments");
				helper.assertTrue(Retirement.isRetired(bram) && bram.getVillagerData().getProfession() == VillagerProfession.NONE
					&& bram.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "Bram not retired: " + bram.getVillagerData());
				helper.assertTrue(bram.getOffers().size() == bramTrades, "Bram's trades for players: " + bram.getOffers().size() + " of " + bramTrades);
				helper.assertTrue(chronicle(hall).contains("Bram retired from the Farmer trade after 132 days grown"), "the chronicle: " + chronicle(hall));
				helper.assertTrue(chronicle(hall).contains("Tom took over the Farmer trade from Bram, as Apprentice")
					|| chronicle(hall).contains("Tom took over the Farmer trade from Bram, as Journeyman"), "the chronicle: " + chronicle(hall));
				helper.assertTrue(lore(level, hallPos, bram).contains("Retired Farmer · mentors the young"), "Bram's card: " + lore(level, hallPos, bram));
				Villager again = reload(level, bram);
				helper.assertTrue(Retirement.isRetired(again) && "minecraft:farmer".equals(ModAttachments.RETIRED.get(again).trade()), "retirement lost in a save");
				again.discard();
			} finally {
				level.setDayTime(clock);
			}
			helper.succeed();
		});
	}

	/** Never while an elder holds a site or a queue, a quarry, Pokémon in their daycare, fossils being revived, or parcels on their round. */
	//$ gametest_ticks_batch AREA '100' '"retireNeverWhile"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "retireNeverWhile")
	public void neverWhileTheyHoldSomething(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.MASON, 3);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 130);
			LifeStages.setAdultSince(tom, today - 20);
			List<Villager> all = List.of(bram, tom);
			UUID owner = UUID.nameUUIDFromBytes("owner".getBytes());
			List<String> held = new ArrayList<>();
			List<Runnable> cases = List.of(
				() -> ModAttachments.BUILDER_JOB.set(bram, new BuilderJob(UUID.randomUUID())),
				() -> ModAttachments.MINER_JOB.set(bram, new BuilderJob(UUID.randomUUID())),
				() -> ModAttachments.DAYCARE.set(bram, List.of(new Daycare.Boarder(owner, "Jesse", new CompoundTag(), 0L))),
				() -> ModAttachments.FOSSIL_REVIVALS.set(bram, List.of(new Revival(owner, "Jesse", ResourceLocation.parse("cobblemon:fossilized_bird"), List.of(), 3, 3))));
			List<Runnable> undo = List.of(() -> ModAttachments.BUILDER_JOB.remove(bram), () -> ModAttachments.MINER_JOB.remove(bram),
				() -> ModAttachments.DAYCARE.remove(bram), () -> ModAttachments.FOSSIL_REVIVALS.remove(bram));
			for (int i = 0; i < cases.size(); i++) {
				cases.get(i).run();
				held.add(Retirement.holds(level, bram));
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(tom) == null, "retiring while holding " + held.get(i));
				undo.get(i).run();
			}
			// Parcels on his round.
			PostOffice office = PostOffice.get(level.getServer());
			Parcel parcel = office.post(owner, "Jesse", owner, "Jesse", GlobalPos.of(level.dimension(), hallPos), level.getGameTime(), List.of());
			try {
				parcel.setStatus(Parcel.Status.CARRIED);
				parcel.claim(bram.getUUID(), level.getGameTime());
				held.add(Retirement.holds(level, bram));
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(tom) == null, "retiring with parcels on his round");
			} finally {
				office.remove(parcel);
			}
			helper.assertTrue(held.equals(List.of("site", "quarry", "daycare", "fossils", "parcels")), "what he held: " + held);
			// With nothing held, he starts.
			helper.assertTrue(Retirement.holds(level, bram) == null, "still holding " + Retirement.holds(level, bram));
			Retirement.round(level, hallPos, all);
			helper.assertTrue(Retirement.shadow(tom) != null, "not retiring with nothing held");
			helper.succeed();
		});
	}

	/**
	 * Nobody to take over: Bram keeps working and the hall says so. Then two elders and two jobless: one retires, the
	 * other only three days after; a Novice elder's successor is an Apprentice; {@code elderRetirement} off starts nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"retireOnePerVillage"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "retireOnePerVillage")
	public void oneRetirementEveryThreeDays(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FLETCHER, 1);
		Villager odo = villager(helper, new BlockPos(5, 2, 9), "Odo", VillagerProfession.LIBRARIAN, 5);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
		Villager ida = villager(helper, new BlockPos(9, 2, 6), "Ida", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long clock = level.getDayTime();
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 140);
			LifeStages.setAdultSince(odo, today - 130);
			LifeStages.setAdultSince(tom, today - 20);
			LifeStages.setAdultSince(ida, today - 20);
			try {
				// Nobody jobless: both keep working, and the hall says Bram would like to retire.
				Retirement.round(level, hallPos, List.of(bram, odo));
				helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.FLETCHER && !Retirement.isRetired(bram), "Bram retired with nobody");
				helper.assertTrue(lore(level, hallPos, bram).contains("Bram would like to retire: the village needs a jobless villager to take over"),
					"Bram's card: " + lore(level, hallPos, bram));

				// elderRetirement off: nothing starts.
				Retirement.ENABLED = false;
				List<Villager> all = List.of(bram, odo, tom, ida);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(tom) == null && Retirement.shadow(ida) == null, "retiring with elderRetirement off");
				Retirement.ENABLED = true;

				// One at a time: Bram (the oldest) first.
				Retirement.round(level, hallPos, all);
				long shadows = all.stream().filter(v -> Retirement.shadow(v) != null).count();
				helper.assertTrue(shadows == 1, "shadows at once: " + shadows);
				Villager first = Retirement.shadow(tom) != null ? tom : ida;
				Villager second = first == tom ? ida : tom;
				helper.assertTrue(Retirement.shadow(first).elder().equals(bram.getUUID()), "not Bram first");
				helper.assertTrue(!lore(level, hallPos, bram).contains("Bram would like to retire: the village needs a jobless villager to take over"), "the hall's line stayed");
				level.setDayTime(clock + 2 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.isRetired(bram) && first.getVillagerData().getProfession() == VillagerProfession.FLETCHER
					&& first.getVillagerData().getLevel() == 2, "a Novice's successor: " + first.getVillagerData());
				helper.assertTrue(Retirement.shadow(second) == null, "Odo started the day Bram retired");
				level.setDayTime(clock + 4 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(second) == null, "Odo started two days after Bram retired");
				level.setDayTime(clock + 5 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(Retirement.shadow(second) != null && Retirement.shadow(second).elder().equals(odo.getUUID()), "Odo didn't start three days after");
				level.setDayTime(clock + 7 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, all);
				helper.assertTrue(second.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && second.getVillagerData().getLevel() == 4,
					"a Master's successor: " + second.getVillagerData());
				helper.assertTrue(Retirement.isRetired(odo) && LifeStages.goodTrait(odo) == LifeStages.Trait.MASTER, "a retired Master is no Master for the charm");
			} finally {
				level.setDayTime(clock);
				Retirement.ENABLED = true;
			}
			helper.succeed();
		});
	}

	/** A retired elder never takes a job again: not by themselves at a workstation, not by order, and is never a successor. */
	//$ gametest_ticks_batch AREA '100' '"retireNeverAgain"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "retireNeverAgain")
	public void aRetiredElderIsNeverReEmployed(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		BlockPos lectern = new BlockPos(3, 2, 3);
		helper.setBlock(lectern, Blocks.LECTERN);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.NONE, 1);
		Villager odo = villager(helper, new BlockPos(7, 2, 6), "Odo", VillagerProfession.LIBRARIAN, 3);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 150);
			LifeStages.setAdultSince(odo, today - 20);
			ModAttachments.RETIRED.set(bram, new Retirement.Retired(today - 10, "minecraft:farmer"));
			bram.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(lectern)));
			bram.setVillagerData(bram.getVillagerData().setProfession(VillagerProfession.LIBRARIAN)); // vanilla's job from a workstation
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE && bram.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
				"a retired elder took a workstation: " + bram.getVillagerData());
			WorkerLimits.order(bram, VillagerProfession.FARMER); // a job by order (the hall, a tool)
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE, "a retired elder took a job by order");
			helper.assertTrue(Retirement.successor(hallPos, odo, List.of(bram, odo), today) == null, "a retired elder chosen to take over");
			helper.succeed();
		});
	}

	/** A retired elder teaches a Novice of their old trade, who learns 50% faster ("taught by Bram"); others aren't taught. */
	//$ gametest_ticks_batch AREA '100' '"retireMentor"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "retireMentor")
	public void aMentorTeachesFiftyPercentFaster(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.NONE, 1);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.FARMER, 1);
		Villager ida = villager(helper, new BlockPos(9, 2, 6), "Ida", VillagerProfession.FARMER, 3);
		Villager eli = villager(helper, new BlockPos(11, 2, 6), "Eli", VillagerProfession.FLETCHER, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 150);
			for (Villager v : List.of(tom, ida, eli)) {
				LifeStages.setAdultSince(v, today - 20);
			}
			int before = Traits.xp(tom, 10, RandomSource.create(7));
			helper.assertTrue(Retirement.extraXp(tom, 10, RandomSource.create(7)) == 0, "taught before a mentor");
			ModAttachments.RETIRED.set(bram, new Retirement.Retired(today - 10, "minecraft:farmer"));
			Retirement.round(level, hallPos, List.of(bram, tom, ida, eli));
			Retirement.Teacher teacher = Retirement.teacher(tom);
			helper.assertTrue(teacher != null && teacher.mentor().equals(bram.getUUID()), "Tom not taught by Bram");
			helper.assertTrue(tom.getUUID().equals(ModAttachments.MENTORING.get(bram)), "Bram's pupil");
			helper.assertTrue(Retirement.teacher(ida) == null && Retirement.teacher(eli) == null, "a Journeyman or another trade taught");
			int after = Traits.xp(tom, 10, RandomSource.create(7));
			helper.assertTrue(after - before == 5, "taught xp: " + after + " against " + before);
			helper.assertTrue(Retirement.extraXp(tom, 10, RandomSource.create(7)) == 5, "the mentor's 50%");
			helper.assertTrue(lore(level, hallPos, tom).contains("taught by Bram"), "Tom's card: " + lore(level, hallPos, tom));
			// Taught for a while only: past the time, no more.
			ModAttachments.TAUGHT_BY.set(tom, new Retirement.Teacher(bram.getUUID(), "Bram", level.getGameTime() - 1));
			helper.assertTrue(Retirement.extraXp(tom, 10, RandomSource.create(7)) == 0, "taught after the teaching ended");
			helper.succeed();
		});
	}

	/** {@code elderRetirement} is in the config, on by default, and switched off from the file. */
	//$ gametest_ticks_batch AREA '20' '"retireConfig"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "retireConfig")
	public void theConfigSwitch(GameTestHelper helper) {
		Leftovers.after(helper, () -> new WorkplaceConfig().apply());
		helper.assertTrue(new WorkplaceConfig().elderRetirement, "off by default");
		WorkplaceConfig.parse("{\"elderRetirement\": false}").apply();
		helper.assertTrue(!Retirement.ENABLED, "elderRetirement off in the file");
		new WorkplaceConfig().apply();
		helper.assertTrue(Retirement.ENABLED, "on again");
		helper.succeed();
	}

	// --- Helpers ----------------------------------------------------------------------------------------------------

	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Retirement.ENABLED = true;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		if (job != VillagerProfession.NONE) {
			v.setVillagerXp(1);
		}
		return v;
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}

	/** A copy of {@code villager} as a save would load it. */
	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(tag);
		copy.setUUID(UUID.randomUUID());
		return copy;
	}

	private static List<String> chronicle(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	private static List<String> lore(ServerLevel level, BlockPos hall, Villager villager) {
		ItemLore lore = VillageHallScreen.person(level, hall, villager).get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}
}
