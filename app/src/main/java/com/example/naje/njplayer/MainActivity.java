package com.example.naje.njplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity  {

    ListView list;
    String[] listItems;
    Button play, next, back;
    SeekBar seek;
    Thread updateSeekBar;
    static int current_seek_position;
    private int audio_position;
    static MediaPlayer mp;
    private ArrayList audios;
    private TextView current_time;
    private boolean isPausedInCall;

    //Declare headsetSwitch variable
    private int headSwitch = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            }
        }

        list =  findViewById(R.id.listView);
        play =  findViewById(R.id.button1);
        next =  findViewById(R.id.button2);
        back =  findViewById(R.id.button3);
        seek =  findViewById(R.id.sb);
        current_time = findViewById(R.id.time);

        //Get the Telephony Manager
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        PhoneStateListener phoneState = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mp != null) {
                            isPausedInCall = true;
                            pauseMusic();
                        }    break;

                        case TelephonyManager.CALL_STATE_IDLE:
                        if (mp != null) {
                            if (isPausedInCall) {
                                playMusic();
                                isPausedInCall = false;
                            }
                        }    break;
                }
            }
        };

        //Register the Listener with the telephony manager
        if (telephonyManager != null) {
            telephonyManager.listen(phoneState, PhoneStateListener.LISTEN_CALL_STATE);
        }

        final ArrayList<File> allAudios = findAudios(Environment.getExternalStorageDirectory());

        if (allAudios.size() > 0) {
            Uri first_audio = Uri.parse(allAudios.get(1).toString());
            mp = MediaPlayer.create(getApplicationContext(), first_audio);

        } else{
            Toast.makeText(this, "You don't have audio files on your device",
                    Toast.LENGTH_LONG).show();
        }

        listItems = new String [allAudios.size()];

        for (int x = 0; x < allAudios.size() ; x++){

            listItems[x] = allAudios.get(x).getName().replace(".mp3","");
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(getApplicationContext(), R.layout.songs_list,R.id.textView, listItems);

        list.setAdapter(adapter);

        updateSeekBar = new Thread(new Runnable() {
            @Override
            public void run() {

                while (true) {
                    try {
                        sleep(1000);
                        try {
                            current_seek_position = mp.getCurrentPosition();
                        } catch (Exception e) {
                            Log.i("Wrong Using", "Please Tap Slowly");
                        }
                        seek.post(new Runnable() {
                            @Override
                            public void run() {
                                seek.setProgress(current_seek_position);
                                int seconds = (int) Math.ceil(current_seek_position / 1000);
                                showTime(seconds);}
                                });

                    } catch (InterruptedException e) {
                        Log.i("Error", e.toString());
                    }
                }

            }
        });


        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view,
                                    int position, long l) {

                audios = allAudios;
                audio_position = position;

                //Check if the music was playing and if so stop it to play the current selected song
                if(mp!=null){
                    mp.stop();
                    mp.release();
                }

                audioSpecifications(audio_position, audios);
                mp.start();

                if ( updateSeekBar.getState() == Thread.State.NEW  ) {

                    //Start the Thread to update the seekBar
                    updateSeekBar.start();
                }
            }
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                mp.seekTo(seekBar.getProgress());
            }
        });

        //Play the Music
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    playStop();
                } catch (Exception e){
                    Toast.makeText(MainActivity.this,
                            "Please select an audio", Toast.LENGTH_SHORT).show();
                }

            }
        });

        //Move to the next song
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    nextSong();
                } catch (Exception e){
                    Toast.makeText(MainActivity.this,
                            "Please select an audio", Toast.LENGTH_SHORT).show();
                }
            }
        });

        //Return to the previous song
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    previousSong();
                } catch (Exception e){
                    Toast.makeText(MainActivity.this,
                            "Please select an audio", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }


    @Override
    protected void onResume() {
        super.onResume();
        //Register headset receiver
        registerReceiver(headSetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
    }


    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(headSetReceiver);

    }

    private void playStop(){
        if (mp.isPlaying())
        {
            mp.pause();
            play.setBackgroundResource(R.drawable.b1);
        }
        else
        {
            mp.start();
            play.setBackgroundResource(R.drawable.b22);
        }
    }


    private void nextSong(){

        mp.stop();
        mp.release();
        audio_position = (audio_position + 1)% audios.size();
        audioSpecifications(audio_position, audios);
        mp.start();
        play.setBackgroundResource(R.drawable.b22);
    }


    private void previousSong(){
        mp.stop();
        mp.release();
        audio_position = (audio_position -1 < 0)? audios.size()-1 :
                audio_position - 1;
        audioSpecifications(audio_position, audios);
        mp.start();
        play.setBackgroundResource(R.drawable.b22);
    }


    private void playMusic() {
        if (!mp.isPlaying()) {
            mp.start();
            play.setBackgroundResource(R.drawable.b22);
        }
    }

    private void pauseMusic() {
        if (mp.isPlaying()) {
            mp.pause();
            play.setBackgroundResource(R.drawable.b1);
        }
    }

    ArrayList<File> findAudios(File sdCard)
    {
        ArrayList<File> all = new ArrayList<>();

        File[] files = sdCard.listFiles();

        for (File singleFile : files){
            if(singleFile.isDirectory() && !singleFile.isHidden()){
                all.addAll(findAudios(singleFile));
            }
            else {
                if (singleFile.getName().endsWith(".mp3"))
                {
                    all.add(singleFile);
                }
            }

        }
        return all;
    }

    private void showTime( int seconds ){

        int min = seconds / 60;
        int sec = seconds % 60;
        String x = String.valueOf(min);
        String y = String.valueOf(sec);

        if ((min < 1) && (sec < 10)) {
            String time = "00:0" + y;
            current_time.setText(time);
        } else if (min < 1) {
            String time = "00:" + y;
            current_time.setText(time);
        } else if (min < 10 && sec < 10) {
            String time = "0" + x + ":0" + y;
            current_time.setText(time);
        } else if (min < 10 && sec > 9) {
            String time = "0" + x + ":" + y;
            current_time.setText(time);
        } else if (min > 9 && sec < 10) {
        String time =  x + ":0" + y;
        current_time.setText(time);
        }else {
            String time = x + ":" + y;
            current_time.setText(time);
        }

    }

    private void audioSpecifications(int audio_position, ArrayList allAudio){

        Uri u = Uri.parse(allAudio.get(audio_position).toString());
        mp = MediaPlayer.create(getApplicationContext(), u);
        play.setBackgroundResource(R.drawable.b22);
        seek.setProgress(0);
        seek.setMax(mp.getDuration());
        current_seek_position = 0;
    }


    //If headset get unplugged, stop music and service
    private BroadcastReceiver headSetReceiver = new BroadcastReceiver(){
        private boolean headSetConnected = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("state")){

                // The headset was disconnected
                if (headSetConnected && intent.getIntExtra("state", 0)==0){
                    headSetConnected = false;
                    headSwitch = 0;

                // The headset was connected
                } else if(!headSetConnected && intent.getIntExtra("state", 0)== 1)
                {
                    headSetConnected = true;
                    headSwitch = 1;

                }
            }
            switch (headSwitch){
                case 0:
                    pauseMusic();
                    break;
                case 1:
                    //Don't do any thing
                    break;
            }
        }
    };

}
