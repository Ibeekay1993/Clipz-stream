import os
import logging
from moviepy.editor import VideoFileClip

# Configure logging for clipper service
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("clipper_service")

def log_video_metadata(source_path: str):
    """
    Logs metadata of the input video including format, duration, size, fps, and codec using MoviePy.
    """
    try:
        logger.info(f"[*] Fetching video metadata for source: '{source_path}'")
        with VideoFileClip(source_path) as video:
            duration = video.duration
            size = video.size
            fps = video.fps
            codec = getattr(video.reader, 'codec', 'unknown_codec')
            logger.info(f"[+] Video metadata loaded successfully from MoviePy:")
            logger.info(f"    - Duration: {duration}s")
            logger.info(f"    - Resolution: {size[0]}x{size[1]}" if (size and len(size) >= 2) else f"    - Size: {size}")
            logger.info(f"    - FPS: {fps}")
            logger.info(f"    - Codec: {codec}")
    except Exception as e:
        logger.warning(f"[-] Could not read video metadata using MoviePy: {e}")

def extract_clip(source_path: str, output_path: str, start: float, end: float) -> str:
    """
    Extracts a subclip using MoviePy's VideoFileClip.
    :param source_path: Path to the original full-length video file on disk.
    :param output_path: Destination path where the trimmed clip should be saved.
    :param start: Float representing the subclip's start time in seconds.
    :param end: Float representing the subclip's end time in seconds.
    :return: The verified path to the newly generated MP4 clip.
    """
    logger.info(f"[*] Starting extract_clip: Trim range is {start}s to {end}s.")
    logger.info(f"[*] Source path: '{source_path}', Output path: '{output_path}'")

    # Run the metadata helper to verify we read the source video correctly
    log_video_metadata(source_path)

    # Ensure output directory is present
    out_dir = os.path.dirname(output_path)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)

    try:
        # Use MoviePy's VideoFileClip to extract and encode the specified subsegment
        logger.info(f"[*] Opening VideoFileClip for source: '{source_path}'")
        with VideoFileClip(source_path) as video:
            subclip = video.subclip(start, end)
            logger.info("[*] Extracting subclip and calling write_videofile...")
            subclip.write_videofile(
                output_path,
                codec="libx264",
                audio_codec="aac",
                temp_audiofile=os.path.join(out_dir, "temp-audio.m4a") if out_dir else "temp-audio.m4a",
                remove_temp=True,
                logger=None  # Disable MoviePy's verbose stdout logger to prevent log pollution
            )
        logger.info("[+] MoviePy write_videofile has successfully completed execution.")
    except Exception as e:
        logger.error(f"[-] MoviePy extraction or write_videofile failed with error: {e}")
        raise e

    if not os.path.exists(output_path):
        raise FileNotFoundError(f"MoviePy completed, but output file was not found at {output_path}")

    file_size = os.path.getsize(output_path)
    logger.info(f"[+] MoviePy: Trim success! File created at {output_path} ({file_size} bytes)")
    return output_path

def generate_clip(source_path: str, start_time: float, end_time: float, output_path: str) -> str:
    """
    Backward-compatible wrapper for extract_clip.
    """
    return extract_clip(source_path, output_path, start_time, end_time)

