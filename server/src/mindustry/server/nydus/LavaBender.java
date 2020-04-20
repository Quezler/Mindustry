package mindustry.server.nydus;

import arc.*;
import arc.math.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.world.*;

import static mindustry.Vars.world;

public class LavaBender implements ApplicationListener{
    @Override
    public void init(){
        Events.on(WorldLoadEvent.class, event -> {
            for(int x = 0; x < world.width(); x++){
                for(int y = 0; y < world.height(); y++){
                    Tile tile = world.rawTile(x, y);

                    if(tile.block == Blocks.rock){
                        tile.setOverlay(Blocks.oreCoal);
                    }

                    if(tile.block == Blocks.sandBoulder){
                        tile.setOverlay(Blocks.oreScrap);
                    }

                    if(tile.block == Blocks.shaleBoulder){
                        tile.setFloor(Blocks.tar.asFloor());
                    }

                    if(tile.block == Blocks.snowrock){
                        tile.setFloor(Blocks.water.asFloor());
                    }
                }
            }
        });
    }
}
