# Renderer comparison — issue #23

Isolated Java 21 / Maven experiment; not part of the production reactor.

Build from the repository root:

```bash
mvn -f spikes/renderer-comparison/pom.xml -pl javafx-prototype -am clean verify
mvn -f spikes/renderer-comparison/pom.xml -pl jme-prototype -am clean verify
```

On a graphical Fedora session (Python 3 and Java 21):

```bash
python3 spikes/renderer-comparison/scripts/run.py javafx --output=/tmp/nodewalker-fx-manual
python3 spikes/renderer-comparison/scripts/run.py jme --output=/tmp/nodewalker-jme-manual
```

Each output directory must be new. Close one application before opening the other.
Only the first Maven build downloads dependencies. The launchers perform no network access.

Controls: drag left mouse to orbit, wheel to zoom, R to reset, M for reduced terrain/decoration,
D to toggle decoration, G for the optional route halo, 0–4 or click a shelter to select,
F12 to save a PNG. The fixed cycle is a visual reference, not a certified optimal result.

Use `--capture-only` to capture the fixed camera and close automatically, or `--benchmark`
for a 10-second warmup and 60-second automatic orbit. These modes are mutually exclusive.
Use `--simple` for the reduced preset. Capture mode locks navigation for reproducibility.

Full methods, results, evidence, limitations, fallback and decision:
[docs/SPIKE_RENDERIZADORES.md](../../docs/SPIKE_RENDERIZADORES.md).
