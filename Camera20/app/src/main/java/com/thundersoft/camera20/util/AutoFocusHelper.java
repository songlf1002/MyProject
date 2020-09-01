package com.thundersoft.camera20.util;

import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.MeteringRectangle;
import android.util.Log;

public class AutoFocusHelper {
    private static final MeteringRectangle[] ZERO_WEIGHT_3A_REGION = new MeteringRectangle[]{
            new MeteringRectangle(0,0,0,0,0)};

    private static final float REGION_WEIGHT = 0.022f;
    private static final float AF_REGION_BOX = 0.2f;
    private static final float AE_REGION_BOX = 0.3f;
    private static final int CAMERA2_REGION_WEIGHT = (int)(lerp(MeteringRectangle.METERING_WEIGHT_MIN,MeteringRectangle.METERING_WEIGHT_MAX,
            REGION_WEIGHT));

    private static Object lerp(int meteringWeightMin, int meteringWeightMax, float regionWeight) {
        return meteringWeightMin + regionWeight*(meteringWeightMax-meteringWeightMin);
    }

    public AutoFocusHelper(){

    }
    public static MeteringRectangle[] getZeroWeightRegion(){

        return ZERO_WEIGHT_3A_REGION;
    }
    public static Rect cropRegionForZoom(CameraCharacteristics characteristics,float zoom){
        Rect sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int xCenter = sensor.width()/2;
        int yCenter = sensor.height()/2;
        int xDelta = (int)(0.5f*sensor.width()/zoom);
        int yDelta = (int)(0.5f*sensor.height()/zoom);
        return new Rect(xCenter-xDelta,yCenter-yDelta,xCenter+xDelta,yCenter+yDelta);
    }

    public static MeteringRectangle[] regionsForNormalizedCoord(float nx,float ny,float fraction,final Rect cropRegion,
                                                                int sensorOrientation){
        int minCropEdge = Math.min(cropRegion.width(),cropRegion.height());
        int halfSideLength=(int)(0.5f*fraction*minCropEdge);
        PointF nsc =normalizedSensorCoordsForNormalizedDisplayCoords(nx,ny,sensorOrientation);
        int xCenterSensor = (int)(cropRegion.left+nsc.x*cropRegion.width());
        int yCenterSensor = (int)(cropRegion.top + nsc.y*cropRegion.height());

        Rect meteringRegion = new Rect(xCenterSensor-halfSideLength,
                yCenterSensor-halfSideLength,
                xCenterSensor+halfSideLength,
                yCenterSensor+halfSideLength);
        meteringRegion.left=clamp(meteringRegion.left,cropRegion.left,cropRegion.right);
        meteringRegion.top=clamp(meteringRegion.top,cropRegion.top,cropRegion.bottom);
        meteringRegion.right=clamp(meteringRegion.right,cropRegion.left,cropRegion.right);
        meteringRegion.bottom=clamp(meteringRegion.bottom,cropRegion.top,cropRegion.bottom);
        return new MeteringRectangle[]{new MeteringRectangle(meteringRegion,CAMERA2_REGION_WEIGHT)};

    }

    private static int clamp(int x, int min, int max) {
        if (x>max){
            return max;
        }
        if (x<min){
            return min;
        }
        return x;
    }

    private static PointF normalizedSensorCoordsForNormalizedDisplayCoords(float nx, float ny, int sensorOrientation)
    {
        switch (sensorOrientation){
            case 0:
                return new PointF(nx,ny);
            case 90:
                return new PointF(ny,1.0f-nx);
            case 180:
                return new PointF(1.0f-nx,1.0f-ny);
            case 270:
                return new PointF(1.0f-ny,nx);
            default:
                return null;
        }
    }

    public static MeteringRectangle[] afRegionsForNormalizedCoord(float nx,float ny,final Rect cropRegion
    ,int sensorOrientation){
        return regionsForNormalizedCoord(nx,ny,AE_REGION_BOX,cropRegion,sensorOrientation);
    }
    public static MeteringRectangle[] aeRegionsForNormalizedCoord(float nx,float ny,final Rect cropRegion
            ,int sensorOrientation){
        return regionsForNormalizedCoord(nx,ny,AF_REGION_BOX,cropRegion,sensorOrientation);
    }

}
