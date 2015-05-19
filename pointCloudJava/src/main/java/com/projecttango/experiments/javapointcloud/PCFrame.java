package com.projecttango.experiments.javapointcloud; /**
 * Created by yanhang on 5/14/15.
 */

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;
import android.graphics.Bitmap;

import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import com.jni.bitmap_operations.JniBitmapHolder;

import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangoutils.ModelMatCalculator;

public class PCFrame {
    private ArrayList<float[]>posedata = new ArrayList<float[]>();
    private PointCloudActivity activity = null;
    private ArrayList<JniBitmapHolder> RGBdata = new ArrayList<JniBitmapHolder>();
    private static Object saveloc = new Object();
    private static Object addloc = new Object();

    public PCFrame(PointCloudActivity activity_){
        activity = activity_;
    }

    static public float[] getMatrix(ModelMatCalculator calculator){
        float[] curmatrix = calculator.getPointCloudModelMatrixCopy();
        curmatrix[3] = -1*curmatrix[12];
        curmatrix[7] = -1*curmatrix[13];
        curmatrix[11] = -1*curmatrix[14];
        curmatrix[12]=0; curmatrix[13]=0; curmatrix[14]=0;
        return curmatrix;
    }

    public void Adddata(Bitmap newRGBdata, ModelMatCalculator calculator){
        synchronized (addloc) {
            float[] posematrix = getMatrix(calculator);
            JniBitmapHolder curholder = new JniBitmapHolder(newRGBdata);
            RGBdata.add(curholder);
            posedata.add(posematrix);
        }
    }

    public boolean isExternalSorageWritable(){
        String state = Environment.getExternalStorageState();
        if(Environment.MEDIA_MOUNTED.equals(state)){
            return true;
        }
        return false;
    }

    public void saveData(){
        synchronized (saveloc) {

            Log.i("Saving data", String.format("Saving data, color frame:%d, pose frame:%d", RGBdata.size(), posedata.size()));
            boolean isExternal = isExternalSorageWritable();

            try {
                if (!isExternal) {
                    Log.e("IOError", "Can not access storage!");
                    throw new IOException();
                }
                File posePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS + "/data/pose");
                File RGBPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS + "/data/images");

                if (!posePath.exists()) {
                    if (!posePath.mkdirs()) {
                        Log.e("IOError", "Can not make directory");
                        activity.setDebugInfo("Can not make directory");
                        throw new IOException();
                    }
                }

                if (!RGBPath.exists()) {
                    if (!RGBPath.mkdirs()) {
                        Log.e("IOError", "Can not make directory");
                        activity.setDebugInfo("Can not make directory!");
                        throw new IOException();
                    }
                }


                for (int i = 0; i < posedata.size(); i++) {
                    File poseFile = new File(posePath, String.format("/pose%03d.txt", i));

                    if (poseFile.exists())
                        poseFile.delete();

                    Log.i("Saving data", poseFile.getAbsolutePath());
                    activity.setDebugInfo(poseFile.getAbsolutePath());

                    FileOutputStream posewriter = new FileOutputStream(poseFile);
                    PrintWriter pw = new PrintWriter(posewriter);
                    float[] curmatrix = posedata.get(i);
                    for (int j = 0; j < 4; j++) {
                        pw.println(String.format("%f %f %f %f", curmatrix[j * 4], curmatrix[j * 4 + 1], curmatrix[j * 4 + 2], curmatrix[j * 4 + 3]));
                        pw.flush();
                    }
                    pw.close();
                    posewriter.close();
                    activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(poseFile)));
                }

                for (int i = 0; i < RGBdata.size(); i++) {
                    File RGBFile = new File(RGBPath, String.format("/image%03d.png", i));

                    if (RGBFile.exists())
                        RGBFile.delete();
                    Log.i("Saving data", RGBFile.getAbsolutePath());
                    activity.setDebugInfo(RGBFile.getAbsolutePath());

                    FileOutputStream RGBstream = new FileOutputStream(RGBFile);
                    Bitmap curimage = RGBdata.get(i).getBitmap();
                    curimage.compress(Bitmap.CompressFormat.PNG, 60, RGBstream);
                    RGBstream.close();
                    activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(RGBFile)));
                }
            } catch (IOException e) {
                Log.e("IOError", "Cannot write files!");
            }
        }
    }
}
