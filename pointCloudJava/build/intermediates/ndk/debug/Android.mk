LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := JniBitmapOperationsLibrary
LOCAL_LDLIBS := \
	-llog \
	-ljnigraphics \

LOCAL_SRC_FILES := \
	/home/yanhang/StudioProjects/PointCloudJava/pointCloudJava/src/main/jni/JniBitmapOperationsLibrary.cpp \

LOCAL_C_INCLUDES += /home/yanhang/StudioProjects/PointCloudJava/pointCloudJava/src/main/jni
LOCAL_C_INCLUDES += /home/yanhang/StudioProjects/PointCloudJava/pointCloudJava/src/debug/jni

include $(BUILD_SHARED_LIBRARY)
