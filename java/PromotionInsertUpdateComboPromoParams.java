package com.ofb.api;

import java.util.Date;
import java.math.BigDecimal;

enum PromoMethod {
	NoAction, XItemsForYPrice, XItemsForYDiscount, XItemsForYPercentDiscount 
}

class CategoryPart {
	public String categoryId;
	public PromoMethod method;
	public BigDecimal quantity;
	public BigDecimal amount;
}

class WeekdayAviability {
	public String startTime;    
	public String endTime;
}

public class PromotionInsertUpdateComboPromoParams {
	public String promoId;
	public String promoName;
	public String promoText;
	public CategoryPart categoryPartFirst;
	public CategoryPart categoryPartSecond;
	public CategoryPart categoryPartThird;
	public CategoryPart categoryPartFourth;	
	public Date startDate;    
	public Date endDate;
	public WeekdayAviability sundayAviability;
	public WeekdayAviability mondayAviability;
	public WeekdayAviability tuesdayAviability;
	public WeekdayAviability wednesayAviability;
	public WeekdayAviability thursdayAviability;
	public WeekdayAviability fridayAviability;
	public WeekdayAviability saturdayAviability;
		
	public String toString() {
		return("promoId: " + promoId + "promoName: " + promoName + "promoText: " + promoText + "promoTypeId: "
				+ " ; startDate: " + startDate + " ; endDate:" + endDate
				);
	}
}