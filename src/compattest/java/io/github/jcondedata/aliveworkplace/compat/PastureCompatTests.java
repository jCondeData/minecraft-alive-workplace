package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonBlocks;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.block.PastureBlock;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.guard.GuardPartners;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Pastures;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Cobblemon Pasture Blocks (and Cobbleworkers, which is installed here): Pokémon partners and courier routes. */
public class PastureCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	static BlockPos pasture(GameTestHelper helper, BlockPos pos) {
		var part = PastureBlock.Companion.getPART();
		var dry = CobblemonBlocks.PASTURE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false);
		helper.setBlock(pos, dry.setValue(part, PastureBlock.PasturePart.BOTTOM));
		helper.setBlock(pos.above(), dry.setValue(part, PastureBlock.PasturePart.TOP));
		return helper.absolutePos(pos);
	}

	static Pokemon pastured(GameTestHelper helper, BlockPos pasture, ServerPlayer player, String species, Direction side) {
		Pokemon pokemon = PokemonProperties.Companion.parse(species + " level=10", " ", "=").create();
		PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
		pc.add(pokemon);
		PokemonPastureBlockEntity block = (PokemonPastureBlockEntity) helper.getLevel().getBlockEntity(pasture);
		helper.assertTrue(block != null && block.tether(player, pokemon, side), "could not put " + species + " in the pasture");
		return pokemon;
	}

	/** A Machop in a pasture near the Builder's Bench speeds the builder up; a Pikachu there doesn't. */
	//$ gametest_ticks AREA '200'
	@GameTest(template = AREA, timeoutTicks = 200)
	public void pasturedPokemonHelpTheVillagerNearby(GameTestHelper helper) {
		helper.assertTrue(FabricLoader.getInstance().isModLoaded("cobbleworkers"), "Cobbleworkers should be installed for this test");
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(2, 2, 2); // y = 1 is this area's floor
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, builder, helper.absolutePos(bench), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		helper.assertTrue(BuilderLevels.delay(100, builder) == 100, "a novice without help takes the full time");

		BlockPos pasture = pasture(helper, new BlockPos(10, 2, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		pastured(helper, pasture, player, "machop", Direction.NORTH);
		pastured(helper, pasture, player, "pikachu", Direction.WEST);

		helper.runAfterDelay(10, () -> {
			Partners.forget(builder);
			var helpers = Partners.helpers(builder);
			helper.assertTrue(helpers.size() == 1 && helpers.get(0).getString().equals("Machop"), "helpers: " + helpers);
			helper.assertTrue(BuilderLevels.delay(100, builder) == 85, "with Machop: " + BuilderLevels.delay(100, builder));
			helper.assertTrue(BuilderLevels.describe(builder).getString().contains("Machop"), "status: " + BuilderLevels.describe(builder).getString());
			helper.succeed();
		});
	}

	/** A Charmander pastured near a Miner's Bench smelts 8 of the ores in the furnace there each time it's tended. */
	//$ gametest_ticks AREA '200'
	@GameTest(template = AREA, timeoutTicks = 200)
	public void fireTypesSmeltAtTheFurnaces(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(2, 2, 2);
		BlockPos chest = new BlockPos(2, 2, 4);
		BlockPos furnace = new BlockPos(4, 2, 2);
		helper.setBlock(bench, ModBlocks.MINERS_BENCH);
		helper.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST);
		helper.setBlock(furnace, net.minecraft.world.level.block.Blocks.FURNACE);
		var oven = (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) helper.getBlockEntity(furnace);
		oven.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON, 20));
		BlockPos pasture = pasture(helper, new BlockPos(10, 2, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		pastured(helper, pasture, player, "charmander", Direction.NORTH);
		helper.runAfterDelay(10, () -> {
			var supplies = io.github.jcondedata.aliveworkplace.build.SupplyContainers.find(level, helper.absolutePos(bench), null);
			io.github.jcondedata.aliveworkplace.work.Furnaces.tend(level, helper.absolutePos(bench), supplies, io.github.jcondedata.aliveworkplace.work.Furnaces::isOre);
			net.minecraft.world.Container box = helper.getBlockEntity(chest);
			helper.assertTrue(box.countItem(net.minecraft.world.item.Items.IRON_INGOT) == 8, "the chest has " + box.countItem(net.minecraft.world.item.Items.IRON_INGOT) + " ingots");
			helper.assertTrue(oven.getItem(0).getCount() == 12, "the furnace has " + oven.getItem(0));
			helper.succeed();
		});
	}

	/**
	 * Air mail: with a Flying-type Pokémon pastured by the Postal Desk, a parcel for a mailbox outside the postman's
	 * round goes straight there when it's handed in, instead of waiting for the dawn mail. (Alone: it shrinks the round.)
	 */
	//$ gametest_ticks_batch AREA '1600' '"air_mail"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "air_mail")
	public void flyingPartnersSendAirMail(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int round = io.github.jcondedata.aliveworkplace.mail.PostOffice.ROUND;
		io.github.jcondedata.aliveworkplace.mail.PostOffice.ROUND = 8;
		BlockPos desk = new BlockPos(2, 2, 2);
		helper.setBlock(desk, ModBlocks.POSTAL_DESK);
		Villager postman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, postman, helper.absolutePos(desk), ModVillagers.POSTAL_DESK_POI, ModVillagers.POSTMAN);
		BlockPos pasture = pasture(helper, new BlockPos(6, 2, 2));
		pastured(helper, pasture, helper.makeMockServerPlayerInLevel(), "pidgey", Direction.SOUTH);

		java.util.UUID alice = java.util.UUID.randomUUID();
		java.util.UUID bob = java.util.UUID.randomUUID();
		BlockPos from = new BlockPos(4, 2, 6);
		BlockPos to = new BlockPos(14, 2, 14); // more than 8 from the desk: outside the round
		var office = io.github.jcondedata.aliveworkplace.mail.PostOffice.get(level.getServer());
		for (var box : new Object[][]{{from, alice, "Alice"}, {to, bob, "Bob"}}) {
			helper.setBlock((BlockPos) box[0], ModBlocks.MAILBOX);
			((io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity) helper.getBlockEntity((BlockPos) box[0])).setOwner((java.util.UUID) box[1], (String) box[2]);
			office.register((java.util.UUID) box[1], net.minecraft.core.GlobalPos.of(level.dimension(), helper.absolutePos((BlockPos) box[0])));
		}
		var parcel = office.post(alice, "Alice", bob, "Bob", net.minecraft.core.GlobalPos.of(level.dimension(), helper.absolutePos(from)), level.getGameTime(),
			List.of(new ItemStack(net.minecraft.world.item.Items.DIAMOND, 2)));
		var bobs = (io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity) helper.getBlockEntity(to);
		helper.succeedWhen(() -> {
			helper.assertTrue(bobs.countItem(net.minecraft.world.item.Items.DIAMOND) == 2, "Bob's mailbox is still empty (parcel " +
				(office.parcel(parcel.id()) == null ? "gone" : office.parcel(parcel.id()).status()) + ")");
			helper.assertTrue(office.parcel(parcel.id()) == null, "the parcel is still on its way");
			io.github.jcondedata.aliveworkplace.mail.PostOffice.ROUND = round;
		});
		helper.onEachTick(() -> {
			if (helper.getTick() >= 1590) {
				io.github.jcondedata.aliveworkplace.mail.PostOffice.ROUND = round; // put it back even if the test fails
			}
		});
	}

	/** A courier route from a pasture empties the chests around it (where Cobbleworkers' Pokémon put their finds). */
	//$ gametest_ticks AREA '1600'
	@GameTest(template = AREA, timeoutTicks = 1600)
	public void courierHaulsFromAPasture(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos pasture = pasture(helper, new BlockPos(11, 2, 11));
		BlockPos berries = new BlockPos(13, 2, 11);
		BlockPos apricorns = new BlockPos(11, 2, 14);
		BlockPos drop = new BlockPos(2, 2, 6);
		for (BlockPos p : List.of(berries, apricorns, drop)) {
			helper.setBlock(p, Blocks.CHEST);
		}
		((Container) helper.getBlockEntity(berries)).setItem(0, new ItemStack(CobblemonItems.ORAN_BERRY, 20));
		((Container) helper.getBlockEntity(apricorns)).setItem(3, new ItemStack(CobblemonItems.RED_APRICORN, 6));
		helper.assertTrue(Pastures.containers(level, pasture).size() == 2, "containers around the pasture: " + Pastures.containers(level, pasture));

		BlockPos desk = new BlockPos(2, 2, 2);
		helper.setBlock(desk, ModBlocks.POSTAL_DESK);
		Villager postman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, postman, helper.absolutePos(desk), ModVillagers.POSTAL_DESK_POI, ModVillagers.POSTMAN);
		ModAttachments.COURIER_ROUTES.set(postman, List.of(new RouteData(Optional.of(pasture), Optional.of(helper.absolutePos(drop)), List.of())));
		Container target = helper.getBlockEntity(drop);
		helper.succeedWhen(() -> {
			helper.assertTrue(target.countItem(CobblemonItems.ORAN_BERRY) == 20, target.countItem(CobblemonItems.ORAN_BERRY) + " berries delivered");
			helper.assertTrue(target.countItem(CobblemonItems.RED_APRICORN) == 6, target.countItem(CobblemonItems.RED_APRICORN) + " apricorns delivered");
		});
	}

	/**
	 * Guards fight beside their Pokémon: a Machop pastured near the Guard Post follows up the guard's hit on a husk with a
	 * move of its own (the damage counts as the guard's); a Pikachu there doesn't join in.
	 */
	//$ gametest_ticks_batch AREA '200' '"guard_partners"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "guard_partners")
	public void pasturedPokemonFightBesideTheGuard(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GuardPartners.reset();
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		BlockPos pasture = pasture(helper, new BlockPos(10, 2, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		pastured(helper, pasture, player, "machop", Direction.NORTH);
		pastured(helper, pasture, player, "pikachu", Direction.WEST);

		helper.runAfterDelay(10, () -> {
			Partners.forget(guard);
			helper.assertTrue(Partners.helpers(guard).size() == 1, "partners: " + Partners.helpers(guard));
			Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(7, 2, 7));
			husk.setNoAi(true);
			float before = husk.getHealth();
			husk.hurt(level.damageSources().mobAttack(guard), 1f);
			// One move on top of the guard's hit (husks' armor takes a little off each); Pikachu's would make it two.
			float lost = before - husk.getHealth();
			float move = GuardPartners.damage(10);
			helper.assertTrue(lost > 1f + 0.8f * move && lost < 1f + 1.5f * move, "husk lost " + lost + " health, one move is " + move);
			helper.assertTrue(husk.getLastDamageSource() != null && husk.getLastDamageSource().is(GuardPartners.POKEMON_MOVE),
				"last hit: " + husk.getLastDamageSource());
			helper.assertTrue(husk.getLastHurtByMob() == guard, "the husk should turn on the guard, not the Pokémon");
			husk.discard();
			helper.succeed();
		});
	}

	/** A shepherd shears a Wooloo kept in a pasture by the loom, like a sheep. */
	//$ gametest_ticks_batch AREA '1200' '"shepherd_wooloo"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "shepherd_wooloo")
	public void shepherdShearsAPasturedWooloo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.LOOM);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.SHEARS));
		Villager shepherd = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, shepherd, helper.absolutePos(new BlockPos(2, 2, 2)), net.minecraft.world.entity.ai.village.poi.PoiTypes.SHEPHERD,
			net.minecraft.world.entity.npc.VillagerProfession.SHEPHERD);
		BlockPos pasture = pasture(helper, new BlockPos(10, 2, 10));
		Pokemon wooloo = pastured(helper, pasture, helper.makeMockServerPlayerInLevel(), "wooloo", Direction.NORTH);
		helper.succeedWhen(() -> {
			var entity = wooloo.getEntity();
			helper.assertTrue(entity != null && !entity.readyForShearing(), "the Wooloo isn't sheared");
			helper.assertTrue(ModAttachments.ANIMALS_SHEARED.getOrElse(shepherd, 0) >= 1, "nothing sheared");
		});
	}

	/** A butcher does the pastured Pokémon's chores from Cobblemon's data: a Miltank milked, a Pidgey brushed for feathers. */
	//$ gametest_ticks_batch AREA '2400' '"herder_pokemon"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "herder_pokemon")
	public void butcherMilksAMiltankAndBrushesAPidgey(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		io.github.jcondedata.aliveworkplace.ranch.PokemonChores.forget();
		helper.assertTrue(io.github.jcondedata.aliveworkplace.ranch.PokemonChores.chores().size() > 100,
			"only " + io.github.jcondedata.aliveworkplace.ranch.PokemonChores.chores().size() + " chores read");
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.SMOKER);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.BUCKET));
		chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.BRUSH));
		for (int i = 0; i < 4; i++) {
			chest.setItem(2 + i, new ItemStack(net.minecraft.world.item.Items.MILK_BUCKET)); // no cows to milk: plenty already
		}
		Villager butcher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, butcher, helper.absolutePos(new BlockPos(2, 2, 2)), net.minecraft.world.entity.ai.village.poi.PoiTypes.BUTCHER,
			net.minecraft.world.entity.npc.VillagerProfession.BUTCHER);
		BlockPos pasture = pasture(helper, new BlockPos(10, 2, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		pastured(helper, pasture, player, "miltank", Direction.NORTH);
		pastured(helper, pasture, player, "pidgey", Direction.WEST);
		helper.succeedWhen(() -> {
			int milk = 0;
			boolean feathers = false;
			boolean wornBrush = false;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack stack = chest.getItem(i);
				milk += stack.is(net.minecraft.world.item.Items.MILK_BUCKET) ? 1 : 0;
				feathers |= stack.is(net.minecraft.world.item.Items.FEATHER);
				wornBrush |= stack.is(net.minecraft.world.item.Items.BRUSH) && stack.getDamageValue() >= 8;
			}
			helper.assertTrue(milk == 5, "milk buckets: " + milk);
			helper.assertTrue(feathers, "no feathers from the Pidgey");
			helper.assertTrue(wornBrush, "the brush isn't back, worn");
			helper.assertTrue(ModAttachments.POKEMON_TENDED.getOrElse(butcher, 0) >= 2, "chores done: "
				+ ModAttachments.POKEMON_TENDED.getOrElse(butcher, 0));
		});
	}
	/**
	 * B24: a pastured Pidgey up in the air is out of the butcher's reach, so he doesn't stand under it waiting: he leaves it
	 * and brushes it once it has landed.
	 */
	//$ gametest_ticks_batch AREA '1800' '"herder_pokemon_air"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "herder_pokemon_air")
	public void b24AFlyingPidgeyIsBrushedOnceItLands(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		io.github.jcondedata.aliveworkplace.ranch.PokemonChores.forget();
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.SMOKER);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.BRUSH));
		Villager butcher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, butcher, helper.absolutePos(new BlockPos(2, 2, 2)), net.minecraft.world.entity.ai.village.poi.PoiTypes.BUTCHER,
			net.minecraft.world.entity.npc.VillagerProfession.BUTCHER);
		BlockPos pasture = pasture(helper, new BlockPos(10, 2, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Pokemon pidgey = pastured(helper, pasture, player, "pidgey", Direction.WEST);
		net.minecraft.world.phys.Vec3 up = net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(9, 8, 6)));
		long[] t = {0};
		boolean[] airborneSeen = {false};
		helper.onEachTick(() -> {
			var entity = pidgey.getEntity();
			if (entity == null || t[0]++ > 600) {
				if (entity != null && entity.isNoGravity()) {
					entity.setNoGravity(false); // it comes down
				}
				return;
			}
			entity.setNoGravity(true);
			entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			entity.teleportTo(up.x, up.y, up.z);
			airborneSeen[0] |= io.github.jcondedata.aliveworkplace.ranch.PokemonChores.airborne(entity);
			if (t[0] == 600) {
				helper.assertTrue(airborneSeen[0], "the held Pidgey never counted as up in the air");
				helper.assertTrue(ModAttachments.POKEMON_TENDED.getOrElse(butcher, 0) == 0, "brushed while 6 blocks up");
				helper.assertTrue(io.github.jcondedata.aliveworkplace.ranch.RanchWork.isBusy(butcher) == false
					|| butcher.distanceToSqr(entity) > 9, "the butcher waits under the flying Pidgey");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(t[0] > 600, "still held up");
			boolean feathers = false;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				feathers |= chest.getItem(i).is(net.minecraft.world.item.Items.FEATHER);
			}
			helper.assertTrue(feathers, "no feathers from the Pidgey once it landed");
		});
	}

	/** A rancher grooms a pastured Eevee once a day, with an Oran Berry from the chest as a treat: friendship goes up. */
	//$ gametest_ticks_batch AREA '1600' '"rancher_pokemon"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "rancher_pokemon")
	public void rancherGroomsAPasturedEevee(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		io.github.jcondedata.aliveworkplace.ranch.RancherWork.forget();
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.FEED_TROUGH);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		chest.setItem(0, new ItemStack(CobblemonItems.ORAN_BERRY, 3));
		Villager rancher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, rancher, helper.absolutePos(new BlockPos(2, 2, 2)), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
		BlockPos pasture = pasture(helper, new BlockPos(10, 2, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Pokemon eevee = pastured(helper, pasture, player, "eevee", Direction.NORTH);
		int before = eevee.getFriendship();
		int groomed = io.github.jcondedata.aliveworkplace.ranch.RancherWork.GROOM + io.github.jcondedata.aliveworkplace.ranch.RancherWork.TREAT;
		helper.succeedWhen(() -> {
			helper.assertTrue(eevee.getFriendship() == before + groomed, "friendship " + before + " -> " + eevee.getFriendship());
			helper.assertTrue(chest.countItem(CobblemonItems.ORAN_BERRY) == 2, "berries left: " + chest.countItem(CobblemonItems.ORAN_BERRY));
			helper.assertTrue(ModAttachments.POKEMON_TENDED.getOrElse(rancher, 0) == 1, "groomed: "
				+ ModAttachments.POKEMON_TENDED.getOrElse(rancher, 0));
		});
	}
}
