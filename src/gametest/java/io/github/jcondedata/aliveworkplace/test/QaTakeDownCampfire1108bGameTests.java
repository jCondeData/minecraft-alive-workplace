package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * QA (lane-b-1108-0638) for taking a build down (docs/wiki/builder.md: "A placed Blueprint sneak-handed over its finished
 * building: take it down"; it takes down only what the blueprint put there, and a campfire is taken because "it drops
 * what's cooking on it", MaterialRules.isProtected). A player cooking on a build's campfire (a chimney's, a camp's) must
 * get that food back when the build is taken down, as when they break the campfire themselves: none of it may vanish.
 * Control: the build's own materials (the planks and the campfire) come back.
 */
public class QaTakeDownCampfire1108bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos LEAD = new BlockPos(3, 2, 3);
	private static final BlockPos BUILD = new BlockPos(6, 2, 6);

	//$ gametest_ticks_batch AREA '1200' '"qa1108bTakeDownCampfire"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa1108bTakeDownCampfire")
	public void foodCookingOnACampfireComesBackWhenTheBuildIsTakenDown(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		var rules = level.getGameRules();
		int delay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		boolean help = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		rules.getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> {
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
		});

		// The build: a plank with a campfire on it, saved as a blueprint and left standing (as built).
		helper.setBlock(BUILD, Blocks.OAK_PLANKS);
		helper.setBlock(BUILD.above(), Blocks.CAMPFIRE);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1108b_campfire_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(BUILD), new Vec3i(1, 2, 1), false, Blocks.STRUCTURE_VOID);
		CampfireBlockEntity campfire = (CampfireBlockEntity) helper.getBlockEntity(BUILD.above());
		helper.assertTrue(campfire.placeFood(null, new ItemStack(Items.BEEF), 1_000_000), "the beef didn't go on the campfire");
		helper.assertTrue(campfire.placeFood(null, new ItemStack(Items.SALMON), 1_000_000), "the salmon didn't go on the campfire");
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(BUILD), Rotation.NONE, Mirror.NONE);

		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = (Container) helper.getBlockEntity(CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, LEAD);
		Builders.employ(level, villager, helper.absolutePos(TABLE));
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite s : List.copyOf(sites.all())) {
				if (villager.getUUID().equals(s.builder())) {
					sites.remove(s.id());
				}
			}
		});
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaTdC"));
		ItemStack blueprint = BlueprintItem.create(id, null);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(placement)));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		c.player().setShiftKeyDown(true);
		QaStageSixGameTests.clickVillager(c.player(), villager, c.seen());
		c.player().setShiftKeyDown(false);
		BuildSite site = Builders.activeSite(level, villager);
		helper.assertTrue(site != null && site.isDeconstruction(), "the sneak hand-over started no take-down: "
			+ c.seen().stream().map(x -> x.getString()).toList());
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null && helper.getBlockState(BUILD.above()).isAir()
				&& helper.getBlockState(BUILD).isAir(), "the take-down isn't finished: " + helper.getBlockState(BUILD) + ", " + helper.getBlockState(BUILD.above()));
			helper.assertTrue(everywhere(helper, chest, villager, Items.OAK_PLANKS) == 1 && everywhere(helper, chest, villager, Items.CAMPFIRE) == 1,
				"the build's own materials didn't come back: " + everywhere(helper, chest, villager, Items.OAK_PLANKS) + " planks, "
					+ everywhere(helper, chest, villager, Items.CAMPFIRE) + " campfires");
			int beef = everywhere(helper, chest, villager, Items.BEEF) + everywhere(helper, chest, villager, Items.COOKED_BEEF);
			int salmon = everywhere(helper, chest, villager, Items.SALMON) + everywhere(helper, chest, villager, Items.COOKED_SALMON);
			helper.assertTrue(beef == 1 && salmon == 1, "the food cooking on the campfire vanished when it was taken down: " + beef + " beef, "
				+ salmon + " salmon found in the chest, the builder's bag and on the ground (expected 1 and 1)");
		});
	}

	/** {@code item} in the chest, the builder's bag and lying on the ground round the test. */
	private static int everywhere(GameTestHelper helper, Container chest, Villager villager, Item item) {
		int n = chest.countItem(item) + ModAttachments.BUILDER_BAG.getOrCreate(villager).count(item);
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}
}
