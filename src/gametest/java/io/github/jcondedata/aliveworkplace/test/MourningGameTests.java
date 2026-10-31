package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Mourning;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * ROADMAP 36.16: villagers live ten times longer (an elder after 1200 grown days, passing 400 elder days later; an old
 * save's villager keeps the day they grew up and counts it against the new span), and a death matters: the players near
 * are told who died, their job and years, the chronicle says the same, family and partner mourn for some days and then
 * stop, and the Undertaker leads a funeral at the grave. {@code villageMourning} off: none of it.
 */
public class MourningGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** The new spans: not an elder at 1199 grown days, one at 1200; an elder of the old 120 days is grown again; a file of before moves to 1200. */
	//$ gametest_ticks_batch AREA '100' '"mourningSpans"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "mourningSpans")
	public void aVillagerIsAnElderOnlyAfterTheNewSpan(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.assertTrue(LifeStages.ELDER_DAYS == 1200 && LifeStages.PASSING_DAYS == 400 && new WorkplaceConfig().villagerElderDays == 1200, "the spans: "
			+ LifeStages.ELDER_DAYS + ", " + LifeStages.PASSING_DAYS);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FARMER);
		ServerLevel level = helper.getLevel();
		long today = Chronicle.day(level);
		LifeStages.setAdultSince(bram, today - 1199);
		helper.assertTrue(!LifeStages.isElder(bram, today) && LifeStages.isElder(bram, today + 1) && LifeStages.daysLeft(bram, today + 1) == 400,
			"an elder from 1200 grown days, with 400 days left");
		helper.assertTrue(LifeStages.daysLeft(bram, today + 400) == 1 && LifeStages.daysLeft(bram, today + 401) == 0, "the night of their passing: "
			+ LifeStages.daysLeft(bram, today + 401));
		// 160 grown days was an elder's whole life before; now it's early in a grown one's.
		LifeStages.setAdultSince(bram, today - 160);
		helper.assertTrue(!LifeStages.isElder(bram, today) && LifeStages.daysLeft(bram, today) == -1 && LifeStages.stage(bram, today) == LifeStages.Stage.GROWN,
			"160 grown days");
		// Years: 18 when grown up, a year more each four seasons.
		helper.assertTrue(Mourning.years(bram, today) == Mourning.GROWN_UP_AGE + 160 / Mourning.yearDays(), "years: " + Mourning.years(bram, today));
		// A config file of before: its 120 was the old default and moves; another number, or a file of the new format, is a choice.
		helper.assertTrue(WorkplaceConfig.parse("{\"villagerElderDays\": 120}").villagerElderDays == 1200, "a file without a format");
		helper.assertTrue(WorkplaceConfig.parse("{\"configVersion\": 3, \"villagerElderDays\": 120}").villagerElderDays == 1200, "a format 3 file");
		helper.assertTrue(WorkplaceConfig.parse("{\"configVersion\": 3, \"villagerElderDays\": 300}").villagerElderDays == 300, "a choice of 300 was lost");
		helper.assertTrue(WorkplaceConfig.parse("{\"configVersion\": 4, \"villagerElderDays\": 120}").villagerElderDays == 120, "a choice of 120 was lost");
		String written = new com.google.gson.Gson().toJson(WorkplaceConfig.parse("{\"villagerElderDays\": 120}"));
		helper.assertTrue(WorkplaceConfig.parse(written).villagerElderDays == 1200 && written.contains("\"configVersion\":5"), "written back: " + written);
		helper.succeed();
	}

	/** A villager from a save of before: the same day they grew up after a reload; an elder under the old span is grown again, noted anew at 1200 days. */
	//$ gametest_ticks_batch AREA '100' '"mourningOldSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "mourningOldSave")
	public void anOldSavesVillagerKeepsTheirAge(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FARMER);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			long today = Chronicle.day(level);
			// As a 0.x save left him: grown 130 days, noted an elder by the old 120-day span.
			LifeStages.setAdultSince(bram, today - 130);
			ModAttachments.ELDER_NOTED.set(bram, true);
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(bram.saveWithoutId(new CompoundTag()));
			helper.assertTrue(Long.valueOf(today - 130).equals(LifeStages.adultSince(copy)) && LifeStages.grownDays(copy, today) == 130,
				"the day he grew up after a reload: " + LifeStages.adultSince(copy));
			helper.assertTrue(!LifeStages.isElder(copy, today), "an elder at 130 grown days");
			LifeStages.round(level, hallPos, List.of(bram));
			helper.assertTrue(!ModAttachments.ELDER_NOTED.has(bram), "still noted an elder");
			helper.assertTrue(LifeStages.isElder(bram, today + 1070) && !LifeStages.isElder(bram, today + 1069), "an elder at 1200 grown days");
			// Under the new span he is noted when he gets there.
			LifeStages.setAdultSince(bram, today - 1200);
			LifeStages.round(level, hallPos, List.of(bram));
			helper.assertTrue(chronicle(hall).contains("Bram is an elder now"), "noted at 1200: " + chronicle(hall));
			helper.succeed();
		});
	}

	/**
	 * Mira, the village's Farmer, dies: the player near hears who, her job and years; the chronicle says the same after the
	 * cause; Pip (her son) grieves and works slower, Tom (her husband) mourns, Odo (no kin) doesn't; after the days it ends.
	 */
	//$ gametest_ticks_batch AREA '200' '"mourningDeath"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "mourningDeath")
	public void aDeathIsMournedAndTheMourningEnds(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager mira = villager(helper, new BlockPos(5, 2, 6), "Mira", VillagerProfession.FARMER);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.FARMER);
		Villager pip = villager(helper, new BlockPos(9, 2, 6), "Pip", VillagerProfession.FLETCHER);
		Villager odo = villager(helper, new BlockPos(13, 2, 6), "Odo", VillagerProfession.MASON);
		List<Component> seen = new ArrayList<>();
		boolean moods = Moods.ENABLED;
		Leftovers.after(helper, () -> Moods.ENABLED = moods);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = ready(helper);
			Moods.ENABLED = true; // (off in GameTests otherwise)
			long today = Chronicle.day(level);
			ServerPlayer player = WonderFinishedGameTests.listener(helper, seen, "Mourner");
			BlockPos at = helper.absolutePos(new BlockPos(11, 2, 4));
			player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			LifeStages.setAdultSince(mira, today - 5L * Mourning.yearDays()); // 23 years old
			ModAttachments.PARTNER.set(mira, new Couples.Partner(tom.getUUID(), tom.getDisplayName().copy(), today - 50, true));
			ModAttachments.PARTNER.set(tom, new Couples.Partner(mira.getUUID(), mira.getDisplayName().copy(), today - 50, true));
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Mira"), Component.literal("Tom"), "", "", true));
			seen.clear();
			mira.kill();

			long years = Mourning.GROWN_UP_AGE + 5;
			String told = "Mira, the village's Farmer, has died aged " + years + ". The village mourns.";
			helper.assertTrue(seen.stream().anyMatch(c -> c.getString().equals(told)), "told: " + seen.stream().map(Component::getString).toList());
			helper.assertTrue(hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.DEATH && e.text().getString().startsWith("Mira")
				&& e.text().getString().endsWith(": the village's Farmer, aged " + years)), "chronicle: " + chronicle(hall));
			List<BlockPos> graves = Graves.near(level, mira.blockPosition(), 4);
			helper.assertTrue(graves.size() == 1 && Mourning.funeralDue(level, graves.get(0)), "a grave waiting for its funeral: " + graves);

			// The family: Pip grieves, Tom mourns, Odo doesn't.
			helper.assertTrue(Mourning.grieving(pip, today) && !Mourning.grieving(odo, today) && !Mourning.grieving(tom, today), "who grieves");
			helper.assertTrue(Couples.isMourning(level, tom) && Mourning.mourning(level, tom) && Mourning.mourning(level, pip) && !Mourning.mourning(level, odo), "who mourns");
			Moods.Mood mood = Moods.fresh(pip);
			helper.assertTrue(mood != null && mood.bad().stream().anyMatch(c -> c.getString().equals("grieving for family")), "Pip's mood: " + mood);
			helper.assertTrue(Mourning.pace(pip) == Mourning.PACE && Mourning.pace(tom) == Mourning.PACE && Mourning.pace(odo) == 1f, "paces");
			helper.assertTrue(Pace.sources().stream().anyMatch(s -> s.id().equals("mourning")), "the pace source");
			helper.assertTrue(Component.translatable("pace.aliveworkplace.source.mourning").getString().equals("mourning"), "the pace's words");

			// Saved and loaded: Pip still grieves her.
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(pip.saveWithoutId(new CompoundTag()));
			Mourning.Grief grief = ModAttachments.GRIEF.get(copy);
			helper.assertTrue(grief != null && grief.day() == today && grief.name().getString().equals("Mira"), "Pip's grief after a reload: " + grief);

			// It ends: three days on for Pip (the hall's round takes it off), five for Tom.
			helper.assertTrue(Mourning.grieving(pip, today + Mourning.GRIEF_DAYS - 1) && !Mourning.grieving(pip, today + Mourning.GRIEF_DAYS), "Pip's days");
			Mourning.endIfOver(level, pip, today + Mourning.GRIEF_DAYS - 1);
			helper.assertTrue(ModAttachments.GRIEF.has(pip), "over too soon");
			Mourning.endIfOver(level, pip, today + Mourning.GRIEF_DAYS);
			helper.assertTrue(!ModAttachments.GRIEF.has(pip) && !Mourning.grieving(pip, today) && Mourning.pace(pip) == 1f, "Pip still grieves");
			Moods.forget(pip);
			Moods.Mood after = Moods.fresh(pip);
			helper.assertTrue(after != null && after.bad().stream().noneMatch(c -> c.getString().equals("grieving for family")), "Pip's mood after: " + after);
			ModAttachments.WIDOWED_DAY.set(tom, today - Couples.MOURNING_DAYS);
			helper.assertTrue(!Mourning.mourning(level, tom) && Mourning.pace(tom) == 1f, "Tom still mourns after " + Couples.MOURNING_DAYS + " days");
			helper.succeed();
		});
	}

	/** An unnamed villager is "the village's Mason"; a villager nobody counted is "at an age nobody had counted"; outside a village nobody is told. */
	//$ gametest_ticks_batch AREA '100' '"mourningWords"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "mourningWords")
	public void theWordsOfADeath(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long today = Chronicle.day(level);
		Villager mason = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 6));
		mason.setNoAi(true);
		mason.setVillagerData(mason.getVillagerData().setProfession(VillagerProfession.MASON));
		helper.assertTrue(Mourning.told(mason, today).getString().equals("The village's Mason has died at an age nobody had counted. The village mourns."),
			"unnamed: " + Mourning.told(mason, today).getString());
		Villager ida = villager(helper, new BlockPos(7, 2, 6), "Ida", VillagerProfession.NONE);
		LifeStages.setAdultSince(ida, today);
		helper.assertTrue(Mourning.told(ida, today).getString().equals("Ida, the village's villager, has died aged 18. The village mourns."),
			"jobless: " + Mourning.told(ida, today).getString());
		// A child's death says no age and is told as before: the chronicle's line is the cause alone.
		Villager kid = villager(helper, new BlockPos(9, 2, 6), "Kit", VillagerProfession.NONE);
		kid.setBaby(true);
		Component cause = Component.literal("Kit fell");
		helper.assertTrue(Mourning.onDeath(level, kid, cause, null) == cause, "a child");
		for (String key : List.of("message.aliveworkplace.mourning.died", "message.aliveworkplace.mourning.died_unnamed", "message.aliveworkplace.mourning.age",
			"message.aliveworkplace.mourning.age_unknown", "message.aliveworkplace.mourning.no_job", "chronicle.aliveworkplace.mourning.died",
			"chronicle.aliveworkplace.funeral", "mood.aliveworkplace.reason.grieving", "message.aliveworkplace.undertaker.state.funeral",
			"message.aliveworkplace.undertaker.state.funeral_going", "aliveworkplace.config.villageMourning", "aliveworkplace.config.villageMourning.tooltip")) {
			String text = Component.translatable(key, "A", "B", "C").getString();
			helper.assertTrue(!text.equals(key) && !text.contains("aliveworkplace."), "no words for " + key);
		}
		helper.succeed();
	}

	/** With villageMourning off: the chronicle has the cause alone, nobody is told, the family don't grieve, the grave waits for no funeral. */
	//$ gametest_ticks_batch AREA '200' '"mourningOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "mourningOff")
	public void mourningSwitchedOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager mira = villager(helper, new BlockPos(5, 2, 6), "Mira", VillagerProfession.FARMER);
		Villager pip = villager(helper, new BlockPos(9, 2, 6), "Pip", VillagerProfession.FLETCHER);
		List<Component> seen = new ArrayList<>();
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = ready(helper);
			long today = Chronicle.day(level);
			ServerPlayer player = WonderFinishedGameTests.listener(helper, seen, "NoMourner");
			BlockPos at = helper.absolutePos(new BlockPos(11, 2, 4));
			player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			LifeStages.setAdultSince(mira, today - 100);
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Mira"), Component.literal("Tom"), "", "", true));
			// The file's switch reaches the game (at once, the defaults back after, so no other test sees it).
			helper.assertTrue(new WorkplaceConfig().villageMourning && Mourning.ENABLED, "on by default");
			try {
				WorkplaceConfig.parse("{\"villageMourning\": false}").apply();
				helper.assertTrue(!Mourning.ENABLED, "the switch");
			} finally {
				new WorkplaceConfig().apply();
			}
			Mourning.ENABLED = false; // (this test's own batch; on again after it)
			seen.clear();
			mira.kill();
			helper.assertTrue(seen.stream().noneMatch(c -> c.getString().contains("The village mourns")), "told: " + seen.stream().map(Component::getString).toList());
			helper.assertTrue(hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.DEATH && e.text().getString().startsWith("Mira")
				&& !e.text().getString().contains("the village's")), "chronicle: " + chronicle(hall));
			helper.assertTrue(!ModAttachments.GRIEF.has(pip) && !Mourning.grieving(pip, today) && Mourning.pace(pip) == 1f, "Pip grieves");
			List<BlockPos> graves = Graves.near(level, mira.blockPosition(), 4);
			helper.assertTrue(graves.size() == 1 && level.getBlockEntity(graves.get(0)) instanceof GraveBlockEntity g && !g.funeralDue(), "a funeral with mourning off");
			helper.succeed();
		});
	}

	/** The Undertaker leads Mira's funeral at her grave: the chronicle notes it, the grave has had its funeral; a reload keeps one still due. */
	//$ gametest_ticks_batch AREA '1200' '"mourningFuneral"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "mourningFuneral")
	public void theUndertakerLeadsAFuneral(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		village(helper);
		ServerLevel level = helper.getLevel();
		Villager mira = villager(helper, new BlockPos(16, 2, 16), "Mira", VillagerProfession.FARMER);
		Villager tom = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 16));
		tom.setCustomName(Component.literal("Tom"));
		helper.setBlock(new BlockPos(5, 2, 5), ModBlocks.UNDERTAKERS_TABLE);
		Villager undertaker = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		undertaker.setCustomName(Component.literal("Ulric"));
		Jobs.employ(level, undertaker, helper.absolutePos(new BlockPos(5, 2, 5)), ModVillagers.UNDERTAKERS_TABLE_POI, ModVillagers.UNDERTAKER);
		BlockPos[] grave = new BlockPos[1];
		helper.runAfterDelay(5, () -> {
			ready(helper);
			// Graves other tests left beside this area (their villagers died in a hall's village too) would be the
			// undertaker's first funerals: this one is about Mira's.
			for (BlockPos left : Graves.near(level, helper.absolutePos(new BlockPos(5, 2, 5)), 48)) {
				level.removeBlock(left, false);
			}
			mira.kill();
			List<BlockPos> graves = Graves.near(level, helper.absolutePos(new BlockPos(16, 2, 16)), 4);
			helper.assertTrue(graves.size() == 1 && Mourning.funeralDue(level, graves.get(0)), "Mira's grave waits for a funeral");
			grave[0] = graves.get(0);
			// Saved and loaded, the grave still waits for it; an old grave (no flag) never does.
			GraveBlockEntity g = (GraveBlockEntity) level.getBlockEntity(grave[0]);
			CompoundTag tag = g.saveWithoutMetadata(level.registryAccess());
			helper.assertTrue(tag.getBoolean("funeral"), "the flag isn't saved: " + tag.getAllKeys());
			tag.remove("funeral");
			GraveBlockEntity old = new GraveBlockEntity(grave[0], g.getBlockState());
			old.loadWithComponents(tag, level.registryAccess());
			helper.assertTrue(!old.funeralDue(), "an old grave waits for a funeral");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(grave[0] != null, "nobody died yet");
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
			helper.assertTrue(hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.FUNERAL && e.text().getString().startsWith("Mira's funeral, led by Ulric;")
				&& e.text().getString().endsWith("came to say goodbye")), "no funeral yet: " + chronicle(hall) + "; the undertaker at "
				+ helper.relativePos(undertaker.blockPosition())
				+ ", graves near the table " + Graves.near(level, helper.absolutePos(new BlockPos(5, 2, 5)), 32) + ", due " + Mourning.funeralDue(level, grave[0])
				+ ", sleeping " + undertaker.isSleeping() + ", job " + undertaker.getVillagerData().getProfession());
			helper.assertTrue(level.getBlockEntity(grave[0]) instanceof GraveBlockEntity g && !g.funeralDue(), "the grave still waits");
		});
	}

	// --- Helpers ----------------------------------------------------------------------------------------------------

	/** A Village Hall (the hall's radius 16 for the test); the switches are as they were when the test ends. */
	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			LifeStages.AGES = true;
			LifeStages.ELDER_DAYS = 1200;
			Mourning.ENABLED = true;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		return v;
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}

	private static List<String> chronicle(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	/** B549: a dead villager's son standing in the square's corner round the hall, outside the village's circle, doesn't grieve; her son inside does. */
	//$ gametest_ticks_batch AREA '100' '"mourningCorner"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "mourningCorner")
	public void kinOutsideTheVillageDontGrieve(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager mira = villager(helper, new BlockPos(5, 2, 6), "Mira", VillagerProfession.FARMER);
		Villager pip = villager(helper, new BlockPos(9, 2, 6), "Pip", VillagerProfession.FLETCHER);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			ready(helper);
			long today = Chronicle.day(level);
			Villager rob = FamilyTreeGameTests.outsideVillage(helper, helper.absolutePos(HALL), 1, 1, "Rob");
			for (Villager son : List.of(pip, rob)) {
				ModAttachments.PARENTS.set(son, new Families.Parents(Component.literal("Mira"), Component.literal("Tom"), "", "", true));
			}
			Mourning.onDeath(level, mira, Component.literal("Mira fell"), null);
			helper.assertTrue(Mourning.grieving(pip, today), "Pip, in the village, doesn't grieve");
			helper.assertTrue(!Mourning.grieving(rob, today) && !ModAttachments.GRIEF.has(rob), "Rob, outside the village's circle, grieves");
			List.of(mira, pip, rob).forEach(Villager::discard);
			helper.succeed();
		});
	}
}
