package ca.dotslash.pvint.listeningcam;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.media.AudioManager;

public class PlayerActivity extends Activity
        implements SurfaceHolder.Callback {

    Uri targetUri;

    MediaPlayer mediaPlayer;
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    MediaRecorder recorder;
    Camera mCamera;

    private int cameraSelectedNumber = 0;
    boolean pausing = false;
    private String videoFilePrefix = "LCam_";
    private String monitorButtonText = "Monitor audio";
    private String monitorButtonText2 = "Stop monitoring";
    private final String saveDir = "ListeningCam";

    private int vWidth = 640;
    private int vHeight = 480;
    private int videoDuration = 1000 * 90;
    private boolean isRecording = false;

    private ProgressBar audioLevelBar;
    private SeekBar videoLengthBar;
    private TextView videoLengthTextView;
    private SeekBar setAudioLevelBar;
    private TextView getAudioLevelText;

    private Context ctx;
    private Activity activity;
    private ScheduledThreadPoolExecutor sch;

    // Audio stuff
    private boolean monitoring = false;
    private TextView audioLevelText;
    private int audioMonitorDelay = 250;    // ms
    private Handler handler = new Handler();
    private int cnt = 0;
    private static final String aFileName = "/dev/null";
    private MediaRecorder mediaRecorder = null;
    private int audioThreshold = 2000;
    private int audioLevelFactor = 328;

    private void debugText(String t)
    {
        String n;
        TextView tv = (TextView) findViewById(R.id.debugTextView);
        n = (String) tv.getText();
        tv.setText(n + '\n' + t);

    }
    // Audio monitor
    private Runnable audioMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            //int x = Integer.parseInt(audioLevelText.getText().toString());
            //x++;

            if (mediaRecorder == null)
                return;

            int x = mediaRecorder.getMaxAmplitude();
            updateAudioMeter(x);

            // check if threshold is exceeded
            if (x > audioThreshold && monitoring)
            {
                // start recording video
                //startVideoRecord();
                //camcorderView.startRecording();
// TODO: clean up audio recorder!!
                mediaRecorder.stop();
                isRecording = false;
                mediaRecorder.reset();
                mediaRecorder.release();

                // video playing test
                recordVid();
                /*monitoring = false;
                Button buttonMonitorAudio = (Button) findViewById(R.id.buttonMonitorAudio);
                buttonMonitorAudio.setText(monitorButtonText);
                handler.postDelayed(this, audioMonitorDelay);*/
            }
            else {
                handler.postDelayed(this, audioMonitorDelay);
            }
        }
    };
    @Override
    protected void onPause() {
        super.onPause();  // Always call the superclass method first
        // release the camera TODO
        stopRecording();
        stopAudio();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        prepareRecorder();
    }

    private void stopRecording()
    {
        if(recorder != null && isRecording)
        {
            recorder.stop();
            recorder.release();
            isRecording = false;
            showNotRecording();
        }
    }

    private void prepareRecorder()
    {
/*        mediaPlayer = new MediaPlayer();

        // prepare recorder
        if(mCamera != null)
            mCamera = Camera.open(0);


        mCamera.unlock();

        startAudioMonitor();*/
    }
    private void stopAudio()
    {
        if(mediaRecorder != null)
        {
            mediaRecorder = null;
            mCamera.release();
        }
        Button b = (Button) findViewById(R.id.buttonMonitorAudio);
        if (b.getText() == monitorButtonText2)
            b.setText(monitorButtonText);

        monitoring = false;

        showNotRecording();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR | ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.main_layout);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ctx = this.getParent();
        activity = this.activity;

        TextView countText = new TextView(this);

        audioLevelText = (TextView) findViewById(R.id.integerTextView);
        audioLevelBar = (ProgressBar) findViewById(R.id.progressBar);
        setAudioLevelBar = (SeekBar) findViewById(R.id.seekBar);
        audioLevelText.setText(Integer.toString(audioThreshold));



/*  DEPRECATED
        TextView mediaUri = (TextView)findViewById(R.id.mediauri);
        targetUri = this.getIntent().getData();
        mediaUri.setText(targetUri.toString());
*/

        Button buttonPlayVideo = (Button) findViewById(R.id.playvideoplayer);
        Button buttonPauseVideo = (Button) findViewById(R.id.pausevideoplayer);
        Button buttonPreview = (Button) findViewById(R.id.previewButton);
        Button buttonStopPreview = (Button) findViewById(R.id.stopPreviewButton);
        Button buttonMonitorAudio = (Button) findViewById(R.id.buttonMonitorAudio);
        buttonMonitorAudio.setText(monitorButtonText);
        getAudioLevelText = (TextView) findViewById(R.id.audioThresholdTextView);
        getAudioLevelText.setText(Integer.toString(audioThreshold / audioLevelFactor));
        setAudioLevelBar.setProgress(audioThreshold / audioLevelFactor);

        videoLengthTextView = (TextView) findViewById(R.id.videoLengthIntegerTextView);
        videoLengthBar = (SeekBar) findViewById(R.id.videoLengthSeekbar);

        videoLengthBar.setProgress(videoDuration / 1000);
        videoLengthTextView.setText(Integer.toString(videoDuration / 1000));

        Switch lockOrientationSwitch = (Switch) findViewById(R.id.lockOrientationSwitch);
        Switch changeCameraSwitch = (Switch) findViewById(R.id.changeCameraSwitch);

        //startAudioMonitor();

        getWindow().setFormat(PixelFormat.UNKNOWN);
        surfaceView = (SurfaceView) findViewById(R.id.surfaceview);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setFixedSize(176, 144);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mediaPlayer = new MediaPlayer();

        // prepare recorder
        mCamera = Camera.open(0);

        // FIXME Not working!!  startCameraPreview();

        /*try {
            mCamera.setPreviewDisplay(surfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        //initRecorder();
        mCamera.unlock();

        startAudioMonitor();


/*
        changeCameraSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cameraSelectedNumber = (isChecked) ? 1 : 0;
                // FIXME need to properly re-init camera
                */
/*mCamera.release();
                mCamera.open(cameraSelectedNumber);
                mCamera.unlock();
*//*


            }
        });
*/

/*        lockOrientationSwitch.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (isChecked)
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                else
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            }

        });*/

        videoLengthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                videoDuration = seekBar.getProgress() * 1000;
                videoLengthTextView.setText(Integer.toString(seekBar.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        setAudioLevelBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            audioThreshold = seekBar.getProgress() * audioLevelFactor;
                            getAudioLevelText.setText(Integer.toString(seekBar.getProgress()));
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {

                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {

                        }
                    });

        buttonMonitorAudio.setOnClickListener(new Button.OnClickListener(){

            @Override
            public void onClick(View arg0) {
                Button b = (Button) findViewById(R.id.buttonMonitorAudio);
                if (b.getText() == monitorButtonText) {
                    //startAudioMonitor();
                    b.setText(monitorButtonText2);
                    monitoring = true;

                }
                else {
                    b.setText(monitorButtonText);
                    monitoring = false;
                    showNotRecording();

                    if (recorder != null && isRecording)
                    {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                //debugText("In oneshot runnable");
                                try {
                                    Thread.sleep(3);    // why ?
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                recorder.stop();
                                isRecording = false;
                                recorder.reset();
                                recorder.release();

                                //debugText("After record.stop");
                                startAudioMonitor();
                            }
                        });
                    }
                }
                    //stopAudioMonitor();

            }});


    }

    private void startCameraPreview()
    {
        Camera.Parameters p = mCamera.getParameters();
        p.setPreviewFpsRange(15,25);
        p.setPreviewSize(vWidth,vHeight);
        mCamera.setParameters(p);
        try {
            mCamera.setPreviewDisplay(surfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            recorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initRecorder()
    {
        recorder = new MediaRecorder();
        mCamera.lock();
        // Note: must be called when camera is locked
        Camera.Parameters cp = mCamera.getParameters();

        mCamera.unlock();
        int numCams = mCamera.getNumberOfCameras();
        recorder.setCamera(mCamera);
        recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

        DateFormat df = new DateFormat();
        String f = videoFilePrefix  + df.format("yyyyMMdd_kkmmss", new java.util.Date()) + ".mp4";


        // Make the directory if not there
        Boolean nf = new File(Environment.getExternalStorageDirectory(), "/" + saveDir).mkdir();
        File tempFile = new File(Environment.getExternalStorageDirectory(), "/" + saveDir + "/" + f);

        // TESTING
        TextView m = (TextView) findViewById(R.id.mediauri);
        int w = cp.getPreviewSize().width;
        int h = cp.getPreviewSize().height;

        int r = w/h;
        vWidth = surfaceView.getWidth();
        vHeight = vWidth / r;

        if (vHeight > surfaceView.getHeight())
        {
            vHeight = surfaceView.getHeight();
            vWidth = vHeight * r;
        }

        //m.setText(Integer.toString(vWidth) + "x" + Integer.toString(vHeight));
        surfaceHolder.setFixedSize(vWidth, vHeight);
        recorder.setOutputFile(tempFile.getPath());
        recorder.setVideoFrameRate(25);
        //recorder.setVideoSize(vWidth, vHeight);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        recorder.setPreviewDisplay(surfaceHolder.getSurface());

        recorder.setMaxDuration(videoDuration * 2);

        try {
            recorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void recordVid()
    {
        recorder = new MediaRecorder();

        mCamera.lock();

        // Note: must be called when camera is locked
        Camera.Parameters cp = mCamera.getParameters();

        mCamera.unlock();

        int numCams = mCamera.getNumberOfCameras();
        //debugText(Integer.toString(numCams) + " cameras");

        recorder.setCamera(mCamera);
        recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

        //SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
        DateFormat df = new DateFormat();
        String f = videoFilePrefix  + df.format("yyyyMMdd_kkmmss", new java.util.Date()) + ".mp4";

        // Make the directory if not there
        Boolean nf = new File(Environment.getExternalStorageDirectory(), "/" + saveDir).mkdir();
        File tempFile = new File(Environment.getExternalStorageDirectory(), "/" + saveDir + "/" + f);

        // TESTING
        TextView m = (TextView) findViewById(R.id.mediauri);
        int w = cp.getPreviewSize().width;
        int h = cp.getPreviewSize().height;

        int r = w/h;
        vWidth = surfaceView.getWidth();
        vHeight = vWidth / r;

        if (vHeight > surfaceView.getHeight())
        {
            vHeight = surfaceView.getHeight();
            vWidth = vHeight * r;
        }

        //m.setText(Integer.toString(vWidth) + "x" + Integer.toString(vHeight));
        surfaceHolder.setFixedSize(vWidth, vHeight);
        recorder.setOutputFile(tempFile.getPath());
        recorder.setVideoFrameRate(25);
        //recorder.setVideoSize(vWidth, vHeight);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        recorder.setPreviewDisplay(surfaceHolder.getSurface());

        recorder.setMaxDuration(videoDuration * 2);

        try {
            recorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        sch = (ScheduledThreadPoolExecutor)
                Executors.newScheduledThreadPool(1);

        // Create a task for one-shot execution using schedule()
        Runnable oneShotTask = new Runnable(){
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    public void run() {
                    //debugText("In oneshot runnable");
                    try {
                        Thread.sleep(3);    // why ?
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    recorder.stop();
                    isRecording = false;
                    recorder.reset();
                    recorder.release();

                    showNotRecording();

                    //debugText("After record.stop");
                    startAudioMonitor();
                    }
                });

            }
        };

        ScheduledFuture<?> oneShotFuture =
                sch.schedule(oneShotTask, videoDuration / 1000, TimeUnit.SECONDS);
        debugText("Recording " + tempFile.getPath());
        isRecording = true;
        recorder.start();
        // show the recording indicator
        showRecording();
        //debugText("After record.start()");
    }
    @Override
    protected void onDestroy() {
        if (sch != null)
            sch.shutdownNow();
        if (recorder != null) {
            try {
                recorder.stop();
                isRecording = false;
                recorder.reset();
                recorder.release();

            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }

        mediaPlayer.release();
        mCamera.release();
        super.onDestroy();

    }

    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
        // TODO Auto-generated method stub

    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        // TODO Auto-generated method stub

    }
    protected int updateAudioMeter(int a)
    {
        int b = a / audioLevelFactor;
        audioLevelText.setText(Integer.toString(b));
        audioLevelBar.setProgress(b);
        return 0;
    }

    private void startAudioMonitor()
    {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(aFileName);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mediaRecorder.start();
        updateAudioMeter(cnt);

        handler.postDelayed(audioMonitorRunnable, audioMonitorDelay);
    }
    private void stopAudioMonitor()
    {
        if(recorder != null)
        {
            recorder.stop();
            isRecording = false;
            recorder.reset();
            recorder.release();
            showNotRecording();
        }
        if(mediaPlayer != null) {
            mediaPlayer.reset();
            mediaPlayer.release();
        }
    }

    private void showRecording()
    {
        TextView t = (TextView) findViewById(R.id.recordingTextView);

        t.setTextColor(Color.RED);
    }
    private void showNotRecording() {
        TextView t = (TextView) findViewById(R.id.recordingTextView);
        t.setTextColor(Color.DKGRAY);
    }
}