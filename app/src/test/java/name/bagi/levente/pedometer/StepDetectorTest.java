package name.bagi.levente.pedometer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Steps from the phone's step sensor chip reach every listener. */
public class StepDetectorTest {
    private static class CountingListener implements StepListener {
        int steps;
        public void onStep() { steps++; }
        public void passValue() { }
    }

    @Test
    public void externalStepsReachAllListeners() {
        StepDetector detector = new StepDetector();
        CountingListener first = new CountingListener();
        CountingListener second = new CountingListener();
        detector.addStepListener(first);
        detector.addStepListener(second);

        for (int i = 0; i < 25; i++) {
            detector.onExternalStep();
        }

        assertEquals(25, first.steps);
        assertEquals(25, second.steps);
    }

    @Test
    public void batchedStepsCountEachStep() {
        StepDetector detector = new StepDetector();
        CountingListener listener = new CountingListener();
        detector.addStepListener(listener);

        // A batch from the step-counter chip after the phone wakes.
        detector.onExternalSteps(137);
        detector.onExternalSteps(0);

        assertEquals(137, listener.steps);
    }

    @Test
    public void listenersAreCalledInOrderAdded() {
        final StringBuilder order = new StringBuilder();
        StepDetector detector = new StepDetector();
        detector.addStepListener(new StepListener() {
            public void onStep() { order.append("day-watcher,"); }
            public void passValue() { }
        });
        detector.addStepListener(new StepListener() {
            public void onStep() { order.append("display"); }
            public void passValue() { }
        });

        detector.onExternalStep();

        // StepService relies on the day watcher running first, so a step
        // just after midnight counts towards the new day.
        assertEquals("day-watcher,display", order.toString());
    }
}
