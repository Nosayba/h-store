package edu.brown.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.collections15.set.ListOrderedSet;
import org.apache.log4j.Logger;
import org.voltdb.compiler.ClusterConfig;

import edu.brown.logging.LoggerUtil;
import edu.brown.logging.LoggerUtil.LoggerBoolean;
import edu.brown.utils.CollectionUtil;
import edu.brown.utils.FileUtil;
import edu.mit.hstore.HStoreSite;

/**
 * @author pavlo
 */
public class ClusterConfiguration extends ClusterConfig {
    private static final Logger LOG = Logger.getLogger(ClusterConfiguration.class);
    private final static LoggerBoolean debug = new LoggerBoolean(LOG.isDebugEnabled());
    private final static LoggerBoolean trace = new LoggerBoolean(LOG.isTraceEnabled());
    static {
        LoggerUtil.attachObserver(LOG, debug, trace);
    }
    
    private static final Pattern COLON_SPLIT = Pattern.compile(":");
    private static final Pattern COMMA_SPLIT = Pattern.compile(",");
    private static final Pattern HYPHEN_SPLIT = Pattern.compile(Pattern.quote("-"));
    
    /**
     * Host -> SiteId -> Set<PartitionConfiguration>
     */
    private final Map<String, Map<Integer, Set<PartitionConfiguration>>> host_sites = new HashMap<String, Map<Integer, Set<PartitionConfiguration>>>();
    
    private final Set<Integer> all_partitions = new HashSet<Integer>();
    
    /**
     * PartitionConfiguration
     */
    private class PartitionConfiguration {
        private final String host;
        private final int site;
        private final int partition;
        
        public PartitionConfiguration(String host, int site, int partition) {
            this.host = host;
            this.site = site;
            this.partition = partition;
        }
        @Override
        public String toString() {
            return String.format("%s - %s", this.host, HStoreSite.formatPartitionName(this.site, this.partition));
        }
    }
    
    public ClusterConfiguration() {
        super();
    }
    
    public ClusterConfiguration(Collection<String> host_triplets) {
        super();
        for (String host_info : host_triplets) {
            this.addPartition(host_info);
        } // FOR
    }
    
    public ClusterConfiguration(String hosts) {
        List<String> host_triplets = new ArrayList<String>();
        if (FileUtil.exists(hosts)) {
            String contents = FileUtil.readFile(hosts);
            CollectionUtil.addAll(host_triplets, contents.split("\n"));
        } else {
            CollectionUtil.addAll(host_triplets, hosts.split(";"));
        }
        for (String host_info : host_triplets) {
            this.addPartition(host_info);
        } // FOR
    }
    
    @Override
    public boolean validate() {
        return (this.host_sites.isEmpty() == false);
    }
    
    public boolean isEmpty() {
        return (this.host_sites.isEmpty());
    }
    
    public void addPartition(String host_info) {
        host_info = host_info.trim();
        if (host_info.isEmpty()) return;
        String data[] = COLON_SPLIT.split(host_info);
        assert(data.length == 3) : "Invalid host information '" + host_info + "'";
        
        String host = data[0];
        if (host.startsWith("#")) return;
        int site = Integer.parseInt(data[1]);
        
        // Partition Ranges
        for (String p : COMMA_SPLIT.split(data[2])) {
            int start = -1;
            int stop = -1;
            String range[] = HYPHEN_SPLIT.split(p);
            if (range.length == 2) {
                start = Integer.parseInt(range[0]);
                stop = Integer.parseInt(range[1]);
            } else {
                start = Integer.parseInt(p);
                stop = start;
            }
            for (int partition = start; partition < stop+1; partition++) {
                this.addPartition(host, site, partition);
            } // FOR
        } // FOR
    }
    
    public synchronized void addPartition(String host, int site, int partition) {
        if (this.all_partitions.contains(partition)) {
            throw new IllegalArgumentException("Duplicate partition id #" + partition + " for host '" + host + "'");
        }
        if (debug.get()) LOG.info(String.format("Adding Partition: %s:%d:%d", host, site, partition));
        
        PartitionConfiguration pc = new PartitionConfiguration(host, site, partition);
        this.all_partitions.add(partition);
        
        // Host -> Sites
        if (!this.host_sites.containsKey(host)) {
            this.host_sites.put(host, new HashMap<Integer, Set<PartitionConfiguration>>());
        }
        if (!this.host_sites.get(host).containsKey(site)) {
            this.host_sites.get(host).put(site, new HashSet<PartitionConfiguration>());
        }
        this.host_sites.get(host).get(site).add(pc);
        if (debug.get()) LOG.debug("New PartitionConfiguration: " + pc);
    }
    
    public Collection<String> getHosts() {
        return (this.host_sites.keySet());
    }
    
    public Collection<Integer> getSites(String host) {
        return (this.host_sites.get(host).keySet());
    }
    
    
    private Collection<PartitionConfiguration> getPartitions(String host, int site) {
        return (this.host_sites.get(host).get(site));
    }
    
    public Collection<Integer> getPartitionIds(String host, int site) {
        Set<Integer> ids = new ListOrderedSet<Integer>();
        for (PartitionConfiguration pc : this.getPartitions(host, site)) {
            ids.add(pc.partition);
        } // FOR
        return (ids);
    }
}