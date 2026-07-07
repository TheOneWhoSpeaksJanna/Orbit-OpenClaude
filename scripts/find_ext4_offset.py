#!/usr/bin/env python3
"""Find the ext4 filesystem offset in an Android system image.

Usage: python3 find_ext4_offset.py <image.raw>

Scans the first 10MB for the ext4 superblock magic (0xEF53) and
verifies it's a real superblock by checking the block size field.
Prints the partition offset (superblock position - 1024).
"""
import struct
import sys

def find_ext4_offset(path):
    with open(path, 'rb') as f:
        data = f.read(10 * 1024 * 1024)  # First 10MB
        for i in range(0, len(data) - 1024, 4):
            sb = data[i:i+1024]
            if len(sb) >= 58 and sb[56:58] == b'\x53\xef':
                # Verify block size is valid
                block_size_log = struct.unpack_from('<I', sb, 24)[0]
                if block_size_log <= 6:  # 1024 << 6 = 65536
                    block_size = 1024 << block_size_log
                    if block_size > 0:
                        offset = i - 1024
                        if offset >= 0:
                            print(offset)
                            return
    print("-1")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: find_ext4_offset.py <image.raw>")
        sys.exit(1)
    find_ext4_offset(sys.argv[1])
