package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.city.CityZones;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * QA (adversarial, from the spec of ROADMAP 27.2, 27.3 and 27.4, not the code): every limit from both sides (16 zones,
 * 24 roads, 64 points, the grid's edge cells, the cell size at radius 64 and 128), malformed and unauthorised packets,
 * the plan on the hall item and in saves, the shipped zone kinds and the map tints at the grid's corners and radius 128.
 *
 * <p>Every test has its own batch: a corner cell of the 32×32 grid is 60 blocks from its hall, so a neighbouring test's
 * hall would otherwise be nearer and the cell refused.
 */
public class QaCityPlanGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(8, 2, 8);

	/** An edit as the screen sends it: written to a packet and read back, as the server receives it. */
	static CityPlans.Edit sent(ServerLevel level, CityPlans.Edit edit) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
		CityPlans.Edit.CODEC.encode(buf, edit);
		return CityPlans.Edit.CODEC.decode(buf);
	}

	static BitSet cells(int... cells) {
		BitSet out = new BitSet();
		for (int c : cells) {
			out.set(c);
		}
		return out;
	}

	static BlockPos p(int dx, int dz) {
		return new BlockPos(dx, 0, dz);
	}

	static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
	}

	/** Removes every Village Hall within 180 blocks of {@code at} that earlier batches left behind (outside their areas, never cleared). */
	static void otherHalls(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos centre = helper.absolutePos(at);
		for (BlockPos old : level.getPoiManager().findAll(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.VILLAGE_HALL_POI), q -> true,
			centre, 180, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			level.removeBlock(old, false);
		}
	}

	static VillageHallBlockEntity reload(ServerLevel level, VillageHallBlockEntity entity, CompoundTag tag) {
		BlockPos at = entity.getBlockPos();
		return (VillageHallBlockEntity) BlockEntity.loadStatic(at, level.getBlockState(at), tag, level.registryAccess());
	}

	/** {@code text} in English from the mod's en_us.json, with positional (%1$s) and plain (%s) arguments. */
	static String english(Component text) {
		JsonObject lang;
		try (InputStream in = Files.newInputStream(FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow()
				.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new GameTestAssertException("can't read en_us.json: " + e);
		}
		TranslatableContents contents = (TranslatableContents) text.getContents();
		if (!lang.has(contents.getKey())) {
			throw new GameTestAssertException("no English for " + contents.getKey());
		}
		String format = lang.get(contents.getKey()).getAsString();
		Object[] args = contents.getArgs();
		Matcher m = Pattern.compile("%(?:(\\d+)\\$)?s").matcher(format);
		StringBuilder out = new StringBuilder();
		int next = 0;
		while (m.find()) {
			int i = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
			if (i >= args.length) {
				throw new GameTestAssertException(contents.getKey() + " uses argument " + (i + 1) + " of " + args.length);
			}
			Object arg = args[i];
			m.appendReplacement(out, Matcher.quoteReplacement(arg instanceof Component c ? c.getString() : String.valueOf(arg)));
		}
		m.appendTail(out);
		return out.toString();
	}

	// --- 27.2: zones -----------------------------------------------------------------------------------------------

	/** 16 zones go on through the packet, the 17th is refused; one taken off makes room again. Every shipped kind can be added. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPlanZones16"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPlanZones16")
	public void sixteenZonesAreAcceptedAndASeventeenthRefused(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<String> kinds = List.of("homes", "workshops", "farms", "market", "civic", "gardens", "defences", "keep_clear");
		for (int i = 0; i < CityPlan.MAX_ZONES; i++) {
			String kind = kinds.get(i % kinds.size());
			helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, kind, "Z" + i, ""))), "zone " + (i + 1) + " (" + kind + ") refused");
		}
		helper.assertTrue(entity.plan().zones().size() == 16, "zones: " + entity.plan().zones().size());
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "Z17", ""))), "a 17th zone went on");
		helper.assertTrue(entity.plan().zones().size() == 16, "zones after the 17th: " + entity.plan().zones().size());
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.removeZone(hall, 3))), "taking zone 4 off was refused");
		helper.assertTrue(entity.plan().zones().size() == 15 && entity.plan().zones().stream().noneMatch(z -> z.name().equals("Z3")),
			"the wrong zone went: " + entity.plan().zones().stream().map(CityPlan.Zone::name).toList());
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "gardens", "Again", ""))), "no room after taking one off");
		helper.assertTrue(entity.plan().zones().get(15).name().equals("Again"), "the new zone isn't last: " + entity.plan().zones().get(15));
		helper.succeed();
	}

	/** The 8 shipped kinds: the spec's order, its banner colours (as VillageMaps.Kind's), only Keep Clear unbuildable, an English name each. */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '20' '"qaCityPlanKinds"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "qaCityPlanKinds")
	public void theEightShippedKindsHaveTheSpecsColoursAndNames(GameTestHelper helper) {
		Map<String, DyeColor> want = new java.util.LinkedHashMap<>();
		want.put("homes", DyeColor.WHITE);
		want.put("workshops", DyeColor.ORANGE);
		want.put("farms", DyeColor.LIME);
		want.put("market", DyeColor.YELLOW);
		want.put("civic", DyeColor.BLUE);
		want.put("gardens", DyeColor.LIGHT_BLUE);
		want.put("defences", DyeColor.BLACK);
		want.put("keep_clear", DyeColor.RED);
		List<String> shipped = CityZones.all().stream().map(CityZones.Kind::id).filter(want::containsKey).toList();
		helper.assertTrue(shipped.equals(List.copyOf(want.keySet())), "the shipped kinds in order: " + shipped);
		// as the village map's banners: Homes, Workshops, Farms, Trade, Learning, Decorations, Defence
		helper.assertTrue(VillageMaps.Kind.HOMES.color == DyeColor.WHITE && VillageMaps.Kind.WORKSHOPS.color == DyeColor.ORANGE
			&& VillageMaps.Kind.FARMS.color == DyeColor.LIME && VillageMaps.Kind.TRADE.color == DyeColor.YELLOW && VillageMaps.Kind.LEARNING.color == DyeColor.BLUE
			&& VillageMaps.Kind.DECORATIONS.color == DyeColor.LIGHT_BLUE && VillageMaps.Kind.DEFENCE.color == DyeColor.BLACK, "the map's banner colours changed");
		Map<String, String> names = Map.of("homes", "Homes", "workshops", "Workshops", "farms", "Farms", "market", "Market", "civic", "Civic",
			"gardens", "Gardens", "defences", "Defenses", "keep_clear", "Keep Clear");
		java.util.Set<Integer> tints = new java.util.HashSet<>();
		for (var e : want.entrySet()) {
			CityZones.Kind kind = CityZones.get(e.getKey()).orElseThrow();
			helper.assertTrue(kind.color() == e.getValue(), e.getKey() + " is " + kind.color() + ", not " + e.getValue());
			helper.assertTrue(kind.buildable() == !e.getKey().equals("keep_clear"), e.getKey() + " buildable: " + kind.buildable());
			helper.assertTrue(english(kind.title()).equals(names.get(e.getKey())), e.getKey() + " reads \"" + english(kind.title()) + "\"");
			helper.assertTrue(!kind.iconItem().equals(net.minecraft.world.item.Items.PAPER), e.getKey() + "'s icon " + kind.icon() + " isn't an item");
			tints.add(kind.mapTint());
		}
		helper.assertTrue(tints.size() == 8, "two shipped kinds share a map tint: " + tints);
		// a pack's file with no map_tint takes its dye's colour; with "buildable": false it is a keep-clear kind; no icon is refused
		CityZones.Kind plain = CityZones.read("mypack:docks", JsonParser.parseString("{\"color\": \"cyan\", \"icon\": \"minecraft:oak_boat\"}").getAsJsonObject());
		helper.assertTrue(plain.mapTint() == (DyeColor.CYAN.getTextureDiffuseColor() & 0xFFFFFF) && plain.buildable() && plain.order() > 7,
			"a pack's kind with defaults: " + plain);
		CityZones.Kind wild = CityZones.read("mypack:wilds", JsonParser.parseString("{\"color\": \"green\", \"icon\": \"minecraft:fern\", \"buildable\": false}").getAsJsonObject());
		helper.assertFalse(wild.buildable(), "\"buildable\": false was ignored");
		boolean refused = false;
		try {
			CityZones.read("mypack:noicon", JsonParser.parseString("{\"color\": \"green\"}").getAsJsonObject());
		} catch (RuntimeException e) {
			refused = true;
		}
		helper.assertTrue(refused, "a kind with no icon was read");
		helper.succeed();
	}

	/** Keep Clear: painted, zoneAt finds it and nothing may be built there, but a road may cross it. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPlanKeepClear"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPlanKeepClear")
	public void keepClearIsUnbuildableButARoadMayCrossIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "keep_clear", "The old oak", ""))), "no Keep Clear zone");
		BitSet strip = new BitSet();
		for (int dx = -12; dx <= 12; dx += 4) {
			strip.set(CityPlan.cellAt(hall, hall.offset(dx, 0, 4)));
		}
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, strip))), "Keep Clear wasn't painted");
		Optional<CityPlan.Zone> at = CityPlan.zoneAt(level, hall.offset(0, 0, 4));
		helper.assertTrue(at.isPresent() && at.get().kind().equals("keep_clear"), "zoneAt by the hall: " + at);
		helper.assertFalse(CityZones.get(at.get().kind()).orElseThrow().buildable(), "Keep Clear may be built on");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(0, -20), p(0, 20)), CityPlan.Road.STREET, ""))),
			"a road across Keep Clear was refused");
		helper.assertTrue(entity.plan().roads().size() == 1 && entity.plan().roads().get(0).style().isEmpty(),
			"the road crossing Keep Clear: " + entity.plan().roads());
		helper.succeed();
	}

	// --- 27.2: the grid ----------------------------------------------------------------------------------------------

	/** Cells 0 and 31 along each side are on the grid, one block further (-1 and 32) isn't, at radius 64 (cell 4) and 128 (cell 8), round a hall at negative coordinates too. */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '20' '"qaCityPlanGridEdge"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "qaCityPlanGridEdge")
	public void theGridsEdgeCellsAreOnItAndOneBlockFurtherIsnt(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		for (int[] rs : new int[][] {{64, 4}, {128, 8}}) {
			VillageHalls.RADIUS = rs[0];
			helper.assertTrue(CityPlan.cellSize() == rs[1], "radius " + rs[0] + ": a cell is " + CityPlan.cellSize() + " blocks, not " + rs[1]);
			int half = rs[0];
			helper.assertTrue(CityPlan.half() == half, "radius " + rs[0] + ": the grid reaches " + CityPlan.half());
			for (BlockPos hall : new BlockPos[] {new BlockPos(0, 64, 0), new BlockPos(-7777, 64, -31), new BlockPos(41, 64, -1)}) {
				String at = "radius " + rs[0] + ", hall " + hall.toShortString() + ": ";
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(-half, 0, -half)) == 0, at + "north-west corner isn't cell 0");
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(half - 1, 0, -half)) == 31, at + "north-east corner isn't cell 31");
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(-half, 0, half - 1)) == 31 * 32, at + "south-west corner isn't cell 992");
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(half - 1, 0, half - 1)) == 1023, at + "south-east corner isn't cell 1023");
				// the first block of column 1 and the last of column 30
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(-half + rs[1], 0, 0)) % 32 == 1, at + "column 1 starts in the wrong place");
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(-half + rs[1] - 1, 0, 0)) % 32 == 0, at + "column 0 ends in the wrong place");
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(half - rs[1] - 1, 0, 0)) % 32 == 30, at + "column 30 ends in the wrong place");
				// column -1 and 32, row -1 and 32
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(-half - 1, 0, 0)) == -1, at + "west of the grid is a cell");
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(half, 0, 0)) == -1, at + "east of the grid is a cell");
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(0, 0, -half - 1)) == -1, at + "north of the grid is a cell");
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(0, 0, half)) == -1, at + "south of the grid is a cell");
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(-half - rs[1] * 31, 0, 0)) == -1, at + "far west wraps onto the grid");
				// the hall itself is in the middle four cells, and each cell's centre is back in that cell
				int mid = CityPlan.cellAt(hall, hall);
				helper.assertTrue(mid == 16 + 16 * 32, at + "the hall is in cell " + mid);
				for (int cell : new int[] {0, 31, 992, 1023, 528}) {
					helper.assertTrue(CityPlan.cellAt(hall, CityPlan.cellCentre(hall, cell)) == cell, at + "cell " + cell + "'s centre is in cell "
						+ CityPlan.cellAt(hall, CityPlan.cellCentre(hall, cell)));
				}
				// zoneAt: a zone of the four corners finds them and nothing just outside
				CityPlan plan = CityPlan.EMPTY.addZone("farms", "Corners", "").paint(0, cells(0, 31, 992, 1023));
				helper.assertTrue(plan.zoneAt(hall, hall.offset(half - 1, 0, half - 1)).isPresent(), at + "zoneAt misses cell 1023");
				helper.assertTrue(plan.zoneAt(hall, hall.offset(half, 0, half)).isEmpty(), at + "zoneAt past the corner finds " + plan.zoneAt(hall, hall.offset(half, 0, half)));
				helper.assertTrue(plan.zoneAt(hall, hall.offset(-half - 1, 0, -half)).isEmpty(), at + "zoneAt west of cell 0 finds a zone");
			}
		}
		helper.succeed();
	}

	/** The paint packet: the four corner cells are painted; cells past 1023 (as a modified client might send) are ignored, not wrapped. */
	//$ gametest_ticks_batch AREA '40' '"qaCityPlanCornerCells"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaCityPlanCornerCells")
	public void cornerCellsArePaintedAndCellsPastTheGridIgnored(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		otherHalls(helper, HALL); // a corner cell is 88 blocks from its hall: any hall another batch left within 176 could be nearer
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "Edges", ""))), "no zone");
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, cells(0, 31, 992, 1023)))), "the corner cells were refused");
			helper.assertTrue(entity.plan().zones().get(0).cells().equals(cells(0, 31, 992, 1023)), "painted: " + entity.plan().zones().get(0).cells());
			CityPlan before = entity.plan();
			int steps = entity.planUndoSteps();
			helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, cells(1024, 1055, 4096, 65535)))),
				"cells past the grid changed the plan");
			helper.assertTrue(entity.plan().equals(before) && entity.planUndoSteps() == steps, "cells past the grid: " + entity.plan().zones().get(0).cells());
			helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, cells(1, 1024)))), "a stroke with one cell past the grid was refused whole");
			helper.assertTrue(entity.plan().zones().get(0).cells().equals(cells(0, 1, 31, 992, 1023)), "after cells 1 and 1024: " + entity.plan().zones().get(0).cells());
			// the saved plan keeps only cells on the grid
			VillageHallBlockEntity copy = reload(level, entity, entity.saveWithFullMetadata(level.registryAccess()));
			helper.assertTrue(copy.plan().zones().get(0).cells().length() <= 1024, "the saved zone reaches cell " + copy.plan().zones().get(0).cells().length());
			helper.succeed();
		});
	}

	/** Radius 128 (cells of 8): a cell nearer a second hall 24 blocks away is refused, the cell by this hall painted. */
	//$ gametest_ticks_batch HUGE '60' '"qaCityPlanNeighbour128"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaCityPlanNeighbour128")
	public void atRadius128ACellNearerTheNeighbourIsRefused(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 128;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, new BlockPos(4, 2, 3));
		hall(helper, new BlockPos(4, 2, 27));
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "no zone");
		int mine = CityPlan.cellAt(hall, hall.offset(1, 0, 1)); // centre 4 blocks from this hall
		int theirs = CityPlan.cellAt(hall, hall.offset(1, 0, 17)); // centre about 20 from this hall, 4-5 from the other
		int north = CityPlan.cellAt(hall, hall.offset(1, 0, -12)); // away from the other hall
		helper.assertTrue(CityPlan.cellCentre(hall, theirs).distSqr(hall.offset(0, 0, 24)) < CityPlan.cellCentre(hall, theirs).distSqr(hall),
			"setup: the cell isn't nearer the other hall");
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, cells(mine, theirs, north)))), "nothing painted");
			CityPlan.Zone zone = entity.plan().zones().get(0);
			helper.assertTrue(zone.has(mine) && zone.has(north), "this village's cells weren't painted: " + zone.cells());
			helper.assertFalse(zone.has(theirs), "a cell nearer the other hall was painted at radius 128");
			// the other hall, broken, no longer takes the cell
			helper.setBlock(new BlockPos(4, 2, 27), Blocks.AIR);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, cells(theirs)))), "the cell is still refused once the other hall is gone");
				helper.succeed();
			});
		});
	}

	// --- 27.2: who may change it, and malformed packets -------------------------------------------------------------

	/** Bad indexes, a hall that isn't there and unknown kinds change nothing, push no undo step and don't claim a hall nobody owns. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPlanMalformed"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPlanMalformed")
	public void malformedPacketsChangeNothingAndClaimNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int near = CityPlan.cellAt(hall, hall.east(2));
		// on a hall nobody owns, a refused packet doesn't make it the sender's
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "no_such_kind", "?", ""))), "an unknown kind went on");
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, cells(near)))), "painting a zone that doesn't exist went through");
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.erase(hall, cells(near)))), "erasing an empty plan changed it");
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.undo(hall))), "undo on a new plan did something");
		helper.assertTrue(entity.owner() == null, "a refused packet claimed the hall for " + entity.ownerName());
		helper.assertTrue(entity.plan().equals(CityPlan.EMPTY) && entity.planUndoSteps() == 0, "the plan: " + entity.plan() + ", undo " + entity.planUndoSteps());

		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "a good zone was refused");
		helper.assertTrue(player.getUUID().equals(entity.owner()), "the first painter didn't get the hall");
		CityPlan before = entity.plan();
		int steps = entity.planUndoSteps();
		List<CityPlans.Edit> bad = List.of(
			CityPlans.Edit.paint(hall, -1, cells(near)),
			CityPlans.Edit.paint(hall, 1, cells(near)),
			CityPlans.Edit.paint(hall, Integer.MAX_VALUE, cells(near)),
			CityPlans.Edit.paint(hall, 0, new BitSet()),
			CityPlans.Edit.editZone(hall, -1, "homes", "X", "", false),
			CityPlans.Edit.editZone(hall, 5, "homes", "X", "", false),
			CityPlans.Edit.editZone(hall, 0, "no_such_kind", "X", "", false),
			CityPlans.Edit.removeZone(hall, -1),
			CityPlans.Edit.removeZone(hall, 1),
			CityPlans.Edit.removeRoad(hall, -1),
			CityPlans.Edit.removeRoad(hall, 0),
			CityPlans.Edit.addRoad(hall, List.of(), 3, ""),
			CityPlans.Edit.addRoad(hall, List.of(p(0, 0), p(5, 5)), 0, ""),
			CityPlans.Edit.addRoad(hall, List.of(p(0, 0), p(5, 5)), -3, ""),
			CityPlans.Edit.addRoad(hall, List.of(p(0, 0), p(5, 5)), 7, ""),
			CityPlans.Edit.wall(hall, List.of(p(1, 1)), true),
			CityPlans.Edit.erase(hall, cells(1024, 2000)),
			CityPlans.Edit.mode(hall, CityPlan.Mode.ASK),
			// the right edit for a block that isn't a hall
			CityPlans.Edit.addZone(hall.east(), "homes", "Homes", ""),
			CityPlans.Edit.addZone(hall.offset(100000, 0, 0), "homes", "Homes", ""));
		for (CityPlans.Edit edit : bad) {
			boolean changed;
			try {
				changed = CityPlans.apply(player, sent(level, edit));
			} catch (RuntimeException e) {
				throw new GameTestAssertException(edit.op() + " zone " + edit.zone() + " threw " + e);
			}
			helper.assertFalse(changed, "a malformed " + edit.op() + " (zone " + edit.zone() + ", width " + edit.width() + ", " + edit.points().size() + " points) went through");
		}
		helper.assertTrue(entity.plan().equals(before), "a malformed packet changed the plan: " + entity.plan());
		helper.assertTrue(entity.planUndoSteps() == steps, "refused packets pushed undo steps: " + steps + " -> " + entity.planUndoSteps());
		helper.succeed();
	}

	/** The owner's friend and an operator may change the plan; a stranger may not, even in an unprotected village; the owner's own edits always go. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPlanWhoMay"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPlanWhoMay")
	public void friendsAndOperatorsMayChangeThePlanStrangersMayNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer friend = helper.makeMockServerPlayerInLevel();
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		ServerPlayer op = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlans.apply(owner, sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "the owner was refused");
		helper.assertFalse(entity.isProtected(), "setup: the village is protected");
		CityPlan before = entity.plan();
		int near = CityPlan.cellAt(hall, hall.east(2));
		helper.assertFalse(CityPlans.apply(stranger, sent(level, CityPlans.Edit.paint(hall, 0, cells(near)))), "a stranger painted an unprotected village's plan");
		helper.assertFalse(CityPlans.apply(stranger, sent(level, CityPlans.Edit.mode(hall, CityPlan.Mode.RUN))), "a stranger set the Steward's mode");
		helper.assertFalse(CityPlans.apply(stranger, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(0, 0), p(9, 0)), 3, ""))), "a stranger drew a road");
		helper.assertTrue(entity.plan().equals(before) && owner.getUUID().equals(entity.owner()), "a stranger changed the plan or took the hall");

		Friends.get(level.getServer()).add(owner.getUUID(), friend.getUUID(), "pal");
		Leftovers.after(helper, () -> Friends.get(level.getServer()).remove(owner.getUUID(), friend.getUUID()));
		helper.assertTrue(CityPlans.apply(friend, sent(level, CityPlans.Edit.paint(hall, 0, cells(near)))), "the owner's friend was refused");
		helper.assertTrue(owner.getUUID().equals(entity.owner()), "the friend's paint took the hall");
		var profile = op.getGameProfile();
		// the game test server makes /op operators of level 0, so the entry is given level 4 as a dedicated server's would be
		level.getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(profile, 4, false));
		Leftovers.after(helper, () -> level.getServer().getPlayerList().getOps().remove(profile));
		helper.assertTrue(op.hasPermissions(2), "setup: the operator has no permissions");
		helper.assertTrue(CityPlans.apply(op, sent(level, CityPlans.Edit.mode(hall, CityPlan.Mode.RUN))), "an operator was refused");
		helper.assertTrue(entity.plan().mode() == CityPlan.Mode.RUN && entity.plan().zones().get(0).has(near), "the plan: " + entity.plan());
		helper.succeed();
	}

	// --- 27.2: the item ------------------------------------------------------------------------------------------------

	/** Right-clicking the hall with a City Plan binds it (the player's entry point); unbound or with its hall gone, using it fails and opens nothing. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPlanItemUse"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPlanItemUse")
	public void anUnboundPlanOrOneWhoseHallIsGoneDoesNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		player.setItemInHand(InteractionHand.MAIN_HAND, plan);
		InteractionResult unbound = plan.use(level, player, InteractionHand.MAIN_HAND).getResult();
		helper.assertTrue(unbound == InteractionResult.FAIL, "an unbound plan used in the air: " + unbound);
		List<Component> tip = new ArrayList<>();
		plan.getItem().appendHoverText(plan, net.minecraft.world.item.Item.TooltipContext.of(level), tip, net.minecraft.world.item.TooltipFlag.NORMAL);
		helper.assertTrue(tip.size() == 1 && english(tip.get(0)).equals("Right-click a Village Hall to plan that village"), "the unbound tooltip: " + tip);

		VillageHallBlockEntity entity = hall(helper, HALL);
		helper.useBlock(HALL, player);
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		var bound = held.get(ModComponents.CITY_PLAN_HALL);
		helper.assertTrue(bound != null && bound.hall().pos().equals(entity.getBlockPos()) && bound.hall().dimension().equals(level.dimension()),
			"right-clicking the hall didn't bind the plan: " + bound);
		helper.assertTrue(held.is(ModItems.CITY_PLAN) && held.getCount() == 1, "binding used the plan up: " + held);
		helper.assertTrue(held.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "a bound plan didn't open");
		tip.clear();
		held.getItem().appendHoverText(held, net.minecraft.world.item.Item.TooltipContext.of(level), tip, net.minecraft.world.item.TooltipFlag.NORMAL);
		String village = VillageHalls.name(level, entity.getBlockPos()).getString();
		helper.assertTrue(english(tip.get(0)).equals("The plan of " + village), "the bound tooltip: " + english(tip.get(0)));

		helper.setBlock(HALL, Blocks.AIR);
		InteractionResult gone = held.use(level, player, InteractionHand.MAIN_HAND).getResult();
		helper.assertTrue(gone == InteractionResult.FAIL, "a plan whose hall was broken: " + gone);
		helper.succeed();
	}

	// --- 27.2: saves and the hall item ---------------------------------------------------------------------------------

	/** An old hall (no plan tag), a broken plan tag and an old zone (no style, renew or cells) all load without losing the hall's other data. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPlanOldSaves"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPlanOldSaves")
	public void oldAndBrokenPlanTagsLoadSafely(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes", "cherry").paint(0, cells(5)));
		CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
		helper.assertTrue(tag.contains("plan"), "a hall with a plan saved no plan tag: " + tag.getAllKeys());

		CompoundTag old = tag.copy();
		old.remove("plan");
		VillageHallBlockEntity a = reload(level, entity, old);
		helper.assertTrue(a.plan().equals(CityPlan.EMPTY) && owner.getUUID().equals(a.owner()), "a 1.0 hall: " + a.plan() + ", owner " + a.owner());
		helper.assertFalse(a.saveWithFullMetadata(level.registryAccess()).contains("plan"), "an empty plan is written to the save");

		for (Tag broken : new Tag[] {StringTag.valueOf("garbage"), new ListTag(), brokenZones()}) {
			CompoundTag bad = tag.copy();
			bad.put("plan", broken);
			VillageHallBlockEntity b;
			try {
				b = reload(level, entity, bad);
			} catch (RuntimeException e) {
				throw new GameTestAssertException("a hall with plan " + broken + " threw " + e);
			}
			helper.assertTrue(b != null && owner.getUUID().equals(b.owner()), "a hall with plan " + broken + " lost its owner");
			helper.assertTrue(b.plan().equals(CityPlan.EMPTY), "a broken plan " + broken + " loaded as " + b.plan());
		}

		CompoundTag zone = new CompoundTag();
		zone.putString("kind", "gardens");
		zone.putString("name", "Old park");
		ListTag zones = new ListTag();
		zones.add(zone);
		CompoundTag planTag = new CompoundTag();
		planTag.put("zones", zones);
		CompoundTag early = tag.copy();
		early.put("plan", planTag);
		VillageHallBlockEntity c = reload(level, entity, early);
		helper.assertTrue(c.plan().zones().size() == 1, "an early zone didn't load: " + c.plan());
		CityPlan.Zone z = c.plan().zones().get(0);
		helper.assertTrue(z.name().equals("Old park") && z.style().isEmpty() && !z.renew() && z.cells().isEmpty() && c.plan().mode() == CityPlan.Mode.ASK
			&& c.plan().roads().isEmpty() && c.plan().wall().isEmpty(), "an early zone's defaults: " + z + ", " + c.plan());
		helper.succeed();
	}

	private static CompoundTag brokenZones() {
		CompoundTag plan = new CompoundTag();
		plan.putString("zones", "not a list");
		plan.putInt("mode", 7);
		return plan;
	}

	/** Every field of a plan survives the hall item being saved in a chest (the item codec), and a plan of only a mode is carried too. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPlanItemSaved"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPlanItemSaved")
	public void theHallItemsPlanSurvivesTheItemBeingSaved(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		CityPlan plan = CityPlan.EMPTY.addZone("market", "Square", "stonework").addZone("keep_clear", "Pond", "");
		plan = plan.paint(0, cells(0, 1, 1023)).paint(1, cells(600)).editZone(0, "market", "Square", "stonework", true);
		plan = plan.addRoad(new CityPlan.Road(List.of(p(-60, 0), p(60, 0)), CityPlan.Road.AVENUE, "cherry", true));
		plan = plan.withWall(new CityPlan.Wall(List.of(p(-63, -64), p(63, -64), p(63, 63)), false)).withMode(CityPlan.Mode.REST);
		entity.setPlan(plan);
		ItemStack item = Block.getDrops(level.getBlockState(hall), level, hall, entity).stream()
			.filter(s -> s.is(ModBlocks.VILLAGE_HALL.asItem())).findFirst().orElse(ItemStack.EMPTY);
		helper.assertTrue(plan.equals(item.get(ModComponents.CITY_PLAN)), "the dropped hall item: " + item.getComponents());
		var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag saved = ItemStack.CODEC.encodeStart(ops, item).getOrThrow();
		ItemStack back = ItemStack.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(plan.equals(back.get(ModComponents.CITY_PLAN)), "the item saved and loaded: " + back.get(ModComponents.CITY_PLAN));
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
		ItemStack.STREAM_CODEC.encode(buf, item);
		helper.assertTrue(plan.equals(ItemStack.STREAM_CODEC.decode(buf).get(ModComponents.CITY_PLAN)), "the item sent to a client lost the plan");

		// only the Steward's mode set: still carried
		entity.setPlan(CityPlan.EMPTY.withMode(CityPlan.Mode.RUN));
		ItemStack modeOnly = Block.getDrops(level.getBlockState(hall), level, hall, entity).stream()
			.filter(s -> s.is(ModBlocks.VILLAGE_HALL.asItem())).findFirst().orElse(ItemStack.EMPTY);
		helper.assertTrue(CityPlan.EMPTY.withMode(CityPlan.Mode.RUN).equals(modeOnly.get(ModComponents.CITY_PLAN)), "a plan of only a mode: " + modeOnly.getComponents());
		// an empty plan: a plain hall item (it stacks with others), and putting one down keeps an empty plan
		entity.setPlan(CityPlan.EMPTY);
		ItemStack plain = Block.getDrops(level.getBlockState(hall), level, hall, entity).stream()
			.filter(s -> s.is(ModBlocks.VILLAGE_HALL.asItem())).findFirst().orElse(ItemStack.EMPTY);
		helper.assertTrue(plain.get(ModComponents.CITY_PLAN) == null, "an empty plan was put on the item");
		helper.assertTrue(ItemStack.isSameItemSameComponents(plain, new ItemStack(ModBlocks.VILLAGE_HALL.asItem())), "an unplanned hall item doesn't stack with a new one");
		helper.succeed();
	}

	/** Broken, the hall carries its plan; put down 40 blocks away, its roads, wall line and zones sit round the new spot, not the old one. */
	//$ gametest_ticks_batch HUGE '40' '"qaCityPlanMoved"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaCityPlanMoved")
	public void aMovedHallsRoadsAndWallLineMoveWithIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos first = new BlockPos(3, 2, 3), second = new BlockPos(26, 2, 25);
		VillageHallBlockEntity entity = hall(helper, first);
		BlockPos a = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(a, "workshops", "Smiths", "")));
		int cell = CityPlan.cellAt(a, a.offset(6, 0, 2));
		CityPlans.apply(player, sent(level, CityPlans.Edit.paint(a, 0, cells(cell))));
		CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(a, List.of(p(-5, 1), p(7, 1)), CityPlan.Road.LANE, "")));
		CityPlans.apply(player, sent(level, CityPlans.Edit.wall(a, List.of(p(-9, -9), p(9, -9), p(9, 9)), true)));
		CityPlan plan = entity.plan();
		helper.assertTrue(plan.zones().size() == 1 && plan.roads().size() == 1 && plan.wall().isPresent(), "setup: " + plan);
		ItemStack item = Block.getDrops(level.getBlockState(a), level, a, entity).stream()
			.filter(s -> s.is(ModBlocks.VILLAGE_HALL.asItem())).findFirst().orElse(ItemStack.EMPTY);
		helper.setBlock(first, Blocks.AIR);
		VillageHallBlockEntity placed = hall(helper, second);
		placed.applyComponentsFromItemStack(item);
		BlockPos b = placed.getBlockPos();
		helper.assertTrue(placed.plan().equals(plan), "the plan put down: " + placed.plan());
		helper.assertTrue(placed.plan().zoneAt(b, b.offset(6, 0, 2)).map(CityPlan.Zone::name).equals(Optional.of("Smiths")), "the zone isn't round the new spot");
		helper.assertTrue(placed.plan().zoneAt(b, a.offset(6, 0, 2)).isEmpty(), "the zone is still at the old spot");
		helper.assertTrue(placed.plan().roads().get(0).points().get(0).equals(p(-5, 1)), "the road's points aren't offsets: " + placed.plan().roads().get(0).points());
		// and its plan survives the moved hall being saved and loaded
		helper.assertTrue(reload(level, placed, placed.saveWithFullMetadata(level.registryAccess())).plan().equals(plan), "the moved hall's plan wasn't saved");
		helper.runAfterDelay(5, () -> { // halls are found by their POI, registered a tick after the block
			helper.assertTrue(CityPlan.zoneAt(level, b.offset(6, 0, 2)).map(CityPlan.Zone::name).equals(Optional.of("Smiths")),
				"zoneAt(level) by the new hall: " + CityPlan.zoneAt(level, b.offset(6, 0, 2)));
			helper.succeed();
		});
	}

	// --- 27.3: undo -------------------------------------------------------------------------------------------------

	/** Undo after a refused stroke undoes the last real change; ten steps then nothing; a new change after undoing works and is undoable. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPlanUndo"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPlanUndo")
	public void undoSkipsRefusedStrokesAndStopsAtTen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<CityPlan> history = new ArrayList<>();
		history.add(entity.plan());
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "no zone");
		history.add(entity.plan());
		for (int i = 0; i < 11; i++) {
			int c = CityPlan.cellAt(hall, hall.offset(-10 + (i % 6) * 4, 0, -6 + (i / 6) * 4));
			helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, cells(c)))), "stroke " + i);
			history.add(entity.plan());
			// a refused stroke between each pair: it must not cost an undo step
			CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 7, cells(c))));
		}
		helper.assertTrue(entity.planUndoSteps() == 10, "undo steps: " + entity.planUndoSteps());
		for (int i = 1; i <= 10; i++) {
			helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.undo(hall))), "undo " + i + " refused");
			CityPlan want = history.get(history.size() - 1 - i);
			helper.assertTrue(entity.plan().equals(want), "after undo " + i + ": " + entity.plan().zones().get(0).cells() + " not " + want.zones().get(0).cells());
		}
		CityPlan floor = entity.plan();
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.undo(hall))), "an 11th undo did something");
		helper.assertTrue(entity.plan().equals(floor) && floor.zones().size() == 1 && floor.zones().get(0).cells().cardinality() == 1,
			"the 11th undo changed the plan: " + entity.plan());
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.mode(hall, CityPlan.Mode.REST))), "a change after undoing was refused");
		helper.assertTrue(entity.planUndoSteps() == 1, "after undoing everything and one change: " + entity.planUndoSteps());
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.undo(hall))) && entity.plan().equals(floor), "the change after undoing can't be undone");
		helper.succeed();
	}

	// --- 27.4: roads and the wall line ----------------------------------------------------------------------------------

	/** Points exactly on the grid's edge are accepted, one block past refused; a wall line of 64 points goes on; no points takes it off; heights are dropped; lanes and roads to other villages don't count towards 24. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPlanLines"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPlanLines")
	public void roadAndWallLimitsFromBothSides(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int h = CityPlan.half();
		helper.assertTrue(h == 64, "setup: the grid reaches " + h);
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(-h, -h), p(h - 1, h - 1)), 1, ""))), "a road corner to corner was refused");
		for (BlockPos off : List.of(p(h, 0), p(-h - 1, 0), p(0, h), p(0, -h - 1), p(Integer.MIN_VALUE / 2, 0))) {
			helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(0, 0), off), 3, ""))), "a road to " + off.toShortString() + " went on");
			helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.wall(hall, List.of(p(0, 0), p(4, 4), off), true))), "a wall line to " + off.toShortString() + " went on");
		}
		// the road's style: 32 letters go, 33 don't
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(0, 2), p(9, 2)), 5, "a".repeat(CityPlans.MAX_NAME)))), "a 32-letter style was refused");
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(0, 4), p(9, 4)), 5, "a".repeat(CityPlans.MAX_NAME + 1)))), "a 33-letter style went on");
		// heights are dropped: the plan keeps where a line runs
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(new BlockPos(1, 300, 6), new BlockPos(8, -64, 6)), 3, ""))), "a road with heights was refused");
		CityPlan.Road last = entity.plan().roads().get(entity.plan().roads().size() - 1);
		helper.assertTrue(last.points().equals(List.of(p(1, 6), p(8, 6))), "the road's points: " + last.points());

		List<BlockPos> sixtyFour = new ArrayList<>();
		for (int i = 0; i < CityPlan.MAX_ROAD_POINTS; i++) {
			sixtyFour.add(p(-32 + i, (i % 2) * 2));
		}
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.wall(hall, sixtyFour, false))), "a wall line of 64 points was refused");
		helper.assertTrue(entity.plan().wall().map(w -> w.points().size() == 64 && !w.closed()).orElse(false), "the wall line: " + entity.plan().wall());
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.wall(hall, List.of(p(-9, -9), p(9, -9)), true))), "redrawing the wall line was refused");
		helper.assertTrue(entity.plan().wall().map(w -> w.points().size() == 2 && w.closed()).orElse(false), "there is still one wall line, redrawn: " + entity.plan().wall());
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.wall(hall, List.of(), true))), "taking the wall line off was refused");
		helper.assertTrue(entity.plan().wall().isEmpty(), "the wall line wasn't taken off");
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.wall(hall, List.of(), true))), "taking off a wall line that isn't there changed the plan");

		// fill up to 24 drawn roads, with a lane from a door and a road to another village besides: those don't count
		CityPlan plan = entity.plan();
		plan = plan.addRoad(new CityPlan.Road(List.of(p(0, 0), p(0, 9)), 1, "", true, List.of(), false, List.of(), true));
		plan = plan.addRoad(new CityPlan.Road(List.of(p(0, 0), p(30, 30)), 3, "", true, List.of(), false, List.of(), false, 0, 0, Optional.of(new BlockPos(300, 0, 300))));
		entity.setPlan(plan);
		while (entity.plan().drawnRoads() < CityPlan.MAX_ROADS) {
			int n = entity.plan().drawnRoads();
			helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(-50, -50 + n * 3), p(50, -50 + n * 3)), 1, ""))),
				"road " + (n + 1) + " was refused with a lane and a caravan road on the plan");
		}
		helper.assertTrue(entity.plan().roads().size() == CityPlan.MAX_ROADS + 2, "roads: " + entity.plan().roads().size());
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(1, 1), p(2, 2)), 1, ""))), "a 25th road went on");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.removeRoad(hall, 2))), "taking a road off was refused");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(1, 1), p(2, 2)), 1, ""))), "no room for a road after taking one off");
		helper.succeed();
	}

	/** Roads and the wall line with every field survive the hall being saved and loaded; a Wall from before 27.18 loads as a player's, closed by default. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPlanLinesSaved"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPlanLinesSaved")
	public void roadsAndTheWallLineWithEveryFieldSurviveAReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		List<BlockPos> long64 = new ArrayList<>();
		for (int i = 0; i < 64; i++) {
			long64.add(p(-64 + i * 2, -64 + i));
		}
		CityPlan plan = CityPlan.EMPTY
			.addRoad(new CityPlan.Road(long64, CityPlan.Road.LANE, "", true))
			.addRoad(new CityPlan.Road(List.of(p(-3, 0), p(3, 0)), CityPlan.Road.AVENUE, "cherry", false))
			.withWall(new CityPlan.Wall(List.of(p(-64, -64), p(63, -64), p(63, 63), p(-64, 63)), false, "stone", true, true));
		helper.assertTrue(plan != null && plan.roads().size() == 2, "setup: " + plan);
		entity.setPlan(plan);
		VillageHallBlockEntity copy = reload(level, entity, entity.saveWithFullMetadata(level.registryAccess()));
		helper.assertTrue(copy.plan().equals(plan), "after a reload: " + copy.plan());
		helper.assertTrue(copy.plan().roads().get(0).points().size() == 64 && !copy.plan().roads().get(1).approved()
			&& copy.plan().wall().map(w -> !w.closed() && w.approved() && w.own() && w.kit().equals("stone")).orElse(false), "fields lost: " + copy.plan());
		CompoundTag wall = new CompoundTag();
		ListTag points = new ListTag();
		points.add(BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, p(1, 1)).getOrThrow());
		points.add(BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, p(5, 5)).getOrThrow());
		wall.put("points", points);
		CityPlan.Wall old = CityPlan.Wall.CODEC.parse(NbtOps.INSTANCE, wall).getOrThrow();
		helper.assertTrue(old.closed() && !old.approved() && !old.own() && old.kit().isEmpty(), "an early wall line: " + old);
		helper.succeed();
	}

	/** The hall's map at radius 128 (cells of 8) has each zone's tint on its own pixels; a zone in the grid's corner, off the 128-block map, draws nothing and doesn't wrap; Keep Clear is red on the map. */
	//$ gametest_ticks_batch AREA '20' '"qaCityPlanMapTints"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPlanMapTints")
	public void theMapTintsAreOnTheRightPixelsAtRadius128AndAtTheEdges(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		int keep = CityZones.get("keep_clear").orElseThrow().mapTint();
		int homes = CityZones.get("homes").orElseThrow().mapTint();

		// radius 64: a Keep Clear zone in the map's south-east corner cell (offsets 60..63) and Homes in the north-west (-64..-61)
		VillageHalls.RADIUS = 64;
		CityPlan plan = CityPlan.EMPTY.addZone("keep_clear", "Edge", "").addZone("homes", "Corner", "");
		plan = plan.paint(0, cells(1023)).paint(1, cells(0));
		entity.setPlan(plan);
		byte[] land = VillageMaps.colors(level, hall, 64, 1);
		byte[] map = colors(level, VillageMaps.map(level, hall));
		helper.assertTrue(map[px(61, 61)] == VillageMaps.tinted(land[px(61, 61)], keep), "inside the corner Keep Clear: " + map[px(61, 61)]);
		helper.assertTrue(map[px(63, 63)] == VillageMaps.nearest(keep), "the map's last pixel, Keep Clear's edge: " + map[px(63, 63)]);
		helper.assertTrue(map[px(59, 61)] == land[px(59, 61)], "the pixel west of the corner cell changed");
		helper.assertTrue(map[px(-64, -64)] == VillageMaps.nearest(homes) && map[px(-62, -62)] == VillageMaps.tinted(land[px(-62, -62)], homes),
			"the north-west corner cell: " + map[px(-64, -64)] + " / " + map[px(-62, -62)]);
		helper.assertTrue(map[px(-60, -62)] == land[px(-60, -62)], "the pixel east of the north-west cell changed");
		int redness = net.minecraft.world.level.material.MapColor.getColorFromPackedId(VillageMaps.nearest(keep) & 0xFF);
		helper.assertTrue((redness & 0xFF) > ((redness >> 8) & 0xFF) + 40, "Keep Clear's edge on the map isn't red: " + Integer.toHexString(redness));

		// radius 128: cells of 8; a zone at offsets 8..15 is one cell; a zone in cell 1023 (offsets 120..127) is off the map
		VillageHalls.RADIUS = 128;
		plan = CityPlan.EMPTY.addZone("homes", "Homes", "").addZone("keep_clear", "Far", "");
		int c = CityPlan.cellAt(hall, hall.offset(8, 0, 8));
		helper.assertTrue(c == CityPlan.cellAt(hall, hall.offset(15, 0, 15)) && c != CityPlan.cellAt(hall, hall.offset(16, 0, 8)), "setup: an 8-block cell");
		plan = plan.paint(0, cells(c)).paint(1, cells(1023, 0));
		entity.setPlan(plan);
		map = colors(level, VillageMaps.map(level, hall));
		helper.assertTrue(map[px(11, 11)] == VillageMaps.tinted(land[px(11, 11)], homes) && map[px(12, 12)] == VillageMaps.tinted(land[px(12, 12)], homes),
			"inside the 8-block Homes cell at radius 128: " + map[px(11, 11)]);
		helper.assertTrue(map[px(8, 11)] == VillageMaps.nearest(homes) && map[px(15, 11)] == VillageMaps.nearest(homes), "the 8-block cell's edges");
		helper.assertTrue(map[px(16, 11)] == land[px(16, 11)] && map[px(7, 11)] == land[px(7, 11)], "a pixel beside the 8-block cell changed");
		for (int dz = -64; dz < 64; dz++) {
			for (int dx = -64; dx < 64; dx++) {
				boolean inCell = dx >= 8 && dx <= 15 && dz >= 8 && dz <= 15;
				helper.assertTrue(inCell || map[px(dx, dz)] == land[px(dx, dz)],
					"at radius 128 the pixel " + dx + ", " + dz + " outside the one Homes cell changed (a cell off the map drawn, or wrapped)");
			}
		}
		helper.succeed();
	}

	private static int px(int dx, int dz) {
		return (dx + 64) + (dz + 64) * 128;
	}

	private static byte[] colors(ServerLevel level, ItemStack map) {
		MapItemSavedData data = level.getMapData(map.get(DataComponents.MAP_ID));
		return data.colors;
	}

	// --- the sentences -------------------------------------------------------------------------------------------------

	/** Every sentence these items show a player, rendered with its real arguments. */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '20' '"qaCityPlanText"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "qaCityPlanText")
	public void theCityPlansSentencesReadRight(GameTestHelper helper) {
		Component v = Component.literal("Oakvale");
		Map<Component, String> want = new java.util.LinkedHashMap<>();
		want.put(Component.translatable("message.aliveworkplace.city_plan.bound", v), "The plan is now Oakvale's");
		want.put(Component.translatable("message.aliveworkplace.city_plan.unbound"), "Right-click a Village Hall with the plan first");
		want.put(Component.translatable("message.aliveworkplace.city_plan.in_zone", "Smithy Row", Component.translatable("zone.aliveworkplace.workshops"), v),
			"You're in Smithy Row (Workshops) on Oakvale's plan");
		want.put(Component.translatable("message.aliveworkplace.city_plan.no_zone", v, 3), "No zone here on Oakvale's plan (3 zones)");
		want.put(Component.translatable("message.aliveworkplace.city_plan.denied", v, "Jesse"), "Only Jesse and their friends may change Oakvale's plan");
		want.put(Component.translatable("message.aliveworkplace.city_plan.other_village", 3), "3 cells are nearer another village: left out");
		want.put(Component.translatable("message.aliveworkplace.city_plan.too_many_roads", CityPlan.MAX_ROADS), "The plan has 24 roads already: take one off first");
		want.put(Component.translatable("message.aliveworkplace.city_plan.too_many_points", CityPlan.MAX_ROAD_POINTS), "A line can have at most 64 points");
		want.put(Component.translatable("message.aliveworkplace.city_plan.bad_line"), "That line can't go on the plan: it needs two points on the map");
		want.put(Component.translatable("tooltip.aliveworkplace.city_plan.bound", v), "The plan of Oakvale");
		want.put(Component.translatable("screen.aliveworkplace.city_plan.title", v), "City Plan of Oakvale");
		want.put(Component.translatable("screen.aliveworkplace.city_plan.zones", 16, CityPlan.MAX_ZONES), "Zones 16/16");
		want.put(Component.translatable("screen.aliveworkplace.city_plan.road.hint", 24, CityPlan.MAX_ROADS), "Roads 24/24: click points, double-click to end");
		want.put(Component.translatable("item.aliveworkplace.city_plan"), "City Plan");
		for (var e : want.entrySet()) {
			String got = english(e.getKey());
			helper.assertTrue(got.equals(e.getValue()), "\"" + got + "\", expected \"" + e.getValue() + "\"");
		}
		for (String key : List.of("undo", "legend", "brush", "eraser", "area", "road", "wall", "wall.closed", "wall.open", "renew.on", "renew.off",
			"as_drawn", "delete", "new", "name", "view_only", "start", "hint", "no_zones", "wall.hint")) {
			String got = english(Component.translatable("screen.aliveworkplace.city_plan." + key));
			helper.assertTrue(!got.isBlank() && !got.contains("%"), "screen." + key + ": \"" + got + "\"");
		}
		helper.succeed();
	}
}
