// JNI bridge between Q Lyrics (WhisperNative.kt) and whisper.cpp: transcribes 16 kHz mono audio and returns every word with its time.
#include <jni.h>
#include <atomic>
#include <string>
#include "whisper.h"

static std::atomic<bool> g_cancel{false};

extern "C" JNIEXPORT jlong JNICALL Java_com_qbdlx_lyrics_WhisperNative_init(JNIEnv* env, jobject, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context* ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL Java_com_qbdlx_lyrics_WhisperNative_free(JNIEnv*, jobject, jlong h) {
    if (h) whisper_free(reinterpret_cast<whisper_context*>(h));
}

extern "C" JNIEXPORT void JNICALL Java_com_qbdlx_lyrics_WhisperNative_cancel(JNIEnv*, jobject) { g_cancel = true; }

// Returns "lang=<code>\n" followed by one "<startMs>\t<endMs>\t<word>\n" per word; "" on failure or when cancelled.
extern "C" JNIEXPORT jstring JNICALL Java_com_qbdlx_lyrics_WhisperNative_transcribe(JNIEnv* env, jobject, jlong h, jfloatArray pcm, jstring lang, jint threads) {
    auto* ctx = reinterpret_cast<whisper_context*>(h);
    if (!ctx) return env->NewStringUTF("");
    g_cancel = false;
    jsize n = env->GetArrayLength(pcm);
    jfloat* samples = env->GetFloatArrayElements(pcm, nullptr);
    const char* lg = env->GetStringUTFChars(lang, nullptr);

    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.n_threads = threads > 0 ? threads : 4;
    wp.language = lg;            // "auto" detects it
    wp.translate = false;
    wp.print_progress = wp.print_realtime = wp.print_timestamps = wp.print_special = false;
    wp.no_context = true;
    wp.token_timestamps = true;  // word times: one segment per word
    wp.split_on_word = true;
    wp.max_len = 1;
    wp.abort_callback = [](void*) { return g_cancel.load(); };
    wp.abort_callback_user_data = nullptr;

    std::string out;
    int rc = whisper_full(ctx, wp, samples, n);
    if (rc == 0 && !g_cancel) {
        out += "lang="; out += whisper_lang_str(whisper_full_lang_id(ctx)); out += "\n";
        int segs = whisper_full_n_segments(ctx);
        for (int i = 0; i < segs; i++) {
            out += std::to_string(whisper_full_get_segment_t0(ctx, i) * 10) + "\t" + std::to_string(whisper_full_get_segment_t1(ctx, i) * 10) + "\t";
            for (const char* c = whisper_full_get_segment_text(ctx, i); *c; c++) out += (*c == '\n' || *c == '\t') ? ' ' : *c;
            out += "\n";
        }
    }
    env->ReleaseStringUTFChars(lang, lg);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    return env->NewStringUTF(out.c_str());
}
