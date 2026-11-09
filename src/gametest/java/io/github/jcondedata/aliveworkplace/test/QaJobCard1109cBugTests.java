package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane c, 2026-11-09), ROADMAP 42.5: the job-status card's need lines read as sentences (B1096), while the
 * Hall's stand-alone request names stay capitalized.
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

	/**
	 * B1096's other jobs: a Lumberjack waiting for an axe, a Fisher for a fishing rod and a Farmer for seeds (posted the
	 * way their work posts them, each on the job it was hired for) read "Needs an axe", "Needs a fishing rod" and "Needs seeds" on their cards, and the
	 * Hall's stand-alone names ("An axe") are unchanged.
	 */
	//$ gametest_batch AREA '"qaJobCard1109cOtherWords"'
	@GameTest(template = AREA, batch = "qaJobCard1109cOtherWords")
	public void otherJobsNeedsReadAsSentences(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(9, 2, 3), ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = QaStageFourGameTests.player(helper, new ArrayList<>(), GameType.SURVIVAL);

		BlockPos chop = new BlockPos(2, 2, 2);
		helper.setBlock(chop, ModBlocks.CHOPPING_BLOCK);
		Villager jack = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		jack.setNoAi(true);
		Jobs.employ(level, jack, helper.absolutePos(chop), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		BlockPos o = helper.absolutePos(new BlockPos(12, 1, 2));
		ModAttachments.TREE_FARM.set(jack, new FieldJob(new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + 3, o.getY() + 3, o.getZ() + 3)));
		Requests.postTool(jack, Items.IRON_AXE, "axe", s -> s.is(Items.IRON_AXE));

		BlockPos barrel = new BlockPos(2, 2, 6);
		helper.setBlock(barrel, Blocks.BARREL);
		Villager finn = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 6));
		finn.setNoAi(true);
		Jobs.employ(level, finn, helper.absolutePos(barrel), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
		ModAttachments.FISHER_JOB.set(finn, true);
		Requests.postTool(finn, Items.FISHING_ROD, "fishing_rod", s -> s.is(Items.FISHING_ROD));

		BlockPos composter = new BlockPos(2, 2, 10);
		helper.setBlock(composter, Blocks.COMPOSTER);
		Villager bram = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 10));
		bram.setNoAi(true);
		Jobs.employ(level, bram, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
		BlockPos f = helper.absolutePos(new BlockPos(12, 1, 10));
		ModAttachments.FARM_FIELD.set(bram, new FieldJob(new BoundingBox(f.getX(), f.getY(), f.getZ(), f.getX() + 3, f.getY() + 1, f.getZ() + 3)));
		Requests.post(bram, new ItemStack(Items.WHEAT_SEEDS), 16, Component.translatable("request.aliveworkplace.seeds"), s -> s.is(Items.WHEAT_SEEDS));

		expectNeed(helper, player, jack, "Needs an axe: ×1", "An axe", "Waiting for an axe");
		expectNeed(helper, player, finn, "Needs a fishing rod: ×1", "A fishing rod", "Waiting for a fishing rod");
		expectNeed(helper, player, bram, "Needs seeds: ×16", "Seeds", "Waiting for 16× seeds");
		helper.succeed();
	}

	/**
	 * {@code worker}'s card has the line {@code line}, their Hall page says {@code waiting} (also mid-sentence), and the
	 * Hall's request list still names it {@code standalone}.
	 */
	private static void expectNeed(GameTestHelper helper, ServerPlayer player, Villager worker, String line, String standalone, String waiting) {
		NurseCardGameTests.click(player, worker, true);
		helper.assertTrue(player.containerMenu instanceof StatusCardMenu, "no card opened for " + worker.getVillagerData().getProfession());
		List<String> lines = JobCardGameTests.lines(((StatusCardMenu) player.containerMenu).card());
		player.closeContainer();
		helper.assertTrue(lines.contains(line), "expected '" + line + "' on the card: " + lines);
		ItemLore lore = VillageHallScreen.person(helper.getLevel(), helper.absolutePos(new BlockPos(9, 2, 3)), worker).get(DataComponents.LORE);
		List<String> page = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
		helper.assertTrue(page.contains(waiting), "expected '" + waiting + "' on the Hall page: " + page);
		List<Requests.Request> requests = Requests.of(helper.getLevel(), worker);
		helper.assertTrue(!requests.isEmpty() && requests.get(0).what().getString().equals(standalone),
			"the Hall's name changed: " + requests.stream().map(r -> r.what().getString()).toList());
	}
}
