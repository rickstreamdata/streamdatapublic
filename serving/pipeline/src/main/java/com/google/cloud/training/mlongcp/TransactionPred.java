package com.google.cloud.training.mlongcp;

import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.DefaultCoder;

@DefaultCoder(AvroCoder.class)
public class TransactionPred {
  Record flight;
  double predictedFraudProbability;
  
  TransactionPred(){}
  TransactionPred(Record f, double pred) {
    this.flight = f;
    this.predictedFraudProbability = pred;
  }
}
