package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.ClassesPage;
import io.github.jcondedata.aliveworkplace.hall.Services;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.Diet;
import io.github.jcondedata.aliveworkplace.people.Luxuries;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * The class chatter of 34.6 (B551): a villager talks of wine and new clothes when their class asks for them and they
 * haven't had them lately, of the Gazette once they read this week's, and of the tavern when it reaches their home; and
 * the hall's tip for a plain diet names the plain diet's count of kinds (B552). The real class and luxury files.
 */
public class ClassChatterGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	/** A Village Hall at {@link #HALL}, classes on with the shipped ladder and luxuries; everything shared put back after. */
	private static VillageHallBlockEntity village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		ClassNeeds.services = Services.HOOK;
		ClassNeeds.luxuryEvery = Luxuries.HOOK;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			SocialClasses.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static void had(Villager v, long day, String... luxuries) {
		Map<ResourceLocation, Long> had = new HashMap<>(ModAttachments.LUXURIES_HAD.getOrElse(v, Map.of()));
		for (String l : luxuries) {
			had.put(ours(l), day);
		}
		ModAttachments.LUXURIES_HAD.set(v, had);
	}

	/**
	 * Wine and clothes: an Artisan who never had cider (their want), Berry Wine or Fine Clothes (the Burghers' needs)
	 * talks of both; a Peasant only of clothes (the Artisans' Work Clothes, and no wine on their ladder step); with them
	 * all had today, neither; nobody unseeded, and nobody with classes off.
	 */
	//$ gametest_batch AREA '"classChatterWants"'
	@GameTest(template = AREA, batch = "classChatterWants")
	public void wineAndClothesAreTalkedOfWhenTheClassLacksThem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		BlockPos at = helper.absolutePos(HALL);
		helper.assertTrue(SocialClasses.get(ours("artisan")) != null && Luxuries.get(ours("berry_wine")) != null, "the shipped classes and luxuries load");
		Villager artisan = villager(helper, new BlockPos(4, 2, 4), "Ada");
		Villager peasant = villager(helper, new BlockPos(6, 2, 4), "Bo");
		Villager unseeded = villager(helper, new BlockPos(8, 2, 4), "Cy");
		SocialClasses.seed(artisan, SocialClasses.get(ours("artisan")));
		SocialClasses.seed(peasant, SocialClasses.get(ours("peasant")));
		long today = Chronicle.day(level);
		List<String> a = Chatter.topics(level, artisan, at);
		helper.assertTrue(a.contains("class_wants_wine") && a.contains("class_wants_clothes"), "an Artisan with no wine or new clothes talks of both: " + a);
		List<String> p = Chatter.topics(level, peasant, at);
		helper.assertTrue(p.contains("class_wants_clothes") && !p.contains("class_wants_wine"), "a Peasant wants the Artisans' clothes, not wine: " + p);
		List<String> u = Chatter.topics(level, unseeded, at);
		helper.assertTrue(!u.contains("class_wants_wine") && !u.contains("class_wants_clothes"), "no class, no class wants: " + u);
		had(artisan, today, "cider", "berry_wine", "work_clothes", "fine_clothes");
		a = Chatter.topics(level, artisan, at);
		helper.assertTrue(!a.contains("class_wants_wine") && !a.contains("class_wants_clothes"), "had them all today: nothing to want: " + a);
		// Berry Wine lasts 2 days: two days on, it's wanted again; the clothes (8 days) aren't.
		had(artisan, today - 2, "berry_wine");
		a = Chatter.topics(level, artisan, at);
		helper.assertTrue(a.contains("class_wants_wine") && !a.contains("class_wants_clothes"), "the wine ran out, the clothes didn't: " + a);
		SocialClasses.ENABLED = false;
		helper.assertTrue(!Chatter.topics(level, artisan, at).contains("class_wants_wine"), "villageClasses off: no class chatter");
		List.of(artisan, peasant, unseeded).forEach(Villager::discard);
		helper.succeed();
	}

	/** The Gazette: talked of while the household's paper is this week's (had within the Gazette's 7 days), not before or after. */
	//$ gametest_batch AREA '"classChatterGazette"'
	@GameTest(template = AREA, batch = "classChatterGazette")
	public void theGazetteIsTalkedOfTheWeekItIsRead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		BlockPos at = helper.absolutePos(HALL);
		Luxuries.Luxury gazette = Luxuries.get(ours("gazette"));
		helper.assertTrue(gazette != null, "the Gazette is a luxury");
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Dee");
		SocialClasses.seed(v, SocialClasses.get(ours("burgher")));
		long today = Chronicle.day(level);
		helper.assertTrue(!Chatter.topics(level, v, at).contains("gazette_news"), "never read one: no news");
		had(v, today, "gazette");
		helper.assertTrue(Chatter.topics(level, v, at).contains("gazette_news"), "read today: the Gazette's news: " + Chatter.topics(level, v, at));
		had(v, today - (gazette.everyDays() - 1), "gazette");
		helper.assertTrue(Chatter.topics(level, v, at).contains("gazette_news"), "still this week's paper");
		had(v, today - gazette.everyDays(), "gazette");
		helper.assertTrue(!Chatter.topics(level, v, at).contains("gazette_news"), "a week old: old news");
		v.discard();
		helper.succeed();
	}

	/** The tavern: talked of by a villager whose bed it reaches (the hall's service list), not one it doesn't, nor one with no bed. */
	//$ gametest_batch AREA '"classChatterTavern"'
	@GameTest(template = AREA, batch = "classChatterTavern")
	public void theTavernIsTalkedOfWhereItReachesHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = village(helper);
		BlockPos at = helper.absolutePos(HALL);
		long today = Chronicle.day(level);
		Villager housed = villager(helper, new BlockPos(4, 2, 4), "Eli");
		Villager homeless = villager(helper, new BlockPos(8, 2, 4), "Fay");
		Beds.home(housed, helper.absolutePos(new BlockPos(2, 2, 2)));
		helper.assertTrue(!Chatter.topics(level, housed, at).contains("tavern_glad"), "no tavern yet: nothing to be glad of");
		hall.setServices(List.of(new Services.Found(ours("tavern"), helper.absolutePos(new BlockPos(10, 2, 10)), 48)), today);
		List<String> topics = Chatter.topics(level, housed, at);
		helper.assertTrue(topics.contains("tavern_glad"), "the tavern reaches their bed: " + topics);
		helper.assertTrue(!Chatter.topics(level, homeless, at).contains("tavern_glad"), "no bed, no tavern near home");
		hall.setServices(List.of(new Services.Found(ours("tavern"), helper.absolutePos(new BlockPos(10, 2, 10)).offset(200, 0, 0), 48)), today);
		helper.assertTrue(!Chatter.topics(level, housed, at).contains("tavern_glad"), "a tavern out of reach of home isn't theirs");
		hall.setServices(List.of(), today);
		List.of(housed, homeless).forEach(Villager::discard);
		helper.succeed();
	}

	/** Each new topic's lines read as sentences, and a chosen one is spoken with its text (not a raw key). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theClassChatterLinesHaveText(GameTestHelper helper) {
		for (String topic : List.of("class_wants_wine", "class_wants_clothes", "gazette_news", "tavern_glad")) {
			for (int i = 0; i < 3; i++) {
				String line = Component.translatable("chatter.aliveworkplace." + topic + "." + i).getString();
				helper.assertTrue(!line.startsWith("chatter.") && (line.endsWith(".") || line.endsWith("?")), "chatter line " + topic + "." + i + ": " + line);
			}
		}
		helper.succeed();
	}

	/** B552: the What next? tip for a plain-diet need names the plain diet's 2 kinds; a varied one's still names 3. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aPlainDietTipNamesThePlainDietsKinds(GameTestHelper helper) {
		String plain = ClassesPage.how(new ClassNeeds.DietNeed(Diet.Kind.PLAIN)).getString();
		String varied = ClassesPage.how(new ClassNeeds.DietNeed(Diet.Kind.VARIED)).getString();
		helper.assertTrue(plain.equals("Keep " + Diet.PLAIN_KINDS + " kinds of meal in the store") && Diet.PLAIN_KINDS == 2, "plain: " + plain);
		helper.assertTrue(varied.equals("Keep " + Diet.VARIED_KINDS + " kinds of meal in the store") && Diet.VARIED_KINDS == 3, "varied: " + varied);
		helper.succeed();
	}
}
