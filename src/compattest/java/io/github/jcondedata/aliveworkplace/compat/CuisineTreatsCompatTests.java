package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonPartners;
import io.github.jcondedata.aliveworkplace.craft.CuisineDishes;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.people.Luxuries;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.PartnerTreats;
import io.github.jcondedata.aliveworkplace.work.Partners;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 38.10 with the real Cobblecuisine 2.0.1: a partner Pokémon helping a builder at work is fed a Poké Puff or a
 * shake from the village store once a day (friendship, its treat day in its own saved data), never from an empty store;
 * a Burgher household takes a Deluxe Shake when due (every 4 days); the switch {@code cuisineDishes} off stops both.
 */
public class CuisineTreatsCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos BENCH = new BlockPos(9, 2, 6);
	private static final BlockPos STOREHOUSE = new BlockPos(12, 2, 2);
	private static final BlockPos STORE_CHEST = new BlockPos(12, 2, 4);
	private static final ResourceLocation DELUXE = AliveWorkplace.id("deluxe_shake");
	private static final ResourceLocation ECLAIR = AliveWorkplace.id("clodsire_eclair");

	private static Item cc(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblecuisine", path));
		if (item == Items.AIR) {
			throw new AssertionError("no cobblecuisine:" + path);
		}
		return item;
	}

	/** A Storehouse and its chest (the village store without a hall). */
	private static Container store(GameTestHelper helper) {
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		return helper.getBlockEntity(STORE_CHEST);
	}

	private static Villager worker(GameTestHelper helper, BlockPos at) {
		helper.setBlock(BENCH, Blocks.CRAFTING_TABLE);
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(2));
		v.refreshBrain(helper.getLevel()); // the trade's brain, as the game gives one with every job (B307)
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(BENCH)));
		v.getBrain().setActiveActivityIfPossible(Activity.WORK);
		return v;
	}

	/**
	 * The Done when: with an empty store a helping Machop gets nothing; with three Poké Puffs it is fed one when its
	 * worker's day is counted (friendship up, the day noted), a second count the same day takes nothing, the next day
	 * takes one more (a shake counts as well), and the day survives a save and load of the Pokémon.
	 */
	//$ gametest_ticks_batch AREA '200' '"cuisine_partner_treat"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cuisine_partner_treat")
	public void aPartnerIsFedOnceADayFromTheStore(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlockPos by = helper.absolutePos(new BlockPos(1, 2, 8));
		owner.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		PastureCompatTests.pastured(helper, pasture, owner, "machop", Direction.NORTH);
		Container store = store(helper);
		Villager builder = worker(helper, new BlockPos(9, 2, 8));
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(CuisineDishes.ENABLED, "cuisineDishes should be on");
			PokemonEntity machop = PartnersAtWorkCompatTests.pokemon(helper, "machop");
			Pokemon p = machop.getPokemon();
			p.setFriendship(50, true);
			long today = Chronicle.day(level);
			Partners.forgetCounted();
			Partners.countDay(builder);
			helper.assertTrue(CobblemonPartners.partnerDays(p) >= 1, "the builder's partner wasn't counted at work");
			helper.assertTrue(CobblemonPartners.treatDay(machop) == -1 && p.getFriendship() == 50,
				"fed from an empty store: day " + CobblemonPartners.treatDay(machop) + ", friendship " + p.getFriendship());

			store.setItem(0, new ItemStack(cc("sweet_pokepuff"), 3));
			Partners.forgetCounted();
			Partners.countDay(builder);
			helper.assertTrue(store.countItem(cc("sweet_pokepuff")) == 2, "puffs left: " + store.countItem(cc("sweet_pokepuff")));
			helper.assertTrue(CobblemonPartners.treatDay(machop) == today, "treat day " + CobblemonPartners.treatDay(machop) + ", today " + today);
			helper.assertTrue(p.getFriendship() == 50 + PartnerTreats.FRIENDSHIP, "friendship after the treat: " + p.getFriendship());
			Partners.forgetCounted();
			Partners.countDay(builder);
			helper.assertTrue(store.countItem(cc("sweet_pokepuff")) == 2 && p.getFriendship() == 50 + PartnerTreats.FRIENDSHIP,
				"fed twice the same day: " + store.countItem(cc("sweet_pokepuff")) + " puffs, friendship " + p.getFriendship());

			// the next day, a shake
			store.setItem(0, ItemStack.EMPTY);
			store.setItem(1, new ItemStack(cc("low_red_shake"), 1));
			BlockPos site = helper.absolutePos(BENCH);
			int fed = PartnerTreats.feed(level, site, Set.of("fighting"), 3, today + 1);
			helper.assertTrue(fed == 1 && store.countItem(cc("low_red_shake")) == 0 && CobblemonPartners.treatDay(machop) == today + 1,
				"the next day: " + fed + " fed, " + store.countItem(cc("low_red_shake")) + " shakes left, day " + CobblemonPartners.treatDay(machop));
			helper.assertTrue(!PartnerTreats.isTreat(new ItemStack(cc("deluxe_jewel_shake"))) && !PartnerTreats.isTreat(new ItemStack(cc("eclair"))),
				"a deluxe shake or the éclair (luxuries) is a partner treat");

			// kept with the Pokémon by Cobblemon
			CompoundTag saved = p.saveToNBT(level.registryAccess(), new CompoundTag());
			Pokemon back = Pokemon.Companion.loadFromNBT(level.registryAccess(), saved);
			long kept = back.getPersistentData().getLong(CobblemonPartners.TREAT_DAY);
			helper.assertTrue(kept == today + 1, "after a save and load the treat day is " + kept);
			level.getServer().getPlayerList().remove(owner);
			helper.succeed();
		});
	}

	/** The switch off: no treat for a partner, and no dish luxury is loaded, wanted or taken. */
	//$ gametest_ticks_batch AREA '200' '"cuisine_treat_off"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cuisine_treat_off")
	public void theSwitchOffFeedsNothing(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlockPos by = helper.absolutePos(new BlockPos(1, 2, 8));
		owner.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		PastureCompatTests.pastured(helper, pasture, owner, "machop", Direction.NORTH);
		Container store = store(helper);
		store.setItem(0, new ItemStack(cc("sweet_pokepuff"), 3));
		worker(helper, new BlockPos(9, 2, 8));
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(10, () -> {
			boolean was = CuisineDishes.ENABLED;
			CuisineDishes.ENABLED = false;
			try {
				PokemonEntity machop = PartnersAtWorkCompatTests.pokemon(helper, "machop");
				int fed = PartnerTreats.feed(level, helper.absolutePos(BENCH), Set.of("fighting"), 3, Chronicle.day(level));
				helper.assertTrue(fed == 0 && store.countItem(cc("sweet_pokepuff")) == 3 && CobblemonPartners.treatDay(machop) == -1,
					"fed with the switch off: " + fed);
				helper.assertTrue(Luxuries.get(DELUXE) == null && Luxuries.get(ECLAIR) == null, "the dish luxuries are loaded with the switch off");
				helper.assertTrue(!Luxuries.isLuxury(new ItemStack(cc("deluxe_jewel_shake"))), "a deluxe shake is a luxury with the switch off");
				helper.assertTrue(Luxuries.of(SocialClasses.get(AliveWorkplace.id("burgher"))).stream().noneMatch(l -> l.id().equals(DELUXE)),
					"a Burgher wants a deluxe shake with the switch off");
			} finally {
				CuisineDishes.ENABLED = was;
			}
			helper.assertTrue(Luxuries.get(DELUXE) != null, "back on, the deluxe shake is a luxury again");
			level.getServer().getPlayerList().remove(owner);
			helper.succeed();
		});
	}

	/**
	 * The Done when: the two dish luxuries load with Cobblecuisine (the six deluxe shakes every 4 days, the éclair every
	 * 8); a Burgher household takes one deluxe shake from the store at dawn when due, nothing two days later, one more on
	 * the fourth day; the day is in its {@code luxuries_had}.
	 */
	//$ gametest_ticks_batch AREA '100' '"cuisine_luxury_burgher"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "cuisine_luxury_burgher")
	public void aBurgherTakesADeluxeShakeWhenDue(GameTestHelper helper) {
		Luxuries.Luxury deluxe = Luxuries.get(DELUXE);
		Luxuries.Luxury eclair = Luxuries.get(ECLAIR);
		helper.assertTrue(deluxe != null && deluxe.everyDays() == 4, "the deluxe shake luxury: " + deluxe);
		helper.assertTrue(eclair != null && eclair.everyDays() == 8 && eclair.matches(new ItemStack(cc("eclair"))), "the éclair luxury: " + eclair);
		for (String shake : List.of("deluxe_jewel_shake", "deluxe_earthy_shake", "deluxe_violet_shake", "deluxe_verdant_shake", "deluxe_coral_shake", "deluxe_bb_shake")) {
			helper.assertTrue(deluxe.matches(new ItemStack(cc(shake))), shake + " isn't a deluxe shake luxury");
		}
		helper.assertTrue(!deluxe.matches(new ItemStack(cc("low_red_shake"))), "a plain shake counts as a deluxe one");
		SocialClasses.SocialClass burgher = SocialClasses.get(AliveWorkplace.id("burgher"));
		helper.assertTrue(burgher != null && Luxuries.of(burgher).contains(deluxe), "the Burgher doesn't want deluxe shakes: " + Luxuries.of(burgher));
		BlockPos hall = new BlockPos(2, 2, 2);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		Container store = store(helper);
		store.setItem(0, new ItemStack(cc("deluxe_coral_shake"), 2));
		Villager bea = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 8));
		bea.setNoAi(true);
		SocialClasses.seed(bea, burgher);
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(hall);
		SocialClasses.check(level, at, List.of(bea), 10, SocialClasses.SLICE);
		Map<ResourceLocation, Long> had = ModAttachments.LUXURIES_HAD.getOrElse(bea, Map.of());
		helper.assertTrue(store.countItem(cc("deluxe_coral_shake")) == 1 && Long.valueOf(10L).equals(had.get(DELUXE)),
			"day 10: " + store.countItem(cc("deluxe_coral_shake")) + " left, had " + had);
		SocialClasses.check(level, at, List.of(bea), 12, SocialClasses.SLICE);
		helper.assertTrue(store.countItem(cc("deluxe_coral_shake")) == 1, "taken on day 12, not due: " + store.countItem(cc("deluxe_coral_shake")));
		SocialClasses.check(level, at, List.of(bea), 14, SocialClasses.SLICE);
		had = ModAttachments.LUXURIES_HAD.getOrElse(bea, Map.of());
		helper.assertTrue(store.countItem(cc("deluxe_coral_shake")) == 0 && Long.valueOf(14L).equals(had.get(DELUXE)),
			"day 14: " + store.countItem(cc("deluxe_coral_shake")) + " left, had " + had);
		helper.succeed();
	}
}
