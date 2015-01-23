package ca.dotslash.pvint.listeningcam;

import java.io.File;
import java.io.IOException;

import java.util.Iterator;
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
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
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
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.w3c.dom.Text;


public class MainActivity extends Activity implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    private MediaRecorder recorder; // main recorder for video


    private Camera mCamera;
    private static final int previewFrameRate = 25;

    private CheckBox keepAwake;

    private int audioThreshold = 4000;  // Default threshold for recording
    private int audioLevelFactor = 328; // Factor to convert input level to 0-100
    private boolean monitoring = false;
    private boolean isRecording = false;
    private double lastNoiseTime;
    private int debugCnt = 0;

    private ScheduledThreadPoolExecutor sch;
    private int audioMonitorDelay = 100;    // ms
    private Handler handler = new Handler();

    private int videoDuration = 1000 * 60;
    private int vWidth, vHeight, sWidth, sHeight;
    private String currentFile;

    private String videoFilePrefix = "LCam_";
    private String monitorButtonText = "Start!";
    private String monitorButtonText2 = "Stop!";
    private final String saveDir = "ListeningCam";
    private static final String aFileName = "/dev/null";

    // input/interface widgets
    private TextView audioLevelText;
    private ProgressBar audioLevelBar;
    private EditText videoLengthEditText;
    private CheckBox recordFixedLengthSwitch;
    private AnimationDrawable microphoneAnimation;
    private ImageView microphoneImage;
    private AnimationDrawable recordAnimation;
    private ImageView recordImage;
    private SeekBar setAudioLevelBar;
    private TextView getAudioLevelText;
    private Button buttonMonitorAudio;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_layout);

        keepAwake = (CheckBox) findViewById(R.id.keepAwakeCheckBox);

        if (keepAwake.isChecked())
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // TODO KeepAwake should only be active when monitoring or recording
        // Also, can I turn the screen off and keep recording? Would need to run as service I think. (Or just dim?)
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

        initInterface();
        initSurface();
        // Camera gets started in onSurfaceCreate()
        startAudioMonitor();


        // Set up listeners
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
                    b.setBackgroundColor(0xffff0000);

                    // show the flashing icon

                    microphoneImage.setBackgroundResource(R.drawable.microphone_animation);
                    microphoneAnimation = (AnimationDrawable) microphoneImage.getBackground();

                    microphoneAnimation.start();

                }
                else {
                    b.setText(monitorButtonText);
                    monitoring = false;
                    showNotRecording();
                    b.setBackgroundColor(0xff00ff00);

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




        // end onCreate()
    }
    @Override
    public void surfaceChanged(SurfaceHolder arg0, int format, int width, int height) {
        surfaceHolder.setFixedSize(width, height);
        initCamera(width, height);
        sWidth = width;
        sHeight = height;
        debugText("SurfaceChanged: " + width + "x" + height);

    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {


    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        // TODO Auto-generated method stub

    }
    private boolean initSurface()
    {
        // Prepare the OpenGL Surface
        getWindow().setFormat(PixelFormat.UNKNOWN);
        surfaceView = (SurfaceView) findViewById(R.id.surfaceview);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setFixedSize(surfaceHolder.getSurfaceFrame().width(), surfaceHolder.getSurfaceFrame().height());
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        return true;
    }

    // overloaded method - use 0 as w & h if not given
    private boolean initCamera()
    {
        boolean r = initCamera(0,0);
        return r;
    }
    private boolean initCamera(int width, int height)
    {

        // Prepare the camera
        mCamera = Camera.open();
        if (mCamera == null)
            debugText("Camera is null!");

        mCamera.lock();
        Camera.Parameters cp = mCamera.getParameters();

        cp.setPreviewFpsRange(15, 30);  // FIXME - hardwired

        List<Camera.Size> previewSizes = cp.getSupportedPreviewSizes();

        Camera.Size s = getOptimalPreviewSize(previewSizes, width, height);



        vWidth = s.width;
        vHeight = s.height;

/*
        // FIXME HARDWIRE TEST
        vWidth = 640;
        vHeight = 480;
*/

        cp.setPreviewSize(vWidth,vHeight);

        debugText("InitCamera: " + Integer.toString(vWidth) + "x" + Integer.toString(vHeight));
        mCamera.setParameters(cp);


        try {
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }



        return true;
    }

    private void initRecorder()
    {
        recorder = new MediaRecorder();
        debugText("Initializing recorder... ");
        mCamera.unlock();
        debugText("Camera unlocked");
        recorder.setCamera(mCamera);
        debugText("Camera Set");
        recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
debugText("1");
        DateFormat df = new DateFormat();
        currentFile = videoFilePrefix  + df.format("yyyyMMdd_kkmmss", new java.util.Date()) + ".mp4";

        // Make the directory if not there
        Boolean nf = new File(Environment.getExternalStorageDirectory(), "/" + saveDir).mkdir();
        File tempFile = new File(Environment.getExternalStorageDirectory(), "/" + saveDir + "/" + currentFile);

        /*int r = vWidth/vHeight;
        vWidth = surfaceView.getWidth();
        vHeight = vWidth / r;

        if (vHeight > surfaceView.getHeight())
        {
            vHeight = surfaceView.getHeight();
            vWidth = vHeight * r;
        }
*/
        //m.setText(Integer.toString(vWidth) + "x" + Integer.toString(vHeight));
        //surfaceHolder.setFixedSize(vWidth, vHeight);  // FIXME
        recorder.setOutputFile(tempFile.getPath());
        recorder.setVideoFrameRate(25);
        debugText("Setting videosize to " + Integer.toString(vWidth) + "x" + Integer.toString(vHeight));
        recorder.setVideoSize(vWidth,vHeight);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        recorder.setPreviewDisplay(surfaceHolder.getSurface());
        debugText("Done");
    }
    public static Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int screenWidth, int screenHeight) {
        double epsilon = 0.17;
        double aspectRatio = ((double)screenWidth)/screenHeight;
        Camera.Size optimalSize = null;
        for (Iterator<Camera.Size> iterator = sizes.iterator(); iterator.hasNext();) {
            Camera.Size currSize =  iterator.next();
            double curAspectRatio = ((double)currSize.width)/currSize.height;
            //do the aspect ratios equal?
            if ( Math.abs( aspectRatio - curAspectRatio ) < epsilon ) {
                //they do
                if(optimalSize!=null) {
                    //is the current size smaller than the one before
                    if(optimalSize.height>currSize.height && optimalSize.width>currSize.width) {
                        optimalSize = currSize;
                    }
                } else {
                    optimalSize = currSize;
                }
            }
            else
            {
                if(optimalSize!=null) {
                    if (optimalSize.height > currSize.height || optimalSize.width > currSize.width)
                    {
                        optimalSize = currSize;
                    }
                }
                else
                    optimalSize = currSize;
            }
        }
        if(optimalSize == null) {
            //did not find a size with the correct aspect ratio.. let's choose the smallest instead
            for (Iterator<Camera.Size> iterator = sizes.iterator(); iterator.hasNext();) {
                Camera.Size currSize =  iterator.next();
                if(optimalSize!=null) {
                    //is the current size smaller than the one before
                    if(optimalSize.height>currSize.height && optimalSize.width>currSize.width) {
                        optimalSize = currSize;
                    } else {
                        optimalSize = currSize;
                    }
                }else {
                    optimalSize = currSize;
                }

            }
        }
        return optimalSize;
    }

    private void initInterface()
    {
        // Init all of the widgets, buttons etc TODO! More to add!
        audioLevelText = (TextView) findViewById(R.id.audioLevelLabel);
        audioLevelBar = (ProgressBar) findViewById(R.id.progressBar);
        videoLengthEditText = (EditText) findViewById(R.id.videoLengthEditText);
        recordFixedLengthSwitch = (CheckBox) findViewById(R.id.recordUntilSilentCheckBox);

        // Prepare the recording animations
        microphoneImage = (ImageView) findViewById(R.id.microphoneAnimationImageView);
        microphoneImage.setBackgroundResource(R.drawable.microphoneidle);
        recordImage = (ImageView) findViewById(R.id.recordAnimationImageView);
        recordImage.setBackgroundResource(R.drawable.cameraidle);

        buttonMonitorAudio = (Button) findViewById(R.id.buttonMonitorAudio);
        buttonMonitorAudio.setText(monitorButtonText);

        setAudioLevelBar = (SeekBar) findViewById(R.id.seekBar);
        setAudioLevelBar.setProgress(audioThreshold / audioLevelFactor);
        getAudioLevelText = (TextView) findViewById(R.id.audioThresholdTextView);
        getAudioLevelText.setText(Integer.toString(audioThreshold / audioLevelFactor));
    }

    private void debugText()
    {
        String t = Integer.toString(debugCnt);
        debugCnt++;
        debugText(t);
    }
    private void debugText(String t)
    {
        String n;
        TextView tv = (TextView) findViewById(R.id.debugTextView);
        n = (String) tv.getText();
        tv.setText(n + '\n' + t);
        Log.d("LCam", t);

    }
    private void debugTextNoReturn(String t)
    {
        String n;
        TextView tv = (TextView) findViewById(R.id.debugTextView);
        n = (String) tv.getText();
        tv.setText(n + t);

    }

    protected int updateAudioMeter(int a)
    {
        int b = a / audioLevelFactor;
        audioLevelText.setText(Integer.toString(b));
        audioLevelBar.setProgress(b);
        return 0;
    }


    private void recordVid()
    {
        debugText("Starting recordVid()");
        initRecorder();

        if (!recordFixedLengthSwitch.isChecked())
        {
            // monitor for silence of videoDuration seconds then stop recording
            lastNoiseTime = System.currentTimeMillis();
        }
        else {
            recorder.setMaxDuration(videoDuration * 2);
        }
        debugText("About to prepare()");
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

        debugText("Recording " + currentFile);
        isRecording = true;

        recorder.start();
        // show the recording indicator
        // FIXME Deprecated?? showRecording();
        //debugText("After record.start()");
    }

    private void startAudioMonitor()
    {
        if (recorder == null)
            recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(aFileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        try {
            recorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        recorder.start();
        //updateAudioMeter(cnt);


        handler.postDelayed(audioMonitorRunnable, audioMonitorDelay);
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
    private void stopAudio()
    {
        if(recorder != null)
        {
            recorder = null;
            mCamera.release();
        }
        Button b = (Button) findViewById(R.id.buttonMonitorAudio);
        if (b.getText() == monitorButtonText2)
            b.setText(monitorButtonText);

        monitoring = false;

        showNotRecording();
    }

    // Runnables
    // Audio monitor
    private Runnable audioMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            if (recorder == null)
                return;

            int x = recorder.getMaxAmplitude();
            updateAudioMeter(x);

            // check if threshold is exceeded
            if (x > audioThreshold && monitoring)
            {
                // TODO: clean up audio recorder!!
                recorder.stop();
                isRecording = false;
                //recorder.reset();
                //recorder.release();

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


}