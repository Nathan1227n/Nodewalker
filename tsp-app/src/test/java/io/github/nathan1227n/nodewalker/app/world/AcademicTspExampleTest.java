package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.model.*;
import io.github.nathan1227n.nodewalker.core.solver.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Aula 5 - Caxeiro Viajante.pdf, p.4; matrix and page supplied by the professor's material reference. v1=0...v4=3. */
class AcademicTspExampleTest {
    static TspProblem example(){return new TspProblem(List.of(new City(0,0,0,0),new City(1,100,30,0),new City(2,100,0,100),new City(3,0,30,100)),
            new long[][]{{0,1,4,6},{1,0,2,3},{4,2,0,3},{6,3,3,0}});}
    @ParameterizedTest @EnumSource(SolverAlgorithm.class)
    void professorExampleHasCanonicalOptimumElevenAndCompleteDecomposition(SolverAlgorithm algorithm) {
        var p=example(); TspSolver solver=algorithm==SolverAlgorithm.BRUTE_FORCE?new BruteForceSolver():new BacktrackingSolver();
        var r=solver.solve(p);assertEquals(SolverStatus.CONCLUIDO,r.status());assertTrue(r.optimalityGuaranteed());
        assertEquals(11,r.cost().orElseThrow());assertEquals(List.of(0,1,3,2,0),r.route().orElseThrow().indices());
        if(algorithm==SolverAlgorithm.BRUTE_FORCE)assertEquals(6,r.metrics().completeRoutesEvaluated().orElseThrow());
        assertEquals(BigInteger.valueOf(6),BigInteger.valueOf(r.metrics().completeRoutesEvaluated().orElseThrow()).add(r.metrics().discardedRoutes().orElseThrow()));
        var b=CostBreakdown.tsp(p,Optional.of(r));assertEquals(4,b.terms().size());assertEquals(List.of(1L,3L,3L,4L),b.terms().stream().map(CostBreakdown.Term::weight).toList());
        assertEquals(List.of(BigInteger.ONE,BigInteger.valueOf(4),BigInteger.valueOf(7),BigInteger.valueOf(11)),b.terms().stream().map(CostBreakdown.Term::accumulated).toList());
        assertEquals("1 + 3 + 3 + 4 = 11",b.expression());assertEquals("2 → 0",b.terms().getLast().connection());
    }
}
