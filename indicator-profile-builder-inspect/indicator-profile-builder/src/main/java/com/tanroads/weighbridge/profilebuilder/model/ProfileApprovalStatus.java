package com.tanroads.weighbridge.profilebuilder.model;

/** DRAFT -> TESTING -> VALIDATED -> APPROVED -> DEPLOYED, strictly forward one step at a
 *  time (ProfileLifecycleService). Note the main system always re-imports a profile as
 *  DRAFT regardless of the status recorded here -- the status in an exported file is a
 *  record of what this tool's technician concluded, not an instruction to the main system. */
public enum ProfileApprovalStatus {
    DRAFT,
    TESTING,
    VALIDATED,
    APPROVED,
    DEPLOYED
}
