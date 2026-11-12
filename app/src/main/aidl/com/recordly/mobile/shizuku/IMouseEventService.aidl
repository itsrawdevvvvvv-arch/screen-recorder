package com.recordly.mobile.shizuku;

import com.recordly.mobile.shizuku.IMouseEventCallback;

interface IMouseEventService {
    void destroy() = 16777114;
    void startTracking(int screenWidth, int screenHeight, float sensitivity, IMouseEventCallback callback) = 1;
    void stopTracking() = 2;
    boolean isTracking() = 3;
}
