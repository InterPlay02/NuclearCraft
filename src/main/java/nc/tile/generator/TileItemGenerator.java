package nc.tile.generator;

import java.util.ArrayList;

import nc.config.NCConfig;
import nc.recipe.BaseRecipeHandler;
import nc.recipe.IIngredient;
import nc.recipe.IRecipe;
import nc.recipe.RecipeMethods;
import nc.recipe.SorptionType;
import nc.tile.IGui;
import nc.tile.dummy.IInterfaceable;
import nc.tile.energy.TileEnergySidedInventory;
import nc.tile.energy.storage.EnumStorage.EnergyConnection;
import nc.tile.energyFluid.IBufferable;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

public abstract class TileItemGenerator extends TileEnergySidedInventory implements IInterfaceable, IBufferable, IGui {

	public final int inputSize;
	public final int outputSize;
	public final int otherSlotsSize;
	
	public int time;
	public boolean isGenerating;
	public boolean hasConsumed;
	
	public int tickCount;
	
	public final BaseRecipeHandler recipes;
	
	public TileItemGenerator(String name, int inSize, int outSize, int otherSize, int capacity, BaseRecipeHandler recipes) {
		super(name, 2*inSize + outSize + otherSize, capacity, EnergyConnection.OUT);
		inputSize = inSize;
		outputSize = outSize;
		otherSlotsSize = otherSize;
		this.recipes = recipes;
		
		int[] topSlots1 = new int[inSize];
		for (int i = 0; i < topSlots1.length; i++) topSlots1[i] = i;
		topSlots = topSlots1;
		
		int[] sideSlots1 = new int[inSize + outSize];
		for (int i = 0; i < sideSlots1.length; i++) sideSlots1[i] = i;
		sideSlots = sideSlots1;
		
		int[] bottomSlots1 = new int[outSize];
		for (int i = inSize; i < inSize + bottomSlots1.length; i++) bottomSlots1[i - inSize] = i;
		bottomSlots = bottomSlots1;
	}
	
	@Override
	public void onAdded() {
		super.onAdded();
		if (!world.isRemote) {
			isGenerating = isGenerating();
			hasConsumed = hasConsumed();
		}
	}
	
	@Override
	public void update() {
		super.update();
		updateGenerator();
	}
	
	public void updateGenerator() {
		boolean wasGenerating = isGenerating;
		isGenerating = canProcess() && isPowered();
		boolean shouldUpdate = false;
		if(!world.isRemote) {
			tick();
			if (time == 0) consume();
			if (isGenerating) process();
			if (wasGenerating != isGenerating) {
				shouldUpdate = true;
				updateBlockType();
			}
			pushEnergy();
		}
		if (shouldUpdate) markDirty();
	}
	
	public void tick() {
		if (tickCount > NCConfig.generator_update_rate) tickCount = 0; else tickCount++;
	}
	
	public boolean shouldCheck() {
		return tickCount > NCConfig.generator_update_rate;
	}
	
	public boolean isGenerating() {
		return canProcess() && isPowered();
	}
	
	public boolean canProcess() {
		return canProcessStacks();
	}
	
	public boolean isPowered() {
		return world.isBlockPowered(pos);
	}
	
	public void process() {
		time += getRateMultiplier();
		storage.changeEnergyStored(getProcessPower());
		if (time >= getProcessTime()) completeProcess();
	}
	
	public void completeProcess() {
		time = 0;
		produceProducts();
	}
	
	public void updateBlockType() {
		removeTileFromENet();
		setState(isGenerating);
		world.notifyNeighborsOfStateChange(pos, blockType, true);
		addTileToENet();
	}
	
	// Processing
	
	public abstract int getRateMultiplier();
		
	public abstract void setRateMultiplier(int value);
		
	public abstract int getProcessTime();
		
	public abstract void setProcessTime(int value);
		
	public abstract int getProcessPower();
		
	public abstract void setProcessPower(int value);
	
	public boolean hasConsumed() {
		if (world.isRemote) return hasConsumed;
		for (int i = 0; i < inputSize; i++) {
			if (!inventoryStacks.get(i + inputSize + outputSize + otherSlotsSize).isEmpty()) {
				return true;
			}
		}
		return false;
	}
		
	public boolean canProcessStacks() {
		for (int i = 0; i < inputSize; i++) {
			if (inventoryStacks.get(i).isEmpty() && !hasConsumed) {
				return false;
			}
		}
		if (time >= getProcessTime()) {
			return true;
		}
		Object[] output = hasConsumed ? outputs() : outputs();
		if (output == null || output.length != outputSize) {
			return false;
		}
		for(int j = 0; j < outputSize; j++) {
			if (output[j] == ItemStack.EMPTY || output[j] == null) {
				return false;
			} else {
				if (!inventoryStacks.get(j + inputSize).isEmpty()) {
					if (!inventoryStacks.get(j + inputSize).isItemEqual((ItemStack)output[j])) {
						return false;
					} else if (inventoryStacks.get(j + inputSize).getCount() + ((ItemStack)output[j]).getCount() > inventoryStacks.get(j + inputSize).getMaxStackSize()) {
						return false;
					}
				}
			}
		}
		return true;
	}
		
	public void consume() {
		IRecipe recipe = getRecipe(false);
		Object[] outputs = outputs();
		int[] inputOrder = inputOrder();
		if (outputs == null || inputOrder == RecipeMethods.INVALID) return;
		if (!hasConsumed) {
			for (int i = 0; i < inputSize; i++) {
				if (!inventoryStacks.get(i + inputSize + outputSize + otherSlotsSize).isEmpty()) {
					inventoryStacks.set(i + inputSize + outputSize + otherSlotsSize, ItemStack.EMPTY);
				}
			}
			for (int i = 0; i < inputSize; i++) {
				if (recipes != null) {
					inventoryStacks.set(i + inputSize + outputSize + otherSlotsSize, new ItemStack(inventoryStacks.get(i).getItem(), recipe.inputs().get(inputOrder[i]).getStackSize(), inventoryStacks.get(i).getMetadata()));
					inventoryStacks.get(i).shrink(recipe.inputs().get(inputOrder[i]).getStackSize());
				} else {
					inventoryStacks.set(i + inputSize + outputSize + otherSlotsSize, new ItemStack(inventoryStacks.get(i).getItem(), 1, inventoryStacks.get(i).getMetadata()));
					inventoryStacks.get(i).shrink(1);
				}
				if (inventoryStacks.get(i).getCount() <= 0) {
					inventoryStacks.set(i, ItemStack.EMPTY);
				}
			}
			hasConsumed = true;
		}
	}
	
	public void produceProducts() {
		if (hasConsumed) {
			Object[] outputs = outputs();
			for (int j = 0; j < outputSize; j++) {
				ItemStack outputStack = (ItemStack) outputs[j];
				if (inventoryStacks.get(j + inputSize).isEmpty()) {
					inventoryStacks.set(j + inputSize, outputStack);
				} else if (inventoryStacks.get(j + inputSize).isItemEqual(outputStack)) {
					inventoryStacks.get(j + inputSize).grow(outputStack.getCount());
				}
			}
			for (int i = inputSize + outputSize + otherSlotsSize; i < 2*inputSize + outputSize + otherSlotsSize; i++) {
				inventoryStacks.set(i, ItemStack.EMPTY);
			}
			hasConsumed = false;
		}
	}
		
	public IRecipe getRecipe(boolean consumed) {
		return recipes.getRecipeFromInputs(consumed ? consumedInputs() : inputs());
	}
	
	public Object[] inputs() {
		Object[] input = new Object[inputSize];
		for (int i = 0; i < inputSize; i++) {
			input[i] = inventoryStacks.get(i);
		}
		return input;
	}
	
	public Object[] consumedInputs() {
		Object[] input = new Object[inputSize];
		for (int i = 0; i < inputSize; i++) {
			input[i] = inventoryStacks.get(i + inputSize + outputSize + otherSlotsSize);
		}
		return input;
	}
	
	public int[] inputOrder() {
		int[] inputOrder = new int[inputSize];
		IRecipe recipe = getRecipe(false);
		if (recipe == null) return new int[] {};
		ArrayList<IIngredient> recipeIngredients = recipe.inputs();
		for (int i = 0; i < inputSize; i++) {
			inputOrder[i] = -1;
			for (int j = 0; j < recipeIngredients.size(); j++) {
				if (recipeIngredients.get(j).matches(inputs()[i], SorptionType.INPUT)) {
					inputOrder[i] = j;
					break;
				}
			}
			if (inputOrder[i] == -1) return RecipeMethods.INVALID;
		}
		return inputOrder;
	}
	
	public Object[] outputs() {
		Object[] output = new Object[outputSize];
		IRecipe recipe = getRecipe(hasConsumed);
		if (recipe == null) return null;
		ArrayList<IIngredient> outputs = recipe.outputs();
		for (int i = 0; i < outputSize; i++) {
			Object out = RecipeMethods.getIngredientFromList(outputs, i);
			if (out == null) return null;
			else output[i] = out;
		}
		return output;
	}
	
	// Inventory
	
	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		if (stack == ItemStack.EMPTY) return false;
		else if (slot >= inputSize && slot < inputSize + outputSize) return false;
		return NCConfig.smart_processor_input ? recipes.isValidInput(stack, inputs()) : recipes.isValidInput(stack);
	}
	
	// SidedInventory
	
	@Override
	public int[] getSlotsForFace(EnumFacing side) {
		return side == EnumFacing.DOWN ? bottomSlots : (side == EnumFacing.UP ? topSlots : sideSlots);
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, EnumFacing direction) {
		return isItemValidForSlot(slot, stack) && direction != EnumFacing.DOWN;
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, EnumFacing direction) {
		return direction != EnumFacing.UP && slot >= inputSize && slot < inputSize + outputSize;
	}
		
	// NBT
	
	@Override
	public NBTTagCompound writeAll(NBTTagCompound nbt) {
		super.writeAll(nbt);
		nbt.setInteger("time", time);
		nbt.setBoolean("isGenerating", isGenerating);
		nbt.setBoolean("hasConsumed", hasConsumed);
		return nbt;
	}
		
	@Override
	public void readAll(NBTTagCompound nbt) {
		super.readAll(nbt);
		time = nbt.getInteger("time");
		isGenerating = nbt.getBoolean("isGenerating");
		hasConsumed = nbt.getBoolean("hasConsumed");
	}
	
	// Inventory Fields

	@Override
	public int getFieldCount() {
		return 2;
	}

	@Override
	public int getField(int id) {
		switch (id) {
		case 0:
			return time;
		case 1:
			return getEnergyStored();
		default:
			return 0;
		}
	}

	@Override
	public void setField(int id, int value) {
		switch (id) {
		case 0:
			time = value;
			break;
		case 1:
			storage.setEnergyStored(value);
		}
	}
}
