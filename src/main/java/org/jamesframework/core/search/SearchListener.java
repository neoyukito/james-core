//  Copyright 2014 Herman De Beukelaer
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package org.jamesframework.core.search;

import org.jamesframework.core.exceptions.IncompatibleSearchListenerException;
import org.jamesframework.core.problems.solutions.Solution;

/**
 * Interface of a listener which may be attached to a search with the specified solution type (or a more specific solution type).
 * It will be informed whenever the search has started, stopped, found a new best solution, completed a step or fired a search message.
 * Every callback receives a reference to the search that called it, which may be cast to a specific search type if required; when
 * this cast fails because a listener has been attached to an incompatible search, an {@link IncompatibleSearchListenerException}
 * may be thrown.
 * 
 * @param <SolutionType> solution type of the search to which the listener may be attached, required to extend {@link Solution}
 * @author Herman De Beukelaer <herman.debeukelaer@ugent.be>
 */
public interface SearchListener<SolutionType extends Solution> {
    
    /**
     * Called when the search has started. Should be called only once during a search run.
     * 
     * @param search search which has started
     */
    public void searchStarted(Search<? extends SolutionType> search);
    
    /**
     * Called when the search has stopped. Should be called only once during a search run.
     * 
     * @param search search which has stopped
     */
    public void searchStopped(Search<? extends SolutionType> search);
    
    /**
     * Called when the search sends a message to its listeners.
     * 
     * @param search search which sends a message
     * @param message the message sent
     */
    public void searchMessage(Search<? extends SolutionType> search, String message);
    
    /**
     * Called when the search has found a new best solution. Should be called exactly once for every improvement.
     * 
     * @param search search which has found a new best solution
     * @param newBestSolution new best solution found
     * @param newBestSolutionEvaluation evaluation of the new best solution
     */
    public void newBestSolution(Search<? extends SolutionType> search, SolutionType newBestSolution, double newBestSolutionEvaluation);
    
    /**
     * Called when the search has completed a step. Should be called exactly once for every completed step.
     * 
     * @param search search which has completed a step
     * @param numSteps number of steps completed so far (during the current search run)
     */
    public void stepCompleted(Search<? extends SolutionType> search, long numSteps);

}