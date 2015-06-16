package org.qcri.affinityplanner;

import it.unimi.dsi.fastutil.ints.AbstractIntComparator;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import org.apache.log4j.Logger;

/**
 * 
 * Generic partitioner. The only abstract methods are relative to the semantic of the affinity graph.
 * It has two children classes implementing these methods:
 * 
 * GraphPartitioner considers affinity graphs where edges (and weights) are tuple - tuple.
 * It also has specific methods for moving more than one tuple together based on tuple - tuple affinity.
 * 
 * SimplePartitioner considers affinity graphs where edges (and weights) are tuple - partition.
 * It only moves one tuple at a time
 * 
 * @author mserafini
 *
 */
public abstract class PartitionerAffinity implements Partitioner {
    
    public class Move {
        public int toPartition;
        public double sndDelta;
        public double rcvDelta;
        public IntOpenHashSet movingVertices;
        
        public Move (){
            this.toPartition = -1;
            this.sndDelta = Double.MAX_VALUE;
            this.rcvDelta = Double.MAX_VALUE;
            this.movingVertices = new IntOpenHashSet();
        }

        public Move(int toPartition, double sndDelta, double rcvDelta, IntOpenHashSet movingVertices) {
            this.toPartition = toPartition;
            this.sndDelta = sndDelta;
            this.rcvDelta = rcvDelta;
            this.movingVertices = movingVertices.clone();
        }
        
        public Move clone(){
            return new Move (this.toPartition, this.sndDelta, this.rcvDelta, this.movingVertices);
        }
        
        public void clear(){
            this.toPartition = -1;
            this.sndDelta = Double.MAX_VALUE;
            this.rcvDelta = Double.MAX_VALUE;
            this.movingVertices.clear();
        }

        public void clearExceptMovingVertices(){
            this.toPartition = -1;
            this.sndDelta = Double.MAX_VALUE;
            this.rcvDelta = Double.MAX_VALUE;
        }
    }
    
    private static final Logger LOG = Logger.getLogger(PartitionerAffinity.class);

    protected AffinityGraph m_graph;

    public abstract boolean repartition ();
        
    /**
     * computes load of a set of vertices in the current partition. this is different from the weight of a vertex because it considers
     * both direct accesses of the vertex and the cost of remote accesses
     */
    protected abstract double getLoadVertices(IntSet vertices);
    
    /**
     * Returns sorted (descending order) list of top-k vertices from site
     * 
     * @param partition
     * @param k
     * @return
     */
    protected IntList getHottestVertices(int partition, int k){

        IntList res = new IntArrayList (k);
        final Int2DoubleMap hotnessMap = new Int2DoubleOpenHashMap (k);

        k = Math.min(k, AffinityGraph.m_partitionVertices.get(partition).size());
        int lowestPos = 0;
        double lowestLoad = Double.MAX_VALUE;
        
        IntSet singleton = new IntOpenHashSet(1);

        for(int vertex : AffinityGraph.m_partitionVertices.get(partition)){

            singleton.clear();
            singleton.add(vertex);
            double vertexLoad = getLoadVertices(singleton);

            if (res.size() < k){

                res.add(vertex);
                hotnessMap.put(vertex, vertexLoad);
                if (lowestLoad > vertexLoad){
                    lowestPos = res.size() - 1;
                    lowestLoad = vertexLoad;
                }
            }

            else{
                if(vertexLoad > lowestLoad){

                    hotnessMap.remove(res.get(lowestPos));

                    res.set(lowestPos, vertex);
                    hotnessMap.put(vertex, vertexLoad);

                    // find new lowest load
                    lowestLoad = vertexLoad;
                    for (int i = 0; i < k; i++){
                        double currLoad = hotnessMap.get(res.get(i)); 
                        if(currLoad < lowestLoad){
                            lowestPos = i;
                            lowestLoad = currLoad; 
                        }
                    }
                }
            }
        }

        // sort determines an _ascending_ order
        // Comparator should return "a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater than the second"
        // We want a _descending_ order, so we need to invert the comparator result
        Collections.sort(res, new AbstractIntComparator (){
            @Override
            public int compare(int o1, int o2) {
                if (hotnessMap.get(o1) < hotnessMap.get(o2)){
                    return 1;
                }
                else if (hotnessMap.get(o1) > hotnessMap.get(o2)){
                    return -1;
                }
                return 0;
            }
        });

        return res;
    }

    /**
     *     Global delta by moving a set of vertices
     *     This value is used to decide whether it is globally good to make a move or not
     *     It ASSUMES that the moved vertices are on the same partition
     *     
     *     if toPartition = -1 we evaluate moving to an unknown REMOTE partition
     */
    protected abstract double getGlobalDelta(IntSet movingVertices, int fromPartition, int toPartition);
    
    /**
     *     Local delta for a receiver by moving a set of vertices
     *     This value is used to determine if the receiver of the vertices will be overloaded 
     *     It ASSUMES that the moved vertices are on the same partition
     *     
     *     if toPartition = -1 we evaluate moving to an unknown REMOTE partition
     */
    protected abstract double getReceiverDelta(IntSet movingVertices, int fromPartition, int toPartition);
    
    /**
     *     Local delta for a sender by moving a set of vertices
     *     This value is used to determine the best tuple to send for a sender 
     *     It ASSUMES that the moved vertices are on the same partition
     *     
     *     if toPartition = -1 we evaluate moving to an unknown REMOTE partition
     */
    protected abstract double getSenderDelta(IntSet movingVertices, int fromPartition, int toPartition);

    public abstract double getLoadPerPartition(int partition);
    
    public double getLoadPerSite(int site){
        IntList partitions = PlanHandler.getPartitionsSite(site);
        double load = 0;
        for (int partition : partitions){
            load += getLoadPerPartition(partition);
        }
        return load;
    }
    
    /**
     * Tries to move movingVertices from overloadedPartition to toPartition. 
     * 
     * Fails if the move does not result in a minimal gain threshold for the fromPartition OR
     *      if the toPartition becomes overloaded as an effect of the transfer.
     * 
     * If the move is allowed, it updates the graph and the plan
     * 
     * @param movingVertices
     * @param fromPartition
     * @param toPartition
     * @return number of partitions actually moved
     */
    protected int tryMoveVertices(IntSet movingVertices, int fromPartition, int toPartition) {

        int numMovedVertices = 0;
        double senderDelta = getSenderDelta(movingVertices, fromPartition, toPartition);
        double receiverDelta = getReceiverDelta(movingVertices, fromPartition, toPartition);

        // check that I get enough overall gain and the additional load of the receiving site does not make it overloaded
        if(senderDelta <= Controller.MIN_SENDER_GAIN_MOVE * -1
                && (receiverDelta < 0 
                        || getLoadPerPartition(toPartition) + receiverDelta < Controller.MAX_LOAD_PER_PART)){

            m_graph.moveHotVertices(movingVertices, fromPartition, toPartition);

            numMovedVertices = movingVertices.size();

            // DEBUG
            System.out.println("Moved " + numMovedVertices + " vertices from partition " 
                    + fromPartition + " to partition " + toPartition + " for a global gain of " 
                    + getGlobalDelta(movingVertices, fromPartition, toPartition) + ", a sender gain of " 
                    + senderDelta + " and a receiver gain of " + receiverDelta);
        }
        return numMovedVertices;
    }
    
    /**
     * Finds the partition where the movingVertices can be moved while maximizing sender gain.
     * Looks for a move that does not overload the receiver. If this is not available, return the best we can find.
     * 
     * Output:
     * - Return value: whether the selected move does not overload the receiver
     * - toPartitionDelta contains the partition where the move should be done and the delta of the move
     * 
     * @param movingVertices
     * @param fromPartition
     * @param activePartitions
     * @param toPartition_senderDelta
     */
    protected void findBestPartition(Move move, int fromPartition, IntList activePartitions){
        
        move.toPartition = -1;
        move.sndDelta = Double.MAX_VALUE;
        move.rcvDelta = Double.MAX_VALUE;
        boolean feasible = false;

        double currLoad = Double.MAX_VALUE;
        
        IntList localPartitions = PlanHandler.getPartitionsSite(PlanHandler.getSitePartition(fromPartition));
        
        for(int toPartition : localPartitions){
                        
            if(fromPartition == toPartition || !activePartitions.contains(toPartition)){
                continue;
            }

            System.out.println("Examining moving to partition: " + toPartition);

            double receiverDelta = getReceiverDelta(move.movingVertices, fromPartition, toPartition);
            
            if(getLoadPerPartition(toPartition) + receiverDelta >= Controller.MAX_LOAD_PER_PART){
            
                // unfeasible move
                if (feasible){
                    System.out.println("Would become overloaded, but have feasible move, skipping");
                    continue;
                }
                System.out.println("Would become overloaded, accepting as unfeasible");
            }
            else{
                // clear any existing unfeasible move
                if (!feasible){
                    move.clearExceptMovingVertices();
                }
               feasible = true;
            }

            // TODO make constant and put out of this loop
            double sendDelta = getSenderDelta(move.movingVertices, fromPartition, toPartition);

            System.out.println("Receiver delta: " + receiverDelta + " min delta " + move.rcvDelta);
            if (receiverDelta <= move.rcvDelta){

                if (receiverDelta == move.rcvDelta){
                    double load = getLoadPerPartition(toPartition);
                    System.out.println("Load: " + load + " min load " + currLoad);
                    if (load < currLoad){
                        currLoad = load;
                        
                        System.out.println("Selected!");
                        move.toPartition = toPartition;
                        move.sndDelta = sendDelta;
                        move.rcvDelta = receiverDelta;
                    }
                }
                else{
                    double load = getLoadPerPartition(toPartition);
                    currLoad = load;
                    
                    System.out.println("Selected!");
                    move.toPartition = toPartition;
                    move.sndDelta = sendDelta;
                    move.rcvDelta = receiverDelta;
                }
            }
        }

        // then try to offload to remote partitions
        for(int toPartition : activePartitions){

            if(!localPartitions.contains(toPartition)){

                System.out.println("Examining moving to partition: " + toPartition);
                // TODO make constant and put out of this loop
                double sendDelta = getSenderDelta(move.movingVertices, fromPartition, toPartition);

                double receiverDelta = getReceiverDelta(move.movingVertices, fromPartition, toPartition);
                
                if(getLoadPerPartition(toPartition) + receiverDelta >= Controller.MAX_LOAD_PER_PART){

                    // unfeasible move
                    if (feasible){
                        System.out.println("Would become overloaded, but have feasible move, skipping");
                        continue;
                    }
                    System.out.println("Would become overloaded, accepting as unfeasible");
                }
                else{
                    // clear any existing unfeasible move
                    if (!feasible){
                        move.clearExceptMovingVertices();
                    }
                    feasible = true;
                }

                System.out.println("Receiver delta: " + receiverDelta + " min delta " + move.rcvDelta);
                if (receiverDelta <= (move.rcvDelta * (1 - Controller.PENALTY_REMOTE_MOVE))){

                    if (receiverDelta == move.rcvDelta){
                        double load = getLoadPerPartition(toPartition);
                        System.out.println("Load: " + load + " min load " + currLoad);
                        if (load < currLoad){
                            currLoad = load;
                            
                            System.out.println("Selected!");
                            move.toPartition = toPartition;
                            move.sndDelta = sendDelta;
                            move.rcvDelta = receiverDelta;
                        }
                    }
                    else {
                        double load = getLoadPerPartition(toPartition);
                        currLoad = load;
                        
                        System.out.println("Selected!");
                        move.toPartition = toPartition;
                        move.sndDelta = sendDelta;
                        move.rcvDelta = receiverDelta;
                    }
                }
            }
        }
    }
    
//    protected double getDeltaMove(IntSet movingVertices, int fromPartition, int toPartition) {
//
//        double senderDelta = getSenderDelta(movingVertices, fromPartition, toPartition);
//        double receiverDelta = getReceiverDelta(movingVertices, fromPartition, toPartition);
//        
//        System.out.println("ReceiverDelta " + receiverDelta);
//        System.out.println("Load at receiver " + getLoadPerPartition(toPartition));
//
//        if(receiverDelta < 0 
//                || getLoadPerPartition(toPartition) + receiverDelta < MAX_LOAD_PER_PART){   // if gainToSite is negative, the load of the receiving site grows
//            return senderDelta;
//        }
//        
//        return Double.MAX_VALUE;
//    }
    
    /**
     * Returns a list of lists of vertices - one list for every remote partition
     * Each list includes up to k elements with the highest outside attraction to that partition
     * The list is sorted by attraction in a descending order
     * 
     * @param this_partition
     * @param k
     * @return
     */
    protected List<IntList> getBorderVertices (int this_partition, int k){

        k = Math.min(k, AffinityGraph.m_partitionVertices.get(this_partition).size());

        List<IntList> res = new ArrayList<IntList>(Controller.MAX_PARTITIONS);

        for (int i = 0; i < Controller.MAX_PARTITIONS; i++){
            res.add(new IntArrayList (k));
        }

        // maps vertices in any top k for any partition to its array of attractions
        final Int2ObjectMap<double[]> topk_attractions = new Int2ObjectOpenHashMap<double[]> ();

        int[] lowest_attraction_position = new int[Controller.MAX_PARTITIONS];
        double[] lowest_attraction = new double[Controller.MAX_PARTITIONS];


        for(int from_vertex : AffinityGraph.m_partitionVertices.get(this_partition)){

            // compute attractions
            double[] curr_attractions = new double[Controller.MAX_PARTITIONS];

            Int2DoubleMap adjacency = AffinityGraph.m_edges.get(from_vertex);
            if (adjacency != null){
                
                updateAttractions(adjacency, curr_attractions);
                
                // rank for each partition
                for(int otherPart = 0; otherPart < Controller.MAX_PARTITIONS; otherPart++){
                    
                    if(otherPart == this_partition){
                        continue;
                    }

                    // consider deltas and ignore negative attraction
                    curr_attractions[otherPart] -= curr_attractions[this_partition];
                    if (curr_attractions[otherPart] <= 0){
                        continue;
                    }
                    
                    IntList topk = res.get(otherPart);
    
                    if(topk.size() < k){
                        
                        // add to top k
                        topk.add(from_vertex);
    
                        if (curr_attractions[otherPart] < lowest_attraction[otherPart]){
                            lowest_attraction[otherPart] = curr_attractions[otherPart];
                            lowest_attraction_position[otherPart] = topk.size() - 1;
                        }

                        // update attractionMap with new attractions
                        double[] attractionMapElem = topk_attractions.get(from_vertex);
                        if (attractionMapElem == null){
                            attractionMapElem = new double[Controller.MAX_PARTITIONS];
                            topk_attractions.put(from_vertex, attractionMapElem);
                        }
                        attractionMapElem[otherPart] = curr_attractions[otherPart];
                        topk_attractions.put(from_vertex, attractionMapElem);
    
                    }
                    else{
                        if (curr_attractions[otherPart] > lowest_attraction[otherPart]){
    
                            // remove lowest vertex from attractionMap
                            int lowestVertex = topk.get(lowest_attraction_position[otherPart]);
                            double[] topk_attraction = topk_attractions.get(lowestVertex);
                            int nonZeroPos = -1;
                            for(int j = 0; j < topk_attraction.length; j++){
                                if (topk_attraction[j] != 0){
                                    nonZeroPos = j;
                                    break;
                                }
                            }
                            if (nonZeroPos == -1){
                                topk_attractions.remove(lowestVertex);
                            }
    
                            // update top k
                            topk.set(lowest_attraction_position[otherPart], from_vertex);
    
                            // add new attractions to top k attractions map
                            topk_attraction = topk_attractions.get(from_vertex);
                            if (topk_attraction == null){
                                topk_attraction = new double[Controller.MAX_PARTITIONS];
                                topk_attractions.put(from_vertex, topk_attraction);
                            }
                            topk_attraction[otherPart] = curr_attractions[otherPart];
                            topk_attractions.put(from_vertex, topk_attraction);
    
                            // recompute minimum
                            lowest_attraction[otherPart] = curr_attractions[otherPart];
                            for (int posList = 0; posList < k; posList++){
                                int vertex = topk.get(posList);
                                double attraction = topk_attractions.get(vertex)[otherPart];
                                if(attraction < lowest_attraction[otherPart]){
                                    lowest_attraction[otherPart] = attraction;
                                    lowest_attraction_position[otherPart] = posList;
                                }
                            }
                        }
                    }
                } // END for(int otherPart = 1; otherPart < MAX_PARTITIONS; otherPart++)
            } // END if (adjacency != null)
        } // END for(String from_vertex : m_graph.m_partitionVertices.get(this_partition))
       
        // sorting
        for(int otherPart = 1; otherPart < Controller.MAX_PARTITIONS; otherPart++){
            IntList topk = res.get(otherPart);

            // sort determines an _ascending_ order
            // Comparator should return "a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater than the second"
            // We want a _descending_ order, so we need to invert the comparator result

            final int part = otherPart; // make Java happy

            Collections.sort(topk, new AbstractIntComparator(){                
                @Override
                public int compare(int o1, int o2) {
                    if (topk_attractions.get(o1)[part] < topk_attractions.get(o2)[part]){
                        return 1;
                    }
                    else if (topk_attractions.get(o1)[part] > topk_attractions.get(o2)[part]){
                        return -1;
                    }
                    return 0;
                }                
            });
        }

        return res;
    }

    
    /**
     *  SCALE IN
     *  
     *  very simple policy: if a partition is underloaded, try to move its whole content to another partition
     */
    protected void scaleIn(IntList activePartitions){

        // detect underloaded partitions
        TreeSet<Integer> underloadedPartitions = new TreeSet<Integer>();
        for(int part : activePartitions){
            if (getLoadPerPartition(part) < Controller.MIN_LOAD_PER_PART){
                underloadedPartitions.add(part);
            }
        }

        if (!underloadedPartitions.isEmpty()){
            System.out.println("SCALING IN");
        }


        // offload from partitions with higher id to partitions with lower id. this helps emptying up the latest servers.
        Iterator<Integer> descending = underloadedPartitions.descendingIterator();
        IntSet removedPartitions = new IntOpenHashSet();

        while(descending.hasNext()){

            int underloadedPartition = descending.next();
            System.out.println("Offloading partition " + underloadedPartition);
            IntOpenHashSet movingVertices = new IntOpenHashSet();
            movingVertices.addAll(AffinityGraph.m_partitionVertices.get(underloadedPartition));

            // try to offload to remote partitions
            IntList localPartitions = PlanHandler.getPartitionsSite(PlanHandler.getSitePartition(underloadedPartition));
            for(int toPartition : activePartitions){
                
                if(underloadedPartition == toPartition){
                    continue;
                }

                if(!localPartitions.contains(toPartition) && !removedPartitions.contains(toPartition)){

                    LOG.debug("Trying with partition " + toPartition);
                    int movedVertices = tryMoveVertices(movingVertices, underloadedPartition, toPartition);

                    if(movedVertices > 0){
                        removedPartitions.add(underloadedPartition);
                        break;                            
                    }
                }
            }
        }
        activePartitions.removeAll(removedPartitions);
    }

    public void writePlan(String newPlanFile){
        m_graph.planToJSON(newPlanFile);
    }
    
    public void graphToFile (Path file){
        m_graph.toFile(file);
    }

    protected int moveColdChunks(int fromPartition, IntList fromHotTuples, IntList activePartitions, int numMovedVertices){

        // clone plan to allow modifications while iterating on the clone
        PlanHandler oldPlan = m_graph.clonePlan();
        
        // remove hot tuples from cold chunks

        for (int topk = 1; topk <= Math.min(Controller.TOPK, fromHotTuples.size()); topk++){
            int hotTuple = fromHotTuples.get(fromHotTuples.size() - topk);

            System.out.println("Hot tuple:" + m_graph.getTupleName(hotTuple));
            String[] fields  = m_graph.getTupleName(hotTuple).split(",");
            String table = fields[0];
            long tupleId = Long.parseLong(fields[1]);
            
            if(Controller.ROOT_TABLE == null){
                oldPlan.removeTupleId(table, fromPartition, tupleId);
            }
            else{
                oldPlan.removeTupleIdAllTables(fromPartition, tupleId);
            }
        }
        
        System.out.println("Cloned plan without hot tuples:\n" + oldPlan);
        
        if(Controller.ROOT_TABLE == null){
            for(String table : m_graph.getTableNames()){
                numMovedVertices += moveColdChunkTable(table, oldPlan, fromPartition, activePartitions, numMovedVertices);
            }
        }
        else{
            numMovedVertices += moveColdChunkTable(Controller.ROOT_TABLE, oldPlan, fromPartition, activePartitions, numMovedVertices);
        }
        
        return numMovedVertices;
    }
    
    private int moveColdChunkTable(String table, Plan oldPlan, int fromPartition, IntList activePartitions, int numMovedVertices){
        System.out.println("Table " + table);

        double coldIncrement = m_graph.getColdIncrement(fromPartition);

        List<List<Plan.Range>> partitionChunks = oldPlan.getRangeChunks(table, fromPartition,  (long) Controller.COLD_CHUNK_SIZE);
        if(partitionChunks.size() > 0) {

            for(List<Plan.Range> chunk : partitionChunks) {  // a chunk can consist of multiple ranges if hot tuples are taken away

                System.out.println("\nNew cold chunk");

                for(Plan.Range r : chunk) { 

                    System.out.println("Range " + r.from + " " + r.to);

                    double rangeWeight = Plan.getRangeWidth(r) * coldIncrement;
                    int toPartition = getLeastLoadedPartition(activePartitions);     

                    System.out.println("Weight " + rangeWeight);
                    System.out.println("To partition " + toPartition);

                    if (rangeWeight + getLoadPerPartition(toPartition) < Controller.MAX_LOAD_PER_PART
                           && numMovedVertices + Plan.getRangeWidth(r) < Controller.MAX_MOVED_TUPLES_PER_PART){

                        // do the move
                        
                        numMovedVertices += Plan.getRangeWidth(r);

                        System.out.println("Moving!");
                        System.out.println("Load before " + getLoadPerPartition(fromPartition));

                        if(Controller.ROOT_TABLE == null){
                            m_graph.moveColdRange(table, r, fromPartition, toPartition);
                        }
                        else{
                            m_graph.moveColdRangeAllTables(r, fromPartition, toPartition);                                    
                        }

                        System.out.println("Load after " + getLoadPerPartition(fromPartition));
                        System.out.println("New plan\n" + m_graph.planToString());

                        // after every move, see if I can stop
                        if(getLoadPerPartition(fromPartition) <= Controller.MAX_LOAD_PER_PART){
                            return numMovedVertices;
                        }
                    }
                    else{
                        System.out.println("Cannot offload partition " + fromPartition);
                        return numMovedVertices;
                    }
                }
            }
        }
        return numMovedVertices;
    }

    
    public int getLeastLoadedPartition(IntList activePartitions){
        double minLoad = Double.MAX_VALUE;
        int res = 0;
        for (int part : activePartitions){
            double newLoad = getLoadPerPartition(part);
            if (newLoad < minLoad){
                res = part;
                minLoad = newLoad; 
            }
        }
        return res;
    }
    
    protected abstract void updateAttractions(Int2DoubleMap adjacency, double[] attractions);

}
