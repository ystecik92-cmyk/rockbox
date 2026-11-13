/***************************************************************************
 *             __________               __   ___.
 *   Open      \______   \ ____   ____ |  | _\_ |__   _______  ___
 *   Source     |       _//  _ \/ ___\|  |/ /| __ \ /  _ \  \/  /
 *   Jukebox    |    |   (  <_> )  \___|    < | \_\ (  <_> )  <  <
 *   Firmware   |____|_  /\____/ \___  >__|_ \|___ /\____/__\/\_ \
 *                     \/            \/     \/    \/            \/
 *
 * Copyright (C) 2010 Thomas Martitz
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 ****************************************************************************/
package org.rockbox;

import java.nio.ByteBuffer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewConfiguration;

public class RockboxFramebuffer extends SurfaceView
                                 implements SurfaceHolder.Callback
{
    /* Keep these in sync with the Android workflow's --lcdwidth/--lcdheight. */
    private static final int LCD_WIDTH = 1080;
    private static final int LCD_HEIGHT = 1920;

    private final DisplayMetrics metrics;
    private final ViewConfiguration view_config;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private Bitmap btm;
    private float contentScale = 1.0f;
    private float contentLeft = 0.0f;
    private float contentTop = 0.0f;

    /* first stage init; needs to run from a thread that has a Looper
     * setup stuff that needs a Context */
    public RockboxFramebuffer(Context c)
    {
        super(c);
        metrics = c.getResources().getDisplayMetrics();
        view_config = ViewConfiguration.get(c);
        setBackgroundColor(Color.BLACK);
        updateContentRect(metrics.widthPixels, metrics.heightPixels);
        getHolder().addCallback(this);
        /* Needed so we can catch KeyEvents */
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        /* don't draw until native is ready (2nd stage) */
        setEnabled(false);
    }

    private void updateContentRect(int width, int height)
    {
        if (width <= 0 || height <= 0)
            return;
        contentScale = Math.min((float) width / LCD_WIDTH,
                                (float) height / LCD_HEIGHT);
        contentLeft = (width - LCD_WIDTH * contentScale) / 2.0f;
        contentTop = (height - LCD_HEIGHT * contentScale) / 2.0f;
    }

    private void drawFramebuffer(Canvas canvas)
    {
        canvas.drawColor(Color.BLACK);
        if (btm != null)
        {
            RectF destination = new RectF(contentLeft, contentTop,
                    contentLeft + LCD_WIDTH * contentScale,
                    contentTop + LCD_HEIGHT * contentScale);
            canvas.drawBitmap(btm, null, destination, bitmapPaint);
        }
    }

    private void update(ByteBuffer framebuffer)
    {
        SurfaceHolder holder = getHolder();
        Canvas canvas = holder.lockCanvas();
        if (canvas == null)
            return;

        try
        {
            if (btm == null)
                return;
            btm.copyPixelsFromBuffer(framebuffer);
            synchronized (holder)
            {
                drawFramebuffer(canvas);
            }
        }
        finally
        {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void update(ByteBuffer framebuffer, Rect dirty)
    {
        /* The native dirty rectangle uses logical Rockbox pixels. Redraw the
         * full scaled frame so Canvas clipping cannot mix logical and device
         * coordinates. The 1080x1920 framebuffer is small enough for this. */
        update(framebuffer);
    }

    public boolean onTouchEvent(MotionEvent me)
    {
        int x = Math.round((me.getX() - contentLeft) / contentScale);
        int y = Math.round((me.getY() - contentTop) / contentScale);

        switch (me.getAction())
        {
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
            touchHandler(false, x, y);
            return true;
        case MotionEvent.ACTION_MOVE:
        case MotionEvent.ACTION_DOWN:
            touchHandler(true, x, y);
            return true;
        }

        return false;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        return buttonHandler(keyCode, true);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        return buttonHandler(keyCode, false);
    }

    private int getDpi()
    {
        return metrics.densityDpi;
    }

    private int getScrollThreshold()
    {
        return view_config.getScaledTouchSlop();
    }

    private native void framebufferReady();

    private native void touchHandler(boolean down, int x, int y);
    public native static boolean buttonHandler(int keycode, boolean state);

    public native void surfaceCreated(SurfaceHolder holder);
    public native void surfaceDestroyed(SurfaceHolder holder);

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        updateContentRect(width, height);
        btm = Bitmap.createBitmap(LCD_WIDTH, LCD_HEIGHT, Bitmap.Config.RGB_565);
        setEnabled(true);
        framebufferReady();
    }
}
