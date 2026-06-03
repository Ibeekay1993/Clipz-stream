import os
from moviepy.editor import VideoFileClip

def generate_clip(source_path: str, start_time: float, end_time: float, output_path: str) -> str:
    """
    Generates a physical subclip of a video using MoviePy.
    :param source_path: Path to the original full-length video file on disk.
    :param start_time: Float representing the subclip's start time in seconds.
    :param end_time: Float representing the subclip's end time in seconds.
    :param output_path: Destination path where the trimmed clip should be saved.
    :return: The verified path to the newly generated MP4 clip.
    """
    print(f"[*] MoviePy: Trimming video '{source_path}' from {start_time}s to {end_time}s")
    print(f"[*] Target subclip path: {output_path}")

    # Ensure output directory is present
    out_dir = os.path.dirname(output_path)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)

    # Use MoviePy's VideoFileClip to extract and encode the specified subsegment
    with VideoFileClip(source_path) as video:
        subclip = video.subclip(start_time, end_time)
        subclip.write_videofile(
            output_path,
            codec="libx264",
            audio_codec="aac",
            temp_audiofile=os.path.join(out_dir, "temp-audio.m4a") if out_dir else "temp-audio.m4a",
            remove_temp=True,
            logger=None  # Disable MoviePy's verbose stdout logger to prevent log pollution
        )

    if not os.path.exists(output_path):
        raise FileNotFoundError(f"MoviePy completed, but output file was not found at {output_path}")

    file_size = os.path.getsize(output_path)
    print(f"[+] MoviePy: Trim success! File created at {output_path} ({file_size} bytes)")
    return output_path
