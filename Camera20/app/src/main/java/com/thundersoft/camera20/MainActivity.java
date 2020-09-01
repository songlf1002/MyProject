package com.thundersoft.camera20;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioAttributes;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.thundersoft.camera20.thread.WorkThreadUtils;
import com.thundersoft.camera20.util.CompareSizeByArea;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;


public class MainActivity extends AppCompatActivity {


    private static final String TAG = "Camera2";

    private static final String TAG_PREVIEW = "预览";

    private static final SparseIntArray ORIENTATION = new SparseIntArray();


    private static final int MAX_PREVIEW_WIDTH = 1920;

    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private static String mCameraId;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private static Handler handlerx;
    private String mFolderPath = "/abc/";//保存视频，图片的路径
    private Size mPreviewSize;
    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraCaptureSession mPreviewSession;
    private CaptureRequest mPreviewRequest;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest.Builder mPreviewBuilder;
    private AutoFitTextureView textureView;
    private Surface mPreviewSurface;
    private Size[] size1;
    private Size[] size2;
    private Size[] size3;
    private Integer mSensorOrientation;

    private boolean mIsRecordingVideo;//开始停止录像

    private MediaRecorder mMediaRecorder;
    private String mNextVideoAbsolutePath;

    // private Semaphore mCameraOpenCloseLock = new Semaphore(1,true);
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private WorkThreadUtils workThreadManager;

    private ImageButton btnSwitch;
    private ImageButton btnTakePicture;
    private ImageButton btnTakeVideo;
    private ImageView ivThumb;

    private Handler mHandler;
    private Handler mUIHandler;
    private Rect maxZoomrect;
    private int maxRealRadio;
    private static File files;
    private Ringtone ringtone;
    private static final int PERMISSION_REQ_ID = 22;
    private Size[] mSize = null;
    public static final int UPDATE_VIEW = 1;
    List<String> mPermissionList = new ArrayList<>();

    private static final String[] REQUESTED_PERMISSION = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO};

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            HandlerThread thread = new HandlerThread("Camera2.0");
            thread.start();
            mHandler = new Handler(thread.getLooper());
            setupCamera(width, height);
            configureTransform(width, height);
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            configureTransform(width, height);
        }
        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
        }
    };

    private void setOrientation(String cameraId) {
        if (cameraId.equals(String.valueOf(1))) {
            ORIENTATION.append(Surface.ROTATION_0, 270);
            ORIENTATION.append(Surface.ROTATION_90, 0);
            ORIENTATION.append(Surface.ROTATION_180, 90);
            ORIENTATION.append(Surface.ROTATION_270, 180);
        }
        if (cameraId.equals(String.valueOf(0))) {
            ORIENTATION.append(Surface.ROTATION_0, 90);
            ORIENTATION.append(Surface.ROTATION_90, 0);
            ORIENTATION.append(Surface.ROTATION_180, 270);
            ORIENTATION.append(Surface.ROTATION_270, 180);
        }
    }

    //相机缩放相关
    private Rect picRect;
    CameraCharacteristics mCameraCharacteristics;
    private void setupCamera(int width, int height) {
        //获取摄像头的管理者CameraManager
    CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            //遍历所有摄像头
            for (String cameraId : manager.getCameraIdList()) {
                setOrientation(cameraId);
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                mCameraCharacteristics = characteristics;
                maxZoomrect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                maxRealRadio = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM).intValue();
                picRect = new Rect(maxZoomrect);
                //cameraCharacteristics = characteristics;
                //默认打开后置摄像头，忽视前置摄像头
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
                    continue;
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)
                ), new CompareSizeByArea());
                choosePreSize(width, height);
                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);

                Log.d(TAG, "setupCamera: 开始预览尺寸---->宽： " + mPreviewSize.getWidth() + "    高" + mPreviewSize.getHeight());

                int orientation = getResources().getConfiguration().orientation;
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    textureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }
                mCameraId = cameraId;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void choosePreSize(int width, int height) {
        int displayRotation = MainActivity.this.getWindowManager().getDefaultDisplay().getRotation();
        mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        boolean swappedDimensions = false;
        switch (displayRotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                    swappedDimensions = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                    swappedDimensions = true;
                }
                break;
            default:
                Log.d(TAG, "Display rotation is invalid: " + displayRotation);
        }
        Point displaySize = new Point();
        MainActivity.this.getWindowManager().getDefaultDisplay().getSize(displaySize);
        int rotatedPreviewWidth = width;
        int rotatedPreviewHeight = height;
        int maxPreviewWidth = displaySize.x;
        int maxPreviewHeight = displaySize.y;
        if (swappedDimensions) {
            rotatedPreviewWidth = height;
            rotatedPreviewHeight = width;
            maxPreviewWidth = displaySize.y;
            maxPreviewHeight = displaySize.x;
        }
        if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
            maxPreviewWidth = MAX_PREVIEW_WIDTH;
        }
        if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
            maxPreviewHeight = MAX_PREVIEW_HEIGHT;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
        return true;
    }

    public Size[] addSize(Size[] sizes, Size size) {
        if (sizes == null) {
            sizes = new Size[1];
            sizes[0] = size;
        } else {
            Size[] copy = Arrays.copyOf(sizes, sizes.length + 1);
            sizes = null;
            sizes = copy;
            sizes[sizes.length - 1] = size;
        }

        return sizes;
    }

    private DisplayMetrics displayMetrics;

    public void getMatchSize() {

        try {
            CameraManager mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            for (final String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
                displayMetrics = getResources().getDisplayMetrics();//获取屏幕分辨率
                int deviceWidth = displayMetrics.widthPixels;
                int deviceHeight = displayMetrics.heightPixels;
                mSize = sizes;
                Log.d(TAG, "getMatchingSize: 屏幕密度宽度=" + deviceWidth);
                Log.d(TAG, "getMatchingSize: 屏幕密度长度=" + deviceHeight);
                for (int i = 0; i < sizes.length; i++) {
                    Size itemSize = sizes[i];
                    Log.d(TAG, "onOptionsItemSelected: " + Double.valueOf(itemSize.getWidth()) / itemSize.getHeight());
                    if ((Double.valueOf(itemSize.getWidth()) / Double.valueOf(itemSize.getHeight())) == 1.0) {
                        //1:1
                        Log.d(TAG, "onOptionsItemSelected1:1: " + itemSize.getWidth() + " " + itemSize.getHeight());
                        size1 = addSize(size1, itemSize);
                        Log.d(TAG, "getMatchSize: size1---->" + size1.length);
                    } else if ((Double.valueOf(itemSize.getWidth()) / itemSize.getHeight()) == (4.0 / 3.0)) {
                        Log.d(TAG, "onOptionsItemSelected4:3: " + itemSize.getWidth() + " " + itemSize.getHeight());

                        size2 = addSize(size2, itemSize);
                        Log.d(TAG, "getMatchSize: size2---->" + size2.length);
                    } else if ((Double.valueOf(itemSize.getWidth()) / itemSize.getHeight()) == (16.0 / 9.0)) {
                        Log.d(TAG, "onOptionsItemSelected16:9: " + itemSize.getWidth() + " " + itemSize.getHeight());
                        size3 = addSize(size3, itemSize);
                        Log.d(TAG, "getMatchSize: size3---->" + size3.length);
                    }

                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setResolution(Size[] size,int a, int b){
        int height = size[0].getHeight();
        int weight = size[0].getWidth();
        for (int i = 0; i < size.length; i++) {
            if (weight < size[i].getWidth()) {
                height = size[i].getHeight();
                weight = size[i].getWidth();
            }
        }
        Log.d(TAG, "onOptionsItemSelected:改变后的分辨率--->宽  " + weight + "   高：" + height);
        if (height < MAX_PREVIEW_HEIGHT) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) textureView.getLayoutParams();
            params.width = height;
            params.height = height * a / b;
            textureView.setLayoutParams(params);
            Log.d(TAG, "onOptionsItemSelected:a ");
        } else {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) textureView.getLayoutParams();
            params.width = MAX_PREVIEW_HEIGHT;
            params.height = MAX_PREVIEW_HEIGHT * a / b;
            textureView.setLayoutParams(params);
            Log.d(TAG, "onOptionsItemSelected:b ");
        }

        mPreviewSize = new Size(weight, height);
        configureTransform(weight, height);
        startPreview();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        getMatchSize();
        switch (item.getItemId()) {
            case R.id.ic_one_to_one:
                setResolution(size1,1,1);
                break;
            case R.id.ic_four_to_three:
                setResolution(size2,4,3);
                break;
            case R.id.ic_sixteen_to_nine:
                setResolution(size3,16,9);
                break;
            default:
        }
        return true;
    }


    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size size, Size t1) {
                    return Long.signum(size.getWidth() * size.getHeight() - t1.getWidth() * t1.getHeight());

                }
            });
        }


        return sizeMap[0];
    }

    private Surface surface;
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            try {
                mPreviewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                SurfaceTexture texture = textureView.getSurfaceTexture();
                texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                surface = new Surface(texture);
                mPreviewBuilder.addTarget(surface);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            startPreview();
            Toast.makeText(MainActivity.this, "照相调用完成000", Toast.LENGTH_SHORT).show();
            btnSwitch.setEnabled(true);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            if (mCameraDevice != null) {
                cameraDevice.close();
                mCameraDevice = null;
                Toast.makeText(MainActivity.this, "照相调用完成sss", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "CameraDeviced DisConnected");
            }
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            if (mCameraDevice != null) {
                cameraDevice.close();
                mCameraDevice = null;
                Activity activity = MainActivity.this;
                if (null != activity) {
                    activity.finish();
                }
                Toast.makeText(MainActivity.this, "摄像头开启失败", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "CameraDevice Error");
            }
        }
    };

    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {

        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {

        }
    };

    private void startPreview() {
        setupImageReader();
        SurfaceTexture mSurfaceTexture = textureView.getSurfaceTexture();
        //设置TextureView的缓冲区大小
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        //获取Surface显示预览数据
        mPreviewSurface = new Surface(mSurfaceTexture);
        try {
            getPreviewRequestBuilder();
            //创建相机捕获会话，第一个参数是捕获数据的输出Surface列表，第二个参数是CameraCaptureSession的状态回调接口，当它创建好后会回调onConfigured方法，第三个参数用来确定Callback
            mCameraDevice.createCaptureSession(Arrays.asList(mPreviewSurface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (null == mCameraDevice) {
                        return;
                    }
                    mCameraCaptureSession = cameraCaptureSession;
                    repeatPreview();

                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }

    private void repeatPreview() {

        //设置反复捕获数据的请求，这样预览界面就会一直有数据显示
        try {
            /* setFocus(mPreviewRequestBuilder);*/
            mPreviewRequestBuilder.setTag(TAG_PREVIEW);
            mPreviewRequest = mPreviewRequestBuilder.build();
            Log.d(TAG, "repeatPreview: " + mPreviewRequest + "    " + mBackgroundHandler);
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, mPreviewCaptureCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void getPreviewRequestBuilder() {
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        //设置预览的显示界面
        mPreviewRequestBuilder.addTarget(mPreviewSurface);
        MeteringRectangle[] meteringRectangles = mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AF_REGIONS);
        if (meteringRectangles != null && meteringRectangles.length > 0) {
            Log.d(TAG, "PreviewRequestBuilder: AF_REGIONS=" + meteringRectangles[0].getRect().toString());

        }
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
    }

    public static Bitmap getRoundBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float roundPx;
        float left, top, right, bottom, dst_left, dst_top, dst_right, dst_bottom;
        if (width <= height) {
            roundPx = width / 2;

            left = 0;
            top = 0;
            right = width;
            bottom = width;

            height = width;

            dst_left = 0;
            dst_top = 0;
            dst_right = width;
            dst_bottom = width;
        } else {
            roundPx = height / 2;

            float clip = (width - height) / 2;

            left = clip;
            right = width - clip;
            top = 0;
            bottom = height;
            width = height;

            dst_left = 0;
            dst_top = 0;
            dst_right = height;
            dst_bottom = height;
        }

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final Rect src = new Rect((int) left, (int) top, (int) right, (int) bottom);
        final Rect dst = new Rect((int) dst_left, (int) dst_top, (int) dst_right, (int) dst_bottom);
        final RectF rectF = new RectF(dst);

        paint.setAntiAlias(true);// 设置画笔无锯齿

        canvas.drawARGB(0, 0, 0, 0); // 填充整个Canvas

        // 以下有两种方法画圆,drawRounRect和drawCircle
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);// 画圆角矩形，第一个参数为图形显示区域，第二个参数和第三个参数分别是水平圆角半径和垂直圆角半径。
        // canvas.drawCircle(roundPx, roundPx, roundPx, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));// 设置两张图片相交时的模式,参考http://www.cnblogs.com/rayray/p/3670120.html
        canvas.drawBitmap(bitmap, src, dst, paint); // 以Mode.SRC_IN模式合并bitmap和已经draw了的Circle

        return output;
    }

    private void setupImageReader() {
        //前三个参数分别是需要的尺寸和格式，最后一个参数代表每次最多获取几帧数据
        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 3);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                Log.i(TAG, "Image Available!");
                /*Image image = imageReader.acquireLatestImage();*/

                //开启线程异步保存图片
                new Thread(new ImageSaver(imageReader.acquireLatestImage())).start();
                handlerx = new Handler() {
                    @Override
                    public void handleMessage(@NonNull Message msg) {
                        switch (msg.what) {
                            case UPDATE_VIEW:
                                Bitmap bitmap = getRoundBitmap(bitmaps);
                                ivThumb.setImageBitmap(bitmap);
                                break;
                            default:
                                break;
                        }

                    }
                };


            }
        }, null);

    }


    private void configureTransform(int width, int height) {

        if (textureView == null || mPreviewSize == null) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();

        RectF viewRect = new RectF(0, 0, width, height);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) height / mPreviewSize.getHeight(),
                    (float) width / mPreviewSize.getWidth());
            //  matrix.postScale(-1,1);

            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);


        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //设置窗体全屏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (getSupportActionBar() != null){
            getSupportActionBar().hide();
        }
        setContentView(R.layout.activity_main);
        Log.d("slf123", "onCreate: ");
        initPermission();
    }

    private static final int SETIMAGE = 1;
    private static final int MOVE_LOCK = 2;

    private void init() {
        textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(textureListener);
        btnSwitch = (ImageButton) findViewById(R.id.switch_picture);
        btnTakePicture = (ImageButton) findViewById(R.id.takePicture);
        btnTakeVideo = (ImageButton) findViewById(R.id.btn_video_recode);
        ivThumb = (ImageView) findViewById(R.id.iv_thumb);
        mUIHandler = new Handler(new InnerCallBack());
        ringtone = RingtoneManager.getRingtone(MainActivity.this, Uri.parse("file:///system/media/audio/ui/camera_click.ogg"));
        AudioAttributes.Builder attr = new AudioAttributes.Builder();
        attr.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION);
        ringtone.setAudioAttributes(attr.build());

        textureView.setOnTouchListener(new View.OnTouchListener() {
            public double zoom;
            public double lastzoom;
            public double lenth;
            int count;

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        count = 1;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (count >= 2) {
                            float x1 = motionEvent.getX(0);
                            float y1 = motionEvent.getY(0);
                            float x2 = motionEvent.getX(1);
                            float y2 = motionEvent.getY(1);
                            float x = x1 - x2;
                            float y = y1 - y2;
                            Double lenthRec = Math.sqrt(x * x + y * y) - lenth;
                            Double viewLenth = Math.sqrt(view.getWidth() * view.getWidth() + view.getHeight() * view.getHeight());
                            zoom = ((lenthRec / viewLenth) * maxRealRadio) + lastzoom;
                            picRect.top = (int) (maxZoomrect.top / (zoom));
                            picRect.left = (int) (maxZoomrect.left / (zoom));
                            picRect.right = (int) (maxZoomrect.right / (zoom));
                            picRect.bottom = (int) (maxZoomrect.bottom / (zoom));
                            Message.obtain(mUIHandler, MOVE_LOCK).sendToTarget();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        count = 0;
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        count++;
                        if (count == 2) {
                            float x1 = motionEvent.getX(0);
                            float y1 = motionEvent.getY(0);
                            float x2 = motionEvent.getX(1);
                            float y2 = motionEvent.getY(1);
                            float x = x1 - x2;
                            float y = y1 - y2;
                            lenth = Math.sqrt(x * x + y * y);
                        }
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                        count--;
                        if (count < 2)
                            lastzoom = zoom;
                        break;

                }
                return true;
            }
        });

        btnTakePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Toast.makeText(MainActivity.this,"照相调用完成",Toast.LENGTH_SHORT);
                shootSound();
                capture();
                Toast.makeText(MainActivity.this, "拍照成功", Toast.LENGTH_SHORT).show();
            }
        });

        btnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnSwitch.setEnabled(false);

                if (mCameraId.equals(String.valueOf(0))) {
                    Log.d(TAG, "后转前");
                    mCameraId = String.valueOf(1);
                    setOrientation(mCameraId);
                    closeCamera();

                    reopenCamera();

                } else if (mCameraId.equals(String.valueOf(1))) {
                    Log.d(TAG, "前转后");
                    mCameraId = String.valueOf(0);
                    setOrientation(mCameraId);

                    closeCamera();

                    reopenCamera();

                }
            }

        });

        btnTakeVideo.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                btnTakeVideo.setEnabled(false);
                Log.d(TAG, "slf:按钮不可用 ");
                if (mIsRecordingVideo) {
                    mIsRecordingVideo = !mIsRecordingVideo;
                    stopRecordingVideo();
                    //btn_record_video.setText("开始录像");
                    Toast.makeText(MainActivity.this, "录像结束", Toast.LENGTH_SHORT).show();
                } else {
                    // btn_record_video.setText("停止录像");
                    mIsRecordingVideo = !mIsRecordingVideo;
                    startRecordingVideo();

                    Toast.makeText(MainActivity.this, "录像开始", Toast.LENGTH_SHORT).show();
                }

            }
        });
        ivThumb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent("android.intent.action.PICK");
                intent.setType("image/*");
                startActivityForResult(intent, 3);
            }
        });

    }

    /**
     * 播放系统的拍照的声音
     */
    public void shootSound() {
        ringtone.stop();
        ringtone.play();
    }


    private void startRecordingVideo() {

        if (mCameraDevice == null || !textureView.isAvailable() || mPreviewSize == null) {

            return;
        }
        try {

            closePreviewSession();

            setUpMediaRecorder();
            //Toast.makeText(MainActivity.this,"1122",Toast.LENGTH_SHORT).show();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    mCameraCaptureSession  =cameraCaptureSession;
                    updatePreview();
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //开启录像
                            Log.d(TAG, "开始录像");
                            mMediaRecorder.start();

                        }
                    });


                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, mBackgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "slf:开始按钮可用 ");
        btnTakeVideo.setEnabled(true);
    }

    private void updatePreview() {
        if (mCameraDevice == null) {
            return;
        }
        try {
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpMediaRecorder() throws Exception {
        Toast.makeText(MainActivity.this, "录像" + String.valueOf(MediaRecorder.AudioSource.MIC), Toast.LENGTH_SHORT).show();
        Log.d(TAG, "fdjsknfdkjsn" + " " + String.valueOf(MediaRecorder.AudioSource.MIC));
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);//用于录制的音源

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath();
        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        Log.d(TAG, "setUpMediaRecorder:--> " + mNextVideoAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);

        mMediaRecorder.setVideoFrameRate(25);//设置要捕获的视频帧速率
        mMediaRecorder.setVideoSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());//设置要捕获的视频的宽度和高度
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);//设置视频编码器，用于录制
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//设置audio的编码格式

        int rotation = MainActivity.this.getWindowManager().getDefaultDisplay().getRotation();
        Log.d(TAG, "setUpMediaRecorder: " + mSensorOrientation);
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(ORIENTATION.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(ORIENTATION.get(rotation));
                break;
        }


        mMediaRecorder.prepare();


    }

    private boolean flag = false;

    private String getVideoFilePath() {
        String path = Environment.getExternalStorageDirectory() + "/abc/";
        setFolderPath(path);
        return path + System.currentTimeMillis() + ".mp4";
    }

    public void setFolderPath(String path) {
        this.mFolderPath = path;
        File mFolder = new File(path);
        if (!mFolder.exists()) {
            mFolder.mkdirs();
            Log.d(TAG, "setFolderPath: 文件夹不存在去创建");
        } else {
            Log.d(TAG, "setFolderPath: 文件夹已创建");
        }
    }


    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    private void stopRecordingVideo() {
        startPreview();
        if (mMediaRecorder != null) {
            try {
                Log.d(TAG, "stopRecordingVideo:未调用 ");
                mMediaRecorder.stop();

            } catch (IllegalStateException e) {
                mMediaRecorder.reset();
            }

            mNextVideoAbsolutePath = null;

            Log.d(TAG, "slf:停止按钮可用 ");
            btnTakeVideo.setEnabled(true);
        }


    }

    public void reopenCamera() {
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("slf123", "onStart: ");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("slf123", "onResume: ");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    protected void onPause() {
        Log.d("slf123", "onPause: ");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("slf123", "onStop: ");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("slf123", "onDestroy: ");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d("slf123", "onRestart: ");
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();

            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private void closeCamera() {

        //mCameraOpenCloseLock.acquire();
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        Log.d(TAG, "closeCamera: " + "关闭相机");

    }


    private void capture() {
        try {

            //首先我们创建请求拍照的CaptureRequest
            final CaptureRequest.Builder mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            //获取屏幕方向
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            mCaptureBuilder.addTarget(mPreviewSurface);
            mCaptureBuilder.addTarget(mImageReader.getSurface());
            mCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //设置拍照方向
            mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));
            //停止预览
            mCameraCaptureSession.stopRepeating();
            //开始拍照，然后回调上面的接口重启预览，因为mCaptureBuilder设置ImageReader作为target，所以会自动回调ImageReader的onImageAvailable()方法保存图片
            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {

                //当一个图像捕捉已经完全完成并且所有的结果元数据都可用时，这个方法就会被调用。
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    repeatPreview();
                }
            };

            mCameraCaptureSession.capture(mCaptureBuilder.build(), captureCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void openCamera() {
        //获取摄像头的管理者CameraManager
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        //检查权限
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            //打开相机，第一个参数表示打开哪个摄像头，第二个参数为相机的状态回调接口，第三个参数来确定Callback在哪个线程执行，为空的话就在当前执行
            manager.openCamera(mCameraId, stateCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private static Bitmap bitmaps;

    private class ImageSaver implements Runnable {


        private Image mImage;
        private String mFolderPath = "/abc/";

        private void setFolderPath(String path) {
            this.mFolderPath = path;
            File mFolder = new File(path);
            if (!mFolder.exists()) {
                mFolder.mkdirs();
                Log.d(TAG, "setFolderPath: 文件夹不存在去创建");
            } else {
                Log.d(TAG, "setFolderPath: 文件夹已创建");
            }
        }

        private String getFolderPath() {
            return mFolderPath;
        }

        public ImageSaver(Image image) {
            mImage = image;
        }

        private String getNowDate() {
            /*SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return simpleDateFormat.format(new Date());*/
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
            return simpleDateFormat.format(new Date());

        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            Log.d(TAG, "run: -->" + data);
            buffer.get(data);
            setFolderPath(Environment.getExternalStorageDirectory() + getFolderPath());
            Log.d(TAG, "run: " + getFolderPath());
            String imageName = getNowDate() + new Random().nextInt(1024) + ".jpg";
            File imageFile = new File(getFolderPath() + imageName);
            files = imageFile;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap != null) {
                bitmap = adjustPhotoRotation(bitmap, 0);
            }
            bitmaps = bitmap;
            Message message = new Message();
            message.what = UPDATE_VIEW;
            handlerx.sendMessage(message);
            if (mCameraId.equals(String.valueOf(1))) {
                Matrix matrix = new Matrix();
                matrix.postScale(-1, 1);
            }


            Log.d(TAG, "run: " + imageFile);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(imageFile);
                fos.write(data, 0, data.length);
                bitmap.compress(Bitmap.CompressFormat.JPEG,100,fos);
                fos.flush();
                buffer.clear();

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (mImage != null) {
                    mImage.close();
                }
                imageFile = null;
                if (fos != null) {
                    try {

                        fos.close();
                        fos = null;
                        MediaStore.Images.Media.insertImage(MainActivity.this.getContentResolver(),
                                getFolderPath(),imageName,null);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    MainActivity.this.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,Uri.parse(getFolderPath())));
                }
            }

        }

        private Bitmap adjustPhotoRotation(Bitmap bitmap, int orientationDegree) {
            Matrix m = new Matrix();
            //m.postSkew(1,-1);

            //m.postRotate(orientationDegree,(float)bitmap.getWidth()/2,(float)bitmap.getHeight()/2);
            m.setRotate(orientationDegree, (float) bitmap.getWidth() / 2, (float) bitmap.getHeight() / 2);
            if (mCameraId.equals(String.valueOf(1))) {
                m.setScale(-1, 1);
            }

            try {
                Bitmap bitmap1 = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
                return bitmap1;
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
            }
            return null;
        }


    }


    private void initPermission() {
        mPermissionList.clear();//清空已经允许的没有通过的权限
        //逐个判断是否有没有通过的权限
        for (int i = 0; i < REQUESTED_PERMISSION.length; i++) {
            if (ContextCompat.checkSelfPermission(this, REQUESTED_PERMISSION[i]) != PackageManager.PERMISSION_GRANTED) {
                mPermissionList.add(REQUESTED_PERMISSION[i]);//添加还未授予的权限到mPermissionList中
            }
        }
        if (mPermissionList.size() > 0) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSION, PERMISSION_REQ_ID);
        } else {
            init();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean hasPermissionDismiss = false;//有权限没有通过
        Log.d(TAG, "onRequestPermissionsResult: " + grantResults[0] + " " + requestCode);
        if (PERMISSION_REQ_ID == requestCode) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] == -1) {
                    hasPermissionDismiss = true;
                    break;
                }
            }
        }
        if (hasPermissionDismiss) {//如果有没有呗允许的权限
            showPermissionDialog();
        } else {
            //权限已经都通过了，可以将程序继续打开
            init();
        }
    }

    AlertDialog mPermissionDialog;
    String mPackName = "com.thundersoft.camera20";

    private void showPermissionDialog() {
        if (mPermissionDialog == null) {
            mPermissionDialog = new AlertDialog.Builder(this).setMessage("已禁用权限，请手动授予")
                    .setPositiveButton("设置", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            cancelPermissionDialog();
                            Uri packageURI = Uri.parse("package:" + mPackName);
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageURI);
                            startActivity(intent);
                        }
                    }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            cancelPermissionDialog();
                            MainActivity.this.finish();
                        }
                    }).create();
        }
        mPermissionDialog.show();
    }

    private void cancelPermissionDialog() {
        mPermissionDialog.cancel();
    }


    private class InnerCallBack implements Handler.Callback {

        @Override
        public boolean handleMessage(@NonNull Message message) {
            switch (message.what) {
                case MOVE_LOCK:
                    mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, picRect);
                    try {
                        mCameraCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null,
                                mHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                    break;
            }
            return false;
        }
    }
}
