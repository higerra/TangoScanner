/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.experiments.javapointcloud;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.projecttango.tangoutils.ModelMatCalculator;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Main Activity class for the Point Cloud Sample. Handles the connection to the {@link Tango}
 * service and propagation of Tango XyzIj data to OpenGL and Layout views. OpenGL rendering logic is
 * delegated to the {@link PCRenderer} class.
 */
public class PointCloudActivity extends Activity implements OnClickListener{

    private static final String TAG = PointCloudActivity.class.getSimpleName();
    private static final int SECS_TO_MILLISECS = 1000;
    private Tango mTango;
    private TangoConfig mConfig;

    private PCRenderer mRenderer;
    private RGBRenderer mRGBRenderer;

    private GLSurfaceView mGLView;
    private GLSurfaceView mColorView;

    private TextView mFrameCountTextView;
    private TextView mPointCountTextView;
    private TextView mTangoServiceVersionTextView;
    private TextView mApplicationVersionTextView;
    private TextView mFrequencyTextView;
    private TextView mDebugInfoTextView;
    private TextView mIntrinsic;
    private TextView mDistortion;

    private Button mFirstPersonButton;
    private Button mThirdPersonButton;
    private Button mTopDownButton;
    private Button mSaveData;
    private Button mCapture;

    private int mPreviousPoseStatus;
    private int mPointCount;
    private float mPosePreviousTimeStamp;
    private float mXyIjPreviousTimeStamp;
    private float mCurrentTimeStamp;
    private float mPointCloudFrameDelta;

    private float mColorPreviousTimeStamp;
    private float mColorDeltaTime;

    private int mColorFrameCount = 0;

    private String mServiceVersion;
    private float mDeltaTime;
    private int count;
    private boolean mIsTangoServiceConnected;
    private TangoPoseData mDevice2Color;
    private TangoPoseData mDevice2Depth;
    private TangoPoseData mPose;

    private static final int UPDATE_INTERVAL_MS = 50;
    private static ModelMatCalculator mExtrinsicCalculator = new ModelMatCalculator();
    public static Object poseLock = new Object();
    public static Object depthLock = new Object();

    private final int mCaptureInterval = 1;
    private int mCaptureCount = 0;

    private final int mPointCloudInterval = 2;
    private int mPointCloudCount = 0;

    private String mDebugText = new String("Initializing...");

    HashMap<Integer, Integer> cameraTextures;

    private PCFrame frame = new PCFrame(this);
    private PointCloud pointcloud = new PointCloud(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jpoint_cloud);
        setTitle(R.string.app_name);
        mFrameCountTextView = (TextView) findViewById(R.id.frame_count);
        mPointCountTextView = (TextView) findViewById(R.id.pointCount);
        mTangoServiceVersionTextView = (TextView) findViewById(R.id.version);
        mApplicationVersionTextView = (TextView) findViewById(R.id.appversion);
        mFrequencyTextView = (TextView) findViewById(R.id.frameDelta);
        mDebugInfoTextView = (TextView) findViewById(R.id.debuginfo);
        mIntrinsic = (TextView) findViewById(R.id.intrinsic);
        mDistortion = (TextView) findViewById(R.id.distortion);

        mFirstPersonButton = (Button) findViewById(R.id.first_person_button);
        mFirstPersonButton.setOnClickListener(this);
        mThirdPersonButton = (Button) findViewById(R.id.third_person_button);
        mThirdPersonButton.setOnClickListener(this);
        mTopDownButton = (Button) findViewById(R.id.top_down_button);
        mTopDownButton.setOnClickListener(this);
        mSaveData = (Button) findViewById(R.id.save_data);
        mSaveData.setOnClickListener(this);
        mCapture = (Button) findViewById(R.id.capture_frame);
        mCapture.setOnClickListener(this);

        cameraTextures = new HashMap<Integer, Integer>();

        mTango = new Tango(this);
        mConfig = new TangoConfig();
        mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);

        int maxDepthPoints = mConfig.getInt("max_point_cloud_elements");
        mRenderer = new PCRenderer(maxDepthPoints);
        mRGBRenderer = new RGBRenderer(this);

        mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
        mGLView.setEGLContextClientVersion(2);
        mGLView.setRenderer(mRenderer);

        mColorView = (GLSurfaceView) findViewById(R.id.color_preview);
        mColorView.setEGLContextClientVersion(2);
        mColorView.setRenderer(mRGBRenderer);
        mColorView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mColorView.setZOrderOnTop(true);


        PackageInfo packageInfo;
        try {
            packageInfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
            mApplicationVersionTextView.setText(packageInfo.versionName);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        // Display the version of Tango Service
        mServiceVersion = mConfig.getString("tango_service_library_version");
        mTangoServiceVersionTextView.setText(mServiceVersion);
        mIsTangoServiceConnected = false;

        startUIThread();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            mTango.disconnect();
            mIsTangoServiceConnected = false;
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mIsTangoServiceConnected) {
            startActivityForResult(
                    Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_MOTION_TRACKING),
                    Tango.TANGO_INTENT_ACTIVITYCODE);
        }
        Log.i(TAG, "onResumed");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == Tango.TANGO_INTENT_ACTIVITYCODE) {
            Log.i(TAG, "Triggered");
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, R.string.motiontrackingpermission, Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            try {
                setTangoListeners();
            } catch (TangoErrorException e) {
                Toast.makeText(this, R.string.TangoError, Toast.LENGTH_SHORT).show();
            } catch (SecurityException e) {
                Toast.makeText(getApplicationContext(), R.string.motiontrackingpermission,
                        Toast.LENGTH_SHORT).show();
            }
            try {
                mTango.connect(mConfig);
                mIsTangoServiceConnected = true;
                TangoCameraIntrinsics intrin = mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                mIntrinsic.setText(String.format("fx:%f fy:%f cx:%f cy:%f", intrin.fx, intrin.fy, intrin.cx, intrin.cy));
                mDistortion.setText(String.format("r1:%f r2:%f r3:%f", intrin.distortion[0], intrin.distortion[1], intrin.distortion[2]));
            } catch (TangoOutOfDateException e) {
                Toast.makeText(getApplicationContext(), R.string.TangoOutOfDateException,
                        Toast.LENGTH_SHORT).show();
            } catch (TangoErrorException e) {
                Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT)
                        .show();
            }
            synchronized(this) {
                for (Map.Entry<Integer, Integer> entry : cameraTextures.entrySet())
                    mTango.connectTextureId(entry.getKey(), entry.getValue());
            }
            setUpExtrinsics();
        }
    }

    public synchronized void attachTexture(final int cameraId, final int textureName) {
        if (textureName > 0) {
            // Link the texture with Tango if the texture changes after
            // Tango is connected. This generally doesn't happen but
            // technically could because they happen in separate
            // threads. Otherwise the link will be made in startTango().
            if (mIsTangoServiceConnected && cameraTextures.get(cameraId) != textureName)
                mTango.connectTextureId(cameraId, textureName);
            cameraTextures.put(cameraId, textureName);
        }
        else
            cameraTextures.remove(cameraId);
    }

    public synchronized void updateTexture(int cameraId) {
        if (mIsTangoServiceConnected) {
            try {
                mTango.updateTexture(cameraId);
            }
            catch (TangoInvalidException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void addFrame(final Bitmap bitmap) {
        TangoPoseData RGBPose = mTango.getPoseAtTime(0.0,
                new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION, TangoPoseData.COORDINATE_FRAME_DEVICE));
        if (RGBPose.statusCode == TangoPoseData.POSE_VALID && mColorFrameCount < 300) {
            mColorDeltaTime = (float)(RGBPose.timestamp - mColorPreviousTimeStamp) * SECS_TO_MILLISECS;
            mColorPreviousTimeStamp = (float)RGBPose.timestamp;

            mExtrinsicCalculator.updatePointCloudModelMatrix(
                    RGBPose.getTranslationAsFloats(),
                    RGBPose.getRotationAsFloats());

            if(mCaptureCount >= mCaptureInterval) {
                frame.Adddata(bitmap, mExtrinsicCalculator);
                //frame.Adddata(bitmap, RGBPose, mDevice2Color);
                mColorFrameCount++;
                mCaptureCount = 0;
            }else
                mCaptureCount++;
        }
    }

    public Point getCameraFrameSize(int cameraId) {
        TangoCameraIntrinsics intrinsics = mTango.getCameraIntrinsics(cameraId);
        return new Point(intrinsics.width, intrinsics.height);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.first_person_button:
                mRenderer.setFirstPersonView();
                break;
            case R.id.third_person_button:
                mRenderer.setThirdPersonView();
                break;
            case R.id.top_down_button:
                mRenderer.setTopDownView();
                break;
            case R.id.save_data:
                mTango.disconnect();
                mIsTangoServiceConnected = false;
                setDebugInfo("Saving data");
                frame.saveData();
                setDebugInfo("Saving point clouds...");
                pointcloud.writePly();
                setDebugInfo("Save complete!");
                break;
            case R.id.capture_frame:
                setDebugInfo("Frame captured");
                mRGBRenderer.startSaveFrame();
            default:
                Log.w(TAG, "Unrecognized button click.");
                return;
        }
    }

    public void setDebugInfo(final String debugtext){
        mDebugText = debugtext;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mRenderer.onTouchEvent(event);
    }

    private void setUpExtrinsics() {
        // Set device to imu matrix in Model Matrix Calculator.
        TangoPoseData device2IMUPose = new TangoPoseData();
        TangoCoordinateFramePair framePair = new TangoCoordinateFramePair();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
        try {
            device2IMUPose = mTango.getPoseAtTime(0.0, framePair);
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
        }
        mRenderer.getModelMatCalculator().SetDevice2IMUMatrix(
                device2IMUPose.getTranslationAsFloats(), device2IMUPose.getRotationAsFloats());
        mExtrinsicCalculator.SetDevice2IMUMatrix(
                device2IMUPose.getTranslationAsFloats(), device2IMUPose.getRotationAsFloats());

        // Set color camera to imu matrix in Model Matrix Calculator.
        TangoPoseData color2IMUPose = new TangoPoseData();

        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR;
        try {
            color2IMUPose = mTango.getPoseAtTime(0.0, framePair);
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
        }
        mRenderer.getModelMatCalculator().SetColorCamera2IMUMatrix(
                color2IMUPose.getTranslationAsFloats(), color2IMUPose.getRotationAsFloats());
        mExtrinsicCalculator.SetColorCamera2IMUMatrix(
                color2IMUPose.getTranslationAsFloats(), color2IMUPose.getRotationAsFloats());

//        mDevice2Color = new TangoPoseData();
//        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR;
//        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
//        mDevice2Color = mTango.getPoseAtTime(0.0, framePair);
//
//        mDevice2Depth = new TangoPoseData();
//        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH;
//        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
//        mDevice2Depth = mTango.getPoseAtTime(0.0, framePair);
    }


    private void setTangoListeners() {
        // Configure the Tango coordinate frame pair
        final ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        // Listen for new Tango data
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                // Make sure to have atomic access to Tango Pose Data so that
                // render loop doesn't interfere while Pose call back is updating
                // the data.
                synchronized (poseLock) {
                    if(pose.statusCode == TangoPoseData.POSE_VALID) {
                        setDebugInfo("Standby");
                        mRGBRenderer.startSaveFrame();
                        mPose = pose;
                        // Calculate the delta time from previous pose.
                        mDeltaTime = (float) (pose.timestamp - mPosePreviousTimeStamp)
                                * SECS_TO_MILLISECS;
                        mPosePreviousTimeStamp = (float) pose.timestamp;
                        if (mPreviousPoseStatus != pose.statusCode) {
                            count = 0;
                        }
                        count++;
                        mPreviousPoseStatus = pose.statusCode;
                        mRenderer.getModelMatCalculator().updateModelMatrix(
                                pose.getTranslationAsFloats(), pose.getRotationAsFloats());
                        mRenderer.updateViewMatrix();
                    }
                }
            }


            @Override
            public void onXyzIjAvailable(final TangoXyzIjData xyzIj) {
                // Make sure to have atomic access to TangoXyzIjData so that
                // render loop doesn't interfere while onXYZijAvailable callback is updating
                // the point cloud data.
                synchronized (depthLock) {
                    mCurrentTimeStamp = (float) xyzIj.timestamp;
                    mPointCloudFrameDelta = (mCurrentTimeStamp - mXyIjPreviousTimeStamp)
                            * SECS_TO_MILLISECS;
                    mXyIjPreviousTimeStamp = mCurrentTimeStamp;
                    mPointCount = xyzIj.xyzCount;
                    byte[] buffer = new byte[xyzIj.xyzCount * 3 * 4];

                    // TODO: Use getXYZBuffer() call instead of parcel file directly.
                    FileInputStream fileStream = new FileInputStream(xyzIj.xyzParcelFileDescriptor
                            .getFileDescriptor());

                    try {
                        fileStream.read(buffer, xyzIj.xyzParcelFileDescriptorOffset, buffer.length);
                        fileStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        TangoPoseData pointCloudPose = mTango.getPoseAtTime(mCurrentTimeStamp,
                                framePairs.get(0));

                        if(pointCloudPose.statusCode == TangoPoseData.POSE_VALID) {
                            mRenderer.getPointCloud().UpdatePoints(buffer, xyzIj.xyzCount);
                            mRenderer.getModelMatCalculator().updatePointCloudModelMatrix(
                                    pointCloudPose.getTranslationAsFloats(),
                                    pointCloudPose.getRotationAsFloats());
                            mRenderer.getPointCloud().setModelMatrix(
                                    mRenderer.getModelMatCalculator().getPointCloudModelMatrixCopy());

                            if(mPointCloudCount >= mPointCloudInterval) {
                                pointcloud.AddPoint(buffer, pointCloudPose, mRenderer.getModelMatCalculator(), xyzIj.xyzCount);
                                //pointcloud.AddPoint(buffer, pointCloudPose, mDevice2Depth, xyzIj.xyzCount);
                                mPointCloudCount = 0;
                            }else{
                                mPointCloudCount++;
                            }
                        }
                    }
                    catch (TangoErrorException e) {
                        Toast.makeText(getApplicationContext(), R.string.TangoError,
                                Toast.LENGTH_SHORT).show();
                    } catch (TangoInvalidException e) {
                        Toast.makeText(getApplicationContext(), R.string.TangoError,
                                Toast.LENGTH_SHORT).show();
                    }

                }
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                if(cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR)
                    mColorView.requestRender();
            }
        });
    }

    /**
     * Create a separate thread to update Log information on UI at the specified interval of
     * UPDATE_INTERVAL_MS. This function also makes sure to have access to the mPose atomically.
     */
    private void startUIThread() {
        new Thread(new Runnable() {
            final DecimalFormat threeDec = new DecimalFormat("0.000");

            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(UPDATE_INTERVAL_MS);
                        // Update the UI with TangoPose information
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                synchronized (depthLock) {
                                    mDebugInfoTextView.setText(mDebugText);
                                    // Display number of points in the point cloud
                                    mPointCountTextView.setText(Integer.toString(mPointCount));
                                    mFrequencyTextView.setText(""
                                            + threeDec.format(mColorDeltaTime));
                                    mFrameCountTextView.setText(Integer.toString(mColorFrameCount));
                                }
                            }
                        });
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
}
