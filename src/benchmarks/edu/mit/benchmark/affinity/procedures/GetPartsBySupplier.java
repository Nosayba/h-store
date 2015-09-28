package edu.mit.benchmark.affinity.procedures;

import java.util.Random;

import org.voltdb.ProcInfo;
import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;


@ProcInfo(
        partitionInfo = "SUPPLIES.SUPPLIER_KEY: 0",
        singlePartition = true
    )
public class GetPartsBySupplier extends VoltProcedure {
//    private static final Logger LOG = Logger.getLogger(VoltProcedure.class);
//    private static final LoggerBoolean debug = new LoggerBoolean();
//    private static final LoggerBoolean trace = new LoggerBoolean();
//    static {
//        LoggerUtil.setupLogging();
//        LoggerUtil.attachObserver(LOG, debug, trace);
//    }
    
    
	public final SQLStmt getSupplierInfoStmt = new SQLStmt("SELECT FIELD1, FIELD2, FIELD3 FROM SUPPLIERS WHERE SUPPLIER_KEY = ? ");
    public final SQLStmt getPartsBySupplierStmt = new SQLStmt("SELECT PART_KEY FROM SUPPLIES WHERE SUPPLIER_KEY = ? ");
    public final SQLStmt getPartInfoStmt = new SQLStmt("SELECT FIELD1, FIELD2, FIELD3 FROM PARTS WHERE PART_KEY = ? ");
    
    public VoltTable[] run(long supplier_key, boolean limitScan, int limitScanTo){
    	voltQueueSQL(getSupplierInfoStmt, supplier_key);
        voltQueueSQL(getPartsBySupplierStmt, supplier_key);
        final VoltTable[] results = voltExecuteSQL();
        assert results.length == 2;
        if (limitScan && results[1].getRowCount() > limitScanTo){
            //Limit the data we are scanning. select parts at random
            long[] parts = new long[results[1].getRowCount()];
            
            //get the list of parts
            for(int i = 0; i < results[1].getRowCount(); ++i) {
                parts[i] = results[1].fetchRow(i).getLong(0);
            }
            //Shuffle list of parts
            Random r = new Random();
            for (int i = parts.length - 1; i > 0; i--) {
                int index = r.nextInt(i + 1);
                // Simple swap
                long a = parts[index];
                parts[index] = parts[i];
                parts[i] = a;
            }            
            
            
            for(int i = 0; i < limitScanTo; ++i) {
                voltQueueSQL(getPartInfoStmt, parts[i]);
            }
            
        } else {
            for(int i = 0; i < results[1].getRowCount(); ++i) {
                voltQueueSQL(getPartInfoStmt, results[1].fetchRow(i).getLong(0));
            }
        }
        return voltExecuteSQL(true);
    }

}
