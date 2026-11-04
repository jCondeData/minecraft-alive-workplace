package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SiteList;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (2026-11-04, the tester of B928 and B937), builders at the edges, from the spec, not the code.
 * <ul>
 * <li>B928 (CHANGELOG, builder.md): a Builder struck by lightning into a witch leaves his work "as a death does": his bag
 * and the blueprint fall where he was, the owner is told (en_us builder_struck), his site is no longer his. Covered here
 * beyond B928GameTests and QaBuilderConverted1104: the owner's message word for word, sites queued behind the build, and
 * the fallen blueprint handed to a new builder by the player, whose build then finishes with nothing made or lost.</li>
 * <li>B937 (CHANGELOG, the hand-over's words "those blocks will be left out and need no materials"): a build across the
 * world's top or under its bottom, through the player's own hand-over: the warning, the build ending with nothing lost,
 * the repair rounds after it asking for nothing over the limit, a take-down of it ending with every block back, and the
 * material list of a build under way (what the bench and the porters read) leaving out the blocks over the limit.</li>
 * </ul>
 * The ways the player-facing lists of the held blueprint still ask for those blocks are in QaBuilderEdges1104bBugTests.
 */
public class QaBuilderEdges1104bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-test");
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	// ------------------------------------------------------------------------------------------------ B928: lightning

	/**
	 * The owner, online, hands the hut over himself; lightning turns the builder mid-STRUCTURE. He reads, word for word,
	 * "Lightning turned your builder into a witch while building Test Hut. Their tools and the blueprint dropped at x y z."
	 * with the spot the blueprint now lies on, out of the bolt's reach (B952). He picks up the fallen blueprint and the spilled materials (put back in the chest), lays
	 * the blueprint on the same spot again and hands it to a second builder at the same bench: it is taken (not refused
	 * over the gone builder's site), the hut finishes and every material is accounted for (the stock less the hut's list).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaEdgesStruckResume"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaEdgesStruckResume")
	public void theBlueprintAStruckBuilderDropsIsHandedToANewBuilderWhoFinishesTheHut(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		difficulty(helper, Difficulty.NORMAL);
		protectionOff(helper);
		Layout l = layout(helper, 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaE"));
		Villager first = builder(helper, l, new BlockPos(3, 2, 3));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placed(placement));
		hand(c, first, false);
		BuildSite site = Builders.activeSite(level, first);
		helper.assertTrue(site != null && !site.isDeconstruction(), "the hand-over started no build: " + texts(c.seen()));
		QaBuilderTerrain1102GameTests.Run r = run(level, first, site, l);
		String hut = Blueprints.displayName(TEST_HUT).getString();
		long[] struckAt = {-1};
		String[] told = {null};
		BlockPos[] stood = {null};
		Villager[] second = {null};
		UUID[] secondSite = {null};
		helper.onEachTick(() -> {
			if (struckAt[0] < 0) {
				BuildSite s = r.site();
				if (s == null || s.stage() != BuildPlan.Stage.STRUCTURE || s.cursor() < 4 || QaBuilderConverted1104GameTests.bag(first, r.plan()).isEmpty()) {
					return;
				}
				stood[0] = first.blockPosition();
				told[0] = "Lightning turned your builder into a witch while building " + hut + ". Their tools and the blueprint dropped at ";
				strike(level, first);
				struckAt[0] = helper.getTick();
				calmWitches(helper);
				return;
			}
			if (second[0] != null || helper.getTick() - struckAt[0] < 5) {
				return;
			}
			// B952: what fell is put down out of the bolt's reach (4+ blocks from where he stood), and the message names that
			// spot: word for word, with the x y z of the block the blueprint now lies on
			ItemEntity lying = null;
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
				if (e.getItem().is(ModItems.BLUEPRINT)) {
					lying = e;
				}
			}
			helper.assertTrue(lying != null, "no blueprint fell when the builder was struck");
			BlockPos at = lying.blockPosition();
			helper.assertTrue(Math.max(Math.abs(at.getX() - stood[0].getX()), Math.abs(at.getZ() - stood[0].getZ())) > 3,
				"the blueprint lies at " + at + ", in the bolt's reach of where the builder stood (" + stood[0] + ")");
			told[0] = told[0] + at.getX() + " " + at.getY() + " " + at.getZ() + ".";
			helper.assertTrue(texts(c.seen()).contains(told[0]), "the owner wasn't told \"" + told[0] + "\"; he saw " + texts(c.seen()));
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null || !first.getUUID().equals(BuildSiteManager.get(level).get(site.id()).builder()),
				"the struck builder still has his site");
			// the player picks up what fell: the blueprint into his hand, the materials back into the chest
			ItemStack fallen = ItemStack.EMPTY;
			Container chest = helper.getBlockEntity(l.supplies().get(0));
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
				if (e.getItem().is(ModItems.BLUEPRINT)) {
					helper.assertTrue(fallen.isEmpty(), "two blueprints fell");
					fallen = e.getItem().copy();
				} else {
					ItemStack rest = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, chest, e.getItem().copy(), null);
					helper.assertTrue(rest.isEmpty(), "the chest is full: " + rest);
				}
				e.discard();
			}
			helper.assertTrue(fallen.is(ModItems.BLUEPRINT), "no blueprint fell where the builder was struck");
			helper.assertTrue(BlueprintItem.data(fallen).map(d -> d.structure().equals(TEST_HUT)).orElse(false), "the fallen blueprint isn't the hut's: " + fallen);
			// laid on the same spot again (the player's click on the ground), and handed to a new builder at the same bench
			fallen.set(ModComponents.BLUEPRINT, BlueprintItem.data(fallen).orElseThrow().withPlacement(Optional.of(placement)));
			second[0] = builder(helper, l, new BlockPos(3, 2, 3));
			c.player().setItemInHand(InteractionHand.MAIN_HAND, fallen);
			int seenBefore = c.seen().size();
			hand(c, second[0], false);
			BuildSite next = Builders.activeSite(level, second[0]);
			helper.assertTrue(next != null && !next.isDeconstruction(), "the new builder didn't take the fallen blueprint: "
				+ texts(c.seen()).subList(seenBefore, c.seen().size()));
			secondSite[0] = next.id();
			r.builder()[0] = second[0];
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(secondSite[0] != null, "not handed to the new builder yet");
			BuildSite s = BuildSiteManager.get(level).get(secondSite[0]);
			helper.assertTrue(s == null, "the new builder's hut isn't finished: " + (s == null ? "" : "stage=" + s.stage() + " status=" + s.status()
				+ " missing=" + s.missing()));
			List<BlockPos> wrong = r.plan().unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut not as drawn after the new builder finished");
			Map<Item, String> off = new HashMap<>();
			for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
				int got = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey()) + inventory(c.player(), e.getKey());
				if (got != QaBuilderTerrain1102GameTests.EXTRA) {
					off.put(e.getKey(), "loose " + got);
				}
			}
			helper.assertTrue(off.isEmpty(), "materials made or lost across the strike and the second builder (wanted " + QaBuilderTerrain1102GameTests.EXTRA
				+ " of each left over): " + off);
			LOGGER.info("qa edges: the struck builder's hut finished by a second builder; the owner read \"{}\"", told[0]);
		});
	}

	/**
	 * A builder with two more huts waiting behind his build (handed over while he was busy) is struck: all three
	 * blueprints fall where he was (none lost, none twice), no site of his is left, open or queued, and the bag's
	 * materials are all accounted for.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaEdgesStruckQueue"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaEdgesStruckQueue")
	public void aStruckBuilderWithHutsQueuedDropsEveryBlueprintOnceAndKeepsNoSite(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		difficulty(helper, Difficulty.NORMAL);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		Villager v = r.builder()[0];
		List<UUID> queued = new ArrayList<>();
		for (BlockPos origin : List.of(new BlockPos(14, 2, 6), new BlockPos(20, 2, 6))) {
			BuildSite q = Builders.enqueue(level, v, null, TEST_HUT,
				new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE));
			queued.add(q.id());
			Leftovers.after(helper, () -> {
				if (BuildSiteManager.get(level).get(q.id()) != null) {
					BuildSiteManager.get(level).remove(q.id());
				}
			});
		}
		helper.assertTrue(Builders.queue(level, v).size() == 2, "setup: " + Builders.queue(level, v).size() + " hut(s) queued, not 2");
		Map<Item, Integer> before = new HashMap<>();
		long[] struckAt = {-1};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (struckAt[0] >= 0 || s == null || s.stage() != BuildPlan.Stage.STRUCTURE || s.cursor() < 4
				|| QaBuilderConverted1104GameTests.bag(v, r.plan()).isEmpty()) {
				return;
			}
			for (Item item : r.plan().materials().keySet()) {
				before.put(item, QaBuilderTerrain1102GameTests.loose(helper, r, item));
			}
			strike(level, v);
			struckAt[0] = helper.getTick();
			calmWitches(helper);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(struckAt[0] >= 0 && helper.getTick() - struckAt[0] >= 20, "not struck yet (or just now)");
			helper.assertTrue(blueprints(helper) == 3, blueprints(helper) + " blueprint(s) on the ground after the strike, not 3 (one per hut)");
			List<String> his = new ArrayList<>();
			for (BuildSite s : BuildSiteManager.get(level).all()) {
				if (v.getUUID().equals(s.builder())) {
					his.add(s.structure() + (s.isQueued() ? " (queued)" : ""));
				}
			}
			helper.assertTrue(his.isEmpty(), "sites still the struck builder's: " + his);
			for (UUID q : queued) {
				helper.assertTrue(BuildSiteManager.get(level).get(q) == null, "a queued hut's site stayed open with nobody to build it");
			}
			for (Map.Entry<Item, Integer> e : before.entrySet()) {
				int now = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey());
				helper.assertTrue(now == e.getValue(), e.getKey() + " went from " + e.getValue() + " to " + now + " after the strike");
			}
		});
	}

	// --------------------------------------------------------------------------------------- B937: the world's edges

	/**
	 * Over the top, through the player: the hut handed over on a platform two blocks under the world's top. The owner
	 * reads the yellow warning word for word; the hut ends with what can stand built, nothing made or lost; a repair of it
	 * (Upkeep's round) asks for nothing, and for exactly the one plank a player then knocks out under the top; a take-down
	 * (sneak hand-over) of it ends, every block of it back with the stock and nothing over the top asked for.
	 */
	//$ gametest_ticks_batch HUGE '15000' '"qaEdgesTopFlow"'
	@GameTest(template = HUGE, timeoutTicks = 15000, batch = "qaEdgesTopFlow")
	public void aHutAcrossTheTopIsWarnedBuiltNeedsNoRepairOverItAndComesDown(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		protectionOff(helper);
		int max = level.getMaxBuildHeight();
		int g = top(helper, 2);
		Layout l = layout(helper, g);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, g, 6)), Rotation.NONE, Mirror.NONE);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaT"));
		Villager v = builder(helper, l, new BlockPos(3, g, 3));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placed(placement));
		hand(c, v, false);
		BuildSite site = Builders.activeSite(level, v);
		helper.assertTrue(site != null && !site.isDeconstruction(), "the hand-over across the top started no build: " + texts(c.seen()));
		String hut = Blueprints.displayName(TEST_HUT).getString();
		String warning = "The top of " + hut + " reaches over the world's build limit (y " + max
			+ "). Nothing can stand up there, so those blocks will be left out and need no materials.";
		helper.assertTrue(texts(c.seen()).contains(warning), "the hand-over didn't warn \"" + warning + "\": " + texts(c.seen()));
		helper.assertFalse(texts(c.seen()).stream().anyMatch(t -> t.contains("below the bottom of the world")), "a warning of the bottom at the top");
		QaBuilderTerrain1102GameTests.Run r = run(level, v, site, l);
		var blueprint = BlueprintLibrary.get(level, TEST_HUT).orElseThrow();
		int[] phase = {0};
		UUID[] down = {null};
		Map<Item, Integer> standing = new HashMap<>();
		helper.onEachTick(() -> {
			if (phase[0] == 0 && r.site() == null) {
				// built: what can stand stands, and nothing was made or lost
				List<BlockPos> unbuilt = r.plan().unfinished(level).stream()
					.filter(p -> p.getY() < max && !(r.plan().targets().get(p).getBlock() instanceof net.minecraft.world.level.block.DoorBlock)).toList();
				helper.assertTrue(unbuilt.isEmpty(), unbuilt.size() + " block(s) under the top unbuilt: " + unbuilt.stream().map(helper::relativePos).toList());
				Map<Item, Integer> under = underLimitNeed(level, r.plan());
				for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
					int got = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey()) + inventory(c.player(), e.getKey());
					int want = e.getValue() + QaBuilderTerrain1102GameTests.EXTRA - under.getOrDefault(e.getKey(), 0);
					helper.assertTrue(got == want, e.getKey() + ": " + got + " loose after the build across the top, wanted " + want);
				}
				// the repair round finds nothing gone (the blocks over the top never stood, and are never "gone")
				int holes = BuildPlan.repair(blueprint, placement, level, BuildSiteManager.get(level).gaveUp(placement)).size();
				helper.assertTrue(holes == 0, "a repair of the finished hut across the top asks for " + holes + " block(s)");
				BlockPos plank = null;
				for (Map.Entry<BlockPos, BlockState> e : r.plan().targets().entrySet()) {
					if (e.getKey().getY() == max - 1 && e.getValue().is(Blocks.OAK_PLANKS) && level.getBlockState(e.getKey()).is(Blocks.OAK_PLANKS)) {
						plank = e.getKey();
						break;
					}
				}
				helper.assertTrue(plank != null, "setup: no plank of the hut stands on the layer under the top");
				level.setBlock(plank, Blocks.AIR.defaultBlockState(), 3);
				int one = BuildPlan.repair(blueprint, placement, level, BuildSiteManager.get(level).gaveUp(placement)).size();
				helper.assertTrue(one == 1, "with one plank knocked out under the top a repair asks for " + one + " block(s), not 1");
				level.setBlock(plank, Blocks.OAK_PLANKS.defaultBlockState(), 3);
				// the take-down: sneak-handed the placed blueprint
				for (Map.Entry<Item, Integer> e : under.entrySet()) {
					standing.put(e.getKey(), e.getValue());
				}
				c.player().getInventory().clearContent(); // (the finished hut's blueprint came back to him)
				c.player().setItemInHand(InteractionHand.MAIN_HAND, placed(placement));
				int seen = c.seen().size();
				hand(c, v, true);
				BuildSite td = Builders.activeSite(level, v);
				helper.assertTrue(td != null && td.isDeconstruction(), "the sneak hand-over of the hut across the top started no take-down: "
					+ texts(c.seen()).subList(seen, c.seen().size()));
				down[0] = td.id();
				phase[0] = 1;
			} else if (phase[0] == 1 && BuildSiteManager.get(level).get(down[0]) == null) {
				phase[0] = 2;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 2, phase[0] == 0 ? "the hut across the top isn't built yet: " + QaBuilderTerrain1102GameTests.state(helper, r)
				: "the take-down of the hut across the top never ends: " + describe(level, down[0]));
			List<String> left = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : r.plan().targets().entrySet()) {
				if (!e.getValue().isAir() && !level.isOutsideBuildHeight(e.getKey()) && !level.getBlockState(e.getKey()).isAir()) {
					left.add(helper.relativePos(e.getKey()).toShortString() + "=" + level.getBlockState(e.getKey()).getBlock());
				}
			}
			helper.assertTrue(left.isEmpty(), left.size() + " block(s) of the hut still stand after the take-down: " + left);
			Map<Item, String> off = new HashMap<>();
			for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
				int got = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey()) + inventory(c.player(), e.getKey());
				int want = e.getValue() + QaBuilderTerrain1102GameTests.EXTRA;
				if (got != want) {
					off.put(e.getKey(), "loose " + got + ", wanted " + want);
				}
			}
			helper.assertTrue(off.isEmpty(), "after building and taking down the hut across the top, the stock isn't whole (what stood: " + standing + "): " + off);
		});
	}

	/**
	 * Under the bottom, through the player: the hut laid so its floor is one layer under the world's bottom (y -64 in the
	 * overworld), on a platform at the bottom, the ground left as it is. The hand-over warns of the bottom word for word;
	 * the hut ends with every block in the world built (but the door, which can't stand with its floor under the bottom),
	 * the floor skipped (counted) and nothing made or lost; a repair of it asks for nothing but that door (tried once).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaEdgesBottom"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaEdgesBottom")
	public void aHutWhoseFloorIsUnderTheWorldsBottomIsWarnedAndEndsWithNothingLost(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		protectionOff(helper);
		int min = level.getMinBuildHeight();
		int g = bottom(helper);
		Layout l = layout(helper, g);
		BlockPos origin = new BlockPos(6, g - 2, 6); // the floor at y min - 1, the walls from y min
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		helper.assertTrue(helper.absolutePos(origin).getY() == min - 1, "setup: the floor isn't one under the bottom: " + helper.absolutePos(origin));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaB"));
		Villager v = builder(helper, l, new BlockPos(3, g, 3));
		ItemStack bp = placed(placement);
		bp.set(ModComponents.BLUEPRINT, BlueprintItem.data(bp).orElseThrow().withLevelGround(false));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, bp);
		hand(c, v, false);
		BuildSite site = Builders.activeSite(level, v);
		helper.assertTrue(site != null && !site.isDeconstruction(), "the hand-over under the bottom started no build: " + texts(c.seen()));
		String hut = Blueprints.displayName(TEST_HUT).getString();
		String warning = "The bottom of " + hut + " reaches below the bottom of the world (y " + min
			+ "). Nothing can be built down there, so those blocks will be left out and need no materials.";
		helper.assertTrue(texts(c.seen()).contains(warning), "the hand-over didn't warn \"" + warning + "\": " + texts(c.seen()));
		QaBuilderTerrain1102GameTests.Run r = run(level, v, site, l);
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		BuildSite[] last = {site};
		helper.onEachTick(() -> {
			if (r.site() != null) {
				last[0] = r.site();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(r.died()[0] == null, "the builder died at the bottom of the world: " + r.died()[0]);
			helper.assertTrue(r.site() == null, "the hut under the bottom never ends: " + QaBuilderTerrain1102GameTests.state(helper, r)
				+ ", " + r.plan().unfinished(level).stream().filter(p -> !level.isOutsideBuildHeight(p)).count() + " block(s) in the world unbuilt");
			// every block in the world built, but one that can't stand there (the door, its floor being under the bottom)
			List<String> unbuilt = r.plan().unfinished(level).stream().filter(p -> !level.isOutsideBuildHeight(p) && r.plan().targets().get(p).canSurvive(level, p))
				.map(p -> p.subtract(placement.origin()).toShortString() + " (hut-relative) wants " + r.plan().targets().get(p) + ", is " + level.getBlockState(p)).toList();
			helper.assertTrue(unbuilt.isEmpty(), "blocks in the world left unbuilt: " + unbuilt + "; hut bounds " + r.plan().bounds() + ", origin " + placement.origin());
			helper.assertTrue(last[0].skipped() > 0, "the floor under the bottom wasn't counted as skipped");
			// what's loose is the stock less what stands of the hut: nothing made or lost
			Map<Item, Integer> stands = standing(level, r.plan());
			Map<Item, String> off = new HashMap<>();
			for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
				int got = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey()) + inventory(c.player(), e.getKey());
				int want = e.getValue() + QaBuilderTerrain1102GameTests.EXTRA - stands.getOrDefault(e.getKey(), 0);
				if (got != want) {
					off.put(e.getKey(), "loose " + got + ", wanted " + want);
				}
			}
			LOGGER.info("qa edges: the hut under the bottom ended, {} skipped, standing {}", last[0].skipped(), stands);
			helper.assertTrue(off.isEmpty(), "materials made or lost by the hut under the bottom: " + off);
			// as Upkeep's round counts it: nothing under the bottom; only the door that couldn't stand, which (as any block a
			// build skipped) its first repair tries once and then gives up on
			BuildPlan repair = BuildPlan.repair(BlueprintLibrary.get(level, TEST_HUT).orElseThrow(), placement, level, BuildSiteManager.get(level).gaveUp(placement));
			List<String> asked = repair.targets().keySet().stream()
				.filter(p -> level.isOutsideBuildHeight(p) || !(repair.targets().get(p).getBlock() instanceof net.minecraft.world.level.block.DoorBlock))
				.map(p -> p.subtract(placement.origin()).toShortString() + "=" + repair.targets().get(p).getBlock()).toList();
			helper.assertTrue(asked.isEmpty(), "a repair of the finished hut under the bottom asks for " + asked);
		});
	}

	/**
	 * A build under way across the top: what the bench's material list (SiteList, the book and the chat) and the porters
	 * and Storehouse (Builders.remainingNeed) ask for is only what can stand under the top, never the blocks over it.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaEdgesTopLists"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaEdgesTopLists")
	public void aBuildUnderWayAcrossTheTopListsOnlyWhatCanStand(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		int g = top(helper, 2);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, g, new BlockPos(6, g, 6));
		BuildSite site = r.site();
		Map<Item, Integer> expected = underLimitNeed(level, r.plan());
		Map<Item, Integer> remaining = new HashMap<>(Builders.remainingNeed(level, site, r.plan()));
		helper.assertTrue(remaining.equals(expected), "what porters and the Storehouse are asked for (remainingNeed) " + remaining
			+ ", not only what can stand under the top " + expected + " (the whole list " + r.plan().materials() + ")");
		Map<Item, Integer> listed = new HashMap<>();
		for (BlueprintSupplies.Line line : SiteList.checklist(level, site, r.builder()[0]).lines()) {
			listed.put(line.item(), line.need());
		}
		helper.assertTrue(listed.equals(expected), "the build's material list asks for " + listed + ", not only what can stand under the top " + expected);
		LOGGER.info("qa edges: across the top the lists ask for {} of the whole {}", expected, r.plan().materials());
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------- helpers

	record Layout(BlockPos bench, List<BlockPos> supplies) {
	}

	/** A Builder's Bench, a chest and four barrels at level {@code g} (as QaBuilderTerrain1102's start), stocked with the hut's list + EXTRA. */
	static Layout layout(GameTestHelper helper, int g) {
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(2, g, 2);
		BlockPos chest = new BlockPos(2, g, 4);
		List<BlockPos> supplies = List.of(chest, new BlockPos(1, g, 4), new BlockPos(3, g, 4), new BlockPos(4, g, 4), new BlockPos(5, g, 4));
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(bench));
		for (BlockPos p : supplies) {
			helper.setBlock(p, p.equals(chest) ? Blocks.CHEST : Blocks.BARREL);
		}
		BuildPlan plan = BuildPlan.create(BlueprintLibrary.get(level, TEST_HUT).orElseThrow(),
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		List<ItemStack> stock = new ArrayList<>();
		plan.materials().forEach((item, n) -> {
			for (int left = n + QaBuilderTerrain1102GameTests.EXTRA; left > 0; left -= item.getDefaultMaxStackSize()) {
				stock.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
			}
		});
		for (int i = 0; i < stock.size(); i++) {
			Container c = helper.getBlockEntity(supplies.get(supplies.size() - 1 - i / 27));
			c.setItem(i % 27, stock.get(i));
		}
		return new Layout(bench, supplies);
	}

	/** A builder employed at the layout's bench; any site of his is closed after the test. */
	static Villager builder(GameTestHelper helper, Layout l, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		Builders.employ(level, v, helper.absolutePos(l.bench()));
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite s : List.copyOf(sites.all())) {
				if (v.getUUID().equals(s.builder())) {
					sites.remove(s.id());
				}
			}
		});
		return v;
	}

	static QaBuilderTerrain1102GameTests.Run run(ServerLevel level, Villager v, BuildSite site, Layout l) {
		return new QaBuilderTerrain1102GameTests.Run(level, new Villager[] {v}, site.id(), site.plan(level), 0, l.supplies(), new String[1]);
	}

	static ItemStack placed(BlueprintData.Placement placement) {
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, null);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(placement)));
		return blueprint;
	}

	static void hand(QaStageFiveGameTests.Client c, Villager v, boolean sneak) {
		c.player().setShiftKeyDown(sneak);
		QaStageSixGameTests.clickVillager(c.player(), v, c.seen());
		c.player().setShiftKeyDown(false);
	}

	static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	static int inventory(ServerPlayer p, Item item) {
		return p.getInventory().countItem(item);
	}

	static void protectionOff(GameTestHelper helper) {
		boolean was = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = was);
	}

	static void difficulty(GameTestHelper helper, Difficulty difficulty) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(difficulty, true);
		Leftovers.after(helper, () -> level.getServer().setDifficulty(was, true));
	}

	/** As B928GameTests strikes: the bolt's hit itself, no fire. */
	static void strike(ServerLevel level, Villager villager) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		bolt.moveTo(villager.position());
		bolt.setVisualOnly(true);
		villager.thunderHit(level, bolt);
	}

	static void calmWitches(GameTestHelper helper) {
		for (Witch w : helper.getLevel().getEntitiesOfClass(Witch.class, helper.getBounds().inflate(16))) {
			w.setNoAi(true);
			w.setCanPickUpLoot(false);
		}
	}

	/** Blueprints on the ground round the test area. */
	static int blueprints(GameTestHelper helper) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
			n += e.getItem().is(ModItems.BLUEPRINT) ? e.getItem().getCount() : 0;
		}
		return n;
	}

	static String describe(ServerLevel level, UUID id) {
		BuildSite s = id == null ? null : BuildSiteManager.get(level).get(id);
		return s == null ? "gone" : "stage=" + s.stage() + " status=" + s.status() + " skipped=" + s.skipped() + " missing=" + s.missing();
	}

	/**
	 * What the hut takes of the blocks that can stand: every drawn block inside the world, by item, a door (or any
	 * two-high block) once and only when both halves are inside. Counted from the drawing, not from the builder's lists.
	 */
	static Map<Item, Integer> underLimitNeed(ServerLevel level, BuildPlan plan) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<BlockPos, BlockState> e : plan.targets().entrySet()) {
			BlockState s = e.getValue();
			if (s.isAir() || level.isOutsideBuildHeight(e.getKey())) {
				continue;
			}
			if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
				if (s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER || level.isOutsideBuildHeight(e.getKey().above())) {
					continue;
				}
			}
			out.merge(s.getBlock().asItem(), 1, Integer::sum);
		}
		return out;
	}

	/** What stands of the plan's blocks as drawn, by item (a two-high block once, by its lower half). */
	static Map<Item, Integer> standing(ServerLevel level, BuildPlan plan) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<BlockPos, BlockState> e : plan.targets().entrySet()) {
			BlockState there = level.getBlockState(e.getKey());
			if (e.getValue().isAir() || level.isOutsideBuildHeight(e.getKey()) || !io.github.jcondedata.aliveworkplace.build.MaterialRules.matches(there, e.getValue())
				|| there.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && there.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
				continue;
			}
			out.merge(there.getBlock().asItem(), 1, Integer::sum);
		}
		return out;
	}

	/** A platform whose top is {@code under} blocks under the world's top: stone under level g, open sky above. Returns g. */
	static int top(GameTestHelper helper, int under) {
		ServerLevel level = helper.getLevel();
		int max = level.getMaxBuildHeight();
		int base = helper.absolutePos(BlockPos.ZERO).getY();
		int g = max - under - base;
		for (int x = -1; x <= 24; x++) {
			for (int z = -1; z <= 16; z++) {
				for (int y = g - 1; y < max - base; y++) {
					helper.setBlock(new BlockPos(x, y, z), y == g - 1 ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
		Leftovers.after(helper, () -> {
			AABB box = new AABB(helper.absolutePos(new BlockPos(-4, g - 2, -4))).expandTowards(32, 10, 24);
			for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
				e.discard();
			}
			for (int x = -1; x <= 24; x++) {
				for (int z = -1; z <= 16; z++) {
					for (int y = g - 1; y < max - base; y++) {
						level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		});
		return g;
	}

	/**
	 * A platform at the world's bottom: stone on y min (but the hut's footprint, x 6-10, z 6-10, where its walls go), open
	 * above to y min + 6. Returns the level g (y min + 1) the bench and the builder stand on. What was there is put back.
	 */
	static int bottom(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int min = level.getMinBuildHeight();
		int base = helper.absolutePos(BlockPos.ZERO).getY();
		int g = min + 1 - base;
		Map<BlockPos, BlockState> was = new HashMap<>();
		for (int x = -1; x <= 16; x++) {
			for (int z = -1; z <= 16; z++) {
				for (int y = g - 1; y <= g + 5; y++) {
					BlockPos abs = helper.absolutePos(new BlockPos(x, y, z));
					was.put(abs, level.getBlockState(abs));
					level.removeBlockEntity(abs);
					boolean hole = x >= 6 && x <= 10 && z >= 6 && z <= 10;
					level.setBlock(abs, y == g - 1 && !hole ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		Leftovers.after(helper, () -> {
			AABB box = new AABB(helper.absolutePos(new BlockPos(-4, g - 70, -4))).expandTowards(24, 80, 24);
			for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
				e.discard();
			}
			for (Map.Entry<BlockPos, BlockState> e : was.entrySet()) {
				level.removeBlockEntity(e.getKey());
				level.setBlock(e.getKey(), e.getValue(), 2);
			}
		});
		return g;
	}
}
