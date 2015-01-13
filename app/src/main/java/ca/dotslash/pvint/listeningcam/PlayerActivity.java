package ca.dotslash.pvint.listeningcam;

import java.io.File;
import java.io.IOException;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.Context;

import android.graphics.PixelFormat;
import android.graphics.drawable.AnimationDrawable;
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

import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;


public class PlayerActivity extends Activity
        implements SurfaceHolder.Callback {

    Uri targetUri;

    MediaPlayer mediaPlayer;
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    MediaRecorder recorder;
    Camera mCamera;

    private int cameraSelectedNumber = 0;
    private int numCameras;
    private int activeCamera = 0;

    boolean pausing = false;
    private String videoFilePrefix = "LCam_";
    private String monitorButtonText = "Start!";
    private String monitorButtonText2 = "Stop!";
    private final String saveDir = "ListeningCam";

    private CheckBox keepAwake;
    private CheckBox recordFixedLengthSwitch;
    private long lastNoiseTime;

    private int videoWidth = 352;
    private int videoHeight = 288;

    private int vWidth = 640;
    private int vHeight = 480;
    private int videoDuration = 1000 * 60;
    private boolean isRecording = false;
    private boolean firstRun = true;

    private ProgressBar audioLevelBar;
    private SeekBar videoLengthBar;
    private EditText videoLengthEditText;
    private TextView videoLengthTextView;
    private SeekBar setAudioLevelBar;
    private TextView getAudioLevelText;
    private ImageButton switchCameraButton;

    private Context ctx;
    private Activity activity;
    private ScheduledThreadPoolExecutor sch;

    // Audio stuff
    private boolean monitoring = false;
    private TextView audioLevelText;
    private int audioMonitorDelay = 100;    // ms
    private Handler handler = new Handler();
    private int cnt = 0;
    private static final String aFileName = "/dev/null";
    private MediaRecorder mediaRecorder = null;
    private int audioThreshold = 4000;
    private int audioLevelFactor = 328;

    private AnimationDrawable microphoneAnimation;
    private ImageView microphoneImage;
    private AnimationDrawable recordAnimation;
    private ImageView recordImage;


    private void debugText(String t)
    {
        String n;
        TextView tv = (TextView) findViewById(R.id.debugTextView);
        n = (String) tv.getText();
        tv.setText(n + '\n' + t);

    }
    private void debugTextNoReturn(String t)
    {
        String n;
        TextView tv = (TextView) findViewById(R.id.debugTextView);
        n = (String) tv.getText();
        tv.setText(n + t);

    }
    // Audio monitor
    private Runnable audioMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaRecorder == null)
                return;

            int x = mediaRecorder.getMaxAmplitude();
            updateAudioMeter(x);

            // check if threshold is exceeded
            if (x > audioThreshold && monitoring)
            {
            // TODO: clean up audio recorder!!
                mediaRecorder.stop();
                isRecording = false;
                mediaRecorder.reset();
                mediaRecorder.release();

                lastNoiseTime = System.currentTimeMillis();

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

    private Runnable audioMeterRunnable = new Runnable() {
        @Override
        public void run() {
            if (recorder == null)
                return;

            int x = recorder.getMaxAmplitude();
            updateAudioMeter(x);

            handler.postDelayed(this, audioMonitorDelay);
            }
    };

    private Runnable silenceOneShotTask = new Runnable () {
        @Override
        public void run() {
            // Checking every n seconds for silence
            runOnUiThread(new Runnable() {
                public void run() {

                    int m = recorder.getMaxAmplitude();
                    updateAudioMeter(m);
                    long now = System.currentTimeMillis();

                    if ((m < audioThreshold) && ((now - lastNoiseTime)  > videoDuration)) {

                            recorder.stop();
                            isRecording = false;
                            recorder.reset();
                            recorder.release();
                            showNotRecording();

                            startAudioMonitor();

                    }
                    else
                    {
                        // restart timer to check in n seconds
                        if(m >= audioThreshold)
                            lastNoiseTime = System.currentTimeMillis();
                        //debugTextNoReturn(Long.toString(lastNoiseTime - now) + ".");
                        handler.postDelayed(this, audioMonitorDelay);
                    }

                }
            });
        }
    };
    // Create a task for one-shot execution for fixed duration video
    private Runnable oneShotTask = new Runnable(){
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

    private int openCamera()
    {
        mCamera = Camera.open(0);
        mCamera.lock();
        // Note: must be called when camera is locked
        Camera.Parameters cp = mCamera.getParameters();

        mCamera.unlock();
        numCameras = mCamera.getNumberOfCameras();

        //List<Camera.Size> localSizes = mCamera.getParameters().getSupportedPreviewSizes();
        //mSupportedPreviewSizes = localSizes;
        //requestLayout();


        int w = cp.getPreviewSize().width;
        int h = cp.getPreviewSize().height;
        //debugText(Integer.toString(w) + "x" + Integer.toString(h));

        int r = w/h;
        vWidth = surfaceView.getWidth();
        vHeight = vWidth / r;

        if (vHeight > surfaceView.getHeight())
        {
            vHeight = surfaceView.getHeight();
            vWidth = vHeight * r;
        }

        if(w>vWidth || h>vHeight)
        {
            cp.setPreviewSize(vWidth,vHeight);
        }


        //m.setText(Integer.toString(vWidth) + "x" + Integer.toString(vHeight));
        surfaceHolder.setFixedSize(vWidth, vHeight);

        // Important: Call startPreview() to start updating the preview
        // surface. Preview must be started before you can take a picture.
        debugText(Integer.toString(vWidth) + "x" + Integer.toString(vHeight));

/*
        try {
            mCamera.setPreviewDisplay(surfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
*/

        try {
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }
    private void prepareSurface()
    {
        getWindow().setFormat(PixelFormat.UNKNOWN);
        surfaceView = (SurfaceView) findViewById(R.id.surfaceview);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setFixedSize(videoWidth, videoHeight);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mediaPlayer = new MediaPlayer();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR | ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.main_layout);

        keepAwake = (CheckBox) findViewById(R.id.keepAwakeCheckBox);

        if (keepAwake.isChecked())
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        keepAwake.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
                                                       @Override
                                                       public void onCheckedChanged(CompoundButton buttonView,
                                                                                    boolean isChecked) {
                                                           if(isChecked)
                                                               getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                                           else
                                                               getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                                                       }
                                                   }
        );

        ctx = this.getParent();
        activity = this.activity;

        TextView countText = new TextView(this);

        audioLevelText = (TextView) findViewById(R.id.integerTextView);
        audioLevelBar = (ProgressBar) findViewById(R.id.progressBar);
        setAudioLevelBar = (SeekBar) findViewById(R.id.seekBar);
        audioLevelText.setText(Integer.toString(audioThreshold));

        recordFixedLengthSwitch = (CheckBox) findViewById(R.id.recordUntilSilentCheckBox);


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

        //videoLengthTextView = (TextView) findViewById(R.id.videoLengthIntegerTextView);
        //videoLengthBar = (SeekBar) findViewById(R.id.videoLengthSeekbar);

        //videoLengthBar.setProgress(videoDuration / 1000);
        //videoLengthTextView.setText(Integer.toString(videoDuration / 1000));

        videoLengthEditText = (EditText) findViewById(R.id.videoLengthEditText);
        videoLengthEditText.setText(Integer.toString(videoDuration / 1000));

        Switch lockOrientationSwitch = (Switch) findViewById(R.id.lockOrientationSwitch);
        Switch changeCameraSwitch = (Switch) findViewById(R.id.changeCameraSwitch);

        //switchCameraButton = (ImageButton) findViewById(R.id.switchCameraButton);

        //startAudioMonitor();

        // Prepare the recording animations
        microphoneImage = (ImageView) findViewById(R.id.microphoneAnimationImageView);
        microphoneImage.setBackgroundResource(R.drawable.microphoneidle);
        recordImage = (ImageView) findViewById(R.id.recordAnimationImageView);
        recordImage.setBackgroundResource(R.drawable.cameraidle);


//        recordImage.setBackgroundResource(R.drawable.recording_animation);
//        recordAnimation = (AnimationDrawable) recordImage.getBackground();
//
//        recordAnimation.start();



        // prepare recorder

        prepareSurface();
        openCamera();

        // FIXME Not working!!  startCameraPreview();

        /*try {
            mCamera.setPreviewDisplay(surfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        //initRecorder();

        // start preview
        //recordVid();
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


        /*videoLengthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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
        });*/
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

/*        switchCameraButton.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View arg0) {
                // TODO Switching Camera may need some work when recording!
                if (numCameras > 0)
                {
                    activeCamera = 1 - activeCamera;
//                    mCamera.open(activeCamera);
                }

            }
        });*/

        buttonMonitorAudio.setOnClickListener(new Button.OnClickListener(){

            @Override
            public void onClick(View arg0) {
                Button b = (Button) findViewById(R.id.buttonMonitorAudio);
                if (b.getText() == monitorButtonText) {
                    //startAudioMonitor();
                    b.setText(monitorButtonText2);
                    monitoring = true;

                    // show the flashing icon

                    microphoneImage.setBackgroundResource(R.drawable.microphone_animation);
                    microphoneAnimation = (AnimationDrawable) microphoneImage.getBackground();

                    microphoneAnimation.start();

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

        videoDuration = 1000 * Integer.parseInt(videoLengthEditText.getText().toString());

        if (videoDuration < 10000)
            videoDuration = 10000;

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
        surfaceHolder.setFixedSize(videoWidth, videoHeight);  // FIXME
        recorder.setOutputFile(tempFile.getPath());
        recorder.setVideoFrameRate(25);
        recorder.setVideoSize(videoWidth, videoHeight);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        recorder.setPreviewDisplay(surfaceHolder.getSurface());


        if (firstRun && false) // FIXME this isn't working yet
        {
            firstRun = false;
            try {
                recorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }

            recorder.start();
            //recorder.stop();

            return;
        }
        if (!recordFixedLengthSwitch.isChecked())
        {
            // monitor for silence of videoDuration seconds then stop recording
            lastNoiseTime = System.currentTimeMillis();
        }
        else {
            recorder.setMaxDuration(videoDuration * 2);
        }

        try {
            recorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        sch = (ScheduledThreadPoolExecutor)
                Executors.newScheduledThreadPool(1);

        if (!recordFixedLengthSwitch.isChecked()) {
            ScheduledFuture<?> oneShotFuture =
                    sch.schedule(silenceOneShotTask, audioMonitorDelay, TimeUnit.MILLISECONDS);
        }
        else {
            ScheduledFuture<?> oneShotFuture =
                    sch.schedule(oneShotTask, videoDuration / 1000, TimeUnit.SECONDS);
        }

        ImageView recordImage = (ImageView) findViewById(R.id.recordAnimationImageView);
        recordImage.setBackgroundResource(R.drawable.recording_animation);
        recordAnimation = (AnimationDrawable) recordImage.getBackground();

        recordAnimation.start();

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
        //updateAudioMeter(cnt);


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
        // deprecated
//        TextView t = (TextView) findViewById(R.id.recordingTextView);
//        t.setTextColor(Color.RED);


    }
    private void showNotRecording() {
//        TextView t = (TextView) findViewById(R.id.recordingTextView);
//        t.setTextColor(Color.DKGRAY);

        if(recordAnimation != null)
            recordAnimation.stop();
        ImageView recordImage = (ImageView) findViewById(R.id.recordAnimationImageView);
        recordImage.setBackgroundResource(R.drawable.cameraidle);

        // stop the flashing icon
        if (microphoneAnimation != null) {
            microphoneAnimation.stop(); // FIXME need to reset to grey
        }

        ImageView microphoneImage = (ImageView) findViewById(R.id.microphoneAnimationImageView);
        microphoneImage.setBackgroundResource(R.drawable.microphoneidle);
    }
}