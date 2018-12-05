package music;

import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.PcmFilterFactory;
import com.sedmelluq.discord.lavaplayer.filter.UniversalPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import music.enums.AudioPlayerState;
import music.enums.Playmode;
import net.dv8tion.jda.core.JDA;

import java.util.List;
import java.util.Stack;
import java.util.Vector;

public class BotAudioPlayer extends AudioEventAdapter {
    public final JDA BOT;
    private Playmode playmode;
    private final AudioPlayer player;
    private AudioPlayerState state;
    private Vector<NormalizedAudioTrack> queue;
    private Stack<NormalizedAudioTrack> old;
    private static final int VOLUME = 18;

    public BotAudioPlayer(JDA bot, AudioPlayerManager audioPlayerManager){
        queue = new Vector<>();
        old = new Stack<>();
        this.BOT = bot;
        this.playmode = Playmode.NORMAL;
        this.player = audioPlayerManager.createPlayer();
        this.player.setVolume(VOLUME);
        this.player.addListener(this);
        this.state = AudioPlayerState.STOPPED;
    }

    public synchronized void addTrack(AudioTrack track){
        NormalizedAudioTrack nTrack = new NormalizedAudioTrack(track.makeClone(), VOLUME);

        if(queue.size() != 0){
            queue.add(nTrack);
            return;
        }

        queue.add(nTrack);

        player.setPaused(false);
        state = AudioPlayerState.PLAYING;
        
        startTrack(nTrack);
    }

    public synchronized void previousSong(){
        if(playmode == Playmode.LOOPALL){
            if(queue.size() == 0) return;
            NormalizedAudioTrack lastTrack = queue.lastElement();
            queue.remove(lastTrack);
            queue.add(0, lastTrack.makeClone());

            startTrack(queue.firstElement());
            state = AudioPlayerState.PLAYING;
            return;
        }

        if(old.size() == 0 ) return;

        NormalizedAudioTrack track = old.pop();

        if(queue.size() > 0) queue.set(0, queue.firstElement().makeClone());

        queue.add(0, track.makeClone());

        startTrack(queue.firstElement());
        state = AudioPlayerState.PLAYING;
    }

    public synchronized void nextSong(){
        player.stopTrack();

        if(queue.size() > 0) {
            if(playmode == Playmode.NORMAL || playmode == Playmode.LOOPONE) {
                old.push(queue.firstElement().makeClone());
                queue.remove(0);
            }else if(playmode == Playmode.LOOPALL){
                queue.add(queue.remove(0).makeClone());
            }
        }

        if(queue.size() == 0) {
            state = AudioPlayerState.STOPPED;
            return;
        }else{
            queue.set(0, queue.firstElement().makeClone());

            startTrack(queue.firstElement());
            state = AudioPlayerState.PLAYING;
        }
    }

    public synchronized void forward(long number){
        if(queue.size() == 0) return;

        long pos = queue.get(0).getPosition();
        long dur = queue.get(0).getDuration();

        if(pos + number >= dur) return;

        if(pos + number > 0) queue.get(0).setPosition(pos + number);
        else queue.get(0).setPosition(0);
    }

    public synchronized void jump(long number){
        if(queue.size() == 0) return;

        if(queue.get(0).getDuration() < number) return;

        if(number > 0) queue.get(0).setPosition(number);
        else queue.get(0).setPosition(0);
    }

    public synchronized void pauseSong(){
        if(state == AudioPlayerState.PLAYING) {
            player.setPaused(true);
        }
    }

    public synchronized void resumeSong(){
        if(state == AudioPlayerState.PAUSED) {
            player.setPaused(false);
        }
    }

    public synchronized void changePlaymode(Playmode playmode){
        if(playmode != null) this.playmode = playmode;
    }

    @Override
    public void onPlayerPause(AudioPlayer player) {
        state = AudioPlayerState.PAUSED;
    }

    @Override
    public void onPlayerResume(AudioPlayer player) {
        state = AudioPlayerState.PLAYING;
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        player.setPaused(false);
        state = AudioPlayerState.PLAYING;
    }

    @Override
    public synchronized void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if(endReason == AudioTrackEndReason.REPLACED || endReason == AudioTrackEndReason.STOPPED) return;

        switch (playmode){
            case NORMAL:
                nextSong();
                break;
            case LOOPONE:
                queue.set(0, queue.firstElement().makeClone());

                startTrack(queue.firstElement());
                break;
            case LOOPALL:
                queue.add(queue.firstElement().makeClone());
                queue.remove(0);
                queue.set(0, queue.firstElement().makeClone());

                startTrack(queue.firstElement());
                break;
            default:
        }
    }

    private void startTrack(NormalizedAudioTrack track){
        player.setVolume(track.getNormalizedVolume());
        player.startTrack(track.getAudioTrack(), false);
    }

    public void destroy(){
        player.destroy();
    }

    public AudioPlayerSendHandler getSendHandler() {
        return new AudioPlayerSendHandler(player);
    }

    public Playmode getPlaymode(){
        return playmode;
    }

    public AudioPlayerState getAudioPlayerState(){
        return state;
    }

    public AudioTrack[] getPlaylist(){
        Object[] objs = queue.toArray();
        AudioTrack[] tracks = new AudioTrack[objs.length];
        for(int i = 0; i < objs.length; i++){
            tracks[i] = (AudioTrack) objs[i];
        }

        return tracks;
    }
}
