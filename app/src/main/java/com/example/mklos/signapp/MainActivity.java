package com.example.mklos.signapp;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View.OnTouchListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.telephony.SmsManager;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.LinkedList;
import java.util.List;


public class MainActivity extends Activity implements OnTouchListener, CvCameraViewListener2, View.OnClickListener {


    //Wartości związane z pozycją ręki
    private static final String TAG                     = "HandPose::MainActivity";
    public static final int JAVA_DETECTOR               = 0;
    public static final int NATIVE_DETECTOR             = 1;
    private static final int CAMERA_PERMISSIONS_REQUEST = 1;


    //Wartości związane z wykluczaniem ręki z tła.
    private Mat  mRgba;
    private Mat  mGray;
    private Mat mIntermediateMat;
    public boolean menu = true;
    public String znakPalców;

    private int mDetectorType = JAVA_DETECTOR;


    private CustomSufaceView   mOpenCvCameraView;
    private List<Size> mResolutionList;


    // Kalibracje treshholdową ustalamy ręcznie, ustawienie progu jest zależne od tła. Jedno sztywne ustawienie nie spełnia rezultatów

    private SeekBar minTresholdSeekbar      = null;
    private SeekBar maxTresholdSeekbar      = null;
    private TextView minTresholdSeekbarText = null;
    private TextView liczbaPalcówText = null;



    private Button save    = null;
    private Button emo    = null;
    private TextView text22    = null;
    private TextView text33    = null;
    private TextView text44    = null;
    private TextView text55    = null;
    private TextView text66    = null;
    double iThreshold = 0;

    private Scalar mBlobColorHsv;
    private Scalar mBlobColorRgba;
    private bloby mDetector;
    private Mat mSpectrum;


    //Informacja czy użytkownik oznaczył rękę na ekranie.
    private boolean wybranoObiekt = false;

    private Size SPECTRUM_SIZE;
    private Scalar CONTOUR_COLOR;
    private Scalar CONTOUR_COLOR_WHITE;



    //Wiadomość oraz podany numer użytkownika
    private String message;
    private String phone_number;

    final Handler mHandler = new Handler();
    int liczbaPalców = 0;




    final Runnable nowaLiczbapalców = new Runnable() {
        public void run() {
            updateNumberOfFingers();
        }
    };


    //Wczytywanie
    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                    // 640x480
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }


    // Wysyłanie wiadomości na podany numer

    public void sendMessage1(View view)
    {
        Button start = (Button)findViewById(R.id.buttonStart);
        ImageView logo = (ImageView)findViewById(R.id.logo);
        RelativeLayout back = (RelativeLayout)findViewById(R.id.background);
        EditText txtphoneNo = (EditText)findViewById(R.id.number);

        phone_number = txtphoneNo.getText().toString();

        ViewGroup layout = (ViewGroup) start.getParent();
        if(null!=layout) //Zabezpiecza przed błędami
            layout.removeView(start);

        layout = (ViewGroup) logo.getParent();
        if(null!=layout) //Zabezpiecza przed błędami
            layout.removeView(logo);

        layout = (ViewGroup) back.getParent();
        if(null!=layout) //Zabezpiecza przed błędami
            layout.removeView(back);

    }

    public int kk=0;
    public void sendMessage2(View view)
    {
        emo = (Button) findViewById(R.id.emotikony);
        kk+=1;
        if(kk > 2)
            kk=0;
    }

    public void sendMessage3(View view)
    {
        sendSMS();
    }


    //Samo wysyłanie wiadomości
    protected void sendSMS() {
        Log.i("Send SMS", "");

        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phone_number, null, message, null, null);
            Toast.makeText(getApplicationContext(), "SMS sent.", Toast.LENGTH_LONG).show();
        }

        catch (Exception e) {
            Toast.makeText(getApplicationContext(), "SMS faild, please try again.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {


        getPermissionToReadCamera();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }

        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        setContentView(R.layout.main_surface_view);
        //Obraz z kamery
        mOpenCvCameraView = (CustomSufaceView) findViewById(R.id.main_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);



        minTresholdSeekbarText = (TextView) findViewById(R.id.textView3);


        liczbaPalcówText = (TextView) findViewById(R.id.liczbaPalców);
        text22 = (TextView) findViewById(R.id.text2);
        text33 = (TextView) findViewById(R.id.text3);
        text44 = (TextView) findViewById(R.id.text4);
        text55 = (TextView) findViewById(R.id.text5);
        text66 = (TextView) findViewById(R.id.text6);
        minTresholdSeekbar = (SeekBar)findViewById(R.id.seekBar1);
        minTresholdSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            int progressChanged = 0;

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
                progressChanged = progress;
                minTresholdSeekbarText.setText(String.valueOf(progressChanged));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                minTresholdSeekbarText.setText(String.valueOf(progressChanged));
            }
        });
        minTresholdSeekbar.setProgress(8700);
        save = (Button) findViewById(R.id.buttonSave);
        save.setOnClickListener(this);


    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null){
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
        mIntermediateMat = new Mat();


        Camera.Size resolution = mOpenCvCameraView.getResolution();
        String caption = "Resolution "+ Integer.valueOf(resolution.width).toString() + "x" + Integer.valueOf(resolution.height).toString();
        Toast.makeText(this, caption, Toast.LENGTH_SHORT).show();

        Camera.Parameters cParams = mOpenCvCameraView.getParameters();
        cParams.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
        mOpenCvCameraView.setParameters(cParams);
        Toast.makeText(this, "Focus mode : "+cParams.getFocusMode(), Toast.LENGTH_SHORT).show();





        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector = new bloby();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
        CONTOUR_COLOR_WHITE = new Scalar(255,255,255,255);

    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;


        //Pobiera wspólrzędne obiektu wyznaczonego przez urzytkownika

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Punkty wyznaczone: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>5) ? x-5 : 0;
        touchedRect.y = (y>5) ? y-5 : 0;

        touchedRect.width = (x+5 < cols) ? x + 5 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+5 < rows) ? y + 5 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        //  Program liczy śreni kolor obiektu by póżniej uwzględnić go jako blob na obrazie i wyznaczyć jego krawędzie
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        mDetector.setHsvColor(mBlobColorHsv);

        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        wybranoObiekt = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return false; // don't need subsequent touch events
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        iThreshold = minTresholdSeekbar.getProgress();

        //Imgproc.blur(mRgba, mRgba, new Size(5,5));
        Imgproc.GaussianBlur(mRgba, mRgba, new org.opencv.core.Size(3, 3), 1, 1);
        //Imgproc.medianBlur(mRgba, mRgba, 3);

        if (!wybranoObiekt) return mRgba;

        List<MatOfPoint> contours = mDetector.getContours();
        mDetector.process(mRgba);

        Log.d(TAG, "Contours count: " + contours.size());

        if (contours.size() <= 0) {
            return mRgba;
        }

        RotatedRect rect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(0)	.toArray()));

        double boundWidth = rect.size.width;
        double boundHeight = rect.size.height;
        int boundPos = 0;
        // Tworzymy liste punktów obiektu
        for (int i = 1; i < contours.size(); i++) {
            rect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(i).toArray()));
            if (rect.size.width * rect.size.height > boundWidth * boundHeight) {
                boundWidth = rect.size.width;
                boundHeight = rect.size.height;
                boundPos = i;
            }
        }

        Rect boundRect = Imgproc.boundingRect(new MatOfPoint(contours.get(boundPos).toArray()));
        Imgproc.rectangle( mRgba, boundRect.tl(), boundRect.br(), CONTOUR_COLOR_WHITE, 2, 8, 0 );

        Log.d(TAG,
                " Row start ["+
                        (int) boundRect.tl().y + "] row end ["+
                        (int) boundRect.br().y+"] Col start ["+
                        (int) boundRect.tl().x+"] Col end ["+
                        (int) boundRect.br().x+"]");

        int rectHeightThresh = 0;
        double a = boundRect.br().y - boundRect.tl().y;
        a = a * 0.7;
        a = boundRect.tl().y + a;

        Log.d(TAG,
                " A ["+a+"] br y - tl y = ["+(boundRect.br().y - boundRect.tl().y)+"]");


        Imgproc.rectangle( mRgba, boundRect.tl(), new Point(boundRect.br().x, a), CONTOUR_COLOR, 2, 8, 0 );

        MatOfPoint2f pointMat = new MatOfPoint2f();
        Imgproc.approxPolyDP(new MatOfPoint2f(contours.get(boundPos).toArray()), pointMat, 3, true);
        contours.set(boundPos, new MatOfPoint(pointMat.toArray()));

        MatOfInt laczenia = new MatOfInt();
        MatOfInt4 conOdcz = new MatOfInt4();
        Imgproc.convexHull(new MatOfPoint(contours.get(boundPos).toArray()), laczenia);

        if(laczenia.toArray().length < 3) return mRgba;

        Imgproc.convexityDefects(new MatOfPoint(contours.get(boundPos)	.toArray()), laczenia, conOdcz);

        List<MatOfPoint> końcowePunkty = new LinkedList<MatOfPoint>();
        List<Point> listPo = new LinkedList<Point>();
        for (int j = 0; j < laczenia.toList().size(); j++) {
            listPo.add(contours.get(boundPos).toList().get(laczenia.toList().get(j)));
        }

        MatOfPoint e = new MatOfPoint();
        e.fromList(listPo);
        końcowePunkty.add(e);

        List<MatOfPoint> defectPoints = new LinkedList<MatOfPoint>();
        List<Point> listPoDefect = new LinkedList<Point>();
        List<Point> palceKonce = new LinkedList<Point>();



        // Co drugi element na liście odpowiada punktowi reprezentującemu palec, inne odpowiadają za punkty złączenia dwóchfliczba palców





        for (int j = 0; j < conOdcz.toList().size(); j = j+4) {
            Point farPoint = contours.get(boundPos).toList().get(conOdcz.toList().get(j+2));
            Point tips = contours.get(boundPos).toList().get(conOdcz.toList().get(j));
            Integer depth = conOdcz.toList().get(j+3);
            if(depth > iThreshold && farPoint.y < a){
                listPoDefect.add(contours.get(boundPos).toList().get(conOdcz.toList().get(j+2)));
                palceKonce.add(contours.get(boundPos).toList().get(conOdcz.toList().get(j)));
            }
            Log.d(TAG, "defects ["+j+"] " + conOdcz.toList().get(j+3));
        }

        MatOfPoint e2 = new MatOfPoint();
        e2.fromList(listPo);
        defectPoints.add(e2);

        Log.d(TAG, "łączenia: " + laczenia.toList());
        Log.d(TAG, "odczepienia: " + conOdcz.toList());

        int wszystkieOdcz = (int) conOdcz.total();
        Log.d(TAG, "wszystkie odczepienia " + wszystkieOdcz);

        this.liczbaPalców = listPoDefect.size();
        if(this.liczbaPalców > 5) this.liczbaPalców = 5;

        mHandler.post(nowaLiczbapalców);

        for(Point p : palceKonce){
            Imgproc.circle(mRgba, p, 10, new Scalar(0,255,0), -1);
        }


        for(Point p : listPoDefect){
            Imgproc.circle(mRgba, p, 8, new Scalar(255,0,255), -1);
        }

        return mRgba;
    }



    // Pobiera rozmieszczenie punktów, analizuje je, i zwraca do pamięci na kliknięcie "save" literę jaki układ ręki symbolizuje


    public void updateNumberOfFingers(){
        liczbaPalcówText.setText(String.valueOf(this.liczbaPalców));
        znakPalców = String.valueOf(this.liczbaPalców);
        Button bt = (Button)findViewById(R.id.emotikony);
        if (kk==0) {
            if (znakPalców == "0") {
                liczbaPalcówText.setText("A");
            } else if (znakPalców == "1") {
                liczbaPalcówText.setText("D");
            } else if (znakPalców == "2") {
                liczbaPalcówText.setText("V");
            } else if (znakPalców == "3") {
                liczbaPalcówText.setText("W");
            } else if (znakPalców == "4") {
                liczbaPalcówText.setText("C");
            }
            else if (znakPalców == "5") {
                liczbaPalcówText.setText("S");
            }
            bt.setText("Litery");
        }
        // Pobiera rozmieszczenie punktów, analizuje je, i zwraca do pamięci na kliknięcie "save" emotikonę  jaki układ ręki symbolizuje
        else if(kk==1){
            if (znakPalców == "0") {
                liczbaPalcówText.setText("\u1F601");
            } else if (znakPalców == "1") {
                liczbaPalcówText.setText("\u1F50");
            } else if (znakPalców == "2") {
                liczbaPalcówText.setText("emo3");
            } else if (znakPalców == "3") {
                liczbaPalcówText.setText("emo4");
            } else if (znakPalców == "4") {
                liczbaPalcówText.setText("emo5");
            }
            bt.setText("Emotikony");
        }
        // Pobiera rozmieszczenie punktów, analizuje je, i zwraca do pamięci na kliknięcie "save" cyfrę jaki układ ręki symbolizuje
        else if(kk==2){
            if (znakPalców == "0") {
                liczbaPalcówText.setText("0");
            } else if (znakPalców == "1") {
                liczbaPalcówText.setText("1");
            } else if (znakPalców == "2") {
                liczbaPalcówText.setText("2");
            } else if (znakPalców == "3") {
                liczbaPalcówText.setText("3");
            } else if (znakPalców == "4") {
                liczbaPalcówText.setText("4");
            }
            bt.setText("Cyfry");
        }
    }

// Zapytania o pozwolenie na używanie camery i wysylanie smsów
    @TargetApi(23)
    public void getPermissionToReadCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(
                    Manifest.permission.CAMERA)) {
            }
            requestPermissions(new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSIONS_REQUEST);}}
    @Override
    @TargetApi(23)
    public void onRequestPermissionsResult(int requestCode,
                                            String permissions[],
                                            int[] grantResults) {
        if (requestCode == CAMERA_PERMISSIONS_REQUEST) {
            if (grantResults.length == 1 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Pozwolenie przyznano", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Pozwolenie  nie zostało przyznane", Toast.LENGTH_SHORT).show();
                    }}}

    public int k=1;
    @Override




    // Sama funkcja zapisująca pobrane znaki do pamięci szeregowej w której umieszczamy message

    public void onClick(View v) {
        String value5 = text66.getText().toString();
        String value4 = text55.getText().toString();
        String value3 = text44.getText().toString();
        String value2 = text33.getText().toString();
        String value1 = text22.getText().toString();
        String value = liczbaPalcówText.getText().toString();
        text66.setText(value);
        text33.setText(value3);
        text44.setText(value4);
        text55.setText(value5);
        text22.setText(value2);

        message = value1 + value2 + value3 + value4 + value;

    }

}

