#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>

// Compact exact-host / URL-substring matcher. Feed normalized EasyList-derived rules from app storage.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_geckobrowser_privacy_NativeFilter_nativeIsBlocked(JNIEnv* env, jobject, jstring url, jobjectArray rules) {
    const char* raw = env->GetStringUTFChars(url, nullptr);
    std::string target(raw ? raw : "");
    env->ReleaseStringUTFChars(url, raw);
    const jsize n = env->GetArrayLength(rules);
    for (jsize i = 0; i < n; ++i) {
        auto rule = (jstring) env->GetObjectArrayElement(rules, i);
        const char* p = env->GetStringUTFChars(rule, nullptr);
        std::string needle(p ? p : "");
        env->ReleaseStringUTFChars(rule, p);
        env->DeleteLocalRef(rule);
        if (!needle.empty() && target.find(needle) != std::string::npos) return JNI_TRUE;
    }
    return JNI_FALSE;
}
