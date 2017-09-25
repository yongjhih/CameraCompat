/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Piasy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.github.piasy.cameracompat.compat;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.github.piasy.cameracompat.CameraCompat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jp.co.cyberagent.android.gpuimage.Rotation;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
class Camera1Helper extends CameraHelper {

    private final CameraController mCameraController;
    private final Camera1PreviewCallback mPreviewCallback;
    private Camera mCamera;

    Camera1Helper(int previewWidth, int previewHeight, int activityRotation, boolean isFront,
            CameraController cameraController, Camera1PreviewCallback previewCallback) {
        super(previewWidth, previewHeight, activityRotation, isFront);
        mCameraController = cameraController;
        mPreviewCallback = previewCallback;
    }

    @Override
    protected boolean startPreview() {
        try {
            mCamera = openCamera();
            Camera.Parameters parameters = mCamera.getParameters();
            List<String> modes = parameters.getSupportedFocusModes();
            String focusMode = findSettableValue(modes,
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            if (focusMode == null) {
                focusMode = findSettableValue(modes,
                        Camera.Parameters.FOCUS_MODE_AUTO,
                        Camera.Parameters.FOCUS_MODE_MACRO,
                        Camera.Parameters.FOCUS_MODE_EDOF);
            }
            if (focusMode != null) {
                parameters.setFocusMode(focusMode);
            }
            setFocusArea(parameters);
            PreviewSize previewSize = findOptSize(mPreviewWidth, mPreviewHeight);
            parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
            mCamera.setParameters(parameters);
            mCamera.setPreviewCallback(mPreviewCallback);
            Rotation rotation = getRotation();
            mCamera.setDisplayOrientation(mIsFront ? (360 - rotation.asInt()) : rotation.asInt());
            mCameraController.onOpened(mCamera, rotation, mIsFront, false);
        } catch (RuntimeException e) {
            CameraCompat.onError(CameraCompat.ERR_UNKNOWN);
            return false;
        }
        return true;
    }

    private static final int AREA_PER_1000 = 400;

    public static void setFocusArea(@NonNull Camera.Parameters parameters) {
        if (parameters.getMaxNumFocusAreas() > 0) {
            List<Camera.Area> middleArea = buildMiddleArea(AREA_PER_1000);
            parameters.setFocusAreas(middleArea);
        }
    }

    public static List<Camera.Area> buildMiddleArea(int areaPer1000) {
        return Collections.singletonList(
                new Camera.Area(new Rect(-areaPer1000, -areaPer1000, areaPer1000, areaPer1000), 1));
    }

    @Override
    protected boolean stopPreview() {
        if (mCamera == null) {
            return true;
        }
        try {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        } catch (RuntimeException e) {
            CameraCompat.onError(CameraCompat.ERR_UNKNOWN);
            return false;
        }
        return true;
    }

    @Override
    protected int getSensorDegree() {
        return getCameraInfo(getCurrentCameraId()).orientation;
    }

    @Override
    protected boolean canOperateFlash() {
        return mCamera != null;
    }

    @Override
    protected void doOpenFlash() throws RuntimeException {
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        mCamera.setParameters(parameters);
    }

    @Override
    protected void doCloseFlash() throws RuntimeException {
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        mCamera.setParameters(parameters);
    }

    @Override
    protected List<PreviewSize> getSupportedSize() throws RuntimeException {
        List<Camera.Size> supportedSize = mCamera.getParameters().getSupportedPreviewSizes();
        List<PreviewSize> results = new ArrayList<>();
        for (int i = 0, size = supportedSize.size(); i < size; i++) {
            Camera.Size option = supportedSize.get(i);
            results.add(new PreviewSize(option.width, option.height));
        }
        return results;
    }

    private Camera openCamera() {
        return Camera.open(mIsFront ? Camera.CameraInfo.CAMERA_FACING_FRONT
                : Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    private int getCurrentCameraId() {
        return mIsFront ? Camera.CameraInfo.CAMERA_FACING_FRONT
                : Camera.CameraInfo.CAMERA_FACING_BACK;
    }

    private Camera.CameraInfo getCameraInfo(final int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        return info;
    }

    interface CameraController {
        void onOpened(final Camera camera, final Rotation rotation, final boolean flipHorizontal,
                final boolean flipVertical);
    }

    @Nullable
    private static String findSettableValue(@NonNull Collection<String> supportedValues,
                                            String... desiredValues) {
        String result = null;
        for (String desiredValue : desiredValues) {
            if (supportedValues.contains(desiredValue)) {
                result = desiredValue;
                break;
            }
        }
        return result;
    }
}
