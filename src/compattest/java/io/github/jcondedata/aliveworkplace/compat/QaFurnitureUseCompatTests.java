package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.FurnitureStock;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureEquivalents;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeat;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeats;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/**
 * QA for ROADMAP 37.6 (villagers use the furniture) and 37.13 (any mod's spruce chair will do), written from the specs:
 * a seated villager stands up when the chair is broken under them, at bedtime and when a hostile comes near; a chair with
 * someone in it isn't sat on; two chairs seat two villagers; a vanilla stair chair is no seat; a broken fancy bed is given
 * up. The equivalence table follows {@code furnitureMods} as the config file sets it (reversed, or with a mod left out),
 * one chair in the chest never furnishes two spots, a spot already built keeps its piece, the switch off makes nothing
 * furniture, and every sentence of the material list's name and hover is translated.
 */
public class QaFurnitureUseCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos CHAIR = new BlockPos(6, 2, 6);
	private static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
	}

	private static Item hcChair() {
		return item("handcrafted:spruce_chair");
	}

	private static Item cfChair() {
		return item("cobblefurnies:spruce_chair");
	}

	private static Runnable clock(ServerLevel level) {
		long day = level.getDayTime();
		long game = level.getGameTime();
		return () -> level.setDayTime(day + level.getGameTime() - game);
	}

	private static void setTime(ServerLevel level, long time) {
		long day = level.getDayTime() / 24000;
		long now = level.getDayTime() % 24000;
		level.setDayTime((now <= time ? day : day + 1) * 24000 + time);
	}

	private static BlockPos chair(GameTestHelper helper, BlockPos at) {
		Block chair = block("handcrafted:oak_chair");
		helper.assertTrue(chair.defaultBlockState().is(FurnitureSeats.SEATS), "Handcrafted's oak chair isn't a villager seat");
		helper.setBlock(at, chair.defaultBlockState());
		return helper.absolutePos(at);
	}

	@org.jetbrains.annotations.Nullable
	private static FurnitureSeat seatOf(Villager v) {
		return v.getVehicle() instanceof FurnitureSeat seat ? seat : null;
	}

	private static List<FurnitureSeat> seats(GameTestHelper helper, BlockPos chair) {
		return helper.getLevel().getEntitiesOfClass(FurnitureSeat.class, new AABB(chair).inflate(1), Entity::isAlive);
	}

	/**
	 * Waits for {@code v} to sit at supper, then runs {@code then} once; succeeds when {@code standing} holds after it. The
	 * clock is put back and the villager removed either way.
	 */
	private static void sitThen(GameTestHelper helper, Villager v, Runnable then, Runnable check) {
		ServerLevel level = helper.getLevel();
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 300);
		boolean[] sat = {false};
		helper.onEachTick(() -> {
			if (!sat[0] && seatOf(v) != null) {
				sat[0] = true;
				then.run();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(sat[0], "the villager never sat on the chair at supper");
			check.run();
			v.discard();
			restore.run();
		});
	}

	// ---- 37.6: villagers use the furniture ----

	//$ gametest_ticks_batch HUGE_AREA '900' '"qa_furniture_seat_broken"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 900, batch = "qa_furniture_seat_broken")
	public void aVillagerStandsUpWhenTheChairIsBrokenUnderThem(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos chair = chair(helper, CHAIR);
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.south());
		sitThen(helper, v, () -> helper.setBlock(CHAIR, Blocks.AIR), () -> {
			helper.assertFalse(v.isPassenger(), "the villager still sits where the chair was broken");
			helper.assertTrue(seats(helper, chair).isEmpty(), "the seat outlived its chair");
		});
	}

	//$ gametest_ticks_batch HUGE_AREA '900' '"qa_furniture_seat_bedtime"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 900, batch = "qa_furniture_seat_bedtime")
	public void aSeatedVillagerStandsUpAtBedtime(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos chair = chair(helper, CHAIR);
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.south());
		sitThen(helper, v, () -> setTime(helper.getLevel(), FurnitureSeats.BEDTIME), () -> {
			helper.assertFalse(v.isPassenger(), "the villager still sits at bedtime (12000)");
			helper.assertTrue(seats(helper, chair).isEmpty(), "the seat stayed after bedtime");
		});
	}

	//$ gametest_ticks_batch HUGE_AREA '900' '"qa_furniture_seat_hostile"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 900, batch = "qa_furniture_seat_hostile")
	public void aSeatedVillagerStandsUpWhenAZombieComesNear(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		chair(helper, CHAIR);
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.south());
		Zombie[] zombie = {null};
		sitThen(helper, v, () -> {
			zombie[0] = helper.spawn(EntityType.ZOMBIE, CHAIR.east(4));
			zombie[0].setNoAi(true);
		}, () -> {
			helper.assertFalse(v.isPassenger(), "the villager stays seated with a zombie 4 blocks away");
			zombie[0].discard();
		});
	}

	//$ gametest_ticks_batch HUGE_AREA '300' '"qa_furniture_seat_occupied"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 300, batch = "qa_furniture_seat_occupied")
	public void aChairWithSomeoneInItIsntSatOn(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos chair = chair(helper, CHAIR);
		ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, CHAIR);
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.south());
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 300);
		helper.assertFalse(FurnitureSeats.free(level, chair, v), "a chair with an armor stand in it counts as free");
		helper.runAfterDelay(200, () -> {
			boolean sat = v.isPassenger();
			int onChair = seats(helper, chair).size();
			v.discard();
			stand.discard();
			restore.run();
			helper.assertFalse(sat, "the villager sat on a chair something already stands in");
			helper.assertTrue(onChair == 0, onChair + " seats on an occupied chair");
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa_furniture_two_chairs"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa_furniture_two_chairs")
	public void twoChairsSeatTwoVillagersOneEach(GameTestHelper helper) {
		// B676: alone in its batch, so clear what earlier batches left round this spot first. A leftover villager just
		// outside the area walking to the first chair keeps it taken (a seat another villager is walking to isn't
		// taken twice), and a leftover hostile keeps a villager from sitting at all.
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = chair(helper, CHAIR);
		BlockPos b = chair(helper, CHAIR.east(3));
		Villager va = helper.spawn(EntityType.VILLAGER, CHAIR.south());
		Villager vb = helper.spawn(EntityType.VILLAGER, CHAIR.east(3).south());
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 300);
		helper.succeedWhen(() -> {
			FurnitureSeat sa = seatOf(va);
			FurnitureSeat sb = seatOf(vb);
			helper.assertTrue(sa != null && sb != null, "not both villagers seated: " + (sa != null) + ", " + (sb != null));
			helper.assertTrue(!sa.blockPosition().equals(sb.blockPosition()), "both villagers on one chair");
			helper.assertTrue(List.of(a, b).contains(sa.blockPosition()) && List.of(a, b).contains(sb.blockPosition()), "seated off the chairs");
			helper.assertTrue(seats(helper, a).size() == 1 && seats(helper, b).size() == 1, "the chairs don't each hold one seat");
			va.discard();
			vb.discard();
			restore.run();
		});
	}

	//$ gametest_ticks_batch HUGE_AREA '300' '"qa_furniture_stair_chair"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 300, batch = "qa_furniture_stair_chair")
	public void aVanillaStairChairIsNoSeat(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.assertFalse(Blocks.SPRUCE_STAIRS.defaultBlockState().is(FurnitureSeats.SEATS), "vanilla spruce stairs are a villager seat");
		helper.setBlock(CHAIR, Blocks.SPRUCE_STAIRS);
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.south());
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 300);
		helper.runAfterDelay(200, () -> {
			boolean sat = v.isPassenger();
			v.discard();
			restore.run();
			helper.assertFalse(sat, "a villager sat on a vanilla stair chair");
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_furniture_fancy_bed_broken"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_furniture_fancy_bed_broken")
	public void aBrokenFancyBedIsGivenUp(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Block fancy = block("handcrafted:oak_fancy_bed");
		BlockPos foot = new BlockPos(5, 2, 6);
		BlockState footState = fancy.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT);
		helper.setBlock(foot, footState);
		helper.setBlock(foot.north(), footState.setValue(BedBlock.PART, BedPart.HEAD));
		Villager v = helper.spawn(EntityType.VILLAGER, foot.south(2));
		Runnable restore = clock(level);
		setTime(level, 13000);
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			BlockPos bed = VillageNeeds.bed(level, v);
			if (!broken[0] && bed != null && level.getBlockState(bed).is(fancy)) {
				broken[0] = true;
				helper.setBlock(foot.north(), Blocks.AIR);
				helper.setBlock(foot, Blocks.AIR);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "the villager never claimed the fancy bed");
			helper.assertFalse(v.isSleeping(), "the villager sleeps on in a broken fancy bed");
			helper.assertTrue(VillageNeeds.bed(level, v) == null, "the villager still has the broken fancy bed as home");
			v.discard();
			restore.run();
		});
	}

	// ---- 37.13: any mod's piece will do ----

	private static void chest(GameTestHelper helper, ItemStack... items) {
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.clearContent();
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i].copy());
		}
	}

	private static BuildSite site(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", COTTAGE,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 3)), Rotation.NONE, Mirror.NONE));
		site.plan(level);
		return site;
	}

	private static BuildSite restock(GameTestHelper helper, BuildSite site, Item wanted) {
		ServerLevel level = helper.getLevel();
		FurnitureStock.restock(level, site, BuilderBag.of(List.of()), SupplyContainers.find(level, helper.absolutePos(BENCH), site.plan(level).bounds()),
			wanted);
		return site;
	}

	private static List<BlockPos> chairSpots(BuildSite site) {
		List<BlockPos> out = new ArrayList<>();
		for (FurnitureSpot spot : BlueprintFurniture.of(COTTAGE)) {
			if (spot.kind() == FurnitureSpot.Kind.CHAIR) {
				out.add(site.placement().origin().offset(spot.pos()));
			}
		}
		return out;
	}

	private static List<String> chairs(BuildSite site, ServerLevel level) {
		BuildPlan plan = site.plan(level);
		Map<BlockPos, BlockState> steps = new HashMap<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			plan.steps(stage).forEach(s -> steps.put(s.pos(), s.state()));
		}
		List<String> out = new ArrayList<>();
		for (BlockPos pos : chairSpots(site)) {
			BlockState state = steps.getOrDefault(pos, Blocks.AIR.defaultBlockState());
			out.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		}
		return out;
	}

	private static void config(List<String> mods) {
		WorkplaceConfig config = new WorkplaceConfig();
		config.furnitureMods = new ArrayList<>(mods);
		config.apply();
	}

	/** The order reversed in the config file: CobbleFurnies' chairs are planned, and taken over Handcrafted's in the chest. */
	//$ gametest_batch HUGE_AREA '"qa_furniture_any_mod"'
	@GameTest(template = HUGE_AREA, batch = "qa_furniture_any_mod")
	public void theOrderReversedInTheConfigFileIsFollowed(GameTestHelper helper) {
		try {
			config(List.of("beautify", "cobblefurnies", "cozyhome", "handcrafted"));
			helper.assertTrue(FurnitureEquivalents.options(hcChair()).equals(List.of(cfChair(), hcChair())),
				"options with CobbleFurnies first: " + FurnitureEquivalents.options(hcChair()));
			helper.assertTrue(FurnitureEquivalents.key(hcChair()) == cfChair(), "the material list counts the chair by " + FurnitureEquivalents.key(hcChair()));
			ServerLevel level = helper.getLevel();
			BuildSite planned = site(helper);
			helper.assertTrue(chairs(planned, level).stream().allMatch("cobblefurnies:spruce_chair"::equals),
				"CobbleFurnies first in the order, the plan's chairs are " + chairs(planned, level));
			chest(helper, new ItemStack(hcChair(), 2), new ItemStack(cfChair(), 2));
			List<String> both = chairs(restock(helper, site(helper), cfChair()), level);
			helper.assertTrue(both.stream().allMatch("cobblefurnies:spruce_chair"::equals), "both in the chest, reversed order: " + both);
			chest(helper, new ItemStack(hcChair(), 2));
			List<String> onlyHc = chairs(restock(helper, site(helper), cfChair()), level);
			helper.assertTrue(onlyHc.stream().allMatch("handcrafted:spruce_chair"::equals), "only Handcrafted's in the chest: " + onlyHc);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/** A mod left out of {@code furnitureMods} isn't an option: its chair in the chest isn't used, and isn't on the hover. */
	//$ gametest_batch HUGE_AREA '"qa_furniture_any_mod"'
	@GameTest(template = HUGE_AREA, batch = "qa_furniture_any_mod")
	public void aModLeftOutOfTheOrderIsNoOption(GameTestHelper helper) {
		try {
			config(List.of("handcrafted", "cozyhome", "beautify"));
			helper.assertTrue(FurnitureEquivalents.options(hcChair()).equals(List.of(hcChair())), "options: " + FurnitureEquivalents.options(hcChair()));
			Component name = FurnitureEquivalents.name(hcChair());
			helper.assertFalse(name.getString().contains("any furniture mod"), "one mod left, yet the list says " + name.getString());
			chest(helper, new ItemStack(cfChair(), 2));
			List<String> chairs = chairs(restock(helper, site(helper), hcChair()), helper.getLevel());
			helper.assertTrue(chairs.stream().allMatch("handcrafted:spruce_chair"::equals), "CobbleFurnies out of the order, the chairs: " + chairs);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/** One CobbleFurnies chair in the chest furnishes one spot; the other keeps Handcrafted's. */
	//$ gametest_batch HUGE_AREA '"qa_furniture_any_mod"'
	@GameTest(template = HUGE_AREA, batch = "qa_furniture_any_mod")
	public void oneChairInTheChestFurnishesOneSpot(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			chest(helper, new ItemStack(cfChair(), 1));
			List<String> chairs = chairs(restock(helper, site(helper), hcChair()), helper.getLevel());
			helper.assertTrue(chairs.size() == 2 && chairs.contains("cobblefurnies:spruce_chair") && chairs.contains("handcrafted:spruce_chair"),
				"one CobbleFurnies chair for two spots: " + chairs);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/** A spot whose Handcrafted chair is already built keeps it; only the unbuilt spot switches to the chest's chair. */
	//$ gametest_batch HUGE_AREA '"qa_furniture_any_mod_half"'
	@GameTest(template = HUGE_AREA, batch = "qa_furniture_any_mod_half")
	public void aHalfFurnishedBuildKeepsItsPieces(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			ServerLevel level = helper.getLevel();
			BuildSite site = site(helper);
			List<BlockPos> spots = chairSpots(site);
			helper.assertTrue(spots.size() == 2, "the cottage has " + spots.size() + " chair spots");
			BlockState built = block("handcrafted:spruce_chair").defaultBlockState();
			level.setBlockAndUpdate(spots.get(0), built);
			chest(helper, new ItemStack(cfChair(), 2));
			List<String> chairs = chairs(restock(helper, site, hcChair()), level);
			level.setBlockAndUpdate(spots.get(0), Blocks.AIR.defaultBlockState());
			helper.assertTrue(chairs.equals(List.of("handcrafted:spruce_chair", "cobblefurnies:spruce_chair")),
				"the built chair and the unbuilt one: " + chairs);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/** With {@code furnishedBuilds} off nothing is furniture: no options, no "any furniture mod", no restocking. */
	//$ gametest_batch HUGE_AREA '"qa_furniture_any_mod"'
	@GameTest(template = HUGE_AREA, batch = "qa_furniture_any_mod")
	public void withFurnishedBuildsOffNothingIsInterchangeable(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			BuildSite site = site(helper);
			Furnishings.ENABLED = false;
			helper.assertTrue(FurnitureEquivalents.options(cfChair()).equals(List.of(cfChair())), "options with the switch off: "
				+ FurnitureEquivalents.options(cfChair()));
			helper.assertTrue(FurnitureEquivalents.name(hcChair()).getString().equals(hcChair().getDescription().getString()),
				"the name with the switch off: " + FurnitureEquivalents.name(hcChair()).getString());
			chest(helper, new ItemStack(cfChair(), 2));
			boolean changed = FurnitureStock.restock(helper.getLevel(), site, BuilderBag.of(List.of()),
				SupplyContainers.find(helper.getLevel(), helper.absolutePos(BENCH), site.plan(helper.getLevel()).bounds()), hcChair());
			helper.assertFalse(changed, "the builder restocked furniture with furnished builds off");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/** Every sentence of the name and the hover is translated, with its arguments in place, for every mod in the default order. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa_furniture_any_mod"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_furniture_any_mod")
	public void theMaterialListTextIsTranslated(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			Language lang = Language.getInstance();
			for (String key : List.of("book.aliveworkplace.material_list.any_mod", "book.aliveworkplace.material_list.mod_options",
				"book.aliveworkplace.material_list.mod_option")) {
				helper.assertTrue(lang.has(key), "no text for " + key);
			}
			for (String mod : Furnishings.DEFAULT_MODS) {
				helper.assertTrue(lang.has("furniture_mod.aliveworkplace." + mod), "no player name for furniture mod " + mod);
			}
			Component name = FurnitureEquivalents.name(cfChair());
			String shown = name.getString();
			helper.assertTrue(shown.equals(hcChair().getDescription().getString() + " (any furniture mod)"),
				"the name (by the first mod's piece): " + shown);
			HoverEvent hover = name.getStyle().getHoverEvent();
			String text = hover == null ? "" : hover.getValue(HoverEvent.Action.SHOW_TEXT).getString();
			String[] lines = text.split("\n");
			helper.assertTrue(lines.length == 3 && lines[0].equals("Any of these will do:"), "the hover: " + text);
			helper.assertTrue(lines[1].equals(hcChair().getDescription().getString() + " (Handcrafted)")
				&& lines[2].equals(cfChair().getDescription().getString() + " (CobbleFurnies)"), "the hover's options, in order: " + text);
			helper.assertFalse(text.contains("%") || text.contains("aliveworkplace."), "a raw key or argument in the hover: " + text);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}
}
