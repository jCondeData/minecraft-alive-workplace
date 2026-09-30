package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Homes, tested from the spec (CHANGELOG 0.136.0, ROADMAP Milestone 20 "Homes", commit 78dee26) by the tester, not the
 * builder: "a villager's home is the finished building their bed is in; a tier II house lifts their mood (+5), tier III
 * more (+10); the hall's list says where each lives, and "What next?" suggests upgrading when more than half of the
 * village's grown-ups (three at least) sleep in first-tier buildings or in none a builder put up".
 *
 * Buildings here are only records of finished builds (what a builder leaves behind) placed high above the test area, so
 * leftover buildings that earlier tests finished on the same spot can't be mistaken for these homes. Beds are the
 * villagers' HOME memories, set and checked in one tick (the brain would drop a memory with no bed block later).
 */
public class HomesSpecGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	/** How far above the test area the recorded buildings stand. */
	private static final int SKY = 100;

	private static BlockPos sky(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(2, 2, 2)).above(SKY);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin, Rotation rotation, Mirror mirror) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), origin, rotation, mirror);
	}

	private static void sleepsAt(ServerLevel level, Villager villager, BlockPos bed) {
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
	}

	/** A blueprint of {@code size} with nothing in it, under {@code id} (only its size matters to a home). */
	private static ResourceLocation emptyBlueprint(GameTestHelper helper, String path, Vec3i size) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", path);
		// Air high above the test area, air ignored: a template with this size and no blocks
		helper.getLevel().getStructureManager().getOrCreate(id).fillFromWorld(helper.getLevel(), sky(helper).above(40), size, false, Blocks.AIR);
		return id;
	}

	private static boolean says(Moods.Mood mood, String reason) {
		return mood.good().stream().map(Component::getContents)
			.anyMatch(c -> c instanceof TranslatableContents t && t.getKey().equals("mood.aliveworkplace.reason." + reason));
	}

	/** The lore line with translation {@code key}, if the icon has one. */
	private static Optional<TranslatableContents> line(ItemStack icon, String key) {
		ItemLore lore = icon.get(DataComponents.LORE);
		if (lore == null) {
			return Optional.empty();
		}
		for (Component c : lore.lines()) {
			if (c.getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
				return Optional.of(t);
			}
		}
		return Optional.empty();
	}

	private static List<String> tipKeys(ServerLevel level, BlockPos hall) {
		return VillageAdvice.tips(level, hall).stream().map(VillageAdvice.Tip::key).toList();
	}

	/** The tip's arguments as the player reads them. */
	private static List<String> printed(VillageAdvice.Tip tip) {
		return Arrays.stream(tip.args()).map(String::valueOf).toList();
	}

	private static Optional<VillageAdvice.Tip> homesTip(ServerLevel level, BlockPos hall) {
		return VillageAdvice.tips(level, hall).stream().filter(t -> t.key().equals("homes")).findFirst();
	}

	/**
	 * The tier goes by the name the same way upgrades do ({@code <name>_2} upgrades {@code <name>}, {@code _3} upgrades
	 * {@code _2}, up to 99): a building is tier N >= 2 exactly when it upgrades tier N - 1, and tier I otherwise.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aBuildingsTierFollowsTheUpgradeChain(GameTestHelper helper) {
		List<String> names = List.of("house", "house_0", "house_1", "house_2", "house_3", "house_02", "house_98", "house_99", "house_100",
			"my_house_2", "styled/cherry/aliveworkplace/stone_house_3", "scans/steve/barn_7");
		List<String> checked = new ArrayList<>();
		for (String name : names) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("x", name);
			int tier = BlueprintUpgrades.tier(id);
			Optional<ResourceLocation> base = BlueprintUpgrades.baseOf(id);
			helper.assertTrue(tier >= 1, name + " has tier " + tier);
			helper.assertTrue((tier >= 2) == base.isPresent(), name + ": tier " + tier + " but it upgrades " + base);
			base.ifPresent(b -> helper.assertTrue(BlueprintUpgrades.tier(b) == tier - 1, name + " (tier " + tier + ") upgrades " + b + ", tier "
				+ BlueprintUpgrades.tier(b)));
			checked.add(name + "=" + tier);
		}
		helper.assertTrue(BlueprintUpgrades.tier(ResourceLocation.fromNamespaceAndPath("x", "house_99")) == 99, "house_99 isn't tier 99");
		helper.assertTrue(BlueprintUpgrades.tier(ResourceLocation.fromNamespaceAndPath("x", "barn_7")) == 7, "barn_7 isn't tier 7");
		helper.assertTrue(BlueprintUpgrades.tier(BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE_3.id(), "cherry")) == 3,
			"a Stone House III in cherry isn't tier III");
		AliveLog.info("[test] tiers: " + checked);
		helper.succeed();
	}

	/**
	 * BUG: a blueprint whose name ends in a number too big for an int ({@code house_99999999999}, e.g. a timestamp on an
	 * imported or scanned file) makes {@code BlueprintUpgrades.tier} throw NumberFormatException instead of being tier I;
	 * with a villager's bed in such a finished building, working out their mood (the hall's list, the work pace) throws.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aBlueprintNamedWithAHugeNumberIsTierOneNotACrash(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = emptyBlueprint(helper, "house_99999999999", new Vec3i(5, 4, 5));
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement placement = placement(helper, sky(helper), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		int tier;
		try {
			tier = BlueprintUpgrades.tier(id);
		} catch (RuntimeException e) {
			helper.fail("BlueprintUpgrades.tier(" + id + ") threw " + e);
			return;
		}
		helper.assertTrue(tier == 1, id + " is tier " + tier);
		sites.recordFinished(id, placement, UUID.randomUUID());
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		sleepsAt(level, villager, sky(helper).offset(2, 1, 2));
		try {
			Moods.Mood mood = Moods.work(level, villager);
			helper.assertTrue(!says(mood, "fine_home") && !says(mood, "grand_home"), "a tier I home lifts the mood: " + mood);
			helper.assertTrue(Homes.of(level, villager).map(h -> h.tier() == 1).orElse(false), "not at home in " + id + ": " + Homes.of(level, villager));
		} catch (RuntimeException e) {
			helper.fail("working out the mood of a villager whose bed is in " + id + " threw " + e);
			return;
		}
		villager.discard();
		helper.succeed();
	}

	/**
	 * BUG: a bed in the far corner of a large building isn't in it. Homes only look at buildings whose origin is within 64
	 * blocks of the bed, but a scanned building can be 48 x 48 (the Scan Tool's limit), whose far corner is 66+ blocks
	 * from its origin. A villager sleeping at the back of a big tier II house gets no home and no "fine home".
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aBedAtTheFarEndOfALargeBuildingIsAtHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int side = io.github.jcondedata.aliveworkplace.blueprint.ScanToolItem.MAX_SIDE;
		ResourceLocation id = emptyBlueprint(helper, "big_house_" + side + "_2", new Vec3i(side, 8, side));
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlockPos origin = sky(helper);
		BlueprintData.Placement placement = placement(helper, origin, Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		sites.recordFinished(id, placement, UUID.randomUUID());
		BlockPos near = origin.offset(1, 1, 1);
		BlockPos far = origin.offset(side - 1, 1, side - 1);
		helper.assertTrue(Homes.at(level, near).map(h -> h.structure().equals(id) && h.tier() == 2).orElse(false),
			"a bed by the door of " + id + " isn't at home: " + Homes.at(level, near));
		helper.assertTrue(Homes.at(level, far).map(h -> h.structure().equals(id) && h.tier() == 2).orElse(false),
			"a bed at the far end of " + id + " (" + String.format("%.1f", Math.sqrt(far.distSqr(origin))) + " blocks from its origin) isn't at home: "
				+ Homes.at(level, far));
		helper.succeed();
	}

	/**
	 * Every bed of every starter house, whichever way the house was turned or mirrored when it was placed, is at home in
	 * that house (the bed's position worked out the way the builder places blocks).
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyBedOfEveryStarterHouseIsAtHomeTurnedAnyWay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlockPos origin = sky(helper).offset(24, 0, 24);
		List<BlueprintData.Placement> used = new ArrayList<>();
		Leftovers.after(helper, () -> used.forEach(sites::forgetFinished));
		List<ResourceLocation> ids = new ArrayList<>();
		StarterBlueprints.ALL.forEach(e -> ids.add(e.id()));
		ids.add(BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE_2.id(), "cherry"));
		int houses = 0;
		int beds = 0;
		for (ResourceLocation id : ids) {
			Blueprint blueprint = BlueprintLibrary.get(level, id).orElse(null);
			helper.assertTrue(blueprint != null, "no blueprint " + id);
			List<BlockPos> heads = blueprint.blocks().stream()
				.filter(e -> e.state().getBlock() instanceof BedBlock && e.state().getValue(BedBlock.PART) == BedPart.HEAD)
				.map(Blueprint.Entry::pos).toList();
			if (heads.isEmpty()) {
				continue;
			}
			houses++;
			for (Rotation rotation : Rotation.values()) {
				for (Mirror mirror : Mirror.values()) {
					BlueprintData.Placement placement = placement(helper, origin, rotation, mirror);
					used.add(placement);
					sites.recordFinished(id, placement, UUID.randomUUID());
					for (BlockPos head : heads) {
						BlockPos bed = origin.offset(StructureTemplate.transform(head, mirror, rotation, BlockPos.ZERO));
						Optional<Homes.Home> home = Homes.at(level, bed);
						helper.assertTrue(home.map(h -> h.structure().equals(id) && h.tier() == BlueprintUpgrades.tier(id)).orElse(false),
							"the bed at " + head + " of " + id + " turned " + rotation + ", mirrored " + mirror + " isn't at home in it: " + home);
						beds++;
					}
					sites.forgetFinished(placement);
				}
			}
		}
		helper.assertTrue(houses >= 17, "only " + houses + " starter builds with beds");
		AliveLog.info("[test] " + houses + " houses, " + beds + " beds checked in every rotation and mirror");
		helper.succeed();
	}

	/**
	 * "What next?" suggests better homes only when MORE than half of the grown-ups sleep in first-tier buildings or in
	 * none a builder put up, and only with three grown-ups or more; children don't count. Exactly half: no tip.
	 */
	//$ gametest_ticks_batch AREA '100' '"theHomesTipCountsGrownUps"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "theHomesTipCountsGrownUps")
	public void theHomesTipNeedsMoreThanHalfOfThreeOrMoreGrownUps(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlockPos origin = sky(helper);
		BlueprintData.Placement placement = placement(helper, origin, Rotation.NONE, Mirror.NONE);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			sites.forgetFinished(placement);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 12));
		Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 12));
		Villager c = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 12));
		Villager d = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 12));
		Villager baby = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		baby.setAge(-24000);
		helper.runAfterDelay(2, () -> {
			sites.recordFinished(StarterBlueprints.STONE_HOUSE_2.id(), placement, UUID.randomUUID());
			// Stone House II here spans x 0-10, z 0-8 from the origin; x 20+ is outside any building
			BlockPos inside = origin.offset(2, 1, 3);
			BlockPos outside = origin.offset(20, 1, 3);
			sleepsAt(level, a, inside);
			sleepsAt(level, b, inside.east(2));
			sleepsAt(level, c, outside);
			sleepsAt(level, d, outside.east(2));
			sleepsAt(level, baby, outside.east(4));
			helper.assertTrue(Homes.of(level, a).map(h -> h.tier() == 2).orElse(false), "a isn't at home in the Stone House II: " + Homes.of(level, a));
			helper.assertTrue(Homes.of(level, c).isEmpty(), "c's bed outside is in a building: " + Homes.of(level, c));

			// 2 of 4 grown-ups in plain homes (the baby's bed doesn't count): exactly half, no tip
			helper.assertTrue(!tipKeys(level, hall).contains("homes"), "better homes suggested for exactly half the grown-ups (2 of 4, and a baby): "
				+ homesTip(level, hall).map(t -> Arrays.toString(t.args())));

			// 3 of 4: more than half
			sleepsAt(level, b, outside.east(6));
			Optional<VillageAdvice.Tip> tip = homesTip(level, hall);
			helper.assertTrue(tip.isPresent(), "no better-homes tip with 3 of 4 grown-ups in plain homes: " + tipKeys(level, hall));
			helper.assertTrue(printed(tip.get()).equals(List.of("3", "4", "" + Homes.TIER_2_MOOD, "" + Homes.TIER_3_MOOD)),
				"the tip should say 3 of 4 (+5, +10): " + Arrays.toString(tip.get().args()));

			// 3 of 3 once a is gone
			a.discard();
			tip = homesTip(level, hall);
			helper.assertTrue(tip.isPresent() && printed(tip.get()).subList(0, 2).equals(List.of("3", "3")),
				"with 3 grown-ups, all in plain homes: " + tip.map(t -> Arrays.toString(t.args())));

			// 2 grown-ups (and the baby): too few for the tip, however they live
			b.discard();
			helper.assertTrue(!tipKeys(level, hall).contains("homes"), "better homes suggested with only 2 grown-ups (and a baby): "
				+ homesTip(level, hall).map(t -> Arrays.toString(t.args())));
			AliveLog.info("[test] homes tip: none at 2/4, 3 of 4, 3 of 3, none with 2 grown-ups");
			helper.succeed();
		});
	}

	/**
	 * The hall's list says where each villager lives: "Lives in the Stone House II" (in a styled one, its styled name),
	 * "Sleeps in a house no builder put up", and for a villager with no bed only that they have none.
	 */
	//$ gametest_ticks_batch AREA '100' '"theHallSaysWhereEachLives"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "theHallSaysWhereEachLives")
	public void theHallsListSaysWhereEachVillagerLives(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlockPos origin = sky(helper);
		BlockPos origin2 = origin.offset(0, 0, 20);
		ResourceLocation cherry = BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE_3.id(), "cherry");
		BlueprintData.Placement placement = placement(helper, origin, Rotation.NONE, Mirror.NONE);
		BlueprintData.Placement placement2 = placement(helper, origin2, Rotation.NONE, Mirror.NONE);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			sites.forgetFinished(placement);
			sites.forgetFinished(placement2);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		String[] names = {"Ada", "Bram", "Cora", "Dell"};
		List<Villager> villagers = new ArrayList<>();
		for (int i = 0; i < names.length; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4 + 2 * i, 2, 12));
			v.setCustomName(Component.literal(names[i]));
			villagers.add(v);
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			sites.recordFinished(StarterBlueprints.STONE_HOUSE_2.id(), placement, UUID.randomUUID());
			sites.recordFinished(cherry, placement2, UUID.randomUUID());
			sleepsAt(level, villagers.get(0), origin.offset(2, 1, 3));
			sleepsAt(level, villagers.get(1), origin2.offset(2, 1, 3));
			sleepsAt(level, villagers.get(2), origin.offset(30, 1, 3));
			villagers.get(3).getBrain().eraseMemory(MemoryModuleType.HOME);
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			java.util.Map<String, ItemStack> icons = new java.util.HashMap<>();
			for (int slot = VillageHallScreen.FIRST_PERSON; slot < ChoiceMenu.SIZE; slot++) {
				ItemStack icon = menu.icon(slot);
				Component name = icon.get(DataComponents.CUSTOM_NAME);
				if (name != null) {
					icons.put(name.getString(), icon);
				}
			}
			for (String name : names) {
				helper.assertTrue(icons.containsKey(name), name + " isn't on the hall's list: " + icons.keySet());
			}
			Optional<TranslatableContents> ada = line(icons.get("Ada"), "screen.aliveworkplace.hall.home");
			helper.assertTrue(ada.isPresent() && ada.get().getArgs().length == 1 && ada.get().getArgs()[0].equals(Blueprints.displayName(StarterBlueprints.STONE_HOUSE_2.id())),
				"Ada (in a Stone House II) isn't said to live in the Stone House II: " + icons.get("Ada").get(DataComponents.LORE));
			Optional<TranslatableContents> bram = line(icons.get("Bram"), "screen.aliveworkplace.hall.home");
			helper.assertTrue(bram.isPresent() && bram.get().getArgs()[0].equals(Blueprints.displayName(cherry)),
				"Bram (in a cherry Stone House III) isn't said to live in it: " + icons.get("Bram").get(DataComponents.LORE));
			helper.assertTrue(line(icons.get("Cora"), "screen.aliveworkplace.hall.home_unbuilt").isPresent()
					&& line(icons.get("Cora"), "screen.aliveworkplace.hall.home").isEmpty(),
				"Cora (bed in no built house) isn't said to sleep in a house no builder put up: " + icons.get("Cora").get(DataComponents.LORE));
			ItemStack dell = icons.get("Dell");
			helper.assertTrue(line(dell, "screen.aliveworkplace.hall.no_bed").isPresent() && line(dell, "screen.aliveworkplace.hall.home").isEmpty()
					&& line(dell, "screen.aliveworkplace.hall.home_unbuilt").isEmpty(),
				"Dell (no bed) should only say there's no bed: " + dell.get(DataComponents.LORE));
			AliveLog.info("[test] the hall's list: Ada in the Stone House II, Bram in the cherry Stone House III, Cora unbuilt, Dell no bed");
			helper.succeed();
		});
	}

	/**
	 * Tier III "and up" is a grand home (+10): a tier IV house counts as grand too. The home and its mood survive the
	 * villager being saved and loaded again (a chunk unload or a restart).
	 */
	//$ gametest_ticks_batch AREA '100' '"aGrandHomeSurvivesAReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aGrandHomeSurvivesAReload")
	public void aTierFourHomeIsGrandAndSurvivesAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation manor = emptyBlueprint(helper, "manor_4", new Vec3i(9, 6, 9));
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlockPos origin = sky(helper);
		BlueprintData.Placement placement = placement(helper, origin, Rotation.NONE, Mirror.NONE);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			sites.forgetFinished(placement);
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		helper.runAfterDelay(2, () -> {
			BlockPos bed = origin.offset(4, 1, 4);
			sleepsAt(level, villager, bed);
			Moods.Mood plain = Moods.work(level, villager);
			sites.recordFinished(manor, placement, UUID.randomUUID());
			Moods.Mood grand = Moods.work(level, villager);
			helper.assertTrue(grand.score() == Math.min(100, plain.score() + Homes.TIER_3_MOOD) && says(grand, "grand_home") && !says(grand, "fine_home"),
				"a tier IV home: " + plain.score() + " -> " + grand);

			CompoundTag tag = new CompoundTag();
			villager.saveWithoutId(tag);
			villager.discard();
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(tag);
			level.addFreshEntity(copy);
			helper.assertTrue(Homes.of(level, copy).map(h -> h.structure().equals(manor) && h.tier() == 4).orElse(false),
				"after a reload the villager isn't at home in " + manor + ": " + Homes.of(level, copy));
			Moods.Mood reloaded = Moods.work(level, copy);
			helper.assertTrue(says(reloaded, "grand_home"), "after a reload no grand home: " + reloaded);
			AliveLog.info("[test] tier IV home: mood " + plain.score() + " -> " + grand.score() + ", grand home kept after a reload");
			copy.discard();
			helper.succeed();
		});
	}

	/** Three grown-ups sleeping in no building a builder put up, round a hall; returns the hall. */
	private static BlockPos threeInPlainHomes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (int i = 0; i < 3; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4 + 2 * i, 2, 12));
			sleepsAt(level, v, sky(helper).offset(30 + 2 * i, 1, 3));
		}
		return helper.absolutePos(HALL);
	}

	/**
	 * BUG: the better-homes tip's second line says "tier II makes its people happier by 3, tier III by 3" (the counts)
	 * instead of "by 5, tier III by 10": {@code Tip.how()} gets the same arguments as the title (plain, grown-ups, 5, 10),
	 * and its two {@code %s} take the first two.
	 */
	//$ gametest_ticks_batch AREA '100' '"theHomesTipSaysHowMuchHappier"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "theHomesTipSaysHowMuchHappier")
	public void theHomesTipSaysHowMuchHappierBetterHomesMake(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = threeInPlainHomes(helper);
		helper.runAfterDelay(1, () -> {
			VillageAdvice.Tip tip = homesTip(level, hall).orElse(null);
			helper.assertTrue(tip != null, "no better-homes tip for 3 grown-ups in plain homes: " + tipKeys(level, hall));
			String title = tip.title().getString();
			String how = tip.how().getString();
			AliveLog.info("[test] homes tip: \"" + title + "\" / \"" + how + "\"");
			helper.assertTrue(title.equals("Better homes for 3 of 3 villagers"), "the tip's title: " + title);
			helper.assertTrue(how.contains("happier by " + Homes.TIER_2_MOOD + ",") && how.endsWith("tier III by " + Homes.TIER_3_MOOD),
				"the tip should say tier II makes them happier by " + Homes.TIER_2_MOOD + ", tier III by " + Homes.TIER_3_MOOD + ": " + how);
			helper.succeed();
		});
	}

	/**
	 * BUG (from 0.134.0, not this change; found while testing the tip above): the rank tip's second line reads
	 * "Village more villagers, N more finished buildings, M more research levels": {@code rank.how} gets the title's
	 * arguments too, so the rank's name stands where the number of villagers belongs and every number moves one along.
	 */
	//$ gametest_ticks_batch AREA '100' '"theRankTipSaysHowMany"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "theRankTipSaysHowMany")
	public void theRankTipSaysHowManyMoreVillagers(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = threeInPlainHomes(helper);
		helper.runAfterDelay(1, () -> {
			VillageAdvice.Tip tip = VillageAdvice.tips(level, hall).stream().filter(t -> t.key().equals("rank")).findFirst().orElse(null);
			helper.assertTrue(tip != null, "no rank tip for a new village: " + tipKeys(level, hall));
			String how = tip.how().getString();
			AliveLog.info("[test] rank tip: \"" + tip.title().getString() + "\" / \"" + how + "\"");
			helper.assertTrue(how.matches("\\d+ more villagers, \\d+ more finished buildings, \\d+ more research levels"),
				"the rank tip should count what's missing: " + how);
			helper.succeed();
		});
	}

	/** Log lines the report can grep for ("[test] ..."). */
	private static final class AliveLog {
		private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

		static void info(String message) {
			LOG.info(message);
		}
	}
}
