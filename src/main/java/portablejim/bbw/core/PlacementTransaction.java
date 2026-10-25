package portablejim.bbw.core;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import portablejim.bbw.basics.Point3d;

import java.util.ArrayList;
import java.util.List;

/** Own the snapshots so Forge's outer item-use wrapper cannot cancel after payment. */
public final class PlacementTransaction {
    public interface Payment { boolean consume(); }

    private PlacementTransaction() { }

    public static boolean place(World world, EntityPlayer player, Point3d point,
                                Block block, int meta, ForgeDirection side, Payment payment) {
        // Do not commit inside a foreign transaction whose later rollback we cannot own.
        if (world.isRemote || !world.capturedBlockSnapshots.isEmpty()) return false;
        boolean capturing = world.captureBlockSnapshots;
        boolean restoring = world.restoringBlockSnapshots;
        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        boolean committed = false;
        try {
            world.captureBlockSnapshots = true;
            boolean placed;
            try {
                placed = world.setBlock(point.x, point.y, point.z, block, meta, 3);
            } finally {
                snapshots.addAll(world.capturedBlockSnapshots);
                world.capturedBlockSnapshots.clear();
                world.captureBlockSnapshots = false;
            }
            if (!placed || snapshots.isEmpty()) return false;
            boolean canceled = snapshots.size() == 1
                    ? ForgeEventFactory.onPlayerBlockPlace(player, snapshots.get(0), side).isCanceled()
                    : ForgeEventFactory.onPlayerMultiBlockPlace(player, snapshots, side).isCanceled();
            if (canceled || !payment.consume()) return false;
            committed = true;
            // Same completion order as ForgeHooks.onPlaceItemIntoWorld, after approval/payment.
            for (BlockSnapshot snapshot : snapshots) {
                Block placedBlock = world.getBlock(snapshot.x, snapshot.y, snapshot.z);
                int placedMeta = world.getBlockMetadata(snapshot.x, snapshot.y, snapshot.z);
                if (!placedBlock.hasTileEntity(placedMeta)) {
                    placedBlock.onBlockAdded(world, snapshot.x, snapshot.y, snapshot.z);
                }
                world.markAndNotifyBlock(snapshot.x, snapshot.y, snapshot.z, null,
                        snapshot.replacedBlock, placedBlock, snapshot.flag);
            }
            return true;
        } finally {
            if (!committed) {
                world.captureBlockSnapshots = false;
                world.restoringBlockSnapshots = true;
                try {
                    for (int i = snapshots.size() - 1; i >= 0; i--) snapshots.get(i).restore(true, false);
                } finally {
                    world.restoringBlockSnapshots = restoring;
                    world.captureBlockSnapshots = capturing;
                }
            } else {
                world.restoringBlockSnapshots = restoring;
                world.captureBlockSnapshots = capturing;
            }
        }
    }
}
