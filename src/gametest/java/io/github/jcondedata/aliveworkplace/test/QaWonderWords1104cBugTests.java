package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane C, 2026-11-04; B367/B878 family, 35.x Wonders): the Foundation Stone's status lists each block a stage still
 * needs as "%s %s, %s still to bring" and "%s %s, all there" (message.aliveworkplace.wonder.status.short/.there),
 * filled with the item's singular name ({@code getDescription()}) after a count of hundreds, while the Steward's
 * shopping list and the quest lines name the same blocks through {@code Words.itemName} ("many read as many", B367).
 * Expected: a line for more than one of a block whose plural en_us.json has reads in the plural ("40 Oak Logs"). Fixed by
 * B921; the status shows the first {@code MAX_LINES} lines, so every line is read through {@code WonderStages.lineText}.
 */
public class QaWonderWords1104cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");

	/** The stone's status for the test folly's stage I, nothing in the chests: every counted block with a plural reads in it. */
	//$ gametest_batch AREA '"qaWonderWords1104c"'
	@GameTest(template = AREA, batch = "qaWonderWords1104c")
	public void theFoundationStoneNamesManyBlocksInThePlural(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(new BlockPos(14, 2, 2), ModBlocks.VILLAGE_HALL);
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity hall = helper.getBlockEntity(new BlockPos(14, 2, 2));
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(new BlockPos(2, 2, 2)));
		BlockPos hallPos = helper.absolutePos(new BlockPos(14, 2, 2));

		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "setup: the test folly isn't loaded: " + Wonders.problems());
		ItemStack plan = WonderPlanItem.of(level, hallPos, folly, Component.literal("Testholm"));
		BlueprintData data = BlueprintItem.data(plan).orElseThrow();
		BlockPos middle = helper.absolutePos(new BlockPos(9, 2, 9));
		BlockPos origin = middle.subtract(StructureTemplate.transform(new BlockPos(4, 0, 4), Mirror.NONE, Rotation.NONE, BlockPos.ZERO));
		BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(at)));
		WonderGoodsKit.stockAll(level, FOLLY, at);
		BlockPos stone = at.origin().offset(-1, 0, -1);
		helper.assertTrue(WonderStages.handOver(owner, builder, plan) == InteractionResult.SUCCESS, "setup: not handed over");
		List<WonderSite> here = WonderManager.get(level).in(hallPos);
		helper.assertTrue(here.size() == 1, "setup: the Wonder wasn't begun: " + here);
		WonderSite site = here.get(0);
		Leftovers.after(helper, () -> forget(level, site.id()));

		String status = WonderStages.statusText(level, stone).getString();
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		List<WonderStages.Line> lines = WonderStages.materials(level, site, stone);
		for (int i = 0; i < lines.size(); i++) {
			WonderStages.Line l = lines.get(i);
			// the status shows the first MAX_LINES lines (then "…and N more"); every line reads as the stone would write it
			String line = WonderStages.lineText(l).getString();
			if (i < WonderStages.MAX_LINES && !status.contains(line)) {
				wrong.add("line " + (i + 1) + " '" + line + "' isn't in the status");
			}
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(l.item());
			if (l.need() < 2 || !Language.getInstance().has("plural.aliveworkplace." + id.getNamespace() + "." + id.getPath())) {
				continue;
			}
			checked++;
			String singular = l.item().getDescription().getString();
			String plural = Words.itemName(l.item(), l.need()).getString();
			if (singular.equals(plural)) {
				continue;
			}
			if (!line.startsWith(l.need() + " " + plural + ",")) {
				wrong.add("'" + l.need() + " " + singular + "' (want '" + l.need() + " " + plural + "')");
			}
		}
		helper.assertTrue(checked > 0, "setup: no counted block of stage I has a plural in en_us.json, nothing checked: " + status);
		helper.assertTrue(wrong.isEmpty(), "the stone names many blocks in the singular: " + wrong + "; status: " + status.replace('\n', '|'));
		helper.succeed();
	}

	private static void forget(ServerLevel level, UUID wonder) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite s : List.copyOf(sites.all())) {
			if (wonder.equals(s.wonder())) {
				sites.remove(s.id());
			}
		}
		WonderManager.get(level).remove(wonder);
	}
}
