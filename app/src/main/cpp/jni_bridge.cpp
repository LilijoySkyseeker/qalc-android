// JNI entry points for io.github.lilijoyskyseeker.qalc.engine.Native.
#include "bridge_core.h"

#include <jni.h>

namespace {

std::string to_string(JNIEnv *env, jstring s) {
	const char *chars = env->GetStringUTFChars(s, nullptr);
	std::string out(chars);
	env->ReleaseStringUTFChars(s, chars);
	return out;
}

jobjectArray to_array(JNIEnv *env, const std::vector<std::string> &items) {
	jobjectArray array = env->NewObjectArray((jsize) items.size(), env->FindClass("java/lang/String"), nullptr);
	for(size_t i = 0; i < items.size(); i++) {
		jstring s = env->NewStringUTF(items[i].c_str());
		env->SetObjectArrayElement(array, (jsize) i, s);
		env->DeleteLocalRef(s);
	}
	return array;
}

}  // namespace

#define JNI_FN(name) Java_io_github_lilijoyskyseeker_qalc_engine_Native_##name

extern "C" {

JNIEXPORT void JNICALL JNI_FN(init)(JNIEnv *env, jobject, jstring userDir) {
	engine_init(to_string(env, userDir));
}

JNIEXPORT jobject JNICALL JNI_FN(calculate)(JNIEnv *env, jobject, jstring expr, jint timeoutMs) {
	CalcResult r = engine_calculate(to_string(env, expr), timeoutMs);
	jclass cls = env->FindClass("io/github/lilijoyskyseeker/qalc/engine/CalcResult");
	jmethodID ctor = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;ZZ)V");
	return env->NewObject(cls, ctor, env->NewStringUTF(r.text.c_str()), env->NewStringUTF(r.parsed.c_str()),
	                      to_array(env, r.messages), (jboolean) r.ok, (jboolean) r.aborted);
}

JNIEXPORT void JNICALL JNI_FN(abort)(JNIEnv *, jobject) {
	engine_abort();
}

JNIEXPORT void JNICALL JNI_FN(apply)(JNIEnv *env, jobject, jintArray values) {
	jint v[6];
	env->GetIntArrayRegion(values, 0, 6, v);
	engine_apply(EngineSettings{v[0], v[1], v[2], v[3], v[4], v[5]});
}

JNIEXPORT jboolean JNICALL JNI_FN(saveDefinitions)(JNIEnv *, jobject) {
	return engine_save_definitions();
}

// Flattened as [url0, path0, url1, path1, ...].
JNIEXPORT jobjectArray JNICALL JNI_FN(rateSources)(JNIEnv *env, jobject) {
	std::vector<std::string> flat;
	for(const RateSource &s : engine_rate_sources()) {
		flat.push_back(s.url);
		flat.push_back(s.path);
	}
	return to_array(env, flat);
}

JNIEXPORT jboolean JNICALL JNI_FN(reloadRates)(JNIEnv *, jobject) {
	return engine_reload_rates();
}

}
