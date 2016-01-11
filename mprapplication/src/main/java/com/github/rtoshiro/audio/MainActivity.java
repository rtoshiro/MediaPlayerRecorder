package com.github.rtoshiro.audio;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import rtoshiro.github.com.audio.MediaPlayerRecorder;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private android.widget.Button btplay;
    private android.widget.TextView txplay;
    private android.widget.Button btrecord;
    private android.widget.TextView txrecord;

    private MediaPlayerRecorder mediaPlayerRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.txrecord = (TextView) findViewById(R.id.tx_record);
        this.btrecord = (Button) findViewById(R.id.bt_record);
        this.txplay = (TextView) findViewById(R.id.tx_play);
        this.btplay = (Button) findViewById(R.id.bt_play);

        this.btplay.setOnClickListener(this);
        this.btrecord.setOnClickListener(this);

        this.mediaPlayerRecorder = new MediaPlayerRecorder();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.bt_play: {
                break;
            }
            case R.id.bt_record: {
                break;
            }
        }
    }
}
