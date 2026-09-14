import datetime as dt
import json
import tempfile
import unittest
from pathlib import Path

from scripts.verification.source_review import review


class SourceReviewTest(unittest.TestCase):
    def test_stale_source_warning_identifies_market_register(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            register = root / 'sources' / 'US-MARKET-BASE' / 'register.json'
            register.parent.mkdir(parents=True)
            register.write_text(
                json.dumps({'entries': [{'id': 'convention', 'retrieved': '2025-01-01'}]})
            )

            warnings = list(review(root, dt.date(2026, 1, 1), horizon=90))

            self.assertEqual(
                warnings,
                [
                    'US-MARKET-BASE/convention: '
                    'source review is older than 180 days (2025-01-01)'
                ],
            )


if __name__ == '__main__':
    unittest.main()
