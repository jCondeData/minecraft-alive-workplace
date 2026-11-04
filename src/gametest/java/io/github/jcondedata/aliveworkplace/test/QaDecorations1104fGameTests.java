package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-04, lane f) of ROADMAP 26.14, the fourteen small decorations, from the spec and the player's side rather
 * than the code. {@link MoreDecorationsGameTests} builds each from the plan's own material count; here each is built from
 * exactly what the player reads on the held blueprint's Material List ({@link BlueprintSupplies#checklist}), nothing
 * more, and must finish with every block as drawn and nothing skipped. Then: the Lily Pond and the Fire Pit taken down
 * (sneak hand-over) leave no water or fire and give back what they cost; the Footbridge over a real 1-deep stream; the
 * Lily Pond on a hillside beside a pit keeps its water in its kerb; the Signpost's boards read as words.
 */
public class QaDecorations1104fGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	/** Supply chests by the bench (within its reach, outside every footprint). */
	private static final List<BlockPos> CHESTS = List.of(new BlockPos(2, 2, 4), new BlockPos(4, 2, 4), new BlockPos(1, 2, 5), new BlockPos(4, 2, 1));
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 9);
	/** Items no survival player can get: a list asking for one asks for the impossible. */
	private static final Set<Item> UNOBTAINABLE = Set.of(Items.AIR, Items.BEDROCK, Items.BARRIER, Items.LIGHT, Items.STRUCTURE_VOID, Items.STRUCTURE_BLOCK,
		Items.COMMAND_BLOCK, Items.SPAWNER, Items.PETRIFIED_OAK_SLAB, Items.BUDDING_AMETHYST, Items.REINFORCED_DEEPSLATE, Items.END_PORTAL_FRAME,
		Items.FARMLAND, Items.DIRT_PATH, Items.JIGSAW, Items.DEBUG_STICK, Items.KNOWLEDGE_BOOK, Items.CHORUS_PLANT, Items.FROGSPAWN, Items.TRIAL_SPAWNER,
		Items.VAULT, Items.INFESTED_STONE, Items.INFESTED_COBBLESTONE, Items.INFESTED_STONE_BRICKS, Items.INFESTED_MOSSY_STONE_BRICKS);

	record Job(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, Map<Item, Integer> list, BlueprintData.Placement placement,
			   StarterBlueprints.Entry entry) {
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	/**
	 * The list a player reads on the held, placed blueprint with a bench and its empty chests beside it: every line, by
	 * item, with what it asks to bring. Every line must ask to bring all it needs (the chests are empty) and be an item a
	 * survival player can get.
	 */
	private static Map<Item, Integer> playerList(GameTestHelper helper, StarterBlueprints.Entry entry, BlueprintData.Placement placement) {
		ServerLevel level = helper.getLevel();
		BlueprintData data = new BlueprintData(entry.id(), Optional.of(entry.size()), Optional.of(placement));
		BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, data)
			.orElseThrow(() -> new GameTestAssertException(entry.id() + ": the held blueprint has no material list"));
		helper.assertTrue(list.bench().isPresent(), entry.id() + ": the material list found no bench beside it");
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (BlueprintSupplies.Line line : list.lines()) {
			helper.assertTrue(line.need() > 0 && line.toBring() == line.need(), entry.id() + ": with empty chests the list says " + line);
			helper.assertTrue(!UNOBTAINABLE.contains(line.item()), entry.id() + "'s material list asks for " + BuiltInRegistries.ITEM.getKey(line.item())
				+ ", which no survival player can get");
			out.merge(line.item(), line.toBring(), Integer::sum);
		}
		helper.assertTrue(!out.isEmpty(), entry.id() + ": the material list is empty");
		LOGGER.info("qa deco 1104f list {}: {}", entry.id(), out);
		return out;
	}

	/** Puts {@code list} into the supply chests, stack by stack, nothing more. */
	private static void stock(GameTestHelper helper, Map<Item, Integer> list) {
		List<ItemStack> stacks = new ArrayList<>();
		list.forEach((item, n) -> {
			int left = n;
			while (left > 0) {
				int take = Math.min(left, item.getDefaultMaxStackSize());
				stacks.add(new ItemStack(item, take));
				left -= take;
			}
		});
		int i = 0;
		for (BlockPos chest : CHESTS) {
			Container c = helper.getBlockEntity(chest);
			for (int slot = 0; slot < c.getContainerSize() && i < stacks.size(); slot++) {
				c.setItem(slot, stacks.get(i++));
			}
		}
		helper.assertTrue(i == stacks.size(), "setup: more stacks than chests (" + stacks.size() + ")");
	}

	/** A bench with empty chests; the list read off the held blueprint; exactly that stocked; a builder put on it. */
	static Job exact(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		ensurePoi(level, helper.absolutePos(BENCH));
		for (BlockPos chest : CHESTS) {
			helper.setBlock(chest, Blocks.CHEST);
		}
		BlueprintData.Placement placement = placement(helper, origin);
		Map<Item, Integer> list = playerList(helper, entry, placement);
		stock(helper, list);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, entry.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + entry.id());
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return new Job(level, villager, site, plan, list, placement, entry);
	}

	private static boolean finished(Job job) {
		return BuildSiteManager.get(job.level()).get(job.site().id()) == null;
	}

	/** Finished, every block as drawn, nothing skipped. */
	static void assertExactlyBuilt(GameTestHelper helper, Job job) {
		helper.assertTrue(finished(job), job.entry().id() + " from exactly its material list is still building: stage=" + job.site().stage() + " status="
			+ job.site().status() + " missing=" + job.site().missing() + " skipped=" + job.site().skipped() + " builder at "
			+ helper.relativePos(job.villager().blockPosition()) + " alive=" + job.villager().isAlive());
		List<BlockPos> unfinished = job.plan().unfinished(job.level());
		if (!unfinished.isEmpty()) {
			StringBuilder sample = new StringBuilder();
			unfinished.stream().limit(5).forEach(p -> sample.append(p.subtract(job.placement().origin()).toShortString()).append('=')
				.append(job.level().getBlockState(p)).append("; "));
			throw new GameTestAssertException(job.entry().id() + ": " + unfinished.size() + " block(s) not as drawn after the build, e.g. " + sample);
		}
		helper.assertTrue(job.site().skipped() == 0, job.entry().id() + ": the build skipped " + job.site().skipped() + " block(s)");
	}

	private static void buildExactly(GameTestHelper helper, StarterBlueprints.Entry entry) {
		Job job = exact(helper, entry, ORIGIN);
		helper.succeedWhen(() -> assertExactlyBuilt(helper, job));
	}

	/** Every fluid block (source or flowing) in the box, relative. */
	private static List<BlockPos> fluids(GameTestHelper helper, BlockPos from, BlockPos to) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(from, to)) {
			if (!helper.getBlockState(p).getFluidState().isEmpty()) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** The absolute spots where the plan pours water. */
	private static Set<BlockPos> waterSpots(GameTestHelper helper, BuildPlan plan) {
		Set<BlockPos> out = new HashSet<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				if (s.state().getFluidState().is(FluidTags.WATER)) {
					out.add(s.pos().immutable());
				}
			}
		}
		return out;
	}

	/** {@code item} in the supply chests, the builder's bag and on the ground near the test. */
	private static int held(GameTestHelper helper, Villager villager, Item item) {
		int n = 0;
		for (BlockPos chest : CHESTS) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(chest)) instanceof Container c) {
				n += c.countItem(item);
			}
		}
		n += ModAttachments.BUILDER_BAG.getOrCreate(villager).count(item);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	// ------------------------------------------------------------------------- 1. exactly what the list asks for

	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fA"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fA")
	public void marketCrossFromExactlyItsList(GameTestHelper helper) {
		buildExactly(helper, StarterBlueprints.MARKET_CROSS);
	}

	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fA"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fA")
	public void stagStatueFromExactlyItsList(GameTestHelper helper) {
		buildExactly(helper, StarterBlueprints.STAG_STATUE);
	}

	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fA"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fA")
	public void flowerPlantersFromExactlyItsList(GameTestHelper helper) {
		buildExactly(helper, StarterBlueprints.FLOWER_PLANTERS);
	}

	/**
	 * The Signpost from exactly its list, and its four boards read as words once built: no raw lang key, no "sign." or
	 * "aliveworkplace." on a board, each board with something on it, and a "To" board giving "N blocks".
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fA"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fA")
	public void signpostFromExactlyItsListWithBoardsInWords(GameTestHelper helper) {
		Job job = exact(helper, StarterBlueprints.SIGNPOST, ORIGIN);
		helper.succeedWhen(() -> {
			assertExactlyBuilt(helper, job);
			int boards = 0;
			for (BlockPos p : BlockPos.betweenClosed(ORIGIN, ORIGIN.offset(2, 4, 2))) {
				if (!(helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof SignBlockEntity sign)) {
					continue;
				}
				boards++;
				List<String> lines = new ArrayList<>();
				for (int i = 0; i < 4; i++) {
					lines.add(sign.getFrontText().getMessage(i, false).getString());
				}
				String all = String.join("|", lines);
				helper.assertTrue(!all.contains("sign.") && !all.contains("aliveworkplace.") && !all.contains("%s"), "a signpost board reads " + lines);
				helper.assertTrue(!all.replace("|", "").isBlank(), "a signpost board at " + p + " is blank");
				if (lines.get(0).equals("To")) {
					helper.assertTrue(lines.get(2).matches("\\d+ blocks?"), "a signpost board says " + lines);
				}
				LOGGER.info("qa deco 1104f signpost board {}: {}", p, lines);
			}
			helper.assertTrue(boards == 4, "the built signpost has " + boards + " boards, wanted 4");
		});
	}

	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fA"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fA")
	public void stoneArchwayFromExactlyItsList(GameTestHelper helper) {
		buildExactly(helper, StarterBlueprints.STONE_ARCHWAY);
	}

	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fA"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fA")
	public void lilyPondFromExactlyItsList(GameTestHelper helper) {
		buildExactly(helper, StarterBlueprints.LILY_POND);
	}

	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fA"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fA")
	public void hayCartFromExactlyItsList(GameTestHelper helper) {
		buildExactly(helper, StarterBlueprints.HAY_CART);
	}

	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fB"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fB")
	public void noticeBoardFromExactlyItsList(GameTestHelper helper) {
		buildExactly(helper, StarterBlueprints.NOTICE_BOARD);
	}

	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fB"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fB")
	public void lanternPergolaFromExactlyItsList(GameTestHelper helper) {
		buildExactly(helper, StarterBlueprints.LANTERN_PERGOLA);
	}

	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fB"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fB")
	public void firePitFromExactlyItsList(GameTestHelper helper) {
		buildExactly(helper, StarterBlueprints.FIRE_PIT);
	}

	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fB"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fB")
	public void sundialFromExactlyItsList(GameTestHelper helper) {
		buildExactly(helper, StarterBlueprints.SUNDIAL);
	}

	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fB"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fB")
	public void topiaryGardenFromExactlyItsList(GameTestHelper helper) {
		buildExactly(helper, StarterBlueprints.TOPIARY_GARDEN);
	}

	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fB"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fB")
	public void marketCornerFromExactlyItsList(GameTestHelper helper) {
		buildExactly(helper, StarterBlueprints.MARKET_CORNER);
	}

	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fB"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fB")
	public void footbridgeFromExactlyItsList(GameTestHelper helper) {
		buildExactly(helper, StarterBlueprints.FOOTBRIDGE);
	}

	// ------------------------------------------------------------------------------------------------ 2. take-down

	/**
	 * Built from exactly its list, then its placed blueprint sneak-handed to the builder (the guide's take-down): every
	 * block comes down, and 100 ticks later no water and no fire is left in or round its footprint, the builder's bag is
	 * empty, and what was paid is back: every item of the list in the chests (or on the ground), and the buckets the
	 * water came in neither lost nor doubled (water buckets plus empty buckets = the water buckets paid).
	 */
	static void takeDown(GameTestHelper helper, StarterBlueprints.Entry entry) {
		Leftovers.clear(helper);
		Job job = exact(helper, entry, ORIGIN);
		ServerLevel level = job.level();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BuildSite[] down = {null};
		long[] downAt = {-1};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (down[0] == null && finished(job) && helper.getTick() > 5) {
				assertExactlyBuilt(helper, job);
				ItemStack print = BlueprintItem.create(entry.id(), null);
				print.set(ModComponents.BLUEPRINT, BlueprintItem.data(print).orElseThrow().withPlacement(Optional.of(job.placement())));
				player.setItemInHand(InteractionHand.MAIN_HAND, print);
				Builders.assign(player, job.villager(), print, true);
				down[0] = Builders.activeSite(level, job.villager());
				helper.assertTrue(down[0] != null && down[0].isDeconstruction(), "the sneak hand-over of the finished " + entry.id() + " started no take-down");
				Leftovers.after(helper, () -> {
					if (BuildSiteManager.get(level).get(down[0].id()) != null) {
						BuildSiteManager.get(level).remove(down[0].id());
					}
				});
			}
			if (down[0] != null && downAt[0] < 0 && BuildSiteManager.get(level).get(down[0].id()) == null) {
				downAt[0] = helper.getTick();
			}
			if (downAt[0] >= 0 && helper.getTick() == downAt[0] + 100) {
				int outside = 0;
				for (BlockPos p : fluids(helper, new BlockPos(0, 0, 0), new BlockPos(21, 12, 21))) {
					if (!job.plan().bounds().isInside(helper.absolutePos(p))) {
						outside++;
					}
				}
				LOGGER.info("qa deco 1104f take-down {}: built={}, down at tick {}, skipped {}, fluids {} ({} outside the footprint), water buckets {}, buckets {}, list {}",
					entry.id(), job.site().isDone(), downAt[0], down[0].skipped(), fluids(helper, new BlockPos(0, 0, 0), new BlockPos(21, 12, 21)).size(), outside,
					held(helper, job.villager(), Items.WATER_BUCKET), held(helper, job.villager(), Items.BUCKET), job.list());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(downAt[0] >= 0, down[0] == null ? "not built yet: " + job.site().stage() + " " + job.site().status() + " " + job.site().missing()
				: "still taking down: " + down[0].stage() + " " + down[0].status());
			helper.assertTrue(helper.getTick() - downAt[0] >= 100, "waiting 100 ticks after the take-down");
			BuildPlan built = job.plan();
			List<String> standing = new ArrayList<>();
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step s : built.steps(stage)) {
					BlockState now = level.getBlockState(s.pos());
					if (!now.isAir() && !now.canBeReplaced() || !now.getFluidState().isEmpty()) {
						standing.add(s.pos().subtract(job.placement().origin()).toShortString() + "=" + now);
					}
				}
			}
			BuildPlan downPlan = down[0].plan(level);
			int downSteps = downPlan == null ? -1 : downPlan.steps(BuildPlan.Stage.DECONSTRUCT).size();
			Map<String, Integer> kinds = new LinkedHashMap<>();
			for (String s : standing) {
				kinds.merge(s.substring(s.indexOf('=') + 1).replaceAll("\\[.*", ""), 1, Integer::sum);
			}
			helper.assertTrue(standing.isEmpty(), entry.id() + " taken down still has " + standing.size() + " of its built blocks standing (" + kinds
				+ "; the take-down planned " + downSteps + " steps, skipped " + down[0].skipped() + "), e.g. " + standing.subList(0, Math.min(5, standing.size())));
			List<BlockPos> water = fluids(helper, new BlockPos(0, 1, 0), new BlockPos(21, 12, 21));
			helper.assertTrue(water.isEmpty(), entry.id() + " taken down left " + water.size() + " fluid block(s), e.g. " + water.subList(0, Math.min(5, water.size()))
				+ (water.isEmpty() ? "" : " = " + helper.getBlockState(water.get(0))));
			List<BlockPos> fire = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(21, 12, 21))) {
				BlockState s = helper.getBlockState(p);
				if (s.is(Blocks.FIRE) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_FIRE)) {
					fire.add(p.immutable());
				}
			}
			helper.assertTrue(fire.isEmpty(), entry.id() + " taken down left fire at " + fire);
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(job.villager()).stacks().stream().allMatch(ItemStack::isEmpty),
				"after the take-down the builder still carries " + ModAttachments.BUILDER_BAG.getOrCreate(job.villager()).stacks());
			List<String> wrong = new ArrayList<>();
			for (Map.Entry<Item, Integer> e : job.list().entrySet()) {
				if (e.getKey() == Items.WATER_BUCKET) {
					continue;
				}
				int got = held(helper, job.villager(), e.getKey());
				if (got != e.getValue()) {
					wrong.add(BuiltInRegistries.ITEM.getKey(e.getKey()).getPath() + " " + got + " (paid " + e.getValue() + ")");
				}
			}
			int paidWater = job.list().getOrDefault(Items.WATER_BUCKET, 0);
			int full = held(helper, job.villager(), Items.WATER_BUCKET);
			int empty = held(helper, job.villager(), Items.BUCKET);
			if (full + empty != paidWater) {
				wrong.add("buckets: " + full + " water + " + empty + " empty (paid " + paidWater + " water buckets)");
			}
			helper.assertTrue(wrong.isEmpty(), entry.id() + " built and taken down gives back the wrong things: " + wrong);
		});
	}

	//$ gametest_ticks_batch BIG '16000' '"qaDeco1104fDownFire"'
	@GameTest(template = BIG, timeoutTicks = 16000, batch = "qaDeco1104fDownFire")
	public void firePitTakenDownLeavesNoFireAndGivesBackItsCost(GameTestHelper helper) {
		takeDown(helper, StarterBlueprints.FIRE_PIT);
	}

	// ------------------------------------------------------------------------------------- 3. footbridge over a stream

	/**
	 * A real stream: a channel 1 deep (the floor dug out, stone under it) of still water running east-west, three wide,
	 * across the area. The player clicks the bank so the Footbridge's channel lies over the stream (origin x 6-12, z 6-12;
	 * the stream at y 1, z 8-10, x 3-18). Built from exactly its list: it finishes, every block as drawn, nothing skipped,
	 * the builder alive and not under water, the stream still water under the deck (x 9, y 1, z 8-10), the bridge's own
	 * water under the deck (x 9, y 2, z 8-10), and no water anywhere else than the stream and the bridge's channel.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fStream"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fStream")
	public void footbridgeOverARealStreamFinishesWithWaterUnderIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		Set<BlockPos> stream = new HashSet<>();
		for (int x = 3; x <= 18; x++) {
			for (int z = 8; z <= 10; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
				stream.add(new BlockPos(x, 1, z));
			}
		}
		Job job = exact(helper, StarterBlueprints.FOOTBRIDGE, new BlockPos(6, 2, 6));
		Set<BlockPos> channel = waterSpots(helper, job.plan());
		helper.assertTrue(channel.size() == 15, "setup: the footbridge pours " + channel.size() + " water, expected 15");
		helper.succeedWhen(() -> {
			assertExactlyBuilt(helper, job);
			Villager v = job.villager();
			helper.assertTrue(v.isAlive() && !v.isEyeInFluid(FluidTags.WATER), "the builder: alive=" + v.isAlive() + " under water=" + v.isEyeInFluid(FluidTags.WATER)
				+ " at " + helper.relativePos(v.blockPosition()));
			for (int z = 8; z <= 10; z++) {
				helper.assertTrue(helper.getBlockState(new BlockPos(9, 1, z)).getFluidState().isSource(), "the stream under the deck at z " + z + " is "
					+ helper.getBlockState(new BlockPos(9, 1, z)));
				helper.assertTrue(helper.getBlockState(new BlockPos(9, 2, z)).getFluidState().isSource(), "the bridge's water under the deck at z " + z + " is "
					+ helper.getBlockState(new BlockPos(9, 2, z)));
			}
			List<String> stray = new ArrayList<>();
			for (BlockPos p : fluids(helper, new BlockPos(0, 1, 0), new BlockPos(21, 12, 21))) {
				if (!stream.contains(p) && !channel.contains(helper.absolutePos(p)) || !helper.getBlockState(p).getFluidState().isSource()) {
					stray.add(p.toShortString() + "=" + helper.getBlockState(p));
				}
			}
			helper.assertTrue(stray.isEmpty(), stray.size() + " stray water block(s) round the footbridge over the stream, e.g. " + stray.subList(0, Math.min(5, stray.size())));
		});
	}

	// ------------------------------------------------------------------------------- 4. lily pond on a hillside

	/**
	 * The Lily Pond (x 6-14, z 6-12) on a hillside beside a pit: the ground rises a block at x 12 and another at x 14 (to
	 * x 20), and a pit 1 deep is dug at x 3-7, z 7-11 (under its west bank and beside it). Built from exactly its list: it
	 * finishes as drawn, nothing skipped; 200 ticks later every one of its water blocks is a source where drawn and there is
	 * no other water anywhere round it (none ran down into the pit, none over the low side).
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaDeco1104fPondSlope"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDeco1104fPondSlope")
	public void lilyPondOnAHillsideBesideAPitKeepsItsWaterIn(GameTestHelper helper) {
		Leftovers.clear(helper);
		for (int x = 12; x <= 20; x++) {
			for (int z = 4; z <= 14; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
				if (x >= 14) {
					helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
				}
			}
		}
		for (int x = 3; x <= 7; x++) {
			for (int z = 7; z <= 11; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
			}
		}
		Job job = exact(helper, StarterBlueprints.LILY_POND, new BlockPos(6, 2, 6));
		Set<BlockPos> pond = waterSpots(helper, job.plan());
		helper.assertTrue(pond.size() >= 10, "setup: the pond pours only " + pond.size() + " water");
		long[] doneAt = {-1};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (doneAt[0] < 0 && finished(job)) {
				doneAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			assertExactlyBuilt(helper, job);
			helper.assertTrue(doneAt[0] >= 0 && helper.getTick() - doneAt[0] >= 200, "waiting 200 ticks after the pond is done");
			for (BlockPos p : pond) {
				helper.assertTrue(helper.getLevel().getBlockState(p).getFluidState().isSource(), "the pond's water at " + helper.relativePos(p) + " is "
					+ helper.getLevel().getBlockState(p));
			}
			List<String> stray = new ArrayList<>();
			for (BlockPos p : fluids(helper, new BlockPos(0, 0, 0), new BlockPos(21, 12, 21))) {
				if (!pond.contains(helper.absolutePos(p))) {
					stray.add(p.toShortString() + "=" + helper.getBlockState(p));
				}
			}
			helper.assertTrue(stray.isEmpty(), stray.size() + " water block(s) outside the Lily Pond's drawn water on the hillside, e.g. "
				+ stray.subList(0, Math.min(5, stray.size())));
		});
	}
}
