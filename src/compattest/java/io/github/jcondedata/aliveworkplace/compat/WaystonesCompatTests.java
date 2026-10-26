package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTypes;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 41.1 with the real Waystones mod: the Steward proposes the Waystone Plaza once the village has a hall, in the
 * village culture's look; a builder builds it and its stone is a working waystone of the mod's named after the village
 * (a player can discover it); renaming the hall renames the stone; a broken stone is put back by the builder's upkeep
 * and is named again; {@code villageWaystones} off, the plaza isn't proposed.
 */
public class WaystonesCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final ResourceLocation RULE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "waystone_plaza");
	private static final BlockPos HALL = new BlockPos(1, 2, 12);
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 3);
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it. */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);

	/**
	 * B541: removes every Village Hall an earlier batch left whose village reaches this test area. A plaza's stone belongs
	 * to the hall nearest the plaza, so a leftover hall 11 blocks north of the plaza (outside this area, never cleared)
	 * took it from this test's hall 12 blocks away, and the round never raised it. Only for tests alone in their batch;
	 * call it before placing anything.
	 */
	private static void clearLeftoverHalls(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		net.minecraft.world.phys.AABB box = helper.getBounds();
		double halfDiagonal = Math.sqrt(box.getXsize() * box.getXsize() + box.getYsize() * box.getYsize() + box.getZsize() * box.getZsize()) / 2;
		int reach = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS + (int) Math.ceil(halfDiagonal);
		for (BlockPos hall : level.getPoiManager().findAll(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.VILLAGE_HALL_POI),
				p -> true, BlockPos.containing(box.getCenter()), reach, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			level.removeBlock(hall, false);
		}
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, String name) {
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setCustomName(Component.literal(name));
		return hall;
	}

	private static BlueprintData.Placement placement(GameTestHelper helper) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
	}

	private static BlockPos stone(GameTestHelper helper) {
		return helper.absolutePos(ORIGIN).offset(STONE);
	}

	private static StewardWishes.Status verdict(GameTestHelper helper) {
		StewardRules.Rule rule = StewardRules.all().stream().filter(r -> r.id().equals(RULE)).findFirst()
			.orElseThrow(() -> new GameTestAssertException("no Steward rule " + RULE));
		StewardConditions.Facts facts = StewardConditions.Facts.of(helper.getLevel(), helper.absolutePos(HALL));
		return StewardWishes.judge(rule, facts, StewardWishes.State.EMPTY, 0).status();
	}

	/** Sets every block of the plaza as a finished one stands, and records it finished (owned by {@code owner}). */
	private static void paste(GameTestHelper helper, ResourceLocation id, java.util.UUID owner) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("no blueprint " + id));
		BuildPlan plan = BuildPlan.create(blueprint, placement(helper));
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(id, placement(helper), owner);
	}

	private static String debug(GameTestHelper helper) {
		BlockPos p = stone(helper);
		return helper.getLevel().getBlockState(p) + " / " + helper.getLevel().getBlockState(p.above()) + " be=" + helper.getLevel().getBlockEntity(p)
			+ " at=" + WaystonesAPI.getWaystoneAt(helper.getLevel(), p).map(w -> w.getClass().getSimpleName() + " valid=" + w.isValid() + " pos=" + w.getPos() + " name=" + w.getName().getString()).orElse("none")
			+ " up=" + WaystonesAPI.getWaystoneAt(helper.getLevel(), p.above()).map(w -> w.getClass().getSimpleName() + " valid=" + w.isValid() + " pos=" + w.getPos()).orElse("none");
	}

	private static Optional<String> nameAt(GameTestHelper helper) {
		return VillageWaystones.EXTENSION.call(s -> s.nameAt(helper.getLevel(), stone(helper)), Optional.<String>empty());
	}

	/**
	 * With a hall, the Steward's plaza rule holds and he tries the village culture's look first (each of the five looks
	 * carries its culture's waystone); a builder builds the plaza from barrels holding exactly its materials (a waystone
	 * among them), and its stone is a working waystone of the mod's, named after the village, that a player discovers.
	 */
	//$ gametest_ticks_batch AREA '2400' '"waystonePlaza"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "waystonePlaza")
	public void theBuilderRaisesAWorkingWaystoneNamedAfterTheVillage(GameTestHelper helper) {
		clearLeftoverHalls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		hall(helper, "Stonebridge");
		helper.assertTrue(VillageWaystones.offered(), "the plaza isn't offered with Waystones installed");
		helper.assertTrue(verdict(helper) == StewardWishes.Status.HELD, "the plaza rule with a hall: " + verdict(helper));
		List<ResourceLocation> tried = VillageWaystones.forVillage(level, helper.absolutePos(HALL));
		String culture = VillageWaystones.culture(level, helper.absolutePos(HALL));
		helper.assertTrue(tried.get(0).equals(VillageWaystones.plazaFor(culture)) && tried.size() == 5, "the plazas tried: " + tried + " in " + culture);
		Map<String, String> stones = Map.of("plains", "waystone", "desert", "sandy_waystone", "savanna", "mud_bricks_waystone",
			"snowy", "deepslate_waystone", "taiga", "mossy_waystone");
		for (Map.Entry<String, String> e : stones.entrySet()) {
			Blueprint b = BlueprintLibrary.get(level, VillageWaystones.plazaFor(e.getKey())).orElseThrow(() -> new GameTestAssertException("no plaza for " + e.getKey()));
			boolean has = b.blocks().stream().anyMatch(en -> BuiltInRegistries.BLOCK.getKey(en.state().getBlock())
				.equals(ResourceLocation.fromNamespaceAndPath("waystones", e.getValue())) && VillageWaystones.isStone(en.state()));
			helper.assertTrue(has, "the " + e.getKey() + " plaza has no waystones:" + e.getValue());
		}

		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, VillageWaystones.PLAZA, placement(helper));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + VillageWaystones.PLAZA);
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(plan.materials().keySet().stream().anyMatch(i -> BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("waystones")),
			"the plan needs no waystone: " + plan.materials().keySet());
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i, 2, 6);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(3)
				.map(p -> helper.relativePos(p) + "=" + level.getBlockState(p)).toList());
			Waystone waystone = WaystonesAPI.getWaystoneAt(level, stone(helper)).filter(Waystone::isValid)
				.orElseThrow(() -> new GameTestAssertException("the plaza's stone isn't a waystone of the mod's: " + debug(helper)));
			helper.assertTrue(waystone.getName().getString().equals("Stonebridge"), "the stone is called " + waystone.getName().getString());
			helper.assertTrue(waystone.getWaystoneType().equals(WaystoneTypes.WAYSTONE), "a " + waystone.getWaystoneType());
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			WaystonesAPI.activateWaystone(player, waystone);
			helper.assertTrue(WaystonesAPI.isWaystoneActivated(player, waystone), "a player can't discover the stone");
		});
	}

	/** The hall renamed: the plaza's stone takes the new name at once. */
	//$ gametest_batch AREA '"waystoneRenamed"'
	@GameTest(template = AREA, batch = "waystoneRenamed")
	public void renamingTheVillageRenamesItsWaystone(GameTestHelper helper) {
		clearLeftoverHalls(helper);
		VillageHallBlockEntity hall = hall(helper, "Oldmere");
		paste(helper, VillageWaystones.PLAZA, java.util.UUID.randomUUID());
		VillageWaystones.round(helper.getLevel(), helper.absolutePos(HALL));
		helper.assertTrue(nameAt(helper).equals(Optional.of("Oldmere")), "the stone after the round: " + nameAt(helper) + " " + debug(helper));
		hall.setCustomName(Component.literal("Newbury"));
		helper.assertTrue(nameAt(helper).equals(Optional.of("Newbury")), "the stone after the village was renamed: " + nameAt(helper));
		helper.succeed();
	}

	/** The stone broken: it's a hole in a finished build; the builder's upkeep puts a waystone back, named again. */
	//$ gametest_ticks_batch AREA '1600' '"waystoneRepair"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "waystoneRepair")
	public void aBrokenWaystoneIsRepairedAndNamedAgain(GameTestHelper helper) {
		clearLeftoverHalls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		hall(helper, "Ferncross");
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		paste(helper, VillageWaystones.PLAZA, builder.getUUID());
		VillageWaystones.round(level, helper.absolutePos(HALL));
		helper.assertTrue(nameAt(helper).equals(Optional.of("Ferncross")), "the stone before it was broken: " + nameAt(helper));
		BlockState lower = level.getBlockState(stone(helper));
		level.setBlock(stone(helper).above(), Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(stone(helper), Blocks.AIR.defaultBlockState(), 3);
		helper.assertTrue(nameAt(helper).isEmpty(), "a broken stone is still a waystone: " + nameAt(helper));
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(lower.getBlock().asItem()));
		boolean was = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false; // 36.15: repairs without asking; the ask has its own tests
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				BuildSite site = ModAttachments.BUILDER_JOB.has(builder) ? Builders.activeSite(level, builder) : Upkeep.look(level, builder);
				helper.assertTrue(site != null && site.isRepair(), "no repair started");
			})
			.thenWaitUntil(() -> {
				helper.assertTrue(VillageWaystones.isStone(level.getBlockState(stone(helper))), "no stone yet: " + level.getBlockState(stone(helper)));
				helper.assertTrue(nameAt(helper).equals(Optional.of("Ferncross")), "the repaired stone: " + nameAt(helper));
			})
			.thenExecute(() -> {
				Upkeep.ENABLED = was;
				Upkeep.ASK = asks;
			})
			.thenSucceed();
	}

	/** {@code villageWaystones} off: the plaza rule doesn't hold and no wish is the plaza, Waystones or not. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void withTheSwitchOffNoPlazaIsOffered(GameTestHelper helper) {
		hall(helper, "Quietford");
		boolean was = VillageWaystones.ENABLED;
		VillageWaystones.ENABLED = false;
		try {
			helper.assertFalse(VillageWaystones.offered(), "offered with the switch off");
			helper.assertTrue(verdict(helper) == StewardWishes.Status.NOT_HELD, "the plaza rule with the switch off: " + verdict(helper));
			StewardConditions.Facts facts = StewardConditions.Facts.of(helper.getLevel(), helper.absolutePos(HALL));
			boolean wished = StewardWishes.rank(StewardRules.all(), facts, StewardWishes.State.EMPTY, 0).stream()
				.anyMatch(w -> w.effect().blueprint().map(VillageWaystones::isPlaza).orElse(false));
			helper.assertFalse(wished, "the Steward wishes for a plaza with the switch off");
		} finally {
			VillageWaystones.ENABLED = was;
		}
		helper.succeed();
	}
}
