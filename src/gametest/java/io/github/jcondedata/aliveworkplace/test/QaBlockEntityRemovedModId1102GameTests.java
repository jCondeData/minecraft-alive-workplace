package io.github.jcondedata.aliveworkplace.test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, saves and optional mods, 2026-11-02; B791's kind for the mod's blocks): each of the mod's block entities
 * (the Village Hall, the tables, counters, boards...) is made with every field it can be given a value in
 * ({@link QaEveryRecordField1101GameTests}'s generator), saved, and every place in its saved tag that names an item or a
 * block is in turn renamed to an id no mod registers, as a world reopened without that mod reads it. The load must not
 * throw (the block would lose everything), and no list, set or map of the block may lose more than the one entry that
 * named the gone thing (a stack of it on the Blueprint Table's shelf, a Storehouse's stock order for it), as a chest
 * keeps its other slots.
 */
public class QaBlockEntityRemovedModId1102GameTests implements FabricGameTest {

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aRemovedModsIdInABlockLosesOnlyThatThing(GameTestHelper helper) {
		QaEveryRecordField1101GameTests maker = new QaEveryRecordField1101GameTests();
		maker.registries = helper.getLevel().registryAccess();
		var registries = helper.getLevel().registryAccess();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		List<String> lost = new ArrayList<>();
		List<String> renamedAt = new ArrayList<>();
		int types = 0;
		for (Map.Entry<net.minecraft.resources.ResourceKey<BlockEntityType<?>>, BlockEntityType<?>> e : BuiltInRegistries.BLOCK_ENTITY_TYPE.entrySet()) {
			if (!e.getKey().location().getNamespace().equals("aliveworkplace")) {
				continue;
			}
			BlockEntityType<?> type = e.getValue();
			BlockState state = BuiltInRegistries.BLOCK.stream().filter(b -> BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals("aliveworkplace"))
				.map(Block::defaultBlockState).filter(type::isValid).findFirst().orElse(null);
			if (state == null) {
				continue; // QaEveryBlockEntityField1101GameTests fails on this
			}
			String be = e.getKey().location().getPath();
			BlockEntity wrote = type.create(pos, state);
			List<Field> collections = new ArrayList<>();
			for (Class<?> c = wrote.getClass(); c != null && c.getName().startsWith("io.github.jcondedata."); c = c.getSuperclass()) {
				for (Field f : c.getDeclaredFields()) {
					if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
						continue;
					}
					f.setAccessible(true);
					try {
						Object value = maker.make(f.getGenericType(), c.getSimpleName() + "." + f.getName());
						Object old = f.get(wrote);
						if (!QaSavedDataCollections1101GameTests.set(f, wrote, value)) {
							continue;
						}
						try { // a made value the save or load can't take is left out (QaEveryBlockEntityField1101GameTests checks each field)
							type.create(pos, state).loadWithComponents(wrote.saveWithoutMetadata(registries), registries);
						} catch (RuntimeException x) {
							if (!Modifier.isFinal(f.getModifiers())) {
								f.set(wrote, old);
							}
							continue;
						}
						if ((Map.class.isAssignableFrom(f.getType()) || java.util.Collection.class.isAssignableFrom(f.getType()))) {
							collections.add(f);
						}
					} catch (QaEveryRecordField1101GameTests.Unbuildable | ReflectiveOperationException | RuntimeException x) {
						// not every field can be made up: the others still carry ids
					}
				}
			}
			types++;
			CompoundTag tag = wrote.saveWithoutMetadata(registries);
			BlockEntity clean = type.create(pos, state);
			clean.loadWithComponents(tag.copy(), registries);
			List<List<Object>> found = new ArrayList<>();
			QaSavedDataRemovedModId1102GameTests.ids(tag, new ArrayList<>(), found);
			Set<String> seen = new LinkedHashSet<>();
			for (List<Object> place : found) {
				String where = be + " " + QaSavedDataRemovedModId1102GameTests.show(place);
				if (!seen.add(where)) {
					continue;
				}
				renamedAt.add(where);
				BlockEntity read = type.create(pos, state);
				try {
					read.loadWithComponents(QaSavedDataRemovedModId1102GameTests.renamed(tag, place), registries);
				} catch (RuntimeException x) {
					lost.add(where + ": the load threw " + x);
					continue;
				}
				for (Field f : collections) {
					try {
						int had = QaSavedDataRemovedModId1102GameTests.size(f.get(clean));
						int got = QaSavedDataRemovedModId1102GameTests.size(f.get(read));
						if (had >= 0 && got < had - 1) { // the renamed entry itself may go (a stack of a gone item, an order for one)
							lost.add(where + ": " + f.getName() + " came back with " + got + " of " + had + " entries");
						}
					} catch (IllegalAccessException x) {
						lost.add(where + ": can't read " + f.getName());
					}
				}
			}
		}
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA block entity removed mod ids: {} block entities, renamed at {}", types, renamedAt);
		lost.forEach(l -> io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.error("QA block entity removed mod id lost: {}", l));
		helper.assertTrue(types >= 6, "only " + types + " block entities made");
		helper.assertTrue(renamedAt.size() >= 3, "only " + renamedAt.size() + " item or block ids found in the blocks: " + renamedAt);
		helper.assertTrue(lost.isEmpty(), QaSavedDataCollections1101GameTests.cut(lost.size()
			+ " renamed id(s) lost more than their own thing or threw (each in the log): " + lost));
		helper.succeed();
	}
}
