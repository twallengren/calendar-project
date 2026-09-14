#!/usr/bin/env python3
"""Execute the shipped browser business-date algorithm over HTTP at both deployment roots.

Requires Chrome/Chromium, available on GitHub's Ubuntu runners. No Python dependencies.
"""
import argparse
import functools
import http.server
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import urllib.request
from urllib.parse import urljoin


def verify(site, chrome):
    class Handler(http.server.SimpleHTTPRequestHandler):
        def do_GET(self):
            if self.path.startswith('/calendar-project/'):
                self.path = self.path[len('/calendar-project'):]
            super().do_GET()

        def log_message(self, *_args):
            pass

    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(Handler, directory=str(site)))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    origin = f'http://127.0.0.1:{server.server_port}'
    try:
        for prefix in ('/', '/calendar-project/'):
            base = origin + prefix
            index_url = base + 'v1/index.json'
            with urllib.request.urlopen(index_url) as response:
                index = json.load(response)
            for entry in index['calendars']:
                manifest_url = urljoin(index_url, entry['href'])
                assert manifest_url.startswith(base + 'v1/'), manifest_url
                with urllib.request.urlopen(manifest_url) as response:
                    manifest = json.load(response)
                for key, href in manifest['links'].items():
                    url = urljoin(manifest_url, href.replace('{year}', str(manifest['years'][0])))
                    assert url.startswith(base + 'v1/'), url
                    with urllib.request.urlopen(url) as response:
                        assert response.status == 200, url
                        body = response.read()
                        if key not in ('ics', 'ics_recent'):
                            json.loads(body)
            v2_url = base + 'v2/index.json'
            with urllib.request.urlopen(v2_url) as response:
                v2 = json.load(response)
            assert v2['schema_version'] == '2.0'
            for entry in v2['calendars']:
                manifest_url = urljoin(v2_url, entry['href'])
                assert manifest_url.startswith(base + 'v2/'), manifest_url
                with urllib.request.urlopen(manifest_url) as response:
                    manifest = json.load(response)
                for year in manifest['years']:
                    url = urljoin(manifest_url, year['href'])
                    assert url.startswith(base + 'v2/'), url
                    with urllib.request.urlopen(url) as response:
                        document = json.load(response)
                    for day in document['days']:
                        incomplete = 'INCOMPLETE' in day['completeness'].values()
                        assert (day['state'] == 'UNKNOWN') == incomplete, (url, day['date'])
                        if incomplete:
                            assert day['effective_confidence'] == 'UNKNOWN'
            with tempfile.TemporaryDirectory(prefix='bdc-browser-') as profile:
                result = subprocess.run([
                    chrome, '--headless', '--disable-gpu', '--disable-dev-shm-usage',
                    '--no-first-run', '--no-default-browser-check', '--disable-background-networking',
                    '--user-data-dir=' + profile, '--virtual-time-budget=15000', '--dump-dom',
                    base + 'compare/settlement-selftest.html',
                ], capture_output=True, text=True, timeout=60, check=True)
                assert 'cases passed' in result.stdout and 'cases FAILED' not in result.stdout, result.stdout[-5000:]
                assert 'badge ok' in result.stdout, result.stdout[-5000:]
            print(f'Browser parity and JSON/ICS links passed at {prefix}')
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--site', type=Path, required=True)
    parser.add_argument('--chrome', default=os.environ.get('CHROME_BIN'))
    args = parser.parse_args()
    chrome = args.chrome or shutil.which('google-chrome') or shutil.which('chromium')
    mac = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
    if not chrome and Path(mac).is_file():
        chrome = mac
    if not chrome:
        parser.error('Chrome is required; pass --chrome or set CHROME_BIN')
    verify(args.site.resolve(), chrome)


if __name__ == '__main__':
    main()
