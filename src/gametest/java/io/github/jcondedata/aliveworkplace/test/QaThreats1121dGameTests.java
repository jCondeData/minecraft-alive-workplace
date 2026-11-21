package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.PatrolMapItem;
import io.github.jcondedata.aliveworkplace.guard.WallSentries;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, lane-d-1121-0109) for the Golem Smith's Wall Sentry (ROADMAP 29.15), written from the spec: a sentry posted
 * by a Patrol Map holds the wall "knocking attackers back off the wall" ({@link WallSentries#struck}, which no test
 * checked), and "in a protected village only its people may post a sentry" (the sneak-click refusal, also unchecked).
 * Each test has a batch of its own and clears leftovers first: a neighbour's Leftovers.clear would discard its golem,
 * and a protected hall would reach into a neighbour's area.
 */
public class QaThreats1121dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String BATCH = "qaThreats1121d";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos POST = new BlockPos(15, 2, 15);
	private static final BlockPos OTHER_POST = new BlockPos(20, 2, 15);

	private static ItemStack map(ServerLevel level, List<BlockPos> points) {
		ItemStack map = new ItemStack(ModItems.PATROL_MAP);
		map.set(ModComponents.PATROL, new PatrolMapItem.Route(Optional.of(level.dimension().location()), points));
		return map;
	}

	private static IronGolem sentryAt(GameTestHelper helper, BlockPos rel) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(rel), GolemSmith.Role.SENTRY);
		helper.assertTrue(golem != null, "setup: no sentry was built");
		Leftovers.after(helper, golem::discard);
		golem.setNoAi(true); // the blow is struck by the test, in this tick; nothing else moves anyone
		return golem;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		player.setShiftKeyDown(true);
		return player;
	}

	/** The player's own sneak-click on the golem with {@code map} in hand. */
	private static void click(GameTestHelper helper, ServerPlayer player, IronGolem golem, ItemStack map) {
		player.setItemInHand(InteractionHand.MAIN_HAND, map);
		UseEntityCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, golem, null);
	}

	/** A still, tough husk 1.5 blocks east of {@code golem} (200 health, so no blow kills it). */
	private static Husk foeEastOf(GameTestHelper helper, IronGolem golem) {
		Husk husk = EntityType.HUSK.create(helper.getLevel());
		helper.assertTrue(husk != null, "setup: no husk");
		husk.moveTo(golem.getX() + 1.5, golem.getY(), golem.getZ(), 0f, 0f);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		husk.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
		husk.setHealth(200);
		helper.getLevel().addFreshEntity(husk);
		Leftovers.after(helper, husk::discard);
		return husk;
	}

	/**
	 * 29.15: a Wall Sentry "knocks attackers back off the wall". A sentry posted by a player's sneak-click with a Patrol
	 * Map strikes a husk standing east of it: the husk is thrown east, away from the sentry, at a speed of at least one
	 * block a tick, and much harder than the same blow from an unposted sentry ("without a map it is an ordinary
	 * golem"), whose husk is barely moved.
	 */
	//$ gametest_ticks_batch AREA '100' 'BATCH + "Knock"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = BATCH + "Knock")
	public void aPostedSentrysBlowThrowsTheFoeBackFarHarderThanAnOrdinaryGolems(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		IronGolem posted = sentryAt(helper, POST);
		click(helper, player, posted, map(level, List.of(posted.blockPosition())));
		helper.assertTrue(WallSentries.post(posted) != null, "setup: the sentry wasn't posted by the map");
		IronGolem plain = sentryAt(helper, new BlockPos(15, 2, 24));
		helper.assertTrue(WallSentries.post(plain) == null, "setup: the unposted sentry has a post");

		Husk hit = foeEastOf(helper, posted);
		Husk control = foeEastOf(helper, plain);
		helper.assertTrue(posted.doHurtTarget(hit), "setup: the posted sentry's blow didn't land");
		helper.assertTrue(plain.doHurtTarget(control), "setup: the unposted sentry's blow didn't land");
		double thrown = hit.getDeltaMovement().x;
		double ordinary = control.getDeltaMovement().x;
		helper.assertTrue(thrown >= 1.0, String.format("a posted sentry's blow threw its foe only %.2f a tick away from it (expected a hard throw off the wall, at least 1.0)", thrown));
		helper.assertTrue(Math.abs(hit.getDeltaMovement().z) < 0.2, String.format("the foe east of the sentry was thrown sideways (z %.2f)", hit.getDeltaMovement().z));
		helper.assertTrue(thrown > ordinary + 0.5, String.format("a posted sentry's blow (%.2f) throws no harder than an ordinary golem's (%.2f)", thrown, ordinary));
		helper.succeed();
	}

	/** A protected village at HALL owned by {@code owner}; taken down again after the test. */
	private static void protectedVillage(GameTestHelper helper, ServerPlayer owner) {
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setProtected(true);
		BlockPos at = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			if (helper.getLevel().getBlockEntity(at) instanceof VillageHallBlockEntity h) {
				h.setProtected(false);
			}
			helper.getLevel().removeBlock(at, false);
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		});
	}

	/**
	 * 29.15: "In a protected village only its people may post a sentry." A stranger's sneak-click with a Patrol Map
	 * leaves the sentry without a post; the owner's sneak-click with the same map posts it at the map's first point.
	 */
	//$ gametest_ticks_batch AREA '100' 'BATCH + "Stranger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = BATCH + "Stranger")
	public void aStrangersMapPostsNoSentryInAProtectedVillageButTheOwnersDoes(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = player(helper);
		ServerPlayer stranger = player(helper);
		protectedVillage(helper, owner);
		IronGolem golem = sentryAt(helper, POST);
		BlockPos post = helper.absolutePos(POST);

		click(helper, stranger, golem, map(level, List.of(post)));
		helper.assertTrue(WallSentries.post(golem) == null, "a stranger posted a sentry in a protected village at " + WallSentries.post(golem));
		click(helper, owner, golem, map(level, List.of(post)));
		helper.assertTrue(post.equals(WallSentries.post(golem)), "the owner's map didn't post the sentry in her own village: " + WallSentries.post(golem));
		helper.succeed();
	}

	/**
	 * The same rule from the other side: once the owner has posted a sentry, a stranger can neither take the post away
	 * with a blank map ("a blank map takes the post away", for the village's people) nor move it with a map of their
	 * own; the owner's blank map does take it away.
	 */
	//$ gametest_ticks_batch AREA '100' 'BATCH + "Blank"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = BATCH + "Blank")
	public void aStrangerCantTakeOrMoveTheOwnersSentryPostButTheOwnerCan(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = player(helper);
		ServerPlayer stranger = player(helper);
		protectedVillage(helper, owner);
		IronGolem golem = sentryAt(helper, POST);
		BlockPos post = helper.absolutePos(POST);
		click(helper, owner, golem, map(level, List.of(post)));
		helper.assertTrue(post.equals(WallSentries.post(golem)), "setup: the owner's map didn't post the sentry: " + WallSentries.post(golem));

		click(helper, stranger, golem, map(level, List.of()));
		helper.assertTrue(post.equals(WallSentries.post(golem)), "a stranger's blank map took the owner's sentry off its post in a protected village: " + WallSentries.post(golem));
		click(helper, stranger, golem, map(level, List.of(helper.absolutePos(OTHER_POST))));
		helper.assertTrue(post.equals(WallSentries.post(golem)), "a stranger's map moved the owner's sentry in a protected village to " + WallSentries.post(golem));
		click(helper, owner, golem, map(level, List.of()));
		helper.assertTrue(WallSentries.post(golem) == null, "the owner's blank map didn't take the post away: " + WallSentries.post(golem));
		helper.succeed();
	}
}
