package com.ofb.api;

import java.util.*;

public class OrderListOrdersResult {
    public int resultCode;
    public String resultText;
    public String token;
	public Date lastUpdatedStamp;
	public Date lastUpdatedTxStamp;
	public Date createdStamp;
	public Date createdTxStamp;
    public List <OrderWithDetails> orders;
}