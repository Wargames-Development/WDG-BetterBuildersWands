package apis.wgc;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import java.util.UUID;


import static com.wdg.wgcore.integration.api.WGCoreIntegrationAccess.*;

public class Integrations {
    public static boolean canPlaceBlockWGC(UUID party, World world, Block block, int blockX, int blockY, int blockZ) {
        return canTargetBlock(party,world,blockX,blockY,blockZ);//TODO replace with actual api function
    }
}
