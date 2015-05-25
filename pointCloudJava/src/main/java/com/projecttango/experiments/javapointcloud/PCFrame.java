package com.projecttango.experiments.javapointcloud; /**
 * Created by yanhang on 5/14/15.
 */

import android.content.Intent;
import android.graphics.Matrix;
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
        float[] transposematrix = MatrixUtil.matrixTranspose(curmatrix);
        return transposematrix;
    }



//    static public float[] getMatrix(TangoPoseData ss2d, TangoPoseData d2c){
//        float[] tvec_ss2d = ss2d.getTranslationAsFloats();
//        float[] rot_ss2d = ss2d.getRotationAsFloats();
////        float[] tvec_d2c = d2c.getTranslationAsFloats();
////        float[] rot_d2c = d2c.getRotationAsFloats();
//
//        float[] mat_ss2d = MatrixUtil.matrixTranspose(ModelMatCalculator.quaternionMatrixOpenGL(rot_ss2d));
//        mat_ss2d[3] = tvec_ss2d[0]; mat_ss2d[7] = tvec_ss2d[1]; mat_ss2d[11] = tvec_ss2d[2];
//
////        float[] mat_d2c = MatrixUtil.matrixTranspose(ModelMatCalculator.quaternionMatrixOpenGL(rot_d2c));
////        mat_d2c[3] = tvec_d2c[0]; mat_d2c[7] = tvec_d2c[1]; mat_d2c[11] = tvec_d2c[2];
//
////        float[] ss2c = MatrixUtil.matrixMul(mat_ss2d, mat_d2c);
//
////        float[] resmatrix = MatrixUtil.extrinsicInverse(mat_ss2d);
////        return resmatrix;
//        //return mat_ss2d;
//        return MatrixUtil.setIdentity();
//    }

    public void Adddata(Bitmap newRGBdata, ModelMatCalculator calculator){
        synchronized (addloc) {
            float[] posematrix = getMatrix(calculator);
            JniBitmapHolder curholder = new JniBitmapHolder(newRGBdata);
            RGBdata.add(curholder);
            posedata.add(posematrix);
        }
    }

//    public void Adddata(Bitmap newRGBdata, TangoPoseData ss2d, TangoPoseData d2c){
//        synchronized (addloc) {
//            float[] posematrix = getMatrix(ss2d, d2c);
//            JniBitmapHolder curholder = new JniBitmapHolder(newRGBdata);
//            RGBdata.add(curholder);
//            posedata.add(posematrix);
//        }
//    }

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
                    curimage.compress(Bitmap.CompressFormat.PNG, 100, RGBstream);
                    RGBstream.close();
                    activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(RGBFile)));
                }
            } catch (IOException e) {
                Log.e("IOError", "Cannot write files!");
            }
        }
    }
}
