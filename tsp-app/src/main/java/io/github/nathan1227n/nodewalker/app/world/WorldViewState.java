package io.github.nathan1227n.nodewalker.app.world;

/**
 * Renderer-independent navigation/selection/settings. Owned by the presentation thread.
 */
public final class WorldViewState {
    private WorldSceneModel model;
    private final WorldPlayback playback = new WorldPlayback();
    private OrbitCamera camera;
    private int selected;
    private boolean simplified;
    private boolean decoration = true;
    private boolean connections;
    private boolean reducedLabels;
    private EdgeWeightMode weightMode=EdgeWeightMode.NONE;
    private EdgeWeightPolicy.Scope weightScope=EdgeWeightPolicy.Scope.AVAILABLE;
    private java.util.List<? extends WorldSceneModel.Edge> secondary=java.util.List.of();
    private EdgeWeightPolicy.Selection weightSelection=new EdgeWeightPolicy.Selection(java.util.OptionalLong.empty(),0,java.util.List.of());
    private long weightRevision;
    public EdgeWeightMode weightMode(){return weightMode;}
    public EdgeWeightPolicy.Scope weightScope(){return weightScope;}
    public void setWeightMode(EdgeWeightMode mode){weightMode=java.util.Objects.requireNonNull(mode);if(mode==EdgeWeightMode.CONNECTIONS)connections=true;refreshWeights();}
    public void setWeightScope(EdgeWeightPolicy.Scope scope){weightScope=java.util.Objects.requireNonNull(scope);refreshWeights();}
    public java.util.List<? extends WorldSceneModel.Edge> secondaryConnections(){return secondary;}
    public EdgeWeightPolicy.Selection weightSelection(){return weightSelection;}
    public long weightRevision(){return weightRevision;}
    private void refreshWeights(){
        secondary=model==null?java.util.List.of():EdgeWeightPolicy.secondaryConnections(model,selected,weightScope);
        weightSelection=EdgeWeightPolicy.select(model,weightMode,connections,secondary);weightRevision++;
    }

    public void setModel(WorldSceneModel next) {
        if (model == null || model.problem() != next.problem()) {
            playback.newWorld(next.problem().size());
            camera = new OrbitCamera(next.positions(), next.terrain(false).extent());
            selected = 0;
        }
        model = next;
        playback.path(next.segments().size());
        refreshWeights();
    }

    public WorldSceneModel model() {
        return model;
    }

    public void clear() {
        model = null;
        playback.clear();
        camera = null;
        selected = 0;
        refreshWeights();
    }

    public WorldPlayback playback() { return playback; }

    /** Pure interpolation used by both renderers; original edge endpoints remain authoritative. */
    public double edgeFraction(int index) { return playback.edgeFraction(index); }
    public double cityFraction(int index) { return playback.cityFraction(index); }
    public java.util.Optional<Vec3> playbackCursor() {
        if (playback.stage() != WorldPlayback.Stage.PATH) return java.util.Optional.empty();
        int edge = playback.completedEdges();
        if (edge >= model.segments().size()) return java.util.Optional.empty();
        var segment = model.segments().get(edge).geometry();
        return java.util.Optional.of(segment.pointAt(playback.edgeFraction(edge)));
    }

    public OrbitCamera camera() {
        return camera;
    }

    public int selected() {
        return selected;
    }

    public void select(int index) {
        if (index < 0 || index >= model.refuges().size()) throw new IllegalArgumentException("Invalid refuge");
        selected = index;
        refreshWeights();
    }

    public boolean simplified() {
        return simplified;
    }

    public void setSimplified(boolean value) {
        simplified = value;
    }

    public boolean decoration() {
        return decoration;
    }

    public void setDecoration(boolean value) {
        decoration = value;
    }

    public boolean connections() {
        return connections;
    }

    public void setConnections(boolean value) {
        connections = value;
        refreshWeights();
    }

    public boolean reducedLabels() {
        return reducedLabels;
    }

    public void setReducedLabels(boolean value) {
        reducedLabels = value;
    }
}
