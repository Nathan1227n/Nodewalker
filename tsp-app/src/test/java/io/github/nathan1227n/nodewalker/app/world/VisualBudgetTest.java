package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.generator.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisualBudgetTest {
    @ParameterizedTest @ValueSource(ints={3,12,40,80})
    void admissionEstimateCoversBothPreparedQualitiesAndDashedTspOverlay(int n) {
        var p=TspGenerator.generate(n,23,new GenerationOptions(100000,16*1024*1024)); var w=PreparedWorld.prepare(p);
        long triangles=w.geometry(false).triangleCount()+w.geometry(true).triangleCount()+288L*n;
        assertTrue(triangles<VisualBudget.estimatedTriangles(n),"triangles="+triangles);
        assertTrue(w.geometry(false).numericBufferBytes()+w.geometry(true).numericBufferBytes()<VisualBudget.estimatedBytes(n));
    }
    @Test void cancellationIsObservedDuringGeometryPreparation() {
        var p=TspGenerator.generate(3,23,new GenerationOptions(10000,1000000)); var calls=new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(java.util.concurrent.CancellationException.class,()->PreparedWorld.prepare(p,()->{if(calls.incrementAndGet()==4)throw new java.util.concurrent.CancellationException();}));
        assertEquals(4,calls.get());
    }
}
