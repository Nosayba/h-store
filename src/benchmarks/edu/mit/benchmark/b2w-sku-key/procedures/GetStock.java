package edu.mit.benchmark.b2w.procedures;

import org.voltdb.ProcInfo;
import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;

import edu.mit.benchmark.b2w.B2WUtil;

@ProcInfo(
        partitionInfo = "STK_INVENTORY_STOCK.partition_key: 0",
        singlePartition = true
    )
public class GetStock extends VoltProcedure {
//    private static final Logger LOG = Logger.getLogger(VoltProcedure.class);
//    private static final LoggerBoolean debug = new LoggerBoolean();
//    private static final LoggerBoolean trace = new LoggerBoolean();
//    static {
//        LoggerUtil.setupLogging();
//        LoggerUtil.attachObserver(LOG, debug, trace);
//    }
        
    public final SQLStmt getStockStmt = new SQLStmt("SELECT * FROM STK_INVENTORY_STOCK WHERE partition_key = ? AND sku = ? ");
        
    public VoltTable[] run(int partition_key, String sku, long sleep_time){
        B2WUtil.sleep(sleep_time);
        
        voltQueueSQL(getStockStmt, partition_key, sku);
        
        return voltExecuteSQL(true);
    }

}
