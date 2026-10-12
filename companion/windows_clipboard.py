"""Win32 clipboard adapter. Media files use CF_HDROP; images also use CF_DIB."""
import ctypes as c
import io
import os
import struct
from pathlib import Path
from PIL import Image

class WindowsClipboard:
    def __init__(self):
        if os.name != 'nt':
            raise RuntimeError('Windows is required for native clipboard sync')
        self.u, self.k, self.shell = c.windll.user32, c.windll.kernel32, c.windll.shell32
        self.u.GetClipboardData.restype = c.c_void_p
        self.u.SetClipboardData.argtypes = [c.c_uint, c.c_void_p]
        self.u.SetClipboardData.restype = c.c_void_p
        self.k.GlobalLock.argtypes = [c.c_void_p]; self.k.GlobalLock.restype = c.c_void_p
        self.k.GlobalUnlock.argtypes = [c.c_void_p]
        self.k.GlobalSize.argtypes = [c.c_void_p]; self.k.GlobalSize.restype = c.c_size_t
        self.k.GlobalAlloc.argtypes = [c.c_uint, c.c_size_t]; self.k.GlobalAlloc.restype = c.c_void_p
        self.k.GlobalFree.argtypes = [c.c_void_p]
        self.shell.DragQueryFileW.argtypes = [c.c_void_p, c.c_uint, c.c_wchar_p, c.c_uint]
        self.u.CreateWindowExW.restype = c.c_void_p
        self.window = self.u.CreateWindowExW(0, 'STATIC', 'MorphOS Clipboard', 0, 0, 0, 0, 0, None, None, None, None)
        self.u.OpenClipboard.argtypes = [c.c_void_p]
        self.excluded = self.u.RegisterClipboardFormatW('ExcludeClipboardContentFromMonitorProcessing')
        self.sequence = self.u.GetClipboardSequenceNumber()

    def read_change(self):
        sequence = self.u.GetClipboardSequenceNumber()
        if sequence == self.sequence or not self.u.OpenClipboard(self.window):
            return None
        try:
            self.sequence = sequence
            if self.u.IsClipboardFormatAvailable(self.excluded):
                return None
            handle = self.u.GetClipboardData(15)
            if handle:
                count = self.shell.DragQueryFileW(handle, 0xffffffff, None, 0)
                paths = []
                for i in range(min(count, 10)):
                    length = self.shell.DragQueryFileW(handle, i, None, 0)
                    value = c.create_unicode_buffer(length + 1)
                    self.shell.DragQueryFileW(handle, i, value, length + 1)
                    path = Path(value.value)
                    if path.is_file() and path.suffix.lower() in ('.png','.jpg','.jpeg','.webp','.gif','.bmp','.mp4','.mov','.webm','.mkv') and path.stat().st_size <= 100*1024*1024:
                        paths.append(path)
                return ('files', paths) if paths else None
            for fmt in (8, 13):
                handle = self.u.GetClipboardData(fmt)
                if not handle:
                    continue
                length = self.k.GlobalSize(handle)
                if not 0 < length <= (32*1024*1024 if fmt == 8 else 131072):
                    continue
                pointer = self.k.GlobalLock(handle)
                if not pointer:
                    continue
                try:
                    raw = c.string_at(pointer, length)
                    if fmt == 13:
                        return 'text', raw.decode('utf-16-le').split('\0')[0][:32768]
                    # Pillow understands a packed DIB without a BMP file header.
                    with Image.open(io.BytesIO(raw)) as image:
                        if image.width * image.height > 40_000_000:
                            return None
                        output = io.BytesIO(); image.save(output, 'PNG')
                        return 'image', output.getvalue()
                finally:
                    self.k.GlobalUnlock(handle)
        finally:
            self.u.CloseClipboard()
        return None

    def write(self, kind, value):
        if not self.u.OpenClipboard(self.window):
            raise RuntimeError('Clipboard is busy')
        try:
            self.u.EmptyClipboard()
            def put(fmt, data):
                handle = self.k.GlobalAlloc(0x42, len(data))
                if not handle: raise MemoryError('Clipboard allocation failed')
                pointer = self.k.GlobalLock(handle)
                if not pointer: self.k.GlobalFree(handle); raise MemoryError('Clipboard lock failed')
                c.memmove(pointer, data, len(data)); self.k.GlobalUnlock(handle)
                if not self.u.SetClipboardData(fmt, handle):
                    self.k.GlobalFree(handle); raise RuntimeError('Clipboard write failed')
            if kind == 'text':
                put(13, (value + '\0').encode('utf-16-le'))
            else:
                path = Path(value).resolve()
                put(15, struct.pack('<IiiII', 20, 0, 0, 0, 1) + (str(path) + '\0\0').encode('utf-16-le'))
                if path.suffix.lower() in ('.png','.jpg','.jpeg','.bmp','.webp'):
                    with Image.open(path) as image:
                        if image.width * image.height <= 40_000_000:
                            output = io.BytesIO(); image.convert('RGB').save(output, 'BMP'); put(8, output.getvalue()[14:])
        finally:
            self.u.CloseClipboard()
            self.sequence = self.u.GetClipboardSequenceNumber()
