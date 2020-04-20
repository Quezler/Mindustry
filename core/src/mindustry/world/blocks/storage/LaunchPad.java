package mindustry.world.blocks.storage;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.traits.*;
import mindustry.entities.type.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.plugin.*;
import mindustry.plugin.spidersilk.SpiderSilk.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.meta.*;

import java.util.concurrent.*;

import static mindustry.Vars.*;

public class LaunchPad extends StorageBlock{
    public final int timerLaunch = timers++;
    public final int timerSilo = timers++;
    /** Time inbetween launches. */
    public float launchTime;

    public LaunchPad(String name){
        super(name);
        update = true;
        hasItems = true;
        solid = true;

        entityType = LaunchPadEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(BlockStat.launchTime, launchTime / 60f, StatUnit.seconds);
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        return item.type == ItemType.material && tile.entity.items.total() < itemCapacity;
    }

    @Override
    public void draw(Tile tile){
        super.draw(tile);

        float progress = Mathf.clamp(Mathf.clamp((tile.entity.items.total() / (float)itemCapacity)) * ((tile.entity.timer.getTime(timerLaunch) / (launchTime / tile.entity.timeScale))));
        float scale = size / 3f;

        Lines.stroke(2f);
        Draw.color(Pal.accentBack);
        Lines.poly(tile.drawx(), tile.drawy(), 4, scale * 10f * (1f - progress), 45 + 360f * progress);

        Draw.color(Pal.accent);

        if(tile.entity.cons.valid()){
            for(int i = 0; i < 3; i++){
                float f = (Time.time() / 200f + i * 0.5f) % 1f;

                Lines.stroke(((2f * (2f - Math.abs(0.5f - f) * 2f)) - 2f + 0.2f));
                Lines.poly(tile.drawx(), tile.drawy(), 4, (1f - f) * 10f * scale);
            }
        }

        Draw.reset();
    }

    @Override
    public void update(Tile tile){
        LaunchPadEntity entity = tile.ent();

        if(world.isZone() && entity.cons.valid() && entity.items.total() >= itemCapacity && entity.timer.get(timerLaunch, launchTime / entity.timeScale)){
            for(Item item : Vars.content.items()){
                Events.fire(Trigger.itemLaunch);
                Effects.effect(Fx.padlaunch, tile);
                int used = Math.min(entity.items.get(item), itemCapacity);
                data.addItem(item, used);
                entity.items.remove(item, used);
                Events.fire(new LaunchItemEvent(item, used));
            }
        }

        if(Nydus.launchpad_upgrading.active()){
            if(entity.timer.get(timerSilo, 60 * 0.5f) && entity.cons.valid()){
                Array<Silk> passed = spiderSilk.silky
                .select(s -> s.team == tile.getTeam())
                .select(s -> tile.entity.items.has(s.requirements, state.rules.buildCostMultiplier * 11))
                .select(s -> s.footprint().count(t -> spiderSilk.reserved.contains(t.pos())) == 0)
                .select(s ->!s.abort.get());

                if(!passed.isEmpty()){
                    Silk silk = passed.first();
                    Bullet bullet = spiderSilk.bullet(spiderSilk.bullets.random(), tile, silk.tile);
                    silk.added.run();
                    tile.entity.items.sub(silk.requirements, state.rules.buildCostMultiplier);
                    bullet.deathrattle = b -> {
                        if(!silk.abort.get()){
                            silk.before.run();
                            silk.trigger.run();
                            silk.after.run();
                        }else{
                            if(!silk.team.cores().isEmpty()) silk.team.core().items.add(silk.requirements, state.rules.buildCostMultiplier);
                        }
                        silk.removed.run();
                    };
                }
            }
        }
    }

    class LaunchPadEntity extends StorageBlockEntity{
        // :ohno:
    }
}
