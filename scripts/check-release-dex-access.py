"""Reject APKs whose DEX classes extend a non-public class in another package."""
import argparse
import struct
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk')
args = parser.parse_args()
classes = {}
with zipfile.ZipFile(args.apk) as archive:
    for name in archive.namelist():
        if not name.startswith('classes') or not name.endswith('.dex'):
            continue
        data = archive.read(name)
        assert data[:4] == b'dex\n', 'invalid DEX header'
        u32 = lambda offset: struct.unpack_from('<I', data, offset)[0]
        strings, types = u32(60), u32(68)

        def descriptor(index):
            offset = u32(strings + 4 * u32(types + 4 * index))
            while data[offset] & 128:
                offset += 1
            offset += 1
            return data[offset:data.index(0, offset)].decode('utf-8', errors='replace')

        for index in range(u32(96)):
            offset = u32(100) + 32 * index
            parent = u32(offset + 8)
            classes[descriptor(u32(offset))] = (
                bool(u32(offset + 4) & 1),
                None if parent == 0xffffffff else descriptor(parent),
            )
assert classes, 'APK contains no DEX classes'
violations = []
for child, (_, parent) in classes.items():
    if parent in classes and not classes[parent][0]:
        if child.rpartition('/')[0] != parent.rpartition('/')[0]:
            violations.append(f'{child} extends inaccessible {parent}')
for violation in violations:
    print(violation)
print(f'DEX superclass access: {len(classes)} classes, {len(violations)} violations')
raise SystemExit(bool(violations))
