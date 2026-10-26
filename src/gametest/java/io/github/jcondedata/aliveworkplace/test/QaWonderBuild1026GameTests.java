package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1026-1538) on the builder side of Wonders (ROADMAP 35.4, 35.5, 35.7): a restart while a stage waits for its
 * goods, between two stages; ticks repeated over a started stage make no second set of parts; the Wonders switch turned
 * off mid-build holds the Wonder where it is and it carries on when back on; a block given up while clearing is still
 * looked at again before the build is called done (B508, the surviving BuildSite mutant); the stage sentences read in
 * English with every argument used.
 */
public class QaWonderBuild1026GameTests implements FabricGameTest {
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");

	private static void finish(BuildSite site) {
		for (int i = 0; i < 20 && !site.isDone(); i++) {
			site.finishList();
		}
	}

	private static void cleanUp(ServerLevel level, UUID wonder) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite s : List.copyOf(sites.all())) {
			if (wonder.equals(s.wonder())) {
				sites.remove(s.id());
			}
		}
		WonderManager.get(level).remove(wonder);
	}

	/** The Wonders and the build sites read back from their saves, as a server restart does. */
	private static void restart(ServerLevel level) {
		var registries = level.registryAccess();
		WonderManager wonders = WonderManager.get(level);
		level.getDataStorage().set(WonderManager.NAME, WonderManager.load(wonders.save(new CompoundTag(), registries), registries));
		BuildSiteManager sites = BuildSiteManager.get(level);
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(sites.save(new CompoundTag(), registries), registries));
	}

	/** Build sites of the Wonder {@code id} not yet done. */
	private static long open(ServerLevel level, UUID id) {
		return BuildSiteManager.get(level).all().stream().filter(s -> id.equals(s.wonder()) && !s.isDone()).count();
	}

	/**
	 * Stage I done, stage II waiting for its goods: a restart then keeps it waiting at stage II with no parts (never
	 * stage I again, never stage II without its goods); once the goods are in it starts exactly once, with stage II's
	 * parts and no others, however many ticks follow and through a second restart mid-stage; and it goes on to the end.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aRestartWhileAStageWaitsForItsGoodsKeepsItWaitingAndStartsItOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null && folly.stages().size() == 3, "the test folly is loaded: " + folly);
		BlockPos origin = new BlockPos(4300, 70, 4000);
		WonderSite begun = WonderManager.get(level).begin(FOLLY, origin, UUID.randomUUID(), "Jesse", 1);
		UUID id = begun.id();
		UUID builder = UUID.randomUUID();
		try {
			BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.CLOCKWISE_180, Mirror.FRONT_BACK);
			List<BuildSite> first = WonderStages.start(level, id, at, origin.offset(-1, 0, -1), builder);
			first.forEach(QaWonderBuild1026GameTests::finish);
			WonderStages.tick(level);
			WonderSite waiting = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(waiting.stage() == 2 && !waiting.started() && waiting.parts().isEmpty(), "stage II waits for its goods: " + waiting);

			restart(level);
			WonderSite read = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(read.equals(waiting), "the waiting Wonder read back as it was: " + read + " vs " + waiting);
			for (int i = 0; i < 5; i++) {
				WonderStages.tick(level);
			}
			WonderSite still = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(still.stage() == 2 && !still.started() && still.parts().isEmpty() && open(level, id) == 0,
				"after the restart stage II started without its goods (or stage I came back): " + still + ", open sites " + open(level, id));

			WonderGoodsKit.handInStage(level, id);
			WonderStages.tick(level);
			WonderSite started = WonderManager.get(level).site(id).orElseThrow();
			int partsOfTwo = folly.stages().get(1).parts().size();
			helper.assertTrue(started.stage() == 2 && started.started() && started.parts().size() == partsOfTwo,
				"stage II started with its " + partsOfTwo + " parts: " + started);
			for (int i = 0; i < 10; i++) {
				WonderStages.tick(level);
			}
			helper.assertTrue(open(level, id) == partsOfTwo, "ticks over a started stage made more parts: " + open(level, id) + " open, " + partsOfTwo + " drawn");
			helper.assertTrue(WonderManager.get(level).site(id).orElseThrow().parts().equals(started.parts()), "the stage's parts changed under it");
			for (UUID part : started.parts()) {
				BuildSite site = BuildSiteManager.get(level).get(part);
				helper.assertTrue(site != null && builder.equals(site.builder()) && site.isQueued() && !site.levelGround(),
					"stage II's part kept for the builder, queued, levelling nothing: " + site);
			}

			restart(level); // a second restart, mid-stage II
			helper.assertTrue(open(level, id) == partsOfTwo, "the restart lost or doubled stage II's parts: " + open(level, id));
			for (int stage = 2; stage <= 3; stage++) {
				for (UUID part : WonderManager.get(level).site(id).orElseThrow().parts()) {
					finish(BuildSiteManager.get(level).get(part));
				}
				WonderStages.tick(level);
				if (stage < 3) {
					WonderGoodsKit.handInStage(level, id);
					WonderStages.tick(level);
				}
			}
			helper.assertTrue(WonderManager.get(level).site(id).orElseThrow().done(), "the folly wasn't finished: " + WonderManager.get(level).site(id));
		} finally {
			cleanUp(level, id);
		}
		helper.succeed();
	}

	/**
	 * Wonders switched off (the config's {@code wonders}) with stage I done: the Wonder waits, saved where it is, through
	 * any number of ticks and a restart; it is neither finished nor moved on, and nothing is taken. Switched back on, it
	 * moves on to stage II as usual.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void switchedOffMidBuildTheWonderWaitsAndCarriesOnWhenBackOn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos origin = new BlockPos(4400, 70, 4000);
		WonderSite begun = WonderManager.get(level).begin(FOLLY, origin, UUID.randomUUID(), "Jesse", 1);
		UUID id = begun.id();
		boolean was = Wonders.ENABLED;
		try {
			BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE);
			List<BuildSite> first = WonderStages.start(level, id, at, origin.offset(-1, 0, -1), null);
			first.forEach(QaWonderBuild1026GameTests::finish);
			WonderSite before = WonderManager.get(level).site(id).orElseThrow();
			Wonders.ENABLED = false;
			for (int i = 0; i < 20; i++) {
				WonderStages.tick(level);
			}
			restart(level);
			WonderStages.tick(level);
			WonderSite off = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(off.stage() == 1 && off.goingUp() && !off.done() && off.parts().equals(before.parts()),
				"switched off, the Wonder moved: " + off + " (was " + before + ")");
			Wonders.ENABLED = true;
			WonderStages.tick(level);
			WonderSite on = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(on.stage() == 2 && on.goingUp(), "back on, stage II didn't follow: " + on);
			WonderGoodsKit.handInStage(level, id);
			WonderStages.tick(level);
			helper.assertTrue(WonderManager.get(level).site(id).orElseThrow().started(), "back on, stage II didn't start with its goods in");
		} finally {
			Wonders.ENABLED = was;
			cleanUp(level, id);
		}
		helper.succeed();
	}

	/**
	 * B508 and the surviving mutant at BuildSite.defer: a block given up for good while CLEARING (a spot the builder
	 * couldn't reach to clear) is not "left out" of the last look before done, because the same spot is a STRUCTURE step
	 * laid later; were it left out, a player breaking that block after it was laid would leave a hole the build calls
	 * done. A STRUCTURE block given up for good is left out (the control), and both survive a save.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBlockGivenUpWhileClearingIsStillLookedAtBeforeDone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos origin = new BlockPos(4500, 70, 4000);
		WonderSite begun = WonderManager.get(level).begin(FOLLY, origin, null, "Test", 1);
		UUID id = begun.id();
		try {
			BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE);
			BuildSite site = WonderStages.start(level, id, at, origin.offset(-1, 0, -1), null).get(0);
			BuildPlan plan = site.plan(level);
			helper.assertTrue(plan != null && site.stage() == BuildPlan.Stage.CLEAR, "a fresh part starts clearing: " + site.stage());
			List<BlockPos> laid = plan.steps(BuildPlan.Stage.STRUCTURE).stream().map(BuildPlan.Step::pos).toList();
			BlockPos spot = plan.steps(BuildPlan.Stage.CLEAR).stream().map(BuildPlan.Step::pos).filter(laid::contains).findFirst().orElse(null);
			helper.assertTrue(spot != null, "no clearing step on a structure spot");
			// clear: the spot can't be done, deferred, then given up on the retry
			int guard = 0;
			while (site.stage() == BuildPlan.Stage.CLEAR && guard++ < 100000) {
				BuildPlan.Step step = site.current(plan);
				if (step == null) {
					site.finishList();
				} else if (step.pos().equals(spot)) {
					site.defer();
				} else {
					site.advance();
				}
			}
			helper.assertTrue(site.skipped() >= 1, "the clearing step wasn't given up: skipped " + site.skipped());
			helper.assertTrue(!site.leftOut(spot), "a block given up while CLEARING at " + spot.subtract(origin).toShortString()
				+ " is left out of the last look before done, though it's a structure block laid later");
			// on through the foundation to the structure: there, give one block up for good (the control)
			BlockPos given = null;
			guard = 0;
			while (site.stage() != BuildPlan.Stage.DECORATION && !site.isDone() && guard++ < 100000) {
				BuildPlan.Step step = site.current(plan);
				if (step == null) {
					site.finishList();
				} else if (site.stage() == BuildPlan.Stage.STRUCTURE && (given == null || step.pos().equals(given))) {
					given = step.pos();
					site.defer();
				} else {
					site.advance();
				}
			}
			helper.assertTrue(given != null && site.leftOut(given), "a STRUCTURE block given up for good isn't left out: " + given);
			BuildSite back = BuildSite.load(site.save());
			helper.assertTrue(back != null && back.leftOut(given) && !back.leftOut(spot), "the left-out blocks read back from the save");
		} finally {
			cleanUp(level, id);
		}
		helper.succeed();
	}

	/**
	 * The sentences a player reads as a Wonder goes from stage to stage, in English, each using every argument it's given
	 *. (Most of the mod's chat lines end without a full stop, so that is not checked.)
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStageSentencesReadInEnglish(GameTestHelper helper) {
		Language en = Language.getInstance();
		Object[][] lines = {
			{"message.aliveworkplace.wonder.stage_begun", 4},
			{"message.aliveworkplace.wonder.part_built", 6},
			{"message.aliveworkplace.wonder.stage_built", 5},
		};
		for (Object[] line : lines) {
			String key = (String) line[0];
			int args = (Integer) line[1];
			helper.assertTrue(en.has(key), "missing: " + key);
			Object[] fill = new Object[args];
			for (int i = 0; i < args; i++) {
				fill[i] = "<" + (i + 1) + ">";
			}
			String text = String.format(en.getOrDefault(key), fill);
			for (int i = 0; i < args; i++) {
				helper.assertTrue(text.contains("<" + (i + 1) + ">"), key + " drops argument " + (i + 1) + ": " + text);
			}
			helper.assertTrue(!text.contains("%"), key + " has a stray %: " + text);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- a real builder runs out mid-stage

	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final ResourceLocation WHOLE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder/test_folly");
	private static final ResourceLocation STAGE_ONE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder/test_folly/stage_1_0");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos BUILDER = new BlockPos(3, 2, 3);
	private static final BlockPos MIDDLE = new BlockPos(14, 2, 14);

	private static List<String> keys(Component c) {
		List<String> out = new ArrayList<>();
		if (c.getContents() instanceof TranslatableContents t) {
			out.add(t.getKey());
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component a) {
					out.addAll(keys(a));
				}
			}
		}
		for (Component sib : c.getSiblings()) {
			out.addAll(keys(sib));
		}
		return out;
	}

	/** Every {@code item} in the area: placed blocks, containers, the villagers' inventories and items on the ground. */
	private static int[] count(GameTestHelper helper, ServerLevel level, Item item, Villager builder) {
		int blocks = 0;
		int stored = 0;
		BlockPos lo = helper.absolutePos(new BlockPos(-2, 0, -2));
		BlockPos hi = helper.absolutePos(new BlockPos(31, 29, 31));
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.min(lo, hi), BlockPos.max(lo, hi))) {
			if (level.getBlockState(p).getBlock().asItem() == item) {
				blocks++;
			}
			if (level.getBlockEntity(p) instanceof Container box) {
				stored += box.countItem(item);
			}
		}
		int carried = builder.isAlive() ? builder.getInventory().countItem(item) : 0;
		int dropped = 0;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(lo.getCenter(), hi.getCenter()).inflate(4))) {
			if (e.getItem().is(item)) {
				dropped += e.getItem().getCount();
			}
		}
		return new int[] {blocks, stored, carried, dropped};
	}

	/**
	 * One builder on stage I with one of its main blocks only half there: it lays what it has and then waits, the stone
	 * saying what's short; the stage isn't called done with holes and stage II doesn't start. The rest brought, stage I
	 * is finished as drawn and stage II begins, and every one of those blocks is accounted for (laid, in a chest, carried
	 * or dropped): nothing lost, nothing made from nothing.
	 */
	//$ gametest_ticks_batch TALL '24000' '"qaWonderShort1026"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "qaWonderShort1026")
	public void aStageWhoseBlocksRunOutWaitsAndLosesNothing(GameTestHelper helper) {
		runOut(helper, false);
	}

	/** As above, with a restart (the Wonders, the build sites and the builder saved and loaded) while it waits. */
	//$ gametest_ticks_batch TALL '24000' '"qaWonderShortReload1026"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "qaWonderShortReload1026")
	public void aRestartWhileAStageWaitsForBlocksLosesNothing(GameTestHelper helper) {
		runOut(helper, true);
	}

	private static void runOut(GameTestHelper helper, boolean reload) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos hallRel = new BlockPos(2, 2, 27);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity hall = helper.getBlockEntity(hallRel);
		hall.setOwner(player.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		java.util.concurrent.atomic.AtomicReference<Villager> who = new java.util.concurrent.atomic.AtomicReference<>(builder);
		CompoundTag[] saved = new CompoundTag[1];

		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		ItemStack plan = WonderPlanItem.of(level, helper.absolutePos(hallRel), folly, Component.literal("Testholm"));
		BlueprintData data = BlueprintItem.data(plan).orElseThrow();
		BlockPos origin = helper.absolutePos(MIDDLE).subtract(new BlockPos(4, 0, 4));
		BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(at)));

		// the block stage I lays most of: only half of what it takes is there at first
		BuildPlan one = BuildPlan.create(BlueprintLibrary.get(level, STAGE_ONE).orElseThrow(), at, level, 0);
		Map<Item, Integer> uses = new HashMap<>();
		for (BuildPlan.Step step : one.steps(BuildPlan.Stage.STRUCTURE)) {
			uses.merge(step.state().getBlock().asItem(), 1, Integer::sum);
		}
		Item scarce = uses.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
		int need = uses.get(scarce);
		helper.assertTrue(need >= 8, "stage I lays only " + need + " of its main block " + scarce);
		int first = need / 2;
		int[] before = count(helper, level, scarce, builder);
		helper.assertTrue(before[2] == 0, "the builder starts with " + before[2] + " " + scarce);

		player.setItemInHand(InteractionHand.MAIN_HAND, plan);
		WonderGoodsKit.stockAll(level, FOLLY, at);
		helper.assertTrue(WonderStages.handOver(player, builder, plan) == InteractionResult.SUCCESS, "not handed over");
		WonderSite begun = WonderManager.get(level).in(helper.absolutePos(hallRel)).get(0);
		BlockPos stone = begun.stone().orElseThrow();
		UUID id = begun.id();
		Leftovers.after(helper, () -> cleanUp(level, id));
		// the chests round the stone: every other block of the folly with some spare, the scarce one half there
		BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, new BlueprintData(WHOLE,
			BlueprintLibrary.get(level, WHOLE).map(Blueprint::size), Optional.empty())).orElseThrow();
		int dx = Integer.signum(stone.getX() - helper.absolutePos(MIDDLE).getX());
		int dz = Integer.signum(stone.getZ() - helper.absolutePos(MIDDLE).getZ());
		List<BlockPos> chests = List.of(stone.offset(2 * dx, 0, 0), stone.offset(0, 0, 2 * dz), stone.offset(2 * dx, 0, 2 * dz), stone.offset(3 * dx, 0, 0));
		chests.forEach(p -> level.setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState()));
		int c = 0;
		int slot = 0;
		for (BlueprintSupplies.Line line : list.lines()) {
			Item item = line.item();
			int give = item == scarce ? first : line.need() + 4;
			for (int left = give; left > 0; left -= item.getDefaultMaxStackSize()) {
				if (slot == 27) {
					c++;
					slot = 0;
				}
				helper.assertTrue(c < chests.size() - 1, "the folly needs more chests"); // the last chest is kept for the rest
				((Container) level.getBlockEntity(chests.get(c))).setItem(slot++, new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
			}
		}
		int rest = need - first + 4;
		AtomicInteger phase = new AtomicInteger(0); // 0 building, 1 rest brought, 2 stage II begun
		AtomicInteger idle = new AtomicInteger(0);
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			if (saved[0] != null) { // the builder comes back the tick after leaving, as the world loads them
				Villager back = EntityType.VILLAGER.create(level);
				back.load(saved[0]);
				level.addFreshEntity(back);
				who.set(back);
				saved[0] = null;
				return;
			}
			WonderSite now = WonderManager.get(level).site(id).orElse(null);
			if (now == null || phase.get() == 2) {
				return;
			}
			if (phase.get() == 0) {
				helper.assertTrue(now.stage() == 1, "stage I was called done with only " + first + " of its " + need + " " + scarce + ": " + now);
				int[] n = count(helper, level, scarce, who.get());
				idle.set(n[1] + n[2] + n[3] == 0 ? idle.get() + 1 : 0);
				if (idle.get() < 600) {
					return;
				}
				// it ran out a while ago: waiting, with stage I unfinished, and the stone says what's short
				helper.assertTrue(n[0] - before[0] == first, "with " + first + " " + scarce + " brought, " + (n[0] - before[0]) + " stand");
				helper.assertTrue(!now.parts().isEmpty() && !WonderStages.stageDone(level, now), "stage I done without its blocks");
				List<String> said = keys(WonderStages.statusText(level, stone));
				helper.assertTrue(said.contains("message.aliveworkplace.wonder.status.short"), "the stone doesn't say what's short: " + said);
				if (reload) {
					restart(level);
					Villager b = who.get();
					saved[0] = b.saveWithoutId(new CompoundTag());
					b.discard();
					WonderSite read = WonderManager.get(level).site(id).orElseThrow();
					helper.assertTrue(read.equals(now) && open(level, id) == 1, "the waiting stage read back: " + read + ", open parts " + open(level, id));
				}
				((Container) level.getBlockEntity(chests.get(chests.size() - 1))).setItem(0, new ItemStack(scarce, rest));
				phase.set(1);
				return;
			}
			if (now.stage() == 2) {
				int[] n = count(helper, level, scarce, who.get());
				int supplied = first + rest;
				int found = n[0] - before[0] + n[1] + n[2] + n[3];
				helper.assertTrue(found == supplied, scarce + " brought " + supplied + ", accounted for " + found + " (laid " + (n[0] - before[0])
					+ ", chests " + n[1] + ", carried " + n[2] + ", dropped " + n[3] + ")");
				List<BlockPos> left = BuildPlan.create(BlueprintLibrary.get(level, STAGE_ONE).orElseThrow(), at, level, 0).unfinished(level);
				helper.assertTrue(left.isEmpty(), "stage II began with " + left.size() + " blocks of stage I missing, e.g. " + left.stream().limit(3).toList());
				phase.set(2);
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(phase.get() == 2, "phase " + phase.get() + ", stage "
			+ WonderManager.get(level).site(id).map(WonderSite::stage).orElse(-1) + ", idle " + idle.get()));
	}
}
