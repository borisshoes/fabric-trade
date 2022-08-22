package net.borisshoes.trade.gui;

import net.borisshoes.trade.TradeSession;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.InventoryChangedListener;
import net.minecraft.item.ItemStack;

public class TradeInventoryListener implements InventoryChangedListener {
   
   TradeSession session;
   private boolean updating = false;
   
   public TradeInventoryListener(TradeSession session){
      this.session = session;
   }
   
   @Override
   public void onInventoryChanged(Inventory inv){
      if(!updating){
         updating = true;
         for(int i = 0; i < 24; i++){
            ItemStack stack = inv.getStack(i);
            if(stack.getCount() != 0){
               //System.out.println("Slot " + i + ": " + stack.getItem().getName().getString() + " (" + stack.getCount() + ")");
            }
         }
         session.checkReadyStatus();
         session.updateGuis();
         
      }
   }
   
   public void finishUpdate(){
      updating = false;
   }
}
