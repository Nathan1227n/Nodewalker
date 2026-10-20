package io.github.nathan1227n.nodewalker.app.operation;

import io.github.nathan1227n.nodewalker.core.graph.*;
import io.github.nathan1227n.nodewalker.core.model.*;
import io.github.nathan1227n.nodewalker.core.shortestpath.*;
import io.github.nathan1227n.nodewalker.core.solver.*;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.*;
import static io.github.nathan1227n.nodewalker.app.operation.ExecutionRequest.Algorithm.*;
import static org.junit.jupiter.api.Assertions.*;

class ResultReportsTest {
    private static final TspProblem P = HudPresentationTest.problem(2,3,4);
    private static ExecutionRecord.Tsp complete(long instance, long run, ExecutionRequest.Algorithm algorithm) {
        return new ExecutionRecord.Tsp(instance,run,OperationalControllerTest.request(algorithm),
                TspResult.completed(P,new TspRoute(List.of(0,1,2,0)),9,SolverMetrics.empty()));
    }
    private static ExecutionRecord.Tsp interrupted(long run, SolverStatus status, boolean incumbent) {
        return new ExecutionRecord.Tsp(1,run,OperationalControllerTest.request(BRUTE_FORCE),TspResult.interrupted(P,status,
                incumbent ? Optional.of(new TspRoute(List.of(0,1,2,0))) : Optional.empty(),
                incumbent ? OptionalLong.of(9) : OptionalLong.empty(),SolverMetrics.empty(),"motivo real"));
    }
    @Test void completedComparisonAgreesAndExportsInstanceIndependentData() {
        var comparison=TspComparison.of(1,List.of(complete(1,1,BRUTE_FORCE),complete(1,2,BACKTRACKING)));
        assertEquals(TspComparison.Verdict.AGREEMENT,comparison.verdict());
        assertTrue(comparison.copyText().contains("Custo: 9")); assertTrue(comparison.copyText().contains("0 → 1 → 2 → 0"));
        assertTrue(comparison.copyText().contains("Tempo interno: não informado")); assertTrue(comparison.copyText().contains("não constituem um benchmark"));
    }
    @Test void latestAttemptWinsByRunIdentityRegardlessOfListOrder() {
        var latest=interrupted(4,SolverStatus.CANCELADO,true);
        var comparison=TspComparison.of(1,List.of(latest,complete(1,2,BACKTRACKING),complete(1,1,BRUTE_FORCE)));
        assertSame(latest,comparison.bruteForce().orElseThrow()); assertEquals(TspComparison.Verdict.PARTIAL,comparison.verdict());
        assertTrue(comparison.copyText().contains("Cancelado")); assertTrue(comparison.copyText().contains("ÓTIMO NÃO CERTIFICADO"));
        assertFalse(HudPresentation.history(List.of(complete(1,1,BRUTE_FORCE),complete(1,2,BACKTRACKING),latest)).contains("Mesmo custo ótimo e mesmo ciclo"));
    }
    @Test void otherInstancesNeverFillTheOtherColumn() {
        var comparison=TspComparison.of(1,List.of(complete(1,1,BRUTE_FORCE),complete(2,2,BACKTRACKING)));
        assertEquals(TspComparison.Verdict.WAITING,comparison.verdict()); assertTrue(comparison.backtracking().isEmpty());
        assertFalse(HudPresentation.history(List.of(complete(1,1,BRUTE_FORCE),complete(2,2,BACKTRACKING))).contains("Mesmo custo ótimo"));
    }
    @Test void dijkstraCannotFillATspColumn() {
        var r=new DijkstraShortestPathSolver().solve(new RefugeGraph(P,RefugeTopology.complete(3)));
        var record=new ExecutionRecord.Paths(1,1,OperationalControllerTest.request(DIJKSTRA),r);
        var comparison=TspComparison.of(1,List.of(record));
        assertTrue(comparison.bruteForce().isEmpty()); assertTrue(comparison.backtracking().isEmpty()); assertEquals(TspComparison.Verdict.WAITING,comparison.verdict());
    }
    @Test void divergentCanonicalCyclesAreFlaggedEvenWhenCostsTie() {
        var cities=List.of(new City(0,0,0,0),new City(1,1,0,0),new City(2,2,0,0),new City(3,3,0,0));
        var p=new TspProblem(cities,new long[][]{{0,1,1,1},{1,0,1,1},{1,1,0,1},{1,1,1,0}});
        var a=new ExecutionRecord.Tsp(1,1,OperationalControllerTest.request(BRUTE_FORCE),TspResult.completed(p,new TspRoute(List.of(0,1,2,3,0)),4,SolverMetrics.empty()));
        var b=new ExecutionRecord.Tsp(1,2,OperationalControllerTest.request(BACKTRACKING),TspResult.completed(p,new TspRoute(List.of(0,1,3,2,0)),4,SolverMetrics.empty()));
        assertEquals(TspComparison.Verdict.DIVERGENCE,TspComparison.of(1,List.of(a,b)).verdict());
    }
    @Test void interruptedCycleHasClearPartialConclusionAndRealReason() {
        var report=ResultReport.of(HudPresentationTest.snapshot(interrupted(1,SolverStatus.LIMITE_ATINGIDO,true),1));
        assertTrue(report.conclusion().startsWith("Resultado parcial")); assertEquals("Limite atingido",report.status());
        assertEquals("9",report.value()); assertEquals("motivo real",report.reason()); assertTrue(report.copyText().contains("Tempo interno do solver: não informado"));
    }
    @Test void noIncumbentInventsNeitherCostNorOptimum() {
        var report=ResultReport.of(HudPresentationTest.snapshot(interrupted(1,SolverStatus.CANCELADO,false),1));
        assertEquals("não informado",report.value()); assertTrue(report.conclusion().contains("sem encontrar um ciclo completo"));
        assertEquals("Cancelado",report.status());
    }
    @Test void completedTspIsExplicitlyACycleAndExportIdentifiesTheRun() {
        var report=ResultReport.of(HudPresentationTest.snapshot(complete(1,7,BACKTRACKING),1));
        assertTrue(report.conclusion().startsWith("Ótimo certificado")); assertTrue(report.copyText().contains("seed 23"));
        assertTrue(report.copyText().contains("instância #1")); assertTrue(report.copyText().contains("execução #7"));
        assertTrue(report.routeLabel().contains("retorno"));
    }
    @Test void exactLargeDistanceSurvivesPresentationAndCopy() {
        var p=HudPresentationTest.problem(Long.MAX_VALUE,Long.MAX_VALUE,1);
        var r=new DijkstraShortestPathSolver().solve(new RefugeGraph(p,RefugeTopology.manual(3,List.of(new RefugeTopology.Edge(0,1),new RefugeTopology.Edge(1,2)))));
        var report=ResultReport.of(HudPresentationTest.snapshot(new ExecutionRecord.Paths(1,2,OperationalControllerTest.request(DIJKSTRA),r),2));
        var exact=BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO).toString();
        assertEquals(exact,report.value()); assertTrue(report.copyText().contains(exact)); assertEquals("0 → 1 → 2",report.route());
        assertTrue(report.conclusion().contains("não é um ciclo TSP")); assertTrue(report.context().contains("Topologia manual"));
    }
    @Test void completedDisconnectedDestinationIsExplainedWithoutInfinity() {
        var r=new DijkstraShortestPathSolver().solve(new RefugeGraph(P,RefugeTopology.manual(3,List.of(new RefugeTopology.Edge(0,1)))));
        var report=ResultReport.of(HudPresentationTest.snapshot(new ExecutionRecord.Paths(1,2,OperationalControllerTest.request(DIJKSTRA),r),2));
        assertTrue(report.conclusion().startsWith("Destino inalcançável")); assertEquals("não informado",report.value());
    }
    @Test void partialDijkstraDistinguishesConfirmedFromUnresolvedDestinations() {
        var r=new DijkstraShortestPathSolver().solve(new RefugeGraph(P,RefugeTopology.complete(3)),ShortestPathExecutionOptions.limits(1,0,1000),new ShortestPathCancellationToken());
        var record=new ExecutionRecord.Paths(1,2,OperationalControllerTest.request(DIJKSTRA),r);
        var origin=ResultReport.of(HudPresentationTest.snapshot(record,0)); var other=ResultReport.of(HudPresentationTest.snapshot(record,1));
        assertTrue(origin.conclusion().startsWith("Distância mínima confirmada")); assertEquals("0",origin.value()); assertEquals("0",origin.route());
        assertEquals("Limite atingido",origin.status()); assertTrue(other.conclusion().startsWith("Destino ainda não resolvido")); assertEquals("não informado",other.value());
    }
    @Test void measuredTimesAreInternalAndOptionalCountersStayAbsent() {
        var metrics=new SolverMetrics(OptionalLong.of(1234567),OptionalLong.of(1),OptionalLong.of(2),Optional.empty());
        var result=TspResult.completed(P,new TspRoute(List.of(0,1,2,0)),9,metrics);
        var record=new ExecutionRecord.Tsp(1,2,OperationalControllerTest.request(BACKTRACKING),result);
        var report=ResultReport.of(HudPresentationTest.snapshot(record,1));
        assertEquals("1.235 ms",report.elapsed()); assertTrue(report.metrics().contains("Folhas descartadas: não informado"));
    }
    @Test void zeroAndMaximumLongCostsArePreservedInReportAndExport() {
        for (long cost : new long[]{0,Long.MAX_VALUE}) {
            var p=HudPresentationTest.problem(cost,0,0);
            var result=TspResult.completed(p,new TspRoute(List.of(0,1,2,0)),cost,SolverMetrics.empty());
            var report=ResultReport.of(HudPresentationTest.snapshot(new ExecutionRecord.Tsp(1,1,OperationalControllerTest.request(BRUTE_FORCE),result),1));
            assertEquals(Long.toString(cost),report.value()); assertTrue(report.copyText().contains("Custo ótimo do ciclo: "+cost));
        }
    }
    @Test void generationErrorNeverFabricatesASolverResultInTheReport() {
        var snapshot=new OperationalSnapshot(OperationalSnapshot.Phase.ERRO,"GERAÇÃO",2,0,3,23,null,null,
                ExecutionRequest.defaults(),1,null,List.of(),"","GERAÇÃO: falha de geração");
        var report=ResultReport.of(snapshot);
        assertEquals("não informado",report.value()); assertEquals("Aguardando execução",report.status());
        assertEquals("GERAÇÃO: falha de geração",report.reason());
        assertEquals(TspComparison.Verdict.WAITING,TspComparison.of(2,snapshot.history()).verdict());
    }

}
