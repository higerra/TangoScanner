package com.projecttango.experiments.javapointcloud;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangoutils.ModelMatCalculator;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * Created by yanhang on 5/17/15.
 */
public class PointCloud {

    private ArrayList<float[]>points = new ArrayList<float[]>();
    private ArrayList<float[]>poses = new ArrayList<float[]>();
    private ArrayList<Integer>points_per_frame = new ArrayList<Integer>();
    private ArrayList<Float>timestamp = new ArrayList<Float>();

    private PointCloudActivity activity;
    private int point_count = 0;
    private Object savelock = new Object();
    public int downsample = 1;

    PointCloud(PointCloudActivity activity_){
        activity = activity_;
    }

    public boolean isExternalSorageWritable(){
        String state = Environment.getExternalStorageState();
        if(Environment.MEDIA_MOUNTED.equals(state)){
            return true;
        }
        return false;
    }

    private float[] transform(final float[] pt, final float[] mat){
        float[] transformed = new float[3];
        transformed[0] = mat[0]*pt[0] + mat[1]*pt[1] + mat[2]*pt[2] + mat[3];
        transformed[1] = mat[4]*pt[0] + mat[5]*pt[1] + mat[6]*pt[2] + mat[7];
        transformed[2] = mat[8]*pt[0] + mat[9]*pt[1] + mat[10]*pt[2] + mat[11];
        return transformed;
    }

    public boolean AddPoint(final byte[] newframe, final TangoPoseData pose, final ModelMatCalculator calculator,  int count, float time){
        synchronized (savelock) {
            timestamp.add(time);
            final FloatBuffer fb = ByteBuffer.wrap(newframe).order(ByteOrder.nativeOrder()).asFloatBuffer();
            if (fb.capacity() % 3 != 0) {
                Log.w("Point Cloud", "Broken points!");
                return false;
            }
            float[] curpoints = new float[count / downsample * 3];
            points_per_frame.add(count / downsample);
            for(int i=0; i<count / downsample; i++) {
                curpoints[i*3] = fb.get(i * downsample * 3);
                curpoints[i*3+1] = fb.get(i * downsample * 3 + 1);
                curpoints[i*3+2] = fb.get(i * downsample * 3 + 2);
            }
            points.add(curpoints);
            float[] curmatrix = PCFrame.getMatrix(calculator);
            poses.add(curmatrix);
            point_count += count / downsample;
            return true;
        }
    }

//    public boolean AddPoint(final byte[] newframe, final TangoPoseData ss2d, final TangoPoseData d2c,  int count){
//        synchronized (savelock) {
//            final FloatBuffer fb = ByteBuffer.wrap(newframe).order(ByteOrder.nativeOrder()).asFloatBuffer();
//            if (fb.capacity() % 3 != 0) {
//                Log.w("Point Cloud", "Broken points!");
//                return false;
//            }
//            float[] curpoints = new float[count / downsample * 3];
//            points_per_frame.add(count / downsample);
//            for(int i=0; i<count / downsample; i++) {
//                curpoints[i*3] = fb.get(i * downsample * 3);
//                curpoints[i*3+1] = fb.get(i * downsample * 3 + 1);
//                curpoints[i*3+2] = fb.get(i * downsample * 3 + 2);
//            }
//            points.add(curpoints);
//            float[] curmatrix = PCFrame.getMatrix(ss2d, d2c);
//            poses.add(curmatrix);
//            point_count += count / downsample;
//            return true;
//        }
//    }
    public void writePlySingle() {
        synchronized (savelock) {
            boolean isExternal = isExternalSorageWritable();
            if (!isExternal) {
                Log.e("IOError", "Can not access storage!");
                return;
            }
            try {
                File plyPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS + "/data/Ply");
                if (!plyPath.exists()) {
                    if (!plyPath.mkdirs()) {
                        Log.e("IOError", "Can not make directory");
                        activity.setDebugInfo("Can not make directory");
                        throw new IOException();
                    }
                }

                for (int frameid = 0; frameid < points.size(); frameid++) {
                    float[] cameramatrix = poses.get(frameid);
                    float[] curpoints = points.get(frameid);
                    File pcfile = new File(plyPath, String.format("/Scan%03d.ply", frameid));
                    Log.i("Saving data", pcfile.getAbsolutePath());
                    if (pcfile.exists())
                        pcfile.delete();

                    FileOutputStream fos = new FileOutputStream(pcfile);
                    DataOutputStream out = new DataOutputStream(fos);
                    out.write("ply\n".getBytes());
                    out.write("format ascii 1.0\n".getBytes());
                    out.write(String.format("element vertex %d\n", points_per_frame.get(frameid)).getBytes());
                    out.write("property float x\nproperty float y\nproperty float z\n".getBytes());
                    out.write("end_header\n".getBytes());
                    for (int i = 0; i < points_per_frame.get(frameid); i++) {
                        float[] curpt = new float[3];
                        curpt[0] = curpoints[i * 3];
                        curpt[1] = curpoints[i * 3 + 1];
                        curpt[2] = curpoints[i * 3 + 2];

                        float[] transformed = transform(curpt, cameramatrix);
                        out.write(String.format("%f %f %f\n", transformed[0], transformed[1], transformed[2]).getBytes());
                    }
                    out.close();
                    fos.close();
                    activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(pcfile)));
                }
                Log.i("Saving data", "Save complete!");
                File timeFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS + String.format("/data/time_Point.txt"));
                if(timeFile.exists())
                    timeFile.delete();
                FileOutputStream timestream = new FileOutputStream(timeFile);
                PrintWriter pw = new PrintWriter(timestream);
                for(int i=0; i<timestamp.size(); i++){
                    pw.println(String.format("%f", timestamp.get(i)));
                }
                pw.close();
                timestream.close();
                activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(timeFile)));

            } catch (IOException e) {
                Log.e("Point Cloud", "Cannot write point cloud file");
            }
        }
    }

    public boolean writePly(){
        synchronized (savelock) {
            boolean isExternal = isExternalSorageWritable();
            if (!isExternal) {
                Log.e("IOError", "Can not access storage!");
                return false;
            }
            try {
                File pcfile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS + "/data/Scan.ply");
                Log.i("Saving data", pcfile.getAbsolutePath());
                if(pcfile.exists())
                    pcfile.delete();

                FileOutputStream fos = new FileOutputStream(pcfile);
                DataOutputStream out = new DataOutputStream(fos);
                out.write("ply\n".getBytes());
                out.write("format ascii 1.0\n".getBytes());
                out.write(String.format("element vertex %d\n", point_count).getBytes());
                out.write("property float x\nproperty float y\nproperty float z\n".getBytes());
                out.write("end_header\n".getBytes());

                for(int frameid=0; frameid<points.size(); frameid++) {
                    float[] cameramatrix = poses.get(frameid);
                    float[] curpoints = points.get(frameid);
                    for (int i = 0; i < points_per_frame.get(frameid); i++) {
                        float[] curpt = new float[3];
                        curpt[0] = curpoints[i*3]; curpt[1] = curpoints[i*3+1]; curpt[2] = curpoints[i*3+2];
                        float[] transformed = transform(curpt, cameramatrix);
                        out.write(String.format("%f %f %f\n", transformed[0], transformed[1], transformed[2]).getBytes());
                    }
                }
                out.close();
                fos.close();

                activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(pcfile)));
                Log.i("Saving data","Save complete!");

            }catch(IOException e){
                Log.e("Point Cloud", "Cannot write point cloud file");
            }
        }
        return true;
    }

}
