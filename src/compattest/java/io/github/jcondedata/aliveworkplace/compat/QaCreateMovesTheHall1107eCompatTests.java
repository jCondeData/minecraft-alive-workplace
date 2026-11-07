package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.UUID;

/**
 * QA (lane E, Create and saves, 2026-11-07): what a Village Hall keeps of its village when Create's Mechanical Piston
 * pushes it two steps (wiki create.md, "Known limits": not tested). Vanilla's piston can't move a hall (it has a block
 * entity); Create's can. A player who nudges his hall along expects to keep his village: its name, owner and
 * chronicle, its Steward at work, its place in its realm, its open quests. Each case first proves the piston turned and
 * the hall now stands two steps west, once. What the move loses (the made-up name, the Steward, the realm, the open quests)
 * is in {@link QaCreateMovesTheHall1107eBugCompatTests}.
 */
public class QaCreateMovesTheHall1107eCompatTests implements FabricGameTest {

	static final String AREA = CompatGameTests.AREA;
	/** The hall in front of the piston; pushed two steps west it ends up at {@code TARGET.west(2)}. */
	static final BlockPos TARGET = new BlockPos(7, 2, 8);
	static final BlockPos MOVED = TARGET.west(2);
	/** A second village's hall, well away from the piston's row (a realm's other member). */
	static final BlockPos OTHER = new BlockPos(3, 2, 3);
	/** Where the Steward stands, by the hall but off the piston's row. */
	static final BlockPos STEWARD_AT = new BlockPos(5, 2, 11);
	/** Ticks for the piston to push two steps (as the sibling QaCreateMovesOurBlocks1102eCompatTests waits). */
	static final int PUSH = 300;

	/** A Mechanical Piston one step east of {@link #TARGET} facing west, two poles behind it, turned from below. */
	static void piston(GameTestHelper helper) {
		BlockPos piston = TARGET.east();
		helper.setBlock(piston.east(2), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston.east(), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston, MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.create("mechanical_piston").defaultBlockState(), "facing", "west"),
			"axis_along_first", "true"));
		helper.setBlock(piston.below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
	}

	static float speed(GameTestHelper helper) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(TARGET.east()));
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(helper.getLevel().registryAccess()), "Speed");
	}

	/** The row the hall may be in, west to east, as read. */
	static String row(GameTestHelper helper) {
		StringBuilder out = new StringBuilder();
		for (int i = 4; i >= 0; i--) {
			BlockPos p = TARGET.west(i);
			out.append(p.getX()).append(": ").append(helper.getBlockState(p).getBlock().getDescriptionId()).append("; ");
		}
		return out.toString();
	}

	/** Village Hall blocks anywhere in the test area. */
	static int halls(GameTestHelper helper) {
		int n = 0;
		BlockPos from = helper.absolutePos(new BlockPos(0, 1, 0));
		BlockPos to = helper.absolutePos(new BlockPos(16, 7, 16));
		for (BlockPos p : BlockPos.betweenClosed(from, to)) {
			if (helper.getLevel().getBlockState(p).is(ModBlocks.VILLAGE_HALL)) {
				n++;
			}
		}
		return n;
	}

	static int hallItemsOnTheGround(GameTestHelper helper) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8),
				e -> e.getItem().is(ModBlocks.VILLAGE_HALL.asItem()))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	/** The piston turned and the hall stands at {@link #MOVED} now, its old spot empty of it: else the test proves nothing. */
	static void assertPushed(GameTestHelper helper) {
		helper.assertTrue(speed(helper) != 0, "the piston never turned, so the test proves nothing");
		helper.assertTrue(helper.getBlockState(MOVED).is(ModBlocks.VILLAGE_HALL) && !helper.getBlockState(TARGET).is(ModBlocks.VILLAGE_HALL),
			"the Mechanical Piston didn't push the Village Hall two steps west: " + row(helper));
	}

	static VillageHallBlockEntity hallAt(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(rel)) instanceof VillageHallBlockEntity e ? e : null;
	}

	/** A hall at {@link #TARGET}, owned by "Jesse", named "Thornholm" (null: left unnamed), three lines in its chronicle. */
	static VillageHallBlockEntity hall(GameTestHelper helper, UUID owner, Component name) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(TARGET, ModBlocks.VILLAGE_HALL);
		BlockPos at = helper.absolutePos(TARGET);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(at);
		entity.setOwner(owner, "Jesse");
		if (name != null) {
			entity.setCustomName(name);
		}
		Chronicle.atHall(level, at, Chronicle.Kind.FOUNDED, Component.literal("Thornholm was founded by Jesse"));
		Chronicle.atHall(level, at, Chronicle.Kind.BUILT, Component.literal("The Bakery was built"));
		Chronicle.atHall(level, at, Chronicle.Kind.MARKET, Component.literal("The first market day"));
		return entity;
	}

	static List<String> chronicle(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	// ---- (1) the hall's own data -------------------------------------------------------------------------------------

	/** The control: a named, owned hall with its chronicle, pushed two steps: one hall, at the new spot, everything kept. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_hall_own_data"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_hall_own_data")
	public void aHallPushedByAMechanicalPistonKeepsItsNameOwnerAndChronicle(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		UUID owner = UUID.randomUUID();
		VillageHallBlockEntity before = hall(helper, owner, Component.literal("Thornholm"));
		List<String> lines = chronicle(before);
		piston(helper);
		helper.runAfterDelay(PUSH, () -> {
			assertPushed(helper);
			helper.assertTrue(halls(helper) == 1, "after the push there are " + halls(helper) + " Village Halls: " + row(helper));
			helper.assertTrue(hallItemsOnTheGround(helper) == 0, "the push dropped " + hallItemsOnTheGround(helper) + " Village Hall item(s)");
			VillageHallBlockEntity after = hallAt(helper, MOVED);
			helper.assertTrue(after != null, "the pushed hall has no hall block entity");
			helper.assertTrue(after.getCustomName() != null && after.getCustomName().getString().equals("Thornholm"),
				"the pushed hall lost its name Thornholm: " + after.getCustomName());
			helper.assertTrue(owner.equals(after.owner()) && "Jesse".equals(after.ownerName()),
				"the pushed hall no longer belongs to Jesse: " + after.owner() + " " + after.ownerName());
			List<String> now = chronicle(after);
			helper.assertTrue(now.size() >= lines.size() && now.subList(0, lines.size()).equals(lines),
				"the pushed hall's chronicle lost lines: was " + lines + ", now " + now);
			helper.succeed();
		});
	}

	/** Create's moving contraptions by the test area (a piston's is its stationary_contraption), as QaCreateMovedSaved1103eCompatTests finds them. */
	static List<Entity> contraptions(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Entity.class, helper.getBounds().inflate(4), e -> {
			ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
			return e.isAlive() && id.getNamespace().equals("create") && id.getPath().endsWith("contraption");
		});
	}

	/**
	 * The world saved and loaded while the hall is in the air on the moving contraption (a player logging off, the chunk
	 * unloading mid-push), as QaCreateMovedSaved1103eCompatTests does it: the contraption saved, removed and loaded back.
	 * Set down two steps on, the hall is still Jesse's Thornholm with its chronicle, once, and no hall item dropped.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_create_hall_saved_mid"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_hall_saved_mid")
	public void aHallSavedMidPushKeepsItsNameOwnerAndChronicle(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		UUID owner = UUID.randomUUID();
		VillageHallBlockEntity before = hall(helper, owner, Component.literal("Thornholm"));
		List<String> lines = chronicle(before);
		piston(helper);
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			if (reloaded[0] || helper.getBlockState(TARGET).is(ModBlocks.VILLAGE_HALL)) {
				return;
			}
			List<Entity> moving = contraptions(helper);
			if (moving.isEmpty()) {
				return;
			}
			reloaded[0] = true;
			for (Entity e : moving) {
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(e.save(tag), "setup: the contraption wasn't saved");
				e.discard();
				Entity back = EntityType.loadEntityRecursive(tag, level, x -> x);
				helper.assertTrue(back != null, "the moving contraption carrying the hall didn't load from its save: " + tag.getAllKeys());
				level.addFreshEntity(back);
			}
		});
		helper.runAfterDelay(PUSH, () -> {
			helper.assertTrue(reloaded[0], "setup: the hall was never seen in the air on the contraption, so nothing was saved mid-move");
			assertPushed(helper);
			helper.assertTrue(halls(helper) == 1, "after a save mid-push there are " + halls(helper) + " Village Halls: " + row(helper));
			helper.assertTrue(hallItemsOnTheGround(helper) == 0, "a save mid-push dropped " + hallItemsOnTheGround(helper) + " Village Hall item(s)");
			VillageHallBlockEntity after = hallAt(helper, MOVED);
			helper.assertTrue(after != null, "the hall saved mid-push has no hall block entity");
			helper.assertTrue(after.getCustomName() != null && after.getCustomName().getString().equals("Thornholm"),
				"the hall saved mid-push lost its name Thornholm: " + after.getCustomName());
			helper.assertTrue(owner.equals(after.owner()) && "Jesse".equals(after.ownerName()),
				"the hall saved mid-push no longer belongs to Jesse: " + after.owner() + " " + after.ownerName());
			List<String> now = chronicle(after);
			helper.assertTrue(now.size() >= lines.size() && now.subList(0, lines.size()).equals(lines),
				"the hall saved mid-push lost chronicle lines: was " + lines + ", now " + now);
			helper.succeed();
		});
	}

	// ---- (5) the village's name --------------------------------------------------------------------------------------

	/** A village the player named keeps that name (the name is the hall's own, so it travels). */
	//$ gametest_ticks_batch AREA '400' '"qa_create_hall_named_name"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_hall_named_name")
	public void aNamedVillageKeepsItsNameWhenAMechanicalPistonPushesItsHall(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		hall(helper, UUID.randomUUID(), Component.literal("Thornholm"));
		String was = VillageHalls.name(helper.getLevel(), helper.absolutePos(TARGET)).getString();
		piston(helper);
		helper.runAfterDelay(PUSH, () -> {
			assertPushed(helper);
			String now = VillageHalls.name(helper.getLevel(), helper.absolutePos(MOVED)).getString();
			helper.assertTrue(was.equals(now), "the village " + was + " is called " + now + " after a Mechanical Piston pushed its hall two blocks");
			helper.succeed();
		});
	}
}
