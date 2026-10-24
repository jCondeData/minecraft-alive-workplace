package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.RemedyItem;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Disaster;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Plague;
import io.github.jcondedata.aliveworkplace.work.PhysicianWork;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * The plague and the Physician (ROADMAP 32.18), with the mod's own {@code plague.json} on the disaster engine: over
 * hall rounds (a fixed {@link RandomSource}) it passes from the ill to those within 3 blocks in a plague and not outside
 * one; an ill villager the Physician has seen passes it on only in their house; the Remedy cures a hexed and a
 * plague-ill villager (from a player's hand and from the Physician's chest) where a honey bottle cures neither; the
 * plague ends the day after the last cure; a Physician foretells it at its first case; the recipe crafts; a player who
 * drinks one loses poison, wither, hunger and nausea; what the Remedy and the Physician leave on a villager survives a
 * save; config {@code physicians} off, the brewing stand offers no Physician; and every new sentence has its text.
 */
public class PlagueGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(16, 2, 16);
	/** Day 48 begins here: far from the other tests' days. */
	private static final long BASE = 47 * VillageNeeds.DAY;

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Disaster plague) {
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long was = level.getDayTime();
		VillageHalls.RADIUS = 20;
		level.setDayTime(BASE + 1000);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
				e.setDisaster(null, -1, -1, -100);
			}
			Disasters.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(was);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		entity.setDisaster(null, -1, -1, -100);
		Disaster plague = Disasters.get(Plague.ID).orElse(null);
		helper.assertTrue(plague != null, "plague.json isn't loaded: " + Disasters.all());
		return new Village(level, hall, entity, plague);
	}

	private static void start(Village v) {
		Disasters.start(v.level(), v.hall(), v.entity(), v.plague(), VillageHalls.census(v.level(), v.hall()), RandomSource.create(1L));
		if (!Plague.ID.equals(v.entity().disaster())) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("the plague didn't start");
		}
	}

	private static Villager villager(GameTestHelper helper, double x, double z) {
		Villager v = helper.spawn(EntityType.VILLAGER, new net.minecraft.world.phys.Vec3(x, 2, z));
		v.setNoAi(true);
		return v;
	}

	private static Disaster.Effect contagion(Village v) {
		return v.plague().effect(Disaster.Effect.Type.CONTAGION).orElseThrow();
	}

	/** Over 20 hall rounds the plague passes from the ill to the villagers beside them, never to one across the village, and not at all outside a plague. */
	//$ gametest_ticks_batch AREA '100' '"plagueSpreads"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "plagueSpreads")
	public void illnessPassesOnInAPlague(GameTestHelper helper) {
		Village v = village(helper);
		Villager sick = villager(helper, 4.5, 4.5);
		Villager beside = villager(helper, 6.5, 4.5);
		Villager behind = villager(helper, 4.5, 6.5);
		Villager far = villager(helper, 14.5, 4.5);
		Sickness.catchPlague(v.level(), sick);
		// Outside a plague: twenty hall rounds pass nothing on.
		for (int i = 0; i < 20; i++) {
			Disasters.round(v.level(), v.hall(), v.entity(), VillageHalls.census(v.level(), v.hall()));
		}
		helper.assertTrue(!Sickness.isIll(beside) && !Sickness.isIll(behind) && !Sickness.isIll(far), "the illness passed on with no plague under way");
		start(v);
		helper.assertTrue(Plague.underway(v.level(), v.hall()), "the plague isn't under way at the hall");
		RandomSource random = RandomSource.create(7L);
		for (int i = 0; i < 20; i++) {
			Disasters.contagion(v.level(), v.hall(), v.plague(), VillageHalls.census(v.level(), v.hall()), random);
		}
		helper.assertTrue(Sickness.isPlagueIll(beside) && Sickness.isPlagueIll(behind), "the villagers 2 blocks from the ill one didn't catch the plague in 20 rounds");
		helper.assertFalse(Sickness.isIll(far), "a villager 10 blocks from the ill caught it");
		helper.assertTrue(Math.abs(Plague.chance(contagion(v), beside) - 0.2) < 1e-6, "a villager without Medicine catches it 1 in 5, not " + Plague.chance(contagion(v), beside));
		helper.succeed();
	}

	/** An ill villager the Physician has seen passes it on only to those sharing their house; one unseen passes it to anyone beside them. */
	//$ gametest_ticks_batch AREA '100' '"plagueSeen"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "plagueSeen")
	public void aSeenVillagerPassesItOnLess(GameTestHelper helper) {
		Village v = village(helper);
		Villager seen = villager(helper, 3.5, 3.5);
		Villager neighbour = villager(helper, 5.5, 3.5);
		Villager housemate = villager(helper, 1.5, 3.5);
		Villager unseen = villager(helper, 12.5, 3.5);
		Villager beside = villager(helper, 14.5, 3.5);
		Sickness.catchPlague(v.level(), seen);
		Sickness.catchPlague(v.level(), unseen);
		ModAttachments.PLAGUE_SEEN.set(seen, true);
		Beds.home(seen, helper.absolutePos(new BlockPos(2, 2, 2)));
		Beds.home(housemate, helper.absolutePos(new BlockPos(1, 2, 2)));
		Beds.home(neighbour, helper.absolutePos(new BlockPos(14, 2, 14)));
		start(v);
		RandomSource random = RandomSource.create(11L);
		List<Villager> all = List.of(seen, neighbour, housemate, unseen, beside);
		for (int i = 0; i < 20; i++) {
			Plague.spread(v.level(), contagion(v), all, random);
		}
		helper.assertTrue(Sickness.isPlagueIll(beside), "the unseen ill villager passed nothing on in 20 rounds");
		helper.assertFalse(Sickness.isIll(neighbour), "the seen ill villager passed it to a neighbour from another house");
		helper.assertTrue(Sickness.isPlagueIll(housemate), "the seen ill villager didn't pass it on in their own house");
		helper.succeed();
	}

	/** The Remedy cures a hexed and a plague-ill villager, from a player's hand and the Physician's chest; a honey bottle cures neither. */
	//$ gametest_ticks_batch AREA '100' '"plagueRemedy"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "plagueRemedy")
	public void theRemedyCuresWhereHoneyDoesNot(GameTestHelper helper) {
		Village v = village(helper);
		start(v);
		Villager hexed = villager(helper, 4.5, 4.5);
		Villager ill = villager(helper, 8.5, 4.5);
		Sickness.hex(v.level(), hexed);
		Sickness.recover(v.level(), ill); // (the plague's first case may have been them)
		Sickness.catchPlague(v.level(), ill);
		helper.assertTrue(Sickness.isHexed(hexed) && Sickness.isPlagueIll(ill), "the patients aren't ill");
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		// A honey bottle in a player's hand: nothing.
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEY_BOTTLE));
		helper.assertTrue(RemedyItem.give(player, v.level(), InteractionHand.MAIN_HAND, hexed) == InteractionResult.PASS, "a honey bottle was given as a Remedy");
		// A Physician with only a honey bottle in the chest by the stand: seen and sent home, not cured.
		helper.setBlock(new BlockPos(2, 2, 10), Blocks.BREWING_STAND);
		helper.setBlock(new BlockPos(3, 2, 10), Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) v.level().getBlockEntity(helper.absolutePos(new BlockPos(3, 2, 10)));
		chest.setItem(0, new ItemStack(Items.HONEY_BOTTLE));
		Villager physician = villager(helper, 2.5, 11.5);
		Stations.assign(v.level(), physician, helper.absolutePos(new BlockPos(2, 2, 10)), ModVillagers.PHYSICIAN);
		helper.assertTrue(physician.getVillagerData().getProfession() == ModVillagers.PHYSICIAN, "no Physician at the brewing stand");
		PhysicianWork.visit(v.level(), physician, ill);
		helper.assertTrue(Sickness.isPlagueIll(ill), "a honey bottle cured the plague");
		helper.assertTrue(Plague.seen(ill), "the Physician's visit didn't see the plague-ill villager");
		helper.assertTrue(Sickness.isHexed(hexed), "the hex lifted by itself");
		// The Remedy from the player's hand cures the hex; from the chest, the plague.
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMEDY));
		helper.assertTrue(RemedyItem.give(player, v.level(), InteractionHand.MAIN_HAND, hexed) == InteractionResult.SUCCESS, "the Remedy wasn't given");
		helper.assertFalse(Sickness.isIll(hexed), "the Remedy didn't cure the hex");
		helper.assertTrue(player.getInventory().countItem(ModItems.REMEDY) == 0 && player.getInventory().countItem(Items.GLASS_BOTTLE) == 1, "the Remedy wasn't used up for its bottle");
		Sickness.hex(v.level(), hexed);
		helper.assertFalse(Sickness.isIll(hexed), "a villager given a Remedy was hexed again within 10 days");
		chest.setItem(1, new ItemStack(ModItems.REMEDY));
		PhysicianWork.visit(v.level(), physician, ill);
		helper.assertFalse(Sickness.isIll(ill), "the Physician's Remedy didn't cure the plague");
		helper.assertTrue(Sickness.isImmune(ill, v.level().getGameTime() + Plague.WELL_FOR - 1) && !Sickness.isImmune(ill, v.level().getGameTime() + Plague.WELL_FOR),
			"the Remedy doesn't keep them well for exactly 10 days");
		helper.assertFalse(Sickness.catchPlague(v.level(), ill), "a villager kept well by the Remedy caught the plague");
		helper.succeed();
	}

	/** The Physician brews a Remedy from its makings in the chest when there's none. */
	//$ gametest_ticks_batch AREA '100' '"plagueBrew"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "plagueBrew")
	public void thePhysicianBrewsARemedy(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(new BlockPos(2, 2, 10), Blocks.BREWING_STAND);
		helper.setBlock(new BlockPos(3, 2, 10), Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) v.level().getBlockEntity(helper.absolutePos(new BlockPos(3, 2, 10)));
		chest.setItem(0, new ItemStack(Items.GLASS_BOTTLE));
		chest.setItem(1, new ItemStack(Items.GLISTERING_MELON_SLICE));
		chest.setItem(2, new ItemStack(Items.HONEY_BOTTLE));
		Villager physician = villager(helper, 2.5, 11.5);
		Stations.assign(v.level(), physician, helper.absolutePos(new BlockPos(2, 2, 10)), ModVillagers.PHYSICIAN);
		helper.assertFalse(PhysicianWork.ready(v.level(), physician, helper.absolutePos(new BlockPos(2, 2, 10))), "a Remedy brewed without a fermented spider eye");
		helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 1, "the makings were used up for nothing");
		chest.setItem(3, new ItemStack(Items.FERMENTED_SPIDER_EYE));
		helper.assertTrue(PhysicianWork.ready(v.level(), physician, helper.absolutePos(new BlockPos(2, 2, 10))), "no Remedy brewed from its makings");
		helper.assertTrue(RemedyItem.isRemedy(physician.getMainHandItem()), "the Physician has no Remedy in hand");
		helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 0 && chest.countItem(Items.FERMENTED_SPIDER_EYE) == 0 && chest.countItem(Items.GLASS_BOTTLE) == 1,
			"the makings weren't taken (the honey's bottle back)");
		helper.succeed();
	}

	/** The plague ends the day after the last of the ill is cured, not before. */
	//$ gametest_ticks_batch AREA '100' '"plagueEnds"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "plagueEnds")
	public void thePlagueEndsADayAfterTheLastCure(GameTestHelper helper) {
		Village v = village(helper);
		Villager sick = villager(helper, 4.5, 4.5);
		Sickness.catchPlague(v.level(), sick);
		start(v);
		long day = Chronicle.day(v.level());
		Disasters.round(v.level(), v.hall(), v.entity(), VillageHalls.census(v.level(), v.hall()));
		v.level().setDayTime(BASE + 3 * VillageNeeds.DAY + 1000); // three days on, still ill: still on
		Disasters.round(v.level(), v.hall(), v.entity(), VillageHalls.census(v.level(), v.hall()));
		helper.assertTrue(Plague.ID.equals(v.entity().disaster()), "the plague ended by itself with the ill still ill");
		Plague.cure(v.level(), sick);
		Disasters.round(v.level(), v.hall(), v.entity(), VillageHalls.census(v.level(), v.hall()));
		helper.assertTrue(Plague.ID.equals(v.entity().disaster()), "the plague ended the same day as the last cure");
		v.level().setDayTime(BASE + 4 * VillageNeeds.DAY + 1000);
		Disasters.round(v.level(), v.hall(), v.entity(), VillageHalls.census(v.level(), v.hall()));
		helper.assertTrue(v.entity().disaster() == null, "the plague didn't end the day after the last cure (began day " + day + ")");
		helper.assertTrue(v.entity().lastDisasterDay() == Chronicle.day(v.level()), "the end wasn't recorded today");
		helper.succeed();
	}

	/** With a Physician in the village the plague starts with its first case, foretold a day before it spreads. */
	//$ gametest_ticks_batch AREA '100' '"plagueForetold"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "plagueForetold")
	public void aPhysicianForetellsItAtItsFirstCase(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(new BlockPos(2, 2, 10), Blocks.BREWING_STAND);
		Villager physician = villager(helper, 2.5, 11.5);
		Stations.assign(v.level(), physician, helper.absolutePos(new BlockPos(2, 2, 10)), ModVillagers.PHYSICIAN);
		Villager a = villager(helper, 6.5, 6.5);
		Villager b = villager(helper, 10.5, 6.5);
		start(v);
		long day = Chronicle.day(v.level());
		helper.assertTrue(v.entity().disasterSince() == day + 1, "a Physician didn't foretell the outbreak a day ahead: since " + v.entity().disasterSince() + ", today " + day);
		long cases = List.of(physician, a, b).stream().filter(Sickness::isPlagueIll).count();
		helper.assertTrue(cases == 1, "the plague didn't start with one first case: " + cases);
		helper.succeed();
	}

	/** The crafting recipe: a glass bottle, a glistering melon slice, a honey bottle and a fermented spider eye make a Remedy. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theRecipeCrafts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.GLISTERING_MELON_SLICE),
			new ItemStack(Items.HONEY_BOTTLE), new ItemStack(Items.FERMENTED_SPIDER_EYE)));
		var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
		helper.assertTrue(recipe.isPresent(), "no recipe for the Remedy's makings");
		ItemStack out = recipe.get().value().assemble(input, level.registryAccess());
		helper.assertTrue(out.is(ModItems.REMEDY) && out.getCount() == 1, "the makings made " + out);
		CraftingInput missing = CraftingInput.of(2, 2, List.of(new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.GLISTERING_MELON_SLICE),
			new ItemStack(Items.HONEY_BOTTLE), ItemStack.EMPTY));
		helper.assertFalse(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, missing, level).map(r -> r.value().assemble(missing, level.registryAccess()).is(ModItems.REMEDY)).orElse(false),
			"a Remedy without its fermented spider eye");
		helper.succeed();
	}

	/** A player who drinks a Remedy loses poison, wither, hunger and nausea, and gets the bottle back. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void drinkingItCuresAPlayer(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
		player.addEffect(new MobEffectInstance(MobEffects.WITHER, 600));
		player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600));
		player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 600));
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600));
		ItemStack left = ModItems.REMEDY.finishUsingItem(new ItemStack(ModItems.REMEDY), helper.getLevel(), player);
		helper.assertFalse(player.hasEffect(MobEffects.POISON) || player.hasEffect(MobEffects.WITHER) || player.hasEffect(MobEffects.HUNGER)
			|| player.hasEffect(MobEffects.CONFUSION), "the Remedy left a bad effect: " + player.getActiveEffects());
		helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "the Remedy took a good effect away");
		helper.assertTrue(left.is(Items.GLASS_BOTTLE), "no bottle back: " + left);
		helper.succeed();
	}

	/** What the Remedy and the Physician leave on a villager (kept well, seen) survives a save and load. */
	//$ gametest_ticks_batch AREA '100' '"plagueSaved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "plagueSaved")
	public void itSurvivesASave(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager v = villager(helper, 4.5, 4.5);
		Sickness.catchPlague(helper.getLevel(), v);
		ModAttachments.PLAGUE_SEEN.set(v, true);
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
		loaded.load(tag);
		helper.assertTrue(Sickness.isPlagueIll(loaded) && Plague.seen(loaded), "the plague or the Physician's visit was lost in a save");
		Plague.cure(helper.getLevel(), v);
		Villager again = EntityType.VILLAGER.create(helper.getLevel());
		again.load(v.saveWithoutId(new CompoundTag()));
		helper.assertTrue(Sickness.isImmune(again, helper.getLevel().getGameTime()) && !Plague.seen(again), "the Remedy's 10 days were lost in a save");
		loaded.discard();
		again.discard();
		v.discard();
		helper.succeed();
	}

	/** Config physicians off: the brewing stand doesn't offer the Physician; on, it does with a glistering melon slice. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theSwitchTakesTheJobAway(GameTestHelper helper) {
		boolean was = PhysicianWork.ENABLED;
		try {
			PhysicianWork.ENABLED = true;
			Stations.Station station = Stations.at(Blocks.BREWING_STAND).orElseThrow();
			helper.assertTrue(station.jobFor(new ItemStack(Items.GLISTERING_MELON_SLICE)).map(j -> j.profession().get() == ModVillagers.PHYSICIAN).orElse(false),
				"a glistering melon slice doesn't pick the Physician");
			helper.assertTrue(Language.getInstance().has(Stations.itemKey(station.jobFor(new ItemStack(Items.GLISTERING_MELON_SLICE)).orElseThrow())),
				"the tooltip's item line has no text");
			PhysicianWork.ENABLED = false;
			helper.assertFalse(station.jobFor(new ItemStack(Items.GLISTERING_MELON_SLICE)).map(j -> j.profession().get() == ModVillagers.PHYSICIAN).orElse(false),
				"config physicians off and a glistering melon slice still picks the Physician");
			helper.assertTrue(station.jobs().stream().filter(Stations::available).noneMatch(j -> j.profession().get() == ModVillagers.PHYSICIAN),
				"config physicians off and the brewing stand's tooltip still lists the Physician");
		} finally {
			PhysicianWork.ENABLED = was;
		}
		helper.succeed();
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void everyNewSentenceHasItsText(GameTestHelper helper) {
		Language lang = Language.getInstance();
		for (String key : List.of("entity.minecraft.villager.physician", "entity.minecraft.zombie_villager.physician",
			"station.aliveworkplace.item.aliveworkplace.physician", "aliveworkplace.config.physicians", "aliveworkplace.config.physicians.tooltip",
			"message.aliveworkplace.physician.state.open", "message.aliveworkplace.physician.state.visiting", "message.aliveworkplace.physician.state.cured",
			"message.aliveworkplace.physician.state.sent_home", "request.aliveworkplace.remedy_physician", "item.aliveworkplace.remedy",
			"message.aliveworkplace.remedy.cured", "message.aliveworkplace.remedy.kept_well",
			"disaster.aliveworkplace.plague.name", "disaster.aliveworkplace.plague.start", "disaster.aliveworkplace.plague.foretold",
			"disaster.aliveworkplace.plague.end", "chronicle.aliveworkplace.plague.start", "chronicle.aliveworkplace.plague.foretold",
			"chronicle.aliveworkplace.plague.end", "screen.aliveworkplace.hall.disaster_well", "screen.aliveworkplace.hall.plague",
			"screen.aliveworkplace.hall.plague_seen", "screen.aliveworkplace.hall.plague_bed")) {
			helper.assertTrue(lang.has(key), "no text for " + key);
		}
		helper.succeed();
	}
}
