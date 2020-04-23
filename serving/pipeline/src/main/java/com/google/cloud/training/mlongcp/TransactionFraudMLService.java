package com.google.cloud.training.mlongcp;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.util.ExponentialBackOff;
import com.google.cloud.training.mlongcp.Record.INPUTCOLS;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TransactionFraudMLService {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionFraudMLService.class);
  private static final String PROJECT = "fs-engineering-sandbox-422774";
  private static String       MODEL   = "transaction";
  private static String       VERSION = "soln";

//fraud_probability,date,trans_id,acct_id,tr_type,operation,amount,balance,k_symbol,bank

  static class Instance {
    String tr_type,operation;
    float amount,balance; 
    
    Instance() {}
    Instance(Record f) {
      //this.key = "" + UUID.randomUUID();
      this.tr_type = f.getField(Record.INPUTCOLS.tr_type);
      this.operation = f.getField(Record.INPUTCOLS.operation);
      this.amount = f.getFieldAsFloat(Record.INPUTCOLS.amount);
      this.balance = f.getFieldAsFloat(Record.INPUTCOLS.balance);
    }
  }

  static class Request {
    List<Instance> instances = new ArrayList<>();
  }

  static class Prediction {
    List<Double> predictions;
  }

  static class Response {
    List<Prediction> predictions = new ArrayList<>();

    public double[] getPredictedTransactionFraud() {
      double[] result = new double[predictions.size()];
      for (int i=0; i < result.length; ++i) {
        Prediction pred = predictions.get(i);
        result[i] = pred.predictions.get(0);
      }
      return result;
    }
  }

  static Response sendRequest(Request req) throws IOException, GeneralSecurityException {
    long startTime = System.currentTimeMillis();
    try {
      // create JSON of request
      Gson gson = new GsonBuilder().create();
      String json = gson.toJson(req, Request.class);
      LOG.debug(json);

      // our service's URL
      String endpoint = "https://ml.googleapis.com/v1/projects/" 
          + String.format("%s/models/%s/versions/%s:predict", PROJECT, MODEL, VERSION);
      GenericUrl url = new GenericUrl(endpoint);

      // set up https
      GoogleCredential credential = GoogleCredential.getApplicationDefault();
      HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
      HttpRequestFactory requestFactory = httpTransport.createRequestFactory(credential);
      HttpContent content = new ByteArrayContent("application/json", json.getBytes());
      
      // send request
      HttpRequest request = requestFactory.buildRequest("POST", url, content);
      request.setUnsuccessfulResponseHandler(new HttpBackOffUnsuccessfulResponseHandler(new ExponentialBackOff()));
      request.setReadTimeout(1 * 60 * 1000); // 1 minute
      String response = request.execute().parseAsString();
      LOG.debug(response);
      
      // parse response
      return gson.fromJson(response, Response.class);
    }
    finally {
      long endTime = System.currentTimeMillis();
      LOG.debug((endTime - startTime) + " msecs overall");
    }
  }
  
  public static double[] mock_batchPredict(Iterable<Record> instances) throws IOException, GeneralSecurityException {
    int n = 0;
    for (@SuppressWarnings("unused") Record f : instances) {
      ++n;
    }
    LOG.info("Mock prediction for " + n + " instances");
    double[] result = new double[n];
    for (int i=0; i < n; ++i) {
      result[i] = Math.random() * 10;
    }
    return result;
  }
  
  public static double[] batchPredict(Iterable<Record> instances) throws IOException, GeneralSecurityException {
    Request request = new Request();
    for (Record f : instances) {
      request.instances.add(new Instance(f));
    }
    Response resp = sendRequest(request);
    double[] result = resp.getPredictedTransactionFraud();
    return result;
  }

  public static double predict(Record f, double defaultValue) throws IOException, GeneralSecurityException {
    
      Request request = new Request();

      // fill in actual values
      Instance instance = new Instance(f);
      request.instances.add(instance);

      // send request
      Response resp = sendRequest(request);
      double[] result = resp.getPredictedTransactionFraud();
      if (result.length > 0) {
        return result[0];
      } else {
        return defaultValue;
      }
    
  }

  public static void main(String[] args) throws Exception {   
    // create request
    Request request = new Request();

    Instance instance = new Instance();
    //instance.key = "notused";
    instance.tr_type = "PRIJEM";
    instance.operation = "PREVODZUCTU";
    instance.amount = 7669;
    instance.balance = 8569;

    request.instances.add(instance);

    // send request to service
    Response resp = sendRequest(request);
    System.out.println(resp.getPredictedTransactionFraud()[0]);

    //fraud_probability,date,trans_id,acct_id,tr_type,operation,amount,balance,k_symbol,bank

    Record f = Record.fromCsv("0.8,10120200002,438905,1492,PRIJEM,PREVODZUCTU,4733,31498.8,DUCHOD,AB");
    System.out.println("predicted=" + predict(f, -1) + " actual=" + f.getFieldAsFloat(INPUTCOLS.fraud_probability));
  }

}
