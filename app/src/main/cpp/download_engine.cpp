#include "download_engine.h"
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <sstream>
#include <fstream>
#include <chrono>
#include <algorithm>

#define LOG_TAG "OrionNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace orion {

constexpr size_t BUFFER_SIZE = 65536;
constexpr int CONNECT_TIMEOUT = 10;

DownloadEngine::DownloadEngine()
    : is_downloading_(false)
    , is_paused_(false)
    , should_cancel_(false)
    , total_bytes_(0)
    , downloaded_bytes_(0)
    , current_speed_(0.0)
    , num_connections_(8) {
    LOGI("DownloadEngine created (HTTP-only, no SSL)");
}

DownloadEngine::~DownloadEngine() {
    cancelDownload();
}

static bool parseUrl(const std::string& url, std::string& host, std::string& path, 
                     int& port, bool& is_https) {
    is_https = (url.find("https://") == 0);
    
    if (is_https) {
        LOGE("HTTPS not supported in C++ engine, use Kotlin engine instead");
        return false;
    }
    
    size_t start = 7;
    
    if (url.length() <= start) {
        return false;
    }
    
    port = 80;
    
    size_t path_pos = url.find('/', start);
    if (path_pos == std::string::npos) {
        host = url.substr(start);
        path = "/";
    } else {
        host = url.substr(start, path_pos - start);
        path = url.substr(path_pos);
    }
    
    size_t port_pos = host.find(':');
    if (port_pos != std::string::npos) {
        port = std::stoi(host.substr(port_pos + 1));
        host = host.substr(0, port_pos);
    }
    
    return true;
}

static int createConnection(const std::string& host, int port) {
    int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) {
        LOGE("Failed to create socket");
        return -1;
    }

    struct timeval timeout;
    timeout.tv_sec = CONNECT_TIMEOUT;
    timeout.tv_usec = 0;
    setsockopt(sockfd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(sockfd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

    int flag = 1;
    setsockopt(sockfd, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));

    struct hostent* server = gethostbyname(host.c_str());
    if (server == nullptr) {
        LOGE("Failed to resolve host: %s", host.c_str());
        close(sockfd);
        return -1;
    }

    struct sockaddr_in serv_addr;
    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    memcpy(&serv_addr.sin_addr.s_addr, server->h_addr, server->h_length);
    serv_addr.sin_port = htons(port);

    if (connect(sockfd, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) < 0) {
        LOGE("Failed to connect to %s:%d", host.c_str(), port);
        close(sockfd);
        return -1;
    }

    LOGD("Connected to %s:%d", host.c_str(), port);
    return sockfd;
}

static bool sendRequest(int sockfd, const std::string& request) {
    size_t total_sent = 0;
    while (total_sent < request.length()) {
        ssize_t sent = send(sockfd, request.c_str() + total_sent, 
                           request.length() - total_sent, MSG_NOSIGNAL);
        if (sent <= 0) {
            LOGE("Failed to send request");
            return false;
        }
        total_sent += sent;
    }
    return true;
}

static std::string receiveHeaders(int sockfd) {
    std::string headers;
    char buffer[1];
    std::string delimiter = "\r\n\r\n";
    
    while (headers.find(delimiter) == std::string::npos) {
        ssize_t received = recv(sockfd, buffer, 1, 0);
        if (received <= 0) {
            break;
        }
        headers += buffer[0];
        
        if (headers.length() > 16384) {
            break;
        }
    }
    
    return headers;
}

int64_t DownloadEngine::getContentLength(const std::string& url) {
    std::string host, path;
    int port;
    bool is_https;
    
    if (!parseUrl(url, host, path, port, is_https)) {
        return -1;
    }

    int sockfd = createConnection(host, port);
    if (sockfd < 0) {
        return -1;
    }

    std::ostringstream request;
    request << "HEAD " << path << " HTTP/1.1\r\n"
            << "Host: " << host << "\r\n"
            << "User-Agent: Orion-Downloader/1.0\r\n"
            << "Connection: close\r\n"
            << "\r\n";

    if (!sendRequest(sockfd, request.str())) {
        close(sockfd);
        return -1;
    }

    std::string headers = receiveHeaders(sockfd);
    close(sockfd);
    
    std::transform(headers.begin(), headers.end(), headers.begin(), ::tolower);
    size_t pos = headers.find("content-length:");
    
    if (pos != std::string::npos) {
        size_t end = headers.find("\r\n", pos);
        std::string length_str = headers.substr(pos + 15, end - pos - 15);
        length_str.erase(0, length_str.find_first_not_of(" \t"));
        return std::stoll(length_str);
    }

    return -1;
}

bool DownloadEngine::supportsRangeRequests(const std::string& url) {
    std::string host, path;
    int port;
    bool is_https;
    
    if (!parseUrl(url, host, path, port, is_https)) {
        return false;
    }

    int sockfd = createConnection(host, port);
    if (sockfd < 0) {
        return false;
    }

    std::ostringstream request;
    request << "HEAD " << path << " HTTP/1.1\r\n"
            << "Host: " << host << "\r\n"
            << "User-Agent: Orion-Downloader/1.0\r\n"
            << "Connection: close\r\n"
            << "\r\n";

    if (!sendRequest(sockfd, request.str())) {
        close(sockfd);
        return false;
    }

    std::string headers = receiveHeaders(sockfd);
    close(sockfd);
    
    std::transform(headers.begin(), headers.end(), headers.begin(), ::tolower);

    return headers.find("accept-ranges: bytes") != std::string::npos;
}

bool DownloadEngine::initializeDownload(const std::string& url) {
    int64_t content_length = getContentLength(url);
    if (content_length <= 0) {
        LOGE("Failed to get content length");
        return false;
    }

    total_bytes_.store(content_length);
    downloaded_bytes_.store(0);

    bool supports_ranges = supportsRangeRequests(url);
    int actual_connections = supports_ranges ? num_connections_ : 1;

    LOGI("Content: %lld bytes, Connections: %d, HTTP-only", 
         (long long)content_length, actual_connections);

    chunks_.clear();
    
    if (actual_connections == 1) {
        chunks_.push_back({0, content_length - 1, 0, false});
    } else {
        int64_t chunk_size = content_length / actual_connections;
        for (int i = 0; i < actual_connections; ++i) {
            int64_t start = i * chunk_size;
            int64_t end = (i == actual_connections - 1) ? 
                         content_length - 1 : (start + chunk_size - 1);
            chunks_.push_back({start, end, 0, false});
        }
    }

    return true;
}

void DownloadEngine::downloadChunk(int chunk_id, const std::string& url, 
                                   const std::string& output_path) {
    if (chunk_id >= static_cast<int>(chunks_.size())) return;

    auto& chunk = chunks_[chunk_id];
    std::string host, path;
    int port;
    bool is_https;
    
    if (!parseUrl(url, host, path, port, is_https)) {
        LOGE("Failed to parse URL for chunk %d", chunk_id);
        return;
    }

    std::string temp_file = output_path + ".part" + std::to_string(chunk_id);
    std::ofstream out(temp_file, std::ios::binary);
    if (!out) {
        LOGE("Failed to open temp file: %s", temp_file.c_str());
        return;
    }

    int sockfd = createConnection(host, port);
    if (sockfd < 0) {
        out.close();
        return;
    }

    std::ostringstream request;
    request << "GET " << path << " HTTP/1.1\r\n"
            << "Host: " << host << "\r\n"
            << "User-Agent: Orion-Downloader/1.0\r\n"
            << "Range: bytes=" << chunk.start << "-" << chunk.end << "\r\n"
            << "Connection: close\r\n"
            << "\r\n";

    if (!sendRequest(sockfd, request.str())) {
        close(sockfd);
        out.close();
        return;
    }

    std::string headers = receiveHeaders(sockfd);
    
    char buffer[BUFFER_SIZE];
    auto start_time = std::chrono::steady_clock::now();
    int64_t chunk_downloaded = 0;

    while (!should_cancel_.load()) {
        while (is_paused_.load() && !should_cancel_.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }

        ssize_t received = recv(sockfd, buffer, BUFFER_SIZE, 0);
        if (received <= 0) break;

        out.write(buffer, received);
        chunk.downloaded += received;
        chunk_downloaded += received;
        downloaded_bytes_.fetch_add(received);

        auto current_time = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
            current_time - start_time).count();
        
        if (elapsed > 0) {
            double speed = (chunk_downloaded * 1000.0) / elapsed;
            current_speed_.store(speed);
        }

        if (progress_callback_ && elapsed > 100) {
            DownloadProgress progress = getProgress();
            progress_callback_(progress);
            start_time = current_time;
            chunk_downloaded = 0;
        }
    }

    close(sockfd);
    out.close();

    if (!should_cancel_.load()) {
        chunk.completed = true;
        LOGD("Chunk %d completed", chunk_id);
    }
}

void DownloadEngine::mergeChunks(const std::string& output_path) {
    std::ofstream out(output_path, std::ios::binary);
    if (!out) {
        LOGE("Failed to open output file: %s", output_path.c_str());
        return;
    }

    for (size_t i = 0; i < chunks_.size(); ++i) {
        std::string temp_file = output_path + ".part" + std::to_string(i);
        std::ifstream in(temp_file, std::ios::binary);
        if (!in) {
            LOGE("Failed to open temp file: %s", temp_file.c_str());
            continue;
        }

        char buffer[BUFFER_SIZE];
        while (in.read(buffer, BUFFER_SIZE) || in.gcount() > 0) {
            out.write(buffer, in.gcount());
        }
        in.close();
        unlink(temp_file.c_str());
    }

    out.close();
    LOGI("Chunks merged successfully");
}

bool DownloadEngine::startDownload(const std::string& url, 
                                   const std::string& output_path,
                                   int num_connections,
                                   ProgressCallback progress_callback) {
    if (is_downloading_.load()) {
        LOGE("Download already in progress");
        return false;
    }

    num_connections_ = std::min(std::max(num_connections, 1), 16);
    progress_callback_ = progress_callback;
    should_cancel_.store(false);
    is_paused_.store(false);

    if (!initializeDownload(url)) {
        return false;
    }

    is_downloading_.store(true);
    worker_threads_.clear();

    for (size_t i = 0; i < chunks_.size(); ++i) {
        worker_threads_.push_back(
            std::make_unique<std::thread>(
                &DownloadEngine::downloadChunk, this, i, url, output_path
            )
        );
    }

    std::thread([this, output_path]() {
        for (auto& thread : worker_threads_) {
            if (thread && thread->joinable()) {
                thread->join();
            }
        }

        if (!should_cancel_.load()) {
            mergeChunks(output_path);
        }

        is_downloading_.store(false);
        LOGI("Download completed");
    }).detach();

    return true;
}

void DownloadEngine::pauseDownload() {
    is_paused_.store(true);
    LOGD("Download paused");
}

void DownloadEngine::resumeDownload() {
    is_paused_.store(false);
    LOGD("Download resumed");
}

void DownloadEngine::cancelDownload() {
    should_cancel_.store(true);
    is_paused_.store(false);
    
    for (auto& thread : worker_threads_) {
        if (thread && thread->joinable()) {
            thread->join();
        }
    }
    
    worker_threads_.clear();
    is_downloading_.store(false);
    LOGD("Download cancelled");
}

DownloadProgress DownloadEngine::getProgress() const {
    DownloadProgress progress;
    progress.downloaded_bytes = downloaded_bytes_.load();
    progress.total_bytes = total_bytes_.load();
    progress.speed_bps = current_speed_.load();
    progress.active_connections = static_cast<int>(chunks_.size());
    return progress;
}

}
