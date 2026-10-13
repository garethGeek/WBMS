package wbms.tanroads.wbms.common;

public class ResponseCode {
    public static final int SUCCESS = 6000;
    public static final int INVALID_REQUEST = 6001;
    public static final int BAD_REQUEST = 6002;
    public static final int UNAUTHORIZED = 6003;
    public static final int NO_RECORD_FOUND = 6004;
    public static final int FAILURE = 6005;
    public static final int DUPLICATE = 6006;
    public static final int DATA_IN_USE = 6007;
    public static final int METHOD_NOT_ALLOWED = 6008;
    public static final int RESTRICTED_ACCESS = 6009;
    public static final int LIMIT_REACHED = 6010;
    public static final int MINIMUM_REQUIREMENT_NOT_MEET = 6011;
    public static final int COMMENT_NECESSARY_IN_ROLLBACK = 6012;
    public static final int INVALID_EMAIL_ADDRESS = 6013;
    public static final int FILE_NOT_EXIST = 6014;
    public static final int EMAIL_NOT_PROVIDED = 6015;
    public static final int INVALID_EMAIL = 6016;
    public static final int NAMES_NOT_PROVIDED = 6017;
    public static final int PASSWORD_NOT_PROVIDED = 6018;
    public static final int APPLICATION_CLOSED = 6019;
    public static final int DATA_MISSED_IN_PAYLOAD = 6020;
    public static final int TIN_NUMBER_ALREADY_EXISTS=6021;
    public static final int BILL_PAID=6022;
    public static final int BILL_EXPIRED=6023;

    public static final String RECORD_NOT_FOUND = "No record found";

    public static final String SUCCESS_MESSAGE = "Success";
    public static final String FAILURE_MESSAGE = "Failure";

    public static final String LINKED_ENTITY_NOT_FOUND = "Link Entity is not found";

    public static final String DUPLICATE_MESSAGE = "Code Already Exit";

    public static final String INVALID_EMAIL_ADDRESS_MESSAGE = "Invalid email address";

    public static final String FILE_NOT_EXIST_MESSAGE = "File does not exist";

    public static final String ENTITY_NOT_FOUND = "Entity Not Found";

    public static final String ENTITY_IN_USE = "Entity Not Found";
}
