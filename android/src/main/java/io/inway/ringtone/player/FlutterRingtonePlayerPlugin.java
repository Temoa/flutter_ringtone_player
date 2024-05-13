package io.inway.ringtone.player;


import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * FlutterRingtonePlayerPlugin
 */
public class FlutterRingtonePlayerPlugin implements MethodCallHandler, FlutterPlugin, AudioManager.OnAudioFocusChangeListener {
    private Context context;
    private MethodChannel methodChannel;
    private Ringtone ringtone;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        onAttachedToEngine(binding.getApplicationContext(), binding.getBinaryMessenger());
    }

    private void onAttachedToEngine(Context applicationContext, BinaryMessenger messenger) {
        this.context = applicationContext;
        RingtoneManager ringtoneManager = new RingtoneManager(context);
        ringtoneManager.setStopPreviousRingtone(true);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        methodChannel = new MethodChannel(messenger, "flutter_ringtone_player");
        methodChannel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        context = null;
        methodChannel.setMethodCallHandler(null);
        methodChannel = null;
    }


    @SuppressWarnings("ConstantConditions")
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        try {
            Uri ringtoneUri = null;
            if (call.method.equals("play")) {
                if (call.hasArgument("uri")) {
                    String uri = call.argument("uri");
                    ringtoneUri = Uri.parse(uri);
                }

                // The androidSound overrides fromAsset if exists
                if (call.hasArgument("android")) {
                    int pref = call.argument("android");
                    switch (pref) {
                        case 1:
                            ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM);
                            break;
                        case 2:
                            ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION);
                            break;
                        case 3:
                            ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE);
                            break;
                        default:
                            result.notImplemented();
                    }

                }
            } else if (call.method.equals("stop")) {
                stop();
                result.success(null);
            }

            if (ringtoneUri != null) {
                stop();

                ringtone = RingtoneManager.getRingtone(context, ringtoneUri);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.setAudioAttributes(
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                    );
                } else {
                    ringtone.setStreamType(AudioManager.STREAM_MUSIC);
                }


                if (call.hasArgument("volume")) {
                    final double volume = call.argument("volume");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.setVolume((float) volume);
                    }
                }

                if (call.hasArgument("looping")) {
                    final boolean looping = call.argument("looping");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.setLooping(looping);
                    }
                }

                if (call.hasArgument("asAlarm")) {
                    final boolean asAlarm = call.argument("asAlarm");
                    /* There's also a .setAudioAttributes method
                       that is more flexible, but .setStreamType
                       is supported in all Android versions
                       whereas .setAudioAttributes needs SDK > 21.
                       More on that at
                       https://developer.android.com/reference/android/media/Ringtone
                    */
                    if (asAlarm) {
                        ringtone.setStreamType(AudioManager.STREAM_ALARM);
                    }
                }

                requestAudioFocus();
                ringtone.play();

                result.success(null);
            }
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            result.error("Exception", e.getMessage(), null);
        }
    }

    private void stop() {
        if (ringtone != null) {
            ringtone.stop();
        }
        abandonAudioFocus();
    }

    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setWillPauseWhenDucked(true)
                    .setAcceptsDelayedFocusGain(true)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(this)
                    .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    private void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(this);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        //
    }
}
