package solver;

import java.io.IOException;
import java.util.*;

import problem.Store;
import problem.Matrix;
import problem.ProblemSpec;

public class MySolverc implements OrderingAgent {
	
	private ProblemSpec spec = new ProblemSpec();
	private Store store;
    private List<Matrix> probabilities;
	private int numItems;
	private double gamma;

	public MySolverc(ProblemSpec spec) throws IOException {
	    this.spec = spec;
		store = spec.getStore();
        probabilities = spec.getProbabilities();
	}


	//MCTS
	public void doOfflineComputation() {
		//Declarations
		Map<Integer, List<Integer>> states = new HashMap<Integer, List<Integer>>();
		Map<Integer, Double> prevValues = new HashMap<Integer, Double>();
		Map<Integer, Double> currValues = new HashMap<Integer, Double>();
		Map<Integer, List<Integer>> action = new HashMap<Integer, List<Integer>>();
		gamma = 0.99;
		numItems = spec.getInitialStock().size();

		//Initialise maps
		states.put(0, spec.getInitialStock());
		for (int i = 0; i < numItems; i++){
			prevValues.put(0, rewardFunction(spec.getInitialStock(), action));
			currValues.put(0, 0.0);
		}

		//Start at week one
		int t = 1;
		while(!valueComp(prevValues, currValues)){
			//For each state in state list
			for (int i = 0; i < numItems; i++){
				List<Integer> state = states.get(i);
				double value = valueFunction(state, prevValues);
				currValues.put(i,value);
			}
			t += 1;
		}
		//states
		//actions

	}
	
	public List<Integer> generateStockOrder(List<Integer> stockInventory,
											int numWeeksLeft) {

		List<Integer> itemOrders = new ArrayList<Integer>();
		List<Integer> itemReturns = new ArrayList<Integer>();

		// Example code that buys one of each item type.
        // TODO Replace this with your own code.

		int totalItems = 0;
		for (int i : stockInventory) {
			totalItems += i;
		}
		
		int totalOrder = 0;
		for (int i = 0; i < store.getMaxTypes(); i++) {
			if (totalItems >= store.getCapacity() ||
			        totalOrder >= store.getMaxPurchase()) {
				itemOrders.add(0);
			} else {
				itemOrders.add(i, 1);
				totalOrder ++;
				totalItems ++;
			}
			itemReturns.add(0);
		}


		// combine orders and returns to get change for each item type
		List<Integer> order = new ArrayList<Integer>(itemOrders.size());
		for(int i = 0; i < itemOrders.size(); i++) {
			order.add(itemOrders.get(i) - itemReturns.get(i));
		}

		return order;
	}

	private void generatePolicy(){

	}

	private double valueFunction(List<Integer> state, Map<Integer, Double> prevValues){
		//double reward = rewardFunction(state, action);
		//double value = 0;
		//For each possible newState

		double maxValue = -Double.MAX_VALUE;
		Map<Integer, List<Integer>> bestAction;
		for (Map<Integer, List<Integer>> action: validActions(state)) {
			double reward = rewardFunction(state, action); // expected immediate reward of being in a state S and perform an action A
			double futureReward = 0; // expected future reward of being in a state S and perform an action A
			for (List<Integer> sPrime : validNextStates(state, action)) {

				// TODO valueFunction(sPrime)
				double valueFuctionSPrime = 1;
				futureReward += transitionFunction(state, action, sPrime) *valueFuctionSPrime;
			}
			double newValue = reward + gamma * futureReward;
			if (newValue > maxValue) {
				maxValue = newValue;
				bestAction = action;
			}
		}
		return maxValue;
		// s.updateAction(bestAction);

	}

	private Set<Map<Integer, List<Integer>>> validActions(List<Integer> state){
		Set<Map<Integer, List<Integer>>> actions = new HashSet<Map<Integer, List<Integer>>>();
		actions = recursiveValid(state, numItems);
		return actions;
	}

	private Set<Map<Integer, List<Integer>>> recursiveValid(List<Integer> state, int depth){
		Set<Map<Integer, List<Integer>>> oldActions = new HashSet<Map<Integer, List<Integer>>>();
		Set<Map<Integer, List<Integer>>> actions = new HashSet<Map<Integer, List<Integer>>>();

		//actions = recursiveValid(state, numItems-1);
		if (depth != numItems){
			if (depth == 0){
				Set<Map<Integer, List<Integer>>> s = new HashSet<Map<Integer, List<Integer>>>();
				for (int i = 0; i < store.getCapacity(); i++){
					List<Integer> l = new ArrayList<>();
					Map<Integer, List<Integer>> m = new HashMap<>();

					for (int j = 0; j < numItems; j++){
						if (j == depth){
							l.add(depth, i);
						}
						else {
							l.add(j, 0);
						}

					}

					m.put(0,l);
					m.put(1,l);
					s.add(m);
				}
				return s;
			} else {
				oldActions = recursiveValid(state, depth - 1);
				for (Map<Integer, List<Integer>> mOld : oldActions) {
					List<Integer> baseList = mOld.get(0);

					for (int i = 0; i < store.getCapacity(); i++){
						List<Integer> l = new ArrayList<>(baseList);
						Map<Integer, List<Integer>> m = new HashMap<>();
						for (int j = 0; j < numItems; j++){
							if (j == depth){
								l.set(depth, i);
							}

						}

						m.put(0,l);
						m.put(1,l);
						actions.add(m);
					}

				}
				return actions;
			}

		} else {

			actions = recursiveValid(state, depth - 1);

		}

		return actions;
	}

	private Set<List<Integer>> validNextStates(List<Integer> state, Map<Integer, List<Integer>> action){
		Set<List<Integer>> validStates = new HashSet<List<Integer>>();
		


		return validStates;
	}

	private boolean valueComp(Map<Integer, Double> prevValues, Map<Integer, Double> currValues){
		for (int i = 0; i< prevValues.size(); i++){
			if (!((Math.abs(prevValues.get(i) - currValues.get(i))) < 0.00000001f)) {
				return false;
			}
		}
		return true;
	}

	private double rewardFunction(List<Integer> state,  Map<Integer, List<Integer>> action){
		List<Double> rewards = new ArrayList<Double>();

		for (int i = 0; i < state.size(); i++) {
			int stock = state.get(i);
			int returned;
			int ordered;
			if (action.size() != 0){
				returned = action.get(1).get(i);
				ordered = action.get(0).get(i);

			} else{
				returned = 0;
				ordered = 0;
			}

			int newStock = stock + ordered - returned;
			double reward = 0;
			//consider when unsatisfied customers
			for (int j = newStock + 1; j < store.getCapacity(); j++){
				reward += (j- stock - ordered + returned) * probabilities.get(i).get(newStock,j);
			}
			reward = reward * spec.getPenaltyFee();
			rewards.add(i, reward);

		}
		double returnReward = 0;
		for (int i = 0; i < state.size(); i++) {
			returnReward += rewards.get(i);
		}
		return returnReward;
		//various different penalties
	}

	private double transitionFunction(List<Integer> state, Map<Integer, List<Integer>> action, List<Integer> statePrime){
		List<Double> ps = new ArrayList<Double>();
		double probability = 1;


		//For each item in state
		for (int i = 0; i < state.size(); i++) {
			int stock = state.get(i);
			int stockPrime = statePrime.get(i);
			int ordered = action.get(0).get(i);
			int returned = action.get(1).get(i);
			int newStock = stock + ordered - returned;
			double p = 0;
			if (stockPrime > newStock){
				p = 0;
			} else if(0 < stockPrime && stockPrime <= newStock){
				p = probabilities.get(i).get(newStock,newStock-stockPrime);
			} else if (stockPrime == 0){
				for (int j = newStock; j < store.getCapacity(); j++){
					p += probabilities.get(i).get(newStock,j);
				}
			}

			//int val = state.get(i) + probabilities.get(i).get(1, 1);
			//newState.set(i, val);
			ps.add(p);
		}
		for (int i = 0; i < state.size(); i++) {
			probability = probability * ps.get(i);
		}
		return probability;
	}

}

