package nc.block.tile;

import nc.NuclearCraft;
import nc.tile.IGui;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;

public class BlockInventoryGui extends BlockInventory {
	
	protected final int guiId;

	public BlockInventoryGui(String name, Material material, int guiId) {
		super(name, material);
		this.guiId = guiId;
	}
	
	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		if (world.isRemote) {
			return true;
		} else if (player != null) {
			TileEntity tileentity = world.getTileEntity(pos);
			if (tileentity instanceof IGui) {
				FMLNetworkHandler.openGui(player, NuclearCraft.instance, guiId, world, pos.getX(), pos.getY(), pos.getZ());
			}
		}
		return true;
	}
}
