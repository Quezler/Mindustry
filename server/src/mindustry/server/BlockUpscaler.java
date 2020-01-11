package mindustry.server;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class BlockUpscaler implements ApplicationListener{
    private Interval timer = new Interval();
    private Array<Tile> tmp = new Array<>();

    @Override
    public void update(){
        if(!state.is(State.playing)) return;

        if(!timer.get(0, 100)) return;

        for(Team team : Team.base()){
            indexer.getAllied(team, BlockFlag.scalable).each(tile -> {

                if(tile == null) return;
                if(!tile.block().flags.contains(BlockFlag.scalable)) return;

                tile.getLinkedTilesAs(tile.block().upscale.get(), tmp);
                if(tmp.select(t -> t.block() != tile.block()).size > 0) return;

                Call.onConstructFinish(tile, tile.block().upscale.get(), -1, tile.rotation(), tile.getTeam(), true);
            });
        }
    }
}
