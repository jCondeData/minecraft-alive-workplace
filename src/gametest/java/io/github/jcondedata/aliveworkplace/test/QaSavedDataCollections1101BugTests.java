package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.story.ReformStories;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;

/**
 * QA (lane E, saves, 2026-11-01), a bug found by {@link QaSavedDataCollections1101GameTests}: a village's drill (the
 * reform story) counts the villagers who drilled in {@code ReformStories.Entry#drillers} and writes "N villagers drilled"
 * in the chronicle when the drill ends, but the file never saves that set: a restart mid-drill forgets everyone who had
 * drilled before it, and the chronicle's count is short.
 */
public class QaSavedDataCollections1101BugTests implements FabricGameTest {

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theVillagersWhoDrilledTodayAreRememberedAcrossARestart(GameTestHelper helper) throws ReflectiveOperationException {
		BlockPos hall = new BlockPos(10, 64, 10);
		ReformStories.Data data = new ReformStories.Data();
		ReformStories.Entry e = data.entry(hall);
		e.drillDay = 12;
		e.drillDone = false;
		Field drillers = ReformStories.Entry.class.getDeclaredField("drillers");
		drillers.setAccessible(true);
		@SuppressWarnings("unchecked")
		Set<UUID> before = (Set<UUID>) drillers.get(e);
		before.add(new UUID(1, 2));
		before.add(new UUID(3, 4));
		Method load = ReformStories.Data.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
		load.setAccessible(true);
		ReformStories.Data read = (ReformStories.Data) load.invoke(null, data.save(new CompoundTag(), helper.getLevel().registryAccess()),
			helper.getLevel().registryAccess());
		Object after = drillers.get(read.entry(hall));
		helper.assertTrue(before.equals(after), "the drill's villagers " + before + " were lost in a restart mid-drill (read " + after + ")");
		helper.succeed();
	}
}
