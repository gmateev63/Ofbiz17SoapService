package com.ofb.api;

import java.util.List;

class Promotion {
	public String productPromoId;
	public String promoName;
	public String promoText;
}

public class PromotionListPromotionsResult {
    public int resultCode;
    public String resultText;
    public String token;
    public List <Promotion> promotions;
}