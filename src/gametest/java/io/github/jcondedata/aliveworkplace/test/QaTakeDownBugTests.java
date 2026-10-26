package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** QA (lane-b-1026-0338): taking a building down: a cancelled take-down's blueprint (B513) and a queued take-down's line (B514). */
public class QaTakeDownBugTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	private static ServerLevel rules(GameTestHelper helper) {
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
		return level;
	}

	private static BlueprintData.Placement standingHut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(HUT);
		level.getStructureManager().get(TEST_HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(7L), Block.UPDATE_ALL);
		return new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
	}

	private static ItemStack placed(BlueprintData.Placement placement) {
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, null);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(placement)));
		return blueprint;
	}

	private static Villager builder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(TABLE));
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (villager.getUUID().equals(site.builder())) {
					sites.remove(site.id());
				}
			}
		});
		return villager;
	}

	private static void handOver(QaStageFiveGameTests.Client c, Villager villager, boolean sneak) {
		c.player().setShiftKeyDown(sneak);
		QaStageSixGameTests.clickVillager(c.player(), villager, c.seen());
		c.player().setShiftKeyDown(false);
	}

	/**
	 * docs/wiki/builder.md: "`/workplace cancel <site>` (the blueprint stays placed; hand it back to carry on)"; en_us
	 * command.aliveworkplace.cancelled: "Canceled the build of %s. Its blueprint is still placed there: hand it back to
	 * carry on". A take-down cancelled with the command says exactly that, but the blueprint the player gets back has
	 * no placement: handed back as it is, the builder answers that it isn't placed.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaTakeDownBugCancel"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaTakeDownBugCancel")
	public void aCancelledTakeDownsBlueprintComesBackStillPlaced(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		BlueprintData.Placement placement = standingHut(helper);
		Villager villager = builder(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaTdbC"));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placed(placement));
		handOver(c, villager, true);
		BuildSite site = Builders.activeSite(level, villager);
		helper.assertTrue(site != null && site.isDeconstruction(), "the sneak hand-over started no take-down: " + texts(c.seen()));
		boolean[] done = {false};
		helper.onEachTick(() -> {
			if (done[0] || io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(villager).count(Items.OAK_PLANKS) < 10
				|| BuildSiteManager.get(level).get(site.id()) == null) {
				return;
			}
			done[0] = true;
			QaStageSixGameTests.command(c, "/workplace cancel " + site.id());
			String hut = Blueprints.displayName(TEST_HUT).getString();
			helper.assertTrue(texts(c.seen()).contains("Canceled the build of " + hut + ". Its blueprint is still placed there: hand it back to carry on, or click the ground to move it."),
				"/workplace cancel said " + texts(c.seen()));
			ItemStack back = ItemStack.EMPTY;
			for (int i = 0; i < c.player().getInventory().getContainerSize(); i++) {
				if (c.player().getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
					back = c.player().getInventory().getItem(i);
				}
			}
			helper.assertTrue(!back.isEmpty(), "no blueprint came back to the player");
			helper.assertTrue(BlueprintItem.data(back).flatMap(BlueprintData::placement).equals(Optional.of(placement)),
				"told \"Its blueprint is still placed there: hand it back to carry on\", but the blueprint came back placed at "
					+ BlueprintItem.data(back).flatMap(BlueprintData::placement) + " (it was " + placement.origin() + ")");
			helper.succeed();
		});
	}

	/**
	 * A take-down sneak-handed to a builder busy with another build is queued, and the player is told so. en_us
	 * message.aliveworkplace.queue.added is a build's line: "%s will build %s after %s (number %s in line). Stock the
	 * materials for it too." For a take-down that is wrong twice: nothing is built, and no materials are needed (it
	 * gives blocks back). The line should say the building will be taken down.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaTakeDownBugQueued"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaTakeDownBugQueued")
	public void aQueuedTakeDownIsAnnouncedAsATakeDown(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		BlueprintData.Placement standing = standingHut(helper);
		Villager villager = builder(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaTdbQ"));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placed(new BlueprintData.Placement(level.dimension().location(),
			helper.absolutePos(new BlockPos(11, 2, 0)), Rotation.NONE, Mirror.NONE)));
		handOver(c, villager, false);
		helper.assertTrue(Builders.activeSite(level, villager) != null, "the first hand-over started nothing: " + texts(c.seen()));
		c.seen().clear();
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placed(standing));
		handOver(c, villager, true);
		List<BuildSite> queue = Builders.queue(level, villager);
		helper.assertTrue(queue.size() == 1 && queue.get(0).isDeconstruction(), "the sneak hand-over queued " + queue + ": " + texts(c.seen()));
		List<String> said = texts(c.seen());
		helper.assertTrue(said.stream().anyMatch(t -> t.contains("take down")) && said.stream().noneMatch(t -> t.contains("Stock the materials")),
			"a queued take-down was announced as a build: " + said);
		helper.succeed();
	}
}
