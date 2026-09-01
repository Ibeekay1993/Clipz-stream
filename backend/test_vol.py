import modal
import os
app = modal.App('test-vol')
vol = modal.Volume.from_name('clipz-shared-storage')
@app.function(volumes={'/root/storage': vol})
def list_clips():
    try:
        vol.reload()
        return os.listdir('/root/storage/clips')
    except Exception as e:
        return str(e)
@app.local_entrypoint()
def main():
    print('Files in clips:', list_clips.remote())
