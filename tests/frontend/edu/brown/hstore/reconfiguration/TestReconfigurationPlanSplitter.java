/**
 * 
 */
package edu.brown.hstore.reconfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import org.junit.Test;
import org.voltdb.utils.Pair;

import edu.brown.BaseTestCase;
import edu.brown.hashing.ExplicitPartitions;
import edu.brown.hashing.ReconfigurationPlan;
import edu.brown.hashing.ReconfigurationPlan.ReconfigurationRange;
import edu.brown.hashing.TwoTieredRangePartitions;
import edu.brown.hstore.reconfiguration.ReconfigurationUtil.AutoSplit;
import edu.brown.hstore.reconfiguration.ReconfigurationUtil.ReconfigurationPair;
import edu.brown.utils.FileUtil;
import edu.brown.utils.ProjectType;

/**
 * @author aelmore
 *
 */
public class TestReconfigurationPlanSplitter extends BaseTestCase {


    private File json_path1;

    private File json_path2;

    @Test
    public void testReconfigurationPair() throws Exception{
        ReconfigurationPair pair1 = new ReconfigurationPair(1,2);
        ReconfigurationPair pair2 = new ReconfigurationPair(2,1);
        ReconfigurationPair pair3 = new ReconfigurationPair(1,3);
        ReconfigurationPair pair4 = new ReconfigurationPair(1,2);
        assertTrue(pair1.compareTo(pair2) < 0);
        assertTrue(pair1.compareTo(pair3) > 0);
        assertTrue(pair1.compareTo(pair4) == 0);
        assertTrue(pair1.equals(pair4));
        assertFalse(pair2.equals(pair3));

        Set<ReconfigurationPair> reconfigPairs = new HashSet<>();
        reconfigPairs.add(pair1);
        reconfigPairs.add(pair2);
        reconfigPairs.add(pair3);
        reconfigPairs.add(pair4);
        assertTrue(reconfigPairs.size() == 3);

    }

    /**
     * 
     */
    @Test
    public void testReconfigurationPlanSplitter() throws Exception{
        ExplicitPartitions p = new TwoTieredRangePartitions(catalogContext, json_path1);
        p.setPartitionPlan(json_path1);    
        ReconfigurationPlan plan = p.setPartitionPlan(json_path2);

        ReconfigurationUtil.naiveSplitReconfigurationPlan(plan, 5, false, 1);
    }
    
    @Test
    public void testInterleavePlans() throws Exception {
        List<ReconfigurationPlan> plans = new ArrayList<>();
        for (int i = 0; i < 30; ++i) {
            plans.add(new ReconfigurationPlan(catalogContext, new HashMap<String, String>()));
        }
        
        Integer[] range = new Integer[]{ 0 };
        for (int i = 0; i < 5; ++i) {
            plans.get(i).addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 3));
            plans.get(5 + i).addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 4));

            plans.get(10 + i).addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 4));
            plans.get(15 + i).addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 3));

            plans.get(20 + i).addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 3));        
            plans.get(25 + i).addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 4));
        }
        int interleave = 6;
        List<ReconfigurationPlan> interleavedPlans = ReconfigurationUtil.interleavePlans(plans, interleave);
        assertEquals(plans.size(), interleavedPlans.size());
        for (ReconfigurationPlan plan : interleavedPlans) {
            System.out.println(plan.getIncoming_ranges().toString());
        }
    }

    @Test
    public void testSplitMigrationPairsCase1() throws Exception{
        AutoSplit as = new AutoSplit();
        as.s = 3;
        as.delta = 2;
        as.r = 2;
        as.numberOfSplits = 3;
        int partitionsPerSite = 1;

        int numberOfSplits = 3;
        Set<ReconfigurationPair> migrationPairs = new HashSet<>();
        migrationPairs.add(new ReconfigurationPair(0, 3));
        migrationPairs.add(new ReconfigurationPair(0, 4));
        migrationPairs.add(new ReconfigurationPair(1, 3));
        migrationPairs.add(new ReconfigurationPair(1, 4));
        migrationPairs.add(new ReconfigurationPair(2, 3));
        migrationPairs.add(new ReconfigurationPair(2, 4));       

        Map<Pair<Integer, Integer>, Integer> pairToSplitMapping = 
                ReconfigurationUtil.splitMigrationPairs(numberOfSplits, migrationPairs, as, partitionsPerSite);
        System.out.println(pairToSplitMapping.toString());
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 3)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 4)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 3)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 4)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 3)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 4)));    

        // scale in
        migrationPairs = new HashSet<>();
        migrationPairs.add(new ReconfigurationPair(3, 0));
        migrationPairs.add(new ReconfigurationPair(4, 0));
        migrationPairs.add(new ReconfigurationPair(3, 1));
        migrationPairs.add(new ReconfigurationPair(4, 1));
        migrationPairs.add(new ReconfigurationPair(3, 2));
        migrationPairs.add(new ReconfigurationPair(4, 2));

        pairToSplitMapping = 
                ReconfigurationUtil.splitMigrationPairs(numberOfSplits, migrationPairs, as, partitionsPerSite);
        System.out.println(pairToSplitMapping.toString());
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 0)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 0)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 1)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 1)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 2)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 2)));
    }

    @Test
    public void testSplitMigrationPairsCase2() throws Exception{
        AutoSplit as = new AutoSplit();
        as.s = 3;
        as.delta = 6;
        as.r = 0;
        as.numberOfSplits = 6;
        int partitionsPerSite = 1;

        int numberOfSplits = 6;
        Set<ReconfigurationPair> migrationPairs = new HashSet<>();
        migrationPairs.add(new ReconfigurationPair(0, 3));
        migrationPairs.add(new ReconfigurationPair(0, 4));
        migrationPairs.add(new ReconfigurationPair(0, 5));
        migrationPairs.add(new ReconfigurationPair(0, 6));
        migrationPairs.add(new ReconfigurationPair(0, 7));
        migrationPairs.add(new ReconfigurationPair(0, 8));
        migrationPairs.add(new ReconfigurationPair(1, 3));
        migrationPairs.add(new ReconfigurationPair(1, 4));
        migrationPairs.add(new ReconfigurationPair(1, 5));
        migrationPairs.add(new ReconfigurationPair(1, 6));
        migrationPairs.add(new ReconfigurationPair(1, 7));
        migrationPairs.add(new ReconfigurationPair(1, 8));
        migrationPairs.add(new ReconfigurationPair(2, 3));
        migrationPairs.add(new ReconfigurationPair(2, 4));
        migrationPairs.add(new ReconfigurationPair(2, 5));
        migrationPairs.add(new ReconfigurationPair(2, 6));
        migrationPairs.add(new ReconfigurationPair(2, 7));
        migrationPairs.add(new ReconfigurationPair(2, 8));

        Map<Pair<Integer, Integer>, Integer> pairToSplitMapping = 
                ReconfigurationUtil.splitMigrationPairs(numberOfSplits, migrationPairs, as, partitionsPerSite);
        System.out.println(pairToSplitMapping.toString());
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 3)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 4)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 5)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 6)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 7)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 8)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 3)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 4)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 5)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 6)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 7)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 8)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 3)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 4)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 5)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 6)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 7)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 8)));  

        // scale in
        migrationPairs = new HashSet<>();
        migrationPairs.add(new ReconfigurationPair(3, 0));
        migrationPairs.add(new ReconfigurationPair(4, 0));
        migrationPairs.add(new ReconfigurationPair(5, 0));
        migrationPairs.add(new ReconfigurationPair(6, 0));
        migrationPairs.add(new ReconfigurationPair(7, 0));
        migrationPairs.add(new ReconfigurationPair(8, 0));
        migrationPairs.add(new ReconfigurationPair(3, 1));
        migrationPairs.add(new ReconfigurationPair(4, 1));
        migrationPairs.add(new ReconfigurationPair(5, 1));
        migrationPairs.add(new ReconfigurationPair(6, 1));
        migrationPairs.add(new ReconfigurationPair(7, 1));
        migrationPairs.add(new ReconfigurationPair(8, 1));
        migrationPairs.add(new ReconfigurationPair(3, 2));
        migrationPairs.add(new ReconfigurationPair(4, 2));
        migrationPairs.add(new ReconfigurationPair(5, 2));
        migrationPairs.add(new ReconfigurationPair(6, 2));
        migrationPairs.add(new ReconfigurationPair(7, 2));
        migrationPairs.add(new ReconfigurationPair(8, 2));

        pairToSplitMapping = 
                ReconfigurationUtil.splitMigrationPairs(numberOfSplits, migrationPairs, as, partitionsPerSite);
        System.out.println(pairToSplitMapping.toString());
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 0)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 0)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(5, 0)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(6, 0)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(7, 0)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(8, 0)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 1)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 1)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(5, 1)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(6, 1)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(7, 1)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(8, 1)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 2)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 2)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(5, 2)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(6, 2)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(7, 2)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(8, 2)));
    }

    @Test
    public void testSplitMigrationPairsCase3() throws Exception{
        AutoSplit as = new AutoSplit();
        as.s = 3;
        as.delta = 11;
        as.r = 2;
        as.numberOfSplits = 11;
        int partitionsPerSite = 1;

        int numberOfSplits = 11;
        Set<ReconfigurationPair> migrationPairs = new HashSet<>();
        migrationPairs.add(new ReconfigurationPair(0, 3));
        migrationPairs.add(new ReconfigurationPair(0, 4));
        migrationPairs.add(new ReconfigurationPair(0, 5));
        migrationPairs.add(new ReconfigurationPair(0, 6));
        migrationPairs.add(new ReconfigurationPair(0, 7));
        migrationPairs.add(new ReconfigurationPair(0, 8));
        migrationPairs.add(new ReconfigurationPair(0, 9));
        migrationPairs.add(new ReconfigurationPair(0, 10));
        migrationPairs.add(new ReconfigurationPair(0, 11));
        migrationPairs.add(new ReconfigurationPair(0, 12));
        migrationPairs.add(new ReconfigurationPair(0, 13));
        migrationPairs.add(new ReconfigurationPair(1, 3));
        migrationPairs.add(new ReconfigurationPair(1, 4));
        migrationPairs.add(new ReconfigurationPair(1, 5));
        migrationPairs.add(new ReconfigurationPair(1, 6));
        migrationPairs.add(new ReconfigurationPair(1, 7));
        migrationPairs.add(new ReconfigurationPair(1, 8));
        migrationPairs.add(new ReconfigurationPair(1, 9));
        migrationPairs.add(new ReconfigurationPair(1, 10));
        migrationPairs.add(new ReconfigurationPair(1, 11));
        migrationPairs.add(new ReconfigurationPair(1, 12));
        migrationPairs.add(new ReconfigurationPair(1, 13));
        migrationPairs.add(new ReconfigurationPair(2, 3));
        migrationPairs.add(new ReconfigurationPair(2, 4));
        migrationPairs.add(new ReconfigurationPair(2, 5));
        migrationPairs.add(new ReconfigurationPair(2, 6));
        migrationPairs.add(new ReconfigurationPair(2, 7));
        migrationPairs.add(new ReconfigurationPair(2, 8));
        migrationPairs.add(new ReconfigurationPair(2, 9));
        migrationPairs.add(new ReconfigurationPair(2, 10));
        migrationPairs.add(new ReconfigurationPair(2, 11));
        migrationPairs.add(new ReconfigurationPair(2, 12));
        migrationPairs.add(new ReconfigurationPair(2, 13));

        Map<Pair<Integer, Integer>, Integer> pairToSplitMapping = 
                ReconfigurationUtil.splitMigrationPairs(numberOfSplits, migrationPairs, as, partitionsPerSite);
        System.out.println(pairToSplitMapping.toString());
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 3)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 4)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 5)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 6)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 7)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 8)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 9)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 10)));
        assertEquals(new Integer(8), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 11)));
        assertEquals(new Integer(9), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 12)));
        assertEquals(new Integer(10), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 13)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 3)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 4)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 5)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 6)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 7)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 8)));
        assertEquals(new Integer(10), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 9)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 10)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 11)));
        assertEquals(new Integer(8), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 12)));
        assertEquals(new Integer(9), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 13)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 3)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 4)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 5)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 6)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 7)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 8)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 9)));
        assertEquals(new Integer(9), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 10)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 11)));
        assertEquals(new Integer(10), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 12)));
        assertEquals(new Integer(8), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 13)));    

        // scale in
        migrationPairs = new HashSet<>();
        migrationPairs.add(new ReconfigurationPair(3, 0));
        migrationPairs.add(new ReconfigurationPair(4, 0));
        migrationPairs.add(new ReconfigurationPair(5, 0));
        migrationPairs.add(new ReconfigurationPair(6, 0));
        migrationPairs.add(new ReconfigurationPair(7, 0));
        migrationPairs.add(new ReconfigurationPair(8, 0));
        migrationPairs.add(new ReconfigurationPair(9, 0));
        migrationPairs.add(new ReconfigurationPair(10, 0));
        migrationPairs.add(new ReconfigurationPair(11, 0));
        migrationPairs.add(new ReconfigurationPair(12, 0));
        migrationPairs.add(new ReconfigurationPair(13, 0));
        migrationPairs.add(new ReconfigurationPair(3, 1));
        migrationPairs.add(new ReconfigurationPair(4, 1));
        migrationPairs.add(new ReconfigurationPair(5, 1));
        migrationPairs.add(new ReconfigurationPair(6, 1));
        migrationPairs.add(new ReconfigurationPair(7, 1));
        migrationPairs.add(new ReconfigurationPair(8, 1));
        migrationPairs.add(new ReconfigurationPair(9, 1));
        migrationPairs.add(new ReconfigurationPair(10, 1));
        migrationPairs.add(new ReconfigurationPair(11, 1));
        migrationPairs.add(new ReconfigurationPair(12, 1));
        migrationPairs.add(new ReconfigurationPair(13, 1));
        migrationPairs.add(new ReconfigurationPair(3, 2));
        migrationPairs.add(new ReconfigurationPair(4, 2));
        migrationPairs.add(new ReconfigurationPair(5, 2));
        migrationPairs.add(new ReconfigurationPair(6, 2));
        migrationPairs.add(new ReconfigurationPair(7, 2));
        migrationPairs.add(new ReconfigurationPair(8, 2));
        migrationPairs.add(new ReconfigurationPair(9, 2));
        migrationPairs.add(new ReconfigurationPair(10, 2));
        migrationPairs.add(new ReconfigurationPair(11, 2));
        migrationPairs.add(new ReconfigurationPair(12, 2));
        migrationPairs.add(new ReconfigurationPair(13, 2));

        pairToSplitMapping = 
                ReconfigurationUtil.splitMigrationPairs(numberOfSplits, migrationPairs, as, partitionsPerSite);
        System.out.println(pairToSplitMapping.toString());        
        assertEquals(new Integer(10), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 0)));
        assertEquals(new Integer(9), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 0)));
        assertEquals(new Integer(8), pairToSplitMapping.get(new Pair<Integer, Integer>(5, 0)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(6, 0)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(7, 0)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(8, 0)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(9, 0)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(10, 0)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(11, 0)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(12, 0)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(13, 0)));
        assertEquals(new Integer(8), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 1)));
        assertEquals(new Integer(10), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 1)));
        assertEquals(new Integer(9), pairToSplitMapping.get(new Pair<Integer, Integer>(5, 1)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(6, 1)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(7, 1)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(8, 1)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(9, 1)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(10, 1)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(11, 1)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(12, 1)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(13, 1)));
        assertEquals(new Integer(9), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 2)));
        assertEquals(new Integer(8), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 2)));
        assertEquals(new Integer(10), pairToSplitMapping.get(new Pair<Integer, Integer>(5, 2)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(6, 2)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(7, 2)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(8, 2)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(9, 2)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(10, 2)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(11, 2)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(12, 2)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(13, 2)));
    }

    @Test
    public void testGetNumberOfSplitsCase1() throws Exception{
        ReconfigurationPlan plan = new ReconfigurationPlan(catalogContext, new HashMap<String, String>());
        Integer[] range = new Integer[]{ 0 };
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 4));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 4));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 4));
        int partitionsPerSite = 1;
        int numberOfSplits = 10;
        AutoSplit as = ReconfigurationUtil.getAutoSplit(plan, partitionsPerSite, numberOfSplits);
        assertEquals(3, as.numberOfSplits);
        assertEquals(30, as.extraSplits);

        plan = new ReconfigurationPlan(catalogContext, new HashMap<String, String>());
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 4, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 4, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 4, 2));
        as = ReconfigurationUtil.getAutoSplit(plan, partitionsPerSite, numberOfSplits);
        assertEquals(3, as.numberOfSplits);
        assertEquals(30, as.extraSplits);
    }

    @Test
    public void testGetNumberOfSplitsCase2() throws Exception{
        ReconfigurationPlan plan = new ReconfigurationPlan(catalogContext, new HashMap<String, String>());
        Integer[] range = new Integer[]{ 0 };
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 4));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 5));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 6));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 7));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 8));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 4));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 5));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 6));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 7));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 8));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 4));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 5));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 6));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 7));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 8));
        int partitionsPerSite = 1;
        int numberOfSplits = 10;
        AutoSplit as = ReconfigurationUtil.getAutoSplit(plan, partitionsPerSite, numberOfSplits);
        assertEquals(6, as.numberOfSplits);

        plan = new ReconfigurationPlan(catalogContext, new HashMap<String, String>());
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 4, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 5, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 6, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 7, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 8, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 4, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 5, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 6, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 7, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 8, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 4, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 5, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 6, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 7, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 8, 2));
        as = ReconfigurationUtil.getAutoSplit(plan, partitionsPerSite, numberOfSplits);
        assertEquals(6, as.numberOfSplits);
    }

    @Test
    public void testGetNumberOfSplitsCase3() throws Exception{
        ReconfigurationPlan plan = new ReconfigurationPlan(catalogContext, new HashMap<String, String>());
        Integer[] range = new Integer[]{ 0 };
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 4));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 5));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 6));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 7));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 8));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 9));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 10));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 11));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 12));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 13));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 4));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 5));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 6));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 7));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 8));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 9));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 10));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 11));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 12));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 13));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 4));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 5));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 6));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 7));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 8));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 9));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 10));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 11));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 12));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 13));
        int partitionsPerSite = 1;
        int numberOfSplits = 10;
        AutoSplit as = ReconfigurationUtil.getAutoSplit(plan, partitionsPerSite, numberOfSplits);
        assertEquals(11, as.numberOfSplits);

        plan = new ReconfigurationPlan(catalogContext, new HashMap<String, String>());
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 4, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 5, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 6, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 7, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 8, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 9, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 10, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 11, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 12, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 13, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 4, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 5, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 6, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 7, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 8, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 9, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 10, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 11, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 12, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 13, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 4, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 5, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 6, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 7, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 8, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 9, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 10, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 11, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 12, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 13, 2));
        as = ReconfigurationUtil.getAutoSplit(plan, partitionsPerSite, numberOfSplits);
        assertEquals(11, as.numberOfSplits);
    }

    @Test
    public void testSplitMigrationPairsCase1_2() throws Exception{
        AutoSplit as = new AutoSplit();
        as.s = 3;
        as.delta = 1;
        as.r = 1;
        as.numberOfSplits = 3;
        int partitionsPerSite = 1;

        int numberOfSplits = 3;
        Set<ReconfigurationPair> migrationPairs = new HashSet<>();
        migrationPairs.add(new ReconfigurationPair(0, 3));
        migrationPairs.add(new ReconfigurationPair(1, 3));
        migrationPairs.add(new ReconfigurationPair(2, 3));

        Map<Pair<Integer, Integer>, Integer> pairToSplitMapping = 
                ReconfigurationUtil.splitMigrationPairs(numberOfSplits, migrationPairs, as, partitionsPerSite);
        System.out.println(pairToSplitMapping.toString());
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 3)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 3)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 3)));

        // scale in
        migrationPairs = new HashSet<>();
        migrationPairs.add(new ReconfigurationPair(3, 0));
        migrationPairs.add(new ReconfigurationPair(3, 1));
        migrationPairs.add(new ReconfigurationPair(3, 2));

        pairToSplitMapping = 
                ReconfigurationUtil.splitMigrationPairs(numberOfSplits, migrationPairs, as, partitionsPerSite);
        System.out.println(pairToSplitMapping.toString());
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 0)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 1)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 2)));
    }

    @Test
    public void testSplitMigrationPairsCase2_2() throws Exception{
        AutoSplit as = new AutoSplit();
        as.s = 1;
        as.delta = 2;
        as.r = 0;
        as.numberOfSplits = 2;
        int partitionsPerSite = 3;

        int numberOfSplits = 2;
        Set<ReconfigurationPair> migrationPairs = new HashSet<>();
        migrationPairs.add(new ReconfigurationPair(0, 3));
        migrationPairs.add(new ReconfigurationPair(0, 6));
        migrationPairs.add(new ReconfigurationPair(1, 4));
        migrationPairs.add(new ReconfigurationPair(1, 7));
        migrationPairs.add(new ReconfigurationPair(2, 5));
        migrationPairs.add(new ReconfigurationPair(2, 8));

        Map<Pair<Integer, Integer>, Integer> pairToSplitMapping = 
                ReconfigurationUtil.splitMigrationPairs(numberOfSplits, migrationPairs, as, partitionsPerSite);
        System.out.println(pairToSplitMapping.toString());
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 3)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 6)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 4)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 7)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 5)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 8)));  

        // scale in
        migrationPairs = new HashSet<>();
        migrationPairs.add(new ReconfigurationPair(3, 0));
        migrationPairs.add(new ReconfigurationPair(6, 0));
        migrationPairs.add(new ReconfigurationPair(4, 1));
        migrationPairs.add(new ReconfigurationPair(7, 1));
        migrationPairs.add(new ReconfigurationPair(5, 2));
        migrationPairs.add(new ReconfigurationPair(8, 2));

        pairToSplitMapping = 
                ReconfigurationUtil.splitMigrationPairs(numberOfSplits, migrationPairs, as, partitionsPerSite);
        System.out.println(pairToSplitMapping.toString());
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 0)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(6, 0)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 1)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(7, 1)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(5, 2)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(8, 2)));
    }

    @Test
    public void testSplitMigrationPairsCase3_2() throws Exception{
        AutoSplit as = new AutoSplit();
        as.s = 3;
        as.delta = 10;
        as.r = 1;
        as.numberOfSplits = 10;
        int partitionsPerSite = 1;        

        int numberOfSplits = 10;
        Set<ReconfigurationPair> migrationPairs = new HashSet<>();
        migrationPairs.add(new ReconfigurationPair(0, 3));
        migrationPairs.add(new ReconfigurationPair(0, 4));
        migrationPairs.add(new ReconfigurationPair(0, 5));
        migrationPairs.add(new ReconfigurationPair(0, 6));
        migrationPairs.add(new ReconfigurationPair(0, 7));
        migrationPairs.add(new ReconfigurationPair(0, 8));
        migrationPairs.add(new ReconfigurationPair(0, 9));
        migrationPairs.add(new ReconfigurationPair(0, 10));
        migrationPairs.add(new ReconfigurationPair(0, 11));
        migrationPairs.add(new ReconfigurationPair(0, 12));
        migrationPairs.add(new ReconfigurationPair(1, 3));
        migrationPairs.add(new ReconfigurationPair(1, 4));
        migrationPairs.add(new ReconfigurationPair(1, 5));
        migrationPairs.add(new ReconfigurationPair(1, 6));
        migrationPairs.add(new ReconfigurationPair(1, 7));
        migrationPairs.add(new ReconfigurationPair(1, 8));
        migrationPairs.add(new ReconfigurationPair(1, 9));
        migrationPairs.add(new ReconfigurationPair(1, 10));
        migrationPairs.add(new ReconfigurationPair(1, 11));
        migrationPairs.add(new ReconfigurationPair(1, 12));
        migrationPairs.add(new ReconfigurationPair(2, 3));
        migrationPairs.add(new ReconfigurationPair(2, 4));
        migrationPairs.add(new ReconfigurationPair(2, 5));
        migrationPairs.add(new ReconfigurationPair(2, 6));
        migrationPairs.add(new ReconfigurationPair(2, 7));
        migrationPairs.add(new ReconfigurationPair(2, 8));
        migrationPairs.add(new ReconfigurationPair(2, 9));
        migrationPairs.add(new ReconfigurationPair(2, 10));
        migrationPairs.add(new ReconfigurationPair(2, 11));
        migrationPairs.add(new ReconfigurationPair(2, 12));

        Map<Pair<Integer, Integer>, Integer> pairToSplitMapping = 
                ReconfigurationUtil.splitMigrationPairs(numberOfSplits, migrationPairs, as, partitionsPerSite);
        System.out.println(pairToSplitMapping.toString());
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 3)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 4)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 5)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 6)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 7)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 8)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 9)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 10)));
        assertEquals(new Integer(8), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 11)));
        assertEquals(new Integer(9), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 12)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 3)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 4)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 5)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 6)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 7)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 8)));
        assertEquals(new Integer(9), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 9)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 10)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 11)));
        assertEquals(new Integer(8), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 12)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 3)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 4)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 5)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 6)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 7)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 8)));
        assertEquals(new Integer(8), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 9)));
        assertEquals(new Integer(9), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 10)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 11)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 12)));

        // scale in
        migrationPairs = new HashSet<>();
        migrationPairs.add(new ReconfigurationPair(3, 0));
        migrationPairs.add(new ReconfigurationPair(4, 0));
        migrationPairs.add(new ReconfigurationPair(5, 0));
        migrationPairs.add(new ReconfigurationPair(6, 0));
        migrationPairs.add(new ReconfigurationPair(7, 0));
        migrationPairs.add(new ReconfigurationPair(8, 0));
        migrationPairs.add(new ReconfigurationPair(9, 0));
        migrationPairs.add(new ReconfigurationPair(10, 0));
        migrationPairs.add(new ReconfigurationPair(11, 0));
        migrationPairs.add(new ReconfigurationPair(12, 0));
        migrationPairs.add(new ReconfigurationPair(3, 1));
        migrationPairs.add(new ReconfigurationPair(4, 1));
        migrationPairs.add(new ReconfigurationPair(5, 1));
        migrationPairs.add(new ReconfigurationPair(6, 1));
        migrationPairs.add(new ReconfigurationPair(7, 1));
        migrationPairs.add(new ReconfigurationPair(8, 1));
        migrationPairs.add(new ReconfigurationPair(9, 1));
        migrationPairs.add(new ReconfigurationPair(10, 1));
        migrationPairs.add(new ReconfigurationPair(11, 1));
        migrationPairs.add(new ReconfigurationPair(12, 1));
        migrationPairs.add(new ReconfigurationPair(3, 2));
        migrationPairs.add(new ReconfigurationPair(4, 2));
        migrationPairs.add(new ReconfigurationPair(5, 2));
        migrationPairs.add(new ReconfigurationPair(6, 2));
        migrationPairs.add(new ReconfigurationPair(7, 2));
        migrationPairs.add(new ReconfigurationPair(8, 2));
        migrationPairs.add(new ReconfigurationPair(9, 2));
        migrationPairs.add(new ReconfigurationPair(10, 2));
        migrationPairs.add(new ReconfigurationPair(11, 2));
        migrationPairs.add(new ReconfigurationPair(12, 2));

        pairToSplitMapping = 
                ReconfigurationUtil.splitMigrationPairs(numberOfSplits, migrationPairs, as, partitionsPerSite);
        System.out.println(pairToSplitMapping.toString());        
        assertEquals(new Integer(9), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 0)));
        assertEquals(new Integer(8), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 0)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(5, 0)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(6, 0)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(7, 0)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(8, 0)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(9, 0)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(10, 0)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(11, 0)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(12, 0)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 1)));
        assertEquals(new Integer(9), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 1)));
        assertEquals(new Integer(8), pairToSplitMapping.get(new Pair<Integer, Integer>(5, 1)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(6, 1)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(7, 1)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(8, 1)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(9, 1)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(10, 1)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(11, 1)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(12, 1)));
        assertEquals(new Integer(8), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 2)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 2)));
        assertEquals(new Integer(9), pairToSplitMapping.get(new Pair<Integer, Integer>(5, 2)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(6, 2)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(7, 2)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(8, 2)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(9, 2)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(10, 2)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(11, 2)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(12, 2)));
    }

    @Test
    public void testSplitMigrationPairsCase3_3() throws Exception{
        AutoSplit as = new AutoSplit();
        as.s = 5;
        as.delta = 8;
        as.r = 3;
        as.numberOfSplits = 8;
        int partitionsPerSite = 1;        

        int numberOfSplits = 8;
        Set<ReconfigurationPair> migrationPairs = new HashSet<>();
        migrationPairs.add(new ReconfigurationPair(0, 5));
        migrationPairs.add(new ReconfigurationPair(0, 6));
        migrationPairs.add(new ReconfigurationPair(0, 7));
        migrationPairs.add(new ReconfigurationPair(0, 8));
        migrationPairs.add(new ReconfigurationPair(0, 9));
        migrationPairs.add(new ReconfigurationPair(0, 10));
        migrationPairs.add(new ReconfigurationPair(0, 11));
        migrationPairs.add(new ReconfigurationPair(0, 12));
        migrationPairs.add(new ReconfigurationPair(1, 5));
        migrationPairs.add(new ReconfigurationPair(1, 6));
        migrationPairs.add(new ReconfigurationPair(1, 7));
        migrationPairs.add(new ReconfigurationPair(1, 8));
        migrationPairs.add(new ReconfigurationPair(1, 9));
        migrationPairs.add(new ReconfigurationPair(1, 10));
        migrationPairs.add(new ReconfigurationPair(1, 11));
        migrationPairs.add(new ReconfigurationPair(1, 12));
        migrationPairs.add(new ReconfigurationPair(2, 5));
        migrationPairs.add(new ReconfigurationPair(2, 6));
        migrationPairs.add(new ReconfigurationPair(2, 7));
        migrationPairs.add(new ReconfigurationPair(2, 8));
        migrationPairs.add(new ReconfigurationPair(2, 9));
        migrationPairs.add(new ReconfigurationPair(2, 10));
        migrationPairs.add(new ReconfigurationPair(2, 11));
        migrationPairs.add(new ReconfigurationPair(2, 12));
        migrationPairs.add(new ReconfigurationPair(3, 5));
        migrationPairs.add(new ReconfigurationPair(3, 6));
        migrationPairs.add(new ReconfigurationPair(3, 7));
        migrationPairs.add(new ReconfigurationPair(3, 8));
        migrationPairs.add(new ReconfigurationPair(3, 9));
        migrationPairs.add(new ReconfigurationPair(3, 10));
        migrationPairs.add(new ReconfigurationPair(3, 11));
        migrationPairs.add(new ReconfigurationPair(3, 12));
        migrationPairs.add(new ReconfigurationPair(4, 5));
        migrationPairs.add(new ReconfigurationPair(4, 6));
        migrationPairs.add(new ReconfigurationPair(4, 7));
        migrationPairs.add(new ReconfigurationPair(4, 8));
        migrationPairs.add(new ReconfigurationPair(4, 9));
        migrationPairs.add(new ReconfigurationPair(4, 10));
        migrationPairs.add(new ReconfigurationPair(4, 11));
        migrationPairs.add(new ReconfigurationPair(4, 12));

        Map<Pair<Integer, Integer>, Integer> pairToSplitMapping = 
                ReconfigurationUtil.splitMigrationPairs(numberOfSplits, migrationPairs, as, partitionsPerSite);
        System.out.println(pairToSplitMapping.toString());
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 5)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 6)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 7)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 8)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 9)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 10)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 11)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(0, 12)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 5)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 6)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 7)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 8)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 9)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 10)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 11)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(1, 12)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 5)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 6)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 7)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 8)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 9)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 10)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 11)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(2, 12)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 5)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 6)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 7)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 8)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 9)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 10)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 11)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(3, 12)));
        assertEquals(new Integer(1), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 5)));
        assertEquals(new Integer(2), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 6)));
        assertEquals(new Integer(4), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 7)));
        assertEquals(new Integer(5), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 8)));
        assertEquals(new Integer(0), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 9)));
        assertEquals(new Integer(6), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 10)));
        assertEquals(new Integer(7), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 11)));
        assertEquals(new Integer(3), pairToSplitMapping.get(new Pair<Integer, Integer>(4, 12)));

    }


    @Test
    public void testGetNumberOfSplitsCase1_2() throws Exception{
        ReconfigurationPlan plan = new ReconfigurationPlan(catalogContext, new HashMap<String, String>());
        Integer[] range = new Integer[]{ 0 };
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 3));
        int partitionsPerSite = 1;
        int numberOfSplits = 10;
        AutoSplit as = ReconfigurationUtil.getAutoSplit(plan, partitionsPerSite, numberOfSplits);
        assertEquals(3, as.numberOfSplits);
        assertEquals(30, as.extraSplits);

        plan = new ReconfigurationPlan(catalogContext, new HashMap<String, String>());
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 2));
        as = ReconfigurationUtil.getAutoSplit(plan, partitionsPerSite, numberOfSplits);
        assertEquals(3, as.numberOfSplits);
        assertEquals(30, as.extraSplits);
    }

    @Test
    public void testGetNumberOfSplitsCase2_2() throws Exception{
        ReconfigurationPlan plan = new ReconfigurationPlan(catalogContext, new HashMap<String, String>());
        Integer[] range = new Integer[]{ 0 };
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 6));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 4));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 7));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 5));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 8));
        int partitionsPerSite = 3;
        int numberOfSplits = 10;
        AutoSplit as = ReconfigurationUtil.getAutoSplit(plan, partitionsPerSite, numberOfSplits);
        assertEquals(2, as.numberOfSplits);

        plan = new ReconfigurationPlan(catalogContext, new HashMap<String, String>());
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 6, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 4, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 7, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 5, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 8, 2));
        as = ReconfigurationUtil.getAutoSplit(plan, partitionsPerSite, numberOfSplits);
        assertEquals(2, as.numberOfSplits);
    }

    @Test
    public void testGetNumberOfSplitsCase3_2() throws Exception{
        ReconfigurationPlan plan = new ReconfigurationPlan(catalogContext, new HashMap<String, String>());
        Integer[] range = new Integer[]{ 0 };
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 4));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 5));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 6));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 7));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 8));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 9));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 10));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 11));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 0, 12));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 4));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 5));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 6));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 7));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 8));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 9));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 10));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 11));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 1, 12));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 3));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 4));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 5));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 6));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 7));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 8));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 9));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 10));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 11));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 2, 12));
        int partitionsPerSite = 1;
        int numberOfSplits = 10;
        AutoSplit as = ReconfigurationUtil.getAutoSplit(plan, partitionsPerSite, numberOfSplits);
        assertEquals(10, as.numberOfSplits);

        plan = new ReconfigurationPlan(catalogContext, new HashMap<String, String>());
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 4, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 5, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 6, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 7, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 8, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 9, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 10, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 11, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 12, 0));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 4, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 5, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 6, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 7, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 8, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 9, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 10, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 11, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 12, 1));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 3, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 4, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 5, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 6, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 7, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 8, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 9, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 10, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 11, 2));
        plan.addRange(new ReconfigurationRange(catalogContext.getTableByName("usertable"), range, range, 12, 2));
        as = ReconfigurationUtil.getAutoSplit(plan, partitionsPerSite, numberOfSplits);
        assertEquals(10, as.numberOfSplits);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp(ProjectType.YCSB);


        String tmp_dir = System.getProperty("java.io.tmpdir");
        json_path1 = FileUtil.join(tmp_dir, "test1.json");
        FileUtil.writeStringToFile(json_path1, plan1);
        json_path2 = FileUtil.join(tmp_dir, "test2.json");
        FileUtil.writeStringToFile(json_path2, plan2);
    }

    String plan1 = "{" 
            + "\"partition_plan\": {" 
            + "    \"tables\": {" 
            + "      \"usertable\": {" 
            + "        \"partitions\": {" 
            + "          \"0\": \"0-2000000\", " 
            + "          \"1\": \"2000000-4000000\", " 
            + "          \"2\": \"4000000-6000000\", " 
            + "          \"3\": \"6000000-8000000\", " 
            + "          \"4\": \"8000000-10000000\", " 
            + "          \"5\": \"10000000-12000000\", " 
            + "          \"6\": \"12000000-14000000\", " 
            + "          \"7\": \"14000000-16000000\", " 
            + "          \"8\": \"16000000-18000000\", " 
            + "          \"9\": \"18000000-20000000\", " 
            + "          \"10\": \"20000000-22000000\"," 
            + "          \"11\": \"22000000-24000000\"," 
            + "          \"12\": \"24000000-26000000\"," 
            + "          \"13\": \"26000000-28000000\"," 
            + "          \"14\": \"28000000-30000000\"," 
            + "          \"15\": \"30000000-32000000\"," 
            + "          \"16\": \"32000000-34000000\"," 
            + "          \"17\": \"34000000-36000000\"," 
            + "          \"18\": \"36000000-38000000\"," 
            + "          \"19\": \"38000000-40000000\"," 
            + "          \"20\": \"40000000-42000000\"," 
            + "          \"21\": \"42000000-44000000\"," 
            + "          \"22\": \"44000000-46000000\"," 
            + "          \"23\": \"46000000-48000000\"," 
            + "          \"24\": \"48000000-50000000\"," 
            + "          \"25\": \"50000000-52000000\"," 
            + "          \"26\": \"52000000-54000000\"," 
            + "          \"27\": \"54000000-56000000\"," 
            + "          \"28\": \"56000000-58000000\"," 
            + "          \"29\": \"58000000-60000000\","
            + "        }" 
            + "      }" 
            + "    }" 
            + "  }, " 
            + "  \"default_table\": \"usertable\"" 
            + "}";

    String plan2 = "{" 
            + "\"partition_plan\": {" 
            + "\"tables\":{" 
            + "\"usertable\":{" 
            + "\"partitions\": {" 
            + "      \"0\":\"1801380-2000000,2767578-2773766,2800215-2808566,2891559-2897250,2967920-2972382,3000259-3013354,3145467-3152723,4041364-4044286,4059162-4072048,4212828-4226432,4378228-4382113,4402517-4406111,4500085-4500800,4504523-4521331,4745700-4769298,6205337-6205745,6228342-6237590,6401193-6412770,6559972-6560941,8021026-8029045,8102494-8108545,8187878-8200033,28000000-28017723\"," 
            + "\"1\":\"3192190-4000000\"," 
            + "\"2\":\"4858542-5951305,5951308-6000000,56075102-56097233\"," 
            + "\"3\":\"6598777-6671003,6671006-8000000,8200033-8206946,24016749-24045495\"," 
            + "\"4\":\"8310829-9504551,9504554-10000000\"," 
            + "\"5\":\"10100004-12000000,56279973-56300007\"," 
            + "\"6\":\"4718760-4726747,4837822-4844939,6081135-6091293,6191465-6200034,6303825-6326396,8108545-8119259,10021102-10040646,12000000-14000000\"," 
            + "\"7\":\"2972382-2972519,2977396-2977467,2977771-2985840,3075789-3079539,3100284-3111684,4044286-4048150,4104026-4105232,4116942-4127879,4228006-4229734,4251220-4251532,4268719-4275584,4341871-4361594,4585052-4595474,4769298-4775476,4844939-4848131,6041474-6042237,6058084-6069246,6140661-6154324,6364512-6364871,6400066-6401193,6412770-6413558,6434848-6434932,6435625-6450419,8043252-8045578,8100012-8102494,8133496-8143901,14000000-16000000,24000000-24005632,24050777-24086558\"," 
            + "\"8\":\"2500143-2504011,2542285-2543777,2547340-2553140,2584134-2591061,2656932-2657253,2659564-2661371,2694918-2700189,2722076-2724982,2733491-2742486,2808566-2813421,2866203-2877720,2988633-2989912,2995221-3000259,3043085-3051490,3140717-3143080,3174087-3178041,4012926-4020290,4105232-4116942,4229734-4230808,4238327-4251220,4387191-4388774,4398952-4400069,4406111-4423217,4700111-4705579,4775476-4775827,4799632-4808386,6042237-6058084,6205745-6217510,6474315-6483937,6572490-6598777,16000000-18000000,56000000-56075102\"," 
            + "\"9\":\"2181338-2185172,2206324-2212532,2239785-2240061,2240415-2244362,2258422-2263639,2300081-2307368,2325120-2325460,2327306-2328806,2333534-2338494,2366142-2368595,2377991-2387551,2451278-2460150,2525623-2527639,2535144-2542285,2574590-2579305,2633356-2638768,2682589-2694918,2775698-2780850,2813421-2816717,2841832-2848340,2906767-2916279,3016491-3021530,3079539-3087188,3152723-3156218,4002591-4005674,4022925-4030002,4127879-4152874,4382113-4387191,4472810-4474349,4500800-4504523,4544349-4546519,4553660-4585052,6155911-6167579,6334390-6352562,8034659-8040266,8127289-8133496,8225722-8225894,8238878-8274527,18035059-20000000,46010866-46027684\"," 
            + "\"10\":\"2083025-2099032,2141724-2147937,2169426-2173655,2177636-2181338,2191267-2200051,2240061-2240415,2244362-2252055,2284168-2289621,2307368-2311228,2328806-2333534,2361939-2366142,2388006-2399506,2467762-2468418,2474666-2476320,2480752-2484276,2504011-2509388,2553140-2556662,2567066-2571186,2600168-2606919,2662248-2665751,2710467-2718666,2773766-2775698,2780850-2792191,2885563-2891559,2955928-2958525,2977467-2977771,2985840-2988633,3013354-3016491,3033061-3038459,3099151-3100284,3111684-3113322,3125258-3140194,4173492-4177246,4188751-4196057,4291970-4300051,4366798-4378228,4529275-4535978,4595474-4598128,4648267-4652332,4717995-4718760,4726747-4745700,6116048-6118454,6154324-6155911,6168207-6178916,6360753-6364512,6450419-6474315,8217627-8217949,8274527-8280743,20000000-22000000,24045495-24050777,26000000-26010391,32115901-32130782\"," 
            + "\"11\":\"2012457-2017220,2024884-2034029,2040807-2043505,2048322-2054447,2060720-2071156,2079615-2083025,2110835-2128515,2156163-2160121,2173655-2177636,2185865-2190055,2212532-2214351,2225424-2234363,2263639-2267770,2289621-2300081,2339569-2350417,2423484-2446969,2579305-2584134,2642066-2643870,2645232-2647408,2661371-2662248,2665751-2682589,2794503-2800215,2877720-2885563,2955291-2955928,2958525-2967920,3054596-3066226,3185937-3192190,4072048-4093759,4339302-4341871,4361594-4366798,4388774-4398952,4546519-4553660,4680298-4693841,4851934-4858542,6091293-6100013,6200034-6204554,6277724-6281373,6326396-6334390,6500526-6512645,8059621-8084239,22000000-24000000,46003080-46010866,56140314-56200006\"," 
            + "\"12\":\"1601380-1701380,24143773-26000000,32042146-32054461,56200006-56279973\"," 
            + "\"13\":\"1301380-1401380,26026769-28000000\"," 
            + "\"14\":\"1101380-1201380,24100006-24143773,28017723-30000000\"," 
            + "\"15\":\"701380-801380,6100013-6106350,6167579-6168207,6178916-6189060,6352562-6360753,6523585-6527672,6548659-6551117,8000000-8010121,8151836-8157839,8217949-8225722,24096995-24100006,26010391-26017288,30000000-32000000,32054461-32063491,56121740-56125692,56300007-56371921\"," 
            + "\"17\":\"401379-501379,4438817-4443314,4521331-4529275,4600098-4602820,4693841-4700111,4775827-4792248,6118454-6119902,6129777-6140661,6300053-6303822,6376101-6400066,8182401-8187878,10045731-10048759,24005632-24016749,32010641-32042146,34000000-36000000\"," 
            + "\"16\":\"601379-624839,1501380-1601380,32130782-34000000\"," 
            + "\"19\":\"2146-2164,2413-2438,2441-2459,2725-2753,2949-3049,5156-5215,5811-5835,5999-6014,6187-6204,6318-6340,6399-6434,6579-6601,6680-6710,7032-7066,7366-7440,8220-8422,11114-11234,13452-14197,28975-29091,29528-29868,101373-123152,1201380-1301380,2110832-2110835,38000000-40000000\"," 
            + "\"18\":\"16563-17289,29868-31301,317395-389283,2476323-2480752,2509388-2513546,2543777-2547340,2556662-2562754,2632599-2633356,2638768-2642066,2657253-2659564,2700189-2705907,2727818-2731583,2759484-2767578,2820531-2829984,2916279-2928392,3038459-3043085,3096904-3099151,3116899-3120007,3156218-3161150,4011198-4012926,4030002-4041364,4181754-4188751,4234862-4237937,4300713-4312654,4400069-4402517,4455372-4459844,4535978-4544346,4635198-4644415,4808386-4831063,6239828-6253784,6533187-6543551,8045578-8059621,18000000-18010314,26017288-26026769,36000000-38000000,46027684-46044437\"," 
            + "\"21\":\"274-278,1151-1153,2352-2354,2459-2466,2468-2501,2753-2768,3049-3067,3343-3399,3835-3858,4214-4227,4284-4372,5837-5862,6204-6297,7066-7136,7661-7678,7743-7880,10031-10110,10538-10822,17289-17424,18127-20036,301376-317395,901380-1001380,8040266-8043252,8096965-8100012,8157839-8163616,8225894-8238878,32100393-32103607,42000000-44000000,46000000-46003080,56100002-56118083\"," 
            + "\"20\":\"2501-2516,2768-2816,3067-3104,3572-3589,3592-3640,3858-3902,4372-4388,4454-4764,8994-9276,14197-14375,17553-17705,22744-23450,35146-44917,624839-701380,2848340-2853487,2899748-2906767,2972519-2977396,3021530-3030777,3120007-3125258,4000000-4002591,4005674-4011198,4093759-4100015,4177246-4181754,4200031-4201426,4209483-4212828,4268494-4268719,4275584-4291970,4443314-4455372,4598128-4600098,4652332-4661940,4831063-4836167,6008386-6009099,6020201-6041474,6287502-6300053,6543551-6548659,6671003-6671006,8010121-8021026,8163616-8168437,8206946-8217627,32000000-32010641,40000000-42000000,46044437-46100004\"," 
            + "\"23\":\"99-101,201-204,222-225,272-274,292-294,306-310,331-334,352-354,358-361,363-365,396-398,406-409,427-430,434-442,446-452,457-461,466-469,498-502,507-509,512-515,522-524,529-534,542-544,555-558,562-565,570-572,577-580,582-587,593-596,599-601,607-610,612-618,624-628,630-636,638-646,652-655,659-664,668-676,679-682,684-700,702-715,725-729,739-746,749-752,757-774,777-780,791-798,801-805,809-815,822-824,826-858,863-869,874-877,880-894,899-911,913-919,923-926,929-941,946-982,984-989,991-995,999-1008,1012-1026,1035-1043,1047-1065,1072-1111,1114-1124,1126-1134,1137-1140,1146-1151,1153-1159,1162-1169,1174-1178,1187-1199,1202-1204,1210-1230,1233-1239,1241-1252,1254-1267,1271-1276,1279-1281,1284-1311,1315-1317,1322-1331,1335-1377,1380-1382,1384-1407,1412-1419,1422-1436,1439-1456,1459-1462,1465-1467,1469-1477,1481-1508,1513-1516,1737-1739,2164-2187,2192-2207,2518-2545,2547-2601,3399-3408,3411-3436,3640-3658,3739-3758,3902-3934,4388-4417,5215-5248,5345-5407,6014-6114,7136-7186,7440-7478,7504-7560,8422-8468,8731-8902,11234-11310,12241-13452,101056-101373,147057-201375,2136886-2141724,2147937-2156163,2185172-2185865,2190055-2191267,2200051-2202962,2214351-2222280,2252055-2255139,2267770-2276012,2311228-2317027,2350664-2355527,2374258-2377991,2405658-2414925,2464432-2464742,2468418-2474666,2513546-2525623,2591061-2596439,2647408-2653602,2718666-2722076,2731583-2733491,2743539-2749781,2792191-2794503,2816717-2820531,2857935-2866203,2946646-2955291,3030777-3033061,3068839-3075789,3140194-3140717,3143080-3145467,3178041-3185937,4100015-4104026,4160581-4166305,4196057-4200031,4230808-4234862,4326065-4339302,4437557-4438817,4472162-4472810,4486738-4500085,4668942-4680298,4836167-4837822,4848131-4851934,6069246-6081135,6189060-6191465,6204554-6205337,6237590-6239828,6266529-6277724,6486476-6500007,8084239-8096965,10048759-10100004,44000000-44058665,46100004-48000000\"," 
            + "\"22\":\"204-207,237-240,354-356,520-522,551-553,558-560,820-822,941-946,1065-1072,1276-1279,1508-1511,1516-1529,1531-1535,1537-1539,1541-1586,1588-1605,1607-1624,1629-1692,2207-2244,3104-3135,3436-3490,3589-3592,3934-3956,4227-4269,4764-4999,7678-7714,8031-8182,10500-10538,10822-10894,11626-11739,14874-15350,23450-23906,32394-35146,389283-401379,801380-901380,6303822-6303825,6434932-6435625,6483937-6486476,6500007-6500526,6512645-6523043,8029045-8034659,8126944-8127289,8143901-8151836,8300041-8310829,32063491-32100005,44058665-46000000,52000000-52071836\"," 
            + "\"25\":\"38-40,104-108,138-141,178-180,198-201,218-220,247-249,289-292,304-306,327-331,365-368,371-376,414-417,471-478,481-483,515-518,636-638,664-666,737-739,746-749,755-757,789-791,869-874,1033-1035,1142-1146,1178-1182,1204-1210,1436-1439,1692-1732,1734-1737,1739-1741,1743-1755,2187-2190,2244-2297,2601-2616,2816-2830,2832-2847,3135-3168,3490-3525,3758-3785,3956-3996,4999-5068,5678-5775,6710-6721,6724-6822,7880-7920,8468-8526,9485-9673,11739-11896,15350-16321,44917-81937,2000000-2012457,2017220-2024884,2034029-2040807,2043505-2048322,2054447-2060720,2071156-2079615,2099032-2110832,2128515-2136886,2160121-2169426,2202962-2206324,2222280-2225424,2234363-2239785,2255139-2258422,2276012-2284168,2317027-2325120,2368595-2374258,2414925-2423484,2464742-2467762,2484276-2485798,2497532-2500143,2527639-2535144,2562754-2567066,2606919-2632599,2829984-2839950,2940669-2946646,2989912-2995221,3051490-3054596,3088225-3091087,3113322-3116899,3161150-3169170,4054830-4059162,4152874-4160581,4201426-4202211,4207848-4209483,4226432-4228006,4237937-4238327,4251532-4268494,4459844-4472162,4602820-4635198,6253784-6266529,6523043-6523585,6527672-6533187,6560941-6572490,8176227-8182401,10040646-10045731,24086558-24096995,32100005-32100393,32103607-32115901,50000000-52000000\"," 
            + "\"24\":\"36-38,44-47,62-64,83-88,101-104,127-129,136-138,168-170,187-191,210-213,220-222,240-243,256-258,269-272,278-280,334-336,338-340,349-352,379-382,387-392,412-414,444-446,461-463,495-498,518-520,527-529,572-575,587-593,610-612,618-621,649-652,729-731,798-801,807-809,824-826,919-923,989-991,997-999,1008-1010,1111-1114,1140-1142,1184-1187,1331-1335,1462-1465,1732-1734,1755-1794,2438-2441,2466-2468,2545-2547,2616-2624,2626-2645,2830-2832,2847-2879,3168-3218,3785-3798,3996-4040,5068-5139,5835-5837,5862-5884,6114-6154,6434-6511,6913-6965,7478-7504,7714-7743,8182-8220,8568-8656,10110-10213,11310-11440,14375-14874,23906-28510,501379-601379,2476320-2476323,4474349-4486738,4544346-4544349,4644415-4647542,4705579-4717995,6000000-6008386,6106350-6116048,6217510-6228342,6413558-6434848,8168437-8176227,9504551-9504554,18010314-18035059,48000000-50000000\"," 
            + "\"27\":\"9-11,18-20,30-34,40-44,74-76,97-99,108-111,162-166,176-178,180-182,207-210,267-269,301-304,312-314,361-363,382-387,404-406,409-412,452-454,463-466,478-481,489-491,502-504,534-537,548-551,553-555,575-577,596-599,601-603,666-668,784-787,818-820,858-861,877-880,926-929,1010-1012,1159-1162,1182-1184,1230-1233,1317-1320,1377-1380,1382-1384,1419-1422,1586-1588,1794-1875,2065-2067,2190-2192,2297-2325,2645-2665,2879-2907,3218-3249,3408-3411,3658-3698,4040-4119,4212-4214,5407-5532,6601-6653,7186-7303,8902-8975,10213-10363,11896-12241,21937-22744,81937-101056,1001380-1101380,10000000-10021102,54000000-56000000\"," 
            + "\"26\":\"64-68,72-74,76-83,88-90,121-127,131-134,141-144,159-162,184-187,194-198,232-234,245-247,254-256,260-264,287-289,299-301,310-312,340-344,368-371,419-421,425-427,430-432,442-444,454-457,469-471,544-546,580-582,603-605,682-684,715-718,731-735,780-784,805-807,894-897,1026-1029,1045-1047,1199-1202,1267-1271,1410-1412,1456-1459,1467-1469,1477-1479,1529-1531,1875-1900,2325-2352,2354-2387,2665-2699,3249-3288,3798-3820,4119-4156,5139-5156,5248-5313,5775-5811,6297-6318,6340-6375,6511-6563,6721-6724,6965-7032,7560-7592,7920-7968,8656-8731,9673-10031,17424-17553,20036-20480,28831-28975,29091-29528,123152-147057,1401380-1501380,5951305-5951308,52071836-54000000,56097233-56100002,56118083-56121740,56125692-56140314\"," 
            + "\"29\":\"2-4,11-15,23-25,34-36,47-54,58-62,90-93,114-121,129-131,147-149,151-156,166-168,191-194,213-216,225-228,230-232,252-254,283-285,296-299,314-320,322-327,376-379,398-404,421-425,432-434,485-489,491-495,504-507,524-527,539-542,568-570,605-607,621-624,646-649,655-659,676-679,700-702,718-723,787-789,982-984,1029-1031,1124-1126,1134-1137,1172-1174,1252-1254,1320-1322,1479-1481,1511-1513,1535-1537,1539-1541,1605-1607,1624-1629,1900-2065,2067-2111,2387-2413,2516-2518,2907-2949,3288-3307,3525-3572,3698-3739,4269-4284,4417-4454,5532-5565,5598-5635,6154-6187,6375-6399,6563-6579,6653-6680,6822-6913,7592-7661,8526-8568,9276-9343,10363-10500,11440-11626,16321-16433,17705-18071,28510-28831,31301-32394,235865-301376,2325460-2327306,2338494-2339569,2350417-2350664,2355527-2361939,2387551-2388006,2399506-2405658,2446969-2451278,2460150-2464432,2485798-2497532,2571186-2574590,2596439-2600168,2643870-2645232,2653602-2656932,2705907-2710467,2724982-2727818,2742486-2743539,2749781-2759484,2839950-2841832,2853487-2857935,2897250-2899748,2928392-2940669,3066226-3068839,3087188-3088225,3091087-3096904,3169170-3174087,4020290-4022925,4048150-4054830,4166305-4173492,4202211-4207848,4300051-4300713,4312654-4326065,4423217-4437557,4647542-4648267,4661940-4668942,4792248-4799632,6009099-6020201,6119902-6129777,6281373-6287502,6364871-6376101,6551117-6559972,8119259-8126944,8280743-8300041,58000000-60000000\"," 
            + "\"28\":\"0-2,4-9,15-18,20-23,25-30,54-58,68-72,93-97,111-114,134-136,144-147,149-151,156-159,170-176,182-184,216-218,228-230,234-237,243-245,249-252,258-260,264-267,280-283,285-287,294-296,320-322,336-338,344-349,356-358,392-396,417-419,483-485,509-512,537-539,546-548,560-562,565-568,628-630,723-725,735-737,752-755,774-777,815-818,861-863,897-899,911-913,995-997,1031-1033,1043-1045,1169-1172,1239-1241,1281-1284,1311-1315,1407-1410,1741-1743,2111-2146,2624-2626,2699-2725,3307-3343,3820-3835,4156-4212,5313-5345,5565-5598,5635-5678,5884-5999,7303-7366,7968-8031,8975-8994,9343-9485,10894-11114,16433-16563,18071-18127,20480-21937,201375-235865,1701380-1801380,56371921-58000000\"" 
            + "}}}}" 
            + "}";

}
