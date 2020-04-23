package com.google.cloud;

import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.DefaultCoder;

@DefaultCoder(AvroCoder.class)
public class LaneInfo {
  private String[] fields;

  private enum Field {
    date, trans_id, acct_id, type, operation, amount, balance, k_symbol, bank;
  }

  public LaneInfo() {
    // for Avro
  }

  public static LaneInfo newLaneInfo(String line) {
    String[] pieces = line.split(",");
    LaneInfo info = new LaneInfo();
    info.fields = pieces;
    return info;
  }

  private String get(Field f) {
    return fields[f.ordinal()];
  }

  public String getTimestamp() {
    return fields[Field.date.ordinal()];
    // return Timestamp.valueOf(fields[Field.TIMESTAMP.ordinal()]).getTime();
  }

  /*
   
   * Create unique key for sensor in a particular lane
   * 
   * @return
   
  public String getSensorKey() {
    StringBuilder sb = new StringBuilder();
    for (int f = Field.LATITUDE.ordinal(); f <= Field.LANE.ordinal(); ++f) {
      sb.append(fields[f]);
      sb.append(',');
    }
    return sb.substring(0, sb.length() - 1); // without trailing comma
  }

  
   * Create unique key for all the sensors for traffic in same direction at a
   * location
   * 
   * @return

  public String getLocationKey() {
    StringBuilder sb = new StringBuilder();
    for (int f = Field.LATITUDE.ordinal(); f <= Field.FREEWAY_DIR.ordinal(); ++f) {
      sb.append(fields[f]);
      sb.append(',');
    }
    return sb.substring(0, sb.length() - 1); // without trailing comma
  }
  */
  public int getTransID() {
    return Integer.parseInt(get(Field.trans_id));
  }

  public int getAcctID() {
    return Integer.parseInt(get(Field.acct_id));
  }

  public String getType() {
    return get(Field.type);
  }

  public String getOperation() {
    return get(Field.operation);
  }
  
  public double getAmount() {
    return Double.parseDouble(get(Field.amount));
  }

  public double getBalance() {
    return Double.parseDouble(get(Field.balance));
  }

  public String getKSymbol() {
    return get(Field.k_symbol);
  }

  public String getBank() {
    return get(Field.bank);
  }
}
