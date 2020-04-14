package mindustry.server.nydus;

import arc.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.distribution.ItemBridge.*;

import static mindustry.Vars.*;

public class BridgeBlocker implements ApplicationListener{
    @Override
    public void init(){
        Events.on(TapConfigEvent.class, event -> {
            if(event.tile.block == Blocks.itemBridge || event.tile.block == Blocks.bridgeConduit){
                if(event.player == null || event.player.getTeam() != Team.cute) Core.app.post(() -> cascade(event.tile));
            }
        });
    }

    protected void cascade(Tile tile){
        ItemBridgeEntity entity = tile.ent();
        if(((ItemBridge)tile.block).linkValid(tile, world.tile(entity.link))){
            Tile link = world.tile(entity.link);

            if(((ItemBridge)link.block).linkValid(link, world.tile(link.<ItemBridgeEntity>ent().link))) link.configure(Pos.invalid);
            entity.incoming.each(pos -> world.tile(pos).configure(Pos.invalid));
            link.<ItemBridgeEntity>ent().incoming.each(pos -> {
                if (world.tile(pos) != tile) world.tile(pos).configure(Pos.invalid);
            });

            cascade(link);
        }
    }
}
