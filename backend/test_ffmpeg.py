#!/usr/bin/env python3
import os
import sys
import subprocess
import urllib.request
import tempfile

def log_banner(msg):
    print("=" * 60)
    print(msg.center(60))
    print("=" * 60)

def download_sample_video(dest_path):
    # Standard high-speed sample video on Google Cloud Storage (12-second clip)
    sample_url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
    print(f"[*] Downloading HD sample video from: {sample_url}")
    try:
        urllib.request.urlretrieve(sample_url, dest_path)
        print(f"[+] Download complete! Saved to {dest_path} ({os.path.getsize(dest_path)} bytes)")
        return True
    except Exception as e:
        print(f"[-] Failed to download sample video: {e}")
        return False

def verify_ffmpeg_installation():
    print("[*] Verifying FFmpeg and FFprobe binary availability...")
    try:
        ffmpeg_version = subprocess.run(["ffmpeg", "-version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        print("[+] FFmpeg is installed:")
        print("    " + ffmpeg_version.stdout.split("\n")[0])
    except FileNotFoundError:
        print("[-] Error: 'ffmpeg' binary was not found on your system path.")
        print("    Please install FFmpeg: e.g. 'sudo apt-get install ffmpeg' or 'brew install ffmpeg'")
        return False

    try:
        ffprobe_version = subprocess.run(["ffprobe", "-version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        print("[+] FFprobe is installed:")
        print("    " + ffprobe_version.stdout.split("\n")[0])
    except FileNotFoundError:
        print("[-] Warning: 'ffprobe' not found. Playability checks will be limited to file headers.")
    
    return True

def run_trim_command(input_file, output_file, start_sec=2, duration_sec=5):
    print(f"[*] Extracting a {duration_sec}-second segment starting at second {start_sec}...")
    
    # Standard FFmpeg command using fast input stream seeking (-ss before -i)
    # Re-encodes using standard libx264/aac stream vectors to guarantee container health
    cmd = [
        "ffmpeg", "-y",
        "-ss", str(start_sec),
        "-i", input_file,
        "-t", str(duration_sec),
        "-c:v", "libx264",
        "-c:a", "aac",
        "-strict", "experimental",
        output_file
    ]
    
    print(f"[*] Running command: {' '.join(cmd)}")
    result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    
    if result.returncode == 0:
        print("[+] FFmpeg execution completed successfully!")
        return True
    else:
        print("[-] FFmpeg command failed with exit code:", result.returncode)
        print("--- FFmpeg Error Output ---")
        print(result.stderr)
        print("---------------------------")
        return False

def verify_out_file(output_file):
    print("[*] Verifying physical output file properties...")
    if not os.path.exists(output_file):
        print("[-] CRITICAL ERROR: Output file was never created!")
        return False
        
    size_bytes = os.path.getsize(output_file)
    print(f"[+] File physical existence check: SUCCESS! (Path: {output_file})")
    print(f"[+] File size: {size_bytes} bytes ({size_bytes / 1024:.2f} KB)")
    
    if size_bytes == 0:
        print("[-] CRITICAL ERROR: File has 0 bytes! Cutting was empty.")
        return False
        
    # Check if we can probe it with ffprobe for absolute validation
    try:
        probe_cmd = [
            "ffprobe", "-v", "error",
            "-show_entries", "format=duration:stream=codec_type,codec_name",
            "-of", "default=noprint_wrappers=1",
            output_file
        ]
        probe_res = subprocess.run(probe_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if probe_res.returncode == 0:
            print("[+] FFprobe playability analysis: SUCCESS!")
            print("--- Output stream specs ---")
            print(probe_res.stdout.strip())
            print("---------------------------")
            return True
        else:
            print("[-] FFprobe validation failed. File may be corrupted or unplayable.")
            print(probe_res.stderr)
            return False
    except FileNotFoundError:
        # Fallback raw byte inspection
        try:
            with open(output_file, 'rb') as f:
                header = f.read(16)
                # Verify standard MP4 signatures (ftyp)
                if b'ftyp' in header:
                    print("[+] Raw header inspection: Matches MP4 container (SUCCESS)")
                    return True
                else:
                    print("[-] Raw header inspection: Missing 'ftyp' token in first 16 bytes. File may be incomplete.")
                    return False
        except Exception as e:
            print(f"[-] Raw header check failed: {e}")
            return False

def main():
    log_banner("Clipz-Stream FFmpeg Diagnostics")
    
    if not verify_ffmpeg_installation():
        sys.exit(1)
        
    # Set up temp working files
    with tempfile.TemporaryDirectory() as tmp_dir:
        input_file = os.path.join(tmp_dir, "input_source.mp4")
        output_file = os.path.join(tmp_dir, "trimmed_output.mp4")
        
        # Accept custom user file as argument
        if len(sys.argv) > 1:
            custom_input = sys.argv[1]
            if os.path.exists(custom_input):
                print(f"[+] Using user provided input file: {custom_input}")
                input_file = custom_input
            else:
                print(f"[-] Error: Provided input file path does not exist: {custom_input}")
                sys.exit(1)
        else:
            if not download_sample_video(input_file):
                print("[-] Could not retrieve sample video asset. Diagnostics aborted.")
                sys.exit(1)
                
        # Perform segment trim
        success = run_trim_command(input_file, output_file)
        
        if success:
            valid = verify_out_file(output_file)
            if valid:
                log_banner("DIAGNOSTICS PASSED")
                print("[+] Your backend environment has fully functional FFmpeg cutting capabilities.")
                print("[+] The exported file is physically valid, structured, and playable.")
                sys.exit(0)
            
        log_banner("DIAGNOSTICS FAILED")
        print("[-] The cutting process did not successfully generate a playable/valid MP4 file.")
        sys.exit(1)

if __name__ == "__main__":
    main()
