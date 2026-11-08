package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA 2026-11-07 (d): adversarial checks of four builder fixes, written from docs/wiki/builder.md and
 * docs/wiki/blueprint-library.md.
 * <ul>
 * <li>B969: "The Table counts only while it still stands (one broken or blown up ... is passed over, and everything goes
 * on the ground nearest its old spot)": the bench replaced by a carpet, a bottom slab or stone, or broken on a pillar
 * in a lava lake; the message always names where everything lies (QaBuilderGaps1105GameTests#struck checks that).</li>
 * <li>B973: "Ground a player can walk or climb to ... comes before a nearer spot shut in a sealed cave": a room under a
 * wheat field whose only way out is a closed door or fence gate (a player opens those) is fine, a spot no player can
 * reach without digging is not; a causeway over lava never gets the bag in the lava.</li>
 * <li>B1002: with blueprintUnlocks off, Wonders are "never handed out" (refused with table.wonder_only, the Blank
 * Blueprint kept), while an ordinary build still is; with it on a hidden Wonder is refused too.</li>
 * <li>B1019: a lead "carries ... never more of a thing than the stretch of work ahead still needs", also when the
 * thing comes from a crewmate's bag: capped per material, nothing spare dropped, a short crewmate topped up later.</li>
 * </ul>
 */
public class QaBuilderFixes1107dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final int SURFACE = QaBuilderGaps1105bGameTests.SURFACE;
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final String WONDER_ONLY = "message.aliveworkplace.table.wonder_only";

	// ------------------------------------------------------------------------------------------------ B969

	/** The bench broken a tick before the bolt and a carpet laid where it stood: nothing is put "on" the carpet's spot in the air. */
	//$ gametest_ticks_batch HUGE '400' '"qa1107dBenchCarpet"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qa1107dBenchCarpet")
	public void aBenchSwappedForACarpetGetsTheBagOnRealGroundTheMessageNames(GameTestHelper helper) {
		benchSwapped(helper, Blocks.RED_CARPET.defaultBlockState());
	}

	/** The same with a bottom slab where the bench stood (its top isn't a full face). */
	//$ gametest_ticks_batch HUGE '400' '"qa1107dBenchSlab"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qa1107dBenchSlab")
	public void aBenchSwappedForABottomSlabGetsTheBagWhereTheMessageSays(GameTestHelper helper) {
		benchSwapped(helper, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
	}

	/** The same with a stone block a player put where the bench stood: it isn't a Table, but its top is solid ground. */
	//$ gametest_ticks_batch HUGE '400' '"qa1107dBenchStone"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qa1107dBenchStone")
	public void aBenchSwappedForStoneGetsTheBagWhereTheMessageSays(GameTestHelper helper) {
		benchSwapped(helper, Blocks.STONE.defaultBlockState());
	}

	private static void benchSwapped(GameTestHelper helper, BlockState instead) {
		BlockPos bench = new BlockPos(16, 2, 10);
		QaBuilderGaps1105GameTests.struck(helper, bench, new BlockPos(10, 2, 10), h -> {
		}, h -> {
			h.getLevel().destroyBlock(h.absolutePos(bench), false);
			h.setBlock(bench, instead);
		}, (told, lying) -> {
			helper.assertTrue(helper.getBlockState(bench).is(instead.getBlock()), "setup: the bench's spot holds " + helper.getBlockState(bench));
			LOGGER.info("qa 1107d bench swapped for {}: told {}, {} item stacks lie there", instead.getBlock(), QaBuilderGaps1105bGameTests.rel(helper, told).toShortString(), lying.size());
		});
	}

	/**
	 * The bench on a one-block stone pillar in a lava lake, broken a tick before the bolt: everything must lie together
	 * at the spot the message names, none of it burnt in the lava (the helper counts every item).
	 */
	//$ gametest_ticks_batch HUGE '400' '"qa1107dBenchLava"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qa1107dBenchLava")
	public void aBenchBrokenOnAPillarInALavaLakeLosesNothingToTheLava(GameTestHelper helper) {
		BlockPos bench = new BlockPos(16, 3, 10);
		QaBuilderGaps1105GameTests.struck(helper, bench, new BlockPos(8, 2, 10), h -> {
			for (int x = 11; x <= 21; x++) {
				for (int z = 3; z <= 17; z++) {
					boolean ring = x == 11 || x == 21 || z == 3 || z == 17;
					h.setBlock(new BlockPos(x, 2, z), ring || (x == 16 && z == 10) ? Blocks.STONE : Blocks.LAVA);
				}
			}
		}, h -> h.getLevel().destroyBlock(h.absolutePos(bench), false), (told, lying) -> {
			for (ItemEntity e : lying) {
				helper.assertFalse(helper.getLevel().getFluidState(e.blockPosition()).isSource() || e.isInLava(), "an item lies in the lava at "
					+ QaBuilderGaps1105bGameTests.rel(helper, e.blockPosition()).toShortString());
			}
			LOGGER.info("qa 1107d bench on a lava pillar: told {} (on {}), all {} stacks there", QaBuilderGaps1105bGameTests.rel(helper, told).toShortString(),
				helper.getLevel().getBlockState(told.below()).getBlock(), lying.size());
		});
	}

	// ------------------------------------------------------------------------------------------------ B973

	/**
	 * A wheat field (nothing can lie on it) over a room whose only way out is a tunnel shut by a closed oak door into a
	 * pit open to the sky beside the field. A player opens doors, so the room counts as reachable; wherever it goes, a
	 * player must get there without digging, and it all lies where the message says.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qa1107dFieldDoor"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qa1107dFieldDoor")
	public void aBuilderStruckOverARoomShutByADoorGetsHisBagWhereAPlayerCanGo(GameTestHelper helper) {
		fieldOverRoom(helper, h -> {
			BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.OPEN, false);
			h.setBlock(new BlockPos(3, 3, 10), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			h.setBlock(new BlockPos(3, 4, 10), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		}, "door");
	}

	/** The same, the tunnel shut by two closed fence gates stacked (a player opens those too). */
	//$ gametest_ticks_batch HUGE '400' '"qa1107dFieldGate"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qa1107dFieldGate")
	public void aBuilderStruckOverARoomShutByFenceGatesGetsHisBagWhereAPlayerCanGo(GameTestHelper helper) {
		fieldOverRoom(helper, h -> {
			BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST).setValue(FenceGateBlock.OPEN, false);
			h.setBlock(new BlockPos(3, 3, 10), gate);
			h.setBlock(new BlockPos(3, 4, 10), gate);
		}, "fence gates");
	}

	private static void fieldOverRoom(GameTestHelper helper, java.util.function.Consumer<GameTestHelper> shut, String what) {
		QaBuilderGaps1105GameTests.struck(helper, new BlockPos(13, SURFACE, 10), new BlockPos(10, SURFACE, 10), h -> {
			QaBuilderGaps1105bGameTests.earth(h, (x, z) -> QaBuilderGaps1105bGameTests.field(x, z)
					? Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7) : Blocks.GRASS_BLOCK.defaultBlockState(),
				(x, z) -> QaBuilderGaps1105bGameTests.field(x, z) ? Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7) : null);
			// the room under the field, 4 to 6 under its surface
			carve(h, 8, 16, 3, 5, 7, 13);
			// the tunnel west from it, two high
			carve(h, 4, 7, 3, 4, 10, 10);
			// the pit beside the field, open to the sky, its floor stone at y 2
			carve(h, 1, 2, 3, SURFACE, 9, 11);
			shut.accept(h);
		}, h -> {
		}, (told, lying) -> {
			BlockPos rel = QaBuilderGaps1105bGameTests.rel(helper, told);
			boolean inRoom = rel.getX() >= 4 && rel.getX() <= 16 && rel.getY() <= 5 && rel.getZ() >= 7 && rel.getZ() <= 13;
			int steps = reachable(helper, told);
			LOGGER.info("qa 1107d field over a room shut by {}: told {} ({}), reached the sky in {} open spots", what, rel.toShortString(),
				inRoom ? "in the room" : "outside it", steps);
		});
	}

	private static void carve(GameTestHelper h, int x0, int x1, int y0, int y1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	/**
	 * A player can get to {@code told} without breaking a block: through spots with nothing solid, or a door, fence gate
	 * or trapdoor (a player opens them), it joins the open sky. Returns how many spots it searched.
	 */
	private static int reachable(GameTestHelper helper, BlockPos told) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds().inflate(10);
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(told));
		Set<BlockPos> seen = new HashSet<>(List.of(told));
		while (!queue.isEmpty() && seen.size() < 40000) {
			BlockPos p = queue.poll();
			if (level.canSeeSky(p)) {
				return seen.size();
			}
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (!box.contains(Vec3.atCenterOf(n)) || seen.contains(n)) {
					continue;
				}
				BlockState s = level.getBlockState(n);
				Block b = s.getBlock();
				if (s.getCollisionShape(level, n).isEmpty() || b instanceof DoorBlock || b instanceof FenceGateBlock || b instanceof TrapDoorBlock) {
					seen.add(n);
					queue.add(n);
				}
			}
		}
		throw new GameTestAssertException("the message names " + QaBuilderGaps1105bGameTests.rel(helper, told).toShortString() + " (on "
			+ level.getBlockState(told.below()).getBlock().getName().getString() + "), shut in: a player can't get there without digging, even opening doors ("
			+ seen.size() + " open spot(s) round it, none under the sky)");
	}

	/**
	 * A builder on a 3-wide stone causeway across a lava lake, his bench on the causeway 2 blocks from him (in the bolt's
	 * reach): it all lies together on dry ground at least 4 blocks off, where the message says, nothing in or beside the
	 * lava burnt.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qa1107dCauseway"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qa1107dCauseway")
	public void aBuilderStruckOnACausewayOverLavaGetsHisBagOnTheCauseway(GameTestHelper helper) {
		QaBuilderGaps1105GameTests.struck(helper, new BlockPos(12, 3, 10), new BlockPos(10, 3, 10), h -> {
			for (int x = 0; x <= 20; x++) {
				for (int z = 0; z <= 20; z++) {
					boolean ring = x == 0 || x == 20 || z == 0 || z == 20;
					boolean causeway = z >= 9 && z <= 11;
					h.setBlock(new BlockPos(x, 2, z), ring || causeway ? Blocks.STONE : Blocks.LAVA);
				}
			}
		}, h -> {
		}, (told, lying) -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(level.getFluidState(told.below()).isEmpty() && !level.getBlockState(told.below()).is(Blocks.LAVA),
				"the message names a spot over lava: " + QaBuilderGaps1105bGameTests.rel(helper, told).toShortString());
			LOGGER.info("qa 1107d causeway over lava: told {} on {}, {} stacks there", QaBuilderGaps1105bGameTests.rel(helper, told).toShortString(),
				level.getBlockState(told.below()).getBlock(), lying.size());
		});
	}

	// ------------------------------------------------------------------------------------------------ B1002

	interface AtTable {
		void run(GameTestHelper helper, QaStageFiveGameTests.Client c, ServerPlayer p, BlockPos table, BlockPos hall);
	}

	/** A hall and a Table, the switch {@code on}, a survival player by the Table with 2 Blank Blueprints. */
	static void atTable(GameTestHelper helper, boolean on, AtTable then) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		BlueprintUnlocks.ENABLED = on;
		BlockPos hallRel = QaBlueprintUnlocksGameTests.HALL;
		BlockPos tableRel = QaBlueprintUnlocksGameTests.TABLE;
		QaBlueprintUnlocksGameTests.village(helper, hallRel, tableRel);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, tableRel);
			ServerPlayer p = c.player();
			p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
			then.run(helper, c, p, helper.absolutePos(tableRel), helper.absolutePos(hallRel));
		});
	}

	static int blueprints(ServerPlayer p) {
		return p.getInventory().countItem(ModItems.BLUEPRINT);
	}

	static int blanks(ServerPlayer p) {
		return p.getInventory().countItem(ModItems.BLANK_BLUEPRINT);
	}

	/** Regression: with the switch off an ordinary build (the Starter Cottage) is still handed out for one Blank Blueprint. */
	//$ gametest_ticks_batch AREA '60' '"qa1107dTableOffCottage"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1107dTableOffCottage")
	public void withTheSwitchOffAnOrdinaryBuildIsStillHandedOut(GameTestHelper helper) {
		atTable(helper, false, (h, c, p, table, hall) -> {
			ResourceLocation id = StarterBlueprints.STARTER_COTTAGE.id();
			c.send(new TablePayloads.Take(table, id));
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(blueprints(p) == 1 && blanks(p) == 1, "with the switch off Get Blueprint for the Starter Cottage left "
					+ blueprints(p) + " blueprints and " + blanks(p) + " blanks (wanted 1 and 1): " + QaStageFourGameTests.keys(c.seen()));
				ResourceLocation got = null;
				for (ItemStack s : p.getInventory().items) {
					if (s.is(ModItems.BLUEPRINT)) {
						got = BlueprintItem.data(s).map(BlueprintData::structure).orElse(null);
					}
				}
				helper.assertTrue(id.equals(got), "the blueprint handed out is of " + got + ", not " + id);
				helper.assertFalse(QaStageFourGameTests.saw(c.seen(), WONDER_ONLY), "the cottage was refused as a Wonder");
				LOGGER.info("qa 1107d table off: the Starter Cottage handed out, 1 blank used");
				helper.succeed();
			});
		});
	}

	/**
	 * With the switch off, every loaded Wonder (the mod's and the test folly) asked for over the network, plain and in a
	 * style, by a survival player and then a creative one, is refused: no blueprint, no Blank Blueprint used, and the
	 * player reads the wonder_only line with the Wonder's name filled in.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1107dTableOffWonders"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1107dTableOffWonders")
	public void withTheSwitchOffNoWonderIsHandedOutPlainStyledOrInCreative(GameTestHelper helper) {
		atTable(helper, false, (h, c, p, table, hall) -> {
			helper.assertTrue(Wonders.loaded().containsKey(FOLLY), "the test folly isn't loaded: " + Wonders.problems());
			List<ResourceLocation> asked = new ArrayList<>();
			for (Wonder w : Wonders.loaded().values()) {
				ResourceLocation id = WonderBlueprints.idOf(w.id());
				asked.add(id);
				asked.add(BlueprintStyles.styled(id, "spruce"));
			}
			for (ResourceLocation id : asked) {
				c.send(new TablePayloads.Take(table, id));
			}
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(blueprints(p) == 0 && blanks(p) == 2, "with the switch off, asking for " + asked.size() + " Wonder ids left "
					+ blueprints(p) + " blueprints and " + blanks(p) + " blanks");
				Component line = QaStageFourGameTests.find(c.seen(), WONDER_ONLY);
				helper.assertTrue(line != null, "no wonder_only line: " + QaStageFourGameTests.keys(c.seen()));
				String text = line.getString();
				helper.assertTrue(text.endsWith(" is raised from its Wonder Plan, not handed out at the Table") && !text.contains("%")
					&& !text.startsWith(" ") && !text.contains("aliveworkplace") && !text.contains("wonder/"),
					"the wonder_only line reads \"" + text + "\"");
				p.setGameMode(GameType.CREATIVE);
				int lines = c.seen().size();
				c.send(new TablePayloads.Take(table, WonderBlueprints.idOf(FOLLY)));
				helper.runAfterDelay(3, () -> {
					helper.assertTrue(blueprints(p) == 0, "with the switch off a creative player was handed the folly");
					helper.assertTrue(c.seen().size() > lines, "the creative player was told nothing");
					LOGGER.info("qa 1107d table off: {} Wonder ids refused (plain and styled), creative too; the line reads \"{}\"", asked.size(), text);
					helper.succeed();
				});
			});
		});
	}

	/** With the switch on, the folly still a "???" (no plan used): the request is refused, no blank used, no Wonder name leaked. */
	//$ gametest_ticks_batch AREA '60' '"qa1107dTableOnHidden"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1107dTableOnHidden")
	public void withTheSwitchOnAHiddenWonderIsRefusedAsBefore(GameTestHelper helper) {
		atTable(helper, true, (h, c, p, table, hall) -> {
			ResourceLocation id = WonderBlueprints.idOf(FOLLY);
			helper.assertTrue(BlueprintUnlocks.state(helper.getLevel(), hall, id) == BlueprintUnlocks.State.HIDDEN, "setup: the folly is "
				+ BlueprintUnlocks.state(helper.getLevel(), hall, id));
			c.send(new TablePayloads.Take(table, id));
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(blueprints(p) == 0 && blanks(p) == 2, "with the switch on the hidden folly left " + blueprints(p) + " blueprints and "
					+ blanks(p) + " blanks");
				helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.table.locked_hidden"),
					"the hidden folly wasn't refused as a ???: " + QaStageFourGameTests.keys(c.seen()));
				LOGGER.info("qa 1107d table on: hidden folly refused with {}", QaStageFourGameTests.keys(c.seen()));
				helper.succeed();
			});
		});
	}

	// ------------------------------------------------------------------------------------------------ B1019

	/** Where the scanned build is drawn, a row along x. */
	private static final BlockPos SRC = new BlockPos(21, 2, 25);
	private static final BlockPos LEAD = new BlockPos(3, 8, 3);
	private static final BlockPos BENCH = new BlockPos(2, 8, 3);
	/** The build's first block; the row runs x 1 to 5 at z 6, y 8, each on its own pillar. */
	private static final BlockPos ROW = new BlockPos(1, 8, 6);

	private record Crew(ServerLevel level, Villager lead, Villager mate, BuildSite site, Map<Item, Integer> need) {
	}

	/**
	 * The lead walled in a glass cell by its bench (no chest, no Storehouse) building a row of {@code row} blocks, a
	 * crewmate (no AI, counted on the site every tick) 4 blocks off with {@code stock} in its bag.
	 */
	private static Crew crew(GameTestHelper helper, Item[] row, Map<Item, Integer> stock) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> need = new LinkedHashMap<>();
		for (int i = 0; i < row.length; i++) {
			helper.setBlock(SRC.offset(i, 0, 0), Block.byItem(row[i]));
			need.merge(row[i], 1, Integer::sum);
		}
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.offset(row.length - 1, 0, 0));
		for (int i = 0; i < row.length; i++) {
			helper.setBlock(SRC.offset(i, 0, 0), Blocks.AIR);
		}
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		for (int i = 0; i < row.length; i++) {
			for (int y = 2; y < ROW.getY(); y++) {
				helper.setBlock(new BlockPos(ROW.getX() + i, y, ROW.getZ()), Blocks.STONE);
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(LEAD.offset(dx, -1, dz), Blocks.STONE);
				for (int dy = 0; dy <= 2; dy++) {
					BlockPos p = LEAD.offset(dx, dy, dz);
					if (!p.equals(LEAD) && !p.equals(LEAD.above())) {
						helper.setBlock(p, Blocks.GLASS);
					}
				}
			}
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager lead = helper.spawn(EntityType.VILLAGER, LEAD);
		Builders.employ(level, lead, helper.absolutePos(BENCH));
		Villager mate = EntityType.VILLAGER.create(level);
		if (mate == null) {
			throw new GameTestAssertException("no villager made");
		}
		mate.setNoAi(true);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(LEAD)).add(4, 0, 0);
		mate.moveTo(at.x, at.y, at.z, 0, 0);
		level.addFreshEntity(mate);
		stock.forEach((item, n) -> ModAttachments.BUILDER_BAG.getOrCreate(mate).addAll(item, n));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, lead, null, id, placement);
		helper.assertTrue(site != null, "the row build didn't start");
		site.seen(mate.getUUID(), level.getGameTime());
		Leftovers.after(helper, () -> {
			lead.discard();
			mate.discard();
		});
		return new Crew(level, lead, mate, site, need);
	}

	private static int bag(Villager v, Item item) {
		return ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
	}

	private static int held(Villager v, Item item) {
		return bag(v, item) + v.getInventory().countItem(item);
	}

	private static int placed(GameTestHelper helper, Item item, int length) {
		int n = 0;
		for (int i = 0; i < length; i++) {
			if (helper.getBlockState(ROW.offset(i, 0, 0)).is(Block.byItem(item))) {
				n++;
			}
		}
		return n;
	}

	private static int onGround(GameTestHelper helper, Item item) {
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(30, 30, 30).inflate(4);
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.isAlive() && e.getItem().is(item)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static String where(GameTestHelper helper, Crew r, int length) {
		StringBuilder s = new StringBuilder("stage " + r.site().stage() + ", status " + r.site().status() + ", skipped " + r.site().skipped());
		r.need().keySet().forEach(item -> s.append("; ").append(item).append(": mate ").append(bag(r.mate(), item)).append(", lead ")
			.append(held(r.lead(), item)).append(", ground ").append(onGround(helper, item)).append(", built ").append(placed(helper, item, length)));
		return s.toString();
	}

	/**
	 * Watches every tick that the lead never holds more of a material than is still to be built of it, and that no
	 * material is made or lost; returns the first breach (or null).
	 */
	private static String[] watch(GameTestHelper helper, Crew r, int length, Map<Item, Integer> total) {
		String[] off = {null};
		helper.onEachTick(() -> {
			if (r.mate().isAlive()) {
				r.site().seen(r.mate().getUUID(), r.level().getGameTime());
			}
			if (off[0] != null) {
				return;
			}
			for (Map.Entry<Item, Integer> e : r.need().entrySet()) {
				Item item = e.getKey();
				int built = placed(helper, item, length);
				int still = e.getValue() - built;
				int holds = held(r.lead(), item);
				int sum = bag(r.mate(), item) + holds + onGround(helper, item) + built;
				if (holds > still) {
					off[0] = "at tick " + helper.getTick() + " the lead holds " + holds + " " + item + " with only " + still + " still to build: " + where(helper, r, length);
				} else if (sum != total.get(item)) {
					off[0] = "at tick " + helper.getTick() + " there were " + sum + " " + item + ", not " + total.get(item) + ": " + where(helper, r, length);
				}
			}
		});
		return off;
	}

	/** A crewmate holds 40 stone, the build needs 5: the lead never holds more than is left to build; mate keeps 35, nothing on the ground. */
	//$ gametest_ticks_batch HUGE '1500' '"qa1107dCrewCap"'
	@GameTest(template = HUGE, timeoutTicks = 1500, batch = "qa1107dCrewCap")
	public void aLeadTakesOnlyTheFiveStoneItsRowNeedsFromACrewmateHoldingForty(GameTestHelper helper) {
		Item[] row = {Items.STONE, Items.STONE, Items.STONE, Items.STONE, Items.STONE};
		Crew r = crew(helper, row, Map.of(Items.STONE, 40));
		String[] off = watch(helper, r, row.length, Map.of(Items.STONE, 40));
		helper.succeedWhen(() -> {
			helper.assertTrue(off[0] == null, off[0] == null ? "" : off[0]);
			helper.assertTrue(placed(helper, Items.STONE, 5) == 5 && r.site().stage() == BuildPlan.Stage.DONE
				&& Builders.activeSite(r.level(), r.lead()) == null, "not built yet: " + where(helper, r, 5));
			helper.assertTrue(bag(r.mate(), Items.STONE) == 35 && held(r.lead(), Items.STONE) == 0 && onGround(helper, Items.STONE) == 0,
				"after the 5-stone row: " + where(helper, r, 5));
			LOGGER.info("qa 1107d crew cap: row built at tick {}, mate kept 35, lead 0, ground 0, never more than needed", helper.getTick());
		});
	}

	/** Two materials (3 stone, 2 oak planks) from a crewmate holding 40 of each: each capped on its own, both 37 and 38 kept. */
	//$ gametest_ticks_batch HUGE '1500' '"qa1107dCrewTwo"'
	@GameTest(template = HUGE, timeoutTicks = 1500, batch = "qa1107dCrewTwo")
	public void aLeadTakesEachOfTwoMaterialsOnlyAsFarAsTheRowNeedsIt(GameTestHelper helper) {
		Item[] row = {Items.STONE, Items.STONE, Items.STONE, Items.OAK_PLANKS, Items.OAK_PLANKS};
		Map<Item, Integer> stock = Map.of(Items.STONE, 40, Items.OAK_PLANKS, 40);
		Crew r = crew(helper, row, stock);
		String[] off = watch(helper, r, row.length, stock);
		helper.succeedWhen(() -> {
			helper.assertTrue(off[0] == null, off[0] == null ? "" : off[0]);
			helper.assertTrue(placed(helper, Items.STONE, 5) == 3 && placed(helper, Items.OAK_PLANKS, 5) == 2 && r.site().stage() == BuildPlan.Stage.DONE
				&& Builders.activeSite(r.level(), r.lead()) == null, "not built yet: " + where(helper, r, 5));
			helper.assertTrue(bag(r.mate(), Items.STONE) == 37 && bag(r.mate(), Items.OAK_PLANKS) == 38 && held(r.lead(), Items.STONE) == 0
				&& held(r.lead(), Items.OAK_PLANKS) == 0 && onGround(helper, Items.STONE) == 0 && onGround(helper, Items.OAK_PLANKS) == 0,
				"after the row: " + where(helper, r, 5));
			LOGGER.info("qa 1107d crew two: built at tick {}, mate kept 37 stone and 38 planks, nothing spare", helper.getTick());
		});
	}

	/**
	 * The crewmate holds only 3 of the 5 stone: the lead takes the 3 (never more than it can build), builds them and
	 * waits; then the crewmate is given 2 more, and the row is finished with them, nothing left over anywhere.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1107dCrewShort"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1107dCrewShort")
	public void aCrewmateShortOfStoneGivesWhatItHasAndTheRowFinishesWhenItGetsMore(GameTestHelper helper) {
		Item[] row = {Items.STONE, Items.STONE, Items.STONE, Items.STONE, Items.STONE};
		Map<Item, Integer> total = new java.util.HashMap<>(Map.of(Items.STONE, 3));
		Crew r = crew(helper, row, Map.of(Items.STONE, 3));
		String[] off = watch(helper, r, row.length, total);
		long[] toppedAt = {-1};
		String[] firstPhase = {null};
		helper.onEachTick(() -> {
			if (toppedAt[0] < 0 && placed(helper, Items.STONE, 5) == 3 && bag(r.mate(), Items.STONE) == 0 && held(r.lead(), Items.STONE) == 0) {
				firstPhase[0] = where(helper, r, 5);
				toppedAt[0] = helper.getTick();
				total.put(Items.STONE, 5);
				ModAttachments.BUILDER_BAG.getOrCreate(r.mate()).addAll(Items.STONE, 2);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(off[0] == null, off[0] == null ? "" : off[0]);
			helper.assertTrue(toppedAt[0] >= 0, "the lead never built the crewmate's 3 stone: " + where(helper, r, 5));
			helper.assertTrue(placed(helper, Items.STONE, 5) == 5 && r.site().stage() == BuildPlan.Stage.DONE
				&& Builders.activeSite(r.level(), r.lead()) == null, "the row isn't finished with the 2 more stone: " + where(helper, r, 5));
			helper.assertTrue(bag(r.mate(), Items.STONE) == 0 && held(r.lead(), Items.STONE) == 0 && onGround(helper, Items.STONE) == 0,
				"after the row: " + where(helper, r, 5));
			LOGGER.info("qa 1107d crew short: 3 built by tick {} ({}), topped up, finished at tick {}", toppedAt[0], firstPhase[0], helper.getTick());
		});
	}
}
