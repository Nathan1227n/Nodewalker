package io.github.nathan1227n.nodewalker.app.world;

/** Renderer-independent navigation/selection/settings. Owned by the presentation thread. */
public final class WorldViewState {
    private TspSceneModel model;
    private OrbitCamera camera;
    private int selected;
    private boolean simplified;
    private boolean decoration = true;
    private boolean connections;

    public void setModel(TspSceneModel next) {
        if (model == null || model.problem() != next.problem()) {
            camera = new OrbitCamera(next.positions(), next.terrain(false).extent());
            selected = 0;
        }
        model = next;
    }
    public TspSceneModel model() { return model; }
    public void clear() { model = null; camera = null; selected = 0; }
    public OrbitCamera camera() { return camera; }
    public int selected() { return selected; }
    public void select(int index) {
        if (index < 0 || index >= model.refuges().size()) throw new IllegalArgumentException("Invalid refuge");
        selected = index;
    }
    public boolean simplified() { return simplified; }
    public void setSimplified(boolean value) { simplified = value; }
    public boolean decoration() { return decoration; }
    public void setDecoration(boolean value) { decoration = value; }
    public boolean connections() { return connections; }
    public void setConnections(boolean value) { connections = value; }
}
