// language: C++, file: native-lib.cpp, target: Android arm64-v8a, NDK 29
// JNI stub — placeholder for future native preprocessing / inference offload.
#include <jni.h>
#include <android/log.h>

extern "C" JNIEXPORT jint JNICALL
Java_com_vanta_aim_NativeBridge_version(JNIEnv*, jobject) {
    return 1;
}
