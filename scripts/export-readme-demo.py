"""Export the recorded UI walkthrough as a 30-second MP4 and README GIF."""
import json, subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
artifacts = root/'artifacts/readme-demo'
media = root/'docs/media'
media.mkdir(exist_ok=True)
data = json.loads((artifacts/'recording.json').read_text())
filters=[]
for i, scene in enumerate(data['scenes']):
    # Trim navigation loading, retaining the real interactions and reading holds.
    start = scene['start'] + .15
    filters.append(f"[0:v]trim=start={start:.6f}:duration=5.8,setpts=PTS-STARTPTS,tpad=stop_mode=clone:stop_duration=0.2,fps=30,trim=duration=6[v{i}]")
filters.append(''.join(f'[v{i}]' for i in range(5))+'concat=n=5:v=1:a=0,scale=1728:1080:flags=lanczos,pad=1920:1080:96:0:color=0x191f21,setsar=1[out]')
movie=media/'agentflow-walkthrough.mp4'
subprocess.run(['ffmpeg','-y','-v','error','-i',data['path'],'-filter_complex',';'.join(filters),'-map','[out]','-an','-c:v','libx264','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(movie)],check=True)
subprocess.run(['ffmpeg','-y','-v','error','-i',str(movie),'-filter_complex','fps=8,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer:bayer_scale=3','-loop','0',str(media/'agentflow-walkthrough.gif')],check=True)
subprocess.run(['ffmpeg','-y','-v','error','-ss','1','-i',str(movie),'-frames:v','1',str(media/'agentflow-walkthrough-poster.png')],check=True)
subprocess.run(['ffmpeg','-y','-v','error','-i',str(movie),'-vf','fps=1/3,scale=640:-1,tile=2x5:padding=8:margin=8:color=white','-frames:v','1',str(artifacts/'contact-sheet.jpg')],check=True)
probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(movie)]))
v=probe['streams'][0]
assert (v['width'],v['height'],v['nb_frames'],v['r_frame_rate'])==(1920,1080,'900','30/1')
assert abs(float(probe['format']['duration'])-30)<.02
subprocess.run(['ffmpeg','-v','error','-i',str(movie),'-f','null','-'],check=True)
(artifacts/'probe.json').write_text(json.dumps(probe,indent=2)+'\n')
print('PASS: 30-second actual-app walkthrough, 1920×1080 H.264, 900 frames, clean decode. GIF and poster exported.')
