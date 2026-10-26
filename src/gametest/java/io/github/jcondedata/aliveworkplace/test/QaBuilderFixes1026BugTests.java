package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * BUG (QA lane-b-1026-0938, not registered): B513's "Left" note. A take-down cancelled with /workplace cancel tells the
 * player "Canceled the build of Test Hut. Its blueprint is still placed there: hand it back to carry on, or click the
 * ground to move it." (en_us command.aliveworkplace.cancelled; builder.md "`/workplace cancel <site>` (the blueprint
 * stays placed; hand it back to carry on)"). Handed back the plain way the line describes, the builder starts BUILDING
 * the hut again instead of carrying on taking it down (only a sneak hand-over carries on).
 */
public class QaBuilderFixes1026BugTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/**
	 * Half the hut taken down, then cancelled: the player reads the cancel line, gets the blueprint back still placed,
	 * and hands it back as told (a plain right-click). Expected: the take-down carries on (or the line tells him to sneak
	 * and doesn't call it a build). Found: the line calls it "the build", says "hand it back to carry on", and the plain
	 * hand-back starts a build over the half-taken-down hut.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaFix1026BugCancelHandBack"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaFix1026BugCancelHandBack")
	public void aCancelledTakeDownHandedBackAsTheLineSaysCarriesOnTheTakeDown(GameTestHelper helper) {
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
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaFxB"));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, QaBuilderFixes1026GameTests.placed(placement));
		QaBuilderFixes1026GameTests.handOver(c, villager, true);
		BuildSite site = Builders.activeSite(level, villager);
		helper.assertTrue(site != null && site.isDeconstruction(), "the sneak hand-over started no take-down: " + texts(c.seen()));
		boolean[] done = {false};
		helper.onEachTick(() -> {
			if (done[0] || ModAttachments.BUILDER_BAG.getOrCreate(villager).isEmpty() || BuildSiteManager.get(level).get(site.id()) == null
				|| site.placed() < 20) {
				return;
			}
			done[0] = true;
			QaStageSixGameTests.command(c, "/workplace cancel " + site.id());
			List<String> said = texts(c.seen());
			int slot = -1;
			for (int i = 0; i < c.player().getInventory().getContainerSize(); i++) {
				if (c.player().getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
					slot = i;
				}
			}
			helper.assertTrue(slot >= 0, "no blueprint came back: " + said);
			ItemStack back = c.player().getInventory().getItem(slot);
			helper.assertTrue(BlueprintItem.data(back).flatMap(BlueprintData::placement).isPresent(), "the blueprint came back unplaced");
			c.player().getInventory().setItem(slot, ItemStack.EMPTY);
			c.player().setItemInHand(InteractionHand.MAIN_HAND, back);
			c.seen().clear();
			QaBuilderFixes1026GameTests.handOver(c, villager, false); // "hand it back": a plain right-click
			BuildSite again = Builders.activeSite(level, villager);
			boolean lineSaysSneak = said.stream().anyMatch(t -> t.toLowerCase(java.util.Locale.ROOT).contains("sneak"));
			helper.assertTrue(lineSaysSneak || (again != null && again.isDeconstruction()),
				"BUG: after /workplace cancel of a take-down the player read " + said + "; handed back as told, the builder "
					+ (again == null ? "started nothing" : "started a " + (again.isDeconstruction() ? "take-down" : "BUILD of " + Blueprints.displayName(again.structure()).getString()
					+ " over the half-taken-down hut")) + " (said " + texts(c.seen()) + ")");
			helper.succeed();
		});
	}
}
