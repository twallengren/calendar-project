#!/usr/bin/env python3
"""Validate that a dataset version exactly matches its release impact."""

import argparse
import sys


def parse(value):
    try:
        parts = tuple(int(part) for part in value.split("."))
    except ValueError as error:
        raise ValueError("invalid semantic version {}".format(value)) from error
    if len(parts) != 3 or any(part < 0 for part in parts):
        raise ValueError("invalid semantic version {}".format(value))
    return parts


def expected(version, severity):
    major, minor, patch = parse(version)
    if severity == "MAJOR":
        return (major + 1, 0, 0)
    if severity == "MINOR":
        return (major, minor + 1, 0)
    if severity == "PATCH":
        return (major, minor, patch + 1)
    raise ValueError("invalid release severity {}".format(severity))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--severity", required=True)
    parser.add_argument("--print", action="store_true", dest="print_expected")
    args = parser.parse_args()
    try:
        wanted = expected(args.baseline, args.severity)
        actual = parse(args.candidate)
    except ValueError as error:
        parser.error(str(error))
    if args.print_expected:
        print("{}.{}.{}".format(*wanted))
        return 0
    if actual != wanted:
        print(
            "{} impact from {} requires {}.{}.{}; candidate is {}".format(
                args.severity, args.baseline, *wanted, args.candidate
            ),
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
