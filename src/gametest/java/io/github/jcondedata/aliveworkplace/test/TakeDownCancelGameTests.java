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
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B526: a take-down canceled half-way with /workplace cancel says it's a take-down, and its blueprint, handed back the
 * plain way the line says (even after a save and reload), carries on taking the building down instead of building it
 * again over the half-taken-down hut. Moved to a new spot, it's a plain blueprint again.
 */
public class TakeDownCancelGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final String CANCELED = "Canceled the take-down of Test Hut. Its blueprint is still placed there: hand it back to carry on taking it down, or click the ground to move it.";
	private static final String TOOLTIP = "Give it to a Builder villager to carry on taking the building down";

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	private static List<String> tooltip(ServerLevel level, ItemStack stack) {
		return stack.getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
	}

	//$ gametest_ticks_batch AREA '3000' '"takeDownCancelHandBack"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "takeDownCancelHandBack")
	public void aCanceledTakeDownHandedBackPlainlyAfterAReloadCarriesOn(GameTestHelper helper) {
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
		BlueprintData.Placement placement = QaBuilderFixes1026GameTests.standingHut(helper);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(TABLE));
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite s : List.copyOf(sites.all())) {
				if (villager.getUUID().equals(s.builder())) {
					sites.remove(s.id());
				}
			}
		});
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("tdcB"));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, QaBuilderFixes1026GameTests.placed(placement));
		QaBuilderFixes1026GameTests.handOver(c, villager, true);
		BuildSite site = Builders.activeSite(level, villager);
		helper.assertTrue(site != null && site.isDeconstruction(), "the sneak hand-over started no take-down: " + texts(c.seen()));
		int[] stage = {0};
		BuildSite[] again = {null};
		helper.onEachTick(() -> {
			if (stage[0] == 0) {
				if (ModAttachments.BUILDER_BAG.getOrCreate(villager).isEmpty() || BuildSiteManager.get(level).get(site.id()) == null || site.placed() < 20) {
					return;
				}
				stage[0] = 1;
				QaStageSixGameTests.command(c, "/workplace cancel " + site.id());
				List<String> said = texts(c.seen());
				helper.assertTrue(said.contains(CANCELED), "the cancel line isn't the take-down's: " + said);
				helper.assertTrue(Builders.activeSite(level, villager) == null, "the take-down is still on after the cancel");
				int slot = -1;
				for (int i = 0; i < c.player().getInventory().getContainerSize(); i++) {
					if (c.player().getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
						slot = i;
					}
				}
				helper.assertTrue(slot >= 0, "no blueprint came back: " + said);
				ItemStack back = c.player().getInventory().getItem(slot);
				c.player().getInventory().setItem(slot, ItemStack.EMPTY);
				List<String> lines = tooltip(level, back);
				helper.assertTrue(lines.contains(TOOLTIP) && lines.stream().noneMatch(l -> l.startsWith("Sneak while giving")),
					"the blueprint's tooltip doesn't say it carries on the take-down: " + lines);
				// a save and reload in between: the blueprint goes through its saved form
				Tag saved = back.save(level.registryAccess());
				ItemStack reloaded = ItemStack.parse(level.registryAccess(), saved).orElseThrow();
				BlueprintData data = BlueprintItem.data(reloaded).orElseThrow();
				helper.assertTrue(data.takeDown() && data.placement().equals(Optional.of(placement)),
					"after a reload the blueprint lost its take-down or its spot: " + data);
				c.player().setItemInHand(InteractionHand.MAIN_HAND, reloaded);
				QaBuilderFixes1026GameTests.handOver(c, villager, false); // "hand it back": a plain right-click
				again[0] = Builders.activeSite(level, villager);
				helper.assertTrue(again[0] != null && again[0].isDeconstruction(),
					"handed back plainly, the builder didn't carry on the take-down: " + texts(c.seen()));
				helper.assertTrue(texts(c.seen()).stream().anyMatch(t -> t.contains("will take down Test Hut")),
					"the hand-back didn't say it carries on taking it down: " + texts(c.seen()));
				helper.assertTrue(texts(c.seen()).stream().noneMatch(t -> t.contains("will build Test Hut")),
					"the hand-back says it builds: " + texts(c.seen()));
				return;
			}
			if (stage[0] == 1) {
				BuildSite now = again[0];
				if (BuildSiteManager.get(level).get(now.id()) == null || now.placed() >= 5) {
					stage[0] = 2;
					helper.assertTrue(now.isDeconstruction(), "the carried-on job turned into a build");
					helper.succeed();
				}
			}
		});
	}

	/**
	 * The take-down mark lives with its spot: kept when the spot is set again to the same place (and through a save),
	 * gone when the blueprint is moved or unplaced, and an old saved blueprint without it reads as a plain one.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aTakeDownBlueprintMovedIsAPlainOneAgain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement here = new BlueprintData.Placement(level.dimension().location(), new BlockPos(10, 64, 10), Rotation.NONE, Mirror.NONE);
		BlueprintData.Placement there = new BlueprintData.Placement(level.dimension().location(), new BlockPos(20, 64, 10), Rotation.NONE, Mirror.NONE);
		BlueprintData down = new BlueprintData(TEST_HUT, Optional.empty(), Optional.of(here)).withTakeDown(true);
		helper.assertTrue(down.withPlacement(Optional.of(here)).takeDown(), "set again to the same spot, the take-down mark went");
		helper.assertTrue(down.withLevelGround(false).withMirrored(true).takeDown(), "switching levelling or mirroring dropped the take-down mark");
		helper.assertFalse(down.withPlacement(Optional.of(there)).takeDown(), "moved to a new spot, it's still a take-down");
		helper.assertFalse(down.withPlacement(Optional.empty()).takeDown(), "unplaced, it's still a take-down");
		helper.assertFalse(down.withStructure(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "other")).takeDown(), "another drawing kept the take-down mark");
		Tag tag = BlueprintData.CODEC.encodeStart(NbtOps.INSTANCE, down).getOrThrow();
		helper.assertTrue(BlueprintData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().takeDown(), "the take-down mark wasn't saved");
		CompoundTag old = (CompoundTag) BlueprintData.CODEC.encodeStart(NbtOps.INSTANCE, down.withTakeDown(false)).getOrThrow();
		helper.assertFalse(old.contains("take_down"), "a plain blueprint saves a take-down field");
		helper.assertFalse(BlueprintData.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow().takeDown(), "an old blueprint reads as a take-down");
		ItemStack plain = BlueprintItem.create(TEST_HUT, null);
		plain.set(ModComponents.BLUEPRINT, BlueprintItem.data(plain).orElseThrow().withPlacement(Optional.of(here)));
		helper.assertFalse(tooltip(level, plain).contains(TOOLTIP), "a plain blueprint's tooltip says it carries on a take-down");
		ItemStack marked = plain.copy();
		marked.set(ModComponents.BLUEPRINT, BlueprintItem.data(plain).orElseThrow().withTakeDown(true));
		helper.assertTrue(tooltip(level, marked).contains(TOOLTIP), "a take-down blueprint's tooltip doesn't say so: " + tooltip(level, marked));
		helper.succeed();
	}
}
