package com.recordly.mobile.shizuku;

interface IMouseEventCallback {
    oneway void onMouseSample(long elapsedMs, float xNorm, float yNorm, boolean isDown, boolean isUp, int button);
    oneway void onStatus(String message);
}
