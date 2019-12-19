package io.anuke.mindustry.world.blocks.water;

import io.anuke.arc.graphics.g2d.*;
import io.anuke.mindustry.content.*;
import io.anuke.mindustry.ui.*;
import io.anuke.mindustry.world.*;
import io.anuke.mindustry.world.meta.*;
import io.anuke.mindustry.world.meta.values.*;

public class WaterBlock extends Block{
    protected Block facade;

    public WaterBlock(String name){
        super(name);

        destructible = true;
    }

    @Override
    public TextureRegion icon(Cicon icon){
        return facade.icon(icon);
    }

    @Override
    public void setStats(){
        //
    }
}
