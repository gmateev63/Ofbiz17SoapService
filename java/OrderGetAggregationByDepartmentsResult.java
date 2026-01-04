package com.ofb.api;

import java.util.List;

public class OrderGetAggregationByDepartmentsResult {
    public int resultCode;
    public String resultText;
    public String token;
    //public String count;
    public List <TransactionTotal> transactionTotals;
}