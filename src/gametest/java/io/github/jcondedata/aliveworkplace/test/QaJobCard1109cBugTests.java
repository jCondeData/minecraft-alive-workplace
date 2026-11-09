package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * QA (lane c, 2026-11-09), ROADMAP 42.5: failing tests for defects in the job-status card. Not registered in
 * fabric.mod.json until the fix lands (the builder registers this class with the fix).
 */
public class QaJobCard1109cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * A Miner whose quarry waits for a pickaxe: the card's need reads "Needs A pickaxe" with a capital A in mid-sentence,
	 * because "Needs %s" wraps the Hall's stand-alone request name ("A pickaxe", also "An axe", "A fishing rod",
	 * "Seeds"). It should read "Needs a pickaxe".
	 */
	//$ gametest_batch AREA '"qaJobCard1109cPickaxeWords"'
	@GameTest(template = AREA, batch = "qaJobCard1109cPickaxeWords")
	public void qaAMinersPickaxeNeedReadsAsASentence(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(9, 2, 3), ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos station = new BlockPos(3, 2, 3);
		helper.setBlock(station, ModBlocks.MINERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		v.setNoAi(true);
		ServerLevel level = helper.getLevel();
		Jobs.employ(level, v, helper.absolutePos(station), ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
		v.setCustomName(Component.literal("Gimli"));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()),
			Optional.of(helper.absolutePos(new BlockPos(14, 1, 14))), Optional.of(helper.absolutePos(new BlockPos(16, 1, 16))), 2));
		Miners.assign(player, v, marker);
		QuarrySite site = Miners.activeSite(level, v);
		helper.assertTrue(site != null, "setup: the miner didn't take the marker");
		Leftovers.after(helper, () -> QuarrySiteManager.get(level).remove(site.id()));
		site.setStatus(QuarrySite.Status.NEEDS_PICKAXE);
		NurseCardGameTests.click(player, v, true);
		helper.assertTrue(player.containerMenu instanceof StatusCardMenu, "no card opened");
		StatusCard card = ((StatusCardMenu) player.containerMenu).card();
		List<String> lines = JobCardGameTests.lines(card);
		helper.assertTrue(lines.contains("Needs a pickaxe: ×1"), "the pickaxe need doesn't read as a sentence: " + lines);
		player.closeContainer();
		helper.succeed();
	}
}
