package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.nurse.Nurses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 28.7 with the real Cobblemon: a nurse at a Healing Machine heals your team in it (the machine is in use, then
 * every Pokémon is full), keeps it charged on shift, waits while it's busy, heals by hand with the config off; the
 * Pokémon Center is in the Blueprint Table and a builder builds both tiers, Healing Machine, PC and Pasture Block too.
 */
public class PokemonCenterCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos MACHINE = new BlockPos(3, 2, 3);
	private static final BlockPos STANDING = new BlockPos(4, 2, 4);

	private static Block machineBlock() {
		return BuiltInRegistries.BLOCK.get(ModVillagers.HEALING_MACHINE_BLOCK);
	}

	/** A nurse employed at a Healing Machine in the test area. */
	private static Villager nurseAtMachine(GameTestHelper helper) {
		helper.setDayTime(2000);
		helper.setBlock(MACHINE, machineBlock());
		Villager nurse = helper.spawn(EntityType.VILLAGER, STANDING);
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(MACHINE), ModVillagers.HEALING_MACHINE_POI, ModVillagers.NURSE);
		helper.assertTrue(nurse.getVillagerData().getProfession() == ModVillagers.NURSE, "setup: not a nurse");
		return nurse;
	}

	private static List<Pokemon> hurtParty(ServerPlayer player, String... species) {
		List<Pokemon> team = new ArrayList<>();
		for (String name : species) {
			Pokemon pokemon = PokemonProperties.Companion.parse(name + " level=20", " ", "=").create();
			Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
			pokemon.setCurrentHealth(1);
			team.add(pokemon);
		}
		return team;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/** Right-click the nurse: your Poké Balls go in her machine (it runs), and then every Pokémon is full. Free. */
	//$ gametest_ticks AREA '600'
	@GameTest(template = AREA, timeoutTicks = 600)
	public void aNurseHealsTheTeamInTheMachine(GameTestHelper helper) {
		Villager nurse = nurseAtMachine(helper);
		ServerPlayer player = player(helper);
		List<Pokemon> team = hurtParty(player, "pikachu", "bulbasaur");
		HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
		machine.setHealingCharge(0);
		int emeralds = player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD);
		Nurses.resetCooldowns();
		Nurses.treat(player, nurse);
		helper.assertTrue(machine.isInUse(), "the machine isn't healing the team");
		helper.assertTrue(player.getUUID().equals(machine.getCurrentUser()), "the machine is healing someone else's team");
		helper.assertFalse(team.get(0).isFullHealth(), "healed at once: the machine's own heal time was skipped");
		helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == emeralds, "healing cost something");
		helper.succeedWhen(() -> {
			for (Pokemon pokemon : team) {
				helper.assertTrue(pokemon.isFullHealth(), pokemon.getSpecies().getName() + " at " + pokemon.getCurrentHealth() + "/" + pokemon.getMaxHealth());
			}
			helper.assertFalse(machine.isInUse(), "the machine is still running");
		});
	}

	/** A second player while the machine runs is asked to wait; their team isn't touched and her cooldown isn't spent. */
	//$ gametest_ticks AREA '200'
	@GameTest(template = AREA, timeoutTicks = 200)
	public void aBusyMachineMakesTheNextPlayerWait(GameTestHelper helper) {
		Villager nurse = nurseAtMachine(helper);
		ServerPlayer first = player(helper);
		ServerPlayer second = player(helper);
		hurtParty(first, "pikachu");
		Pokemon waiting = hurtParty(second, "squirtle").get(0);
		Nurses.resetCooldowns();
		Nurses.treat(first, nurse);
		Nurses.treat(second, nurse);
		HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
		helper.assertTrue(first.getUUID().equals(machine.getCurrentUser()), "the first team should be in the machine");
		helper.assertFalse(waiting.isFullHealth(), "the second team was healed while the machine was busy");
		helper.succeed();
	}

	/** On shift she keeps her machine charged. */
	//$ gametest_ticks AREA '1200'
	@GameTest(template = AREA, timeoutTicks = 1200)
	public void aNurseOnShiftKeepsTheMachineCharged(GameTestHelper helper) {
		helper.setDayTime(3000);
		nurseAtMachine(helper);
		helper.setDayTime(3000);
		HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
		machine.setHealingCharge(0);
		helper.succeedWhen(() -> helper.assertTrue(machine.getHealingCharge() >= machine.getMaxCharge(),
			"charge " + machine.getHealingCharge() + " of " + machine.getMaxCharge()));
	}

	/** Config {@code nurseHealingMachine} off: she heals the team by hand at once, and the machine stays idle. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void withTheConfigOffSheHealsByHand(GameTestHelper helper) {
		Villager nurse = nurseAtMachine(helper);
		ServerPlayer player = player(helper);
		Pokemon pokemon = hurtParty(player, "pikachu").get(0);
		Nurses.HEALING_MACHINE = false;
		try {
			Nurses.resetCooldowns();
			Nurses.treat(player, nurse);
		} finally {
			Nurses.HEALING_MACHINE = true;
		}
		HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
		helper.assertFalse(machine.isInUse(), "the machine ran with the config off");
		helper.assertTrue(pokemon.isFullHealth(), "not healed by hand");
		helper.succeed();
	}

	/** With Cobblemon the Pokémon Center is in the Blueprint Table, and the hall suggests one. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePokemonCenterIsInTheTable(GameTestHelper helper) {
		List<ResourceLocation> listed = TableServer.listing(helper.getLevel().getServer()).stream().map(e -> e.id()).toList();
		for (StarterBlueprints.Entry entry : StarterBlueprints.COBBLEMON_ONLY) {
			helper.assertTrue(listed.contains(entry.id()), entry.id() + " isn't in the Blueprint Table");
		}
		helper.assertTrue(VillageAdvice.wantsPokemonCenter(true, VillageRanks.Rank.VILLAGE, List.of()), "the hall's advice");
		helper.succeed();
	}

	/** A builder builds the Pokémon Center, Healing Machine and PC included, from what's in the barrels. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"pokemon_center_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "pokemon_center_build")
	public void aBuilderBuildsThePokemonCenter(GameTestHelper helper) {
		build(helper, StarterBlueprints.POKEMON_CENTER, List.of(new BlockPos(6, 1, 7), new BlockPos(10, 1, 7)));
	}

	/** And Pokémon Center II: the lodge, its Shop Counter and beds, and the Pasture Block in the garden. */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"pokemon_center_2_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "pokemon_center_2_build")
	public void aBuilderBuildsPokemonCenterII(GameTestHelper helper) {
		build(helper, StarterBlueprints.POKEMON_CENTER_2, List.of(new BlockPos(6, 1, 22), new BlockPos(6, 1, 14)));
	}

	/** ROADMAP 28.7a: a builder builds the Mountain Lodge II with Cobblemon: its Healing Machine, Pasture Block and Shop Counter in place. */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"pokemon_center_lodge_2_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "pokemon_center_lodge_2_build")
	public void aBuilderBuildsTheLodgePokemonCenterII(GameTestHelper helper) {
		// 21 wide: at (8, 2, 3) it and its landscaping margin (a block round it) stay inside the 30-wide area
		build(helper, StarterBlueprints.POKEMON_CENTER_LODGE_2, List.of(new BlockPos(6, 1, 7), new BlockPos(6, 1, 15), new BlockPos(16, 1, 6)),
			new BlockPos(8, 2, 3));
	}

	/** ROADMAP 28.7a: and the Sunny Plaza II. */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"pokemon_center_plaza_2_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "pokemon_center_plaza_2_build")
	public void aBuilderBuildsThePlazaPokemonCenterII(GameTestHelper helper) {
		build(helper, StarterBlueprints.POKEMON_CENTER_PLAZA_2, List.of(new BlockPos(6, 1, 7), new BlockPos(6, 1, 21), new BlockPos(6, 1, 14)));
	}

	/** Builds {@code entry} at (9, 2, 3) from barrels holding exactly its materials; {@code check}: template spots that must hold Cobblemon's blocks (or the Shop Counter). */
	private static void build(GameTestHelper helper, StarterBlueprints.Entry entry, List<BlockPos> check) {
		build(helper, entry, check, new BlockPos(9, 2, 3));
	}

	/** As {@link #build(GameTestHelper, StarterBlueprints.Entry, List)}, at {@code origin}. */
	private static void build(GameTestHelper helper, StarterBlueprints.Entry entry, List<BlockPos> check, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + entry.id());
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(plan.materials().keySet().stream().anyMatch(i -> BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("cobblemon")),
			"the plan needs none of Cobblemon's blocks: " + plan.materials().keySet());
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(3)
				.map(p -> helper.relativePos(p) + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			for (BlockPos spot : check) {
				BlockState there = level.getBlockState(placement.origin().offset(spot));
				helper.assertTrue(BuiltInRegistries.BLOCK.getKey(there.getBlock()).getNamespace().equals("cobblemon")
					|| there.is(ModBlocks.SHOP_COUNTER), "nothing of Cobblemon's at " + spot + ": " + there);
			}
		});
	}
}
