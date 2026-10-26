package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.ClassMigration;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA for two failing showcase scenes (B451 elders, B452 class_switch_on): each scene's player situation staged through
 * the same entry points, once with a real bed (as a villager who found his bed has one) and once exactly as the scene
 * stages it. Since B362 (2026-10-24) a villager is housed only while his bed's point of interest is there
 * ({@code VillageNeeds.bed}); a bed's point of interest is its head half. The elders scene places a lone default-state
 * bed block (its foot half) and the class_switch_on scene none at all, so in both the villager the scene expects to be
 * housed has no bed: the mod is right and the scenes need a real bed (the tests' {@link Beds#home}).
 */
public class QaElderClassSceneGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	// --- B451: the elders scene ----------------------------------------------------------------------------------------

	/** The scene's Bram and Tom by a hall, the clock a day on as the scene moves it; {@code bed} houses Bram. Returns {Bram, Tom}. */
	private static Villager[] elders(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		boolean moods = Moods.ENABLED;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Moods.ENABLED = moods;
			LifeStages.AGES = true;
			LifeStages.ELDER_DAYS = 1200;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager bram = farmer(helper, new BlockPos(5, 2, 6), "Bram");
		Villager tom = farmer(helper, new BlockPos(8, 2, 6), "Tom");
		return new Villager[] {bram, tom};
	}

	private static Villager farmer(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		v.setVillagerXp(10);
		return v;
	}

	/**
	 * Runs the scene's steps on Bram (a day short of an elder, fed, with {@code housing} run to give him his bed) and Tom
	 * (grown 30 days): a day on, two of the hall's life rounds; returns Bram's mood and card that day, the clock put back.
	 */
	private static void sceneDay(GameTestHelper helper, Villager bram, Villager tom, Runnable housing, java.util.function.BiConsumer<Moods.Mood, List<String>> check) {
		ServerLevel level = helper.getLevel();
		BlockPos hallPos = helper.absolutePos(HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(hallPos);
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		Moods.ENABLED = true; // (off in GameTests otherwise)
		long clock = level.getDayTime();
		long today = Chronicle.day(level);
		LifeStages.setAdultSince(bram, today - LifeStages.ELDER_DAYS + 1);
		LifeStages.setAdultSince(tom, today - 30);
		ModAttachments.LAST_MEAL.set(bram, level.getGameTime());
		ModAttachments.LAST_MEAL.set(tom, level.getGameTime());
		housing.run();
		helper.assertTrue(!LifeStages.isElder(bram, today), "Bram was staged a day short of an elder");
		try {
			level.setDayTime(clock + VillageNeeds.DAY);
			long next = Chronicle.day(level);
			LifeStages.round(level, hallPos, List.of(bram, tom));
			LifeStages.round(level, hallPos, List.of(bram, tom));
			helper.assertTrue(LifeStages.isElder(bram, next) && LifeStages.grownDays(bram, next) == LifeStages.ELDER_DAYS && !LifeStages.isElder(tom, next),
				"a day on, Bram is an elder (grown " + LifeStages.grownDays(bram, next) + " days) and Tom is not");
			long noted = hall.chronicle().stream().filter(e -> e.text().getString().equals("Bram is an elder now")).count();
			helper.assertTrue(noted == 1, "the chronicle noted Bram " + noted + " times");
			VillageNeeds.forget();
			Moods.forget();
			ItemLore lore = VillageHallScreen.person(level, hallPos, bram).get(DataComponents.LORE);
			List<String> card = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
			helper.assertTrue(card.contains("Elder · grown " + LifeStages.ELDER_DAYS + " days"), "Bram's card: " + card);
			check.accept(Moods.work(level, bram), card);
			helper.assertTrue(Moods.work(level, tom).good().stream().noneMatch(r -> r.getString().equals("a quiet old age")), "Tom has a quiet old age");
		} finally {
			level.setDayTime(clock);
		}
	}

	private static List<String> texts(List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	/**
	 * B451, the scene's player situation with a real bed: Bram, fed and sleeping in a bed of his own, a day on is an elder
	 * and "a quiet old age" is the first good reason in his mood and on his hall card (34.19). Tom, younger, has none.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaEldersScene"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaEldersScene")
	public void theEldersSceneWithARealBedHasAQuietOldAgeOnTheCard(GameTestHelper helper) {
		Villager[] v = elders(helper);
		helper.runAfterDelay(5, () -> {
			sceneDay(helper, v[0], v[1], () -> Beds.home(v[0], helper.absolutePos(new BlockPos(3, 2, 3))), (mood, card) -> {
				helper.assertTrue(!mood.good().isEmpty() && mood.good().get(0).getString().equals("a quiet old age"), "Bram's mood: " + texts(mood.good()));
				helper.assertTrue(card.stream().anyMatch(l -> l.contains("a quiet old age")), "Bram's card: " + card);
			});
			helper.succeed();
		});
	}

	/**
	 * B451, exactly as the elders scene stages Bram's bed: one {@code RED_BED} block in its default state (the foot half,
	 * no head) and his HOME memory on it. That is no bed a villager can sleep in, so he has no bed ("no bed" in his mood)
	 * and no quiet old age: what the scene saw. The mod is right since B362; the scene's bed is wrong.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaEldersSceneFootBed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaEldersSceneFootBed")
	public void theEldersScenesLoneFootHalfBedHousesNobody(GameTestHelper helper) {
		Villager[] v = elders(helper);
		BlockPos bed = new BlockPos(3, 2, 3);
		helper.setBlock(bed, Blocks.RED_BED); // as the scene: level.setBlockAndUpdate(BED, Blocks.RED_BED.defaultBlockState())
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos abs = helper.absolutePos(bed);
			helper.assertTrue(level.getBlockState(abs).is(Blocks.RED_BED), "the scene's bed block is there");
			sceneDay(helper, v[0], v[1], () -> v[0].getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), abs)), (mood, card) -> {
				helper.assertTrue(VillageNeeds.bed(level, v[0]) == null, "a lone foot half counted as a bed");
				helper.assertTrue(texts(mood.bad()).contains("no bed") || mood.good().stream().noneMatch(r -> r.getString().equals("a bed of their own")),
					"Bram's mood without a real bed: good " + texts(mood.good()) + ", bad " + texts(mood.bad()));
				helper.assertTrue(mood.good().stream().noneMatch(r -> r.getString().equals("a quiet old age")), "a quiet old age with no real bed: " + texts(mood.good()));
			});
			helper.succeed();
		});
	}

	// --- B452: the class_switch_on scene -------------------------------------------------------------------------------

	/** The scene's "builder's cottage": a recorded finished building of size 6×5×6 at {@code origin} (relative). */
	private static void cottage(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_switch_on_cottage");
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin).above(40), new Vec3i(6, 5, 6), false, Blocks.AIR);
		BuildSiteManager.get(level).recordFinished(id, new BlockData(level, helper.absolutePos(origin)).placement(), UUID.randomUUID());
		Leftovers.after(helper, () -> Leftovers.finished(helper));
	}

	private record BlockData(ServerLevel level, BlockPos origin) {
		BlueprintData.Placement placement() {
			return new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		}
	}

	/**
	 * The scene's village with the real class files: Odo and Pia married, three others; all fed with a varied diet and a
	 * chapel reaching every home (the scene's services hook). Returns the five, Odo and Pia first.
	 */
	private static List<Villager> switchOnVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaClassMigrationGameTests.oldHall(helper, false);
		ClassNeeds.ServiceList services = ClassNeeds.services;
		ClassNeeds.services = (l, hall, home) -> Set.of(AliveWorkplace.id("chapel"));
		Leftovers.after(helper, () -> ClassNeeds.services = services);
		String[] names = {"Odo", "Pia", "Ann", "Bo", "Cy"};
		List<Villager> people = new java.util.ArrayList<>();
		for (int i = 0; i < names.length; i++) {
			Villager v = QaClassMigrationGameTests.villager(helper, i, names[i]);
			QaClassMigrationGameTests.wellFed(level, v, true);
			people.add(v);
		}
		QaClassMigrationGameTests.marry(people.get(0), people.get(1));
		return people;
	}

	/**
	 * B452, the scene's player situation with a real bed: the couple in a builder's cottage (a grade I home), fed now on a
	 * varied diet, a chapel reaching them, seeded at the switch: Artisans (their every need but luxuries holds; 34.22).
	 * The three with no home are Peasants. One chronicle entry, settling in for 7 days.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassSwitchOnScene"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassSwitchOnScene")
	public void theSwitchOnCoupleInACottageWithABedAreArtisans(GameTestHelper helper) {
		List<Villager> people = switchOnVillage(helper);
		cottage(helper, new BlockPos(13, 2, 13));
		BlockPos head = helper.absolutePos(new BlockPos(15, 2, 15));
		Beds.home(people.get(0), head);
		Beds.home(people.get(1), head);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
			helper.assertTrue(ClassNeeds.homeGrade(level, people.get(0)) >= 1, "the cottage isn't Odo's grade I home: " + ClassNeeds.homeGrade(level, people.get(0)));
			long today = Chronicle.day(level);
			QaClassMigrationGameTests.seedAll(level, hall, today);
			helper.assertTrue(hall.classesVersion() == ClassMigration.VERSION, "not every villager was seeded");
			helper.assertTrue(QaClassMigrationGameTests.cls(people.get(0)).equals("artisan") && QaClassMigrationGameTests.cls(people.get(1)).equals("artisan"),
				"the couple in the cottage: " + QaClassMigrationGameTests.cls(people.get(0)) + " / " + QaClassMigrationGameTests.cls(people.get(1)));
			for (Villager v : people.subList(2, 5)) {
				helper.assertTrue(QaClassMigrationGameTests.cls(v).equals("peasant"), v.getName().getString() + " with no home: " + QaClassMigrationGameTests.cls(v));
			}
			long entries = hall.chronicle().stream().filter(e -> e.text().getString().contains("found their places")).count();
			helper.assertTrue(entries == 1, "chronicle entries: " + entries);
			helper.assertTrue(ClassMigration.daysLeft(level, helper.absolutePos(HALL), today) == 7, "settling in: " + ClassMigration.daysLeft(level, helper.absolutePos(HALL), today));
			helper.succeed();
		});
	}

	/**
	 * B452, exactly as the class_switch_on scene stages the couple: their HOME memory points into the recorded cottage,
	 * but no bed was ever put there (the cottage is an empty structure). Since B362 a home with no bed houses nobody, so
	 * they have no home grade and are seeded Peasants: what the scene saw. The mod is right; the scene needs a bed.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassSwitchOnSceneNoBed"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassSwitchOnSceneNoBed")
	public void theSwitchOnScenesCoupleWithNoBedArePeasants(GameTestHelper helper) {
		List<Villager> people = switchOnVillage(helper);
		cottage(helper, new BlockPos(13, 2, 13));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			GlobalPos home = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(15, 3, 15))); // the scene's sky.offset(2, 1, 2)
			people.get(0).getBrain().setMemory(MemoryModuleType.HOME, home);
			people.get(1).getBrain().setMemory(MemoryModuleType.HOME, home);
			helper.assertTrue(VillageNeeds.bed(level, people.get(0)) == null, "a HOME memory with no bed counted as a bed");
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
			QaClassMigrationGameTests.seedAll(level, hall, Chronicle.day(level));
			helper.assertTrue(hall.classesVersion() == ClassMigration.VERSION, "not every villager was seeded");
			helper.assertTrue(QaClassMigrationGameTests.cls(people.get(0)).equals("peasant") && QaClassMigrationGameTests.cls(people.get(1)).equals("peasant"),
				"the couple with no bed: " + QaClassMigrationGameTests.cls(people.get(0)) + " / " + QaClassMigrationGameTests.cls(people.get(1)));
			helper.succeed();
		});
	}
}
