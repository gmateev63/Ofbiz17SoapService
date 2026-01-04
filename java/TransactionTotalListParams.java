package com.ofb.api;

import java.util.Date;

enum AggregationLevel {
        ITEM_AGGREGATION, ITEM_PROMO_AGGREGATION, DEPARTMENT_AGGREGATION, STORE_AGGREGATION, TAX_AGGREGATION, PROMO_AGGREGATION
}

//enum PaidState {
//      PAID, NOT_PAID, ALL
//}

public class TransactionTotalListParams {
        public Date fromTime;
        public Date toTime;
        public AggregationLevel aggregationLevel;
        public PaidState paid;
        public String departmentId;

        public String toString() {
                return("fromTime: " + fromTime + " ; toTime:" + toTime
                                + " ; aggregationLevel: " + aggregationLevel + " ; paid:" + paid + " ; departmentId:" + departmentId
                                );
        }
}
