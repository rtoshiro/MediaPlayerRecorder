package rtoshiro.github.com.audio;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;

import java.io.IOException;

/**
 * Created by Tox on 12/10/15.
 * <p/>
 * Library that handles playing and recording audio files.
 * Play local and remote files and record local files.
 */
public class MediaPlayerRecorder implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener, MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnBufferingUpdateListener {

    public interface OnTimeUpdateListener {
        /**
         * Called when the time has elapsed
         * It is called for player or recorder.
         *
         * @param mpr             MediaPlayerRecorder the time has elapsed
         * @param currentPosition When PLAYING state, returns MediaPlayer.getCurrentPosition. (elapsed time in milliseconds)
         *                        When RECORDING state, returns the elapsed time since recording started (elapsed time in milliseconds)
         */
        void onTimeUpdate(MediaPlayerRecorder mpr, long currentPosition);
    }

    public interface OnCompletionListener {
        /**
         * Called when the end of a media source is reached during playback or
         * when recorder max duration or max file size has reached
         *
         * @param mpr     MediaPlayerRecorder
         * @param success Indicates if it has ended successfully or not (called after onError)
         */
        void onCompletion(MediaPlayerRecorder mpr, boolean success);
    }

    public interface OnPreparedListener {
        /**
         * Called when MediaPlayerRecorder is prepared to record.
         *
         * @param mpr MediaPlayerRecorder prepared to record
         */
        void onRecorderPrepared(MediaPlayerRecorder mpr);

        /**
         * Called when MediaPlayerRecorder is prepared to play.
         *
         * @param mpr MediaPlayerRecorder prepared to play
         */
        void onPlayerPrepared(MediaPlayerRecorder mpr);
    }

    public interface OnBufferingUpdateListener {
        /**
         * Called to update status in buffering a media stream received through progressive HTTP download.
         *
         * @param mpr     MediaPlayerRecorder the update pertains to.
         * @param percent Percentage 0-100 of the content that has been buffered or played thus far
         */
        void onBufferingUpdate(MediaPlayerRecorder mpr, int percent);
    }

    public interface OnSeekListener {
        /**
         * Called to indicate the completion of a seek operation.
         *
         * @param mpr MediaPlayerRecorderthat issued the seek operation
         */
        void onSeekComplete(MediaPlayerRecorder mpr);
    }

    public interface OnErrorListener {
        /**
         * Called to indicate error
         *
         * @param mpr   MediaPlayerRecorder the error pertains to
         * @param what  the type of error that has occurred
         *              MediaPlayer.MEDIA_ERROR_UNKNOWN
         *              MediaPlayer.MEDIA_ERROR_SERVER_DIED
         *              MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN
         *              MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN
         *              MediaRecorder.MEDIA_ERROR_SERVER_DIED
         * @param extra an extra code, specific to the error. Typically implementation dependent
         *              MediaPlayer.MEDIA_ERROR_IO
         *              MediaPlayer.MEDIA_ERROR_MALFORMED
         *              MediaPlayer.MEDIA_ERROR_UNSUPPORTED
         *              MediaPlayer.MEDIA_ERROR_TIMED_OUT
         * @return True if the method handled the error, false if it didn't. Returning false, or not having an OnErrorListener at all, will cause the OnCompletionListener to be called.
         */
        boolean onError(MediaPlayerRecorder mpr, int what, int extra);
    }

    /**
     * Initial state before calling prepareTo or when recording is paused
     * As recorder doesn't have PAUSED stated, it sets state to NONE
     */
    public static final int NONE = 0;

    /**
     * In this state the MediaPlayerRecorder has already been paused from recording or playing
     */
    public static final int ERROR = -1;

    /**
     * When player is paused
     */
    public static final int PAUSED = 1;

    /**
     * When player is playing something
     */
    public static final int PLAYING = 2;

    /**
     * When player is preparing to play
     */
    public static final int PREPARINGTOPLAY = 3;

    /**
     * When player is prepared to play
     */
    public static final int PREPAREDTOPLAY = 4;

    /**
     * When player is preparing to play and is going to play as soon as the player is prepared
     */
    public static final int PREPARINGTOPLAYANDPLAYING = 5;

    /**
     * When recorder is recording
     */
    public static final int RECORDING = 6;

    /**
     * When recorder is preparing to record
     */
    public static final int PREPARINGTORECORD = 7;

    /**
     * When recorder is prepared to record
     */
    public static final int PREPAREDTORECORD = 8;

    /**
     * When recorder is preparing to record and is going to record as soon as the recorder is ready
     */
    public static final int PREPARINGTORECORDANDRECORDING = 9;

    protected MediaPlayer player;
    protected MediaRecorder recorder;

    protected int currentState;
    protected int lastState;
    protected String dataSource;
    protected boolean looping;
    protected int maxDuration;
    protected long maxFileSize;
    protected long startRecordTime;
    protected Handler handler;

    /**
     * Runnable responsable to keep tracking on player or recorder updates
     */
    protected Runnable runnableTimeUpdate = new Runnable() {
        public void run() {
            if (onTimeUpdateListener != null) {
                if (currentState == PLAYING) {
                    onTimeUpdateListener.onTimeUpdate(MediaPlayerRecorder.this, player.getCurrentPosition());
                } else if (currentState == RECORDING) {
                    long millis = System.currentTimeMillis() - startRecordTime;
                    onTimeUpdateListener.onTimeUpdate(MediaPlayerRecorder.this, millis);
                }

                handler.postDelayed(this, 1000);
            }
        }
    };

    protected OnTimeUpdateListener onTimeUpdateListener;
    protected OnCompletionListener onCompletionListener;
    protected OnPreparedListener onPreparedListener;
    protected OnBufferingUpdateListener onBufferingUpdateListener;
    protected OnSeekListener onSeekListener;
    protected OnErrorListener onErrorListener;

    protected void initRecorder() {
        if (this.recorder == null) {
            this.recorder = new MediaRecorder();
            this.recorder.setOnErrorListener(this);
            this.recorder.setOnInfoListener(this);
            this.recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            this.recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            this.recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            this.recorder.setMaxDuration(maxDuration);
            this.recorder.setMaxFileSize(maxFileSize);
            if (this.dataSource != null)
                this.recorder.setOutputFile(dataSource);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
                this.recorder.setAudioSamplingRate(16000);
                this.recorder.setAudioChannels(1);
            }
        }
    }

    protected void initPlayer() throws IOException {
        if (this.player == null) {
            this.player = new MediaPlayer();
            this.player.setOnPreparedListener(this);
            this.player.setOnErrorListener(this);
            this.player.setOnSeekCompleteListener(this);
            this.player.setOnCompletionListener(this);
            this.player.setOnBufferingUpdateListener(this);
            this.player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            this.player.setLooping(looping);
            if (this.dataSource != null)
                this.player.setDataSource(dataSource);
        }
    }

    protected void releasePlayer() {
        if (this.player != null) {
            this.player.release();
            this.player = null;
        }
    }

    protected void releaseRecorder() {
        if (this.recorder != null) {
            this.recorder.stop();
            this.recorder.release();
            this.recorder = null;
        }
    }

    protected void startTimeUpdate() {
        startRecordTime = System.currentTimeMillis();
        handler.post(runnableTimeUpdate);
    }

    protected void stopTimeUpdate() {
        handler.removeCallbacks(runnableTimeUpdate);
    }

    public MediaPlayerRecorder() {
        this.looping = false;
        this.handler = new Handler();
        this.currentState = NONE;
    }

    /**
     * Prepares the player for playback, asynchronously.
     *
     * @return True if has started preparing to play successfully. Otherwise, returns false
     * @throws IllegalStateException If the current state is related to record (RECORDING, PREPARINGTORECORD, ...)
     */
    public boolean prepareToPlay() throws IllegalStateException {
        switch (this.currentState) {
            case NONE:
            case PAUSED: {
                this.currentState = PREPARINGTOPLAY;
                break;
            }
            case PLAYING:
            case PREPARINGTOPLAY:
            case PREPAREDTOPLAY:
            case PREPARINGTOPLAYANDPLAYING: {
                return true;
            }
            case RECORDING:
            case PREPARINGTORECORD:
            case PREPAREDTORECORD:
            case PREPARINGTORECORDANDRECORDING: {
                throw new IllegalStateException("Recording state conflicts with Playing state");
            }
        }

        if (this.player == null) {
            try {
                initPlayer();
                this.player.prepareAsync();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else if (this.currentState == PREPARINGTOPLAY)
            this.currentState = PREPAREDTOPLAY;

        return true;
    }

    /**
     * Prepares the recorder to begin capturing and encoding data.
     * This method must be called after setting up the desired audio and video sources.
     *
     * @return True is it has started preparing for record successfully. Otherwise, false
     */
    public boolean prepareToRecord() throws IllegalStateException {
        if (dataSource == null)
            return false;

        switch (this.currentState) {
            case NONE:
            case PAUSED: {
                release();

                this.currentState = PREPARINGTORECORD;
                break;
            }
            case RECORDING:
            case PREPARINGTORECORD:
            case PREPAREDTORECORD:
            case PREPARINGTORECORDANDRECORDING: {
                return true;
            }
            case PLAYING:
            case PREPARINGTOPLAY:
            case PREPAREDTOPLAY:
            case PREPARINGTOPLAYANDPLAYING: {
                throw new IllegalStateException("Playing state conflicts with Recording state");
            }

        }

        initRecorder();
        try {
            this.recorder.prepare();
            if (this.currentState == PREPARINGTORECORDANDRECORDING) {
                this.currentState = PREPAREDTORECORD;

                if (onPreparedListener != null)
                    onPreparedListener.onRecorderPrepared(this);

                record();
            } else {
                this.currentState = PREPAREDTORECORD;

                if (onPreparedListener != null)
                    onPreparedListener.onRecorderPrepared(this);
            }


        } catch (IOException e) {
            e.printStackTrace();

            pause();
            return false;
        }

        return true;
    }

    /**
     * Starts or resumes playback.
     * If playback had previously been paused, playback will continue from where it was paused.
     * If playback had been released, or never started before, playback will start at the beginning.
     *
     * @return True if is has been started without any error. Returns false if prepareToPlay returns false.
     */
    public boolean play() throws IllegalStateException {
        switch (this.currentState) {
            case NONE: {
                if (prepareToPlay())
                    return play();
                else
                    return false;
            }
            case PLAYING:
            case PREPARINGTOPLAYANDPLAYING: {
                return true;
            }
            case PREPARINGTOPLAY: {
                this.currentState = PREPARINGTOPLAYANDPLAYING;
                return true;
            }
            case PAUSED:
            case PREPAREDTOPLAY: {
                this.currentState = PLAYING;
                this.startTimeUpdate();
                this.player.start();
                return true;
            }
            case RECORDING:
            case PREPARINGTORECORD:
            case PREPAREDTORECORD:
            case PREPARINGTORECORDANDRECORDING: {
                throw new IllegalStateException("Recording state conflicts with Playing state");
            }
            default:
                return false;
        }
    }

    /**
     * Pauses playback or recording.
     * It can be safely called in any state.
     */
    public void pause() {
        switch (this.currentState) {
            case PLAYING:
            case PREPARINGTOPLAY:
            case PREPAREDTOPLAY:
            case PREPARINGTOPLAYANDPLAYING: {
                this.player.pause();
                stopTimeUpdate();
                this.currentState = PAUSED;
                break;
            }
            case RECORDING:
            case PREPAREDTORECORD:
            case PREPARINGTORECORD:
            case PREPARINGTORECORDANDRECORDING: {
                if (this.recorder != null) {
//                    releaseRecorder();
                    release();
                    stopTimeUpdate();
                }
                break;
            }
        }
    }

    /**
     * Begins capturing and encoding data to the file specified with setDataSource()
     * *
     *
     * @return True if is has started recording successfully
     * @throws IllegalStateException If the current state is related to player
     */
    public boolean record() throws IllegalStateException {

        switch (this.currentState) {
            case NONE:
            case PAUSED: {
                if (prepareToRecord())
                    return record();
                else
                    return false;
            }
            case PLAYING:
            case PREPARINGTOPLAY:
            case PREPAREDTOPLAY:
            case PREPARINGTOPLAYANDPLAYING: {
                throw new IllegalStateException("Playing state conflicts with Recording state");
            }
            case RECORDING:
            case PREPARINGTORECORDANDRECORDING: {
                return true;
            }
            case PREPARINGTORECORD: {
                this.currentState = PREPARINGTORECORDANDRECORDING;
                return true;
            }
            case PREPAREDTORECORD: {
                this.currentState = RECORDING;
                this.startTimeUpdate();
                this.recorder.start();
                return true;
            }
            default:
                return false;
        }
    }

    /**
     * Seeks to specified time position.
     *
     * @param sec the offset in milliseconds from the start to seek to
     */
    public void seekTo(int sec) {
        if (this.currentState == RECORDING ||
                this.currentState == PREPARINGTORECORDANDRECORDING ||
                this.currentState == PREPARINGTORECORD ||
                this.currentState == PREPAREDTORECORD) {
            throw new IllegalStateException("Recording state conflicts with playing state");
        }
        if (this.player != null) {
            this.lastState = this.currentState;
            pause();
            this.player.seekTo(sec);
        }
    }

    /**
     * Releases all resources from player and recorder.
     * It is called when recorder is paused
     */
    public void release() {
        releasePlayer();
        releaseRecorder();
        this.currentState = NONE;
    }

    public boolean isPlaying() {
        return (this.currentState == PLAYING || this.currentState == PREPARINGTOPLAYANDPLAYING);
    }

    public boolean isRecording() {
        return (this.currentState == RECORDING || this.currentState == PREPARINGTORECORDANDRECORDING);
    }

    public boolean isLooping() {
        return looping;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
        if (this.player != null)
            this.player.setLooping(looping);
    }

    public String getDataSource() {
        return dataSource;
    }

    /**
     * Sets the data source (file-path or http URL) to use.
     * If it has already been initialized, the MediaPlayerRecorder releases the player and recorder
     * before setting the new data source. In that case, you should call play/record again.
     *
     * @param path The path of the file or the http URL of the stream.
     */
    public void setDataSource(String path) {
        release();
        this.dataSource = path;
    }

    public int getMaxDuration() {
        return maxDuration;
    }

    public void setMaxDuration(int maxDuration) {
        this.maxDuration = maxDuration;
        if (this.recorder != null)
            this.recorder.setMaxDuration(maxDuration);
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
        if (this.recorder != null)
            this.recorder.setMaxFileSize(maxFileSize);
    }

    public int getCurrentState() {
        return currentState;
    }

    /**
     * Gets the current playback position.
     *
     * @return The current position in milliseconds, or -1 if current position
     * cannot be calculated
     */
    public int getCurrentPosition() {
        if (this.player != null)
            return this.player.getCurrentPosition();
        return -1;
    }

    /**
     * Gets the duration of the file.
     *
     * @return The duration in milliseconds, or -1 if no duration is available
     * (for example, if streaming live content)
     */
    public int getDuration() {
        if (this.player != null)
            return this.player.getDuration();
        return -1;
    }

    public OnTimeUpdateListener getOnTimeUpdateListener() {
        return onTimeUpdateListener;
    }

    public void setOnTimeUpdateListener(OnTimeUpdateListener onTimeUpdateListener) {
        this.onTimeUpdateListener = onTimeUpdateListener;
    }

    public OnCompletionListener getOnCompletionListener() {
        return onCompletionListener;
    }

    public void setOnCompletionListener(OnCompletionListener onCompletionListener) {
        this.onCompletionListener = onCompletionListener;
    }

    public OnPreparedListener getOnPreparedListener() {
        return onPreparedListener;
    }

    public void setOnPreparedListener(OnPreparedListener onPreparedListener) {
        this.onPreparedListener = onPreparedListener;
    }

    public OnBufferingUpdateListener getOnBufferingUpdateListener() {
        return onBufferingUpdateListener;
    }

    public void setOnBufferingUpdateListener(OnBufferingUpdateListener onBufferingUpdateListener) {
        this.onBufferingUpdateListener = onBufferingUpdateListener;
    }

    public OnSeekListener getOnSeekListener() {
        return onSeekListener;
    }

    public void setOnSeekListener(OnSeekListener onSeekListener) {
        this.onSeekListener = onSeekListener;
    }

    public OnErrorListener getOnErrorListener() {
        return onErrorListener;
    }

    public void setOnErrorListener(OnErrorListener onErrorListener) {
        this.onErrorListener = onErrorListener;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        int oldState = this.currentState;
        this.currentState = PREPAREDTOPLAY;

        if (oldState == PREPARINGTOPLAYANDPLAYING)
            play();

        if (onPreparedListener != null)
            onPreparedListener.onPlayerPrepared(this);
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        pause();

        if (onErrorListener != null)
            return onErrorListener.onError(this, i, i1);
        return false;
    }

    @Override
    public void onError(MediaRecorder mediaRecorder, int i, int i1) {
        pause();

        boolean result = false;
        if (onErrorListener != null)
            result = onErrorListener.onError(this, i, i1);

        if (!result)
            if (onCompletionListener != null)
                onCompletionListener.onCompletion(this, false);
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        if (this.currentState == PLAYING && this.looping) {
            play();
        } else {
            pause();
        }

        if (onCompletionListener != null)
            onCompletionListener.onCompletion(this, true);
    }

    @Override
    public void onInfo(MediaRecorder mediaRecorder, int i, int i1) {
        if (i == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                i == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            pause();

            if (onCompletionListener != null)
                onCompletionListener.onCompletion(this, true);
        } else {
            onError(mediaRecorder, i, i1);
        }
    }

    @Override
    public void onSeekComplete(MediaPlayer mediaPlayer) {
        if (this.lastState == PLAYING)
            play();

        if (onSeekListener != null)
            onSeekListener.onSeekComplete(this);
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int percent) {
        if (onBufferingUpdateListener != null)
            onBufferingUpdateListener.onBufferingUpdate(this, percent);
    }

}
