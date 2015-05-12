/**
 * @author Marco
 */

package org.qcri.affinityplanner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import org.apache.log4j.Logger;
import org.voltdb.CatalogContext;

import edu.brown.hstore.HStoreConstants;

public class AffinityGraph {
  
    private static final Logger LOG = Logger.getLogger(AffinityGraph.class);

    // determines granularity of edges, either vertex - vertex of vertex - partition 
    private static boolean m_tupleGranularity = false;
    
    // fromVertex -> adjacency list, where adjacency list is a toVertex -> edgeWeight map or toPartition -> edgeWeight map, depending on granularity 
    // (weight of edge is # of txns touching both partitions per second)
    protected static Int2ObjectOpenHashMap<Int2DoubleOpenHashMap> m_edges = new Int2ObjectOpenHashMap <Int2DoubleOpenHashMap> ();
    
    // vertex -> full name
    protected static Int2ObjectOpenHashMap<String> m_vertex_to_name = new Int2ObjectOpenHashMap <String> ();

    // vertex -> weight map (number of txns accesses the vertext per second)
    protected static final Int2DoubleOpenHashMap m_vertices = new Int2DoubleOpenHashMap (1000);
    
    // partition -> vertex and vertex -> partition mappings
    protected static final List<IntOpenHashSet> m_partitionVertices = new ArrayList<IntOpenHashSet> ();
    protected static final Int2IntOpenHashMap m_vertexPartition = new Int2IntOpenHashMap ();
    
    private static PlanHandler m_plan_handler = null;
    
    private static long[] m_intervalsInSecs;
    
    private static class LoaderThread implements Runnable {
        private Path[] logFiles;
        private AtomicInteger nextLogFileCounter;
        
        public LoaderThread(Path[] logFiles, AtomicInteger nextLogFileCounter){
            this.logFiles = logFiles;
            this.nextLogFileCounter = nextLogFileCounter;
        }

        @Override
        public void run() {
            try {
                int nextLogFile = nextLogFileCounter.getAndIncrement();
                
                while (nextLogFile < logFiles.length){
                    
                    double increment = 1.0/m_intervalsInSecs[nextLogFile];
                    
                    loadLogFile(logFiles[nextLogFile], increment);
                    
                    nextLogFile = nextLogFileCounter.getAndIncrement();                    
                }
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        
        private void loadLogFile(Path logFile, double normalizedIncrement) throws Exception{
            
            // init local data structures to be merged later
            Int2ObjectOpenHashMap<String> vertex_to_name = new Int2ObjectOpenHashMap <String> ();
            Int2IntOpenHashMap vertexPartition = new Int2IntOpenHashMap();
            Int2DoubleOpenHashMap vertices = new Int2DoubleOpenHashMap (1000);
            List<IntOpenHashSet> partitionVertices = new ArrayList<IntOpenHashSet> ();
            for (int i = 0; i < m_partitionVertices.size(); i++){
                partitionVertices.add(new IntOpenHashSet());
            }

            
            System.out.println("Loading from file " + logFile);

            // SETUP
            BufferedReader reader;
            try {
                reader = Files.newBufferedReader(logFile, Charset.forName("US-ASCII"));
            } catch (IOException e) {
                Controller.record("Error while reading file " + logFile.toString() + "\n Stack trace:\n" + Controller.stackTraceToString(e));
                throw e;
            }
            String line;
            // vertices with number of SQL statements they are involved in
            // READ FIRST LINE
            try {
                line = reader.readLine();
            } catch (IOException e) {
                Controller.record("Error while reading file " + logFile.toString() + "\n Stack trace:\n" + Controller.stackTraceToString(e));
                throw e;
            }
            if (line == null){
                Controller.record("File " + logFile.toString() + " is empty");
                throw new Exception();
            }

//            System.out.println("Tran ID = " + currTransactionId);
            IntOpenHashSet curr_transaction = new IntOpenHashSet ();
            // PROCESS LINE
            while(line != null){

                // if finished with one transaction, update graph and clear before moving on

                if (line.equals("END")){

                    for(int from : curr_transaction){
                        // update FROM vertex in graph
                        double currentVertexWeight = vertices.get(from);
                        if (currentVertexWeight == vertices.defaultReturnValue()){
                            vertices.put(from, normalizedIncrement);
                        }
                        else{
                            vertices.put(from, currentVertexWeight + normalizedIncrement);                         
                        }
                        
                        // store site mappings for FROM vertex
                        int partition = 0;
                        String fromName = vertex_to_name.get(from);
                        partition = m_plan_handler.getPartition(fromName);
                        
                        if (partition == HStoreConstants.NULL_PARTITION_ID){
                            LOG.info("Exiting graph loading. Could not find partition for key " + fromName);
                            System.out.println("Exiting graph loading. Could not find partition for key " + fromName);
                            throw new Exception();                            
                        }
                        partitionVertices.get(partition).add(from);
                        vertexPartition.put(from, partition);
                        
                        Int2DoubleOpenHashMap adjacency = null;

                        synchronized(m_edges){
                            adjacency = m_edges.get(from);
                            if(adjacency == null){
                                adjacency = new Int2DoubleOpenHashMap ();
                                m_edges.put(from, adjacency);
                            }
                        }

                        // update FROM -> TO edges
                        for(int to : curr_transaction){
                            if (from != to){
                                // if lower granularity, edges link vertices to partitions, not other vertices
                                if(!m_tupleGranularity){
                                    String toName = vertex_to_name.get(to);
                                    to = m_plan_handler.getPartition(toName);
                                }
                                
                                synchronized(adjacency){
                                    double currentEdgeWeight = adjacency.get(to);
                                    if (currentEdgeWeight == 0){
                                        adjacency.put(to, normalizedIncrement);
                                    }
                                    else{
                                        adjacency.put(to, currentEdgeWeight + normalizedIncrement);
                                    }
                                }
                            }
                        } // END for(String to : curr_transaction)

                    } // END for(String from : curr_transaction)

                    curr_transaction.clear();

                    //                    System.out.println("Tran ID = " + currTransactionId);
                } // END if (line.equals("END"))

                else{
                    int hash = line.hashCode();
                    curr_transaction.add(hash);
                    if (!vertex_to_name.containsKey(hash)){
                        vertex_to_name.put(hash, line);
                    }
                }

                /* this is what one would do to count different accesses within the same transaction. 
                 * For the moment I count only the number of transactions accessing a tuple
                Double weight = transaction.get(vertex[1]);
                if (weight == null){
                    transaction.put(vertex[1], 1.0/intervalsInSecs[currLogFile]);
                }
                else{
                    transaction.put(vertex[1], (weight+1.0)/intervalsInSecs[currLogFile]);
                } 
                */
                
                // READ NEXT LINE
                try {
                    line = reader.readLine();
                } catch (IOException e) {
                    Controller.record("Error while reading file " + logFile.toString() + "\n Stack trace:\n" + Controller.stackTraceToString(e));
                    System.out.println("Error while reading file " + logFile.toString() + "\n Stack trace:\n" + Controller.stackTraceToString(e));
                    throw e;
                }
            }// END  while(line != null)
            
            // merge local data structures
            synchronized(m_vertex_to_name){
                m_vertex_to_name.putAll(vertex_to_name);
            }
            synchronized(m_vertexPartition){
                m_vertexPartition.putAll(vertexPartition);
            }
            synchronized(m_partitionVertices){
                for (int i = 0; i < m_partitionVertices.size(); i++){
                    IntSet localVertices = partitionVertices.get(i);
                    m_partitionVertices.get(i).addAll(localVertices);
                }
            }
            synchronized(m_vertices){
                m_vertices.putAll(vertices);
            }
        }
    }
    
    public AffinityGraph(boolean tupleGranularity, CatalogContext catalogContext, File planFile, 
            Path[] logFiles, Path[] intervalFiles, int noPartitions) throws Exception {
        m_tupleGranularity = tupleGranularity;

        BufferedReader reader;

        for (int i = 0; i < noPartitions; i++){
            m_partitionVertices.add(new IntOpenHashSet());
        }

        try {
            m_plan_handler = new PlanHandler(planFile, catalogContext);
        } catch (Exception e) {
            Controller.record("Could not create plan handler " + Controller.stackTraceToString(e));
            throw e;
        }

        // read monitoring intervals for all sites - in seconds
        m_intervalsInSecs = new long[intervalFiles.length];
        int currInterval = 0;
        for (Path intervalFile : intervalFiles){
            if (intervalFile == null){
                Controller.record("interval file name is null");
                throw new Exception();
            }
            try {
                reader = Files.newBufferedReader(intervalFile, Charset.forName("US-ASCII"));
                String line = reader.readLine();
                reader.close();
                m_intervalsInSecs[currInterval] = Long.parseLong(line) / 1000;
                currInterval++;
            } catch (IOException e) {
                Controller.record("Error while reading interval file " + intervalFile.toString() + "\n Stack trace:\n" + Controller.stackTraceToString(e));
                Thread.sleep(1000);
                throw e;
            }
            catch (NumberFormatException e1){
                Controller.record("Error while converting interval from file " + intervalFile.toString() + "\n Stack trace:\n" + Controller.stackTraceToString(e1));
                Thread.sleep(1000);
                throw e1;
            }
        }
        
        // scan files for all partitions
        Thread[] loadingThreads = new Thread[Controller.LOAD_THREADS];
        AtomicInteger nextLogFileCounter = new AtomicInteger(0);
        
        for (int currLogFile = 0; currLogFile < Controller.LOAD_THREADS; currLogFile++){
            
            Thread loader = new Thread (new LoaderThread (logFiles, nextLogFileCounter));
            loadingThreads[currLogFile] = loader;
            loader.start();
        }
        
        // wait for all loader threads to finish
        for (Thread loader : loadingThreads){
            loader.join();
        }
    }
    
    public void moveHotVertices(IntSet movedVertices, int fromPartition, int toPartition) {
        for (int movedVertex : movedVertices){
            m_partitionVertices.get(fromPartition).remove(movedVertex);
            m_partitionVertices.get(toPartition).add(movedVertex);
            m_vertexPartition.put(movedVertex, toPartition);

            // update plan too
            // format of vertices is <TABLE>,<PART-KEY>,<VALUE>
            String movedVertexName = m_vertex_to_name.get(movedVertex);
            String [] fields = movedVertexName.split(",");
//            System.out.println("table: " + fields[0] + " from partition: " + fromPartition + " to partition " + toPartition);
//            System.out.println("remove ID: " + fields[2]);
            m_plan_handler.removeTupleId(fields[0], fromPartition, Long.parseLong(fields[2]));
//            System.out.println("After removal");
//            System.out.println(m_plan_handler.toString() + "\n");
            m_plan_handler.addRange(fields[0], toPartition, Long.parseLong(fields[2]), Long.parseLong(fields[2]));
//            System.out.println("After adding");
//            System.out.println(m_plan_handler.toString() + "\n");
        }
        
//        System.out.println(m_plan_handler.toString() + "\n");
    }
    
    public void moveColdRange(String table, Plan.Range movedRange, int fromPart, int toPart){
        
        m_plan_handler.moveColdRange(table, movedRange, fromPart, toPart);

    }
    
    public int getPartition(int vertex){
        String vertexName = m_vertex_to_name.get(vertex);
        return m_plan_handler.getPartition(vertexName);
    }
    
    public static boolean isActive(int partition){
        return m_plan_handler.isActive(partition);
    }
    
    public void planToJSON(String newPlanFile){
        m_plan_handler.toJSON(newPlanFile);
    }
    
    
    
    public void toMetisFile(Path file, Path mapOut){
        LOG.info("Writing graph for " + file.toString() +" . Number of vertices: " + m_vertices.size());
        BufferedWriter writer, mapWriter = null;
        try {
            writer = Files.newBufferedWriter(file, Charset.forName("US-ASCII"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            String graphInfo = "011"; //ABC A:VertexSize B:VertexWeight C:EdgeWeight
            int edgeCount=0;
            for(int v_id : m_vertices.keySet()){                
                if (m_edges.get(v_id)!=null)
                    edgeCount+=m_edges.get(v_id).size();
            }
            String header = String.format("%s %s %s",m_vertices.size(), (int)(edgeCount/2), graphInfo);
            writer.write(header);
            writer.newLine();
            
            //Need an ordered list of vertexes 1 .. N
            Int2IntOpenHashMap vert_to_increment = new Int2IntOpenHashMap(m_vertices.size());
            int[] vert_ids = new int[m_vertices.size()+1];
            int count = 1;
            for (int vert_hash: m_vertices.keySet()){
                vert_ids[count] = vert_hash;
                vert_to_increment.put(vert_hash, count);
                count++;
            }
            if (mapOut != null){
                mapWriter = Files.newBufferedWriter(mapOut, Charset.forName("US-ASCII"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                for (int i = 1; i < vert_ids.length; i++ ){
                    mapWriter.write(String.format("%s", vert_ids[i]));
                    mapWriter.newLine();
                }
            }

            //Each line is [vert size] [edge to vertex id 1] [edge 1 weight] [edge to vertex id 2] [edge 2 weight] ...
            //vertex ID is implicitly line number (starting at 1)
            for(int incrID = 1; incrID < vert_ids.length; incrID++){
                //TODO check on format of weights
                int vert_hash = vert_ids[incrID];
                double vert_weight = m_vertices.get(vert_hash);
                //StringBuilder sb = new StringBuilder(String.format("%s %d", m_vertex_to_name.get(vert_hash), (int)(vert_weight*100)));//TODO casting to int
                StringBuilder sb = new StringBuilder(String.format("%d", (int)(vert_weight*100)));//TODO casting to int
                
                for (Entry<Integer, Double> edge: m_edges.get(vert_hash).entrySet()){
                    int weight = (int) (edge.getValue()*100);
                    sb.append(String.format(" %d %d", vert_to_increment.get(edge.getKey()),weight ));//TODO casting to int
                }
                writer.write(sb.toString());
                writer.newLine();
            }
            writer.close();
            if (mapWriter!=null) mapWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
            Controller.record("Error while opening file " + file.toString());
            System.out.println("Error while opening file " + file.toString());
            return;
       }        
        
    }

    
    public String planToString(){
        return m_plan_handler.toString();
    }
    
    public String[] getTableNames(){
        return m_plan_handler.table_names;
    }
    
    public List<Plan.Range> getAllRanges (String table, int partition){
        return m_plan_handler.getAllRanges(table, partition);
    }
    
    public double getColdTupleWeight (int partition){
        return 1.0 / m_intervalsInSecs[partition] / Controller.COLD_TUPLE_FRACTION_ACCESSES;
    }
    
    public int getHotVertexCount(int partition){
        return m_partitionVertices.get(partition).size();
    }
    
    public PlanHandler clonePlan(){
        return m_plan_handler.clone();
    }
    
    public List<Plan.Range> getRangeValues(String table, int partition, long from, long to){
        return m_plan_handler.getRangeValues(table, partition, from, to);
    }
    
    public void toFile(Path file){
        System.out.println("Writing graph. Number of vertices: " + m_edges.size());
        BufferedWriter writer;
        String s;
        double totalWeight = 0;
        try {
            writer = Files.newBufferedWriter(file, Charset.forName("US-ASCII"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            for(int vertex : m_vertices.keySet()){
                s = "Vertex " + m_vertex_to_name.get(vertex) + " - weight " + m_vertices.get(vertex);
                totalWeight += m_vertices.get(vertex);
                writer.write(s, 0, s.length());
                writer.newLine();
                Int2DoubleMap adjacency = m_edges.get(vertex);
                if(adjacency == null){
                    writer.newLine();
                    continue;
                }
                for (Int2DoubleMap.Entry edge : adjacency.int2DoubleEntrySet()){
                    s = m_vertex_to_name.get(edge.getIntKey()) + " - weight " + edge.getDoubleValue();
                    writer.write(s, 0, s.length());
                    writer.newLine();
                }
                writer.newLine();
            }
            s = "Total weight: " + totalWeight;
            writer.write(s, 0, s.length());
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            Controller.record("Error while opening file " + file.toString());
            System.out.println("Error while opening file " + file.toString());
            return;
       }
    }
    

}
