#!/usr/bin/env python3
"""Opt-in Fedora/Linux graphical review. JavaFX windows, never a headless CI test."""
import argparse
import json
import os
import pathlib
import platform
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]


def run_metadata(command):
    if not shutil.which(command[0]):
        return "unavailable"
    result = subprocess.run(command, text=True, capture_output=True, check=False)
    return (result.stdout + result.stderr).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path, default=ROOT / "tsp-app/target/visual-review")
    software = parser.add_mutually_exclusive_group()
    software.add_argument("--software-2d", action="store_true", help="Force Prism software + --2d; never create Scene3D")
    software.add_argument("--software-auto", action="store_true", help="Force Prism software without --2d; assert automatic fallback and disabled 3D button")
    parser.add_argument("--results", action="store_true", help="Capture result presentation and same-instance TSP comparison using real solvers")
    parser.add_argument("--playback", action="store_true", help="Capture gradual refuge/path drawing with pause, speed, replay and mode-switch assertions")
    parser.add_argument("--operational", action="store_true", help="Exercise the real generation/solver/cancellation panel; no presentation fixtures")
    parser.add_argument("--native-input", action="store_true", help="Use Robot to drag, scroll, click and test T/R in the visible 3D window")
    parser.add_argument("--maven-repo", type=pathlib.Path, default=pathlib.Path.home() / ".m2/repository")
    args = parser.parse_args()
    if sum((args.playback, args.operational, args.results)) > 1:
        parser.error("Choose only one of --playback, --operational or --results")
    subprocess.run(["mvn", "--batch-mode", "--no-transfer-progress", "-pl", "tsp-app", "-am", "test-compile"], cwd=ROOT, check=True)
    version = ET.parse(ROOT / "pom.xml").findtext("{*}properties/{*}javafx.version")
    classifier = "linux-aarch64" if platform.machine() in ("aarch64", "arm64") else "linux"
    jars = [args.maven_repo / f"org/openjfx/javafx-{part}/{version}/javafx-{part}-{version}-{classifier}.jar"
            for part in ("base", "graphics", "controls")]
    for jar in jars:
        if not jar.is_file():
            parser.error(f"JavaFX jar missing: {jar}. Build on Linux first, or supply --maven-repo.")
    args.output.mkdir(parents=True, exist_ok=True)
    metadata = {"system": platform.system(), "release": platform.release(), "architecture": platform.machine(),
                "session": os.environ.get("XDG_SESSION_TYPE", "unknown"), "java": run_metadata(["java", "-version"]),
                "graphics": run_metadata(["glxinfo", "-B"]), "software2d": args.software_2d,
                "playback": args.playback, "operational": args.operational, "results": args.results, "automaticFallback": args.software_auto, "nativeInput": args.native_input,
                "method": "Real visible window; JavaFX Scene.snapshot after pulse barriers. API-driven controls. No presented FPS measurement."}
    (args.output / "environment.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    classpath = os.pathsep.join(str(ROOT / p) for p in ("tsp-core/target/classes", "tsp-app/target/classes", "tsp-app/target/test-classes"))
    command = ["java"] + (["-Dprism.order=sw"] if args.software_2d or args.software_auto else [])
    command += ["--module-path", os.pathsep.join(map(str, jars)), "--add-modules", "javafx.controls", "-cp", classpath,
                "io.github.nathan1227n.nodewalker.app." + ("ResultsVisualReview" if args.results else "PlaybackVisualReview" if args.playback else "OperationalVisualReview" if args.operational else "VisualReview"), str(args.output.resolve())]
    if args.software_2d:
        command.append("--2d-only")
    if args.software_auto:
        command.append("--fallback-only")
    if args.native_input:
        command.append("--native-input")
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
    log = result.stdout + result.stderr
    (args.output / "review.log").write_text(log, encoding="utf-8")
    print(log, end="")
    if result.returncode:
        raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
