/*
 * Copyright 2014 Ghent University, Bayer CropScience.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jamesframework.core.search.algo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jamesframework.core.exceptions.SearchException;
import org.jamesframework.core.factory.MetropolisSearchFactory;
import org.jamesframework.core.problems.Problem;
import org.jamesframework.core.problems.objectives.evaluations.PenalizedEvaluation;
import org.jamesframework.core.search.NeighbourhoodSearch;
import org.jamesframework.core.search.Search;
import org.jamesframework.core.subset.SubsetSolution;
import org.jamesframework.core.search.SearchTestTemplate;
import static org.jamesframework.core.search.SearchTestTemplate.setRandomSeed;
import org.jamesframework.core.search.listeners.SearchListener;
import org.jamesframework.core.search.neigh.Neighbourhood;
import org.jamesframework.core.search.status.SearchStatus;
import org.jamesframework.core.subset.neigh.SingleSwapNeighbourhood;
import org.jamesframework.test.stubs.NeverSatisfiedConstraintStub;
import org.jamesframework.test.stubs.NeverSatisfiedPenalizingConstraintStub;
import org.jamesframework.test.util.DelayedExecution;
import org.jamesframework.test.util.TestConstants;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 * Test parallel tempering algorithm.
 * 
 * @author <a href="mailto:herman.debeukelaer@ugent.be">Herman De Beukelaer</a>
 */
public class ParallelTemperingTest extends SearchTestTemplate {
    
    // set custom seeds if desired (null for random seeds)
    // - seeds[0]:    main parallel tempering search seed
    // - seeds[1-10]: replica seeds
    private static final long[] seeds = null;
    /*private static final long[] seeds = new long[]{-2236172874615269829L,
                                                     -5188315990827871739L,
                                                     -7319285098849436177L,
                                                     -713556478495592021L,
                                                     -4012602022630517470L,
                                                     1957185940936080323L,
                                                     -7751036578472904675L,
                                                     -7937324089557802382L,
                                                     6315018116614864482L,
                                                     5844242055203561180L,
                                                     2379339502611672320L};*/
    
    // parallel tempering search
    private ParallelTempering<SubsetSolution> search;
    private List<MetropolisSearch<SubsetSolution>> replicas;
    
    // number of replicas
    private final int numReplicas = 10;
    
    // minium and maximum temperatures
    private final double MIN_TEMP = 50.0  * 1e-6;
    private final double MAX_TEMP = 200.0 * 1e-6;
    
    // maximum runtime
    private final long SINGLE_RUN_RUNTIME = 1000;
    private final long MULTI_RUN_RUNTIME = 100;
    private final TimeUnit MAX_RUNTIME_TIME_UNIT = TimeUnit.MILLISECONDS;
    
    // number of runs in multi run tests
    private final int NUM_RUNS = 5;
    
    /**
     * Print message when starting tests.
     */
    @BeforeClass
    public static void setUpClass() {
        System.out.println("# Testing ParallelTempering ...");
        SearchTestTemplate.setUpClass();
    }

    /**
     * Print message when tests are complete.
     */
    @AfterClass
    public static void tearDownClass() {
        System.out.println("# Done testing ParallelTempering!");
    }
    
    @Override
    @Before
    public void setUp(){
        // call super
        super.setUp();
        // set Metropolis search factory (random or custom seeds)
        // --> both store the created replicas externally
        replicas = new ArrayList<>();
        MetropolisSearchFactory<SubsetSolution> msf;
        if(seeds == null){
            msf = (p, n, t) -> {
                MetropolisSearch<SubsetSolution> ms = new MetropolisSearch<>(p, n, t);
                setRandomSeed(ms);
                replicas.add(ms);
                return ms;
            };
        } else {
            msf = new MetropolisSearchFactory<SubsetSolution>() {

                private int i = 1;
                
                @Override
                public MetropolisSearch<SubsetSolution> create(Problem<SubsetSolution> p,
                                                               Neighbourhood<? super SubsetSolution> n,
                                                               double t) {
                    MetropolisSearch<SubsetSolution> ms = new MetropolisSearch<>(p, n, t);
                    setSeed(ms, seeds[i++]);
                    replicas.add(ms);
                    return ms;
                }
                
            };
        }
        // create parallel tempering search
        search = new ParallelTempering<>(problem, neigh, numReplicas, MIN_TEMP, MAX_TEMP, msf);
        // set and log seed for main search
        if(seeds == null){
            setRandomSeed(search);
        } else {
            setSeed(search, seeds[0]);
        }
    }
    
    @After
    public void tearDown(){
        // print number of accepted/rejected moves of last run
        if(search.getSteps() > 0){
            System.out.println("   >>> num accepted/rejected moves during last run: "
                                + search.getNumAcceptedMoves() + "/" + search.getNumRejectedMoves());
        }
        // dispose search
        if(search.getStatus() == SearchStatus.IDLE){
            search.dispose();
        }
    }

    /**
     * Test constructors.
     */
    @Test
    public void testConstructors(){
        
        System.out.println(" - test constructors");
        
        // test basic constructor without custom Metropolis factory
        search = new ParallelTempering<>(problem, neigh, numReplicas, MIN_TEMP, MAX_TEMP);
        
        // test exceptions
        
        boolean thrown;
        
        thrown = false;
        try{
            search = new ParallelTempering<>(problem, neigh, 0, MIN_TEMP, MAX_TEMP);
        } catch(IllegalArgumentException ex){
            thrown = true;
        }
        assertTrue(thrown);
        
        thrown = false;
        try{
            search = new ParallelTempering<>(problem, neigh, numReplicas, 0.0, MAX_TEMP);
        } catch(IllegalArgumentException ex){
            thrown = true;
        }
        assertTrue(thrown);
        
        thrown = false;
        try{
            search = new ParallelTempering<>(problem, neigh, numReplicas, MIN_TEMP, 0.0);
        } catch(IllegalArgumentException ex){
            thrown = true;
        }
        assertTrue(thrown);
        
        thrown = false;
        try{
            search = new ParallelTempering<>(problem, neigh, numReplicas, MAX_TEMP, MIN_TEMP);
        } catch(IllegalArgumentException ex){
            thrown = true;
        }
        assertTrue(thrown);
        
        thrown = false;
        try{
            search = new ParallelTempering<>(problem, neigh, numReplicas, MIN_TEMP, MAX_TEMP, null);
        } catch(NullPointerException ex){
            thrown = true;
        }
        assertTrue(thrown);
        
    }
    
    @Test
    public void testSetReplicaSteps() {
        System.out.println(" - test set replica steps");
        
        boolean thrown = false;
        try{
            search.setReplicaSteps(0);
        } catch (IllegalArgumentException ex){
            thrown = true;
        }
        assertTrue(thrown);
        
        thrown = false;
        try{
            search.setReplicaSteps(-1);
        } catch (IllegalArgumentException ex){
            thrown = true;
        }
        assertTrue(thrown);
        
        for(int i=0; i<100; i++){
            int repSteps = RG.nextInt(500)+1;
            search.setReplicaSteps(repSteps);
            assertEquals(repSteps, search.getReplicaSteps());
        }
    }
    
    @Test
    public void testSetNeighbourhood(){
        System.out.println(" - test set neighbourhood");
        
        Neighbourhood<SubsetSolution> newNeigh = new SingleSwapNeighbourhood();
        
        search.setNeighbourhood(newNeigh);
        assertEquals(newNeigh, search.getNeighbourhood());
        
        replicas.forEach(rep -> {
            assertEquals(newNeigh, rep.getNeighbourhood());
        });
    }
    
    @Test
    public void testSetCurrentSolution(){
        System.out.println(" - test set current solution");
        
        SubsetSolution sol = problem.createRandomSolution();
        
        search.setCurrentSolution(sol);
        assertEquals(sol, search.getCurrentSolution());
                
        replicas.forEach(rep -> {
            assertNotSame(sol, rep.getCurrentSolution());
            assertEquals(sol, rep.getCurrentSolution());
        });
    }
    
    @Test
    public void testInterruptReplicaExecution(){
        System.out.println(" - test interrupt replica execution");
        
        // schedule task to interrupt the thread that runs the main search
        // while it is waiting for the replicas to complete their current run
        final Thread thr = Thread.currentThread();
        DelayedExecution.schedule(() -> thr.interrupt(), 500);
        
        boolean thrown = false;
        try{
            search.setReplicaSteps(1000000000); // keep those replicas busy!
            search.start();
        } catch(SearchException ex){
            thrown = true;
        }
        assertTrue(thrown);
    }
    
    /**
     * Test single run.
     */
    @Test
    public void testSingleRun() {
        System.out.println(" - test single run");
        // single run
        singleRunWithMaxRuntime(search, SINGLE_RUN_RUNTIME, MAX_RUNTIME_TIME_UNIT);
    }
    
    /**
     * Test number of accepted/rejected moves.
     */
    @Test
    public void testNumAcceptedAndRejectedMoves() {
        System.out.println(" - test number of accepted/rejected moves");
        // attach separate listener to each replica to count number of accepted/rejected moves
        List<AcceptedRejectedMovesListener> listeners = new ArrayList<>();
        replicas.forEach(r -> {
            AcceptedRejectedMovesListener l = new AcceptedRejectedMovesListener();
            listeners.add(l);
            r.addSearchListener(l);
        });
        // single run
        singleRunWithMaxRuntime(search, SINGLE_RUN_RUNTIME, MAX_RUNTIME_TIME_UNIT);
        // verify
        assertEquals(listeners.stream().mapToLong(l -> l.getAccepted()).sum(), search.getNumAcceptedMoves());
        assertEquals(listeners.stream().mapToLong(l -> l.getRejected()).sum(), search.getNumRejectedMoves());
    }
    
    /**
     * Test single run with unsatisfiable constraint.
     */
    @Test
    public void testSingleRunWithUnsatisfiableConstraint() {
        System.out.println(" - test single run with unsatisfiable constraint");
        // add constraint
        problem.addMandatoryConstraint(new NeverSatisfiedConstraintStub());
        // single run
        singleRunWithMaxRuntime(search, SINGLE_RUN_RUNTIME, MAX_RUNTIME_TIME_UNIT);
        // verify
        assertNull(search.getBestSolution());
    }
    
    /**
     * Test single run with unsatisfiable penalizing constraint.
     */
    @Test
    public void testSingleRunWithUnsatisfiablePenalizingConstraint() {
        System.out.println(" - test single run with unsatisfiable penalizing constraint");
        // set constraint
        final double penalty = 7.8;
        problem.addPenalizingConstraint(new NeverSatisfiedPenalizingConstraintStub(penalty));
        // single run
        singleRunWithMaxRuntime(search, SINGLE_RUN_RUNTIME, MAX_RUNTIME_TIME_UNIT);
        // verify
        PenalizedEvaluation penEval = (PenalizedEvaluation) search.getBestSolutionEvaluation();
        assertEquals(penalty, penEval.getEvaluation().getValue() - penEval.getValue(), TestConstants.DOUBLE_COMPARISON_PRECISION);
    }
    
    /**
     * Test subsequent runs (maximizing).
     */
    @Test
    public void testSubsequentRuns() {
        System.out.println(" - test subsequent runs (maximizing)");
        // perform multiple runs (maximizing objective)
        multiRunWithMaximumRuntime(search, MULTI_RUN_RUNTIME, MAX_RUNTIME_TIME_UNIT, NUM_RUNS, true, true);
    }
    
    /**
     * Test subsequent runs (minimizing).
     */
    @Test
    public void testSubsequentRunsMinimizing() {
        System.out.println(" - test subsequent runs (minimizing)");
        // set minimizing
        obj.setMinimizing();
        // perform multiple runs
        multiRunWithMaximumRuntime(search, MULTI_RUN_RUNTIME, MAX_RUNTIME_TIME_UNIT, NUM_RUNS, false, true);
    }

    /**
     * Test subsequent runs with unsatisfiable constraint.
     */
    @Test
    public void testSubsequentRunsWithUnsatisfiableConstraint() {
        System.out.println(" - test subsequent runs with unsatisfiable constraint");
        // set constraint
        problem.addMandatoryConstraint(new NeverSatisfiedConstraintStub());
        // perform multiple runs (maximizing objective)
        multiRunWithMaximumRuntime(search, MULTI_RUN_RUNTIME, MAX_RUNTIME_TIME_UNIT, NUM_RUNS, true, true);
        // verify
        assertNull(search.getBestSolution());
    }
    
    /**
     * Test subsequent runs with unsatisfiable penalizing constraint.
     */
    @Test
    public void testSubsequentRunsWithUnsatisfiablePenalizingConstraint() {
        System.out.println(" - test subsequent runs with unsatisfiable penalizing constraint");
        // set constraint
        final double penalty = 7.8;
        problem.addPenalizingConstraint(new NeverSatisfiedPenalizingConstraintStub(penalty));
        // perform multiple runs (maximizing objective)
        multiRunWithMaximumRuntime(search, MULTI_RUN_RUNTIME, MAX_RUNTIME_TIME_UNIT, NUM_RUNS, true, true);
        // verify
        PenalizedEvaluation penEval = (PenalizedEvaluation) search.getBestSolutionEvaluation();
        assertEquals(penalty, penEval.getEvaluation().getValue() - penEval.getValue(), TestConstants.DOUBLE_COMPARISON_PRECISION);
    }
    
    /**
     * Test subsequent runs with penalizing constraint.
     */
    @Test
    public void testSubsequentRunsWithPenalizingConstraint() {
        System.out.println(" - test subsequent runs with penalizing constraint");
        // set constraint
        problem.addPenalizingConstraint(constraint);
        // perform 3 times as many runs as usual for this harder problem (maximizing objective)
        multiRunWithMaximumRuntime(search, MULTI_RUN_RUNTIME, MAX_RUNTIME_TIME_UNIT, 3*NUM_RUNS, true, true);
        // constraint satisfied ?
        if(problem.getViolatedConstraints(search.getBestSolution()).isEmpty()){
            System.out.println("   >>> constraint satisfied!");
        } else {
            System.out.println("   >>> constraint not satisfied, penalty "
                    + constraint.validate(search.getBestSolution(), data).getPenalty());
        }
    }
    
    private class AcceptedRejectedMovesListener implements SearchListener<SubsetSolution>{
        
        private long accepted = 0, rejected = 0;
        
        @Override
        public void searchStopped(Search<? extends SubsetSolution> search){
            NeighbourhoodSearch<?> nsearch = (NeighbourhoodSearch<?>) search;
            accepted += nsearch.getNumAcceptedMoves();
            rejected += nsearch.getNumRejectedMoves();
        }

        public long getAccepted() {
            return accepted;
        }

        public long getRejected() {
            return rejected;
        }
        
    }
    
}
