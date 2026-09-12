# -*- coding: utf-8 -*-
import os, zipfile
root = r'.'
out = r'..\ADBToolboxGlass-v2.3-source.zip'
exclude_dirs = {'build', '.gradle', '.kotlin', '.git', '.idea', 'iosApp'}
exclude_exts = {'.log', '.txt'}
exclude_files = {'local.properties'}
def walk(base):
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames[:] = [d for d in dirnames if d not in exclude_dirs and not d.endswith('.build')]
        for fn in filenames:
            if fn in exclude_files: continue
            if os.path.splitext(fn)[1].lower() in exclude_exts: continue
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, base)
            yield full, rel
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zf:
    for full, rel in walk(root):
        zf.write(full, os.path.join('ADBToolboxGlass', rel))
print('done')
