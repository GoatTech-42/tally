"""Render the silent walkthrough from its timeline and unmodified real captures.

Usage: python render-first-install.py --captures DIR --output DIR
Requires Pillow, ffmpeg and Segoe UI (Windows). No network or speech service.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps
import argparse, json, subprocess, textwrap, sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

parser = argparse.ArgumentParser()
parser.add_argument('--captures', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--timeline', type=Path, default=Path(__file__).with_name('first-install.json'))
parser.add_argument('--name', default='first-install')
parser.add_argument('--heading', default='FIRST INSTALL / START HERE')
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
scenes = json.loads(args.timeline.read_text(encoding='utf-8'))
font = lambda n, bold=False: ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf' if bold else 'C:/Windows/Fonts/segoeui.ttf', n)
BG, PANEL, EDGE, TEXT, MUTED, TEAL, GOLD = '#0b111b', '#162231', '#30465b', '#f0f2f5', '#b5c5d7', '#57dfca', '#f0ca7c'

def lines(draw, value, face, width):
    result, line = [], ''
    for word in value.split():
        candidate = (line + ' ' + word).strip()
        if line and draw.textlength(candidate, font=face) > width:
            result.append(line); line = word
        else: line = candidate
    return result + ([line] if line else [])

def block(draw, value, xy, face, width, color):
    x, y = xy
    for line in lines(draw, value, face, width):
        draw.text((x, y), line, font=face, fill=color); y += face.size + 7
    return y

def stamp(seconds):
    ms = round(seconds * 1000)
    return f'{ms//3600000:02}:{ms//60000%60:02}:{ms//1000%60:02}.{ms%1000:03}'

concat, vtt, chapter_vtt = [], ['WEBVTT', ''], ['WEBVTT', '']
for i, scene in enumerate(scenes):
    frame = Image.new('RGB', (1920, 1080), BG); draw = ImageDraw.Draw(frame)
    draw.text((65, 28), 'GoNuts / ' + args.heading, font=font(24, True), fill=TEAL)
    draw.text((1690, 28), f'{i:02} / {len(scenes)-1:02}', font=font(24), fill=MUTED)
    draw.text((65, 75), scene['title'], font=font(50, True), fill=TEXT)
    draw.text((68, 143), scene['subtitle'], font=font(28), fill=MUTED)
    for n, bullet in enumerate(scene['bullets']):
        y = 230 + n * 189
        draw.rounded_rectangle((65, y, 590, y + 165), radius=18, fill=PANEL, outline=EDGE, width=2)
        draw.text((85, y + 17), f'{n+1:02}', font=font(26, True), fill=TEAL)
        assert block(draw, bullet, (143, y + 18), font(31, True), 420, TEXT) < y + 166
    picture = scene['image']
    draw.rounded_rectangle((625, 215, 1860, 896), radius=18, fill='#060b12', outline=EDGE, width=2)
    if picture in ('fabric', 'folder'):
        draw.text((660, 247), 'VERSION CHECK' if picture == 'fabric' else 'EXAMPLE FOLDER DIAGRAM', font=font(27, True), fill=TEAL)
        items = [('Minecraft Java Edition', '26.2'), ('Fabric Loader + Fabric API', 'matching Minecraft 26.2'), ('Java runtime', '25')] if picture == 'fabric' else [('Your selected installation', 'Open its game folder'), ('mods/', 'GoNuts-0.1.0-mc26.2.jar'), ('mods/', 'matching Fabric API .jar')]
        for j, (label, value) in enumerate(items):
            yy = 330 + j * 153
            draw.text((670, yy), label, font=font(33, True), fill=TEXT)
            draw.text((680, yy + 53), value, font=font(31), fill=GOLD)
        draw.text((660, 849), 'Instructional diagram - not a screenshot of your computer.', font=font(23), fill=MUTED)
    else:
        shot = Image.open(args.captures / picture).convert('RGB')
        shot = ImageOps.contain(shot, (1200, 640), Image.Resampling.LANCZOS)
        frame.paste(shot, (643 + (1200-shot.width)//2, 230 + (640-shot.height)//2))
    draw.text((645, 900), 'PUBLIC WEBSITE' if picture == 'download-page.png' else 'REAL OFFLINE GUI CAPTURE' if picture not in ('fabric', 'folder') else 'INSTALLATION INSTRUCTIONS', font=font(19), fill=MUTED)
    chunks = textwrap.wrap(scene['captionText'], 108, break_long_words=False)
    total_chars = sum(map(len, chunks)); at = scene['start']
    for j, chunk in enumerate(chunks):
        duration = scene['duration'] * len(chunk) / total_chars
        end = at + duration
        captioned = frame.copy(); d = ImageDraw.Draw(captioned)
        d.rectangle((0, 934, 1920, 1067), fill='#050910')
        caption_lines = lines(d, chunk, font(33), 1780)
        assert len(caption_lines) <= 3
        for row, line in enumerate(caption_lines):
            d.text(((1920-d.textlength(line, font=font(33)))/2, 947+row*38), line, font=font(33), fill=TEXT)
        d.rectangle((0, 1071, int(1920*end/(scenes[-1]['start']+scenes[-1]['duration'])), 1080), fill=TEAL)
        name = f'frame-{i:02}-{j:02}.png'; captioned.save(args.output/name)
        concat.extend([f"file '{name}'", f'duration {duration:.6f}'])
        vtt.extend([f'{stamp(at)} --> {stamp(end)}', '\n'.join(textwrap.wrap(chunk, 64, break_long_words=False)), ''])
        if i == 5 and j == 0: captioned.save(args.output/'start-here-chapter.png')
        if i == 0 and j == 0: captioned.save(args.output/(args.name+'-poster.jpg'), quality=92)
        at = end
    chapter_vtt.extend([f'{stamp(scene["start"])} --> {stamp(scene["start"]+scene["duration"])}', scene['title'], ''])
    print(f'{i}: {scene["title"]} at {scene["start"]:.1f}s', flush=True)
concat.append(f"file '{name}'")
(args.output/'frames.txt').write_text('\n'.join(concat)+'\n', encoding='utf-8')
(args.output/(args.name+'.vtt')).write_text('\n'.join(vtt).rstrip()+'\n', encoding='utf-8')
(args.output/(args.name+'-chapters.vtt')).write_text('\n'.join(chapter_vtt).rstrip()+'\n', encoding='utf-8')
duration = scenes[-1]['start']+scenes[-1]['duration']
subprocess.run(['ffmpeg','-hide_banner','-loglevel','warning','-y','-f','concat','-safe','0','-i',str(args.output/'frames.txt'),'-t',str(duration),'-r','24','-an','-c:v','libx264','-threads','2','-preset','veryfast','-crf','22','-pix_fmt','yuv420p','-g','48','-movflags','+faststart',str(args.output/(args.name+'.mp4'))], check=True)
print(f'COMPLETE: {duration:.1f}s, silent 1920x1080 / 24fps', flush=True)
