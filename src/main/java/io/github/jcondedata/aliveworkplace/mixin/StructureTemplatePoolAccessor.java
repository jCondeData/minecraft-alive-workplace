package io.github.jcondedata.aliveworkplace.mixin;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets {@code VillageHouses} add the builder's workshop to the vanilla village house pools. */
@Mixin(StructureTemplatePool.class)
public interface StructureTemplatePoolAccessor {
	@Accessor("templates")
	ObjectArrayList<StructurePoolElement> aliveworkplace$templates();

	@Accessor("rawTemplates")
	List<Pair<StructurePoolElement, Integer>> aliveworkplace$rawTemplates();

	@Mutable
	@Accessor("rawTemplates")
	void aliveworkplace$setRawTemplates(List<Pair<StructurePoolElement, Integer>> rawTemplates);
}
