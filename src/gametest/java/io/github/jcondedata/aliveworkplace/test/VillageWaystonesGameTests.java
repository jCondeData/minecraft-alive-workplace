package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.ItemLore;

/**
 * ROADMAP 41.1 without the Waystones mod (the main suite never loads it): the Waystone Plaza is never offered. The
 * Steward's rule for it is loaded, but stands aside as "needs a mod", nothing fills the waystones extension, and no
 * wish of a village with a hall is the plaza, and the hall's Map lists no waystone network (41.2). The compat suite has the rest (WaystonesCompatTests).
 */
public class VillageWaystonesGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation RULE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "waystone_plaza");

	/** No Waystones: the plaza rule is skipped for the missing mod, nothing fills the extension, no wish is the plaza. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void withoutWaystonesNoPlazaIsOffered(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = new BlockPos(2, 2, 2);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		helper.assertFalse(VillageWaystones.EXTENSION.present(), "something fills the waystones extension without the mod");
		helper.assertFalse(VillageWaystones.offered(), "the plaza is offered without Waystones");
		StewardRules.Rule rule = StewardRules.all().stream().filter(r -> r.id().equals(RULE)).findFirst().orElse(null);
		helper.assertTrue(rule != null, "no Steward rule " + RULE + " is loaded");
		StewardConditions.Facts facts = StewardConditions.Facts.of(level, helper.absolutePos(hall));
		StewardWishes.Verdict verdict = StewardWishes.judge(rule, facts, StewardWishes.State.EMPTY, 0);
		helper.assertTrue(verdict.status() == StewardWishes.Status.MOD_MISSING, "the plaza rule without Waystones: " + verdict.status());
		boolean wished = StewardWishes.rank(StewardRules.all(), facts, StewardWishes.State.EMPTY, 0).stream()
			.anyMatch(w -> w.effect().blueprint().map(VillageWaystones::isPlaza).orElse(false));
		helper.assertFalse(wished, "the Steward wishes for a Waystone Plaza without Waystones");
		helper.succeed();
	}

	/** No Waystones (41.2): the hall's Map lists no waystone network, not even "none yet", and the network is empty. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void withoutWaystonesTheMapListsNoNetwork(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = new BlockPos(2, 2, 2);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(VillageWaystones.network(level, helper.absolutePos(hall), player).isEmpty(), "a network without Waystones");
		helper.assertTrue(VillageWaystones.mapLines(level, helper.absolutePos(hall), player).isEmpty(), "Map lines without Waystones");
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(hall));
		List<String> keys = menu.icon(VillageHallScreen.MAP).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream()
			.map(c -> c.getContents() instanceof TranslatableContents t ? t.getKey() : "").toList();
		helper.assertTrue(keys.contains("screen.aliveworkplace.hall.map_hint"), "the Map's lore: " + keys);
		helper.assertTrue(keys.stream().noneMatch(k -> k.startsWith("screen.aliveworkplace.hall.waystone")), "waystone lines without Waystones: " + keys);
		helper.succeed();
	}
}
