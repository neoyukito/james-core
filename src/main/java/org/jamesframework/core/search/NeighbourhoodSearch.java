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

package org.jamesframework.core.search;

import java.util.Arrays;
import org.jamesframework.core.search.status.SearchStatus;
import java.util.Collection;
import java.util.function.Predicate;
import org.jamesframework.core.exceptions.SearchException;
import org.jamesframework.core.problems.Problem;
import org.jamesframework.core.problems.sol.Solution;
import org.jamesframework.core.problems.constraints.validations.Validation;
import org.jamesframework.core.problems.objectives.evaluations.Evaluation;
import org.jamesframework.core.search.cache.EvaluatedMoveCache;
import org.jamesframework.core.search.cache.SingleEvaluatedMoveCache;
import org.jamesframework.core.search.neigh.Move;
import org.jamesframework.core.util.JamesConstants;

/**
 * A neighbourhood search is a specific kind of local search in which the current solution is repeatedly modified by
 * applying moves, generated by one or more neighbourhoods, that transform this solution into a similar, neighbouring
 * solution. Generated moves can either be accepted, in which case the current solution is updated, or rejected, in
 * which case the current solution is retained. The number of accepted and rejected moves during the current or last
 * run can be accessed. This additional metadata applies to the current run only.
 * 
 * @param <SolutionType> solution type of the problems that may be solved using this search, required to extend {@link Solution}
 * @author <a href="mailto:herman.debeukelaer@ugent.be">Herman De Beukelaer</a>
 */
public abstract class NeighbourhoodSearch<SolutionType extends Solution> extends LocalSearch<SolutionType> {

    /******************/
    /* PRIVATE FIELDS */
    /******************/
    
    // number of accepted/rejected moves during current run
    private long numAcceptedMoves, numRejectedMoves;
    
    // evaluated move cache
    private EvaluatedMoveCache cache;
    
    /***************/
    /* CONSTRUCTOR */
    /***************/
    
    /**
     * Create a new neighbourhood search to solve the given problem, with default name "NeighbourhoodSearch".
     * 
     * @throws NullPointerException if <code>problem</code> is <code>null</code>
     * @param problem problem to solve
     */
    public NeighbourhoodSearch(Problem<SolutionType> problem){
        this(null, problem);
    }
    
    /**
     * Create a new neighbourhood search to solve the given problem, with a custom name. If <code>name</code> is
     * <code>null</code>, the default name "NeighbourhoodSearch" will be assigned.
     * 
     * @throws NullPointerException if <code>problem</code> is <code>null</code>
     * @param problem problem to solve
     * @param name custom search name
     */
    public NeighbourhoodSearch(String name, Problem<SolutionType> problem){
        super(name != null ? name : "NeighbourhoodSearch", problem);
        // initialize per run metadata
        numAcceptedMoves = JamesConstants.INVALID_MOVE_COUNT;
        numRejectedMoves = JamesConstants.INVALID_MOVE_COUNT;
        // set default (single) evaluated move cache
        cache = new SingleEvaluatedMoveCache();
    }
    
    /*********/
    /* CACHE */
    /*********/
    
    /**
     * Sets a custom evaluated move cache, which is used to avoid repeated evaluation or validation of the same move
     * from the same current solution. By default, a {@link SingleEvaluatedMoveCache} is used. Note that this method
     * may only be called when the search is idle. If the cache is set to <code>null</code>, no caching will be applied.
     * 
     * @param cache custom evaluated move cache
     * @throws SearchException if the search is not idle
     */
    public void setEvaluatedMoveCache(EvaluatedMoveCache cache){
        // acquire status lock
        synchronized(getStatusLock()){
            // assert idle
            assertIdle("Cannot set custom evaluated move cache in neighbourhood search.");
            // set cache
            this.cache = cache;
        }
    }
    
    /******************/
    /* INITIALIZATION */
    /******************/
    
    /**
     * When a neighbourhood search is started, the number of accepted and rejected moves is reset to zero.
     */
    @Override
    protected void searchStarted(){
        // call super
        super.searchStarted();
        // reset neighbourhood search specific, per run metadata
        numAcceptedMoves = 0;
        numRejectedMoves = 0;
    }
    
    /*****************************************/
    /* METADATA APPLYING TO CURRENT RUN ONLY */
    /*****************************************/
    
    /**
     * <p>
     * Get the number of moves accepted during the <i>current</i> (or last) run. The precise return value
     * depends on the status of the search:
     * </p>
     * <ul>
     *  <li>
     *   If the search is either RUNNING or TERMINATING, this method returns the number of moves accepted
     *   since the current run was started.
     *  </li>
     *  <li>
     *   If the search is IDLE, the total number of moves accepted during the last run is returned, if any.
     *   Before the first run, {@link JamesConstants#INVALID_MOVE_COUNT}.
     *  </li>
     *  <li>
     *   While INITIALIZING the current run, {@link JamesConstants#INVALID_MOVE_COUNT} is returned.
     *  </li>
     * </ul>
     * <p>
     * The return value is always positive, except in those cases when {@link JamesConstants#INVALID_MOVE_COUNT}
     * is returned.
     * </p>
     * 
     * @return number of moves accepted during the current (or last) run
     */
    public long getNumAcceptedMoves(){
        // depends on search status: synchronize with status updates
        synchronized(getStatusLock()){
            if(getStatus() == SearchStatus.INITIALIZING){
                // initializing
                return JamesConstants.INVALID_MOVE_COUNT;
            } else {
                // idle, running or terminating
                return numAcceptedMoves;
            }
        }
    }
    
    /**
     * <p>
     * Get the number of moves rejected during the <i>current</i> (or last) run. The precise return value
     * depends on the status of the search:
     * </p>
     * <ul>
     *  <li>
     *   If the search is either RUNNING or TERMINATING, this method returns the number of moves rejected
     *   since the current run was started.
     *  </li>
     *  <li>
     *   If the search is IDLE, the total number of moves rejected during the last run is returned, if any.
     *   Before the first run, {@link JamesConstants#INVALID_MOVE_COUNT}.
     *  </li>
     *  <li>
     *   While INITIALIZING the current run, {@link JamesConstants#INVALID_MOVE_COUNT} is returned.
     *  </li>
     * </ul>
     * <p>
     * The return value is always positive, except in those cases when {@link JamesConstants#INVALID_MOVE_COUNT}
     * is returned.
     * </p>
     * 
     * @return number of moves rejected during the current (or last) run
     */
    public long getNumRejectedMoves(){
        // depends on search status: synchronize with status updates
        synchronized(getStatusLock()){
            if(getStatus() == SearchStatus.INITIALIZING){
                // initializing
                return JamesConstants.INVALID_MOVE_COUNT;
            } else {
                // idle, running or terminating
                return numRejectedMoves;
            }
        }
    }
    
    /***********************/
    /* PROTECTED UTILITIES */
    /***********************/
    
    /**
     * When updating the current solution in a neighbourhood search, the evaluated move cache is
     * cleared because it is no longer valid for the new current solution.
     * 
     * @param solution new current solution
     * @param evaluation evaluation of new current solution
     * @param validation validation of new current solution
     */
    @Override
    protected void updateCurrentSolution(SolutionType solution, Evaluation evaluation, Validation validation){
        // call super
        super.updateCurrentSolution(solution, evaluation, validation);
        // clear evaluated move cache
        if(cache != null){
            cache.clear();
        }
    }
    
    /**
     * Evaluates a move to be applied to the current solution. If this move has been evaluated
     * before and the obtained evaluation is still available in the cache, the cached evaluation
     * will be returned. Else, the evaluation will be computed and offered to the cache.
     * 
     * @param move move to be applied to the current solution
     * @return evaluation of obtained neighbour, possibly retrieved from the evaluated move cache
     */
    protected Evaluation evaluate(Move<? super SolutionType> move){
        Evaluation eval = null;
        // check cache
        if(cache != null){
            eval = cache.getCachedMoveEvaluation(move);
        }
        if(eval != null){
            // cache hit: return cached value
            return eval;
        } else {
            // cache miss: evaluate and cache
            eval = getProblem().evaluate(move, getCurrentSolution(), getCurrentSolutionEvaluation());
            if(cache != null){
                cache.cacheMoveEvaluation(move, eval);
            }
            return eval;
        }
    }
    
    /**
     * Validates a move to be applied to the current solution. If this move has been validated
     * before and the obtained validation is still available in the cache, the cached validation
     * will be returned. Else, the validation will be computed and offered to the cache.
     * 
     * @param move move to be applied to the current solution
     * @return validation of obtained neighbour, possibly retrieved from the evaluated move cache
     */
    protected Validation validate(Move<? super SolutionType> move){
        Validation val = null;
        // check cache
        if(cache != null){
            val = cache.getCachedMoveValidation(move);
        }
        if(val != null){
            // cache hit: return cached value
            return val;
        } else {
            // cache miss: validate and cache
            val = getProblem().validate(move, getCurrentSolution(), getCurrentSolutionValidation());
            if(cache != null){
                cache.cacheMoveValidation(move, val);
            }
            return val;
        }
    }
    
    /**
     * <p>
     * Checks whether applying the given move to the current solution yields a valid improvement.
     * An improvement is made if and only if (1) the given move is not <code>null</code>,
     * (2) the move is valid, and (3) the obtained neighbour has a better evaluation than the
     * current solution or the current solution is invalid.
     * </p>
     * <p>
     * Note that computed values are cached to prevent multiple evaluations or validations of the same move.
     * </p>
     * 
     * @param move move to be applied to the current solution
     * @return <code>true</code> if applying this move yields a valid improvement
     */
    protected boolean isImprovement(Move<? super SolutionType> move){
        return move != null
                && validate(move).passed()
                && (!getCurrentSolutionValidation().passed()
                    || computeDelta(evaluate(move), getCurrentSolutionEvaluation()) > 0);
    }
    
    /**
     * <p>
     * Get the best valid move among a collection of possible moves. The best valid move is the one yielding the
     * largest delta (see {@link #computeDelta(Evaluation, Evaluation)}) when being applied to the current solution.
     * </p>
     * <p>
     * If <code>requireImprovement</code> is set to <code>true</code>, only moves that improve the current solution
     * are considered, i.e. moves that yield a positive delta (unless the current solution is invalid, then all
     * valid moves are improvements). Any number of additional filters can be specified so that moves are only
     * considered if they pass through all filters. Each filter is a predicate that should return <code>true</code>
     * if a given move is to be considered. If any filter returns <code>false</code> for a specific move, this
     * move is discarded.
     * </p>
     * <p>
     * Returns <code>null</code> if no move is found that satisfies all conditions.
     * </p>
     * <p>
     * Note that all computed evaluations and validations are cached.
     * Before returning the selected best move, if any, its evaluation and validity are cached
     * again to maximize the probability that these values will remain available in the cache.
     * </p>
     * 
     * @param moves collection of possible moves
     * @param requireImprovement if set to <code>true</code>, only improving moves are considered
     * @param filters additional move filters
     * @return best valid move, may be <code>null</code>
     */
    @SafeVarargs
    protected final Move<? super SolutionType> getBestMove(Collection<? extends Move<? super SolutionType>> moves,
                                                           boolean requireImprovement,
                                                           Predicate<? super Move<? super SolutionType>>... filters){
        // track best valid move + corresponding evaluation, validation and delta
        Move<? super SolutionType> bestMove = null;
        double bestMoveDelta = -Double.MAX_VALUE, curMoveDelta;
        Evaluation curMoveEvaluation, bestMoveEvaluation = null;
        Validation curMoveValidation, bestMoveValidation = null;
        // go through all moves
        for (Move<? super SolutionType> move : moves) {
            // check filters
            if(Arrays.stream(filters).allMatch(filter -> filter.test(move))){
                // validate move
                curMoveValidation = validate(move);
                if (curMoveValidation.passed()) {
                    // evaluate move
                    curMoveEvaluation = evaluate(move);
                    // compute delta
                    curMoveDelta = computeDelta(curMoveEvaluation, getCurrentSolutionEvaluation());
                    // compare with current best move
                    if (curMoveDelta > bestMoveDelta    // higher delta
                            && (!requireImprovement     // ensure improvement, if required
                                || curMoveDelta > 0
                                || !getCurrentSolutionValidation().passed())) {
                        bestMove = move;
                        bestMoveDelta = curMoveDelta;
                        bestMoveEvaluation = curMoveEvaluation;
                    }
                }
            }
        }
        // re-cache best move, if any
        if(bestMove != null && cache != null){
            cache.cacheMoveEvaluation(bestMove, bestMoveEvaluation);
            cache.cacheMoveValidation(bestMove, bestMoveValidation);
        }
        // return best move
        return bestMove;
    }
    
    /**
     * Accept the given move by applying it to the current solution. Updates the evaluation and validation of
     * the current solution and checks whether a new best solution has been found. The updates only take place
     * if the applied move yields a valid neighbour, else calling this method does not have any effect and
     * <code>false</code> is returned.
     * <p>
     * After updating the current solution, the evaluated move cache is cleared as this cache is no longer valid
     * for the new current solution. Furthermore, any local search listeners are informed and the number of
     * accepted moves is updated.
     * 
     * @param move accepted move to be applied to the current solution
     * @return <code>true</code> if the update has been successfully performed,
     *         <code>false</code> if the update was cancelled because the obtained
     *         neighbour is invalid
     */
    protected boolean accept(Move<? super SolutionType> move){
        // validate move (often retrieved from cache)
        Validation newValidation = validate(move);
        if(newValidation.passed()){
            // evaluate move (often retrieved from cache)
            Evaluation newEvaluation = evaluate(move);
            // apply move to current solution (IMPORTANT: after evaluation/validation of the move!)
            move.apply(getCurrentSolution());
            // update current solution and best solution
            updateCurrentAndBestSolution(getCurrentSolution(), newEvaluation, newValidation);
            // increase accepted move counter
            incNumAcceptedMoves(1);
            // update successful
            return true;
        } else {
            // update cancelled: invalid neighbour
            return false;
        }
    }
    
    /**
     * Increase the number of accepted moves with the given value.
     * 
     * @param inc value with which the number of accepted moves is increased
     */
    protected void incNumAcceptedMoves(long inc){
        numAcceptedMoves += inc;
    }
    
    /**
     * Reject the given move. The default implementation ignores the specified move and simply updates
     * the <em>number</em> of rejected moves. Specific searches can override this behaviour if desired.
     * 
     * @param move rejected move (ignored)
     */
    protected void reject(Move<? super SolutionType> move){
        incNumRejectedMoves(1);
    }
    
    /**
     * Increase the number of rejected moves with the given value.
     * 
     * @param inc value with which the number of rejected moves is increased
     */
    protected void incNumRejectedMoves(long inc){
        numRejectedMoves += inc;
    }
    
}
