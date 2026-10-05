package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Furnaces;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * ROADMAP 28.5, partners at work in the post, the forge and the kitchen: each show starts from the worker's own work
 * (the postman's air mail and round, a Fire partner's smelting on the spot, the chef's cooking, the smiths' forging and
 * mending, the fletcher's fletching, the tinkerer's golem), played by a pastured partner of the show's type.
 */
public class PartnersForgeCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static ResourceLocation show(String name) {
		return AliveWorkplace.id(name);
	}

	private static String why(Set<ResourceLocation> started) {
		return started + " (" + PartnerShows.lastRefusal() + ")";
	}

	/** No show display is left anywhere near the test once its shows are over. */
	private static void noDisplaysLeft(GameTestHelper helper) {
		helper.assertTrue(PartnerShows.running(helper.getLevel()) == 0, "a show is still on");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(Display.ItemDisplay.class, helper.getBounds().inflate(32),
			d -> d.getTags().contains(PartnerShows.DISPLAY_TAG)).isEmpty(), "a show's display was left behind");
	}

	// --- the postman -----------------------------------------------------------------------------

	private record Post(Villager postman, PostOffice office, MailboxBlockEntity bobs, UUID parcel) {
	}

	/** A Postal Desk with its postman, Alice's and Bob's mailboxes, and a parcel of diamonds from Alice to Bob. */
	private static Post post(GameTestHelper helper, BlockPos to) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos desk = new BlockPos(2, 2, 2);
		helper.setBlock(desk, ModBlocks.POSTAL_DESK);
		Villager postman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, postman, helper.absolutePos(desk), ModVillagers.POSTAL_DESK_POI, ModVillagers.POSTMAN);
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		BlockPos from = new BlockPos(4, 2, 6);
		PostOffice office = PostOffice.get(level.getServer());
		for (Object[] box : new Object[][]{{from, alice, "Alice"}, {to, bob, "Bob"}}) {
			helper.setBlock((BlockPos) box[0], ModBlocks.MAILBOX);
			((MailboxBlockEntity) helper.getBlockEntity((BlockPos) box[0])).setOwner((UUID) box[1], (String) box[2]);
			office.register((UUID) box[1], GlobalPos.of(level.dimension(), helper.absolutePos((BlockPos) box[0])));
		}
		var parcel = office.post(alice, "Alice", bob, "Bob", GlobalPos.of(level.dimension(), helper.absolutePos(from)), level.getGameTime(),
			List.of(new ItemStack(Items.DIAMOND, 2)));
		return new Post(postman, office, helper.getBlockEntity(to), parcel.id());
	}

	/**
	 * Air mail: a Pidgey by the desk takes off with a bundle when a parcel for outside the round is handed in, climbs out
	 * of sight, and lands back where it took off, empty-handed; the parcel is in Bob's mailbox.
	 */
	//$ gametest_ticks_batch AREA '1600' '"partners_air_mail"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_air_mail")
	public void aPidgeyTakesTheAirMailUpAndLandsBackEmptyHanded(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		int round = PostOffice.ROUND;
		PostOffice.ROUND = 8;
		PartnerShowsCompatTests.after(helper, () -> PostOffice.ROUND = round);
		Post p = post(helper, new BlockPos(14, 2, 14)); // more than 8 from the desk: outside the round
		PartnersAtWorkCompatTests.partner(helper, new BlockPos(6, 2, 2), "pidgey");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = PartnersAtWorkCompatTests.watch(helper, p.postman(), "pidgey", carried);
		double[] highest = {0};
		double[] takeOffY = {Double.NaN};
		helper.onEachTick(() -> {
			if (helper.getTick() > 5) {
				PokemonEntity pidgey = PartnersAtWorkCompatTests.pokemon(helper, "pidgey");
				double up = PartnerShows.flying(pidgey);
				if (up > 0 && Double.isNaN(takeOffY[0])) {
					takeOffY[0] = pidgey.getY() - up;
				}
				highest[0] = Math.max(highest[0], up);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("postman_flying_takes_the_air_mail")), "no air mail show: " + why(started));
			helper.assertTrue(carried.contains(Items.BUNDLE), "the Pidgey carried " + carried + ", not a bundle");
			helper.assertTrue(highest[0] >= 6, "the Pidgey only climbed " + highest[0] + " blocks");
			helper.assertTrue(p.bobs().countItem(Items.DIAMOND) == 2, "Bob's mailbox is still empty");
			noDisplaysLeft(helper);
			PokemonEntity pidgey = PartnersAtWorkCompatTests.pokemon(helper, "pidgey");
			helper.assertTrue(PartnerShows.flying(pidgey) == 0 && Math.abs(pidgey.getY() - takeOffY[0]) < 1.5,
				"the Pidgey didn't land back: y " + pidgey.getY() + ", took off at " + takeOffY[0]);
		});
	}

	/**
	 * B52: air mail never carries a Pidgey out of what its pasture lets it roam (Cobblemon sends a pastured Pokémon that
	 * leaves that box back to the PC). With the pasture's range cut to 16 blocks the Pidgey climbs only as high as that
	 * allows, stays pastured, and lands back where it took off.
	 */
	//$ gametest_ticks_batch AREA '1600' '"partners_air_mail_low"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_air_mail_low")
	public void airMailKeepsThePidgeyWithinItsPasturesRange(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.showsOn(helper);
		int round = PostOffice.ROUND;
		PostOffice.ROUND = 8;
		var config = com.cobblemon.mod.common.Cobblemon.INSTANCE.getConfig();
		int wander = config.getPastureMaxWanderDistance();
		config.setPastureMaxWanderDistance(16); // read when the Pasture Block is placed
		PartnerShowsCompatTests.after(helper, () -> {
			PostOffice.ROUND = round;
			config.setPastureMaxWanderDistance(wander);
		});
		Post p = post(helper, new BlockPos(14, 2, 14)); // outside the round: air mail
		PartnersAtWorkCompatTests.partner(helper, new BlockPos(6, 2, 2), "pidgey");
		double top = helper.absolutePos(new BlockPos(6, 2, 2)).getY() + 16; // the pasture's range, upward
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = PartnersAtWorkCompatTests.watch(helper, p.postman(), "pidgey", carried);
		double[] highest = {0};
		double[] takeOffY = {Double.NaN};
		helper.onEachTick(() -> {
			if (helper.getTick() > 5) {
				PokemonEntity pidgey = PartnersAtWorkCompatTests.pokemon(helper, "pidgey");
				helper.assertTrue(pidgey.getTethering() != null, "the Pidgey left its pasture at y " + pidgey.getY() + " (top " + top + ")");
				double up = PartnerShows.flying(pidgey);
				if (up > 0 && Double.isNaN(takeOffY[0])) {
					takeOffY[0] = pidgey.getY() - up;
				}
				highest[0] = Math.max(highest[0], up);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("postman_flying_takes_the_air_mail")), "no air mail show: " + why(started));
			helper.assertTrue(highest[0] > 0, "the Pidgey never took off");
			helper.assertTrue(takeOffY[0] + highest[0] < top, "the Pidgey climbed to " + (takeOffY[0] + highest[0]) + ", its pasture's top is " + top);
			helper.assertTrue(p.bobs().countItem(Items.DIAMOND) == 2, "Bob's mailbox is still empty");
			noDisplaysLeft(helper);
			PokemonEntity pidgey = PartnersAtWorkCompatTests.pokemon(helper, "pidgey");
			helper.assertTrue(PartnerShows.flying(pidgey) == 0 && Math.abs(pidgey.getY() - takeOffY[0]) < 1.5,
				"the Pidgey didn't land back: y " + pidgey.getY() + ", took off at " + takeOffY[0]);
		});
	}

	/** On the round, a Pidgey flies ahead to the mailbox the postman is carrying a parcel to. */
	//$ gametest_ticks_batch AREA '1600' '"partners_deliver"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_deliver")
	public void aPidgeyFliesAheadToTheNextMailbox(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		Post p = post(helper, new BlockPos(9, 2, 9)); // on the round
		PartnersAtWorkCompatTests.partner(helper, new BlockPos(12, 2, 4), "pidgey");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = PartnersAtWorkCompatTests.watch(helper, p.postman(), "pidgey", carried);
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("postman_flying_flies_ahead")), "no fly-ahead show: " + why(started));
			helper.assertFalse(started.contains(show("postman_flying_takes_the_air_mail")), "a parcel on the round went by air");
			helper.assertTrue(p.bobs().countItem(Items.DIAMOND) == 2, "Bob's mailbox is still empty");
			noDisplaysLeft(helper);
		});
	}

	// --- Fire partners at the furnace ------------------------------------------------------------

	/** A Charmander pastured by an armorer's blast furnace breathes fire into it each time it smelts the 8 on the spot. */
	//$ gametest_ticks_batch AREA '600' '"partners_fire_smelt"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "partners_fire_smelt")
	public void aCharmanderBreathesFireIntoTheArmorersBlastFurnace(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		ServerLevel level = helper.getLevel();
		BlockPos furnace = new BlockPos(2, 2, 2);
		BlockPos chest = new BlockPos(2, 2, 4);
		helper.setBlock(furnace, Blocks.BLAST_FURNACE);
		helper.setBlock(chest, Blocks.CHEST);
		AbstractFurnaceBlockEntity oven = helper.getBlockEntity(furnace);
		oven.setItem(0, new ItemStack(Items.RAW_IRON, 20));
		Villager armorer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		armorer.setNoAi(true); // the smelting below is the armorer's own tending, without its walk
		Jobs.employ(level, armorer, helper.absolutePos(furnace), PoiTypes.ARMORER, VillagerProfession.ARMORER);
		PartnersAtWorkCompatTests.partner(helper, new BlockPos(10, 2, 3), "charmander");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = PartnersAtWorkCompatTests.watch(helper, armorer, "charmander", carried);
		boolean[] tended = {false};
		helper.onEachTick(() -> {
			if (!tended[0] && helper.getTick() > 20) {
				tended[0] = true;
				var supplies = SupplyContainers.find(level, helper.absolutePos(furnace), null);
				Furnaces.tend(level, helper.absolutePos(furnace), supplies, Furnaces::isOre, armorer);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("furnace_fire_breathes_into_the_furnace")), "no fire show: " + why(started));
			Container box = helper.getBlockEntity(chest);
			helper.assertTrue(box.countItem(Items.IRON_INGOT) == 8, "the chest has " + box.countItem(Items.IRON_INGOT) + " ingots");
			helper.assertTrue(carried.isEmpty(), "the Charmander carried " + carried);
			noDisplaysLeft(helper);
		});
	}

	/** With nothing for the Fire partner to smelt (an empty furnace), it doesn't come: the show follows the work. */
	//$ gametest_ticks_batch AREA '200' '"partners_fire_idle"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "partners_fire_idle")
	public void noFireShowWhenThereIsNothingToSmelt(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		ServerLevel level = helper.getLevel();
		BlockPos furnace = new BlockPos(2, 2, 2);
		helper.setBlock(furnace, Blocks.BLAST_FURNACE);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Villager armorer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		armorer.setNoAi(true);
		Jobs.employ(level, armorer, helper.absolutePos(furnace), PoiTypes.ARMORER, VillagerProfession.ARMORER);
		PartnersAtWorkCompatTests.partner(helper, new BlockPos(10, 2, 3), "charmander");
		helper.runAfterDelay(40, () -> {
			var supplies = SupplyContainers.find(level, helper.absolutePos(furnace), null);
			Furnaces.tend(level, helper.absolutePos(furnace), supplies, Furnaces::isOre, armorer);
			helper.assertTrue(PartnerShows.lastShow(armorer) == null, "a fire show with nothing smelted: " + PartnerShows.lastShow(armorer));
			helper.succeed();
		});
	}

	// --- the chef --------------------------------------------------------------------------------

	private static Villager chef(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos stove = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(stove, ModBlocks.KITCHEN_STOVE);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.HONEY_BOTTLE, 2));
		chest.setItem(1, new ItemStack(Items.BROWN_MUSHROOM, 2));
		chest.setItem(2, new ItemStack(Items.WHEAT, 2));
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(stove), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		return chef;
	}

	private static Item bait() {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_bait"));
	}

	/** A Charmander by the stove fans the flames while the chef cooks Poké Bait. */
	//$ gametest_ticks_batch AREA '2400' '"partners_cook_fire"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "partners_cook_fire")
	public void aCharmanderFansTheChefsFlames(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		Villager chef = chef(helper);
		PartnersAtWorkCompatTests.partner(helper, new BlockPos(10, 2, 3), "charmander");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = PartnersAtWorkCompatTests.watch(helper, chef, "charmander", carried);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("chef_fire_fans_the_flames")), "no flames show: " + why(started));
			helper.assertTrue(chest.countItem(bait()) == 8, chest.countItem(bait()) + " Poké Bait cooked");
			helper.assertTrue(carried.isEmpty(), "the Charmander carried " + carried);
			noDisplaysLeft(helper);
		});
	}

	/** A Rattata by the stove carries the cooked Poké Bait to the chest. */
	//$ gametest_ticks_batch AREA '2400' '"partners_cook_normal"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "partners_cook_normal")
	public void aRattataCarriesTheChefsDishToTheChest(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		int before = PartnerShows.deliveries();
		Villager chef = chef(helper);
		PartnersAtWorkCompatTests.partner(helper, new BlockPos(10, 2, 3), "rattata");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = PartnersAtWorkCompatTests.watch(helper, chef, "rattata", carried);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("chef_normal_carries_the_dish")), "no dish show: " + why(started));
			helper.assertTrue(carried.contains(bait()), "the Rattata carried " + carried + ", not the Poké Bait");
			helper.assertTrue(PartnerShows.deliveries() > before, "the Rattata never set the dish down at the chest");
			helper.assertTrue(chest.countItem(bait()) == 8, chest.countItem(bait()) + " Poké Bait in the chest");
			noDisplaysLeft(helper);
		});
	}

	// --- the smiths ------------------------------------------------------------------------------

	/** A Magnemite sparks at the Ball Smith's bench, then carries the batch of Poké Balls to the chest. */
	//$ gametest_ticks_batch AREA '1600' '"partners_forge"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_forge")
	public void aMagnemiteSparksAtTheBallSmithsBenchAndCarriesTheBalls(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		int before = PartnerShows.deliveries();
		helper.setDayTime(2000);
		BlockPos bench = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(bench, ModBlocks.BALL_WORKBENCH);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(CobblemonItems.RED_APRICORN, 8));
		chest.setItem(1, new ItemStack(Items.COPPER_INGOT, 2));
		Villager smith = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), smith, helper.absolutePos(bench), ModVillagers.BALL_WORKBENCH_POI, ModVillagers.BALL_SMITH);
		PartnersAtWorkCompatTests.partner(helper, new BlockPos(10, 2, 3), "magnemite");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = PartnersAtWorkCompatTests.watch(helper, smith, "magnemite", carried);
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("smith_partner_sparks_at_the_table")), "no forge show: " + why(started));
			helper.assertTrue(carried.contains(CobblemonItems.POKE_BALL), "the Magnemite carried " + carried + ", not the Poké Balls");
			helper.assertTrue(PartnerShows.deliveries() > before, "the Magnemite never set the balls down at the chest");
			helper.assertTrue(chest.countItem(CobblemonItems.POKE_BALL) == 8, chest.countItem(CobblemonItems.POKE_BALL) + " Poké Balls made");
			noDisplaysLeft(helper);
		});
	}

	/** A Machop holds the worn pickaxe at the weaponsmith's grindstone while it's mended. */
	//$ gametest_ticks_batch AREA '1600' '"partners_mend"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_mend")
	public void aMachopHoldsTheWornPieceAtTheGrindstone(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		helper.setDayTime(2000);
		BlockPos grindstone = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(grindstone, Blocks.GRINDSTONE);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		pickaxe.setDamageValue(200);
		chest.setItem(0, pickaxe);
		chest.setItem(1, new ItemStack(Items.IRON_INGOT, 3));
		Villager smith = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), smith, helper.absolutePos(grindstone), PoiTypes.WEAPONSMITH, VillagerProfession.WEAPONSMITH);
		PartnersAtWorkCompatTests.partner(helper, new BlockPos(10, 2, 3), "machop");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = PartnersAtWorkCompatTests.watch(helper, smith, "machop", carried);
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("weaponsmith_partner_holds_the_worn_piece")), "no mending show: " + why(started));
			helper.assertTrue(carried.contains(Items.IRON_PICKAXE), "the Machop held " + carried + ", not the pickaxe");
			helper.assertTrue(ModAttachments.ITEMS_MENDED.getOrElse(smith, 0) == 1, "mended " + ModAttachments.ITEMS_MENDED.getOrElse(smith, 0));
			noDisplaysLeft(helper);
		});
	}

	// --- the fletcher ----------------------------------------------------------------------------

	/** A Pidgey brings a feather to the fletching table while the fletcher makes the guard's bow. */
	//$ gametest_ticks_batch AREA '1600' '"partners_fletch"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_fletch")
	public void aPidgeyBringsAFeatherToTheFletcher(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		PartnerShowsCompatTests.showsOn(helper);
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		helper.setDayTime(2000);
		BlockPos table = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(table, Blocks.FLETCHING_TABLE);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.STICK, 3));
		chest.setItem(1, new ItemStack(Items.STRING, 3));
		Villager fletcher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), fletcher, helper.absolutePos(table), PoiTypes.FLETCHER, VillagerProfession.FLETCHER);
		// The guard's post across the area, inside it (B48): its 17 x 17 floor is all the test keeps loaded and ticking. A
		// post at (19, 19) was off the floor, in a chunk that often isn't ticked, where the guard and then the fletcher
		// bringing the bow stood frozen.
		BlockPos post = new BlockPos(15, 2, 15);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		helper.setBlock(new BlockPos(15, 2, 13), Blocks.CHEST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 14));
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Container guardsChest = helper.getBlockEntity(new BlockPos(15, 2, 13));
		PartnersAtWorkCompatTests.partner(helper, new BlockPos(10, 2, 3), "pidgey");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = PartnersAtWorkCompatTests.watch(helper, fletcher, "pidgey", carried);
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("fletcher_flying_brings_a_feather")), "no feather show: " + why(started));
			helper.assertTrue(carried.contains(Items.FEATHER), "the Pidgey brought " + carried + ", not a feather");
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.BOW) || guardsChest.countItem(Items.BOW) == 1,
				"no bow for the guard (B74): guard holds " + guard.getItemBySlot(EquipmentSlot.MAINHAND) + " / " + guard.getItemBySlot(EquipmentSlot.OFFHAND)
					+ ", guard's chest " + contents(guardsChest) + ", fletcher's chest " + contents(chest) + ", fletcher holds " + fletcher.getMainHandItem()
					+ ", fletcher at " + helper.relativePos(fletcher.blockPosition()) + ", day time " + helper.getLevel().getDayTime() % 24000);
			noDisplaysLeft(helper);
		});
	}

	// --- the tinkerer ----------------------------------------------------------------------------

	/** A Magnemite sparks over the iron golem the tinkerer is patching up. */
	//$ gametest_ticks_batch AREA '1600' '"partners_tinker"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_tinker")
	public void aMagnemiteSparksOverTheGolemBeingMended(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		helper.setDayTime(2000);
		BlockPos bench = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(bench, ModBlocks.TINKERS_BENCH);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
		Villager tinkerer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), tinkerer, helper.absolutePos(bench), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(9, 2, 9));
		golem.setNoAi(true);
		golem.setHealth(30f);
		PartnersAtWorkCompatTests.partner(helper, new BlockPos(13, 2, 3), "magnemite");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = PartnersAtWorkCompatTests.watch(helper, tinkerer, "magnemite", carried);
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("tinkerer_partner_sparks_over_the_work")), "no sparks show: " + why(started));
			helper.assertTrue(golem.getHealth() >= golem.getMaxHealth(), "the golem's at " + golem.getHealth());
			noDisplaysLeft(helper);
			golem.discard();
		});
	}

	/** A chest's stacks, for failure messages. */
	private static List<ItemStack> contents(Container c) {
		List<ItemStack> out = new java.util.ArrayList<>();
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (!c.getItem(i).isEmpty()) {
				out.add(c.getItem(i));
			}
		}
		return out;
	}
}
