package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.gem.GemBeds;
import io.github.jcondedata.aliveworkplace.gem.GemGrowers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;

/** ROADMAP 28.11: the Gem Grower at a stonecutter, her amethyst beds, planted beds, orders and the bed files. */
public class GemGrowerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos CUTTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos BUDDING = new BlockPos(8, 2, 2);
	private static final BlockPos CLUSTER = BUDDING.above();
	private static final BlockPos BUD = BUDDING.south();

	/** A stonecutter with a chest by it holding {@code chest}, and a villager made its Gem Grower by a player's amethyst shard. */
	static Villager grower(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.AMETHYST_SHARD));
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		// (what the use-entity event does when a player sneak-right-clicks her with the shard)
		InteractionResult picked = Stations.choose(player, villager, player.getMainHandItem());
		helper.assertTrue(picked == InteractionResult.SUCCESS, "the shard picked no job: " + picked);
		helper.assertTrue(GemGrowers.isGrower(villager), "not a gem grower: " + villager.getVillagerData().getProfession());
		return villager;
	}

	private static String said(GameTestHelper helper, Villager grower) {
		WorkerStatus.Entry e = WorkerStatus.get(grower, helper.getLevel().getGameTime());
		return (e == null ? "no status" : e.line().getString()) + " at " + helper.relativePos(grower.blockPosition()) + ", beds "
			+ GemGrowers.beds(grower) + ", bag " + ModAttachments.BUILDER_BAG.getOrCreate(grower).stacks();
	}

	private static void amethystBed(GameTestHelper helper) {
		helper.setBlock(BUDDING, Blocks.BUDDING_AMETHYST);
		helper.setBlock(CLUSTER, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
		helper.setBlock(BUD, Blocks.SMALL_AMETHYST_BUD.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.SOUTH));
	}

	/** The Done when: a full amethyst cluster picked (its own loot, into her chest), the budding block still there, the small bud left to grow. */
	//$ gametest_ticks_batch AREA '2400' '"gem_amethyst"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "gem_amethyst")
	public void aFullAmethystClusterIsPickedAndTheBuddingBlockStays(GameTestHelper helper) {
		Leftovers.clear(helper);
		amethystBed(helper);
		Villager grower = grower(helper);
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(!helper.getBlockState(CLUSTER).is(Blocks.AMETHYST_CLUSTER), "the cluster wasn't picked; she says " + said(helper, grower));
			helper.assertTrue(chest.countItem(Items.AMETHYST_SHARD) >= 4, "shards in the chest: " + chest.countItem(Items.AMETHYST_SHARD)
				+ "; she says " + said(helper, grower));
			helper.assertTrue(helper.getBlockState(BUDDING).is(Blocks.BUDDING_AMETHYST), "the budding block was broken");
			helper.assertTrue(helper.getBlockState(BUD).is(Blocks.SMALL_AMETHYST_BUD) || helper.getBlockState(BUD).is(Blocks.MEDIUM_AMETHYST_BUD)
				|| helper.getBlockState(BUD).is(Blocks.LARGE_AMETHYST_BUD), "the unripe bud was picked: " + helper.getBlockState(BUD));
			helper.assertTrue(GemGrowers.beds(grower).contains(helper.absolutePos(BUDDING)), "the bed isn't remembered");
			helper.assertTrue(ModAttachments.GEMS_PICKED.getOrElse(grower, 0) >= 1, "no pick counted");
		});
	}

	/** A test bed planted with something (as a data pack could add): crying obsidian, planted with budding amethyst. */
	private static GemBeds.Bed plantedBed() {
		return new GemBeds.Bed(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "test_planted"),
			java.util.Optional.of(ResourceLocation.withDefaultNamespace("budding_amethyst")),
			new GemBeds.Touch(ResourceLocation.withDefaultNamespace("crying_obsidian"), null),
			List.of(ResourceLocation.withDefaultNamespace("amethyst_cluster")), ResourceLocation.withDefaultNamespace("amethyst_cluster"),
			Map.of(), 1, java.util.Set.of(), 50);
	}

	/**
	 * Missing makings: a planted bed with nothing to plant in her chests waits and says so; once the planting is in her
	 * chest she sets it against the bed's block.
	 */
	//$ gametest_ticks_batch AREA '2400' '"gem_planting"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "gem_planting")
	public void aPlantedBedWaitsForItsMakingsThenIsPlanted(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<GemBeds.Bed> shipped = GemBeds.beds();
		List<GemBeds.Bed> withTest = new ArrayList<>(shipped);
		withTest.add(plantedBed());
		GemBeds.set(withTest);
		Leftovers.after(helper, () -> GemBeds.set(shipped));
		BlockPos touch = new BlockPos(8, 2, 6);
		helper.setBlock(touch, Blocks.CRYING_OBSIDIAN);
		Villager grower = grower(helper);
		boolean[] waited = {false};
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(helper.getBlockState(touch.above()).isAir(), "planted with nothing in her chests");
			String status = said(helper, grower);
			helper.assertTrue(status.startsWith(Language.getInstance().getOrDefault("message.aliveworkplace.gem_grower.state.needs_planting")),
				"she doesn't say the bed wants planting: " + status);
			Container chest = helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.BUDDING_AMETHYST));
			waited[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(waited[0], "not yet waited");
			helper.assertTrue(helper.getBlockState(touch.above()).is(Blocks.BUDDING_AMETHYST), "not planted; she says " + said(helper, grower));
			helper.assertTrue(helper.getBlockState(touch).is(Blocks.CRYING_OBSIDIAN), "the bed's block is gone");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.BUDDING_AMETHYST) == 0, "the planting is still in the chest");
		});
	}

	/** Config {@code gemGrowers} off: a shard picks no job, and a grower already hired stands idle (the cluster stays). */
	//$ gametest_ticks_batch AREA '600' '"gem_off"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "gem_off")
	public void theSwitchOffStopsTheJob(GameTestHelper helper) {
		Leftovers.clear(helper);
		amethystBed(helper);
		Villager grower = grower(helper);
		GemGrowers.ENABLED = false;
		Leftovers.after(helper, () -> GemGrowers.ENABLED = true);
		Villager other = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 3));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		helper.assertTrue(!Stations.picksAny(new ItemStack(Items.AMETHYST_SHARD)), "a shard still picks a job with the switch off");
		helper.assertTrue(Stations.choose(player, other, new ItemStack(Items.AMETHYST_SHARD)) == InteractionResult.PASS, "the shard did something");
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(helper.getBlockState(CLUSTER).is(Blocks.AMETHYST_CLUSTER), "she picked with the switch off");
			helper.assertTrue(other.getVillagerData().getProfession() != ModVillagers.GEM_GROWER, "the other villager became a grower");
			String status = said(helper, grower);
			helper.assertTrue(status.startsWith(Language.getInstance().getOrDefault("message.aliveworkplace.gem_grower.state.off")),
				"she doesn't say she's off: " + status);
			GemGrowers.ENABLED = true;
			helper.succeed();
		});
	}

	/** Her stonecutter broken mid-job: she stops and says so, and a jobless villager by a stonecutter still becomes a Mason. */
	//$ gametest_ticks_batch AREA '600' '"gem_broken"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "gem_broken")
	public void aBrokenStonecutterStopsHer(GameTestHelper helper) {
		Leftovers.clear(helper);
		amethystBed(helper);
		Villager grower = grower(helper);
		helper.setBlock(CUTTER, Blocks.AIR);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(helper.getBlockState(CLUSTER).is(Blocks.AMETHYST_CLUSTER), "she picked with no stonecutter");
			helper.assertTrue(Stations.at(Blocks.STONECUTTER).map(s -> s.jobs().get(0).profession().get() == VillagerProfession.MASON
				&& s.byItself()).orElse(false), "the stonecutter's own job isn't the Mason's");
			helper.succeed();
		});
	}

	/** Orders: the screen lists the beds; picking one keeps only it (amethyst then isn't hers), unpicking means every bed. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void ordersPickWhichBedsSheKeeps(GameTestHelper helper) {
		Villager grower = grower(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		ChoiceMenu menu = GemGrowers.ordersMenuForTest(player, grower);
		ItemStack first = menu.icon(GemGrowers.FIRST_BED_SLOT);
		helper.assertTrue(first.is(Items.AMETHYST_CLUSTER) && first.getHoverName().getString().equals("Amethyst"),
			"the first bed button: " + first + " " + first.getHoverName().getString());
		GemBeds.Bed amethyst = GemBeds.byName(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "amethyst"));
		helper.assertTrue(amethyst != null && GemGrowers.bedsAt(Blocks.BUDDING_AMETHYST.defaultBlockState(), grower).contains(amethyst),
			"with no orders the amethyst bed isn't hers");
		GemGrowers.toggle(grower, ResourceLocation.fromNamespaceAndPath("aliveworkplace", "fire_gem"));
		helper.assertTrue(GemGrowers.bedsAt(Blocks.BUDDING_AMETHYST.defaultBlockState(), grower).isEmpty(), "ordered other beds, still keeps amethyst");
		menu.press(GemGrowers.FIRST_BED_SLOT, player);
		helper.assertTrue(GemGrowers.orders(grower).contains(amethyst.name()), "pressing the button didn't order it: " + GemGrowers.orders(grower));
		helper.assertTrue(menu.icon(GemGrowers.FIRST_BED_SLOT).hasFoil(), "the ordered bed doesn't shine");
		GemGrowers.toggle(grower, ResourceLocation.fromNamespaceAndPath("aliveworkplace", "fire_gem"));
		GemGrowers.toggle(grower, amethyst.name());
		helper.assertTrue(GemGrowers.orders(grower).isEmpty() && !ModAttachments.GEM_ORDERS.has(grower), "orders not cleared");
		helper.succeed();
	}

	/** The Done when: a malformed bed file is logged (file and field) and skipped, and the rest still load; the shipped beds load. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aMalformedBedFileIsLoggedAndSkipped(GameTestHelper helper) {
		Map<ResourceLocation, String> files = new LinkedHashMap<>();
		files.put(ResourceLocation.fromNamespaceAndPath("testpack", "gem_beds/good.json"),
			"{\"touch\": \"minecraft:budding_amethyst\", \"grows\": [\"minecraft:amethyst_cluster\"], \"ripe\": \"minecraft:amethyst_cluster\"}");
		files.put(ResourceLocation.fromNamespaceAndPath("testpack", "gem_beds/no_touch.json"),
			"{\"grows\": [\"minecraft:amethyst_cluster\"], \"ripe\": \"minecraft:amethyst_cluster\"}");
		files.put(ResourceLocation.fromNamespaceAndPath("testpack", "gem_beds/bad_ripe.json"),
			"{\"touch\": \"minecraft:stone\", \"grows\": [\"minecraft:amethyst_cluster\"], \"ripe\": \"minecraft:amethyst_cluster[stage=3]\"}");
		files.put(ResourceLocation.fromNamespaceAndPath("testpack", "gem_beds/not_json.json"), "{ \"touch\": ");
		files.put(ResourceLocation.fromNamespaceAndPath("testpack", "gem_beds/later.json"),
			"{\"touch\": \"minecraft:stone\", \"grows\": [\"minecraft:dirt\"], \"ripe\": \"minecraft:dirt\", \"requires\": [\"no_such_mod\"]}");
		List<String> problems = new ArrayList<>();
		List<GemBeds.Bed> beds = GemBeds.readAll(files, problems);
		helper.assertTrue(beds.size() == 1 && beds.get(0).name().equals(ResourceLocation.fromNamespaceAndPath("testpack", "good")),
			"loaded: " + beds);
		helper.assertTrue(problems.size() == 3, "problems: " + problems);
		helper.assertTrue(problems.get(0).contains("no_touch") && problems.get(0).contains("\"touch\""), "the first problem: " + problems.get(0));
		helper.assertTrue(problems.get(1).contains("bad_ripe") && problems.get(1).contains("ripe"), "the second problem: " + problems.get(1));
		helper.assertTrue(problems.get(2).contains("not_json"), "the third problem: " + problems.get(2));
		// The shipped beds: amethyst always; without Cobblemon none of Cobblemon's.
		List<String> shipped = GemBeds.beds().stream().map(b -> b.name().getPath()).toList();
		helper.assertTrue(shipped.contains("amethyst"), "the amethyst bed didn't load: " + shipped);
		helper.assertTrue(!shipped.contains("tumblestone") && !shipped.contains("fire_gem"), "Cobblemon's beds loaded without it: " + shipped);
		helper.succeed();
	}

	/** Every sentence she says and every bed's name are in the language file. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void herTextIsTranslated(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		String[] states = {"off", "no_stonecutter", "no_beds", "needs_planting", "tending", "picking", "planting", "fetching", "putting_away",
			"no_chest", "blank_tm"};
		for (String state : states) {
			check(missing, "message.aliveworkplace.gem_grower.state." + state);
		}
		for (String key : List.of("entity.minecraft.villager.gem_grower", "entity.minecraft.zombie_villager.gem_grower",
				"message.aliveworkplace.gem_grower.title", "message.aliveworkplace.gem_grower.not_yours", "screen.aliveworkplace.gem_grower.orders",
				"screen.aliveworkplace.gem_grower.info", "screen.aliveworkplace.gem_grower.every", "screen.aliveworkplace.gem_grower.only",
				"screen.aliveworkplace.gem_grower.on", "screen.aliveworkplace.gem_grower.off", "screen.aliveworkplace.gem_grower.planted_with",
				"station.aliveworkplace.item.aliveworkplace.gem_grower", "station.aliveworkplace.item.minecraft.mason",
				"aliveworkplace.config.gemGrowers", "aliveworkplace.config.gemGrowers.tooltip")) {
			check(missing, key);
		}
		String[] shipped = {"amethyst", "tumblestone", "sky_tumblestone", "black_tumblestone", "normal_gem", "fire_gem", "water_gem", "grass_gem",
			"electric_gem", "ice_gem", "fighting_gem", "poison_gem", "ground_gem", "flying_gem", "psychic_gem", "bug_gem", "rock_gem", "ghost_gem",
			"dragon_gem", "dark_gem", "steel_gem", "fairy_gem"};
		for (String bed : shipped) {
			check(missing, "gem_bed.aliveworkplace." + bed);
		}
		helper.assertTrue(missing.isEmpty(), "untranslated: " + missing);
		String tending = net.minecraft.network.chat.Component.translatable("message.aliveworkplace.gem_grower.state.tending", 3).getString();
		helper.assertTrue(tending.equals("Tending 3 gem beds"), "reads: " + tending);
		helper.succeed();
	}

	private static void check(List<String> missing, String key) {
		if (!Language.getInstance().has(key)) {
			missing.add(key);
		}
	}
}
