package edu.mit.benchmark.b2w.procedures;

import java.util.HashSet;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.voltdb.ProcInfo;
import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;
import org.voltdb.VoltTableRow;
import org.voltdb.types.TimestampType;

import edu.brown.logging.LoggerUtil;
import edu.brown.logging.LoggerUtil.LoggerBoolean;
import edu.mit.benchmark.b2w.B2WConstants;
import edu.mit.benchmark.b2w.B2WUtil;

@ProcInfo(
        partitionInfo = "STK_STOCK_TRANSACTION.partition_key: 0",
        singlePartition = true
    )
public class CreateStockTransaction extends VoltProcedure {
    private static final Logger LOG = Logger.getLogger(VoltProcedure.class);
    private static final LoggerBoolean debug = new LoggerBoolean();
    private static final LoggerBoolean trace = new LoggerBoolean();
    static {
        LoggerUtil.setupLogging();
        LoggerUtil.attachObserver(LOG, debug, trace);
    }
    
    public final SQLStmt createStockTxnStmt = new SQLStmt(
            "INSERT INTO STK_STOCK_TRANSACTION (" +
                "partition_key, " +
                "transaction_id, " +
                "reserve_id, " +
                "brand, " +
                "creation_date, " +
                "current_status, " +
                "expiration_date, " +
                "is_kit, " +
                "requested_quantity, " +
                "reserve_lines, " +
                "reserved_quantity, " +
                "sku, " +
                "status, " +
                "store_id, " +
                "subinventory, " +
                "warehouse" +
            ") VALUES (" +
                "?, " +   // partition_key
                "?, " +   // transaction_id
                "?, " +   // reserve_id
                "?, " +   // brand
                "?, " +   // creation_date
                "?, " +   // current_status
                "?, " +   // expiration_date
                "?, " +   // is_kit
                "?, " +   // requested_quantity
                "?, " +   // reserve_lines
                "?, " +   // reserved_quantity
                "?, " +   // sku
                "?, " +   // status
                "?, " +   // store_id
                "?, " +   // subinventory
                "?"   +   // warehouse
            ");");
    
    public final SQLStmt getStockTxnStmt = new SQLStmt("SELECT reserve_id FROM STK_STOCK_TRANSACTION " +
                                                       " WHERE partition_key = ? AND transaction_id = ?;");

    public VoltTable[] run(int partition_key, String transaction_id, String[] reserve_id, String[] brand, TimestampType[] timestamp,
            TimestampType[] expiration_date, byte[] is_kit, int[] requested_quantity, String[] reserve_lines, int[] reserved_quantity, String[] sku, 
            String[] store_id, int[] subinventory, int[] warehouse, long sleep_time) {
        B2WUtil.sleep(sleep_time);
                
        String current_status = B2WConstants.STATUS_NEW;
        
        voltQueueSQL(getStockTxnStmt, partition_key, transaction_id);
        final VoltTable[] stock_txn_results = voltExecuteSQL();
        assert stock_txn_results.length == 1;
        
        HashSet<String> existing_reserve_ids = new HashSet<>();
        for (int i = 0; i < stock_txn_results[0].getRowCount(); ++i) {
            final VoltTableRow stock_txn = stock_txn_results[0].fetchRow(i);
            final int RESERVE_ID = 0;
            existing_reserve_ids.add(stock_txn.getString(RESERVE_ID));
        } 
            
        for (int i = 0; i < reserve_id.length; ++i) { 
            if (reserve_id[i] == null || reserve_id[i].isEmpty() || existing_reserve_ids.contains(reserve_id[i])) continue;

            JSONObject status_obj = new JSONObject();
            try {
                status_obj.put(timestamp[i].toString(), current_status);
            } catch (JSONException e) {
                if (debug.val) {
                    LOG.debug("Failed to append current status " + current_status + " at timestamp " + timestamp[i].toString());
                }
            }       
            String status = status_obj.toString();
            if (trace.val) {
                LOG.trace("Creating transaction " + transaction_id + " with status " + status);
            }

            voltQueueSQL(createStockTxnStmt,
                    partition_key,
                    transaction_id,
                    reserve_id[i],
                    brand[i],
                    timestamp[i],
                    current_status,
                    expiration_date[i],
                    is_kit[i],
                    requested_quantity[i],
                    reserve_lines[i],
                    reserved_quantity[i],
                    sku[i],
                    status,
                    store_id[i],
                    subinventory[i],
                    warehouse[i]);
        }
        
        if (reserve_id.length == existing_reserve_ids.size()) return null;
        
        return voltExecuteSQL(true);
    }

}
