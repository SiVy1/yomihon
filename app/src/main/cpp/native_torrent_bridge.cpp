#include <jni.h>
#include <string>
#include <sstream>

namespace {

std::string jstringToString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return "";
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars == nullptr ? "" : chars);
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

std::string escapeJson(const std::string& value) {
    std::ostringstream stream;
    for (const char character : value) {
        switch (character) {
            case '\\':
                stream << "\\\\";
                break;
            case '"':
                stream << "\\\"";
                break;
            case '\n':
                stream << "\\n";
                break;
            case '\r':
                stream << "\\r";
                break;
            case '\t':
                stream << "\\t";
                break;
            default:
                stream << character;
                break;
        }
    }
    return stream.str();
}

std::string ensureExtension(const std::string& input, const std::string& fallbackExtension) {
    if (input.find('.') != std::string::npos) {
        return input;
    }
    return input + fallbackExtension;
}

std::string lastPathSegment(const std::string& input) {
    const auto index = input.find_last_of("/\\");
    if (index == std::string::npos) {
        return input;
    }
    return input.substr(index + 1);
}

std::string buildSessionId(
    const std::string& infoHash,
    const std::string& magnetUri,
    const std::string& torrentUrl,
    const std::string& storageDirectory
) {
    if (!infoHash.empty()) {
        return infoHash;
    }
    if (!magnetUri.empty()) {
        return "magnet-" + std::to_string(std::hash<std::string>{}(magnetUri));
    }
    if (!torrentUrl.empty()) {
        return "torrent-" + std::to_string(std::hash<std::string>{}(torrentUrl));
    }
    return "storage-" + std::to_string(std::hash<std::string>{}(storageDirectory));
}

}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_eu_kanade_tachiyomi_ui_anime_player_NativeTorrentJni_prepareSessionJson(
    JNIEnv* env,
    jclass,
    jstring magnetUri,
    jstring torrentUrl,
    jstring infoHash,
    jstring storageDirectory,
    jstring displayName,
    jlong sizeBytes,
    jstring subtitleHint
) {
    const std::string magnet = jstringToString(env, magnetUri);
    const std::string torrent = jstringToString(env, torrentUrl);
    const std::string hash = jstringToString(env, infoHash);
    const std::string directory = jstringToString(env, storageDirectory);
    const std::string name = lastPathSegment(jstringToString(env, displayName));
    const std::string subtitle = lastPathSegment(jstringToString(env, subtitleHint));

    const std::string mediaFile = ensureExtension(name.empty() ? "episode" : name, ".mkv");
    const std::string subtitleFile = ensureExtension(subtitle.empty() ? "subtitle" : subtitle, ".ass");
    const std::string sessionId = buildSessionId(hash, magnet, torrent, directory);

    std::ostringstream json;
    json << "{";
    json << "\"sessionId\":\"" << escapeJson(sessionId) << "\",";
    json << "\"proxyUrl\":null,";
    json << "\"files\":[";
    json << "{";
    json << "\"id\":\"video-hint\",";
    json << "\"path\":\"" << escapeJson(mediaFile) << "\",";
    json << "\"sizeBytes\":" << sizeBytes;
    json << "},";
    json << "{";
    json << "\"id\":\"subtitle-hint\",";
    json << "\"path\":\"" << escapeJson(subtitleFile) << "\"";
    json << "}";
    json << "]";
    json << "}";

    const std::string payload = json.str();
    return env->NewStringUTF(payload.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_eu_kanade_tachiyomi_ui_anime_player_NativeTorrentJni_selectVideoFileProxyUrl(
    JNIEnv*,
    jclass,
    jstring,
    jstring
) {
    return nullptr;
}

extern "C"
JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_ui_anime_player_NativeTorrentJni_selectSubtitleTrack(
    JNIEnv*,
    jclass,
    jstring,
    jstring
) {
}

extern "C"
JNIEXPORT void JNICALL
Java_eu_kanade_tachiyomi_ui_anime_player_NativeTorrentJni_stopSession(
    JNIEnv*,
    jclass,
    jstring
) {
}
