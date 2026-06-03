import os
import time
import logging

# Configure basic logging for service output tracking
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("cleanup_service")

def cleanup_expired_files(directory: str, max_age_seconds: float = 1800) -> int:
    """
    Cleans up files in the specified directory that are older than max_age_seconds.
    
    :param directory: The physical directory path to scan.
    :param max_age_seconds: Age limit in seconds (default is 1800s / 30 minutes).
    :return: Number of files successfully removed.
    """
    if not os.path.exists(directory):
        logger.warning(f"Cleanup aborted: Directory '{directory}' does not exist.")
        return 0

    now = time.time()
    deleted_count = 0

    logger.info(f"[*] Starting local storage auto-cleanup sweep on '{directory}' (Max age: {max_age_seconds}s)")

    try:
        for filename in os.listdir(directory):
            file_path = os.path.join(directory, filename)
            
            # Skip subdirectories to avoid accidental system file deletions
            if os.path.isdir(file_path):
                continue

            try:
                # Check file modification time to measure true age
                file_mtime = os.path.getmtime(file_path)
                file_age = now - file_mtime

                if file_age > max_age_seconds:
                    logger.info(f"[*] File '{filename}' has aged out ({file_age:.1f}s older than threshold). Deleting...")
                    os.remove(file_path)
                    deleted_count += 1
            except Exception as file_err:
                logger.error(f"[-] Failed to process or delete '{file_path}': {file_err}")

        if deleted_count > 0:
            logger.info(f"[+] Storage auto-cleanup completed. Successfully removed {deleted_count} stale video/temp files.")
        else:
            logger.info("[*] Auto-cleanup complete. No expired files found.")

    except Exception as scan_err:
        logger.error(f"[-] Failed during auto-cleanup directory scan: {scan_err}")

    return deleted_count

def start_background_cleanup_worker(directory: str, max_age_seconds: float = 1800, interval_seconds: float = 300):
    """
    Spawns a background daemon thread that runs periodically to perform cleanup.
    
    :param directory: Target directory to clean.
    :param max_age_seconds: How old a file must be to be eligible for removal.
    :param interval_seconds: Dormant period between sweeps.
    """
    import threading

    def worker_loop():
        logger.info(f"[+] Spawning physical storage cleanup worker daemon thread for '{directory}'")
        while True:
            try:
                cleanup_expired_files(directory, max_age_seconds)
            except Exception as e:
                logger.error(f"[-] Critical exception in background cleanup worker loop: {e}")
            time.sleep(interval_seconds)

    cleanup_thread = threading.Thread(target=worker_loop, daemon=True, name="StorageCleanupWorker")
    cleanup_thread.start()
    return cleanup_thread
