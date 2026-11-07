package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.RepairAsks;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b-1107-0338) for 37.7's open question (furniture-and-storage.md, Decisions: "not tested: whether a repair of
 * a finished Grand building then swaps its floors"). A small Grand building (four plank floor blocks and one birch wall
 * panel, which Grand makes dark oak planks / polished andesite, or with {@code furnishedBuilds} on Rechiseled's dark oak
 * flooring / andesite squares) stands finished; floor and panel blocks are broken and its owner says [Yes] to the
 * Builder's repair question (the chat button's command). Whatever the repair puts in: every hole is filled, no undamaged
 * block is torn down or swapped, no block is lost or made from nothing, and the building isn't asked about again
 * straight after. With the switch off the repair is today's Grand (vanilla); built and repaired with it on, Rechiseled.
 * What the switched-on repair of an older vanilla Grand building puts in is printed ("QA1107") for the wiki.
 */
public class QaGrandRepair1107CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos ORIGIN = CompatGameTests.ORIGIN;
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	/** The floor (x 0 to 3) and the panel (x 4), in a row from the origin; x 1 and 3 and the panel are broken. */
	private static final int LENGTH = 5;
	private static final int[] HOLES = {1, 3, 4};
	private static final String FLOOR = "minecraft:dark_oak_planks";
	private static final String FLOOR_R = "rechiseled:dark_oak_planks_flooring";
	private static final String PANEL = "minecraft:polished_andesite";
	private static final String PANEL_R = "rechiseled:andesite_squares";

	record Stage(ServerLevel level, ServerPlayer owner, Villager builder, ResourceLocation structure, BlueprintData.Placement placement,
				 Map<BlockPos, BlockState> standing, Container chest) {
	}

	/**
	 * The Grand building as finished with the switch at {@code builtOn}, its owner (a player), a Builder they hired at a
	 * bench with a chest of 2 dark oak planks, 2 Rechiseled floorings, 1 polished andesite and 1 andesite squares (enough
	 * for the holes in either look). Restores the switches after the test.
	 */
	private static Stage stage(GameTestHelper helper, boolean builtOn) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		boolean furnished = Furnishings.ENABLED;
		boolean upkeep = Upkeep.ENABLED;
		boolean ask = Upkeep.ASK;
		PartnerShowsCompatTests.after(helper, () -> {
			Furnishings.ENABLED = furnished;
			Upkeep.ENABLED = upkeep;
			Upkeep.ASK = ask;
		});
		Upkeep.ENABLED = true;
		Upkeep.ASK = true;
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Map<BlockPos, BlockState> design = new HashMap<>();
		for (int x = 0; x < LENGTH; x++) {
			design.put(new BlockPos(x, 0, 0), x < 4 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.BIRCH_PLANKS.defaultBlockState());
		}
		ResourceLocation base = CompatGameTests.blueprintFrom(helper, "qa_grand_repair", design, Map.of());
		ResourceLocation id = BlueprintStyles.styled(base, "grand");
		Furnishings.ENABLED = builtOn;
		Blueprint built = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("no " + id));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		Map<BlockPos, BlockState> standing = new LinkedHashMap<>();
		for (Blueprint.Entry e : built.blocks()) {
			BlockPos at = placement.origin().offset(e.pos());
			level.setBlockAndUpdate(at, e.state());
			standing.put(at, e.state());
		}
		helper.assertTrue(standing.size() == LENGTH, "the Grand row has " + standing.size() + " blocks");
		String wantFloor = builtOn ? FLOOR_R : FLOOR;
		helper.assertTrue(id(standing.get(at(placement, 0))).equals(wantFloor), "built with the switch " + (builtOn ? "on" : "off")
			+ ", the floor is " + id(standing.get(at(placement, 0))) + ", not " + wantFloor);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BuildSiteManager.get(level).recordFinished(id, placement, owner.getUUID());
		PartnerShowsCompatTests.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(item(FLOOR), 2));
		chest.setItem(1, new ItemStack(item(FLOOR_R), 2));
		chest.setItem(2, new ItemStack(item(PANEL), 1));
		chest.setItem(3, new ItemStack(item(PANEL_R), 1));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		return new Stage(level, owner, builder, id, placement, standing, chest);
	}

	private static BlockPos at(BlueprintData.Placement placement, int x) {
		return placement.origin().offset(x, 0, 0);
	}

	/** Breaks the holes, lets the Builder see them (he asks) and has the owner click [Yes]. */
	private static void breakAndSayYes(GameTestHelper helper, Stage s) {
		for (int x : HOLES) {
			s.level().setBlockAndUpdate(at(s.placement(), x), Blocks.AIR.defaultBlockState());
		}
		if (!ModAttachments.BUILDER_JOB.has(s.builder())) {
			Upkeep.look(s.level(), s.builder());
		}
		try {
			s.level().getServer().getCommands().getDispatcher().execute(RepairAsks.command("yes", s.placement().origin()).substring(1),
				s.owner().createCommandSourceStack());
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new GameTestAssertException("the chat's [Yes] command didn't parse: " + e.getMessage());
		}
	}

	/**
	 * The repair is over (no repair site, the Builder free) and its unambiguous parts hold: every hole filled, every
	 * undamaged block as it stood, the floor and panel materials neither lost nor doubled, nothing left to repair.
	 * Returns what each hole got.
	 */
	private static Map<Integer, String> assertRepaired(GameTestHelper helper, Stage s) {
		ServerLevel level = s.level();
		helper.assertTrue(BuildSiteManager.get(level).all().stream().noneMatch(site -> site.placement().equals(s.placement())),
			"still repairing");
		helper.assertTrue(!ModAttachments.BUILDER_JOB.has(s.builder()), "the Builder still has a job");
		Map<Integer, String> got = new TreeMap<>();
		for (int x : HOLES) {
			BlockState there = level.getBlockState(at(s.placement(), x));
			helper.assertTrue(!there.isAir(), "the hole at x " + x + " is still open");
			got.put(x, id(there));
		}
		for (int x = 0; x < LENGTH; x++) {
			if (isHole(x)) {
				continue;
			}
			BlockPos p = at(s.placement(), x);
			helper.assertTrue(level.getBlockState(p).equals(s.standing().get(p)), "the undamaged block at x " + x + " was " + id(s.standing().get(p))
				+ " and is now " + id(level.getBlockState(p)));
		}
		Map<String, Integer> left = new HashMap<>();
		for (String item : List.of(FLOOR, FLOOR_R, PANEL, PANEL_R)) {
			left.put(item, held(helper, s, item(item)));
		}
		int floorPlaced = (int) java.util.stream.IntStream.of(HOLES).filter(x -> x < 4).count();
		int floorLeft = left.get(FLOOR) + left.get(FLOOR_R);
		int panelLeft = left.get(PANEL) + left.get(PANEL_R);
		helper.assertTrue(floorLeft == 4 - floorPlaced, "floor blocks: " + floorPlaced + " put back and " + floorLeft + " left of 4 " + left);
		helper.assertTrue(panelLeft == 2 - 1, "panel blocks: 1 put back and " + panelLeft + " left of 2 " + left);
		// each hole took its own kind out of the chest: what's left of that item is one less per hole that holds it
		for (String item : List.of(FLOOR, FLOOR_R, PANEL, PANEL_R)) {
			int start = item.equals(FLOOR) || item.equals(FLOOR_R) ? 2 : 1;
			long used = got.values().stream().filter(item::equals).count();
			helper.assertTrue(left.get(item) == start - used, item + ": " + used + " put in, " + left.get(item) + " left of " + start);
		}
		BuildSiteManager manager = BuildSiteManager.get(level);
		Blueprint now = BlueprintLibrary.get(level, s.structure()).orElseThrow();
		int missing = BuildPlan.repair(now, s.placement(), level, manager.gaveUp(s.placement())).size();
		helper.assertTrue(missing == 0, "after the repair " + missing + " blocks still count as missing (it would be asked about again)");
		helper.assertTrue(manager.repairAsk(s.placement()) == null, "a repair question is still open: " + manager.repairAsk(s.placement()));
		return got;
	}

	private static boolean isHole(int x) {
		return java.util.Arrays.stream(HOLES).anyMatch(h -> h == x);
	}

	/** {@code item} in the chest, the Builder's bag and inventory, and lying about. */
	private static int held(GameTestHelper helper, Stage s, Item item) {
		int n = 0;
		for (int i = 0; i < s.chest().getContainerSize(); i++) {
			ItemStack stack = s.chest().getItem(i);
			n += stack.is(item) ? stack.getCount() : 0;
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrElse(s.builder(), null);
		if (bag != null) {
			n += bag.count(item);
		}
		for (int i = 0; i < s.builder().getInventory().getContainerSize(); i++) {
			ItemStack stack = s.builder().getInventory().getItem(i);
			n += stack.is(item) ? stack.getCount() : 0;
		}
		for (ItemEntity e : s.level().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	/**
	 * (a) The open case: a Grand building finished with {@code furnishedBuilds} off (vanilla floors), the switch turned on
	 * (2.1 installed), two floor blocks and the panel broken, repaired after the owner's [Yes]. Either floor may go back;
	 * the holes are filled, the two undamaged vanilla floor blocks stay vanilla (a repair never tears down what stands),
	 * no material is lost or doubled, and it isn't asked about again. What went back is printed for the wiki.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaGrandRepairSwitchedOn"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaGrandRepairSwitchedOn")
	public void aVanillaGrandBuildingRepairedAfterTheSwitchIsTurnedOn(GameTestHelper helper) {
		Stage s = stage(helper, false);
		Furnishings.ENABLED = true;
		breakAndSayYes(helper, s);
		helper.succeedWhen(() -> {
			Map<Integer, String> got = assertRepaired(helper, s);
			for (int x = 0; x < LENGTH; x++) {
				if (!isHole(x)) {
					helper.assertTrue(id(s.level().getBlockState(at(s.placement(), x))).startsWith("minecraft:"),
						"an undamaged vanilla block turned Rechiseled at x " + x);
				}
			}
			System.out.println("QA1107 switched-on repair of a vanilla Grand building put back (x -> block): " + got);
		});
	}

	/** (b) The same vanilla Grand building repaired with the switch still off puts back today's Grand blocks, vanilla. */
	//$ gametest_ticks_batch AREA '2400' '"qaGrandRepairSwitchOff"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaGrandRepairSwitchOff")
	public void aVanillaGrandBuildingRepairedWithTheSwitchOffStaysVanilla(GameTestHelper helper) {
		Stage s = stage(helper, false);
		Furnishings.ENABLED = false;
		breakAndSayYes(helper, s);
		helper.succeedWhen(() -> {
			Map<Integer, String> got = assertRepaired(helper, s);
			helper.assertTrue(got.equals(Map.of(1, FLOOR, 3, FLOOR, 4, PANEL)), "with the switch off the repair put back " + got);
		});
	}

	/** (c) A Grand building built with the switch on (Rechiseled floors and panel) and repaired with it on gets Rechiseled back. */
	//$ gametest_ticks_batch AREA '2400' '"qaGrandRepairRechiseled"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaGrandRepairRechiseled")
	public void aRechiseledGrandBuildingIsRepairedInRechiseled(GameTestHelper helper) {
		Stage s = stage(helper, true);
		Furnishings.ENABLED = true;
		breakAndSayYes(helper, s);
		helper.succeedWhen(() -> {
			Map<Integer, String> got = assertRepaired(helper, s);
			helper.assertTrue(got.equals(Map.of(1, FLOOR_R, 3, FLOOR_R, 4, PANEL_R)), "built and repaired in Rechiseled, the repair put back " + got);
		});
	}

	/**
	 * (d) Saves: the buildings list saved and loaded between finishing (switch off) and the repair (switch on), as on a
	 * server restart with 2.1: the loaded building is the same Grand building, and the repair it plans covers exactly the
	 * holes, with the same blocks as before the restart (no undamaged vanilla block counted as missing).
	 */
	//$ gametest_batch AREA '"qaGrandRepairSaved"'
	@GameTest(template = AREA, batch = "qaGrandRepairSaved")
	public void aSavedVanillaGrandBuildingPlansTheSameRepairAfterLoading(GameTestHelper helper) {
		Stage s = stage(helper, false);
		ServerLevel level = s.level();
		Furnishings.ENABLED = true;
		for (int x : HOLES) {
			level.setBlockAndUpdate(at(s.placement(), x), Blocks.AIR.defaultBlockState());
		}
		BuildSiteManager live = BuildSiteManager.get(level);
		CompoundTag tag = live.save(new CompoundTag(), level.registryAccess());
		BuildSiteManager loaded = BuildSiteManager.load(tag, level.registryAccess());
		BuildSiteManager.Finished f = loaded.finishedIn(level).stream().filter(b -> b.placement().equals(s.placement())).findFirst()
			.orElseThrow(() -> new GameTestAssertException("the Grand building isn't in the loaded list"));
		helper.assertTrue(f.structure().equals(s.structure()) && f.owner().equals(s.owner().getUUID()), "loaded as " + f);
		Blueprint now = BlueprintLibrary.get(level, f.structure()).map(b -> loaded.furniture(f.placement()).apply(b, f.structure())).orElseThrow();
		Map<BlockPos, String> after = plan(BuildPlan.repair(now, f.placement(), level, loaded.gaveUp(f.placement())));
		Blueprint before = BlueprintLibrary.get(level, s.structure()).map(b -> live.furniture(s.placement()).apply(b, s.structure())).orElseThrow();
		Map<BlockPos, String> was = plan(BuildPlan.repair(before, s.placement(), level, live.gaveUp(s.placement())));
		Set<BlockPos> holes = new java.util.HashSet<>();
		for (int x : HOLES) {
			holes.add(at(s.placement(), x));
		}
		helper.assertTrue(after.keySet().equals(holes), "the loaded repair plans " + after.keySet().stream().map(p -> p.subtract(s.placement().origin()).toShortString()).toList());
		helper.assertTrue(after.equals(was), "the loaded repair plans " + after.values() + ", before the save " + was.values());
		System.out.println("QA1107 saved and loaded, the switched-on repair plans: " + new TreeMap<>(after.entrySet().stream()
			.collect(java.util.stream.Collectors.toMap(e -> e.getKey().getX() - s.placement().origin().getX(), Map.Entry::getValue))));
		helper.succeed();
	}

	private static Map<BlockPos, String> plan(BuildPlan plan) {
		Map<BlockPos, String> out = new HashMap<>();
		java.util.stream.Stream.of(BuildPlan.Stage.values()).flatMap(st -> plan.steps(st).stream()).forEach(st -> out.put(st.pos(), id(st.state())));
		return out;
	}

	private static String id(BlockState state) {
		return state == null ? "none" : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow(() -> new GameTestAssertException("no item " + id));
	}
}
