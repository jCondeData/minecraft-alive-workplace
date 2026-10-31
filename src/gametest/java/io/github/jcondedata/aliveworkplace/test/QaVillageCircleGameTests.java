package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Mourning;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.Vec3;

/**
 * QA for B548, B549 and ROADMAP 34.22, written from the spec: the village is the circle of {@link VillageHalls#RADIUS}
 * round its hall and {@code HEIGHT} up or down ({@link VillageHalls#contains}), not the square box round it. The corner
 * tests elsewhere (FamilyTreeGameTests, MourningGameTests, ClassGameTests, QaClassMigrationBugTests) put a villager 15/15
 * out; these press on the circle's own edges from both sides: on the axis exactly {@code RADIUS} out (in) and one past
 * (out), the farthest diagonal inside and the nearest one outside, and the vertical bound. Each test runs alone in its
 * batch with a 16-block village; the far villagers stand outside the test area with no AI (they don't fall or wander).
 */
public class QaVillageCircleGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	// --- the circle's edges, from the real RADIUS and contains -------------------------------------------------------

	/** The largest {@code d} with (d, d) inside the circle; (d, d + 1) is then outside it. */
	static int diagonal() {
		int r = VillageHalls.RADIUS;
		int d = (int) Math.floor(r / Math.sqrt(2));
		while (2L * (d + 1) * (d + 1) <= (long) r * r) {
			d++;
		}
		while (2L * d * d > (long) r * r) {
			d--;
		}
		return d;
	}

	/** The highest block above the hall still in its village (HEIGHT), found through {@link VillageHalls#contains}. */
	static int height(BlockPos hall) {
		int h = 0;
		while (h < 512 && VillageHalls.contains(hall, hall.above(h + 1))) {
			h++;
		}
		return h;
	}

	/** Checks the staging: each of {@code in} is in the village, each of {@code out} isn't. */
	static void staged(GameTestHelper helper, BlockPos hall, List<BlockPos> in, List<BlockPos> out) {
		for (BlockPos p : in) {
			helper.assertTrue(VillageHalls.contains(hall, p), "staging: " + p.subtract(hall) + " isn't in the village");
		}
		for (BlockPos p : out) {
			helper.assertTrue(!VillageHalls.contains(hall, p), "staging: " + p.subtract(hall) + " is in the village");
		}
	}

	/** A villager with no AI standing (or hanging) at the absolute {@code pos}, which may be outside the test area. */
	static Villager at(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		helper.getLevel().addFreshEntity(v);
		return v;
	}

	private static String names(List<Villager> who) {
		return who.stream().map(v -> v.getName().getString()).toList().toString();
	}

	// --- 34.22 / B548: seeding --------------------------------------------------------------------------------------

	/**
	 * Seeding an old village takes a villager exactly RADIUS out on each axis and one on the farthest diagonal inside,
	 * and leaves out one a block past the edge on the axis and one on the nearest diagonal outside; the chronicle line
	 * counts only the four inside.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCircleSeedEdges"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCircleSeedEdges")
	public void seedingTakesTheEdgeNotOnePast(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = QaClassMigrationGameTests.oldHall(helper, false);
		BlockPos h = hall.getBlockPos();
		int r = VillageHalls.RADIUS;
		int d = diagonal();
		List<BlockPos> in = List.of(h.offset(r, 0, 0), h.offset(0, 0, -r), h.offset(-r, 0, 0), h.offset(d, 0, d));
		List<BlockPos> out = List.of(h.offset(r + 1, 0, 0), h.offset(0, 0, r + 1), h.offset(-d, 0, -(d + 1)));
		staged(helper, h, in, out);
		List<Villager> ins = new ArrayList<>();
		List<Villager> outs = new ArrayList<>();
		for (int i = 0; i < in.size(); i++) {
			ins.add(at(helper, in.get(i), "In" + i));
		}
		for (int i = 0; i < out.size(); i++) {
			outs.add(at(helper, out.get(i), "Out" + i));
		}
		QaClassMigrationGameTests.seedAll(level, hall, Chronicle.day(level));
		List<Villager> unseeded = ins.stream().filter(v -> !ModAttachments.SOCIAL_CLASS.has(v)).toList();
		List<Villager> wronglySeeded = outs.stream().filter(ModAttachments.SOCIAL_CLASS::has).toList();
		helper.assertTrue(unseeded.isEmpty(), "villagers on the village's edge weren't seeded: " + names(unseeded));
		helper.assertTrue(wronglySeeded.isEmpty(), "villagers just past the village's edge were seeded: " + names(wronglySeeded));
		String name = VillageHalls.name(level, h).getString();
		List<String> lines = hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CLASS).map(e -> e.text().getString()).toList();
		helper.assertTrue(lines.equals(List.of("The people of " + name + " found their places: 4 peasants")), "the chronicle: " + lines);
		ins.forEach(Villager::discard);
		outs.forEach(Villager::discard);
		helper.succeed();
	}

	/** Seeding takes a villager HEIGHT blocks above the hall and leaves out one a block higher. */
	//$ gametest_ticks_batch AREA '200' '"qaCircleSeedHeight"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCircleSeedHeight")
	public void seedingTakesTheTopNotOneAbove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = QaClassMigrationGameTests.oldHall(helper, false);
		BlockPos h = hall.getBlockPos();
		int top = height(h);
		helper.assertTrue(top == 32, "the village's height: " + top + ", not the 32 the hall's HEIGHT promises");
		BlockPos high = h.offset(1, top, 0);
		BlockPos tooHigh = h.offset(-1, top + 1, 0);
		staged(helper, h, List.of(high), List.of(tooHigh));
		Villager roofer = at(helper, high, "Roofer");
		Villager kite = at(helper, tooHigh, "Kite");
		QaClassMigrationGameTests.seedAll(level, hall, Chronicle.day(level));
		helper.assertTrue(ModAttachments.SOCIAL_CLASS.has(roofer), "a villager HEIGHT blocks above the hall wasn't seeded");
		helper.assertTrue(!ModAttachments.SOCIAL_CLASS.has(kite), "a villager above the village's height was seeded");
		roofer.discard();
		kite.discard();
		helper.succeed();
	}

	/**
	 * The one chat line reaches a player exactly RADIUS out on the axis and one on the farthest diagonal inside, and not
	 * one a block past the axis edge or on the nearest diagonal outside.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCircleSeedHeard"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCircleSeedHeard")
	public void theChatLineReachesTheEdgeNotOnePast(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = QaClassMigrationGameTests.oldHall(helper, false);
		QaClassMigrationGameTests.singles(helper, 2);
		BlockPos h = hall.getBlockPos();
		int r = VillageHalls.RADIUS;
		int d = diagonal();
		BlockPos edge = h.offset(0, 0, r);
		BlockPos diag = h.offset(-d, 0, d);
		BlockPos past = h.offset(0, 0, -(r + 1));
		BlockPos diagPast = h.offset(d + 1, 0, d);
		staged(helper, h, List.of(edge, diag), List.of(past, diagPast));
		List<Component> onEdge = new ArrayList<>();
		List<Component> onDiag = new ArrayList<>();
		List<Component> onPast = new ArrayList<>();
		List<Component> onDiagPast = new ArrayList<>();
		QaClassMigrationGameTests.listener(helper, onEdge, Vec3.atBottomCenterOf(edge), "qa-edge");
		QaClassMigrationGameTests.listener(helper, onDiag, Vec3.atBottomCenterOf(diag), "qa-diag");
		QaClassMigrationGameTests.listener(helper, onPast, Vec3.atBottomCenterOf(past), "qa-past");
		QaClassMigrationGameTests.listener(helper, onDiagPast, Vec3.atBottomCenterOf(diagPast), "qa-diag-past");
		QaClassMigrationGameTests.seedAll(level, hall, Chronicle.day(level));
		long e = QaClassMigrationGameTests.heardSeeding(onEdge);
		long g = QaClassMigrationGameTests.heardSeeding(onDiag);
		long p = QaClassMigrationGameTests.heardSeeding(onPast);
		long q = QaClassMigrationGameTests.heardSeeding(onDiagPast);
		helper.assertTrue(e == 1 && g == 1 && p == 0 && q == 0,
			"heard the line: on the axis edge " + e + ", farthest diagonal in " + g + " (both 1); one past the edge " + p + ", nearest diagonal out " + q + " (both 0)");
		helper.succeed();
	}

	// --- B549: the class dawn ---------------------------------------------------------------------------------------

	/** A Village Hall at {@link #HALL} with classes on and a 16-block village; everything shared put back afterwards. */
	static VillageHallBlockEntity classVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply(); // classes off again (GameTests)
			ClassNeeds.services = io.github.jcondedata.aliveworkplace.hall.Services.HOOK;
			ClassNeeds.luxuryEvery = io.github.jcondedata.aliveworkplace.people.Luxuries.HOOK;
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		return hall;
	}

	/** Four classes whose every step up needs only a plain diet. */
	static void plainLadder() {
		String plain = "{\"type\": \"diet\", \"kind\": \"plain\"}";
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(AliveWorkplace.id("peasant"), JsonParser.parseString("{\"tier\": 0, \"tax\": 1}"));
		files.put(AliveWorkplace.id("artisan"), JsonParser.parseString("{\"tier\": 1, \"tax\": 1.5, \"needs\": [" + plain + "]}"));
		files.put(AliveWorkplace.id("burgher"), JsonParser.parseString("{\"tier\": 2, \"tax\": 2.5, \"needs\": [" + plain + "]}"));
		files.put(AliveWorkplace.id("noble"), JsonParser.parseString("{\"tier\": 3, \"tax\": 4, \"needs\": [" + plain + "]}"));
		SocialClasses.load(files);
	}

	private static void wholeDay(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long day) {
		for (int round = 0; round < 20 && SocialClasses.round(level, hall, entity, day) != null; round++) {
		}
	}

	private static String cls(Villager v) {
		ResourceLocation c = ModAttachments.SOCIAL_CLASS.get(v);
		return c == null ? "none" : c.getPath();
	}

	/**
	 * Peasants eating plainly exactly RADIUS out on the axis and on the farthest diagonal inside rise after two dawns;
	 * ones a block past the axis edge and on the nearest diagonal outside aren't counted at all (their progress untouched).
	 */
	//$ gametest_batch AREA '"qaCircleDawnEdges"'
	@GameTest(template = AREA, batch = "qaCircleDawnEdges")
	public void theDawnTakesTheEdgeNotOnePast(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = classVillage(helper);
		plainLadder();
		BlockPos h = helper.absolutePos(HALL);
		int r = VillageHalls.RADIUS;
		int d = diagonal();
		List<BlockPos> in = List.of(h.offset(-r, 0, 0), h.offset(d, 0, -d));
		List<BlockPos> out = List.of(h.offset(-(r + 1), 0, 0), h.offset(d + 1, 0, -d));
		staged(helper, h, in, out);
		List<Villager> ins = new ArrayList<>();
		List<Villager> outs = new ArrayList<>();
		for (int i = 0; i < in.size(); i++) {
			ins.add(at(helper, in.get(i), "In" + i));
			outs.add(at(helper, out.get(i), "Out" + i));
		}
		SocialClasses.SocialClass peasant = SocialClasses.get(AliveWorkplace.id("peasant"));
		for (Villager v : java.util.stream.Stream.concat(ins.stream(), outs.stream()).toList()) {
			SocialClasses.seed(v, peasant);
			ModAttachments.RECENT_MEALS.set(v, List.of(ResourceLocation.withDefaultNamespace("bread"), ResourceLocation.withDefaultNamespace("carrot"),
				ResourceLocation.withDefaultNamespace("apple")));
		}
		wholeDay(level, h, hall, 101);
		wholeDay(level, h, hall, 102);
		for (Villager v : ins) {
			helper.assertTrue(cls(v).equals("artisan"), v.getName().getString() + ", on the village's edge, didn't rise: " + cls(v) + " " + SocialClasses.progress(v));
		}
		for (Villager v : outs) {
			helper.assertTrue(cls(v).equals("peasant") && SocialClasses.progress(v).day() == 0,
				v.getName().getString() + ", just past the village's edge, was counted at its dawns: " + cls(v) + " " + SocialClasses.progress(v));
		}
		ins.forEach(Villager::discard);
		outs.forEach(Villager::discard);
		helper.succeed();
	}

	// --- B549: the family book --------------------------------------------------------------------------------------

	/**
	 * The hall's family round writes in villagers exactly RADIUS out on the axis, on the farthest diagonal inside and
	 * HEIGHT blocks above the hall, and leaves out one a block past each.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCircleFamilyEdges"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCircleFamilyEdges")
	public void theFamilyBookTakesTheEdgeAndTheTop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = classVillage(helper); // (classes on make no difference to the book)
		hall.setNeeds(new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f));
		VillageNeeds.forget();
		Moods.forget();
		BlockPos h = helper.absolutePos(HALL);
		int r = VillageHalls.RADIUS;
		int d = diagonal();
		int top = height(h);
		List<BlockPos> in = List.of(h.offset(0, 0, r), h.offset(-d, 0, -d), h.offset(2, top, 2));
		List<BlockPos> out = List.of(h.offset(0, 0, r + 1), h.offset(-(d + 1), 0, -d), h.offset(-2, top + 1, -2));
		staged(helper, h, in, out);
		List<Villager> ins = new ArrayList<>();
		List<Villager> outs = new ArrayList<>();
		for (int i = 0; i < in.size(); i++) {
			ins.add(at(helper, in.get(i), "In" + i));
			outs.add(at(helper, out.get(i), "Out" + i));
		}
		FamilyBook.round(level, h, hall);
		List<Villager> missing = ins.stream().filter(v -> hall.families().get(v.getUUID()) == null).toList();
		List<Villager> extra = outs.stream().filter(v -> hall.families().get(v.getUUID()) != null || FamilyBook.family(v) != 0).toList();
		helper.assertTrue(missing.isEmpty(), "villagers inside the village aren't in its book: " + names(missing));
		helper.assertTrue(extra.isEmpty(), "villagers just past the village's edge were written into its book: " + names(extra));
		ins.forEach(Villager::discard);
		outs.forEach(Villager::discard);
		helper.succeed();
	}

	// --- B549: weddings ---------------------------------------------------------------------------------------------

	/**
	 * Two courting couples, due to marry at midday: Ann (exactly RADIUS out on the axis) and Ben (by the hall) marry;
	 * Cal (a block past the edge) and Dee (by the hall) don't, as Cal isn't in the village. The wedding's festival day
	 * reaches Dee, by the hall, and a villager on the axis edge, but not Cal or one a block past the edge.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCircleWedding"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCircleWedding")
	public void aWeddingNeedsBothInTheVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		classVillage(helper);
		SocialClasses.ENABLED = false;
		boolean couples = Couples.ENABLED;
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			Couples.ENABLED = couples;
			level.setDayTime(time);
		});
		Couples.ENABLED = true;
		level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 7000);
		long today = Chronicle.day(level);
		BlockPos h = helper.absolutePos(HALL);
		int r = VillageHalls.RADIUS;
		staged(helper, h, List.of(h.offset(r, 0, 0), h.offset(0, 0, r)), List.of(h.offset(-(r + 1), 0, 0), h.offset(0, 0, -(r + 1))));
		Villager ann = at(helper, h.offset(r, 0, 0), "Ann");
		Villager ben = at(helper, h.offset(2, 0, 0), "Ben");
		Villager cal = at(helper, h.offset(-(r + 1), 0, 0), "Cal");
		Villager dee = at(helper, h.offset(-2, 0, 0), "Dee");
		Villager eve = at(helper, h.offset(0, 0, r), "Eve");
		Villager fox = at(helper, h.offset(0, 0, -(r + 1)), "Fox");
		for (Villager[] pair : List.of(new Villager[] {ann, ben}, new Villager[] {cal, dee}, new Villager[] {eve, fox})) {
			boolean married = pair[0] == eve; // (Eve and Fox, married long ago, just look on)
			ModAttachments.PARTNER.set(pair[0], new Couples.Partner(pair[1].getUUID(), pair[1].getDisplayName(), married ? 1 : today - 3, married));
			ModAttachments.PARTNER.set(pair[1], new Couples.Partner(pair[0].getUUID(), pair[0].getDisplayName(), married ? 1 : today - 3, married));
		}
		Couples.round(level, h);
		helper.assertTrue(Couples.partner(ann).married() && Couples.partner(ben).married(), "Ann, on the village's edge, and Ben didn't marry");
		helper.assertTrue(!Couples.partner(cal).married() && !Couples.partner(dee).married(), "Cal, a block past the village's edge, married Dee");
		helper.assertTrue(Long.valueOf(today).equals(ModAttachments.FESTIVAL_DAY.get(dee)) && Long.valueOf(today).equals(ModAttachments.FESTIVAL_DAY.get(eve)),
			"the wedding's festival missed someone in the village: Dee " + ModAttachments.FESTIVAL_DAY.get(dee) + ", Eve " + ModAttachments.FESTIVAL_DAY.get(eve));
		helper.assertTrue(!Long.valueOf(today).equals(ModAttachments.FESTIVAL_DAY.get(cal)) && !Long.valueOf(today).equals(ModAttachments.FESTIVAL_DAY.get(fox)),
			"the wedding's festival reached villagers outside the village: Cal " + ModAttachments.FESTIVAL_DAY.get(cal) + ", Fox " + ModAttachments.FESTIVAL_DAY.get(fox));
		List.of(ann, ben, cal, dee, eve, fox).forEach(Villager::discard);
		helper.succeed();
	}

	// --- B549: mourning ---------------------------------------------------------------------------------------------

	/** A hall (16-block village) with mourning's switches put back when the test ends; ready after a few ticks. */
	static void mourningVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
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
		Mourning.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	static void mourningReady(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		VillageHalls.forgetNearest();
	}

	static Villager worker(GameTestHelper helper, BlockPos absolute, String name, VillagerProfession job) {
		Villager v = at(helper, absolute, name);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		return v;
	}

	/**
	 * A death by the hall: her sons exactly RADIUS out on the axis and HEIGHT blocks above the hall grieve; her sons a
	 * block past the axis edge and a block above the village's top don't.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCircleMourningEdges"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCircleMourningEdges")
	public void kinOnTheEdgeGrieveKinOnePastDont(GameTestHelper helper) {
		mourningVillage(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			mourningReady(helper);
			long today = Chronicle.day(level);
			BlockPos h = helper.absolutePos(HALL);
			int r = VillageHalls.RADIUS;
			int top = height(h);
			List<BlockPos> in = List.of(h.offset(0, 0, -r), h.offset(1, top, 1));
			List<BlockPos> out = List.of(h.offset(0, 0, -(r + 1)), h.offset(-1, top + 1, -1));
			staged(helper, h, in, out);
			Villager mira = worker(helper, h.offset(3, 0, 3), "Mira", VillagerProfession.FARMER);
			List<Villager> grieve = List.of(worker(helper, in.get(0), "Pip", VillagerProfession.FLETCHER), worker(helper, in.get(1), "Sam", VillagerProfession.MASON));
			List<Villager> dont = List.of(worker(helper, out.get(0), "Rob", VillagerProfession.FLETCHER), worker(helper, out.get(1), "Tim", VillagerProfession.MASON));
			for (Villager son : java.util.stream.Stream.concat(grieve.stream(), dont.stream()).toList()) {
				ModAttachments.PARENTS.set(son, new Families.Parents(Component.literal("Mira"), Component.literal("Tom"), "", "", true));
			}
			Mourning.onDeath(level, mira, Component.literal("Mira fell"), null);
			List<Villager> dry = grieve.stream().filter(v -> !Mourning.grieving(v, today)).toList();
			List<Villager> wet = dont.stream().filter(v -> Mourning.grieving(v, today) || ModAttachments.GRIEF.has(v)).toList();
			helper.assertTrue(dry.isEmpty(), "her sons inside the village don't grieve: " + names(dry));
			helper.assertTrue(wet.isEmpty(), "her sons just outside the village grieve: " + names(wet));
			mira.discard();
			grieve.forEach(Villager::discard);
			dont.forEach(Villager::discard);
			helper.succeed();
		});
	}
}
