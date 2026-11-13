/***************************************************************************
 *             __________               __   ___.
 *   Open      \______   \ ____   ____ |  | _\_ |__   _______  ___
 *   Source     |       _//  _ \_/ ___\|  |/ /| __ \ /  _ \  \/  /
 *   Jukebox    |    |   (  <_> )  \___|    < | \_\ (  <_> > <  <
 *   Firmware   |____|_  /\____/ \___  >__|_ \|___  /\____/__/\_ \
 *                     \/            \/     \/    \/            \/
 *
 * Copyright (C) 2010 by Thomas Martitz
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY
 * KIND, either express or implied.
 *
 ****************************************************************************/


#include <jni.h>
#include <stdbool.h>
#include "config.h"

extern JNIEnv *env_ptr;
extern jclass  RockboxService_class;
extern jobject RockboxService_instance;

static jfieldID __battery_level;
static jobject BatteryMonitor_instance;

static bool clear_jni_exception(JNIEnv *env)
{
    if (!(*env)->ExceptionCheck(env))
        return false;

    (*env)->ExceptionDescribe(env);
    (*env)->ExceptionClear(env);
    return true;
}

/* Called directly from the Java JNI entry point, before Rockbox starts its
 * cooperative threads. Constructing this Android object later from a
 * Rockbox thread can exhaust the VM's Java stack while entering the
 * BroadcastReceiver constructor on Android. */
void android_battery_init(void)
{
    JNIEnv *env = env_ptr;
    if (!env || !RockboxService_instance)
        return;

    jclass cls = (*env)->FindClass(env, "org/rockbox/monitors/BatteryMonitor");
    if (clear_jni_exception(env) || !cls)
        return;

    jmethodID constructor = (*env)->GetMethodID(env, cls, "<init>",
                                                "(Landroid/content/Context;)V");
    if (clear_jni_exception(env) || !constructor)
    {
        (*env)->DeleteLocalRef(env, cls);
        return;
    }

    jobject instance = (*env)->NewObject(env, cls, constructor,
                                          RockboxService_instance);
    if (clear_jni_exception(env) || !instance)
    {
        (*env)->DeleteLocalRef(env, cls);
        return;
    }

    jfieldID level = (*env)->GetFieldID(env, cls, "mBattLevel", "I");
    if (clear_jni_exception(env) || !level)
    {
        (*env)->DeleteLocalRef(env, instance);
        (*env)->DeleteLocalRef(env, cls);
        return;
    }

    BatteryMonitor_instance = instance;
    __battery_level = level;
    (*env)->DeleteLocalRef(env, cls);
}

int _battery_level(void)
{
    if (!BatteryMonitor_instance || !__battery_level)
        return -1;

    return (*env_ptr)->GetIntField(env_ptr, BatteryMonitor_instance, __battery_level);
}
