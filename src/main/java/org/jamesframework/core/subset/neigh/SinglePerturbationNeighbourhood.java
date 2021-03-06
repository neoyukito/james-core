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

package org.jamesframework.core.subset.neigh;

import java.util.ArrayList;
import org.jamesframework.core.subset.neigh.moves.SubsetMove;
import org.jamesframework.core.subset.neigh.moves.SwapMove;
import org.jamesframework.core.subset.neigh.moves.AdditionMove;
import org.jamesframework.core.subset.neigh.moves.DeletionMove;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.jamesframework.core.subset.SubsetSolution;
import org.jamesframework.core.util.RouletteSelector;
import org.jamesframework.core.util.SetUtilities;

/**
 * <p>
 * A subset neighbourhood that generates swap moves (see {@link SwapMove}), addition moves (see {@link AdditionMove})
 * and deletion moves (see {@link DeletionMove}). All three generated move types are subtypes of {@link SubsetMove}.
 * Applying an addition or deletion move to a given subset solution will increase, respectively decrease, the number
 * of selected items. Therefore, this neighbourhood is also suited for variable size subset selection problems, in
 * contrast to a {@link SingleSwapNeighbourhood}.
 * </p>
 * <p>
 * When sampling random moves from this neighbourhood, every individual move is generated with equal probability,
 * taking into account the different number of possible moves of each type. In general this means that more swap
 * moves will be generated because there are usually more possible swaps compared to deletions or additions. If
 * it is desired to change this behaviour, take a look at the composite neighbourhood which is provided in the
 * extensions module and allows to combine any set of neighbourhoods with custom weights.
 * </p>
 * <p>
 * A single perturbation neighbourhood respects the optional minimum and maximum subset size specified at construction.
 * When the given subset solution has minimal size, no deletion moves will be generated. Similarly, when the current
 * solution has maximum size, no addition moves will be generated.
 * </p>
 * <p>
 * If desired, a set of fixed IDs can be provided which are not allowed to be added, deleted nor swapped.
 * None of the moves generated by this neighbourhood will ever select nor deselect any of these fixed IDs.
 * </p>
 * <p>
 * Note that this neighbourhood is thread-safe: it can be safely used to concurrently generate moves in different
 * searches running in separate threads.
 * </p>
 * 
 * @author <a href="mailto:herman.debeukelaer@ugent.be">Herman De Beukelaer</a>
 */
public class SinglePerturbationNeighbourhood extends SubsetNeighbourhood {

    // move type enum used for randomly picking a move type
    private enum MoveType {
        ADDITION, DELETION, SWAP
    }
    
    // minimum and maximum subset size
    private final int minSubsetSize;
    private final int maxSubsetSize;
    
    /**
     * Creates a new single perturbation neighbourhood without size limits.
     */
    public SinglePerturbationNeighbourhood(){
        this(0, Integer.MAX_VALUE);
    }
    
    /**
     * Creates a new single perturbation neighbourhood with given minimum and maximum subset size.
     * Only moves that result in a valid solution size after application to the current solution
     * will ever be generated. Positive values are required for the minimum and maximum size,
     * with minimum smaller than or equal to maximum; else, an exception is thrown.
     * 
     * @param minSubsetSize minimum subset size (&ge; 0)
     * @param maxSubsetSize maximum subset size (&gt; 0)
     * @throws IllegalArgumentException if minimum size is not positive, maximum size is not strictly
     *                                  positive, or minimum &gt; maximum
     */
    public SinglePerturbationNeighbourhood(int minSubsetSize, int maxSubsetSize){
        this(minSubsetSize, maxSubsetSize, null);
    }
    
    /**
     * Creates a new single perturbation neighbourhood with given minimum and maximum subset size,
     * providing a set of fixed IDs which are not allowed to be added, deleted nor swapped.
     * Only moves that result in a valid solution size after application to the current solution
     * will ever be generated. Positive values are required for the minimum and maximum size,
     * with minimum smaller than or equal to maximum; else, an exception is thrown.
     * 
     * @param minSubsetSize minimum subset size (&ge; 0)
     * @param maxSubsetSize maximum subset size (&gt; 0)
     * @param fixedIDs set of fixed IDs
     * @throws IllegalArgumentException if minimum size is not positive, maximum size is not strictly
     *                                  positive, or minimum &gt; maximum
     */
    public SinglePerturbationNeighbourhood(int minSubsetSize, int maxSubsetSize, Set<Integer> fixedIDs){
        super(fixedIDs);
        // validate sizes
        if(minSubsetSize < 0){
            throw new IllegalArgumentException("Error while creating single perturbation neighbourhood: minimum subset size should be non-negative.");
        }
        if(maxSubsetSize <= 0){
            throw new IllegalArgumentException("Error while creating single perturbation neighbourhood: maximum subset size should be strictly positive.");
        }
        if(minSubsetSize > maxSubsetSize){
            throw new IllegalArgumentException("Error while creating single perturbation neighbourhood: "
                                                + "minimum subset size should be smaller than or equal to maximum subset size.");
        }
        this.minSubsetSize = minSubsetSize;
        this.maxSubsetSize = maxSubsetSize;
    }
    
    /**
     * <p>
     * Generates a random swap, deletion or addition move that transforms the given subset solution into
     * a neighbour within the minimum and maximum allowed subset size. If no valid move can be generated,
     * <code>null</code> is returned. If any fixed IDs have been specified, these will not be considered
     * for deletion nor addition.
     * </p>
     * <p>
     * Note that every individual move is generated with equal probability, taking into account the
     * different number of possible moves of each type.
     * </p>
     * 
     * @param solution solution for which a random move is generated
     * @param rnd source of randomness used to generate random move
     * @return random move, <code>null</code> if no valid move can be generated
     */
    @Override
    public SubsetMove getRandomMove(SubsetSolution solution, Random rnd) {
        // get set of candidate IDs for deletion and addition (fixed IDs are discarded)
        Set<Integer> removeCandidates = getRemoveCandidates(solution);
        Set<Integer> addCandidates = getAddCandidates(solution);
        // compute number of possible moves of each type (addition, deletion, swap)
        int numAdd = canAdd(solution, addCandidates) ? addCandidates.size() : 0;
        int numDel = canRemove(solution, removeCandidates) ? removeCandidates.size(): 0;
        int numSwap = canSwap(solution, addCandidates, removeCandidates) ? addCandidates.size()*removeCandidates.size() : 0;
        // pick move type using roulette selector
        MoveType selectedMoveType = RouletteSelector.select(
                                        Arrays.asList(MoveType.ADDITION, MoveType.DELETION, MoveType.SWAP),
                                        Arrays.asList((double) numAdd, (double) numDel, (double) numSwap),
                                        rnd
                                    );
        // in case of no valid moves: return null
        if(selectedMoveType == null){
            return null;
        } else {
            // generate random move of chosen type
            switch(selectedMoveType){
                case ADDITION : return new AdditionMove(SetUtilities.getRandomElement(addCandidates, rnd));
                case DELETION : return new DeletionMove(SetUtilities.getRandomElement(removeCandidates, rnd));
                case SWAP     : return new SwapMove(
                                                    SetUtilities.getRandomElement(addCandidates, rnd),
                                                    SetUtilities.getRandomElement(removeCandidates, rnd)
                                                );
                default : throw new Error("This should never happen. If this exception is thrown, "
                                            + "there is a serious bug in SinglePerturbationNeighbourhood.");
            }
        }
    }

    /**
     * Generate all valid swap, deletion and addition moves that transform the given subset solution into
     * a neighbour within the minimum and maximum allowed subset size. The returned list may be empty,
     * if no valid moves exist. If any fixed IDs have been specified, these will not be considered
     * for deletion nor addition.
     * 
     * @param solution solution for which a set of all valid moves is generated
     * @return list of all valid swap, deletion and addition moves
     */
    @Override
    public List<SubsetMove> getAllMoves(SubsetSolution solution) {
        // get set of candidate IDs for deletion and addition (fixed IDs are discarded)
        Set<Integer> removeCandidates = getRemoveCandidates(solution);
        Set<Integer> addCandidates = getAddCandidates(solution);
        // create empty list of moves
        List<SubsetMove> moves = new ArrayList<>();
        // generate all addition moves, if valid
        if(canAdd(solution, addCandidates)){
            // create addition move for each add candidate
            addCandidates.forEach(add -> moves.add(new AdditionMove(add)));
        }
        // generate all deletion moves, if valid
        if(canRemove(solution, removeCandidates)){
            // create deletion move for each remove candidate
            removeCandidates.forEach(remove -> moves.add(new DeletionMove(remove)));
        }
        // generate all swap moves, if valid
        if(canSwap(solution, addCandidates, removeCandidates)){
            // create swap move for each combination of add and remove candidate
            addCandidates.forEach(add -> {
                removeCandidates.forEach(remove -> {
                    moves.add(new SwapMove(add, remove));
                });
            });
        }
        // return generated moves
        return moves;
    }
    
    /**
     * Check if it is allowed to add one more item to the selection.
     * 
     * @param solution solution for which moves are generated
     * @param addCandidates set of candidate IDs to be added
     * @return <code>true</code> if it is allowed to add an item to the selection
     */
    private boolean canAdd(SubsetSolution solution, Set<Integer> addCandidates){
        return !addCandidates.isEmpty()
                    && isValidSubsetSize(solution.getNumSelectedIDs()+1);
    }
    
    /**
     * Check if it is allowed to remove one more item from the selection.
     * 
     * @param solution solution for which moves are generated
     * @param deleteCandidates set of candidate IDs to be deleted
     * @return <code>true</code> if it is allowed to remove an item from the selection
     */
    private boolean canRemove(SubsetSolution solution, Set<Integer> deleteCandidates){
        return !deleteCandidates.isEmpty()
                    && isValidSubsetSize(solution.getNumSelectedIDs()-1);
    }
    
    /**
     * Check if it is possible to swap a selected and unselected item.
     * 
     * @param solution solution for which moves are generated
     * @param addCandidates set of candidate IDs to be added
     * @param deleteCandidates set of candidate IDs to be deleted
     * @return <code>true</code> if it is possible to perform a swap
     */
    private boolean canSwap(SubsetSolution solution, Set<Integer> addCandidates, Set<Integer> deleteCandidates){
        return !addCandidates.isEmpty()
                    && !deleteCandidates.isEmpty()
                    && isValidSubsetSize(solution.getNumSelectedIDs());
    }
    
    /**
     * Verifies whether the given subset size is valid, taking into account
     * the minimum and maximum size specified at construction.
     * 
     * @param size size to verify
     * @return <code>true</code> if size falls within bounds
     */
    private boolean isValidSubsetSize(int size){
        return size >= minSubsetSize && size <= maxSubsetSize;
    }

    /**
     * Get the minimum subset size specified at construction.
     * If no minimum size has been set this method returns 0.
     * 
     * @return minimum subset size
     */
    public int getMinSubsetSize() {
        return minSubsetSize;
    }

    /**
     * Get the maximum subset size specified at construction.
     * If no maximum size has been set this method returns {@link Integer#MAX_VALUE}.
     * 
     * @return maximum subset size
     */
    public int getMaxSubsetSize() {
        return maxSubsetSize;
    }

}
