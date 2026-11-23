#!/usr/bin/env python3
# 双指旋转 two-finger rotation
# Copyright (C) 2026 程溁 (contact: see LICENSING.md)
# SPDX-License-Identifier: GPL-3.0-only OR LicenseRef-Commercial
# See LICENSING.md for the additional terms and the commercial license.
"""Download a project's Gradle distribution and place it in the wrapper cache.

Use this when `gradlew` fails to download its distribution, for example with an SSL handshake
error on networks where Java cannot reach the GitHub host the official URL redirects to. The
script tries the official URL first, then mirrors, and only keeps a file whose SHA-256 matches
the official checksum. The wrapper then finds the zip in its cache, skips its own download and
unpacks it as usual. Project files are not touched.

Usage:
    python -I tools/seed_gradle_wrapper.py <path/to/gradle-wrapper.properties>

Works for any project with a Gradle wrapper.
"""
import hashlib
import os
import re
import sys
import urllib.request
from pathlib import Path

DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz"
HEADERS = {"User-Agent": "seed-gradle-wrapper/1.0"}


def base36(n: int) -> str:
    out = ""
    while n:
        n, r = divmod(n, 36)
        out = DIGITS[r] + out
    return out or "0"


def open_url(url: str, timeout: int):
    return urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=timeout)


def official_sha256(url: str):
    try:
        with open_url(url + ".sha256", 30) as r:
            value = r.read().decode("ascii", "replace").strip().split()[0].lower()
        return value if re.fullmatch(r"[0-9a-f]{64}", value) else None
    except Exception as e:  # noqa: BLE001 - report and carry on
        print("  could not fetch the official checksum:", e)
        return None


def download(url: str, dest: Path) -> str:
    """Downloads url to dest and returns its SHA-256."""
    digest = hashlib.sha256()
    with open_url(url, 60) as r, open(dest, "wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            digest.update(chunk)
            f.write(chunk)
    return digest.hexdigest()


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    props = Path(sys.argv[1]).read_text(encoding="utf-8")
    match = re.search(r"^distributionUrl=(.+)$", props, re.M)
    if not match:
        print("no distributionUrl in", sys.argv[1])
        return 1
    url = match.group(1).strip().replace("\\:", ":")
    zip_name = url.rsplit("/", 1)[1]                     # gradle-9.9.2-bin.zip
    dist_name = zip_name[: -len(".zip")]                 # gradle-9.9.2-bin
    version = re.sub(r"^gradle-|-(bin|all)$", "", dist_name)
    # Same as the wrapper's PathAssembler: base-36 of the MD5 of the URL string.
    url_hash = base36(int.from_bytes(hashlib.md5(url.encode("utf-8")).digest(), "big"))

    home = Path(os.environ.get("GRADLE_USER_HOME") or Path.home() / ".gradle")
    target_dir = home / "wrapper" / "dists" / dist_name / url_hash
    target = target_dir / zip_name
    if target.exists() or (target_dir / (zip_name + ".ok")).exists():
        print("already present:", target_dir)
        return 0

    expected = official_sha256(url)
    if expected is None:
        print("refusing to continue without the official checksum")
        return 1

    sources = [
        url,
        f"https://mirrors.aliyun.com/gradle/distributions/v{version}/{zip_name}",
        f"https://mirrors.cloud.tencent.com/gradle/{zip_name}",
    ]
    target_dir.mkdir(parents=True, exist_ok=True)
    part = target_dir / (zip_name + ".part")
    for source in sources:
        print("trying", source)
        try:
            actual = download(source, part)
        except Exception as e:  # noqa: BLE001 - try the next source
            print("  failed:", e)
            continue
        if actual == expected:
            part.replace(target)
            print("seeded", target)
            return 0
        print("  checksum mismatch, discarded")
    part.unlink(missing_ok=True)
    print("no source delivered a file matching the official checksum")
    return 1


if __name__ == "__main__":
    sys.exit(main())
