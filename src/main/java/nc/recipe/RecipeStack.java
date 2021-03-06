package nc.recipe;

import java.util.List;

import com.google.common.collect.Lists;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public class RecipeStack implements IIngredient, IRecipeStack {
	
	public Object stack;

	public RecipeStack(Object stack) {
		this.stack = stack;
	}
	
	@Override
	public Object getIngredient() {
		return stack instanceof ItemStack ? ((ItemStack) stack).copy() : (stack instanceof FluidStack ? ((FluidStack) stack).copy() : stack);
	}

	@Override
	public boolean matches(Object object, SorptionType type) {
		if (object instanceof ItemStack && stack instanceof ItemStack) {
			ItemStack itemstack = (ItemStack) object;
			if (!itemstack.isItemEqual((ItemStack)stack)) {
				return false;
			}
			if (!ItemStack.areItemStackTagsEqual(itemstack, (ItemStack)stack)) {
				return false;
			}
			return type.checkStackSize(((ItemStack)stack).getCount(), itemstack.getCount());
		}
		else if (object instanceof FluidStack && stack instanceof FluidStack) {
			FluidStack fluidstack = (FluidStack) object;
			if (!fluidstack.isFluidEqual((FluidStack)stack)) {
				return false;
			}
			if (!FluidStack.areFluidStackTagsEqual(fluidstack, (FluidStack)stack)) {
				return false;
			}
			return type.checkStackSize(((FluidStack)stack).amount, fluidstack.amount);
		}
		return false;
	}

	@Override
	public Object getOutputStack() {
		return stack instanceof ItemStack ? ((ItemStack) stack).copy() : (stack instanceof FluidStack ? ((FluidStack) stack).copy() : stack);
	}

	@Override
	public List<Object> getIngredientList() {
		return Lists.newArrayList(stack);
	}

	@Override
	public int getStackSize() {
		if (stack instanceof ItemStack) return ((ItemStack)stack).getCount();
		else if (stack instanceof FluidStack) return ((FluidStack)stack).amount;
		return 0;
	}
}
