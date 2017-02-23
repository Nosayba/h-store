package org.qcri.affinityplanner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.qcri.affinityplanner.Plan.Range;

import edu.brown.BaseTestCase;
import edu.brown.utils.ProjectType;

/**
 * @author rytaft
 */
public class TestReconfigurationPlanner extends BaseTestCase {

    final int PARTITIONS_PER_SITE = 6;
    Map<String,Integer> tableSizes;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp(ProjectType.B2W);
        this.tableSizes = new HashMap<>();
        tableSizes.put("stk_inventory_stock_quantity", 1000003);
        tableSizes.put("stk_stock_transaction", 1000003);
        tableSizes.put("stk_inventory_stock", 1000003);
        tableSizes.put("cart", 1000003);
        tableSizes.put("checkout", 1000003);
    }

    public void testPlanner1() throws Exception {
        ReconfigurationPlanner planner = new ReconfigurationPlanner (plan1, 18, PARTITIONS_PER_SITE);
        planner.repartition();
        System.out.println(planner.getPlanString());
        Plan plan = planner.getPlan();
        for(String table : plan.table_names) {
            Map<Integer,List<Range>> rangeMap = plan.getAllRanges(table);
            for (int i = 0; i < 18; ++i) {
                assertEquals(4,rangeMap.get(i).size());
            }
        }
        
        assertTrue(plan.verifyPlan(tableSizes));
    }
    
    public void testPlanner2() throws Exception {
        ReconfigurationPlanner planner = new ReconfigurationPlanner (plan2, 36, PARTITIONS_PER_SITE);
        planner.repartition();
        System.out.println(planner.getPlanString());
        Plan plan = planner.getPlan();
        for(String table : plan.table_names) {
            Map<Integer,List<Range>> rangeMap = plan.getAllRanges(table);
            Long keys_per_part = new Long(1000003/36);
            for (int i = 0; i < 36; ++i) {
                long num_keys = Plan.getRangeListWidth(rangeMap.get(i));
                assertTrue(num_keys < keys_per_part + 3 && num_keys > keys_per_part - 3);
            }
        }
        
        assertTrue(plan.verifyPlan(tableSizes));
    }

    String plan1 = 
         "    {"
       + "     \"partition_plan\": {"
       + "       \"tables\": {"
       + "         \"stk_inventory_stock_quantity\": {"
       + "           \"partitions\": {"
       + "             \"0\": \"0-27778\"," 
       + "             \"1\": \"27778-55556\"," 
       + "             \"2\": \"55556-83334\"," 
       + "             \"3\": \"83334-111112\"," 
       + "             \"4\": \"111112-138890\"," 
       + "             \"5\": \"138890-166668\"," 
       + "             \"6\": \"166668-194446\"," 
       + "             \"7\": \"194446-222224\"," 
       + "             \"8\": \"222224-250002\"," 
       + "             \"9\": \"250002-277780\"," 
       + "             \"10\": \"277780-305558\"," 
       + "             \"11\": \"305558-333336\"," 
       + "             \"12\": \"333336-361114\"," 
       + "             \"13\": \"361114-388892\"," 
       + "             \"14\": \"388892-416670\"," 
       + "             \"15\": \"416670-444448\"," 
       + "             \"16\": \"444448-472226\"," 
       + "             \"17\": \"472226-500004\"," 
       + "             \"18\": \"500004-527782\"," 
       + "             \"19\": \"527782-555560\"," 
       + "             \"20\": \"555560-583338\"," 
       + "             \"21\": \"583338-611116\"," 
       + "             \"22\": \"611116-638894\"," 
       + "             \"23\": \"638894-666672\"," 
       + "             \"24\": \"666672-694450\"," 
       + "             \"25\": \"694450-722228\"," 
       + "             \"26\": \"722228-750006\"," 
       + "             \"27\": \"750006-777784\"," 
       + "             \"28\": \"777784-805562\"," 
       + "             \"29\": \"805562-833340\"," 
       + "             \"30\": \"833340-861118\"," 
       + "             \"31\": \"861118-888895\"," 
       + "             \"32\": \"888895-916672\"," 
       + "             \"33\": \"916672-944449\"," 
       + "             \"34\": \"944449-972226\"," 
       + "             \"35\": \"972226-1000003\""
       + "            }"
       + "          },"
       + "         \"cart\": {"
       + "            \"partitions\": {"
       + "             \"0\": \"0-27778\"," 
       + "             \"1\": \"27778-55556\"," 
       + "             \"2\": \"55556-83334\"," 
       + "             \"3\": \"83334-111112\"," 
       + "             \"4\": \"111112-138890\"," 
       + "             \"5\": \"138890-166668\"," 
       + "             \"6\": \"166668-194446\"," 
       + "             \"7\": \"194446-222224\"," 
       + "             \"8\": \"222224-250002\"," 
       + "             \"9\": \"250002-277780\"," 
       + "             \"10\": \"277780-305558\"," 
       + "             \"11\": \"305558-333336\"," 
       + "             \"12\": \"333336-361114\"," 
       + "             \"13\": \"361114-388892\"," 
       + "             \"14\": \"388892-416670\"," 
       + "             \"15\": \"416670-444448\"," 
       + "             \"16\": \"444448-472226\"," 
       + "             \"17\": \"472226-500004\"," 
       + "             \"18\": \"500004-527782\"," 
       + "             \"19\": \"527782-555560\"," 
       + "             \"20\": \"555560-583338\"," 
       + "             \"21\": \"583338-611116\"," 
       + "             \"22\": \"611116-638894\"," 
       + "             \"23\": \"638894-666672\"," 
       + "             \"24\": \"666672-694450\"," 
       + "             \"25\": \"694450-722228\"," 
       + "             \"26\": \"722228-750006\"," 
       + "             \"27\": \"750006-777784\"," 
       + "             \"28\": \"777784-805562\"," 
       + "             \"29\": \"805562-833340\"," 
       + "             \"30\": \"833340-861118\"," 
       + "             \"31\": \"861118-888895\"," 
       + "             \"32\": \"888895-916672\"," 
       + "             \"33\": \"916672-944449\"," 
       + "             \"34\": \"944449-972226\"," 
       + "             \"35\": \"972226-1000003\""
       + "            }"
       + "          },"
       + "         \"stk_stock_transaction\": {"
       + "           \"partitions\": {"
       + "             \"0\": \"0-27778\"," 
       + "             \"1\": \"27778-55556\"," 
       + "             \"2\": \"55556-83334\"," 
       + "             \"3\": \"83334-111112\"," 
       + "             \"4\": \"111112-138890\"," 
       + "             \"5\": \"138890-166668\"," 
       + "             \"6\": \"166668-194446\"," 
       + "             \"7\": \"194446-222224\"," 
       + "             \"8\": \"222224-250002\"," 
       + "             \"9\": \"250002-277780\"," 
       + "             \"10\": \"277780-305558\"," 
       + "             \"11\": \"305558-333336\"," 
       + "             \"12\": \"333336-361114\"," 
       + "             \"13\": \"361114-388892\"," 
       + "             \"14\": \"388892-416670\"," 
       + "             \"15\": \"416670-444448\"," 
       + "             \"16\": \"444448-472226\"," 
       + "             \"17\": \"472226-500004\"," 
       + "             \"18\": \"500004-527782\"," 
       + "             \"19\": \"527782-555560\"," 
       + "             \"20\": \"555560-583338\"," 
       + "             \"21\": \"583338-611116\"," 
       + "             \"22\": \"611116-638894\"," 
       + "             \"23\": \"638894-666672\"," 
       + "             \"24\": \"666672-694450\"," 
       + "             \"25\": \"694450-722228\"," 
       + "             \"26\": \"722228-750006\"," 
       + "             \"27\": \"750006-777784\"," 
       + "             \"28\": \"777784-805562\"," 
       + "             \"29\": \"805562-833340\"," 
       + "             \"30\": \"833340-861118\"," 
       + "             \"31\": \"861118-888895\"," 
       + "             \"32\": \"888895-916672\"," 
       + "             \"33\": \"916672-944449\"," 
       + "             \"34\": \"944449-972226\"," 
       + "             \"35\": \"972226-1000003\""
       + "            }"
       + "          },"
       + "         \"checkout\": {"
       + "           \"partitions\": {"
       + "             \"0\": \"0-27778\"," 
       + "             \"1\": \"27778-55556\"," 
       + "             \"2\": \"55556-83334\"," 
       + "             \"3\": \"83334-111112\"," 
       + "             \"4\": \"111112-138890\"," 
       + "             \"5\": \"138890-166668\"," 
       + "             \"6\": \"166668-194446\"," 
       + "             \"7\": \"194446-222224\"," 
       + "             \"8\": \"222224-250002\"," 
       + "             \"9\": \"250002-277780\"," 
       + "             \"10\": \"277780-305558\"," 
       + "             \"11\": \"305558-333336\"," 
       + "             \"12\": \"333336-361114\"," 
       + "             \"13\": \"361114-388892\"," 
       + "             \"14\": \"388892-416670\"," 
       + "             \"15\": \"416670-444448\"," 
       + "             \"16\": \"444448-472226\"," 
       + "             \"17\": \"472226-500004\"," 
       + "             \"18\": \"500004-527782\"," 
       + "             \"19\": \"527782-555560\"," 
       + "             \"20\": \"555560-583338\"," 
       + "             \"21\": \"583338-611116\"," 
       + "             \"22\": \"611116-638894\"," 
       + "             \"23\": \"638894-666672\"," 
       + "             \"24\": \"666672-694450\"," 
       + "             \"25\": \"694450-722228\"," 
       + "             \"26\": \"722228-750006\"," 
       + "             \"27\": \"750006-777784\"," 
       + "             \"28\": \"777784-805562\"," 
       + "             \"29\": \"805562-833340\"," 
       + "             \"30\": \"833340-861118\"," 
       + "             \"31\": \"861118-888895\"," 
       + "             \"32\": \"888895-916672\"," 
       + "             \"33\": \"916672-944449\"," 
       + "             \"34\": \"944449-972226\"," 
       + "             \"35\": \"972226-1000003\""
       + "            }"
       + "          },"
       + "         \"stk_inventory_stock\": {"
       + "           \"partitions\": {"
       + "             \"0\": \"0-27778\"," 
       + "             \"1\": \"27778-55556\"," 
       + "             \"2\": \"55556-83334\"," 
       + "             \"3\": \"83334-111112\"," 
       + "             \"4\": \"111112-138890\"," 
       + "             \"5\": \"138890-166668\"," 
       + "             \"6\": \"166668-194446\"," 
       + "             \"7\": \"194446-222224\"," 
       + "             \"8\": \"222224-250002\"," 
       + "             \"9\": \"250002-277780\"," 
       + "             \"10\": \"277780-305558\"," 
       + "             \"11\": \"305558-333336\"," 
       + "             \"12\": \"333336-361114\"," 
       + "             \"13\": \"361114-388892\"," 
       + "             \"14\": \"388892-416670\"," 
       + "             \"15\": \"416670-444448\"," 
       + "             \"16\": \"444448-472226\"," 
       + "             \"17\": \"472226-500004\"," 
       + "             \"18\": \"500004-527782\"," 
       + "             \"19\": \"527782-555560\"," 
       + "             \"20\": \"555560-583338\"," 
       + "             \"21\": \"583338-611116\"," 
       + "             \"22\": \"611116-638894\"," 
       + "             \"23\": \"638894-666672\"," 
       + "             \"24\": \"666672-694450\"," 
       + "             \"25\": \"694450-722228\"," 
       + "             \"26\": \"722228-750006\"," 
       + "             \"27\": \"750006-777784\"," 
       + "             \"28\": \"777784-805562\"," 
       + "             \"29\": \"805562-833340\"," 
       + "             \"30\": \"833340-861118\"," 
       + "             \"31\": \"861118-888895\"," 
       + "             \"32\": \"888895-916672\"," 
       + "             \"33\": \"916672-944449\"," 
       + "             \"34\": \"944449-972226\"," 
       + "             \"35\": \"972226-1000003\""
       + "            }"
       + "          }"
       + "        }"
       + "      }," 
       + "     \"default_table\": \"cart\""
       + "    }";
    
    String plan2 =
         "    {"
       + "     \"partition_plan\": {"
       + "       \"tables\": {"
       + "         \"stk_inventory_stock_quantity\": {"
       + "           \"partitions\": {"
       + "             \"0\": \"0-27778,972226-1000003\","
       + "             \"1\": \"27778-55556,888895-916672\","
       + "             \"2\": \"55556-83334,805562-833340\","
       + "             \"3\": \"83334-111112,722228-750006\","
       + "             \"4\": \"111112-138890,638894-666672\","
       + "             \"5\": \"138890-166668,555560-583338\"," 
       + "             \"6\": \"166668-194446,944449-972226\","
       + "             \"7\": \"194446-222224,861118-888895\","
       + "             \"8\": \"222224-250002,777784-805562\","
       + "             \"9\": \"250002-277780,694450-722228\","
       + "             \"10\": \"277780-305558,611116-638894\"," 
       + "             \"11\": \"305558-333336,527782-555560\","
       + "             \"12\": \"333336-361114,916672-944449\","
       + "             \"13\": \"361114-388892,833340-861118\","
       + "             \"14\": \"388892-416670,750006-777784\","
       + "             \"15\": \"416670-444448,666672-694450\","
       + "             \"16\": \"444448-472226,583338-611116\"," 
       + "             \"17\": \"472226-527782\""
       + "            }"
       + "          }," 
       + "         \"cart\": {"
       + "           \"partitions\": {"
       + "             \"0\": \"0-27778,972226-1000003\","
       + "             \"1\": \"27778-55556,888895-916672\","
       + "             \"2\": \"55556-83334,805562-833340\","
       + "             \"3\": \"83334-111112,722228-750006\","
       + "             \"4\": \"111112-138890,638894-666672\","
       + "             \"5\": \"138890-166668,555560-583338\"," 
       + "             \"6\": \"166668-194446,944449-972226\","
       + "             \"7\": \"194446-222224,861118-888895\","
       + "             \"8\": \"222224-250002,777784-805562\","
       + "             \"9\": \"250002-277780,694450-722228\","
       + "             \"10\": \"277780-305558,611116-638894\"," 
       + "             \"11\": \"305558-333336,527782-555560\","
       + "             \"12\": \"333336-361114,916672-944449\","
       + "             \"13\": \"361114-388892,833340-861118\","
       + "             \"14\": \"388892-416670,750006-777784\","
       + "             \"15\": \"416670-444448,666672-694450\","
       + "             \"16\": \"444448-472226,583338-611116\"," 
       + "             \"17\": \"472226-527782\""
       + "            }"
       + "          }," 
       + "         \"stk_stock_transaction\": {"
       + "           \"partitions\": {"
       + "             \"0\": \"0-27778,972226-1000003\","
       + "             \"1\": \"27778-55556,888895-916672\","
       + "             \"2\": \"55556-83334,805562-833340\","
       + "             \"3\": \"83334-111112,722228-750006\","
       + "             \"4\": \"111112-138890,638894-666672\","
       + "             \"5\": \"138890-166668,555560-583338\"," 
       + "             \"6\": \"166668-194446,944449-972226\","
       + "             \"7\": \"194446-222224,861118-888895\","
       + "             \"8\": \"222224-250002,777784-805562\","
       + "             \"9\": \"250002-277780,694450-722228\","
       + "             \"10\": \"277780-305558,611116-638894\"," 
       + "             \"11\": \"305558-333336,527782-555560\","
       + "             \"12\": \"333336-361114,916672-944449\","
       + "             \"13\": \"361114-388892,833340-861118\","
       + "             \"14\": \"388892-416670,750006-777784\","
       + "             \"15\": \"416670-444448,666672-694450\","
       + "             \"16\": \"444448-472226,583338-611116\"," 
       + "             \"17\": \"472226-527782\""
       + "            }"
       + "          }," 
       + "         \"checkout\": {"
       + "           \"partitions\": {"
       + "             \"0\": \"0-27778,972226-1000003\","
       + "             \"1\": \"27778-55556,888895-916672\","
       + "             \"2\": \"55556-83334,805562-833340\","
       + "             \"3\": \"83334-111112,722228-750006\","
       + "             \"4\": \"111112-138890,638894-666672\","
       + "             \"5\": \"138890-166668,555560-583338\"," 
       + "             \"6\": \"166668-194446,944449-972226\","
       + "             \"7\": \"194446-222224,861118-888895\","
       + "             \"8\": \"222224-250002,777784-805562\","
       + "             \"9\": \"250002-277780,694450-722228\","
       + "             \"10\": \"277780-305558,611116-638894\"," 
       + "             \"11\": \"305558-333336,527782-555560\","
       + "             \"12\": \"333336-361114,916672-944449\","
       + "             \"13\": \"361114-388892,833340-861118\","
       + "             \"14\": \"388892-416670,750006-777784\","
       + "             \"15\": \"416670-444448,666672-694450\","
       + "             \"16\": \"444448-472226,583338-611116\"," 
       + "             \"17\": \"472226-527782\""
       + "            }"
       + "          }," 
       + "         \"stk_inventory_stock\": {"
       + "           \"partitions\": {"
       + "             \"0\": \"0-27778,972226-1000003\","
       + "             \"1\": \"27778-55556,888895-916672\","
       + "             \"2\": \"55556-83334,805562-833340\","
       + "             \"3\": \"83334-111112,722228-750006\","
       + "             \"4\": \"111112-138890,638894-666672\","
       + "             \"5\": \"138890-166668,555560-583338\"," 
       + "             \"6\": \"166668-194446,944449-972226\","
       + "             \"7\": \"194446-222224,861118-888895\","
       + "             \"8\": \"222224-250002,777784-805562\","
       + "             \"9\": \"250002-277780,694450-722228\","
       + "             \"10\": \"277780-305558,611116-638894\"," 
       + "             \"11\": \"305558-333336,527782-555560\","
       + "             \"12\": \"333336-361114,916672-944449\","
       + "             \"13\": \"361114-388892,833340-861118\","
       + "             \"14\": \"388892-416670,750006-777784\","
       + "             \"15\": \"416670-444448,666672-694450\","
       + "             \"16\": \"444448-472226,583338-611116\"," 
       + "             \"17\": \"472226-527782\""
       + "            }"
       + "          }"
       + "        }"
       + "      }," 
       + "     \"default_table\": \"cart\""
       + "    }";

}
