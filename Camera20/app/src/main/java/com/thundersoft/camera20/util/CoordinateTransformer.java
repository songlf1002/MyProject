package com.thundersoft.camera20.util;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.CameraCharacteristics;


public class CoordinateTransformer {
    private final Matrix mPreviewToCameraTransform;
    private RectF mDriveRectF;


    public CoordinateTransformer(CameraCharacteristics chr,RectF previewRect) {
        if (!hasNonZeroArea(previewRect)){
            throw new IllegalArgumentException("previewRect");
        }
        Rect rect = chr.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        Integer sensorOrientation = chr.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int rotation = sensorOrientation ==null ? 90 :sensorOrientation;
        mDriveRectF = new RectF(rect);
        Integer face = chr.get(CameraCharacteristics.LENS_FACING);
        boolean mirrorx = face != null && face == CameraCharacteristics.LENS_FACING_FRONT;
        mPreviewToCameraTransform = previewToCameraTransform(mirrorx,rotation,previewRect);
    }
    public RectF toCameraSpace(RectF source){
        RectF result = new RectF();
        mPreviewToCameraTransform.mapRect(result,source);
        return result;
    }

    private Matrix previewToCameraTransform(boolean mirrorx, int rotation, RectF previewRect) {
        Matrix transform = new Matrix();
        transform.setScale(mirrorx?-1:1,1);
        transform.postRotate(-rotation);
        transform.mapRect(previewRect);
        Matrix fill = new Matrix();
        fill.setRectToRect(previewRect,mDriveRectF,Matrix.ScaleToFit.FILL);
        transform.setConcat(fill,transform);
        return transform;
    }

    private boolean hasNonZeroArea(RectF previewRect) {
        return previewRect.width() != 0 && previewRect.height() != 0;
    }


}
