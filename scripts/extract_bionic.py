#!/usr/bin/env python3
"""Extract Bionic linker64 and runtime libraries from Android system image.

Usage: python3 extract_bionic.py <system.raw> <output_dir>

Finds the ext4 filesystem inside the system image, reads it with the
ext4 Python module, and extracts:
- /system/lib64/bootstrap/*.so (Bionic runtime libs)
- com.android.runtime.apex (contains linker64)
Then unzips the APEX and extracts linker64 from its ext4 payload.
"""
import os
import sys
import struct
import zipfile

def find_ext4_offset(path):
    """Find the ext4 superblock magic (0x53EF) in the image."""
    with open(path, 'rb') as f:
        offset = 0
        while offset < 100 * 1024 * 1024:
            f.seek(offset)
            data = f.read(1024)
            if len(data) < 58:
                break
            for i in range(0, len(data) - 58, 4):
                if data[i+56:i+58] == b'\x53\xef':
                    sb_start = offset + i - 56
                    if sb_start >= 0:
                        fs_offset = sb_start - 1024
                        print(f"Found ext4 at offset {fs_offset}")
                        return fs_offset
            offset += 1024
    return None

def extract_ext4(path, output_dir):
    """Extract Bionic libs from an ext4 image using the ext4 module."""
    from ext4 import Volume

    with open(path, 'rb') as f:
        vol = Volume(f)
        root = vol.root

    def get_dir(parent, name):
        for entry, filetype in parent.opendir():
            n = entry.name
            if isinstance(n, bytes):
                n = n.decode('utf-8', errors='replace')
            if n == name:
                return vol.inodes[entry.inode]
        return None

    def extract_inode(inode, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        bio = inode.open()
        data = bio.read(inode.size)
        with open(path, 'wb') as out:
            out.write(data)
        print(f"  Extracted: {path} ({len(data)} bytes)")
        return len(data)

    # Get bootstrap Bionic libs
    system_inode = get_dir(root, 'system')
    if system_inode:
        lib64 = get_dir(system_inode, 'lib64')
        if lib64:
            bootstrap = get_dir(lib64, 'bootstrap')
            if bootstrap:
                print("Found /system/lib64/bootstrap/")
                for entry, filetype in bootstrap.opendir():
                    name = entry.name
                    if isinstance(name, bytes):
                        name = name.decode('utf-8', errors='replace')
                    if name in ('.', '..'):
                        continue
                    inode = vol.inodes[entry.inode]
                    if inode.size > 1000:
                        extract_inode(inode, os.path.join(output_dir, name))

        # Get APEX for linker64
        apex = get_dir(system_inode, 'apex')
        if apex:
            for entry, filetype in apex.opendir():
                name = entry.name
                if isinstance(name, bytes):
                    name = name.decode('utf-8', errors='replace')
                if 'runtime' in name:
                    print(f"Found APEX: {name}")
                    inode = vol.inodes[entry.inode]
                    extract_inode(inode, os.path.join(output_dir, 'runtime.apex'))
                    break

def extract_linker_from_apex(apex_path, output_dir):
    """Extract linker64 from an APEX file."""
    with zipfile.ZipFile(apex_path) as z:
        z.extractall('/tmp/apex_dir/')

    payload = '/tmp/apex_dir/apex_payload.img'
    if not os.path.exists(payload):
        print("No apex_payload.img in APEX")
        return

    from ext4 import Volume
    with open(payload, 'rb') as f:
        vol = Volume(f)
        root = vol.root

    def get_dir(parent, name):
        for entry, filetype in parent.opendir():
            n = entry.name
            if isinstance(n, bytes):
                n = n.decode('utf-8', errors='replace')
            if n == name:
                return vol.inodes[entry.inode]
        return None

    # Extract linker64 from /bin/
    bin_dir = get_dir(root, 'bin')
    if bin_dir:
        for entry, filetype in bin_dir.opendir():
            name = entry.name
            if isinstance(name, bytes):
                name = name.decode('utf-8', errors='replace')
            if 'linker64' in name:
                inode = vol.inodes[entry.inode]
                bio = inode.open()
                data = bio.read(inode.size)
                with open(os.path.join(output_dir, 'linker64'), 'wb') as out:
                    out.write(data)
                print(f"  Extracted: linker64 ({len(data)} bytes)")
                return

    # Also extract Bionic libs from /lib64/bionic/
    lib64 = get_dir(root, 'lib64')
    if lib64:
        bionic = get_dir(lib64, 'bionic')
        if bionic:
            for entry, filetype in bionic.opendir():
                name = entry.name
                if isinstance(name, bytes):
                    name = name.decode('utf-8', errors='replace')
                if name in ('.', '..'):
                    continue
                inode = vol.inodes[entry.inode]
                if inode.size > 1000:
                    bio = inode.open()
                    data = bio.read(inode.size)
                    with open(os.path.join(output_dir, name), 'wb') as out:
                        out.write(data)
                    print(f"  Extracted Bionic lib: {name} ({len(data)} bytes)")

def main():
    if len(sys.argv) < 3:
        print("Usage: extract_bionic.py <system.raw> <output_dir>")
        sys.exit(1)

    system_raw = sys.argv[1]
    output_dir = sys.argv[2]
    os.makedirs(output_dir, exist_ok=True)

    # Find ext4 offset
    fs_offset = find_ext4_offset(system_raw)
    if fs_offset is None:
        print("ERROR: Could not find ext4 filesystem")
        sys.exit(1)

    # Extract ext4 to separate file
    fs_size = 214153 * 4096  # Known size from AOSP ATD image
    ext4_path = '/tmp/system.ext4'
    print(f"Extracting ext4 ({fs_size} bytes) from offset {fs_offset}...")
    with open(system_raw, 'rb') as src:
        src.seek(fs_offset)
        with open(ext4_path, 'wb') as dst:
            remaining = fs_size
            while remaining > 0:
                chunk = src.read(min(1024*1024, remaining))
                if not chunk:
                    break
                dst.write(chunk)
                remaining -= len(chunk)

    # Extract Bionic libs and APEX from the ext4
    extract_ext4(ext4_path, output_dir)

    # If we got the APEX, extract linker64 from it
    apex_path = os.path.join(output_dir, 'runtime.apex')
    if os.path.exists(apex_path):
        print("Extracting linker64 from APEX...")
        extract_linker_from_apex(apex_path, output_dir)

    print("Done!")

if __name__ == '__main__':
    main()
