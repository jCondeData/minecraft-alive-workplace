package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.craft.LuxuryWork;
import io.github.jcondedata.aliveworkplace.jeweller.MegaStones;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.LeaderBracelets;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * ROADMAP 38.6 without Mega Showdown: no Mega recipes load, the Miner keeps everything, no bracelet is given and nothing
 * is saved; and the luxury recipe format's new parts (an output named by a tag and a chooser, {@code keep}, {@code when})
 * read and fail as they should. With Mega Showdown: {@code MegaStoneCompatTests}.
 */
public class MegaStoneGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** Without Mega Showdown the Mega recipes aren't loaded (quietly), and the Jeweller's list is today's. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void withoutMegaShowdownNothingChanges(GameTestHelper helper) {
		helper.assertTrue(!MegaBracelets.MOD, "this suite runs without Mega Showdown");
		helper.assertTrue(LuxuryRecipes.all().keySet().stream().noneMatch(id -> id.getPath().startsWith("mega_")),
			"Mega recipes loaded without Mega Showdown: " + LuxuryRecipes.all().keySet());
		helper.assertTrue(LuxuryRecipes.forJob(ModVillagers.JEWELLER, 5).size() == 3, "the Jeweller's recipes: " + LuxuryRecipes.forJob(ModVillagers.JEWELLER, 5));
		helper.assertTrue(!MegaStones.on() && !LeaderBracelets.on(), "Mega Stones or bracelets on without Mega Showdown");
		helper.assertTrue(!MegaStones.forTheJeweller(new ItemStack(Items.DIAMOND)) && !MegaStones.forTheJeweller(new ItemStack(Items.EMERALD_ORE)),
			"the Miner takes things to the store for the Jeweller");
		helper.succeed();
	}

	/** Without Mega Showdown a hall's round notes no Leader village and beating a Leader gives nothing and marks nothing. */
	//$ gametest_batch AREA '"megaNoBracelet"'
	@GameTest(template = AREA, batch = "megaNoBracelet")
	public void withoutMegaShowdownNoBracelet(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager leader = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		leader.setNoAi(true);
		leader.setVillagerData(leader.getVillagerData().setProfession(ModVillagers.TRAINER_LEADER).setLevel(4));
		BlockPos hall = helper.absolutePos(new BlockPos(4, 2, 4));
		LeaderBracelets.round(helper.getLevel(), hall, List.of(leader));
		helper.assertTrue(!LeaderBracelets.hasLeader(helper.getLevel().getServer(), GlobalPos.of(helper.getLevel().dimension(), hall)),
			"a Leader village noted without Mega Showdown");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		LeaderBracelets.beaten(helper.getLevel(), leader, player);
		helper.assertTrue(LeaderBracelets.beatenBy(helper.getLevel().getServer(), player.getUUID()).isEmpty(), "a beaten village noted without Mega Showdown");
		helper.getLevel().getServer().getPlayerList().remove(player);
		leader.discard();
		helper.succeed();
	}

	private static LuxuryRecipes.Recipe read(String json) {
		return LuxuryRecipes.read(AliveWorkplace.id("test_tag_output"), JsonParser.parseString(json));
	}

	private static boolean fails(String json, String says) {
		try {
			read(json);
			return false;
		} catch (JsonSyntaxException e) {
			return e.getMessage().contains(says);
		}
	}

	/**
	 * The format: an output named by a tag and a chooser makes whatever of the chooser's picks are in the tag, kept in
	 * {@code keep} each; a {@code when} switch that's off makes nothing; an unknown chooser or switch is refused naming it.
	 */
	//$ gametest_batch AREA '"megaRecipeFormat"'
	@GameTest(template = AREA, batch = "megaRecipeFormat")
	public void aTagOutputWithAChooser(GameTestHelper helper) {
		Leftovers.clear(helper);
		LuxuryRecipes.chooser("aliveworkplace_test_logs", maker -> List.of(Items.STONE, Items.OAK_LOG, Items.BIRCH_LOG, Items.OAK_LOG));
		LuxuryRecipes.Recipe r = read("""
			{"job": "aliveworkplace:jeweller", "level": 3, "keep": 1,
			 "inputs": [{"item": "minecraft:emerald", "count": 4}],
			 "output": {"tag": "#minecraft:logs", "for": "aliveworkplace_test_logs"}}""");
		Villager maker = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		maker.setNoAi(true);
		helper.assertTrue(r.makes(Items.OAK_LOG) && r.makes(Items.SPRUCE_LOG) && !r.makes(Items.STONE) && r.output() == Items.AIR, "makes: " + r);
		helper.assertTrue(r.goods(maker).equals(List.of(Items.OAK_LOG, Items.BIRCH_LOG)), "goods " + r.goods(maker));
		helper.assertTrue(r.keepOr(LuxuryWork.KEEP) == 1 && r.on(), "keep " + r.keepOr(LuxuryWork.KEEP));
		LuxuryRecipes.Recipe off = read("""
			{"job": "aliveworkplace:jeweller", "when": "village_megas",
			 "inputs": [{"item": "minecraft:emerald"}], "output": {"tag": "minecraft:logs", "for": "aliveworkplace_test_logs"}}""");
		helper.assertTrue(!off.on() && off.goods(maker).isEmpty() && off.keepOr(LuxuryWork.KEEP) == LuxuryWork.KEEP, "switched off but makes " + off.goods(maker));
		LuxuryRecipes.Recipe plain = read("""
			{"job": "aliveworkplace:jeweller", "inputs": [{"item": "minecraft:emerald"}], "output": {"id": "minecraft:diamond"}}""");
		helper.assertTrue(plain.makes(Items.DIAMOND) && !plain.makes(Items.EMERALD) && plain.goods(maker).equals(List.of(Items.DIAMOND)) && plain.on(),
			"a plain recipe: " + plain);
		helper.assertTrue(fails("""
			{"job": "aliveworkplace:jeweller", "inputs": [{"item": "minecraft:emerald"}], "output": {"tag": "minecraft:logs", "for": "nobody"}}""",
			"no chooser nobody"), "an unknown chooser loads");
		helper.assertTrue(fails("""
			{"job": "aliveworkplace:jeweller", "when": "never", "inputs": [{"item": "minecraft:emerald"}], "output": {"id": "minecraft:diamond"}}""",
			"no switch never"), "an unknown switch loads");
		helper.assertTrue(fails("""
			{"job": "aliveworkplace:jeweller", "keep": -1, "inputs": [{"item": "minecraft:emerald"}], "output": {"id": "minecraft:diamond"}}""",
			"keep -1"), "a negative keep loads");
		maker.discard();
		helper.succeed();
	}

	/** Every new sentence a player reads. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theText(GameTestHelper helper) {
		String progress = JewellerGameTests.english(Component.translatable("message.aliveworkplace.leader_bracelet.progress", "the Barony of Oakmere", 1, 3));
		helper.assertTrue(progress.equals("Trainer Leaders of the Barony of Oakmere beaten: 1 of 3. Beat them all for a Mega Bracelet from the capital's hall."),
			progress);
		String given = JewellerGameTests.english(Component.translatable("message.aliveworkplace.leader_bracelet.given", "the Barony of Oakmere", "Oakmere"));
		helper.assertTrue(given.equals("You've beaten every Trainer Leader of the Barony of Oakmere! The hall of Oakmere gives you a Mega Bracelet."), given);
		String tip = JewellerGameTests.english(Component.translatable("aliveworkplace.config.villageMegas.tooltip"));
		helper.assertTrue(tip.contains("The Jeweler cuts Mega Stones and Key Stones"), tip);
		String needs = JewellerGameTests.english(Component.translatable("message.aliveworkplace.luxury.state.needs_store", 4L, Items.EMERALD.getDescription()));
		helper.assertTrue(needs.equals("needs 4 × Emerald in the Storehouse"), needs);
		helper.succeed();
	}
}
