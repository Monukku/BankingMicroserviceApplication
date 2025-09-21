package com.rewabank.customer.constants;

public class CustomerConstants {
        private CustomerConstants(){
        }
        public static final  boolean ACTIVE_SW=true;
        public static final  boolean IN_ACTIVE_SW=false;
        public static final  String SAVINGS="savings";
        public static final  String ADDRESS="123 Main road ,Rewa Madhya Pradesh";
        public static final  String STATUS_201="201";
        public static final  String MESSAGE_201="Customer Created successfully";
        public static final  String STATUS_200="200";
        public static final  String MESSAGE_200="Request processed successfully";
        public static final  String STATUS_417="417";
        public static final  String MESSAGE_417_UPDATE="Update operation failed please try again or contact dev team";
        public static final  String MESSAGE_417_DELETE="Delete operation failed please try again or contact dev team";
        public static final  String STATUS_500="500";
        public static final  String MESSAGE_500="An error occurred please try again or contact dev team";
        public static final String STATUS_409 = "409";
        public static final String MESSAGE_409 = "Customer deactivation failed. The account might already be deactivated or doesn't exist.";

}
