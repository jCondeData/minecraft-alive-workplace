package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Tonics;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA for the four more tonics (ROADMAP 30.16) and the Miners', Smiths', Woodsmen's (30.18), Harvest, Herders',
 * Scholars' (30.19), Healers', Merchants', Wardens' and Trainers' (30.20) Guilds, written from the specs: every job
 * each tonic lists drinks it and every job the spec leaves out refuses all four; a tonic's last tick, its sentence and
 * a save and reload; which trade belongs to which guild (the Bard to none, Cobblemon's trades to none without it); a
 * charter granted by the player's shift-right-click at the hall; a guild's perks only for its own members, only inside
 * its village, and still in force after a reload; and the rounding of the guilds' "a quarter less".
 */
public class QaTonicsGuildsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos GUILDHALL = new BlockPos(11, 2, 5);
	private static final float EPSILON = 1e-4f;

	private static final List<String> SMITH = List.of("minecraft:armorer", "minecraft:toolsmith", "minecraft:weaponsmith", "aliveworkplace:tinkerer", "aliveworkplace:ball_smith");
	private static final List<String> SCHOLAR = List.of("aliveworkplace:scholar", "aliveworkplace:teacher", "minecraft:librarian", "minecraft:cartographer", "aliveworkplace:fossil_scientist");
	private static final List<String> HARVEST = List.of("minecraft:farmer", "aliveworkplace:orchard_keeper", "aliveworkplace:florist", "aliveworkplace:beekeeper",
		"aliveworkplace:composter", "minecraft:shepherd", "minecraft:butcher", "aliveworkplace:rancher", "aliveworkplace:chef");
	private static final List<String> WOODSMAN = List.of("aliveworkplace:lumberjack", "minecraft:fletcher", "minecraft:fisherman", "aliveworkplace:porter", "aliveworkplace:postman");
	/** The spec's "no tonic for": guards, nurses, clerics, undertakers, innkeepers, shopkeepers, ferrymen, bards, trainers, tutors, traders. */
	private static final List<String> NO_TONIC = List.of("aliveworkplace:guard", "aliveworkplace:nurse", "minecraft:cleric", "aliveworkplace:undertaker",
		"aliveworkplace:innkeeper", "aliveworkplace:shopkeeper", "aliveworkplace:ferryman", "aliveworkplace:bard", "aliveworkplace:trainer",
		"aliveworkplace:trainer_leader", "aliveworkplace:tutor", "aliveworkplace:pokemon_trader");

	// --- 30.16: the four more tonics ---------------------------------------------------------------------------------

	/** Every job each of the four tonics lists is suited by it (and by no other of the four); the spec's left-out jobs refuse all four. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyListedJobSuitsItsTonicAndTheLeftOutJobsNone(GameTestHelper helper) {
		Map<String, List<String>> lists = new LinkedHashMap<>();
		lists.put("smiths_draught", SMITH);
		lists.put("scholars_infusion", SCHOLAR);
		lists.put("harvest_cordial", HARVEST);
		lists.put("woodsmans_broth", WOODSMAN);
		Map<String, Tonics.Tonic> tonics = new LinkedHashMap<>();
		for (String id : lists.keySet()) {
			Tonics.Tonic t = Tonics.get(AliveWorkplace.id(id)).orElse(null);
			helper.assertTrue(t != null, "tonic " + id + " didn't load");
			helper.assertTrue(t.percent() == 25 && t.ticks() == 24000, id + ": " + t.percent() + "% for " + t.ticks());
			tonics.put(id, t);
		}
		helper.assertTrue(tonics.get("smiths_draught").maker() == Tonics.Maker.ALCHEMIST && tonics.get("scholars_infusion").maker() == Tonics.Maker.ALCHEMIST
			&& tonics.get("harvest_cordial").maker() == Tonics.Maker.CHEF && tonics.get("woodsmans_broth").maker() == Tonics.Maker.CHEF, "makers");
		List<String> wrong = new ArrayList<>();
		lists.forEach((id, jobs) -> {
			for (String job : jobs) {
				Villager v = grown(helper, job);
				tonics.forEach((other, t) -> {
					if (t.suits(v) != other.equals(id)) {
						wrong.add(job + (t.suits(v) ? " suits " : " refuses ") + other);
					}
				});
			}
		});
		for (String job : NO_TONIC) {
			Villager v = grown(helper, job);
			tonics.forEach((id, t) -> {
				if (t.suits(v)) {
					wrong.add(job + " (no tonic) suits " + id);
				}
			});
		}
		helper.assertTrue(wrong.isEmpty(), "wrong: " + wrong);
		helper.succeed();
	}

	/**
	 * A Rancher and a Ball Smith drink by the player's right-click; a creative player keeps the bottle; the sentence a
	 * player reads; the tonic still works on its last tick and is gone on the tick it ends.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaTonicGuildsLastTick"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTonicGuildsLastTick")
	public void theRarerJobsDrinkAndTheTonicEndsOnItsLastTick(GameTestHelper helper) {
		Leftovers.clear(helper);
		quiet(helper);
		Villager rancher = worker(helper, new BlockPos(4, 2, 4), ModVillagers.RANCHER, "Rhea");
		Villager smith = worker(helper, new BlockPos(8, 2, 4), ModVillagers.BALL_SMITH, "Bo");
		Villager postman = worker(helper, new BlockPos(12, 2, 4), ModVillagers.POSTMAN, "Pat");
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HARVEST_CORDIAL, 2));
			helper.assertTrue(rightClick(player, rancher) == InteractionResult.SUCCESS, "no answer from the Rancher");
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "the Rancher didn't drink: " + player.getMainHandItem());
			helper.assertTrue(Math.abs(Tonics.pace(rancher) - 1f / 1.25f) < EPSILON, "Rancher 25% faster: " + Tonics.pace(rancher));
			ItemStack draught = new ItemStack(ModItems.SMITHS_DRAUGHT);
			Tonics.Offer offer = Tonics.offer(player, smith, draught);
			helper.assertTrue(offer.outcome() == Tonics.Outcome.DRUNK && draught.isEmpty(), "the Ball Smith: " + offer.outcome() + " " + draught);
			String said = offer.message().getString();
			helper.assertTrue(said.equals("Bo drinks the Smith's Draft: 25% faster for 20 minutes."), "said: " + said);
			ServerPlayer creative = player(helper, GameType.CREATIVE);
			ItemStack broth = new ItemStack(ModItems.WOODSMANS_BROTH);
			helper.assertTrue(Tonics.offer(creative, postman, broth).outcome() == Tonics.Outcome.DRUNK && broth.getCount() == 1, "creative: the Postman drank, the broth kept: " + broth);
			// The last tick and the end.
			long now = level.getGameTime();
			String id = AliveWorkplace.id("harvest_cordial").toString();
			ModAttachments.TONIC.set(rancher, new Tonics.Drunk(id, now + 1));
			helper.assertTrue(Tonics.active(rancher) != null && Tonics.ticksLeft(rancher) == 1 && Tonics.pace(rancher) < 1f, "the last tick: still working");
			String label = Tonics.paceLabel(rancher).getString();
			helper.assertTrue(label.equals("Harvest Cordial, 1 min left"), "the last tick's label: " + label);
			ModAttachments.TONIC.set(rancher, new Tonics.Drunk(id, now));
			helper.assertTrue(Tonics.active(rancher) == null && Tonics.ticksLeft(rancher) == 0 && Tonics.pace(rancher) == 1f, "ended: still working");
			helper.succeed();
		});
	}

	/** A Fossil Scientist's Scholar's Infusion is kept over a save and reload, with the same end. */
	//$ gametest_ticks_batch AREA '100' '"qaTonicSaved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTonicSaved")
	public void aTonicIsKeptOverASaveAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		quiet(helper);
		Villager scientist = worker(helper, new BlockPos(4, 2, 4), ModVillagers.FOSSIL_SCIENTIST, "Fitz");
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCHOLARS_INFUSION));
			helper.assertTrue(rightClick(player, scientist) == InteractionResult.SUCCESS && player.getMainHandItem().isEmpty(), "the Fossil Scientist didn't drink");
			Tonics.Drunk drunk = ModAttachments.TONIC.get(scientist);
			Villager back = EntityType.VILLAGER.create(level);
			back.load(scientist.saveWithoutId(new CompoundTag()));
			helper.assertTrue(drunk != null && drunk.equals(ModAttachments.TONIC.get(back)), "saved: " + drunk + " -> " + ModAttachments.TONIC.get(back));
			helper.assertTrue(Tonics.active(back) != null && Tonics.active(back).id().equals(AliveWorkplace.id("scholars_infusion"))
				&& Tonics.ticksLeft(back) == 24000, "after the reload: " + Tonics.ticksLeft(back));
			helper.succeed();
		});
	}

	// --- 30.18-30.20: the guilds ---------------------------------------------------------------------------------------

	/** Each trade the specs name finds its guild; the Bard has none, and without Cobblemon neither do the Trainers' Guild's trades. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyTradeFindsItsGuild(GameTestHelper helper) {
		Map<String, String> want = new LinkedHashMap<>();
		for (String j : List.of("aliveworkplace:miner", "aliveworkplace:sifter", "aliveworkplace:netherworker")) want.put(j, "miners");
		for (String j : SMITH) want.put(j, "smiths");
		for (String j : List.of("aliveworkplace:lumberjack", "minecraft:fletcher", "minecraft:fisherman")) want.put(j, "woodsmen");
		for (String j : List.of("minecraft:farmer", "aliveworkplace:orchard_keeper", "aliveworkplace:florist", "aliveworkplace:beekeeper", "aliveworkplace:composter", "aliveworkplace:chef")) want.put(j, "harvest");
		for (String j : List.of("minecraft:shepherd", "minecraft:butcher", "aliveworkplace:rancher")) want.put(j, "herders");
		for (String j : List.of("aliveworkplace:scholar", "aliveworkplace:teacher", "minecraft:librarian", "minecraft:cartographer")) want.put(j, "scholars");
		for (String j : List.of("aliveworkplace:nurse", "minecraft:cleric", "aliveworkplace:undertaker")) want.put(j, "healers");
		for (String j : List.of("aliveworkplace:shopkeeper", "aliveworkplace:innkeeper", "aliveworkplace:ferryman", "aliveworkplace:postman", "aliveworkplace:porter")) want.put(j, "merchants");
		want.put("aliveworkplace:guard", "wardens");
		for (String j : List.of("aliveworkplace:bard", "aliveworkplace:trainer", "aliveworkplace:trainer_leader", "aliveworkplace:tutor", "aliveworkplace:pokemon_trader", "aliveworkplace:fossil_scientist")) want.put(j, null);
		List<String> wrong = new ArrayList<>();
		want.forEach((job, guild) -> {
			Guilds.Guild g = Guilds.forTrade(grown(helper, job));
			// The test data pack's own "test_bards" guild (for another test) is no guild of the mod's.
			String got = g == null || g.id().getPath().startsWith("test_") ? null : g.id().getPath();
			if (guild == null ? got != null : !guild.equals(got)) {
				wrong.add(job + " -> " + got + " (want " + guild + ")");
			}
		});
		helper.assertTrue(wrong.isEmpty(), "wrong guilds: " + wrong);
		helper.succeed();
	}

	/**
	 * The player shift-right-clicks a Master Armorer with a Guild Charter: one charter used, the Smiths' Guild chartered
	 * with them as Guild Master; once a Guildhall stands, every smith works 15% faster and a Weaponsmith's ingot puts back
	 * a third; a Lumberjack in the same village gains nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGuildCharterClick"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuildCharterClick")
	public void aCharterGivenByShiftRightClickFoundsTheSmithsGuild(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager abe = villager(helper, new BlockPos(12, 2, 12), "Abe", VillagerProfession.ARMORER, 5);
		Villager tom = villager(helper, new BlockPos(13, 2, 12), "Tom", VillagerProfession.TOOLSMITH, 2);
		Villager tink = villager(helper, new BlockPos(14, 2, 12), "Tink", ModVillagers.TINKERER, 2);
		Villager ball = villager(helper, new BlockPos(12, 2, 13), "Ball", ModVillagers.BALL_SMITH, 2);
		Villager wes = villager(helper, new BlockPos(13, 2, 13), "Wes", VillagerProfession.WEAPONSMITH, 2);
		Villager lum = villager(helper, new BlockPos(14, 2, 13), "Lum", ModVillagers.LUMBERJACK, 2);
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = city(helper);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GUILD_CHARTER, 2));
			player.setShiftKeyDown(true);
			helper.assertTrue(rightClick(player, abe) == InteractionResult.SUCCESS, "no answer from the Armorer");
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "charters left: " + player.getMainHandItem());
			helper.assertTrue(hall.guilds().size() == 1 && hall.guilds().get(0).id().equals(AliveWorkplace.id("smiths"))
				&& hall.guilds().get(0).masterName().equals("Abe"), "chartered: " + hall.guilds());
			Component line = Guilds.masterLine(abe);
			helper.assertTrue(line != null && line.getString().equals("Guild Master of the Smiths' Guild"), "status: " + line);
			helper.assertTrue(Guilds.pace(tom) == 1f, "no Guildhall yet: faster");
			helper.assertTrue(Math.abs(Guilds.mendShare(wes, 0.25f) - 0.25f) < EPSILON, "no Guildhall yet: mends more");
			found(helper, hall);
			for (Villager v : List.of(abe, tom, tink, ball, wes)) {
				helper.assertTrue(Math.abs(Guilds.pace(v) * 1.15f - 1f) < EPSILON, v.getName().getString() + " 15% faster: " + Guilds.pace(v));
			}
			helper.assertTrue(Math.abs(Guilds.mendShare(wes, 0.25f) - 1f / 3f) < 1e-3f, "a third per ingot: " + Guilds.mendShare(wes, 0.25f));
			helper.assertTrue(Guilds.pace(lum) == 1f && Guilds.toolWear(lum) == 1f, "a Lumberjack isn't a smith");
			player.setShiftKeyDown(false);
			helper.succeed();
		});
	}

	/**
	 * A founded Woodsmen's Guild: a Fisherman in the village has his rod wear halved and works faster, one outside the
	 * village has neither; the perks hold after the hall is saved and loaded again and the guilds forgotten (a restart).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGuildVillageOnly"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuildVillageOnly")
	public void perksReachOnlyTheVillageAndSurviveAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		VillageHalls.RADIUS = 8; // the Guildhall (6 away) inside, the far Fisherman (about 12.7 away) outside
		Villager master = villager(helper, new BlockPos(12, 2, 12), "Lars", ModVillagers.LUMBERJACK, 5);
		Villager near = villager(helper, new BlockPos(13, 2, 12), "Finn", VillagerProfession.FISHERMAN, 2);
		Villager far = villager(helper, new BlockPos(2, 2, 2), "Fara", VillagerProfession.FISHERMAN, 2);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = city(helper);
			helper.assertTrue(Guilds.grant(level, master).outcome() == Guilds.Outcome.GRANTED, "chartered");
			found(helper, hall);
			helper.assertTrue(Guilds.toolWear(near) == 0.5f && Math.abs(Guilds.pace(near) * 1.15f - 1f) < EPSILON, "in the village: " + Guilds.toolWear(near));
			helper.assertTrue(Guilds.toolWear(far) == 1f && Guilds.pace(far) == 1f, "outside the village: " + Guilds.toolWear(far) + " " + Guilds.pace(far));
			CompoundTag tag = hall.saveWithoutMetadata(level.registryAccess());
			hall.setGuilds(List.of());
			Guilds.forget();
			CivicEffects.forget();
			helper.assertTrue(Guilds.toolWear(near) == 1f, "no guild left: still halved");
			hall.loadWithComponents(tag, level.registryAccess());
			Guilds.forget();
			CivicEffects.forget();
			helper.assertTrue(hall.guilds().size() == 1 && hall.guilds().get(0).id().equals(AliveWorkplace.id("woodsmen")), "reloaded: " + hall.guilds());
			helper.assertTrue(Guilds.founded(level, hall, AliveWorkplace.id("woodsmen")), "not founded after the reload");
			helper.assertTrue(Guilds.toolWear(near) == 0.5f, "after the reload: " + Guilds.toolWear(near));
			helper.succeed();
		});
	}

	/**
	 * A founded Herders' Guild: Shepherds and Ranchers breed up to 12, hired Butchers keep 14; a Farmer (the Harvest
	 * Guild's, not founded) still breeds 8, reaches 16 and works at the usual pace.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGuildHerders"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuildHerders")
	public void herdersGuildIsForHerdersOnly(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager master = villager(helper, new BlockPos(12, 2, 12), "Rhea", ModVillagers.RANCHER, 5);
		Villager shep = villager(helper, new BlockPos(13, 2, 12), "Sheb", VillagerProfession.SHEPHERD, 2);
		Villager butch = villager(helper, new BlockPos(14, 2, 12), "Bert", VillagerProfession.BUTCHER, 2);
		Villager farmer = villager(helper, new BlockPos(12, 2, 13), "Fay", VillagerProfession.FARMER, 2);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = city(helper);
			helper.assertTrue(Guilds.grant(helper.getLevel(), master).outcome() == Guilds.Outcome.GRANTED, "chartered");
			helper.assertTrue(Guilds.herd(master, 8) == 8 && Guilds.herd(butch, 10) == 10, "not founded: 8 and 10");
			found(helper, hall);
			helper.assertTrue(Guilds.herd(master, 8) == 12 && Guilds.herd(shep, 8) == 12, "Ranchers and Shepherds: 12, got " + Guilds.herd(master, 8) + " " + Guilds.herd(shep, 8));
			helper.assertTrue(Guilds.herd(butch, 10) == 14, "a hired Butcher keeps 14, got " + Guilds.herd(butch, 10));
			helper.assertTrue(Math.abs(Guilds.pace(butch) * 1.15f - 1f) < EPSILON, "a Butcher 15% faster");
			helper.assertTrue(Guilds.herd(farmer, 8) == 8 && Guilds.reach(farmer, 16) == 16 && Guilds.pace(farmer) == 1f, "the Farmer gained from the Herders' Guild");
			helper.succeed();
		});
	}

	/**
	 * A founded Harvest Guild: a Chef and a Composter work 15% faster and reach 24 (not 16); a Shepherd (the Herders'
	 * Guild's) still reaches 16, breeds 8 and works at the usual pace.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGuildHarvest"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuildHarvest")
	public void harvestGuildIsForItsSixTradesOnly(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager master = villager(helper, new BlockPos(12, 2, 12), "Flo", ModVillagers.FLORIST, 5);
		Villager chef = villager(helper, new BlockPos(13, 2, 12), "Cook", ModVillagers.CHEF, 2);
		Villager comp = villager(helper, new BlockPos(14, 2, 12), "Cora", ModVillagers.COMPOSTER, 2);
		Villager shep = villager(helper, new BlockPos(12, 2, 13), "Sheb", VillagerProfession.SHEPHERD, 2);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = city(helper);
			helper.assertTrue(Guilds.grant(helper.getLevel(), master).outcome() == Guilds.Outcome.GRANTED, "chartered");
			helper.assertTrue(Guilds.reach(chef, 16) == 16, "not founded: 16");
			found(helper, hall);
			for (Villager v : List.of(chef, comp)) {
				helper.assertTrue(Math.abs(Guilds.pace(v) * 1.15f - 1f) < EPSILON && Guilds.reach(v, 16) == 24, v.getName().getString() + ": " + Guilds.pace(v) + " " + Guilds.reach(v, 16));
			}
			helper.assertTrue(Guilds.reach(shep, 16) == 16 && Guilds.herd(shep, 8) == 8 && Guilds.pace(shep) == 1f, "the Shepherd gained from the Harvest Guild");
			helper.succeed();
		});
	}

	/**
	 * A founded Scholars' Guild: research costs a quarter less, rounded up (1 stays 1, 4 is 3, 5 is 4, 100 is 75); a
	 * Librarian works faster but a Fossil Scientist (no guild without Cobblemon) doesn't; off, the full price.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGuildScholars"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuildScholars")
	public void scholarsGuildRoundsTheQuarterUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager master = villager(helper, new BlockPos(12, 2, 12), "Sage", ModVillagers.SCHOLAR, 5);
		Villager lib = villager(helper, new BlockPos(13, 2, 12), "Libby", VillagerProfession.LIBRARIAN, 2);
		Villager fossil = villager(helper, new BlockPos(14, 2, 12), "Fitz", ModVillagers.FOSSIL_SCIENTIST, 2);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = city(helper);
			helper.assertTrue(Guilds.grant(level, master).outcome() == Guilds.Outcome.GRANTED, "chartered");
			int[][] costs = {{0, 0}, {1, 1}, {2, 2}, {3, 3}, {4, 3}, {5, 4}, {7, 6}, {8, 6}, {100, 75}};
			for (int[] c : costs) {
				helper.assertTrue(Guilds.researchCost(level, hall, c[0]) == c[0], "not founded: " + c[0] + " -> " + Guilds.researchCost(level, hall, c[0]));
			}
			found(helper, hall);
			for (int[] c : costs) {
				helper.assertTrue(Guilds.researchCost(level, hall, c[0]) == c[1], "founded: " + c[0] + " -> " + Guilds.researchCost(level, hall, c[0]) + ", want " + c[1]);
			}
			helper.assertTrue(Math.abs(Guilds.pace(lib) * 1.15f - 1f) < EPSILON, "a Librarian 15% faster");
			helper.assertTrue(Guilds.pace(fossil) == 1f, "a Fossil Scientist is in the Scholars' Guild");
			try {
				Guilds.ENABLED = false;
				helper.assertTrue(Guilds.researchCost(level, hall, 5) == 5, "guilds off: 5 -> " + Guilds.researchCost(level, hall, 5));
			} finally {
				Guilds.ENABLED = true;
			}
			helper.succeed();
		});
	}

	/**
	 * A founded Merchants' Guild: hiring costs a quarter less, rounded up (1 stays 1, 5 is 4, 16 is 12) for a guest in
	 * the village, the full price outside it; Postmen and Ferrymen work faster but only Porters carry more.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGuildMerchants"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuildMerchants")
	public void merchantsGuildRoundsTheHireAndCarriesForPortersOnly(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		VillageHalls.RADIUS = 8;
		Villager master = villager(helper, new BlockPos(12, 2, 12), "Mara", ModVillagers.SHOPKEEPER, 5);
		Villager post = villager(helper, new BlockPos(13, 2, 12), "Pat", ModVillagers.POSTMAN, 2);
		Villager ferry = villager(helper, new BlockPos(14, 2, 12), "Fred", ModVillagers.FERRYMAN, 2);
		Villager porter = villager(helper, new BlockPos(12, 2, 13), "Pip", ModVillagers.PORTER, 2);
		Villager guest = villager(helper, new BlockPos(13, 2, 13), "Tam", VillagerProfession.NITWIT, 1);
		Villager away = villager(helper, new BlockPos(2, 2, 2), "Ada", VillagerProfession.NITWIT, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = city(helper);
			helper.assertTrue(Guilds.grant(level, master).outcome() == Guilds.Outcome.GRANTED, "chartered");
			found(helper, hall);
			int[][] prices = {{1, 1}, {4, 3}, {5, 4}, {16, 12}};
			for (int[] p : prices) {
				helper.assertTrue(Guilds.hirePrice(guest, p[0]) == p[1], "in the village: " + p[0] + " -> " + Guilds.hirePrice(guest, p[0]) + ", want " + p[1]);
				helper.assertTrue(Guilds.hirePrice(away, p[0]) == p[0], "outside: " + p[0] + " -> " + Guilds.hirePrice(away, p[0]));
			}
			ModAttachments.TRAVELLER.set(guest, new Traveller(level.getGameTime(), 3));
			helper.assertTrue(Innkeepers.emeralds(guest, Innkeepers.traveller(guest)) == 12, "a level-3 traveller: " + Innkeepers.emeralds(guest, Innkeepers.traveller(guest)));
			for (Villager v : List.of(post, ferry, porter)) {
				helper.assertTrue(Math.abs(Guilds.pace(v) * 1.15f - 1f) < EPSILON, v.getName().getString() + " 15% faster");
			}
			helper.assertTrue(Guilds.carry(porter, 9) == 12 && Guilds.carry(post, 9) == 9, "carry: porter " + Guilds.carry(porter, 9) + ", postman " + Guilds.carry(post, 9));
			helper.succeed();
		});
	}

	/**
	 * A founded Wardens' Guild: a guard in the village trains up to Master (5, not 4) and hits 10% harder, one outside
	 * the village neither; it adds no pace.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGuildWardens"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuildWardens")
	public void wardensGuildOnlyInItsVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		VillageHalls.RADIUS = 8;
		Villager master = villager(helper, new BlockPos(12, 2, 12), "Wulf", ModVillagers.GUARD, 5);
		Villager near = villager(helper, new BlockPos(13, 2, 12), "Gerd", ModVillagers.GUARD, 4);
		Villager far = villager(helper, new BlockPos(2, 2, 2), "Gard", ModVillagers.GUARD, 4);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = city(helper);
			helper.assertTrue(Guilds.grant(helper.getLevel(), master).outcome() == Guilds.Outcome.GRANTED, "chartered");
			helper.assertTrue(Guilds.trainUpTo(near, 4) == 4 && Guilds.strength(near) == 1f, "not founded");
			found(helper, hall);
			helper.assertTrue(Guilds.trainUpTo(near, 4) == 5 && Math.abs(Guilds.strength(near) - 1.1f) < EPSILON, "in the village: " + Guilds.trainUpTo(near, 4) + " " + Guilds.strength(near));
			helper.assertTrue(Guilds.trainUpTo(far, 4) == 4 && Guilds.strength(far) == 1f, "outside: " + Guilds.trainUpTo(far, 4) + " " + Guilds.strength(far));
			helper.assertTrue(Guilds.pace(near) == 1f, "the Wardens' Guild changes the pace");
			helper.succeed();
		});
	}

	/**
	 * A founded Healers' Guild: illness a day shorter for anyone in the village (not outside it); nurses and undertakers
	 * look 48 out (not 32), a Cleric works faster but looks as far as before.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGuildHealers"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuildHealers")
	public void healersGuildNumbersForWhomTheSpecSays(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		VillageHalls.RADIUS = 8;
		Villager master = villager(helper, new BlockPos(12, 2, 12), "Hilde", ModVillagers.NURSE, 5);
		Villager under = villager(helper, new BlockPos(13, 2, 12), "Ulf", ModVillagers.UNDERTAKER, 2);
		Villager cleric = villager(helper, new BlockPos(14, 2, 12), "Cid", VillagerProfession.CLERIC, 2);
		Villager mason = villager(helper, new BlockPos(12, 2, 13), "Ike", VillagerProfession.MASON, 2);
		Villager far = villager(helper, new BlockPos(2, 2, 2), "Fae", VillagerProfession.MASON, 2);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = city(helper);
			helper.assertTrue(Guilds.grant(helper.getLevel(), master).outcome() == Guilds.Outcome.GRANTED, "chartered");
			helper.assertTrue(Guilds.recoveryDays(mason) == 0 && Guilds.radius(under, 32) == 32, "not founded");
			found(helper, hall);
			helper.assertTrue(Guilds.recoveryDays(mason) == -1 && Guilds.recoveryDays(far) == 0, "a day sooner: in " + Guilds.recoveryDays(mason) + ", out " + Guilds.recoveryDays(far));
			helper.assertTrue(Guilds.radius(master, 32) == 48 && Guilds.radius(under, 32) == 48, "48 out: " + Guilds.radius(master, 32) + " " + Guilds.radius(under, 32));
			helper.assertTrue(Guilds.radius(cleric, 32) == 32 && Math.abs(Guilds.pace(cleric) * 1.15f - 1f) < EPSILON, "the Cleric: " + Guilds.radius(cleric, 32) + " " + Guilds.pace(cleric));
			helper.assertTrue(Guilds.pace(mason) == 1f, "a Mason is no healer");
			helper.succeed();
		});
	}

	/** A Town (two guilds) that has both: the refusal reads in the plural; every guild's name and perk read as English, not keys. */
	//$ gametest_ticks_batch AREA '100' '"qaGuildWords"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuildWords")
	public void theGuildSentencesRead(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager lars = villager(helper, new BlockPos(12, 2, 12), "Lars", ModVillagers.LUMBERJACK, 5);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = city(helper);
			hall.setRank(VillageRanks.Rank.TOWN);
			hall.setGuilds(List.of(new Guilds.Charter(AliveWorkplace.id("miners"), UUID.randomUUID(), "Brokk", 0L, Optional.empty()),
				new Guilds.Charter(AliveWorkplace.id("smiths"), UUID.randomUUID(), "Abe", 0L, Optional.empty())));
			Guilds.forget();
			Guilds.Offer offer = Guilds.grant(level, lars);
			String said = offer.message().getString();
			helper.assertTrue(offer.outcome() == Guilds.Outcome.FULL && said.endsWith(" is a Town and may have 2 guilds; it has them all."), "full: " + offer.outcome() + " " + said);
			List<String> raw = new ArrayList<>();
			for (String id : List.of("miners", "smiths", "woodsmen", "harvest", "herders", "scholars", "healers", "merchants", "wardens")) {
				Guilds.Guild g = Guilds.get(AliveWorkplace.id(id));
				String name = g == null ? "missing" : g.name().getString();
				String perk = g == null ? "missing" : g.perk().getString();
				if (!name.endsWith("Guild") || perk.contains("aliveworkplace") || !perk.endsWith(".")) {
					raw.add(id + ": " + name + " / " + perk);
				}
			}
			helper.assertTrue(raw.isEmpty(), "unreadable: " + raw);
			helper.succeed();
		});
	}

	// --- Helpers -------------------------------------------------------------------------------------------------------

	private static VillagerProfession job(String id) {
		return BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(id));
	}

	/** A grown villager of {@code id}'s job, never added to the level (for data checks). */
	private static Villager grown(GameTestHelper helper, String id) {
		VillagerProfession p = job(id);
		helper.assertTrue(p != null && BuiltInRegistries.VILLAGER_PROFESSION.getKey(p).toString().equals(id), "no job " + id);
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.setVillagerData(v.getVillagerData().setProfession(p).setLevel(2));
		return v;
	}

	private static void quiet(GameTestHelper helper) {
		Leftovers.halls(helper);
		CivicEffects.forget();
		boolean moods = Moods.ENABLED;
		Moods.ENABLED = false;
		Moods.forget();
		Leftovers.after(helper, () -> {
			Moods.ENABLED = moods;
			Moods.forget();
		});
	}

	private static Villager worker(GameTestHelper helper, BlockPos at, VillagerProfession job, String name) {
		Villager villager = villager(helper, at, name, job, 2);
		villager.setVillagerXp(10);
		return villager;
	}

	private static ServerPlayer player(GameTestHelper helper, GameType mode) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(mode);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/** The player right-clicks {@code villager} with what's in their main hand, through the game's use-entity event. */
	private static InteractionResult rightClick(ServerPlayer player, Villager villager) {
		player.moveTo(villager.getX() + 1, villager.getY(), villager.getZ(), 90f, 0f);
		return UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
	}

	/** A Village Hall at {@link #HALL} (radius 16 unless the test narrows it); what the test changed is put back when it ends. */
	private static void village(GameTestHelper helper) {
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		BlueprintData.Placement at = placement(helper);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Guilds.ENABLED = true;
			BuildSiteManager.get(helper.getLevel()).forgetFinished(at);
			VillageNeeds.forget();
			CivicEffects.forget();
			Guilds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(GUILDHALL), Rotation.NONE, Mirror.NONE);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		return v;
	}

	/** The hall, made a City with no guilds yet. */
	private static VillageHallBlockEntity city(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		hall.setRank(VillageRanks.Rank.CITY);
		hall.setGuilds(List.of());
		VillageNeeds.forget();
		CivicEffects.forget();
		Guilds.forget();
		Moods.forget();
		return hall;
	}

	/** A Guildhall is finished: the guild claims it and is founded. */
	private static void found(GameTestHelper helper, VillageHallBlockEntity hall) {
		ServerLevel level = helper.getLevel();
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL.id(), placement(helper), UUID.randomUUID());
		Guilds.round(level, helper.absolutePos(HALL), hall);
		helper.assertTrue(Guilds.founded(level, hall, hall.guilds().get(0).id()), "founded: " + hall.guilds());
	}
}
