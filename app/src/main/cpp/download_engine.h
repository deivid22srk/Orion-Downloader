#ifndef ORION_DOWNLOAD_ENGINE_H
#define ORION_DOWNLOAD_ENGINE_H

#include <string>
#include <vector>
#include <atomic>
#include <memory>
#include <functional>
#include <thread>
#include <mutex>

namespace orion {

struct DownloadProgress {
    int64_t downloaded_bytes;
    int64_t total_bytes;
    double speed_bps;
    int active_connections;
};

using ProgressCallback = std::function<void(const DownloadProgress&)>;

class DownloadEngine {
public:
    DownloadEngine();
    ~DownloadEngine();

    bool startDownload(
        const std::string& url,
        const std::string& output_path,
        int num_connections = 8,
        ProgressCallback progress_callback = nullptr
    );

    void pauseDownload();
    void resumeDownload();
    void cancelDownload();
    
    bool isDownloading() const { return is_downloading_.load(); }
    bool isPaused() const { return is_paused_.load(); }
    
    DownloadProgress getProgress() const;

private:
    struct ChunkInfo {
        int64_t start;
        int64_t end;
        int64_t downloaded;
        bool completed;
    };

    bool initializeDownload(const std::string& url);
    void downloadChunk(int chunk_id, const std::string& url, const std::string& output_path);
    void mergeChunks(const std::string& output_path);
    bool supportsRangeRequests(const std::string& url);
    int64_t getContentLength(const std::string& url);
    
    std::vector<ChunkInfo> chunks_;
    std::vector<std::unique_ptr<std::thread>> worker_threads_;
    
    std::atomic<bool> is_downloading_;
    std::atomic<bool> is_paused_;
    std::atomic<bool> should_cancel_;
    std::atomic<int64_t> total_bytes_;
    std::atomic<int64_t> downloaded_bytes_;
    std::atomic<double> current_speed_;
    
    ProgressCallback progress_callback_;
    mutable std::mutex mutex_;
    
    int num_connections_;
    std::string temp_dir_;
};

}

#endif
