package solver;

import java.io.IOException;
import java.util.*;

import problem.Store;
import problem.Matrix;
import problem.ProblemSpec;

public class MySolver implements OrderingAgent {
	
	private ProblemSpec spec = new ProblemSpec();
	private Store store;
    private List<Matrix> probabilities;
	private int numItems;
	private double gamma;
	private double smallFraction;
	private int capacity;
	private int reductionThreshold;
	private int maxPurchase;
	private int maxReturn;
	private double heuristicThreshold;
	private Set<List<Integer>> possibleActions;
	private Map<List<Integer>, Set<List<Integer>>> validActions;
	private Map<List<Integer>, Set<List<Integer>>> validStates;
	private Map<List<Integer>, List<Integer>> bestActions;
	Set<List<Integer>> allStates;

	public int iterations;

	public MySolver(ProblemSpec spec) throws IOException {
	    this.spec = spec;
		store = spec.getStore();
        probabilities = spec.getProbabilities();
	}

	public void doOfflineComputation() {
		long beginOffline = System.currentTimeMillis();
		//Declarations
		allStates = new HashSet<List<Integer>>();
		Map<List<Integer>, Double> prevValues = new HashMap<List<Integer>, Double>();
		Map<List<Integer>, Double> currValues = new HashMap<List<Integer>, Double>();
		List<Integer> action = new ArrayList<Integer>();
		gamma = 0.3;
		smallFraction = 0.00000001;
		reductionThreshold = 5;
		heuristicThreshold = 0;
		capacity = store.getCapacity();
		maxPurchase = store.getMaxPurchase();
		maxReturn = store.getMaxReturns();
		numItems = spec.getInitialStock().size();
		long init = System.currentTimeMillis();
		possibleActions = allPossibleActions(numItems * 2);
		possibleActions = shrinkPossibleActions();

		long endI = System.currentTimeMillis() - init;
		System.out.print("Init took " + endI + "ms, and there are " + possibleActions.size() + " possible actions \n");
		validActions = new HashMap<List<Integer>, Set<List<Integer>>>();
		validStates = new HashMap<List<Integer>, Set<List<Integer>>>();
		bestActions = new HashMap<List<Integer>, List<Integer>>();


		//Initialise maps
		allStates.add(spec.getInitialStock());
		prevValues.put(spec.getInitialStock(), rewardFunction(spec.getInitialStock(), action));


		//Tests for reward function and transition function
		/*
		List<Integer> s = new ArrayList<>();
		s.add(0,1);
		s.add(1,0);

		List<Integer> a = new ArrayList<>();
		a.add(0,1);
		a.add(1,1);
		a.add(2,0);
		a.add(3,0);
		List<Integer> p = new ArrayList<>();
		p.add(0,1);
		p.add(1,1);

		double val1 = rewardFunction(s, a);
		double val2 = transitionFunction(s, a, p);

		System.out.print(val2);
		*/



		//Start at week one
		int t = 1;
		iterations = 0;
		while(!valueComp(prevValues, currValues) && ((System.currentTimeMillis()-beginOffline) < 155000)){
			//Update prev and curr V*
			if (currValues.size() != 0){
				prevValues = currValues;
				currValues = new HashMap<List<Integer>, Double>();
			}

			//Start timer
			long start = System.currentTimeMillis();


			if (iterations == reductionThreshold){
				heuristicThreshold = heuristic(prevValues);
			}


			//State selection optimisation (reduce from entire S)
			Set<List<Integer>> states = new HashSet<List<Integer>>();
			List<Integer> bestState = highestState(prevValues);

			//If no bestState is found, set to initState
			if (bestState.size() == 0){
				bestState = spec.getInitialStock();
			}

			//Get bestAction if possible, otherwise take none
			List<Integer> bestAction = bestActions.get(bestState);
			if (bestAction == null){
				bestAction =  new ArrayList<Integer>(Collections.nCopies(numItems*2, 0));
			}

			//Find all valid newStates from the above bestState and bestActions
			Set<List<Integer>> newStates = validNextStates(bestState, bestAction);
			if(newStates.size() != 0){
				states = newStates;
			}

			//For each state in state list
			for(List<Integer> state : states){
				if (iterations > reductionThreshold){
					double previousValue = prevValues.getOrDefault(state, 0.0);
					if(previousValue < heuristicThreshold){
						currValues.put(state, previousValue);
					} else{
						currValues.put(state, calcValueFunction(state, prevValues));

					}
				} else{
					currValues.put(state, calcValueFunction(state, prevValues));
				}

			}
			long stop = System.currentTimeMillis() - start;
			System.out.print("Iteration took " + stop + "ms, and there are " + states.size() + " states \n");

			iterations += 1;
			t += 1;
		}

	}
	
	public List<Integer> generateStockOrder(List<Integer> stockInventory,
											int numWeeksLeft) {

		List<Integer> itemOrders = new ArrayList<Integer>();
		List<Integer> itemReturns = new ArrayList<Integer>();

		//Calculate action
		List<Integer> action = bestActions.get(stockInventory);

		for (int i = 0; i < numItems; i++){
			itemOrders.add(i, action.get(i));
			itemReturns.add(i, action.get(i+numItems));
		}



		// combine orders and returns to get change for each item type
		List<Integer> order = new ArrayList<Integer>(itemOrders.size());
		for(int i = 0; i < itemOrders.size(); i++) {
			order.add(itemOrders.get(i) - itemReturns.get(i));
		}
		return order;
	}


	private List<Integer> highestState(Map<List<Integer>, Double> prevValues){
		double highest = 0;
		List<Integer> bestState = new ArrayList<>();
		//double gamma = 1;
		int size = prevValues.size();
		for(List<Integer> s : prevValues.keySet()){
			double max = prevValues.get(s);
			if (max > highest){
				highest = max;
				bestState = s;
			}
		}
		//avg *= gamma;
		return bestState;
	}

	private double heuristic(Map<List<Integer>, Double> prevValues){
		double avg = 0;
		//double gamma = 1;
		int size = prevValues.size();
		for(double max : prevValues.values()){
			avg += max;
		}
		avg = avg / size;
		//avg *= gamma;
		return avg;
	}

	private boolean valueComp(Map<List<Integer>, Double> prevValues, Map<List<Integer>, Double> currValues){
		if (prevValues.size() != currValues.size()){
			return false;
		}
		for(List<Integer> state : currValues.keySet()){
			if(!(currValues.containsKey(state) && prevValues.containsKey(state))){
				return false;
			}
			if (!((Math.abs(prevValues.get(state) - currValues.get(state))) < smallFraction)) {
				return false;
			}
		}
		return true;
	}

	private double calcValueFunction(List<Integer> state, Map<List<Integer>, Double> prevValues){

		double maxValue = -Double.MAX_VALUE;
		List<Integer> bestAction = new ArrayList<>();


		for (List<Integer> action: validActions(state)) {

			double reward = rewardFunction(state, action);
			double futureReward = 0;

			for (List<Integer> sPrime : validNextStates(state, action)) {

				/*if(!allStates.contains(sPrime)){
					allStates.add(sPrime);
				}*/

				futureReward += transitionFunction(state, action, sPrime) * prevValues.getOrDefault(sPrime, 0.0);
			}



			double newValue = reward + gamma * futureReward;
			if (newValue > maxValue) {
				maxValue = newValue;
				bestAction = action;
			}


		}
		if (bestAction.size() != 0){
			bestActions.put(state, bestAction);
		}

		return maxValue;

	}

	private Set<List<Integer>> shrinkPossibleActions(){
		Set<List<Integer>> actions = new HashSet<List<Integer>>();
		for(List<Integer> action : possibleActions){
			boolean possible = true;
			int totalOrdered = 0;
			int totalReturned = 0;
			for (int i = 0; i < numItems; i++){
				int ordered = action.get(i);
				int returned = action.get(i + numItems);
				totalOrdered += ordered;
				totalReturned += returned;
			}
			if (totalOrdered > maxPurchase) {
				possible = false;
			}
			if (totalReturned > maxReturn) {
				possible = false;
			}
			if (possible) actions.add(action);
		}
		return actions;
	}

	private Set<List<Integer>> validActions(List<Integer> state){
		//long start = System.currentTimeMillis();
		Set<List<Integer>> actions = new HashSet<List<Integer>>();


		if (validActions.containsKey(state)){
			return validActions.get(state);
		}
		for(List<Integer> action : possibleActions){
			boolean possible = true;
			//int totalOrdered = 0;
			//int totalReturned = 0;
			for (int i = 0; i < numItems; i++){
				int stock = state.get(i);
				int ordered = action.get(i);
				int returned = action.get(i + numItems);
				int newStock = stock + ordered - returned;
				if (newStock < 0 || newStock >= capacity){
					possible = false;
					continue;
				}
				//totalOrdered += ordered;
				//totalReturned += returned;
			}
			/*if (totalOrdered > maxPurchase) {
				possible = false;
			}
			if (totalReturned > maxReturn) {
				possible = false;
			}*/
			if (possible) actions.add(action);


		}
		validActions.put(state, actions);
		//long stop = System.currentTimeMillis() - start;
		//System.out.print("Valid actions took " + 0 + "ms, and there are " + actions.size() + " actions \n");
		return actions;
	}

	private Set<List<Integer>> allPossibleActions(int depth){
		Set<List<Integer>> oldActions = new HashSet<List<Integer>>();
		Set<List<Integer>> actions = new HashSet<List<Integer>>();

		//If we're not at shallowest depth
		if (depth != numItems*2){
			//At deepest depth
			if (depth == 0){
				Set<List<Integer>> s = new HashSet<List<Integer>>();
				for (int i = 0; i <=maxPurchase; i++){
					List<Integer> l = new ArrayList<Integer>(Collections.nCopies(numItems*2, 0));
					//List<Integer> l = new ArrayList<>();
					l.set(depth, i);
					/*
					for (int j = 0; j < numItems*2; j++){
						if (j == depth){

						}
						else {
							l.add(j, 0);
						}

					}*/
					s.add(l);
				}
				return s;
			//All middle depths
			} else {
				oldActions = allPossibleActions(depth - 1);
				for (List<Integer> baseList : oldActions) {
					if(depth > numItems/2.0){
						for (int i = 0; i <= maxReturn; i++){
							List<Integer> l = new ArrayList<>(baseList);
							l.set(depth, i);
						/*for (int j = 0; j < numItems*2; j++){
							if (j == depth){
								l.set(depth, i);
							}

						}*/
							actions.add(l);
						}
					} else {
						for (int i = 0; i <= maxPurchase; i++){
							List<Integer> l = new ArrayList<>(baseList);
							l.set(depth, i);
						/*for (int j = 0; j < numItems*2; j++){
							if (j == depth){
								l.set(depth, i);
							}

						}*/
							actions.add(l);
						}
					}


				}
				return actions;
			}
		//Start recursion
		} else {

			actions = allPossibleActions(depth - 1);

		}

		return actions;
	}

	private Set<List<Integer>> validNextStates(List<Integer> state, List<Integer> action){
		Set<List<Integer>> states = new HashSet<List<Integer>>();



		List<Integer> determinedState = nextState(state, action);
		if (!isValidState(determinedState)){
			return states;
		}

		if (validStates.containsKey(determinedState)){
			return validStates.get(determinedState);
		}
		//For each non deterministic result, find possible states that are valid
		states = recursiveStates(determinedState, numItems);

		validStates.put(determinedState, states);
		return states;
	}

	private Set<List<Integer>> recursiveStates(List<Integer> state, int depth){
		Set<List<Integer>> oldStates = new HashSet<List<Integer>>();
		Set<List<Integer>> states = new HashSet<List<Integer>>();

		//Not at shallowest depth
		if (depth != numItems){
			//Deepest depth / bottom
			if (depth == 0){
				Set<List<Integer>> s = new HashSet<List<Integer>>();
				for(int j = 0; j <= state.get(depth); j++){
					List<Integer> newState = new ArrayList<Integer>(state);
					newState.set(depth, j);
					s.add(newState);
					//if(!allStates.contains(newState)) allStates.add(newState);
				}
				return s;

			//Middle depths
			} else {
				oldStates = recursiveStates(state, depth - 1);
				states = new HashSet<List<Integer>>(oldStates);
				for (List<Integer> baseList : oldStates) {

					for(int j = 0; j <= baseList.get(depth); j++){
						List<Integer> newState = new ArrayList<Integer>(baseList);
						newState.set(depth, j);
						states.add(newState);
						//if(!allStates.contains(newState)) allStates.add(newState);
					}

				}
				return states;
			}
		//Start recursion
		} else {

			states = recursiveStates(state, depth - 1);

		}

		return states;
	}


	private List<Integer> nextState(List<Integer> state,List<Integer> action){
		List<Integer> determinedState = new ArrayList<>();
		for(int i = 0; i < numItems; i++){
			int newStock = state.get(i) + action.get(i) - action.get(i + numItems);
			determinedState.add(i, newStock);
		}
		return determinedState;
	}

	private boolean isValidState(List<Integer> state){
		if (state.size() != numItems) return false;
		for (int i = 0; i < numItems; i++){
			int val = state.get(i);
			if (val < 0 || val > capacity) return false;
		}
		return true;
	}



	private double rewardFunction(List<Integer> state,  List<Integer> action){
		//List<Double> rewards = new ArrayList<Double>();
		//For each item
		double totalReward = 0;
		for (int i = 0; i < numItems; i++) {
			int stock = state.get(i);
			int returned;
			int ordered;
			double price = spec.getPrices().get(i);
			if (action.size() != 0){
				returned = action.get(i+numItems);
				ordered = action.get(i);
			} else{
				returned = 0;
				ordered = 0;
			}

			int newStock = stock + ordered - returned;
			double reward = 0;
			Matrix m = probabilities.get(i);

			//J is number of sales
			for (int j = 0; j <= capacity; j++){
				if (newStock - j < 0){
					int numSalesMissed = Math.abs(newStock - j);
					double sales = (((j-numSalesMissed) * price * 0.75));
					double missedSales = (numSalesMissed * price * 0.25);
					reward += m.get(newStock,j) * (sales - missedSales);

				} else {
					reward += m.get(newStock, j) * ((j * price * 0.75));

				}

			}
			/*for (int j = newStock + 1; j < store.getCapacity(); j++){
				reward += (j- stock - ordered + returned) * probabilities.get(i).get(newStock,j);
			}*/
			//reward = reward * spec.getPenaltyFee();
			//rewards.add(i, reward);
			totalReward += reward;
		}

		//Add up rewards for each item
		/*for (int i = 0; i < state.size(); i++) {
			totalReward += rewards.get(i);
		}*/
		return totalReward;
		//various different penalties
	}

	private double transitionFunction(List<Integer> state, List<Integer> action, List<Integer> statePrime){
		double probability = 1;


		//For each item in state
		for (int i = 0; i < state.size(); i++) {
			int stock = state.get(i);
			int stockPrime = statePrime.get(i);
			int ordered = action.get(i);
			int returned = action.get(i + numItems);
			int newStock = stock + ordered - returned;
			double p = 0;
			Matrix m = probabilities.get(i);
			if (stockPrime > newStock){
				p = 0;
			} else if(0 < stockPrime && stockPrime <= newStock){
				p = m.get(newStock,newStock-stockPrime);
			} else if (stockPrime == 0){
				for (int j = newStock; j <= capacity; j++) {
					p += m.get(newStock,j);
				}
			}
			probability = probability * p;
		}
		return probability;
	}

}

