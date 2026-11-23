package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Gifts;
import io.github.jcondedata.aliveworkplace.story.Tastes;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane c, 2026-11-23) for gifts (ROADMAP 31.6, docs/wiki/gifts.md), written from the wiki's promises that the
 * existing tests leave open: friendship stays within 0 to 1000 when a gift (and a name day's triple) would pass it;
 * the day limit turns exactly at the chronicle's midnight and the week at day 7 to day 8, with the refusal and the
 * thanks both in the giver's chat ("Dara: ..."); a name day is that day only, to the tick; the gift counts for whoever
 * gives it, not whoever wrapped it; and the unwrapped item lands with everything on it (name, wear), a wrapped Milk
 * Bucket as milk, in the chests, the store and the pockets. Every gift goes through the player's right-click.
 */
public class QaGifts1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	/** A hall, alone in the batch; {@code then} runs once its POI is in. The clock, radius, switch and tastes are put back. */
	private static void village(GameTestHelper helper, Runnable then) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long dayTime = level.getDayTime();
		boolean friendship = Friendship.ENABLED;
		Map<ResourceLocation, JsonElement> files = Tastes.files(level.getServer().getResourceManager());
		Tastes.load(files);
		VillageHalls.RADIUS = 16;
		Friendship.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Friendship.ENABLED = friendship;
			level.setDayTime(dayTime);
			Tastes.load(files);
			CivicEffects.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			CivicEffects.forget();
			then.run();
		});
	}

	private static int uuids;

	/** A Nimble villager's UUID (sugar and rabbit's feet: in no test here) whose name day misses days 8 to 17 (mod 28). */
	private static UUID quietUuid(ServerLevel level) {
		java.util.Random random = new java.util.Random(1123L + 7919L * uuids++);
		for (int i = 0; i < 100_000; i++) {
			UUID id = new UUID(random.nextLong(), random.nextLong());
			if (Traits.of(id).equals(List.of(Traits.Trait.NIMBLE)) && GiftGameTests.quietFromDay8(id) && level.getEntity(id) == null) {
				return id;
			}
		}
		throw new IllegalStateException("no UUID");
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, String name, VillagerProfession profession) {
		ServerLevel level = helper.getLevel();
		Villager v = EntityType.VILLAGER.create(level);
		v.setUUID(quietUuid(level));
		v.setVillagerData(v.getVillagerData().setProfession(profession));
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
		v.moveTo(pos.x, pos.y, pos.z, 0, 0);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		level.addFreshEntity(v);
		return v;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/** The first tick of {@code day} by the chronicle's count. */
	private static void startOf(ServerLevel level, long day) {
		level.setDayTime((day - 1) * VillageNeeds.DAY);
	}

	/** The last tick of {@code day} by the chronicle's count. */
	private static void endOf(ServerLevel level, long day) {
		level.setDayTime(day * VillageNeeds.DAY - 1);
	}

	/** What a client sends when its player right-clicks {@code villager} holding a Gift of {@code gift}. */
	private static void giveByClick(ServerPlayer player, Villager villager, ItemStack gift) {
		player.setItemInHand(InteractionHand.MAIN_HAND, gift);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
	}

	private static ItemStack gift(ItemStack item, ServerPlayer from) {
		return Gifts.wrap(item, from.getGameProfile().getName());
	}

	/**
	 * "Friendship stays within 0 to 1000": a loved gift at 950 stops at 1000; a loved gift on the name day's last tick
	 * counts three times (900 + 240) and stops at 1000, not 980; a hated gift on the next day's first tick costs 40,
	 * not three times as much (100 to 60); a hated name-day gift at 30 stops at 0. A Gift wrapped by someone else counts
	 * for the player who gives it ("your hearts with them move").
	 */
	//$ gametest_ticks_batch AREA '60' '"qaGiftsCap1123c"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaGiftsCap1123c")
	public void friendshipStaysWithinZeroAndAThousandAndANameDayIsThatDayOnly(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			helper.assertTrue(Friendship.MAX == 1000, "the cap is " + Friendship.MAX);
			long nameDay = 28 + Gifts.nameDay(dara.getUUID()) + 1;
			helper.assertTrue(Gifts.isNameDay(dara, nameDay) && !Gifts.isNameDay(dara, nameDay + 1) && !Gifts.isNameDay(dara, nameDay - 1), "setup: the name day");

			// An ordinary day (the day before the name day): 950 + 80 stops at 1000.
			startOf(level, nameDay - 1);
			ServerPlayer alex = player(helper);
			Friendship.add(dara, alex, 950);
			giveByClick(alex, dara, gift(new ItemStack(Items.CAKE), alex));
			helper.assertTrue(alex.getMainHandItem().isEmpty(), "the gift wasn't taken: " + alex.getMainHandItem());
			helper.assertTrue(Friendship.points(dara, alex.getUUID()) == 1000, "950 and a loved gift: " + Friendship.points(dara, alex.getUUID()) + ", not 1000");

			// The name day's very last tick: three times (900 + 240), capped at 1000.
			endOf(level, nameDay);
			helper.assertTrue(Chronicle.day(level) == nameDay, "setup: day " + Chronicle.day(level));
			ServerPlayer sam = player(helper);
			Friendship.add(dara, sam, 900);
			giveByClick(sam, dara, gift(new ItemStack(Items.CAKE), sam));
			helper.assertTrue(Friendship.points(dara, sam.getUUID()) == 1000, "a loved gift on the name day's last tick at 900: "
				+ Friendship.points(dara, sam.getUUID()) + ", not 1000 (980 would be an ordinary day's)");
			// A hated name-day gift at 30 stops at 0.
			ServerPlayer cora = player(helper);
			Friendship.add(dara, cora, 30);
			giveByClick(cora, dara, gift(new ItemStack(Items.ROTTEN_FLESH), cora));
			helper.assertTrue(cora.getMainHandItem().isEmpty() && Friendship.points(dara, cora.getUUID()) == 0,
				"a hated name-day gift at 30: " + Friendship.points(dara, cora.getUUID()) + ", hand " + cora.getMainHandItem());

			// The first tick of the day after: an ordinary day again, a hated gift costs 40 (100 to 60, not 0).
			startOf(level, nameDay + 1);
			ServerPlayer bram = player(helper);
			Friendship.add(dara, bram, 100);
			giveByClick(bram, dara, gift(new ItemStack(Items.ROTTEN_FLESH), bram));
			helper.assertTrue(Friendship.points(dara, bram.getUUID()) == 60, "a hated gift the tick after the name day at 100: "
				+ Friendship.points(dara, bram.getUUID()) + ", not 60");

			// A Gift someone else wrapped: the giver's hearts move, not the wrapper's.
			ServerPlayer wrapper = player(helper);
			ServerPlayer giver = player(helper);
			giveByClick(giver, dara, gift(new ItemStack(Items.COOKIE), wrapper));
			helper.assertTrue(Friendship.points(dara, giver.getUUID()) == 45 && Friendship.points(dara, wrapper.getUUID()) == 0,
				"someone else's gift: giver " + Friendship.points(dara, giver.getUUID()) + ", wrapper " + Friendship.points(dara, wrapper.getUUID()));
			helper.succeed();
		});
	}

	/**
	 * "Days are the world's days (the same count the chronicle uses); weeks are seven days from the world's first day":
	 * a second gift on the day's last tick is refused, with "Dara: You already gave me something today, NAME. Keep it
	 * for tomorrow." in the giver's chat and the Gift left in hand; the next day's first tick takes it, with Dara's thanks
	 * in the chat; the week's last tick refuses a third ("Two gifts in one week is plenty, NAME. Keep it for next
	 * week."), the next week's first tick takes it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaGiftsMidnight1123c"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaGiftsMidnight1123c")
	public void theDayAndWeekTurnAtTheChroniclesMidnightAndTheGiverReadsItInChat(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
			ServerPlayer alex = client.player();
			String name = alex.getGameProfile().getName();
			long monday = GiftGameTests.quietWeek(dara);
			helper.assertTrue(Gifts.week(monday) != Gifts.week(monday - 1) && Gifts.week(monday + 6) == Gifts.week(monday)
				&& Gifts.week(monday + 7) != Gifts.week(monday), "setup: " + monday + " doesn't start a week");

			startOf(level, monday);
			client.seen().clear();
			giveByClick(alex, dara, gift(new ItemStack(Items.STICK), alex));
			helper.assertTrue(alex.getMainHandItem().isEmpty() && Friendship.points(dara, alex.getUUID()) == 20, "the first gift: " + Friendship.points(dara, alex.getUUID()));
			List<String> chat = client.seen().stream().map(Component::getString).toList();
			helper.assertTrue(chat.contains("Dara: Stick. Thank you, " + name + ".") || chat.contains("Dara: A present! Stick... that's thoughtful."),
				"the thanks in the giver's chat: " + chat);

			endOf(level, monday);
			helper.assertTrue(Chronicle.day(level) == monday, "setup: the last tick is day " + Chronicle.day(level));
			client.seen().clear();
			giveByClick(alex, dara, gift(new ItemStack(Items.CAKE), alex));
			chat = client.seen().stream().map(Component::getString).toList();
			helper.assertTrue(alex.getMainHandItem().is(ModItems.GIFT) && Gifts.contents(alex.getMainHandItem()).is(Items.CAKE), "the refused gift left the hand: " + alex.getMainHandItem());
			helper.assertTrue(Friendship.points(dara, alex.getUUID()) == 20, "a gift on the same day's last tick counted: " + Friendship.points(dara, alex.getUUID()));
			helper.assertTrue(chat.contains("Dara: You already gave me something today, " + name + ". Keep it for tomorrow."), "the day line in chat: " + chat);

			startOf(level, monday + 1);
			client.seen().clear();
			giveByClick(alex, dara, alex.getMainHandItem());
			chat = client.seen().stream().map(Component::getString).toList();
			helper.assertTrue(alex.getMainHandItem().isEmpty() && Friendship.points(dara, alex.getUUID()) == 100, "the next day's first tick: " + Friendship.points(dara, alex.getUUID()));
			helper.assertTrue(chat.contains("Dara: Cake! You remembered.") || chat.contains("Dara: Cake? For me? It's my favorite, " + name + "!"), "the cake's thanks in chat: " + chat);

			endOf(level, monday + 6);
			client.seen().clear();
			giveByClick(alex, dara, gift(new ItemStack(Items.COOKIE), alex));
			chat = client.seen().stream().map(Component::getString).toList();
			helper.assertTrue(alex.getMainHandItem().is(ModItems.GIFT) && Friendship.points(dara, alex.getUUID()) == 100, "a third gift on the week's last tick: " + Friendship.points(dara, alex.getUUID()));
			helper.assertTrue(chat.contains("Dara: Two gifts in one week is plenty, " + name + ". Keep it for next week."), "the week line in chat: " + chat);

			startOf(level, monday + 7);
			giveByClick(alex, dara, alex.getMainHandItem());
			helper.assertTrue(alex.getMainHandItem().isEmpty() && Friendship.points(dara, alex.getUUID()) == 145, "the next week's first tick: " + Friendship.points(dara, alex.getUUID()));
			helper.succeed();
		});
	}

	/**
	 * "One item is taken from the stack on the grid, with everything on it (name, enchantments, wear)" and "a wrapped Milk
	 * Bucket keeps its bucket": unwrapped by a right-click, a named, worn sword lands in the mason's supply chest as it
	 * was; a Milk Bucket lands in the village's store as a Milk Bucket; a named emerald lands in a villager's pockets
	 * with its name. The wrapping is used up everywhere.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaGiftsWhole1123c"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaGiftsWhole1123c")
	public void theUnwrappedItemLandsWithEverythingOnIt(GameTestHelper helper) {
		BlockPos bench = new BlockPos(2, 2, 3);
		BlockPos chest = new BlockPos(2, 2, 4);
		BlockPos storehouse = new BlockPos(15, 2, 15);
		BlockPos storeChest = new BlockPos(15, 2, 14);
		helper.setBlock(bench, Blocks.STONECUTTER);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager mason = villager(helper, new BlockPos(4, 2, 4), "Marta", VillagerProfession.MASON);
			mason.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(bench)));
			Villager idle = villager(helper, new BlockPos(6, 2, 4), "Ines", VillagerProfession.NONE);
			long monday = GiftGameTests.quietWeek(mason, idle);
			startOf(level, monday);
			level.setDayTime(level.getDayTime() + 1000);
			ServerPlayer alex = player(helper);

			// A named, worn sword: into her supply chest as it was.
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			sword.set(DataComponents.CUSTOM_NAME, Component.literal("Biter"));
			sword.setDamageValue(37);
			giveByClick(alex, mason, gift(sword.copy(), alex));
			helper.assertTrue(alex.getMainHandItem().isEmpty(), "the sword's gift wasn't taken");
			net.minecraft.world.Container hers = helper.getBlockEntity(chest);
			ItemStack landed = ItemStack.EMPTY;
			for (int i = 0; i < hers.getContainerSize(); i++) {
				if (hers.getItem(i).is(Items.DIAMOND_SWORD)) {
					landed = hers.getItem(i);
				}
			}
			helper.assertTrue(ItemStack.matches(landed, sword), "the sword in her chest: " + landed + " name " + landed.getHoverName().getString()
				+ " damage " + landed.getDamageValue() + ", not Biter at 37");
			helper.assertTrue(SupplyContainers.count(level, List.of(helper.absolutePos(chest)), ModItems.GIFT) == 0
				&& SupplyContainers.count(level, List.of(helper.absolutePos(chest)), ModItems.GIFT_WRAP) == 0, "the wrapping went into the chest");

			// A Milk Bucket: into the store as milk, not an empty bucket.
			List<BlockPos> store = VillageNeeds.store(level, hall);
			giveByClick(alex, idle, gift(new ItemStack(Items.MILK_BUCKET), alex));
			helper.assertTrue(alex.getMainHandItem().isEmpty(), "the milk's gift wasn't taken: " + alex.getMainHandItem());
			helper.assertTrue(SupplyContainers.count(level, store, Items.MILK_BUCKET) == 1 && SupplyContainers.count(level, store, Items.BUCKET) == 0,
				"the store holds milk " + SupplyContainers.count(level, store, Items.MILK_BUCKET) + ", buckets " + SupplyContainers.count(level, store, Items.BUCKET));

			// No store left: a named emerald in her pockets, name and all.
			helper.setBlock(storeChest, Blocks.AIR);
			helper.setBlock(storehouse, Blocks.AIR);
			startOf(level, monday + 1);
			level.setDayTime(level.getDayTime() + 1000);
			ItemStack emerald = new ItemStack(Items.EMERALD);
			emerald.set(DataComponents.CUSTOM_NAME, Component.literal("Lucky Stone"));
			giveByClick(alex, idle, gift(emerald.copy(), alex));
			helper.assertTrue(alex.getMainHandItem().isEmpty(), "the emerald's gift wasn't taken");
			ItemStack pocketed = ItemStack.EMPTY;
			for (int i = 0; i < idle.getInventory().getContainerSize(); i++) {
				if (idle.getInventory().getItem(i).is(Items.EMERALD)) {
					pocketed = idle.getInventory().getItem(i);
				}
			}
			helper.assertTrue(ItemStack.matches(pocketed, emerald), "her pockets: " + pocketed + " named " + pocketed.getHoverName().getString());
			helper.succeed();
		});
	}
}
