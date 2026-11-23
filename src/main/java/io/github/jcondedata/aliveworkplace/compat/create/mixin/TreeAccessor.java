package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.simibubi.create.content.kinetics.saw.TreeCutter;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The blocks of a tree Create's saw is about to fell (B1518: protection keeps the ones inside a village). */
@Mixin(TreeCutter.Tree.class)
public interface TreeAccessor {
	@Accessor(value = "logs", remap = false)
	List<BlockPos> aliveworkplace$logs();

	@Accessor(value = "leaves", remap = false)
	List<BlockPos> aliveworkplace$leaves();

	@Accessor(value = "attachments", remap = false)
	List<BlockPos> aliveworkplace$attachments();
}
