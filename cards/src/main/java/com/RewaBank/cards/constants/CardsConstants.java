package com.RewaBank.cards.constants;

public final class CardsConstants {

    private CardsConstants() {
        // restrict instantiation
    }

    public static final String  CREDIT_CARD = "Credit Card";
    public static final int  NEW_CARD_LIMIT = 1_00_000;
    public static final String  STATUS_201 = "201";
    public static final String  MESSAGE_201 = "Card created successfully";
    public static final String  STATUS_200 = "200";
    public static final String  MESSAGE_200 = "Request processed successfully";
    public static final String  STATUS_500 = "500";
    public static final String  MESSAGE_500_UPDATE= "Update operation failed. Please try again or contact Dev team";
    public static final String  MESSAGE_500_DELETE= "Delete operation failed. Please try again or contact Dev team";
    public static final boolean ACTIVE_SW = true;
    public static final boolean IN_ACTIVE_SW = false;
    // public static final String  STATUS_500 = "500";
    // public static final String  MESSAGE_500 = "An error occurred. Please try again or contact Dev team";
}
