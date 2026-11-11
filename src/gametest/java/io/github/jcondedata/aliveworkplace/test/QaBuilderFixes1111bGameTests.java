package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * QA (qa-lane-b-1111-0637) of four landed builder fixes, written from their specs:
 * <ul>
 * <li>B872 (docs/wiki/builder.md): plants with no item a survival player can hold cost what grows them. A bamboo shoot
 * costs one bamboo and is planted; each piece of a chorus plant's stalk costs a chorus flower, so a stalk two tall under
 * its flower asks three. Controls: a grown bamboo stalk and a chorus flower ask their own items.</li>
 * <li>B1117: frogspawn is never placed nor asked (a pond is built without it), next to other never-placed blocks, and a
 * paid pond finishes instead of waiting forever.</li>
 * <li>B1037 (docs/wiki/blueprint-library.md): a Table request (Get Blueprint, Store, Take Stored) works from exactly the
 * reach and is refused just past it, and from a Table that is loaded but far.</li>
 * <li>B1022: every stage id of every Wonder (on disk, plain and styled) is refused with table.wonder_only and the blank
 * kept, the switch off, on while ???, and on with the Wonder unlocked view-only by its plan; an ordinary build is still
 * handed out after.</li>
 * </ul>
 */
public class QaBuilderFixes1111bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation COTTAGE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "starter_cottage");
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final String WONDER_ONLY = "message.aliveworkplace.table.wonder_only";
	/** The Table's reach (docs/wiki/blueprint-library.md: within 8 blocks of the table). */
	private static final double REACH = 8.0;

	// ------------------------------------------------------------------------------------------------ B872

	/** Paid: a bamboo shoot on grass asks exactly one bamboo (and no shoot item), uses it up and stands again. */
	//$ gametest_ticks_batch HUGE '4000' '"qa1111bShootPaid"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1111bShootPaid")
	public void aPaidBambooShootUsesOneBambooAndIsPlanted(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.GRASS_BLOCK);
		s.put(0, 1, 0, Blocks.BAMBOO_SAPLING);
		QaScanRebuild1102dGameTests.Built b = QaScanItemless1103GameTests.paidRoundTrip(helper, s);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = b.b().site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.BAMBOO, 0) == 1, "the shoot's list " + asked + " doesn't ask exactly 1 bamboo");
		helper.assertTrue(!asked.containsKey(Items.AIR), "the list " + asked + " asks air");
		Container chest = QaScanItemless1103GameTests.stock(helper, asked);
		helper.succeedWhen(() -> {
			QaScanItemless1103GameTests.finished(helper, b);
			BlockState now = level.getBlockState(b.b().origin().offset(0, 1, 0));
			helper.assertTrue(now.is(Blocks.BAMBOO_SAPLING) || now.is(Blocks.BAMBOO), "the paid shoot was built as " + now);
			Villager builder = level.getEntitiesOfClass(Villager.class, helper.getBounds()).stream().findFirst().orElse(null);
			helper.assertTrue(QaScanItemless1103GameTests.held(chest, builder, Items.BAMBOO) == 0, "the bamboo paid for the shoot wasn't used: "
				+ QaScanItemless1103GameTests.held(chest, builder, Items.BAMBOO) + " left");
			helper.assertTrue(b.b().site().skipped() == 0, b.b().site().skipped() + " skipped");
		});
	}

	/** Control: a grown bamboo stalk three tall (bamboo has its own item) asks three bamboo and stands again. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1111bStalk"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1111bStalk")
	public void aScannedGrownBambooStalkAsksItsBamboo(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.GRASS_BLOCK);
		for (int y = 1; y <= 3; y++) {
			s.put(0, y, 0, Blocks.BAMBOO);
		}
		QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.BAMBOO, 3), false);
	}

	/** Control: a chorus flower alone on end stone asks its own item, one chorus flower, and stands again. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1111bFlower"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1111bFlower")
	public void aScannedChorusFlowerAsksOneChorusFlower(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.END_STONE);
		s.put(0, 1, 0, Blocks.CHORUS_FLOWER);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.CHORUS_FLOWER, 1, Items.END_STONE, 1), false);
		helper.assertTrue(!b.b().site().plan(helper.getLevel()).materials().containsKey(Items.CHORUS_PLANT), "a lone flower asks a chorus plant");
	}

	/**
	 * Paid: a stalk two tall under its flower on end stone asks exactly three chorus flowers (the spec's own example) and
	 * no chorus plant, and the paid build finishes with the plant standing again instead of waiting forever.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qa1111bChorusPaid"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1111bChorusPaid")
	public void aPaidChorusPlantUsesThreeChorusFlowersAndStandsAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.END_STONE);
		BlockState stalk = Blocks.CHORUS_PLANT.defaultBlockState().setValue(BlockStateProperties.UP, true).setValue(BlockStateProperties.DOWN, true);
		s.put(0, 1, 0, stalk);
		s.put(0, 2, 0, stalk);
		s.put(0, 3, 0, Blocks.CHORUS_FLOWER);
		QaScanRebuild1102dGameTests.Built b = QaScanItemless1103GameTests.paidRoundTrip(helper, s);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = b.b().site().plan(level).materials();
		helper.assertTrue(!asked.containsKey(Items.CHORUS_PLANT), "the list " + asked + " asks a chorus plant item");
		helper.assertTrue(asked.getOrDefault(Items.CHORUS_FLOWER, 0) == 3, "the list " + asked + " doesn't ask 3 chorus flowers for a stalk two tall under its flower");
		Container chest = QaScanItemless1103GameTests.stock(helper, asked);
		helper.succeedWhen(() -> {
			b.done(helper);
			Villager builder = level.getEntitiesOfClass(Villager.class, helper.getBounds()).stream().findFirst().orElse(null);
			helper.assertTrue(QaScanItemless1103GameTests.held(chest, builder, Items.CHORUS_FLOWER) == 0, "of 3 chorus flowers paid, "
				+ QaScanItemless1103GameTests.held(chest, builder, Items.CHORUS_FLOWER) + " were left unused");
		});
	}

	// ------------------------------------------------------------------------------------------------ B1117

	/**
	 * A pond of two water blocks, frogspawn on one and a lily pad on the other, a spawner (never placed) at its edge: the
	 * list asks the lily pad and two water buckets, never frogspawn or a spawner, and the pond is built with the lily pad
	 * and without the frogspawn.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1111bPondMix"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1111bPondMix")
	public void frogspawnBesideALilyPadAndASpawnerIsLeftOut(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int x = 0; x < 4; x++) {
			for (int z = 0; z < 3; z++) {
				s.put(x, 0, z, Blocks.STONE);
				if (z != 1 || (x != 1 && x != 2)) {
					s.put(x, 1, z, Blocks.STONE);
				}
			}
		}
		s.put(1, 1, 1, Blocks.WATER);
		s.put(2, 1, 1, Blocks.WATER);
		s.put(1, 2, 1, Blocks.FROGSPAWN);
		s.put(2, 2, 1, Blocks.LILY_PAD);
		s.put(0, 2, 0, Blocks.SPAWNER);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.LILY_PAD, 1, Items.WATER_BUCKET, 2, Items.STONE, 22), true);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = b.b().site().plan(level).materials();
		helper.assertTrue(!asked.containsKey(Items.FROGSPAWN) && !asked.containsKey(Items.SPAWNER), "the list " + asked + " asks frogspawn or a spawner");
		helper.succeedWhen(() -> {
			QaScanItemless1103GameTests.finished(helper, b);
			BlockPos o = b.b().origin();
			helper.assertTrue(level.getBlockState(o.offset(2, 2, 1)).is(Blocks.LILY_PAD), "the lily pad isn't back: " + level.getBlockState(o.offset(2, 2, 1)));
			helper.assertTrue(!level.getBlockState(o.offset(1, 2, 1)).is(Blocks.FROGSPAWN), "the builder put frogspawn down");
			helper.assertTrue(!level.getBlockState(o.offset(0, 2, 0)).is(Blocks.SPAWNER), "the builder put a spawner down");
			helper.assertTrue(level.getFluidState(o.offset(1, 1, 1)).isSource() && level.getFluidState(o.offset(2, 1, 1)).isSource(), "the pond's water isn't back");
		});
	}

	/** Paid: the frogspawn pond is stocked with exactly its list and finishes (the bug's paid build waited forever). */
	//$ gametest_ticks_batch HUGE '4000' '"qa1111bPondPaid"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1111bPondPaid")
	public void aPaidFrogspawnPondFinishes(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				s.put(x, 0, z, Blocks.STONE);
				if (x != 1 || z != 1) {
					s.put(x, 1, z, Blocks.STONE);
				}
			}
		}
		s.put(1, 1, 1, Blocks.WATER);
		s.put(1, 2, 1, Blocks.FROGSPAWN);
		QaScanRebuild1102dGameTests.Built b = QaScanItemless1103GameTests.paidRoundTrip(helper, s);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = b.b().site().plan(level).materials();
		helper.assertTrue(!asked.containsKey(Items.FROGSPAWN), "the paid list " + asked + " asks frogspawn");
		QaScanItemless1103GameTests.stock(helper, asked);
		helper.succeedWhen(() -> {
			QaScanItemless1103GameTests.finished(helper, b);
			helper.assertTrue(level.getFluidState(b.b().origin().offset(1, 1, 1)).isSource(), "the paid pond has no water");
		});
	}

	// ------------------------------------------------------------------------------------------------ B1037

	/**
	 * Each request kind at the edge of reach: just past 8 blocks from the Table's center (8.01) it is refused and nothing
	 * moves; at exactly 8 it works. Get Blueprint, then Store All, then Take Stored, alternately just past and exactly at.
	 * A Table that is loaded (in this test area) but 20 blocks off refuses Get Blueprint too.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1111bTableReach"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1111bTableReach")
	public void tableRequestsWorkAtExactlyTheReachAndNotJustPastIt(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		BlockPos tableRel = QaBlueprintUnlocksGameTests.TABLE;
		QaBlueprintUnlocksGameTests.village(helper, QaBlueprintUnlocksGameTests.HALL, tableRel);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, tableRel);
			ServerPlayer p = c.player();
			p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
			BlockPos table = helper.absolutePos(tableRel);
			Vec3 center = Vec3.atCenterOf(table);
			// loaded but far: the Table stands in this test area, the player 20 blocks off
			p.moveTo(center.x + 20, center.y, center.z, 90f, 0f);
			c.send(new TablePayloads.Take(table, COTTAGE));
			helper.assertTrue(bps(p) == 0 && QaBuilderFixes1107dGameTests.blanks(p) == 2, "Get Blueprint from 20 blocks off a loaded Table handed out "
				+ bps(p) + " blueprints");
			// just past
			p.moveTo(center.x + REACH + 0.01, center.y, center.z, 90f, 0f);
			c.send(new TablePayloads.Take(table, COTTAGE));
			helper.assertTrue(bps(p) == 0 && QaBuilderFixes1107dGameTests.blanks(p) == 2, "Get Blueprint from 8.01 blocks handed out " + bps(p) + " blueprints");
			// exactly at
			p.moveTo(center.x + REACH, center.y, center.z, 90f, 0f);
			c.send(new TablePayloads.Take(table, COTTAGE));
			helper.assertTrue(bps(p) == 1 && QaBuilderFixes1107dGameTests.blanks(p) == 1, "Get Blueprint from exactly 8 blocks: " + bps(p)
				+ " blueprints, " + QaBuilderFixes1107dGameTests.blanks(p) + " blanks (expected 1 and 1)");
			ItemStack held = heldBlueprint(p);
			helper.assertTrue(!held.isEmpty(), "no blueprint to store");
			ItemStack sample = held.copyWithCount(1);
			// Store: just past, then exactly at
			p.moveTo(center.x, center.y, center.z + REACH + 0.01, 0f, 0f);
			c.send(new TablePayloads.Store(table, true));
			helper.assertTrue(bps(p) == 1, "Store All from 8.01 blocks took the blueprint");
			p.moveTo(center.x, center.y, center.z + REACH, 0f, 0f);
			int before = c.seen().size();
			c.send(new TablePayloads.Store(table, true));
			helper.assertTrue(bps(p) == 0, "Store All from exactly 8 blocks left " + bps(p) + " blueprints with the player");
			Component stored = QaStageFourGameTests.find(c.seen().subList(before, c.seen().size()), "message.aliveworkplace.table.stored");
			helper.assertTrue(stored != null && stored.getString().startsWith("Stored ") && stored.getString().endsWith(" in the table")
				&& !stored.getString().contains("%"), "the Store line reads " + (stored == null ? "nothing: " + QaStageFourGameTests.keys(c.seen()) : "\"" + stored.getString() + "\""));
			// Take Stored: just past, then exactly at
			p.moveTo(center.x - REACH - 0.01, center.y, center.z, 0f, 0f);
			c.send(new TablePayloads.TakeStored(table, sample));
			helper.assertTrue(bps(p) == 0, "Take Stored from 8.01 blocks handed back " + bps(p) + " blueprints");
			p.moveTo(center.x - REACH, center.y, center.z, 0f, 0f);
			c.send(new TablePayloads.TakeStored(table, sample));
			helper.assertTrue(bps(p) == 1, "Take Stored from exactly 8 blocks handed back " + bps(p) + " blueprints");
			helper.succeed();
		});
	}

	private static int bps(ServerPlayer p) {
		return QaBuilderFixes1107dGameTests.blueprints(p);
	}

	private static ItemStack heldBlueprint(ServerPlayer p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
				return p.getInventory().getItem(i);
			}
		}
		return ItemStack.EMPTY;
	}

	// ------------------------------------------------------------------------------------------------ B1022

	/** Every Wonder stage on disk (any namespace, wonder/<id>/stage_*), plain and in a style. */
	private static List<ResourceLocation> allStages(ServerLevel level) {
		List<ResourceLocation> out = new ArrayList<>();
		level.getStructureManager().listTemplates()
			.filter(id -> id.getPath().startsWith("wonder/") && id.getPath().contains("/stage_"))
			.sorted()
			.forEach(id -> {
				out.add(id);
				out.add(BlueprintStyles.styled(id, "spruce"));
			});
		return out;
	}

	/** Asks for each id; none handed out, the blanks kept, a wonder_only line read with no stray placeholder or id. */
	private static void refuseAll(GameTestHelper helper, QaStageFiveGameTests.Client c, ServerPlayer p, BlockPos table, List<ResourceLocation> ids, String when) {
		int before = c.seen().size();
		for (ResourceLocation id : ids) {
			c.send(new TablePayloads.Take(table, id));
		}
		helper.assertTrue(bps(p) == 0 && QaBuilderFixes1107dGameTests.blanks(p) == 2, when + ": asking " + ids.size() + " Wonder stage ids left "
			+ bps(p) + " blueprints and " + QaBuilderFixes1107dGameTests.blanks(p) + " of 2 blanks");
		List<Component> said = c.seen().subList(before, c.seen().size());
		long refusals = QaBlueprintUnlocksGameTests.linesOf(said, WONDER_ONLY);
		helper.assertTrue(refusals == ids.size(), when + ": " + refusals + " wonder_only lines for " + ids.size() + " requests: " + QaStageFourGameTests.keys(said));
		for (Component line : said) {
			if (WONDER_ONLY.equals(QaStageFiveGameTests.key(line))) {
				String text = line.getString();
				helper.assertTrue(text.endsWith(" is raised from its Wonder Plan, not handed out at the Table") && !text.contains("%")
					&& !text.startsWith(" ") && !text.contains("stage_") && !text.contains(":"), when + ": the wonder_only line reads \"" + text + "\"");
			}
		}
	}

	/** Control after the refusals: the Starter Cottage is still handed out for one blank. */
	private static void cottageStillHandedOut(GameTestHelper helper, QaStageFiveGameTests.Client c, ServerPlayer p, BlockPos table, String when) {
		c.send(new TablePayloads.Take(table, COTTAGE));
		helper.assertTrue(bps(p) == 1 && QaBuilderFixes1107dGameTests.blanks(p) == 1, when + ": after the refusals the Starter Cottage gave "
			+ bps(p) + " blueprints, " + QaBuilderFixes1107dGameTests.blanks(p) + " blanks left (expected 1 and 1)");
	}

	/** Switch off: every stage of every Wonder on disk (the Colossus' four included), plain and styled, refused; cottage still given. */
	//$ gametest_ticks_batch AREA '60' '"qa1111bStagesOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1111bStagesOff")
	public void withTheSwitchOffEveryStageOfEveryWonderIsRefused(GameTestHelper helper) {
		QaBuilderFixes1107dGameTests.atTable(helper, false, (h, c, p, table, hall) -> {
			List<ResourceLocation> ids = allStages(helper.getLevel());
			for (int n = 1; n <= 4; n++) {
				ResourceLocation stage = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "wonder/colossus/stage_" + n + "_0");
				helper.assertTrue(ids.contains(stage), "the Colossus' stage " + n + " isn't on disk: " + ids.size() + " stage ids found");
			}
			helper.assertTrue(ids.stream().anyMatch(id -> id.getNamespace().equals("aliveworkplace_test")), "the test folly's stages aren't found");
			refuseAll(helper, c, p, table, ids, "switch off");
			cottageStillHandedOut(helper, c, p, table, "switch off");
			helper.succeed();
		});
	}

	/** Switch on, the Wonders still ???: every stage refused, the whole Wonder ids give nothing either; cottage still given. */
	//$ gametest_ticks_batch AREA '60' '"qa1111bStagesHidden"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1111bStagesHidden")
	public void withTheSwitchOnEveryStageOfAHiddenWonderIsRefused(GameTestHelper helper) {
		QaBuilderFixes1107dGameTests.atTable(helper, true, (h, c, p, table, hall) -> {
			refuseAll(helper, c, p, table, allStages(helper.getLevel()), "switch on, hidden");
			for (Wonder w : Wonders.loaded().values()) {
				c.send(new TablePayloads.Take(table, WonderBlueprints.idOf(w.id())));
			}
			helper.assertTrue(bps(p) == 0 && QaBuilderFixes1107dGameTests.blanks(p) == 2, "switch on: a hidden Wonder's whole id gave " + bps(p) + " blueprints");
			cottageStillHandedOut(helper, c, p, table, "switch on, hidden");
			helper.succeed();
		});
	}

	/**
	 * Switch on, the test folly unlocked view-only by using its Wonder Plan on the Table (the player's own entry point):
	 * its stages are still refused (first, middle and last), as is its whole id; the cottage is still given.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1111bStagesViewOnly"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1111bStagesViewOnly")
	public void anUnlockedWondersStagesAreStillRefused(GameTestHelper helper) {
		QaBuilderFixes1107dGameTests.atTable(helper, true, (h, c, p, table, hall) -> {
			ServerLevel level = helper.getLevel();
			Wonder folly = Wonders.loaded().get(FOLLY);
			helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
			ResourceLocation whole = WonderBlueprints.idOf(folly.id());
			p.setItemInHand(InteractionHand.MAIN_HAND, WonderPlanItem.of(level, hall, folly, Component.literal("Testholm")));
			// the plan took the hand the 2 blanks were put in: give the player 2 blanks again beside it
			p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2 - QaBuilderFixes1107dGameTests.blanks(p)));
			helper.assertTrue(QaBuilderFixes1107dGameTests.blanks(p) == 2, "setup: " + QaBuilderFixes1107dGameTests.blanks(p) + " blanks");
			QaBlueprintUnlocksGameTests.clickTable(helper, p, QaBlueprintUnlocksGameTests.TABLE);
			helper.assertTrue(BlueprintUnlocks.state(level, hall, whole) == BlueprintUnlocks.State.VIEW_ONLY,
				"the plan used on the Table left the folly " + BlueprintUnlocks.state(level, hall, whole));
			List<ResourceLocation> stages = new ArrayList<>();
			for (int n = 1; n <= 3; n++) {
				stages.add(ResourceLocation.fromNamespaceAndPath(FOLLY.getNamespace(), "wonder/" + FOLLY.getPath() + "/stage_" + n + "_0"));
			}
			refuseAll(helper, c, p, table, stages, "switch on, folly view-only");
			c.send(new TablePayloads.Take(table, whole));
			helper.assertTrue(bps(p) == 0 && QaBuilderFixes1107dGameTests.blanks(p) == 2, "the view-only folly's whole id gave " + bps(p) + " blueprints");
			cottageStillHandedOut(helper, c, p, table, "switch on, folly view-only");
			helper.succeed();
		});
	}
}
