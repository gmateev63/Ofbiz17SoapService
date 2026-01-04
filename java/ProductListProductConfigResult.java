package com.ofb.api;

import java.util.Date;
import java.util.List;

class ProductConfig {
	public String productId;
	public String configItemId;
	public String sequenceNum;
	public String description;
	public String longDescription;
	public Date fromDate;
	public Date thruDate;
	public String defaultConfigOptionId;
	public String isMandatory;
}

public class ProductListProductConfigResult {
    public int resultCode;
    public String resultText;
    public String token;
    public List <ProductConfig> productConfigs;
}