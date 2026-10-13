#!/usr/bin/env node
// Copies the Python package's bundled data (itself derived from blessed/ by
// python/scripts/sync_data.py) into javascript/data so both ports read the same
// files: manifest.json plus <ID>/metadata.json and <ID>/holidays.csv.
// `--check` verifies the copy is up to date without writing.
import { readFileSync, writeFileSync, mkdirSync, readdirSync, existsSync, statSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const source = join(here, "..", "..", "python", "bdc_calendars", "data");
const target = join(here, "..", "data");
const check = process.argv.includes("--check");

function walk(dir, base = "") {
  const out = [];
  for (const name of readdirSync(dir).sort()) {
    const path = join(dir, name);
    const rel = base ? base + "/" + name : name;
    if (statSync(path).isDirectory()) out.push(...walk(path, rel));
    else if (name.endsWith(".json") || name.endsWith(".csv")) out.push(rel);
  }
  return out;
}

const files = walk(source);
let stale = 0;
for (const rel of files) {
  const content = readFileSync(join(source, rel));
  const destination = join(target, rel);
  if (existsSync(destination) && readFileSync(destination).equals(content)) continue;
  stale++;
  if (!check) {
    mkdirSync(dirname(destination), { recursive: true });
    writeFileSync(destination, content);
  }
}
if (existsSync(target)) {
  for (const rel of walk(target)) {
    if (!files.includes(rel)) {
      stale++;
      if (!check) rmSync(join(target, rel));
    }
  }
}
if (check) {
  if (stale) {
    console.error(`${stale} data file(s) differ from python/bdc_calendars/data; run npm run sync-data`);
    process.exit(1);
  }
  console.log(`javascript/data is up to date (${files.length} files)`);
} else {
  const manifest = JSON.parse(readFileSync(join(target, "manifest.json"), "utf8"));
  console.log(`Synced ${files.length} files; data v${manifest.data_version}`);
}
