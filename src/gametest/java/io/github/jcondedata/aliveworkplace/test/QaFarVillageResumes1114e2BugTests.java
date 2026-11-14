package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Places;
import io.github.jcondedata.aliveworkplace.story.QuestTracker;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.Rewards;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Lane E QA (1114e, second pass), B1231: B1220's decision says a quest finished while its hall was unloaded "waits" and
 * Stories.round pays it to its last helper. A helper who logged off meanwhile keeps it waiting until a round finds them
 * online (it used to be paid to nobody and lost).
 */
public class QaFarVillageResumes1114e2BugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static BlockPos far(GameTestHelper helper, int dx, int dz) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + dz);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	/** A real (listed) player with this profile at {@code at} (relative); removed when the test ends. */
	private static ServerPlayer join(GameTestHelper helper, GameProfile profile, BlockPos at) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.teleportTo(level, abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0f, 0f);
		Leftovers.after(helper, () -> {
			if (level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
				level.getServer().getPlayerList().remove(player);
			}
		});
		return player;
	}

	/**
	 * Ana finishes a far village's kill quest while its hall is unloaded, then logs off. Someone else's visit runs the hall's
	 * round. Ana logs in again and the hall's next round runs: the quest she finished should reach her (a quest done is
	 * never lost: B1220, "a kill still counts ... Stories.round pays any finished quest with its last helper").
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114e2OfflineHelperPaid"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114e2OfflineHelperPaid")
	public void aFarQuestFinishedByAPlayerWhoLoggedOffIsStillTheirs(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 8900, -8900);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		Leftovers.after(helper, () -> {
			if (loaded(level, hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
			}
			Stories.Data.get(level).remove(hall);
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(hall).inflate(4))) {
				item.discard();
			}
		});
		GameProfile anaProfile = new GameProfile(UUID.randomUUID(), "far-ana");
		ServerPlayer ana = join(helper, anaProfile, new BlockPos(2, 2, 2));
		long now = level.getGameTime();
		ResourceLocation file = AliveWorkplace.id("personal/qa1114e2offline");
		Quest q = new Quest(UUID.randomUUID(), file, "hall", Optional.empty(), "Ana", now, now + 24000L * 3,
			List.of(new Objectives.Kill("minecraft:zombie", 1, true)), new int[1], List.of(new Rewards.ItemReward(ResourceLocation.parse("minecraft:diamond"), 3)));
		Stories.post(level, hall, q);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
		zombie.setNoAi(true);
		Stories.onKill(level, zombie, ana);
		zombie.discard();
		helper.assertTrue(q.done() && ana.getUUID().equals(q.last) && !loaded(level, hall), "setup: Ana's kill didn't finish the far quest");

		level.getServer().getPlayerList().remove(ana); // Ana logs off
		helper.assertTrue(level.getServer().getPlayerList().getPlayer(anaProfile.getId()) == null, "setup: Ana is still online");

		// Someone else's visit: the far hall's chunk loads and its round runs.
		level.getChunk(hall);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Stories.round(level, hall, entity);
		boolean stillWaiting = Stories.open(level, hall).stream().anyMatch(x -> x.id.equals(q.id));
		int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(hall).inflate(4), i -> i.getItem().is(Items.DIAMOND))
			.stream().mapToInt(i -> i.getItem().getCount()).sum();
		List<String> chronicle = entity.chronicle().stream().map(e -> e.text().getString()).filter(t -> t.contains("did a quest")).toList();

		// Ana logs in again; the hall's next round.
		ServerPlayer back = join(helper, anaProfile, new BlockPos(2, 2, 2));
		Stories.round(level, hall, entity);
		int diamonds = back.getInventory().countItem(Items.DIAMOND);
		int points = Reputation.points(level, hall, anaProfile.getId());
		helper.assertTrue(diamonds == 3 && points > 0 && chronicle.stream().allMatch(t -> t.startsWith("far-ana")),
			"a quest Ana finished was lost because she was offline at the hall's round: once back she has " + diamonds + " of its 3 diamonds ("
				+ dropped + " were dropped on the ground at the far hall, where nobody was), " + points + " standing in the village, the quest still waiting: " + stillWaiting + ", the chronicle: " + chronicle);
		helper.succeed();
	}

	/**
	 * B1231 with a place to reach (B1223) and a save in between: Ana reaches a far quest's place, which finishes it while
	 * the hall is unloaded, and logs off. The hall's round runs past the quest's deadline (it stays, done, and is paid to
	 * nobody), the stories are saved and loaded again, and once Ana is back the next round pays her and records the quest.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114e2OfflineReachPaid"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114e2OfflineReachPaid")
	public void aFarPlaceReachedByAPlayerWhoLoggedOffWaitsThroughASave(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -8900, 8900);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		Leftovers.after(helper, () -> {
			if (loaded(level, hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
			}
			Stories.Data.get(level).remove(hall);
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(hall).inflate(4))) {
				item.discard();
			}
		});
		GameProfile anaProfile = new GameProfile(UUID.randomUUID(), "far-ana2");
		ServerPlayer ana = join(helper, anaProfile, new BlockPos(2, 2, 2));
		long now = level.getGameTime();
		Quest q = new Quest(UUID.randomUUID(), AliveWorkplace.id("personal/qa1114e2reach"), "hall", Optional.empty(), "Ana", now, now,
			List.of(new Objectives.Reach(Places.point(ana.blockPosition()), 32)), new int[1], List.of(new Rewards.ItemReward(ResourceLocation.parse("minecraft:diamond"), 2)));
		Stories.post(level, hall, q);
		QuestTracker.checkReach(ana);
		helper.assertTrue(q.done() && ana.getUUID().equals(q.last) && !loaded(level, hall), "setup: reaching the place didn't finish the far quest");
		level.getServer().getPlayerList().remove(ana); // Ana logs off

		level.getChunk(hall);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Stories.round(level, hall, entity); // its deadline has passed, its helper is away
		helper.assertTrue(Stories.open(level, hall).stream().anyMatch(x -> x.id.equals(q.id)),
			"a place Ana reached was dropped at the round while she was offline (past its deadline)");
		helper.assertTrue(entity.chronicle().stream().noneMatch(e -> e.text().getString().contains("did a quest")),
			"the quest was paid to nobody while its helper was offline");

		var registries = level.registryAccess();
		level.getDataStorage().set("aliveworkplace_stories", Stories.Data.load(Stories.Data.get(level).save(new CompoundTag(), registries), registries));
		helper.assertTrue(Stories.open(level, hall).stream().anyMatch(x -> x.id.equals(q.id) && x.done() && anaProfile.getId().equals(x.last)),
			"the quest waiting for Ana wasn't kept, done and hers, through a save");

		ServerPlayer back = join(helper, anaProfile, new BlockPos(2, 2, 2));
		Stories.round(level, hall, entity);
		int diamonds = back.getInventory().countItem(Items.DIAMOND);
		List<String> chronicle = entity.chronicle().stream().map(e -> e.text().getString()).filter(t -> t.contains("did a quest")).toList();
		helper.assertTrue(diamonds == 2 && Reputation.points(level, hall, anaProfile.getId()) > 0 && chronicle.size() == 1 && chronicle.get(0).startsWith("far-ana2")
				&& Stories.open(level, hall).stream().noneMatch(x -> x.id.equals(q.id)),
			"once back, Ana wasn't paid the place she reached: " + diamonds + " of 2 diamonds, the chronicle: " + chronicle);
		helper.succeed();
	}
}
