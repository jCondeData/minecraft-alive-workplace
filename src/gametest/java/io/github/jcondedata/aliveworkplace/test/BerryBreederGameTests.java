package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.berry.BerryBreeders;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** ROADMAP 28.9 without Cobblemon: the Berry Breeder isn't offered, and her texts, show and partner types are there. */
public class BerryBreederGameTests implements FabricGameTest {
	/** Without Cobblemon no item picks the job at a composter, and a breeder (from an old save) only says why she's idle. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noBreederWithoutCobblemon(GameTestHelper helper) {
		helper.assertTrue(!BerryBreeders.available() && BerryBreeders.data() == null, "the job is offered without Cobblemon");
		Stations.Station composter = Stations.at(Blocks.COMPOSTER).orElseThrow();
		helper.assertTrue(composter.has(ModVillagers.BERRY_BREEDER), "the composter doesn't list the Berry Breeder");
		for (ItemStack stack : new ItemStack[] {new ItemStack(Items.SWEET_BERRIES), new ItemStack(Items.GLOW_BERRIES), new ItemStack(Items.BONE_MEAL)}) {
			helper.assertTrue(!BerryBreeders.isBerry(stack) && !Stations.picks(stack, ModVillagers.BERRY_BREEDER), stack + " picks the Berry Breeder");
		}
		helper.assertTrue(Stations.of(ModVillagers.BERRY_BREEDER).filter(s -> s.block() == Blocks.COMPOSTER).isPresent(), "her station");
		helper.succeed();
	}

	/** Grass and Bug partners help her; a Bug partner flits between the paired plants (one data file). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void herPartnersAndShow(GameTestHelper helper) {
		helper.assertTrue(Partners.types(ModVillagers.BERRY_BREEDER).equals(Set.of("grass", "bug")), "types " + Partners.types(ModVillagers.BERRY_BREEDER));
		PartnerShows.Show show = PartnerShows.shows().stream()
			.filter(s -> s.name().getPath().equals("berry_breeder_bug_flits_between_the_plants")).findFirst().orElse(null);
		helper.assertTrue(show != null, "the show didn't load: " + PartnerShows.shows().size() + " shows");
		helper.assertTrue(show.cue().equals("pair") && show.types().equals(Set.of("bug")) && show.effect() == PartnerShows.Effect.NONE, "show " + show);
		helper.succeed();
	}

	/** Every sentence a player reads about her is in the lang file. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void herTextsAreTranslated(GameTestHelper helper) {
		Language lang = Language.getInstance();
		String[] keys = {"entity.minecraft.villager.berry_breeder", "entity.minecraft.zombie_villager.berry_breeder",
			"station.aliveworkplace.item.aliveworkplace.berry_breeder", "aliveworkplace.config.berryBreeders",
			"aliveworkplace.config.berryBreeders.tooltip", "message.aliveworkplace.berry_breeder.title", "message.aliveworkplace.berry_breeder.found",
			"message.aliveworkplace.berry_breeder.book_title", "message.aliveworkplace.berry_breeder.book.previous", "message.aliveworkplace.berry_breeder.book.next",
			"message.aliveworkplace.berry_breeder.book.page", "message.aliveworkplace.berry_breeder.book.found", "message.aliveworkplace.berry_breeder.book.clear",
			"message.aliveworkplace.berry_breeder.book.stop_plot", "message.aliveworkplace.berry_breeder.book.goal", "message.aliveworkplace.berry_breeder.book.lit",
			"message.aliveworkplace.berry_breeder.book.wild", "message.aliveworkplace.berry_breeder.book.pair", "message.aliveworkplace.berry_breeder.book.click",
			"message.aliveworkplace.berry_breeder.goal.none", "message.aliveworkplace.berry_breeder.goal.reached",
			"message.aliveworkplace.berry_breeder.goal.out_of_reach", "message.aliveworkplace.berry_breeder.goal.step",
			"message.aliveworkplace.berry_breeder.goal.have", "message.aliveworkplace.berry_breeder.goal.cannot", "message.aliveworkplace.berry_breeder.goal.set",
			"message.aliveworkplace.berry_plot.no_block", "message.aliveworkplace.berry_plot.started"};
		for (String key : keys) {
			helper.assertTrue(lang.has(key), "no text for " + key);
		}
		for (String state : new String[] {"pick", "deposit", "plant", "mulch", "clear", "growing", "no_goal", "goal_reached", "out_of_reach",
				"needs", "no_farmland", "no_room", "breeding", "no_chest", "off"}) {
			helper.assertTrue(lang.has("message.aliveworkplace.berry_breeder.state." + state), "no text for the state " + state);
		}
		helper.succeed();
	}
}
