package mindustry.core;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.CommandHandler.*;
import arc.util.io.*;
import arc.util.serialization.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.ctype.*;
import mindustry.entities.*;
import mindustry.entities.traits.BuilderTrait.*;
import mindustry.entities.traits.*;
import mindustry.entities.type.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.game.Teams.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.net.Administration.*;
import mindustry.net.Packets.*;
import mindustry.plugin.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import mindustry.world.blocks.units.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.stream.*;
import java.util.zip.*;

import static arc.util.Log.*;
import static mindustry.Vars.*;

public class NetServer implements ApplicationListener{
    private final static int maxSnapshotSize = 430, timerBlockSync = 0;
    private final static float serverSyncTime = 12, blockSyncTime = 60 * 8;
    private final static Vec2 vector = new Vec2();
    private final static Rect viewport = new Rect();
    /** If a player goes away of their server-side coordinates by this distance, they get teleported back. */
    private final static float correctDist = 16f;

    private final Json json = new Json();

    public final Administration admins = new Administration();
    public final CommandHandler clientCommands = new CommandHandler("/");
    public TeamAssigner assigner = (player, players) -> {
        if(state.rules.pvp){
            //find team with minimum amount of players and auto-assign player to that.
            TeamData re = state.teams.getActive().min(data -> {
                if((state.rules.waveTeam == data.team && state.rules.waves) || !data.team.active()) return Integer.MAX_VALUE;

                int count = 0;
                for(Player other : players){
                    if(other.getTeam() == data.team && other != player){
                        count++;
                    }
                }
                return count;
            });
            return re == null ? null : re.team;
        }

        return state.rules.defaultTeam;
    };
    public IconAssigner iconAssigner = (player) -> {
        if(player.isServer) return Iconc.star;
        if(player.isAdmin) return Iconc.admin;

        Gamemode gamemode = Gamemode.bestFit(state.rules);
        if(gamemode == Gamemode.pvp)     return Iconc.modePvp;
        if(gamemode == Gamemode.editor)  return Iconc.fill;
        if(gamemode == Gamemode.attack)  return Iconc.modeAttack;
        if(gamemode == Gamemode.sandbox) return Iconc.wrench;

        return Iconc.modeSurvival;
    };
    public StatusAssigner statusAssigner = (player) -> {
        String prefix = "";

        if(player == null || player.con == null || !player.con.hasConnected) return prefix;

        prefix += Strings.format("[#{0}] ", player.getTeam().color);

        if(player.idle > 60 * 60){
            prefix += Iconc.pause;
        }else if(player.isDead()){
            prefix += Iconc.cancel;
        }else{
            prefix += Iconc.play;
        }

        prefix += " []";

        return prefix;
    };

    private boolean closing = false;
    private Interval timer = new Interval();

    private ByteBuffer writeBuffer = ByteBuffer.allocate(127);
    private ByteBufferOutput outputBuffer = new ByteBufferOutput(writeBuffer);

    /** Stream for writing player sync data to. */
    private ReusableByteOutStream syncStream = new ReusableByteOutStream();
    /** Array to hold the tiles that need sinking. */
    public Array<Tile> titanic = new Array<>();
    /** Data stream for writing player sync data to. */
    private DataOutputStream dataStream = new DataOutputStream(syncStream);

    public NetServer(){

        net.handleServer(Connect.class, (con, connect) -> {
            if(admins.isIPBanned(connect.addressTCP)){
                con.yeet(KickReason.banned, "ip", connect.addressTCP);
            }

            if(admins.isSubnetBanned(connect.addressTCP)){
                con.yeet(KickReason.banned, "subnet", connect.addressTCP);
            }
        });

        net.handleServer(Disconnect.class, (con, packet) -> {
            if(con.player != null){
                onDisconnect(con.player, packet.reason);
            }
        });

        net.handleServer(ConnectPacket.class, (con, packet) -> {
            if(con.address.startsWith("steam:")){
                packet.uuid = con.address.substring("steam:".length());
            }

            String uuid = packet.uuid;
            byte[] buuid = Base64Coder.decode(uuid);
            CRC32 crc = new CRC32();
            crc.update(buuid, 0, 8);
            ByteBuffer buff = ByteBuffer.allocate(8);
            buff.put(buuid, 8, 8);
            buff.position(0);
            if(crc.getValue() != buff.getLong()){
                con.yeet(KickReason.clientOutdated);
                return;
            }

            if(admins.isIPBanned(con.address) || admins.isSubnetBanned(con.address)) return;

            if(con.hasBegunConnecting){
                con.yeet(KickReason.idInUse);
                return;
            }

            PlayerInfo info = admins.getInfo(uuid);

            con.hasBegunConnecting = true;
            con.mobile = packet.mobile;

            if(packet.uuid == null || packet.usid == null){
                con.yeet(KickReason.idInUse);
                return;
            }

            if(admins.isIDBanned(uuid)){
                con.yeet(KickReason.banned, "uuid", uuid);
                return;
            }

            if(Time.millis() < info.lastKicked){
                con.yeet(KickReason.recentKick, "second(s)", (int)Math.ceil((info.lastKicked - Time.millis()) / 1000));
                return;
            }

            if(admins.getPlayerLimit() > 0 && playerGroup.size() >= admins.getPlayerLimit() && !netServer.admins.isAdmin(uuid, packet.usid)){
                con.yeet(KickReason.playerLimit, "playerlimit", admins.getPlayerLimit());
                return;
            }

            Array<String> extraMods = packet.mods.copy();
            Array<String> missingMods = mods.getIncompatibility(extraMods);

            if(!extraMods.isEmpty() || !missingMods.isEmpty()){
                //can't easily be localized since kick reasons can't have formatted text with them
                StringBuilder result = new StringBuilder("[accent]Incompatible mods![]\n\n");
                if(!missingMods.isEmpty()){
                    result.append("Missing:[lightgray]\n").append("> ").append(missingMods.toString("\n> "));
                    result.append("[]\n");
                }

                if(!extraMods.isEmpty()){
                    result.append("Unnecessary mods:[lightgray]\n").append("> ").append(extraMods.toString("\n> "));
                }
                con.yeet(result.toString());
            }

            if(!admins.isWhitelisted(packet.uuid, packet.usid)){
                info.adminUsid = packet.usid;
                info.lastName = packet.name;
                info.id = packet.uuid;
                admins.save();
                Call.onInfoMessage(con, "You are not whitelisted here.");
                Log.info("&lcDo &lywhitelist-add {0}&lc to whitelist the player &lb'{1}'", packet.uuid, packet.name);
                con.yeet(KickReason.whitelist);
                return;
            }

            if(packet.versionType == null || ((packet.version == -1 || !packet.versionType.equals(Version.type)) && Version.build != -1 && !admins.allowsCustomClients())){
                con.yeet(!Version.type.equals(packet.versionType) ? KickReason.typeMismatch : KickReason.customClient);
                return;
            }

            boolean preventDuplicates = headless && netServer.admins.getStrict();

            if(preventDuplicates){
                for(Player player : playerGroup.all()){
                    if(player.name.trim().equalsIgnoreCase(packet.name.trim())){
                        con.yeet(KickReason.nameInUse);
                        return;
                    }

                    if(player.uuid != null && player.usid != null && (player.uuid.equals(packet.uuid) || player.usid.equals(packet.usid))){
                        con.yeet(KickReason.idInUse, "uuid", player.uuid);
                        return;
                    }
                }
            }

            packet.name = fixName(packet.name);

            if(packet.name.trim().length() <= 0){
                con.yeet(KickReason.nameEmpty, "length", packet.name.trim().length());
                return;
            }

            String ip = con.address;

            admins.updatePlayerJoined(uuid, ip, packet.name);

            if(packet.version != Version.build && Version.build != -1 && packet.version != -1){
                con.yeet(packet.version > Version.build ? KickReason.serverOutdated : KickReason.clientOutdated);
                return;
            }

            if(packet.version == -1){
                con.modclient = true;
            }

            Player player = new Player();

            player.isAdmin = admins.isAdmin(uuid, packet.usid);
            player.con = con;
            player.usid = packet.usid;
            player.name = packet.name;
            player.uuid = uuid;
            player.isMobile = packet.mobile;
            player.dead = true;
            player.setNet(player.x, player.y);
            player.color.set(packet.color);
            player.color.a = 1f;
            player.lastSpawner = MechPad.coco.get(player.uuid);

            if(!spiderweb.has(player.uuid)) spiderweb.add(player.uuid);
            player.spiderling = spiderweb.get(player.uuid);

            player.spiderling.load();

            if(!player.spiderling.names.contains(player.name)){
                player.spiderling.names.add(player.name);
            }

            //save admin ID but don't overwrite it
            if(!player.isAdmin && !info.admin){
                info.adminUsid = packet.usid;
            }

            try{
                writeBuffer.position(0);
                player.write(outputBuffer);
            }catch(Throwable t){
                t.printStackTrace();
                con.yeet(KickReason.nameEmpty, "name", "malformed");
                return;
            }

            con.player = player;

            //playing in pvp mode automatically assigns players to teams
            player.setTeam(assignTeam(player, playerGroup.all()));

            sendWorldData(player);
            player.postSync();

            platform.updateRPC();

            Events.fire(new PlayerConnect(player));
        });

        net.handleServer(InvokePacket.class, (con, packet) -> {
            if(con.player == null) return;
            try{
                RemoteReadServer.readPacket(packet.writeBuffer, packet.type, con.player);
            }catch(ValidateException e){
                Log.debug("Validation failed for '{0}': {1}", e.player, e.getMessage());
            }catch(RuntimeException e){
                if(e.getCause() instanceof ValidateException){
                    ValidateException v = (ValidateException)e.getCause();
                    Log.debug("Validation failed for '{0}': {1}", v.player, v.getMessage());
                }else{
                    throw e;
                }
            }
        });

        registerCommands();
    }

    @Override
    public void init(){
        mods.eachClass(mod -> mod.registerClientCommands(clientCommands));
        coreProtect.registerClientCommands(clientCommands);
    }

    private void registerCommands(){
        clientCommands.<Player>register("help", "[page]", "Lists all commands.", (args, player) -> {
            if(args.length > 0 && !Strings.canParseInt(args[0])){
                player.sendMessage("[scarlet]'page' must be a number.");
                return;
            }
            int commandsPerPage = 6;
            int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
            int pages = Mathf.ceil((float)clientCommands.getCommandList().size / commandsPerPage);

            page --;

            if(page >= pages || page < 0){
                player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] " + pages + "[scarlet].");
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append(Strings.format("[orange]-- Commands Page[lightgray] {0}[gray]/[lightgray]{1}[orange] --\n\n", (page+1), pages));

            for(int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), clientCommands.getCommandList().size); i++){
                Command command = clientCommands.getCommandList().get(i);
                result.append("[orange] /").append(command.text).append("[white] ").append(command.paramText).append("[lightgray] - ").append(command.description).append("\n");
            }
            player.sendMessage(result.toString());
        });

        clientCommands.<Player>register("t", "<message...>", "Send a message only to your teammates.", (args, player) -> {
            playerGroup.all().each(p -> p.getTeam() == player.getTeam(), o -> o.sendMessage(args[0], player, "[#" + player.getTeam().color.toString() + "]<T>" + NetClient.colorizeName(player.id, player.name)));
        });

        //duration of a a kick in seconds
        int kickDuration = 60 * 15;
        //voting round duration in seconds
        float voteDuration = 0.5f * 60;
        //cooldown between votes
        int voteCooldown = 60 * 1;

        class VoteSession{
            Player target, caster;
            ObjectSet<String> voted = new ObjectSet<>();
            VoteSession[] map;
            Timer.Task task;
            int votes;

            public VoteSession(VoteSession[] map, Player target){
                this.target = target;
                this.map = map;
                this.task = Timer.schedule(() -> {
                    if(!checkPass()){
                        Call.sendMessage(Strings.format("[lightgray]Vote failed. Not enough votes to kick[orange] {0}[lightgray].", target.name));
                        map[0] = null;
                        task.cancel();
                    }
                }, voteDuration);
            }

            void vote(Player player, int d){
                votes += (d * player.voteMultiplier());
                voted.addAll(player.uuid, admins.getInfo(player.uuid).lastIP);

                Call.sendMessage(Strings.format("[orange]{0}[lightgray] has voted on kicking[orange] {1}[].[accent] ({2}/{3})\n[lightgray]Type[orange] /vote <y/n>[] to agree.",
                            player.name, target.name, votes, votesRequired()));
            }

            boolean checkPass(){

                if(votes <= -votesRequired()){
                    target = caster;
                    votes = votesRequired();
                }

                if(votes >= votesRequired()){
                    Call.sendMessage(Strings.format("[orange]Vote " + (target == caster ? "backfired" : "passed") +".[scarlet] {0}[orange] will be banned from the server for {1} minutes.", target.name, (kickDuration/60*(target.getInfo().timesKicked+1))));
                    target.getInfo().lastKicked = Time.millis() + kickDuration*1000*(target.getInfo().timesKicked+1);
                    playerGroup.all().each(p -> p.uuid != null && p.uuid.equals(target.uuid), p -> p.con.yeet(KickReason.vote));
                    map[0] = null;
                    task.cancel();
                    return true;
                }
                return false;
            }
        }

        Timekeeper vtime = new Timekeeper(voteCooldown);
        //current kick sessions
        VoteSession[] currentlyKicking = {null};

        clientCommands.<Player>register("votekick", "[player...]", "Vote to kick a player, with a cooldown.", (args, player) -> {
            if(!Config.enableVotekick.bool()){
                player.sendMessage("[scarlet]Vote-kick is disabled on this server.");
                return;
            }

            if(playerGroup.size() < 3){
                player.sendMessage("[scarlet]At least 3 players are needed to start a votekick.");
                return;
            }

            if(player.isLocal){
                player.sendMessage("[scarlet]Just kick them yourself if you're the host.");
                return;
            }

            if(args.length == 0){
                StringBuilder builder = new StringBuilder();
                builder.append("[orange]Players to kick: \n");
                for(Player p : playerGroup.all()){
                    if(p.isAdmin || p.con == null || p == player) continue;

                    builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id).append(")\n");
                }
                player.sendMessage(builder.toString());
            }else{
                Player found;
                if(args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))){
                    int id = Strings.parseInt(args[0].substring(1));
                    found = playerGroup.find(p -> p.id == id);
                }else{
                    found = playerGroup.find(p -> p.name.equalsIgnoreCase(args[0]));
                }

                if(found == null && args[0].length() > 1){
                    for(Player p : playerGroup.all()){
                        if((netServer.statusAssigner.assign(p) + p.name).equals(args[0])){
                            found = p;
                        }
                    }
                }

                if(found != null){
                    if(found.isAdmin){
                        player.sendMessage("[scarlet]Did you really expect to be able to kick an admin?");
                    }else if(found.isLocal){
                        player.sendMessage("[scarlet]Local players cannot be kicked.");
                    }else if(found.getTeam() != player.getTeam()){
                        player.sendMessage("[scarlet]Only players on your team can be kicked.");
                    }else{
                        if(!vtime.get()){
                            player.sendMessage("[scarlet]You must wait " + voteCooldown/60 + " minutes between votekicks.");
                            return;
                        }

                        VoteSession session = new VoteSession(currentlyKicking, found);
                        session.caster = player;
                        session.vote(player, 1);
                        vtime.reset();
                        currentlyKicking[0] = session;
                    }
                }else{
                    player.sendMessage("[scarlet]No player[orange]'" + args[0] + "'[scarlet] found.");
                }
            }
        });

        clientCommands.<Player>register("vote", "<y/n>", "Vote to kick the current player.", (arg, player) -> {
            if(currentlyKicking[0] == null){
                player.sendMessage("[scarlet]Nobody is being voted on.");
            }else{
                if(player.isLocal){
                    player.sendMessage("Local players can't vote. Kick the player yourself instead.");
                    return;
                }

                //hosts can vote all they want
                if(player.uuid != null && (currentlyKicking[0].voted.contains(player.uuid) || currentlyKicking[0].voted.contains(admins.getInfo(player.uuid).lastIP))){
                    player.sendMessage("[scarlet]You've already voted. Sit down.");
                    return;
                }

                if(currentlyKicking[0].target == player){
                    player.sendMessage("[scarlet]You can't vote on your own trial.");
                    return;
                }

                if(!arg[0].toLowerCase().equals("y") && !arg[0].toLowerCase().equals("n")){
                    player.sendMessage("[scarlet]Vote either 'y' (yes) or 'n' (no).");
                    return;
                }

                int sign = arg[0].toLowerCase().equals("y") ? 1 : -1;
                currentlyKicking[0].vote(player, sign);
            }
        });


        clientCommands.<Player>register("sync", "Re-synchronize world state.", (args, player) -> {
            if(player.isLocal){
                player.sendMessage("[scarlet]Re-synchronizing as the host is pointless.");
            }else{
                if(Time.timeSinceMillis(player.getInfo().lastSyncTime) < 1000 * 5){
                    player.sendMessage("[scarlet]You may only /sync every 5 seconds.");
                    return;
                }

                player.getInfo().lastSyncTime = Time.millis();
                player.syncbeacons.clear();
                Call.onWorldDataBegin(player.con);
                netServer.sendWorldData(player);
                player.postSync();
            }
        });

        clientCommands.<Player>register("tp", "[x] [y]", "Teleport to coordinates.", (args, player) -> {

            if(!player.isAdmin){
                player.sendMessage("[scarlet]This command is reserved for admins.");
                return;
            }

            int x = Mathf.clamp(Strings.parseInt(args[0]), 0, world.width());
            int y = Mathf.clamp(Strings.parseInt(args[1]), 0, world.height());

            player.setNet(x * tilesize, y * tilesize);
        });

        clientCommands.<Player>register("js", "<script...>", "Run arbitrary Javascript.", (args, player) -> {

            if(!player.isAdmin){
                player.sendMessage("[scarlet]This command is reserved for admins.");
                return;
            }

            scripter = player;
            player.sendMessage("[lightgray]" + mods.getScripts().runConsole("me = Vars.scripter;" + args[0]));
        });

        clientCommands.<Player>register("ts", "<script> [arguments...]", "Run typed Javascript.", (args, player) -> {

            if(!player.isAdmin){
                player.sendMessage("[scarlet]This command is reserved for admins.");
                return;
            }

            scripter = player;
            CompletableFuture.runAsync(() -> {
                try{
                    URL url = new URL("https://api.github.com/repos/Quezler/mindustry__nydus--script-pool/git/trees/HEAD?recursive=true");

                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Authorization", "token " + System.getenv("GithubToken"));

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String scripts = reader.lines().collect(Collectors.joining("\n"));

                        ObjectMap<String, Object> list = json.fromJson(ObjectMap.class, scripts);
                        String script = "\"[white]" + args[0] + "[scarlet] was not found\"";

                        for (int i = 0; i < ((Array)list.get("tree")).size; i++) {
                            JsonValue t = (JsonValue)((Array)list.get("tree")).get(i);

                            if (t.get("type").asString().equalsIgnoreCase("blob")) {
                                if (t.getString("path").substring(t.getString("path").lastIndexOf("/")+1).equals(args[0] + ".js")) {
                                    url = new URL("https://raw.githubusercontent.com/Quezler/mindustry__nydus--script-pool/master/" + t.getString("path"));

                                    conn = (HttpURLConnection) url.openConnection();
                                    conn.setRequestMethod("GET");
                                    conn.setRequestProperty("Authorization", "token " + System.getenv("GithubToken"));

                                    try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                                        script = r.lines().collect(Collectors.joining("\n"));
                                        break;
                                    }
                                }
                            }
                        }

                        StringBuilder jsArray;
                        if(args.length > 1){
                            jsArray = new StringBuilder("[");

                            StringBuilder str = new StringBuilder();
                            char[] argChars = args[1].toCharArray();
                            boolean inDoubleQuote = false;
                            boolean inSingleQuote = false;
                            for(int i = 0; i < argChars.length; i++){
                                char c = argChars[i];
                                if(c == '"'){
                                    inDoubleQuote = (!inDoubleQuote && !inSingleQuote) || argChars[i - 1] == '\\';
                                }else if(c == '\''){
                                    inSingleQuote = (!inSingleQuote && !inDoubleQuote) || argChars[i - 1] == '\\';
                                }

                                if(i == (argChars.length - 1)){ // on last index
                                    str.append(c);
                                    jsArray.append(str);
                                    str = new StringBuilder();

                                    continue;
                                }else if(inDoubleQuote || inSingleQuote){
                                    str.append(c);
                                    continue;
                                }else if(c == ' '){
                                    if(argChars[i - 1] == ' ') continue;
                                    str.append(", ");
                                }

                                str.append(c);
                            }

                            jsArray.append("]");
                        }else{
                            jsArray = new StringBuilder("[]");
                        }

                        String finalScript = "args = " + jsArray + ";" + script;
                        Core.app.post(() -> {
                            String output = mods.getScripts().runConsole(finalScript);
                            if(!output.equals("0.0")){
                                player.sendMessage("[lightgray]" + output);
                            }
                        });
                    }

                } catch (FileNotFoundException e) {
                    player.sendMessage(args[0] + " [scarlet]was not found");
                } catch(Exception e) {
                    e.printStackTrace();
                    player.sendMessage("[lightgray]" + e.getClass().getName());
                }
            });
        });

        clientCommands.<Player>register("me", "<keyword>", "Tries to apply the keyword to you.", (args, player) -> {

            if(!player.isAdmin){
                player.sendMessage("[scarlet]This command is reserved for admins.");
                return;
            }

            Team team = Structs.find(Team.all(), t -> t.name.equals(args[0]));
            if(team != null){
                player.team = team;
                return;
            }

            Mech mech = content.mechs().find(m -> m.name.equals(args[0]));
            if(mech != null){
                player.mech = mech;
                return;
            }

            Block block = content.blocks().find(b -> b.name.equals(args[0]));
            if(block != null){
                player.tileOn().constructNet(block, player.team, (byte)0);
                return;
            }

            Item item = content.items().find(i -> i.name.equals(args[0]));
            if(item != null){
                player.item().item = item;
                player.item().amount = player.getItemCapacity();
                return;
            }

            if("heal".equals(args[0])){
                player.heal();
                return;
            }

            if("kill".equals(args[0])){
                player.kill();
                return;
            }
        });

        clientCommands.<Player>register("readme", "README.md", (args, player) -> {

            player.sendMessage(Config.name.string().replace("[goldenrod]Nydus Network ", "") + " [goldenrod]" + Iconc.down);
            for(Nydus modifier : Nydus.values()) player.sendMessage(modifier.description);
            player.sendMessage(Config.name.string().replace("[goldenrod]Nydus Network ", "") + " [goldenrod]" + Iconc.up);

        });

        clientCommands.<Player>register("kil", "Server is kil.", (args, player) -> {

            if(!player.isAdmin){
                player.sendMessage("[scarlet]This command is reserved for admins.");
                return;
            }

            System.exit(2);
        });

        clientCommands.<Player>register("redeem", "[key]", "Redeems a key", (args, player) -> {
            if (!admins.existsKey(args[0])) {
                player.sendMessage("Key doesn't exist");
                return;
            }
            admins.adminByKey(args[0], player);
            
            player.sendMessage("Key Redeemed");
            Log.info("Key '" + args[0] + "' redeemed");
        });
    }

    public int votesRequired(){
        return 2 + (playerGroup.size() > 4 ? 1 : 0);
    }

    public Team assignTeam(Player current, Iterable<Player> players){
        return assigner.assign(current, players);
    }

    public void sendWorldData(Player player){
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DeflaterOutputStream def = new FastDeflaterOutputStream(stream);
        NetworkIO.writeWorld(player, def);
        WorldStream data = new WorldStream();
        data.stream = new ByteArrayInputStream(stream.toByteArray());
        player.con.sendStream(data);

        Log.debug("Packed {0} compressed bytes of world data.", stream.size());
    }

    public static void onDisconnect(Player player, String reason){
        //singleplayer multiplayer wierdness
        if(player.con == null){
            player.remove();
            return;
        }

        if(!player.con.hasDisconnected){
            if(player.con.hasConnected){
                Events.fire(new PlayerLeave(player));
                if(Config.showConnectMessages.bool()) spiderChat.disconnected(player);
                Call.onPlayerDisconnect(player.id);
            }

            if(Config.showConnectMessages.bool()) Log.info("&lm[{1}] &lc{0} has disconnected. &lg&fi({2})", player.name, player.uuid, reason);
        }

        player.remove();
        player.con.hasDisconnected = true;
    }

    @Remote(targets = Loc.client, unreliable = true)
    public static void onClientShapshot(
        Player player,
        int snapshotID,
        float x, float y,
        float pointerX, float pointerY,
        float rotation, float baseRotation,
        float xVelocity, float yVelocity,
        Tile mining,
        boolean boosting, boolean shooting, boolean chatting, boolean building,
        BuildRequest[] requests,
        float viewX, float viewY, float viewWidth, float viewHeight
    ){
        NetConnection connection = player.con;
        if(connection == null || snapshotID < connection.lastRecievedClientSnapshot) return;

        boolean verifyPosition = !player.isDead() && netServer.admins.getStrict() && headless;

        if(connection.lastRecievedClientTime == 0) connection.lastRecievedClientTime = Time.millis() - 16;

        connection.viewX = viewX;
        connection.viewY = viewY;
        connection.viewWidth = viewWidth;
        connection.viewHeight = viewHeight;

        long elapsed = Time.timeSinceMillis(connection.lastRecievedClientTime);

        float maxSpeed = boosting && !player.mech.flying ? player.mech.compoundSpeedBoost : player.mech.compoundSpeed;
        float maxMove = elapsed / 1000f * 60f * Math.min(maxSpeed, player.mech.maxSpeed) * 1.2f;

        player.pointerX = pointerX;
        player.pointerY = pointerY;
        player.setMineTile(mining);
        player.isTyping = chatting;
        player.isBoosting = boosting;
        player.isShooting = shooting;
        player.isBuilding = building;
        player.buildQueue().clear();

        for(BuildRequest req : requests){
            if(req == null) continue;
            Tile tile = world.tile(req.x, req.y);
            if(tile == null || (!req.breaking && req.block == null)) continue;
            //auto-skip done requests
            if(req.breaking && tile.block() == Blocks.air){
                continue;
            }else if(!req.breaking && tile.block() == req.block && (!req.block.rotate || tile.rotation() == req.rotation)){
                continue;
            }else if(connection.rejectedRequests.contains(r -> r.breaking == req.breaking && r.x == req.x && r.y == req.y)){ //check if request was recently rejected, and skip it if so
                continue;
            }else if(!netServer.admins.allowAction(player, req.breaking ? ActionType.breakBlock : ActionType.placeBlock, tile, action -> { //make sure request is allowed by the server
                action.block = req.block;
                action.rotation = req.rotation;
                action.config = req.config;
            })){
                //force the player to remove this request if that's not the case
                Call.removeQueueBlock(player.con, req.x, req.y, req.breaking);
                connection.rejectedRequests.add(req);
                continue;
            }
            player.buildQueue().addLast(req);
        }

        connection.rejectedRequests.clear();

        vector.set(x - player.getInterpolator().target.x, y - player.getInterpolator().target.y);
        vector.limit(maxMove);

        float prevx = player.x, prevy = player.y;
        player.set(player.getInterpolator().target.x, player.getInterpolator().target.y);
        if(!player.mech.flying && player.boostHeat < 0.01f){
            player.move(vector.x, vector.y);
        }else{
            player.x += vector.x;
            player.y += vector.y;
        }
        float newx = player.x, newy = player.y;

        if(!verifyPosition){
            player.x = prevx;
            player.y = prevy;
            newx = x;
            newy = y;
        }else if(Mathf.dst(x, y, newx, newy) > correctDist){
            Call.onPositionSet(player.con, newx, newy); //teleport and correct position when necessary
        }

        //reset player to previous synced position so it gets interpolated
        player.x = prevx;
        player.y = prevy;

        //set interpolator target to *new* position so it moves toward it
        player.getInterpolator().read(player.x, player.y, newx, newy, rotation, baseRotation);
        player.velocity().set(xVelocity, yVelocity); //only for visual calculation purposes, doesn't actually update the player

        connection.lastRecievedClientSnapshot = snapshotID;
        connection.lastRecievedClientTime = Time.millis();
    }

    @Remote(targets = Loc.client, called = Loc.server)
    public static void onAdminRequest(Player player, Player other, AdminAction action){

        if(!player.isAdmin){
            Log.warn("ACCESS DENIED: Player {0} / {1} attempted to perform admin action without proper security access.",
            player.name, player.con.address);
            return;
        }

        if(other == null || ((other.isAdmin && !player.isLocal) && other != player)){
            Log.warn("{0} attempted to perform admin action on nonexistant or admin player.", player.name);
            return;
        }

        if(action == AdminAction.wave){
            //no verification is done, so admins can hypothetically spam waves
            //not a real issue, because server owners may want to do just that
            state.wavetime = 0f;
        }else if(action == AdminAction.ban){
            netServer.admins.banPlayerIP(other.con.address);
            other.con.yeet(KickReason.banned);
            spiderChat.banned(other);
            Log.info("&lc{0} has banned {1}.", player.name, other.name);
        }else if(action == AdminAction.kick){
            other.con.yeet(KickReason.kick);
            other.getInfo().lastKicked = Time.millis() + (30 * 60) * 1000;
            spiderChat.kicked(other);
            Log.info("&lc{0} has kicked {1}.", player.name, other.name);
        }else if(action == AdminAction.trace){
            TraceInfo info = new TraceInfo(other.con.address, other.uuid, other.con.modclient, other.con.mobile);
            if(player.con != null){
                Call.onTraceInfo(player.con, other, info);
            }else{
                NetClient.onTraceInfo(other, info);
            }
            Log.info("&lc{0} has requested trace info of {1}.", player.name, other.name);
        }
    }

    @Remote(targets = Loc.client)
    public static void connectConfirm(Player player){
        if(player.con == null || player.con.hasConnected) return;

        if(!state.rules.tags.containsKey("silicon")){
            if(!player.spiderling.unlockedBlocks.contains(Blocks.siliconSmelter)){
                Call.onConnect(player.con, "mindustry.nydus.app", 6569);
                Log.info("&lm[{1}] &y{0} got sent to the silicon valley. ", player.name, player.uuid);
                return;
            }
        }

        player.add();
        player.con.hasConnected = true;
        if(Config.showConnectMessages.bool()){
            spiderChat.connected(player);
            Log.info("&lm[{1}] &y{0} has connected. ", player.name, player.uuid);
            player.spiderling.log();
        }

        if(!Config.motd.string().equalsIgnoreCase("off")){
            player.sendMessage(Config.motd.string());
        }

        Events.fire(new PlayerJoin(player));
        Call.onInfoToast(player.con,"/readme", 2.5f);
    }

    public boolean isWaitingForPlayers(){
        if(state.rules.pvp){
            int used = 0;
            for(TeamData t : state.teams.getActive()){
                if(playerGroup.count(p -> p.getTeam() == t.team) > 0){
                    used++;
                }
            }
            return used < 2;
        }
        return false;
    }

    @Override
    public void update(){

        if(!headless && !closing && net.server() && state.is(State.menu)){
            closing = true;
            ui.loadfrag.show("$server.closing");
            Time.runTask(5f, () -> {
                net.closeServer();
                ui.loadfrag.hide();
                closing = false;
            });
        }

        if(!state.is(State.menu) && net.server()){
            sync();
        }
    }

    /** Should only be used on the headless backend. */
    public void openServer(){
        try{
            net.host(Config.port.num());
            info("&lcOpened a server on port {0}.", Config.port.num());
        }catch(BindException e){
            Log.err("Unable to host: Port already in use! Make sure no other servers are running on the same port in your network.");
            state.set(State.menu);
        }catch(IOException e){
            err(e);
            state.set(State.menu);
        }
    }

    public void kickAll(KickReason reason){
        for(NetConnection con : net.getConnections()){
            con.yeet(reason);
        }
    }

    /** Writes a block snapshot to all players. */
    public void writeBlockSnapshots(){
        for(TileEntity entity : tileGroup.all()){
            if(!entity.block.sync) continue;

            titanic.add(entity.tile);
        }
    }

    /** Sends a block snapshot to all players. */
    public void sendBlockSnapshots() throws IOException{
        if(titanic.isEmpty()) return;

        syncStream.reset();

        short sent = 0;
        for(Tile tile : titanic){
            sent ++;

            if(tile == null || tile.entity == null) continue;

            dataStream.writeInt(tile.entity.tile.pos());
            tile.entity.write(dataStream);

            if(syncStream.size() > maxSnapshotSize){
                dataStream.close();
                byte[] stateBytes = syncStream.toByteArray();
                Call.onBlockSnapshot(sent, (short)stateBytes.length, net.compressSnapshot(stateBytes));
                sent = 0;
                syncStream.reset();
            }
        }

        if(sent > 0){
            dataStream.close();
            byte[] stateBytes = syncStream.toByteArray();
            Call.onBlockSnapshot(sent, (short)stateBytes.length, net.compressSnapshot(stateBytes));
        }

        titanic.clear();
    }

    public void writeEntitySnapshot(Player player) throws IOException{
        syncStream.reset();
        Array<CoreEntity> cores = state.teams.cores(player.getTeam());

        dataStream.writeByte(cores.size);

        for(CoreEntity entity : cores){
            dataStream.writeInt(entity.tile.pos());
            entity.items.write(dataStream);
        }

        dataStream.close();
        byte[] stateBytes = syncStream.toByteArray();

        //write basic state data.
        Call.onStateSnapshot(player.con, state.wavetime, state.wave, state.enemies, (short)stateBytes.length, net.compressSnapshot(stateBytes));

        viewport.setSize(player.con.viewWidth, player.con.viewHeight).setCenter(player.con.viewX, player.con.viewY);

        //check for syncable groups
        for(EntityGroup<?> group : entities.all()){
            if(group.isEmpty() || !(group.all().get(0) instanceof SyncTrait)) continue;

            //make sure mapping is enabled for this group
            if(!group.mappingEnabled()){
                throw new RuntimeException("Entity group '" + group.getType() + "' contains SyncTrait entities, yet mapping is not enabled. In order for syncing to work, you must enable mapping for this group.");
            }

            syncStream.reset();

            int sent = 0;

            for(Entity entity :  group.all()){
                SyncTrait sync = (SyncTrait)entity;
                if(!sync.isSyncing()) continue;

                //write all entities now
                dataStream.writeInt(entity.getID()); //write id
                dataStream.writeByte(sync.getTypeID().id); //write type ID
                sync.write(dataStream); //write entity

                sent++;

                if(syncStream.size() > maxSnapshotSize){
                    dataStream.close();
                    byte[] syncBytes = syncStream.toByteArray();
                    Call.onEntitySnapshot(player.con, (byte)group.getID(), (short)sent, (short)syncBytes.length, net.compressSnapshot(syncBytes));
                    sent = 0;
                    syncStream.reset();
                }
            }

            if(sent > 0){
                dataStream.close();

                byte[] syncBytes = syncStream.toByteArray();
                Call.onEntitySnapshot(player.con, (byte)group.getID(), (short)sent, (short)syncBytes.length, net.compressSnapshot(syncBytes));
            }
        }
    }

    String fixName(String name){
        name = name.trim();
        if(name.equals("[") || name.equals("]")){
            return "";
        }

        for(int i = 0; i < name.length(); i++){
            if(name.charAt(i) == '[' && i != name.length() - 1 && name.charAt(i + 1) != '[' && (i == 0 || name.charAt(i - 1) != '[')){
                String prev = name.substring(0, i);
                String next = name.substring(i);
                String result = checkColor(next);

                name = prev + result;
            }
        }

        StringBuilder result = new StringBuilder();
        int curChar = 0;
        while(curChar < name.length() && Strings.stripColors(result.toString()).getBytes().length < maxNameLength){
            result.append(name.charAt(curChar++));
        }
        return result.toString();
    }

    String checkColor(String str){

        for(int i = 1; i < str.length(); i++){
            if(str.charAt(i) == ']'){
                String color = str.substring(1, i);

                if(Colors.get(color.toUpperCase()) != null || Colors.get(color.toLowerCase()) != null){
                    Color result = (Colors.get(color.toLowerCase()) == null ? Colors.get(color.toUpperCase()) : Colors.get(color.toLowerCase()));
                    if(result.a <= 0.8f){
                        return str.substring(i + 1);
                    }
                }else{
                    try{
                        Color result = Color.valueOf(color);
                        if(result.a <= 0.8f){
                            return str.substring(i + 1);
                        }
                    }catch(Exception e){
                        return str;
                    }
                }
            }
        }
        return str;
    }

    void sync(){

        try{
            //iterate through each player
            for(int i = 0; i < playerGroup.size(); i++){
                Player player = playerGroup.all().get(i);
                if(player.isLocal) continue;

                if(player.con == null || !player.con.isConnected()){
                    onDisconnect(player, "disappeared");
                    continue;
                }

                NetConnection connection = player.con;

                if(!player.timer.get(Player.timerSync, serverSyncTime) || !connection.hasConnected) continue;

                writeEntitySnapshot(player);
            }

            if(playerGroup.size() > 0 && Core.settings.getBool("blocksync") && timer.get(timerBlockSync, blockSyncTime)){
                writeBlockSnapshots();
            }

            sendBlockSnapshots();

        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public interface TeamAssigner{
        Team assign(Player player, Iterable<Player> players);
    }

    public interface IconAssigner{
        char assign(Player player);
    }

    public interface StatusAssigner{
        String assign(Player player);
    }
}
