#!/usr/bin/env python3
"""Create byte-reproducible tar.gz and ZIP archives from a directory."""

from __future__ import annotations

import argparse
import gzip
import os
import stat
import tarfile
import zipfile

EPOCH = 315532800  # ZIP's minimum timestamp, 1980-01-01T00:00:00Z


def paths(root):
    for directory, names, files in os.walk(root):
        names.sort()
        for name in sorted(files):
            path = os.path.join(directory, name)
            yield path, os.path.relpath(path, root).replace(os.sep, "/")


def create_tar(root, output):
    with open(output, "wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as compressed:
            with tarfile.open(mode="w", fileobj=compressed, format=tarfile.PAX_FORMAT) as archive:
                for path, relative in paths(root):
                    info = archive.gettarinfo(path, arcname=relative)
                    info.uid = info.gid = 0
                    info.uname = info.gname = ""
                    info.mtime = 0
                    info.mode = 0o755 if info.mode & stat.S_IXUSR else 0o644
                    with open(path, "rb") as handle:
                        archive.addfile(info, handle)


def create_zip(root, output):
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path, relative in paths(root):
            info = zipfile.ZipInfo(relative, (1980, 1, 1, 0, 0, 0))
            mode = 0o755 if os.stat(path).st_mode & stat.S_IXUSR else 0o644
            info.external_attr = (mode & 0xFFFF) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            with open(path, "rb") as handle:
                archive.writestr(info, handle.read())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source")
    parser.add_argument("output_prefix")
    args = parser.parse_args()
    create_tar(args.source, args.output_prefix + ".tar.gz")
    create_zip(args.source, args.output_prefix + ".zip")


if __name__ == "__main__":
    main()
