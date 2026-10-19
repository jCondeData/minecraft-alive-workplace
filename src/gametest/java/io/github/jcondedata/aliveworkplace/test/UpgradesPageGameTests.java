package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.UpgradesPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * The Village Hall's Upgrades page (ROADMAP 36.14), through the hall's own screen: the list of finished builds with a next
 * level (with level, cost and space), an upgrade with its space free built by the village's Builder, one blocked by
 * another build or a fence (what and where, and nothing ordered), one over natural ground only (it goes ahead and the
 * Builder clears it), a build with no next level left off, a house's residents keeping their beds through the upgrade,
 * and the order kept through a save and reload.
 */
public class UpgradesPageGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation HUT_2 = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut_2");
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	private static final BlockPos HALL = new BlockPos(15, 2, 11);
	/** The finished Test Hut: x 3-7, y 2-5, z 3-7; its level II adds the layer at y = 6 (a lantern in the middle). */
	private static final BlockPos HUT_ORIGIN = new BlockPos(3, 2, 3);
	private static final int ADDED_Y = 6;
	private static final BlockPos LANTERN = new BlockPos(5, ADDED_Y, 5);
	private static final BlockPos BENCH = new BlockPos(13, 2, 3);
	private static final BlockPos CHEST = new BlockPos(13, 2, 5);
	private static final BlockPos BUILDER = new BlockPos(12, 2, 4);

	private record Village(ServerLevel level, BlockPos hall, BlueprintData.Placement hut, Villager builder) {
	}

	/** The hall, a finished Test Hut standing, and a Builder at his bench with lanterns in his chest. */
	private static Village stage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement hut = placement(helper, HUT_ORIGIN);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			sites.forgetFinished(hut);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (site.placement().equals(hut)) {
					sites.remove(site.id());
				}
			}
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(HUT_ORIGIN);
		level.getStructureManager().get(HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		sites.recordFinished(HUT, hut, UUID.randomUUID());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.LANTERN, 4));
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		return new Village(level, helper.absolutePos(HALL), hut, builder);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos rel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
	}

	private static ChoiceMenu openPage(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		int tab = HallPages.slot(UpgradesPage.PAGE);
		helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW, "the Upgrades page has no tab: " + tab);
		helper.assertTrue(menu.icon(tab).is(Items.BRICKS), "the tab: " + menu.icon(tab));
		menu.press(tab, player);
		helper.assertTrue(menu.icon(4).is(Items.BRICKS), "the page's header: " + menu.icon(4));
		return menu;
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	/** The site building the hut's level II, if any. */
	private static Optional<BuildSite> upgradeSite(Village v) {
		return BuildSiteManager.get(v.level()).all().stream().filter(s -> s.placement().equals(v.hut()) && !s.isDone()).findFirst();
	}

	/** The hut finished as its level II: recorded, no site open, the lantern up and the added layer otherwise clear. */
	private static void assertUpgraded(GameTestHelper helper, Village v) {
		helper.assertTrue(upgradeSite(v).isEmpty(), "still building: " + upgradeSite(v).map(s -> s.stage() + " " + s.status() + " missing " + s.missing()));
		helper.assertTrue(BuildSiteManager.get(v.level()).finishedIn(v.level()).stream()
			.anyMatch(f -> f.placement().equals(v.hut()) && f.structure().equals(HUT_2)), "the hut isn't recorded as its level II");
		helper.assertBlockPresent(Blocks.LANTERN, LANTERN);
	}

	/** The page: the hut with its level (I, next II), where it is, the cost (one lantern), the free space and the click. */
	//$ gametest_ticks_batch AREA '100' '"upgradesAreListed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "upgradesAreListed")
	public void upgradesAreListed(GameTestHelper helper) {
		Village v = stage(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			List<UpgradesPage.Upgrade> upgrades = UpgradesPage.upgrades(v.level(), v.hall());
			helper.assertTrue(upgrades.size() == 1 && upgrades.get(0).next().equals(HUT_2), "upgrades: " + upgrades);
			UpgradesPage.Upgrade hut = upgrades.get(0);
			helper.assertTrue(hut.free() && hut.blocker() == null && hut.toClear() == 0, "the hut's space isn't free: " + hut);
			helper.assertTrue(hut.total() == 1 && hut.cost().size() == 1 && hut.cost().get(0).getKey() == Items.LANTERN, "the cost: " + hut.cost());
			ChoiceMenu menu = openPage(helper, player);
			ItemStack row = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(row.is(Items.BRICKS), "the hut's row: " + row);
			helper.assertTrue(row.getHoverName().getString().equals(Blueprints.displayName(HUT).getString()), "its name: " + row.getHoverName().getString());
			List<String> lines = lore(row);
			String level = Component.translatable("screen.aliveworkplace.upgrades.level", "I", "II", Blueprints.displayName(HUT_2)).getString();
			helper.assertTrue(lines.contains(level), "its level (" + level + "): " + lines);
			String where = VillageHallScreen.where(v.hall(), v.hut().origin()).getString();
			helper.assertTrue(lines.contains(where), "where (" + where + "): " + lines);
			String cost = Component.translatable("screen.aliveworkplace.upgrades.cost.one", 1,
				Component.translatable("screen.aliveworkplace.upgrades.item", 1, Items.LANTERN.getDescription())).getString();
			helper.assertTrue(lines.contains(cost), "the cost (" + cost + "): " + lines);
			helper.assertTrue(lines.contains(Component.translatable("screen.aliveworkplace.upgrades.free").getString()), "the space: " + lines);
			helper.assertTrue(lines.contains(Component.translatable("screen.aliveworkplace.upgrades.click").getString()), "the click: " + lines);
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW + 1).isEmpty(), "a second row: " + menu.icon(VillageHallScreen.FIRST_ROW + 1));
			helper.succeed();
		});
	}

	/** A build with no next level (a finished Test Hut II: there's no III) isn't listed, and can't be ordered. */
	//$ gametest_ticks_batch AREA '100' '"aBuildWithNoNextLevelIsNotListed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aBuildWithNoNextLevelIsNotListed")
	public void aBuildWithNoNextLevelIsNotListed(GameTestHelper helper) {
		Village v = stage(helper);
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		sites.recordFinished(HUT_2, v.hut(), UUID.randomUUID()); // the hut already upgraded
		BlueprintData.Placement top = placement(helper, new BlockPos(3, 2, 13));
		sites.recordFinished(HUT_2, top, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(top));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(UpgradesPage.upgrades(v.level(), v.hall()).isEmpty(), "listed: " + UpgradesPage.upgrades(v.level(), v.hall()));
			ChoiceMenu menu = openPage(helper, player);
			ItemStack none = menu.icon(VillageHallScreen.FIRST_ROW + 4);
			helper.assertTrue(none.is(Items.PAPER) && none.getHoverName().getString().equals(Component.translatable("screen.aliveworkplace.upgrades.none").getString()),
				"the empty page: " + none);
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).isEmpty(), "a row: " + menu.icon(VillageHallScreen.FIRST_ROW));
			helper.assertTrue(UpgradesPage.order(v.level(), v.hall(), player, top) == UpgradesPage.Outcome.GONE, "a build with no next level was ordered");
			helper.assertTrue(upgradeSite(v).isEmpty(), "a site was opened");
			helper.succeed();
		});
	}

	/** The space free: a click sends the Builder, who builds the level II over the hut; the row shows it under way meanwhile. */
	//$ gametest_ticks_batch AREA '2400' '"aFreeUpgradeIsBuilt"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "aFreeUpgradeIsBuilt")
	public void aFreeUpgradeIsBuilt(GameTestHelper helper) {
		Village v = stage(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPage(helper, player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			BuildSite site = upgradeSite(v).orElse(null);
			helper.assertTrue(site != null && site.structure().equals(HUT_2), "no site for the level II: " + site);
			helper.assertTrue(v.builder().getUUID().equals(site.builder()), "not the village's Builder: " + site.builder());
			helper.assertTrue(ModAttachments.BUILDER_JOB.has(v.builder()) && ModAttachments.BUILDER_JOB.get(v.builder()).siteId().equals(site.id()),
				"the Builder wasn't set to it");
			helper.assertTrue(menu.icon(4).is(Items.BRICKS), "not back on the page: " + menu.icon(4));
			ItemStack row = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(row.is(Items.SCAFFOLDING) && lore(row).contains(Component.translatable("screen.aliveworkplace.upgrades.building").getString()),
				"the row doesn't say it's under way: " + lore(row));
			helper.assertTrue(UpgradesPage.order(v.level(), v.hall(), player, v.hut()) == UpgradesPage.Outcome.BUILDING, "ordered twice");
			helper.assertTrue(BuildSiteManager.get(v.level()).all().stream().filter(s -> s.placement().equals(v.hut())).count() == 1, "two sites");
			helper.succeedWhen(() -> assertUpgraded(helper, v));
		});
	}

	/**
	 * Blocked: another build's block where the level II goes (the row names the build, the block and where; a click
	 * orders nothing), then, with that build gone, a fence nobody's build owns (named with where); with that gone, free.
	 */
	//$ gametest_ticks_batch AREA '100' '"aBlockedUpgradeSaysWhatIsInTheWay"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aBlockedUpgradeSaysWhatIsInTheWay")
	public void aBlockedUpgradeSaysWhatIsInTheWay(GameTestHelper helper) {
		Village v = stage(helper);
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		BlueprintData.Placement pillar = placement(helper, new BlockPos(7, 2, 7));
		sites.recordFinished(PILLAR, pillar, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(pillar));
		BlockPos corner = new BlockPos(7, ADDED_Y, 7);
		helper.setBlock(corner, Blocks.STONE_BRICKS);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			UpgradesPage.Upgrade hut = UpgradesPage.upgrades(v.level(), v.hall()).get(0);
			helper.assertTrue(hut.blocker() != null && hut.blocker().pos().equals(helper.absolutePos(corner)) && hut.blocker().building() != null
				&& hut.blocker().building().getString().equals(Blueprints.displayName(PILLAR).getString()), "not blocked by the pillar: " + hut.blocker());
			ChoiceMenu menu = openPage(helper, player);
			ItemStack row = menu.icon(VillageHallScreen.FIRST_ROW);
			BlockPos at = helper.absolutePos(corner);
			String says = Component.translatable("screen.aliveworkplace.upgrades.blocked_by", Blueprints.displayName(PILLAR), Blocks.STONE_BRICKS.getName(),
				at.getX(), at.getY(), at.getZ()).getString();
			helper.assertTrue(row.is(Items.BARRIER) && lore(row).contains(says), "the row (" + says + "): " + lore(row));
			helper.assertTrue(!lore(row).contains(Component.translatable("screen.aliveworkplace.upgrades.click").getString()), "a blocked row offers the click");
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(upgradeSite(v).isEmpty() && !ModAttachments.BUILDER_JOB.has(v.builder()), "a blocked upgrade was ordered");
			helper.assertBlockPresent(Blocks.STONE_BRICKS, corner);

			// the pillar taken down, a fence put up in the same corner: a built block, of no build
			sites.forgetFinished(pillar);
			helper.setBlock(corner, Blocks.OAK_FENCE);
			UpgradesPage.Upgrade fenced = UpgradesPage.upgrades(v.level(), v.hall()).get(0);
			helper.assertTrue(fenced.blocker() != null && fenced.blocker().building() == null && fenced.blocker().state().is(Blocks.OAK_FENCE),
				"not blocked by the fence: " + fenced.blocker());
			String fence = Component.translatable("screen.aliveworkplace.upgrades.blocked", Blocks.OAK_FENCE.getName(), at.getX(), at.getY(), at.getZ()).getString();
			helper.assertTrue(lore(UpgradesPage.icon(v.level(), v.hall(), fenced)).contains(fence), "the fence's row (" + fence + ")");
			helper.assertTrue(UpgradesPage.order(v.level(), v.hall(), player, v.hut()) == UpgradesPage.Outcome.BLOCKED, "the fenced upgrade was ordered");
			// a path is built too
			helper.setBlock(corner, Blocks.DIRT_PATH);
			helper.assertTrue(UpgradesPage.upgrades(v.level(), v.hall()).get(0).blocker() != null, "a path doesn't block it");

			// water is natural (checked before it can flow: the same tick)
			helper.setBlock(corner, Blocks.WATER);
			helper.assertTrue(UpgradesPage.upgrades(v.level(), v.hall()).get(0).free(), "water blocks it");
			helper.setBlock(corner, Blocks.AIR);
			helper.assertTrue(UpgradesPage.upgrades(v.level(), v.hall()).get(0).free(), "still blocked with the corner clear");
			helper.succeed();
		});
	}

	/** Natural ground only where the level II goes (grass, dirt, stone, gravel, sand, snow, logs, leaves): free; the Builder clears it all. */
	//$ gametest_ticks_batch AREA '2400' '"naturalGroundDoesNotBlockAnUpgrade"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "naturalGroundDoesNotBlockAnUpgrade")
	public void naturalGroundDoesNotBlockAnUpgrade(GameTestHelper helper) {
		Village v = stage(helper);
		BlockState[] ground = {Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(),
			Blocks.GRAVEL.defaultBlockState(), Blocks.SAND.defaultBlockState()};
		int placed = 0;
		for (int x = 3; x <= 7; x++) {
			for (int z = 3; z <= 7; z++) {
				boolean edge = x == 3 || x == 7 || z == 3 || z == 7;
				BlockState state = edge ? ground[(x + z) % ground.length]
					: x == 5 && z == 5 ? Blocks.SNOW_BLOCK.defaultBlockState()
					: (x + z) % 2 == 0 ? Blocks.OAK_LOG.defaultBlockState() : Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
				helper.setBlock(new BlockPos(x, ADDED_Y, z), state);
				placed++;
			}
		}
		int layer = placed;
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			UpgradesPage.Upgrade hut = UpgradesPage.upgrades(v.level(), v.hall()).get(0);
			helper.assertTrue(hut.free() && hut.blocker() == null, "natural ground blocks it: " + hut.blocker());
			helper.assertTrue(hut.toClear() == layer, "blocks to clear: " + hut.toClear() + " of " + layer);
			ChoiceMenu menu = openPage(helper, player);
			ItemStack row = menu.icon(VillageHallScreen.FIRST_ROW);
			String clear = Component.translatable("screen.aliveworkplace.upgrades.free_clear", layer).getString();
			helper.assertTrue(row.is(Items.BRICKS) && lore(row).contains(clear), "the row (" + clear + "): " + lore(row));
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(upgradeSite(v).isPresent(), "the upgrade over natural ground wasn't ordered");
			helper.succeedWhen(() -> {
				assertUpgraded(helper, v);
				for (int x = 3; x <= 7; x++) {
					for (int z = 3; z <= 7; z++) {
						BlockPos p = new BlockPos(x, ADDED_Y, z);
						if (!p.equals(LANTERN)) {
							helper.assertTrue(helper.getBlockState(p).isAir(), "not cleared at " + p + ": " + helper.getBlockState(p));
						}
					}
				}
			});
		});
	}

	/** The hut's two residents keep their beds: the upgrade goes up around them, the beds stand and are still their homes, now level II. */
	//$ gametest_ticks_batch AREA '2400' '"residentsKeepTheirBedsThroughAnUpgrade"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "residentsKeepTheirBedsThroughAnUpgrade")
	public void residentsKeepTheirBedsThroughAnUpgrade(GameTestHelper helper) {
		Village v = stage(helper);
		ServerLevel level = v.level();
		BlockPos[] heads = {new BlockPos(4, 3, 5), new BlockPos(6, 3, 5)};
		for (BlockPos head : heads) {
			helper.setBlock(head.north(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
			helper.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		}
		List<Villager> residents = List.of(helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 12)), helper.spawn(EntityType.VILLAGER, new BlockPos(11, 2, 14)));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			for (int i = 0; i < heads.length; i++) {
				BlockPos head = helper.absolutePos(heads[i]);
				helper.assertTrue(level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(head), head, 1).isPresent(), "setup: bed " + i + " has no home POI");
				residents.get(i).getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
				helper.assertTrue(Homes.of(level, residents.get(i)).map(h -> h.tier() == 1).orElse(false), "setup: resident " + i + " not at home in the hut");
			}
			helper.assertTrue(UpgradesPage.upgrades(level, v.hall()).get(0).free(), "the residents' beds block the upgrade");
			ChoiceMenu menu = openPage(helper, player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(upgradeSite(v).isPresent(), "the upgrade of the lived-in hut wasn't ordered");
			helper.succeedWhen(() -> {
				assertUpgraded(helper, v);
				for (int i = 0; i < heads.length; i++) {
					BlockPos head = helper.absolutePos(heads[i]);
					helper.assertBlockPresent(Blocks.RED_BED, heads[i]);
					helper.assertBlockPresent(Blocks.RED_BED, heads[i].north());
					Villager resident = residents.get(i);
					helper.assertTrue(resident.getBrain().getMemory(MemoryModuleType.HOME).map(g -> g.pos().equals(head)).orElse(false),
						"resident " + i + " lost their bed: " + resident.getBrain().getMemory(MemoryModuleType.HOME));
					helper.assertTrue(Homes.of(level, resident).map(h -> h.tier() == 2 && h.structure().equals(HUT_2)).orElse(false),
						"resident " + i + "'s home isn't the level II: " + Homes.of(level, resident));
				}
			});
		});
	}

	/** The order is the build site: saved and loaded again it's still the level II for the same Builder, and the Builder's job too. */
	//$ gametest_ticks_batch AREA '100' '"anOrderedUpgradeSurvivesAReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "anOrderedUpgradeSurvivesAReload")
	public void anOrderedUpgradeSurvivesAReload(GameTestHelper helper) {
		Village v = stage(helper);
		ServerLevel level = v.level();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(UpgradesPage.order(level, v.hall(), player, v.hut()) == UpgradesPage.Outcome.STARTED, "setup: not ordered");
			BuildSite site = upgradeSite(v).orElseThrow();
			CompoundTag tag = BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess());
			BuildSiteManager loaded = BuildSiteManager.load(tag, level.registryAccess());
			BuildSite again = loaded.get(site.id());
			helper.assertTrue(again != null && again.structure().equals(HUT_2) && again.placement().equals(v.hut())
				&& v.builder().getUUID().equals(again.builder()) && !again.isDone(), "after a reload the site: " + again);
			helper.assertTrue(loaded.finishedIn(level).stream().anyMatch(f -> f.placement().equals(v.hut()) && f.structure().equals(HUT)),
				"after a reload the hut isn't there as level I");
			CompoundTag saved = new CompoundTag();
			v.builder().saveWithoutId(saved);
			v.builder().discard();
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(saved);
			level.addFreshEntity(copy);
			BuilderJob job = ModAttachments.BUILDER_JOB.get(copy);
			helper.assertTrue(job != null && job.siteId().equals(site.id()), "after a reload the Builder's job: " + job);
			ChoiceMenu menu = openPage(helper, player);
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.SCAFFOLDING), "after a reload the row: " + menu.icon(VillageHallScreen.FIRST_ROW));
			helper.succeed();
		});
	}

	/** Every sentence the page and its clicks show is written. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void upgradesWordsAreWritten(GameTestHelper helper) {
		Language lang = Language.getInstance();
		for (String key : List.of("screen.aliveworkplace.upgrades", "screen.aliveworkplace.upgrades.count", "screen.aliveworkplace.upgrades.count.one",
			"screen.aliveworkplace.upgrades.hint", "screen.aliveworkplace.upgrades.title", "screen.aliveworkplace.upgrades.about",
			"screen.aliveworkplace.upgrades.space", "screen.aliveworkplace.upgrades.none", "screen.aliveworkplace.upgrades.none_hint",
			"screen.aliveworkplace.upgrades.more", "screen.aliveworkplace.upgrades.level", "screen.aliveworkplace.upgrades.cost",
			"screen.aliveworkplace.upgrades.cost.one", "screen.aliveworkplace.upgrades.cost.more", "screen.aliveworkplace.upgrades.cost.none",
			"screen.aliveworkplace.upgrades.item", "screen.aliveworkplace.upgrades.free", "screen.aliveworkplace.upgrades.free_clear",
			"screen.aliveworkplace.upgrades.free_clear.one", "screen.aliveworkplace.upgrades.blocked", "screen.aliveworkplace.upgrades.blocked_by",
			"screen.aliveworkplace.upgrades.building", "screen.aliveworkplace.upgrades.click", "message.aliveworkplace.upgrades.started",
			"message.aliveworkplace.upgrades.queued", "message.aliveworkplace.upgrades.blocked", "message.aliveworkplace.upgrades.building",
			"message.aliveworkplace.upgrades.not_allowed", "message.aliveworkplace.upgrades.no_builder", "message.aliveworkplace.upgrades.gone",
			"chronicle.aliveworkplace.upgrade_ordered")) {
			helper.assertTrue(lang.has(key), "not written: " + key);
		}
		helper.assertTrue(lang.getOrDefault("screen.aliveworkplace.upgrades.blocked_by").split("%s", -1).length == 6, "where a blocked row says");
		helper.succeed();
	}
}
